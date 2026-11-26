package com.pocketworker.app.core.mcp

import android.content.Context
import android.util.Log
import androidx.javascriptengine.IsolateTerminatedException
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.javascriptengine.SandboxDeadException
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Offline AST syntax checker backed by Android's out-of-process V8 sandbox (`androidx.javascriptengine`).
 *
 * Uses a persistent [JavaScriptIsolate] loaded lazily from `assets/syntax/syntax-checker.bundle.js`
 * (via `provideNamedData` to stay completely below Android Binder transaction limits):
 * - JS / JSX / TS / TSX: `@babel/parser`
 * - PHP 8.x / PHTML: `glayzzle/php-parser`
 * - CSS: `css-tree`
 * - HTML: `parse5` + void-aware tag balance
 * - YAML: `js-yaml`
 * - TOML: `smol-toml`
 * - SQL: `sql-parser-cst` (PostgreSQL / MySQL / SQLite / MariaDB)
 * - Python, C, C++, C#, Java, Kotlin, Go, Rust, Bash: `web-tree-sitter` WASM grammars loaded lazily per language.
 */
class JsSandboxSyntaxEngine(
    context: Context
) : SyntaxCheckEngine {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val dataKeySeq = AtomicLong(0L)

    private var sandbox: JavaScriptSandbox? = null
    private var isolate: JavaScriptIsolate? = null
    private var bundleLoaded: Boolean = false
    private var treeSitterReady: Boolean = false
    private val loadedGrammars = mutableSetOf<String>()

    @Volatile
    private var permanentlyUnsupportedReason: String? = null

    override suspend fun check(relativePath: String, content: String): SyntaxEngineResult {
        val ext = extractExtension(relativePath)
        val tsGrammar = EXT_TO_TS_GRAMMAR[ext]
        if (ext !in PURE_JS_EXTENSIONS && tsGrammar == null) {
            return SyntaxEngineResult(handled = false, fallbackReason = "Extension '.$ext' uses native/fallback validator")
        }

        permanentlyUnsupportedReason?.let { reason ->
            return SyntaxEngineResult(handled = false, fallbackReason = reason)
        }

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                // Retry once automatically if the out-of-process V8 sandbox died between calls
                var attempt = 0
                while (attempt < 2) {
                    attempt++
                    try {
                        val activeIsolate = ensureReadyIsolateLocked()
                            ?: return@withLock SyntaxEngineResult(
                                handled = false,
                                fallbackReason = permanentlyUnsupportedReason ?: "JavaScriptSandbox unavailable"
                            )

                        if (tsGrammar != null) {
                            ensureTreeSitterGrammarLocked(activeIsolate, tsGrammar)
                        }

                        val primary = runCheckLocked(activeIsolate, relativePath, content, tsGrammar)

                        // Smart C/C++ header handling: `.h` files in C++ repos often use classes/namespaces/templates.
                        // If C parser finds errors, try C++ before reporting failure.
                        if (ext == "h" && primary.handled && primary.issues.any { it.severity == "error" }) {
                            ensureTreeSitterGrammarLocked(activeIsolate, "cpp")
                            val cppResult = runCheckLocked(activeIsolate, relativePath, content, "cpp")
                            if (cppResult.handled && cppResult.issues.none { it.severity == "error" }) {
                                return@withLock cppResult
                            }
                        }

                        return@withLock primary
                    } catch (ex: SandboxDeadException) {
                        Log.w(TAG, "V8 syntax sandbox died (attempt $attempt); resetting", ex)
                        resetLocked()
                        if (attempt >= 2) {
                            return@withLock SyntaxEngineResult(
                                handled = false,
                                fallbackReason = "V8 sandbox terminated: ${ex.message}"
                            )
                        }
                    } catch (ex: IsolateTerminatedException) {
                        Log.w(TAG, "V8 syntax isolate terminated (attempt $attempt); resetting", ex)
                        resetLocked()
                        if (attempt >= 2) {
                            return@withLock SyntaxEngineResult(
                                handled = false,
                                fallbackReason = "V8 isolate terminated: ${ex.message}"
                            )
                        }
                    } catch (ex: Throwable) {
                        Log.w(TAG, "V8 syntax check failed for '$relativePath': ${ex.message}", ex)
                        return@withLock SyntaxEngineResult(
                            handled = false,
                            fallbackReason = ex.message ?: ex.javaClass.simpleName
                        )
                    }
                }
                SyntaxEngineResult(handled = false, fallbackReason = "V8 sandbox retry exhausted")
            }
        }
    }

    override fun close() {
        runCatching { isolate?.close() }
        runCatching { sandbox?.close() }
        isolate = null
        sandbox = null
        bundleLoaded = false
        treeSitterReady = false
        loadedGrammars.clear()
    }

    private suspend fun ensureReadyIsolateLocked(): JavaScriptIsolate? {
        val existing = isolate
        if (existing != null && bundleLoaded) {
            return existing
        }

        val supported = runCatching { JavaScriptSandbox.isSupported() }.getOrDefault(false)
        if (!supported) {
            permanentlyUnsupportedReason = "JavaScriptSandbox is not supported by this device's WebView"
            return null
        }

        val sb = sandbox ?: withTimeout(INIT_TIMEOUT_MS) {
            JavaScriptSandbox.createConnectedInstanceAsync(appContext).awaitResult()
        }.also { sandbox = it }

        if (!sb.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN) ||
            !sb.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_PROVIDE_CONSUME_ARRAY_BUFFER)
        ) {
            permanentlyUnsupportedReason = "WebView JavaScriptSandbox lacks Promise/ArrayBuffer support"
            resetLocked()
            return null
        }

        val iso = sb.createIsolate()
        isolate = iso
        bundleLoaded = false
        treeSitterReady = false
        loadedGrammars.clear()

        // 1. Evaluate tiny UTF-8 loader bootstrap (<1 KB, safe under Binder limits)
        withTimeout(INIT_TIMEOUT_MS) {
            iso.evaluateJavaScriptAsync(BOOTSTRAP_JS).awaitResult()
        }

        // 2. Pass the full minified parser bundle via named data (bypasses Binder 1 MB limit)
        val bundleBytes = readAssetBytes(BUNDLE_ASSET_PATH)
        val bundleKey = nextDataKey("bundle")
        iso.provideNamedData(bundleKey, bundleBytes)

        val loadRes = withTimeout(INIT_TIMEOUT_MS) {
            iso.evaluateJavaScriptAsync("globalThis.__pwLoadBundle(${JSONObject.quote(bundleKey)})")
                .awaitResult()
        }
        if (loadRes != "ok") error("Unexpected bundle bootstrap result: $loadRes")
        bundleLoaded = true
        return iso
    }

    private suspend fun ensureTreeSitterGrammarLocked(
        iso: JavaScriptIsolate,
        grammar: String
    ) {
        if (!treeSitterReady) {
            val runtimeBytes = readAssetBytes(TREE_SITTER_RUNTIME_ASSET)
            val runtimeKey = nextDataKey("ts_rt")
            iso.provideNamedData(runtimeKey, runtimeBytes)
            val initRes = withTimeout(INIT_TIMEOUT_MS) {
                iso.evaluateJavaScriptAsync(
                    "globalThis.__pwSyntax.initTreeSitterFromNamedData(${JSONObject.quote(runtimeKey)})"
                ).awaitResult()
            }
            if (initRes != "ok") error("Tree-sitter init failed: $initRes")
            treeSitterReady = true
        }

        if (grammar !in loadedGrammars) {
            val assetPath = "$ASSETS_DIR/tree-sitter-$grammar.wasm"
            val grammarBytes = readAssetBytes(assetPath)
            val grammarKey = nextDataKey("ts_$grammar")
            iso.provideNamedData(grammarKey, grammarBytes)
            val loadRes = withTimeout(INIT_TIMEOUT_MS) {
                iso.evaluateJavaScriptAsync(
                    "globalThis.__pwSyntax.loadGrammarFromNamedData(${JSONObject.quote(grammar)}, ${JSONObject.quote(grammarKey)})"
                ).awaitResult()
            }
            if (loadRes != "ok") error("Tree-sitter grammar '$grammar' load failed: $loadRes")
            loadedGrammars.add(grammar)
        }
    }

    private suspend fun runCheckLocked(
        iso: JavaScriptIsolate,
        relativePath: String,
        content: String,
        explicitGrammar: String?
    ): SyntaxEngineResult {
        val sourceKey = nextDataKey("src")
        val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
        iso.provideNamedData(sourceKey, contentBytes)

        val metaJson = JSONObject().apply {
            put("filePath", relativePath)
            if (explicitGrammar != null) {
                put("grammar", explicitGrammar)
            }
        }.toString()

        val rawResponse = withTimeout(CHECK_TIMEOUT_MS) {
            iso.evaluateJavaScriptAsync(
                "globalThis.__pwSyntax.checkFromNamedData(${JSONObject.quote(metaJson)}, ${JSONObject.quote(sourceKey)})"
            ).awaitResult()
        }

        return parseEngineResponse(rawResponse)
    }

    private fun resetLocked() {
        runCatching { isolate?.close() }
        runCatching { sandbox?.close() }
        isolate = null
        sandbox = null
        bundleLoaded = false
        treeSitterReady = false
        loadedGrammars.clear()
    }

    private fun readAssetBytes(assetPath: String): ByteArray =
        appContext.assets.open(assetPath).use { it.readBytes() }

    private fun nextDataKey(prefix: String): String =
        "${prefix}_${dataKeySeq.incrementAndGet()}"

    companion object {
        private const val TAG = "JsSandboxSyntaxEngine"
        private const val ASSETS_DIR = "syntax"
        private const val BUNDLE_ASSET_PATH = "$ASSETS_DIR/syntax-checker.bundle.js"
        private const val TREE_SITTER_RUNTIME_ASSET = "$ASSETS_DIR/tree-sitter.wasm"
        private const val INIT_TIMEOUT_MS = 20_000L
        private const val CHECK_TIMEOUT_MS = 15_000L

        private val DIRECT_EXECUTOR = Executor { command -> command.run() }

        private val PURE_JS_EXTENSIONS = setOf(
            "js", "mjs", "cjs", "jsx",
            "ts", "mts", "cts", "tsx",
            "php", "phtml",
            "css",
            "html", "htm",
            "yml", "yaml",
            "toml",
            "sql", "ddl", "dml"
        )

        private val EXT_TO_TS_GRAMMAR = mapOf(
            "py" to "python",
            "pyw" to "python",
            "pyi" to "python",
            "c" to "c",
            "h" to "c",
            "cpp" to "cpp",
            "cc" to "cpp",
            "cxx" to "cpp",
            "hpp" to "cpp",
            "hh" to "cpp",
            "hxx" to "cpp",
            "cs" to "c_sharp",
            "java" to "java",
            "kt" to "kotlin",
            "kts" to "kotlin",
            "go" to "go",
            "rs" to "rust",
            "sh" to "bash",
            "bash" to "bash",
            "zsh" to "bash"
        )

        internal fun extractExtension(filePath: String): String {
            val base = filePath.substringAfterLast('/').substringAfterLast('\\').lowercase()
            if (base == "gradlew" || base == ".bashrc" || base == ".zshrc") return "sh"
            return base.substringAfterLast('.', "")
        }

        internal fun parseEngineResponse(rawJson: String): SyntaxEngineResult {
            val obj = JSONObject(rawJson)
            val handled = obj.optBoolean("handled", false)
            if (!handled) {
                return SyntaxEngineResult(
                    handled = false,
                    fallbackReason = obj.optString("reason", "unhandled by JS engine")
                )
            }
            val engine = obj.optString("engine", "js-ast")
            val arr = obj.optJSONArray("issues")
            val issues = mutableListOf<SyntaxIssue>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val line = item.optInt("line", 1).coerceAtLeast(1)
                    val col = item.optInt("column", 1).coerceAtLeast(1)
                    val severity = item.optString("severity", "error")
                    val message = item.optString("message", "Syntax error")
                    val endLine = if (item.has("endLine") && !item.isNull("endLine")) item.optInt("endLine") else null
                    val endCol = if (item.has("endColumn") && !item.isNull("endColumn")) item.optInt("endColumn") else null
                    val code = item.optString("code", "").takeIf { it.isNotBlank() }
                    issues.add(
                        SyntaxIssue(
                            line = line,
                            column = col,
                            severity = severity,
                            message = message,
                            endLine = endLine,
                            endColumn = endCol,
                            code = code
                        )
                    )
                }
            }
            return SyntaxEngineResult(
                handled = true,
                engine = engine,
                issues = issues
            )
        }

        private val BOOTSTRAP_JS = """
            (function() {
              function decodeUtf8(buf) {
                const b = new Uint8Array(buf);
                const out = [];
                let chunk = "";
                let i = 0;
                const n = b.length;
                while (i < n) {
                  const b0 = b[i++];
                  if (b0 < 0x80) chunk += String.fromCharCode(b0);
                  else if ((b0 & 0xe0) === 0xc0 && i < n) chunk += String.fromCharCode(((b0 & 0x1f) << 6) | (b[i++] & 0x3f));
                  else if ((b0 & 0xf0) === 0xe0 && i + 1 < n) chunk += String.fromCharCode(((b0 & 0x0f) << 12) | ((b[i++] & 0x3f) << 6) | (b[i++] & 0x3f));
                  else if ((b0 & 0xf8) === 0xf0 && i + 2 < n) {
                    const cp = (((b0 & 0x07) << 18) | ((b[i++] & 0x3f) << 12) | ((b[i++] & 0x3f) << 6) | (b[i++] & 0x3f)) - 0x10000;
                    chunk += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
                  } else chunk += "\ufffd";
                  if (chunk.length >= 4096) { out.push(chunk); chunk = ""; }
                }
                if (chunk.length > 0) out.push(chunk);
                return out.join("");
              }
              globalThis.__pwLoadBundle = async function(key) {
                const buf = await android.consumeNamedDataAsArrayBuffer(key);
                const src = decodeUtf8(buf);
                (0, eval)(src);
                return "ok";
              };
              return "ok";
            })();
        """.trimIndent()

        private suspend fun <T> ListenableFuture<T>.awaitResult(): T =
            suspendCancellableCoroutine { cont ->
                addListener(
                    {
                        try {
                            cont.resume(get())
                        } catch (ex: ExecutionException) {
                            cont.resumeWithException(ex.cause ?: ex)
                        } catch (ex: Throwable) {
                            cont.resumeWithException(ex)
                        }
                    },
                    DIRECT_EXECUTOR
                )
                cont.invokeOnCancellation { cancel(true) }
            }
    }
}
