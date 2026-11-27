package com.pocketworker.app.core.ai

import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.mcp.McpToolDefinition
import com.pocketworker.app.core.storage.AiProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets

data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

sealed interface LlmMessage {
    data class User(val text: String) : LlmMessage
    data class Assistant(
        val text: String,
        val toolCalls: List<LlmToolCall> = emptyList(),
        val reasoningDetailsJson: String? = null
    ) : LlmMessage

    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val contentJson: String,
        val isError: Boolean
    ) : LlmMessage
}

data class LlmToolSpec(
    val name: String,
    val description: String,
    val inputSchema: JSONObject
) {
    companion object {
        fun fromMcp(def: McpToolDefinition): LlmToolSpec = LlmToolSpec(
            name = def.name,
            description = def.description,
            inputSchema = def.inputSchema
        )
    }
}

data class LlmRequest(
    val model: String,
    val systemPrompt: String,
    val messages: List<LlmMessage>,
    val tools: List<LlmToolSpec>,
    val apiKey: String,
    val maxTokens: Int = 4096,
    val proxy: AiProxyConfig? = null
)

sealed interface LlmStreamEvent {
    data class TextDelta(val text: String) : LlmStreamEvent
    data class ToolUseStarted(
        val id: String,
        val name: String,
        val isWebSearch: Boolean = false
    ) : LlmStreamEvent

    data class ToolUseInputDelta(
        val id: String,
        val partialJson: String
    ) : LlmStreamEvent

    data class ToolUseCompleted(val call: LlmToolCall) : LlmStreamEvent
    data class ReasoningDetailsCaptured(val reasoningDetailsJson: String) : LlmStreamEvent
    data class WebSearchUpdate(
        val id: String,
        val query: String?,
        val finished: Boolean
    ) : LlmStreamEvent

    data class MessageFinished(val stopReason: String?) : LlmStreamEvent
    data class Error(
        val message: String,
        val statusCode: Int? = null
    ) : LlmStreamEvent
}

interface LlmStreamingClient {
    fun streamChat(request: LlmRequest): Flow<LlmStreamEvent>
}

class LlmHttpException(
    val statusCode: Int,
    val responseBody: String
) : IOException("HTTP $statusCode: $responseBody")

interface SseHttpTransport {
    suspend fun postStream(
        url: String,
        headers: Map<String, String>,
        bodyJson: String,
        proxy: AiProxyConfig? = null,
        onLine: suspend (String) -> Unit
    )
}

class UrlConnectionSseTransport(
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 90_000
) : SseHttpTransport {

    override suspend fun postStream(
        url: String,
        headers: Map<String, String>,
        bodyJson: String,
        proxy: AiProxyConfig?,
        onLine: suspend (String) -> Unit
    ) {
        val targetUrl = URL(url)
        val connection = if (proxy != null) {
            AiProxyAuthenticatorRegistry.register(proxy)
            (targetUrl.openConnection(proxy.toJavaProxy()) as HttpURLConnection)
        } else {
            (targetUrl.openConnection() as HttpURLConnection)
        }.apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            doOutput = true
            useCaches = false
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            proxy?.basicAuthorizationHeader()?.let { authHeader ->
                setRequestProperty("Proxy-Authorization", authHeader)
            }
        }

        try {
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(bodyJson)
                writer.flush()
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val errBody = runCatching {
                    val stream = connection.errorStream ?: connection.inputStream
                    stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                throw LlmHttpException(statusCode, errBody)
            }

            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    onLine(line)
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

fun buildAgentSystemPrompt(
    shareWorkspaceContext: Boolean,
    userCustomPrompt: String
): String {
    val base = buildString {
        appendLine("Ты — встроенный AI-агент разработки в мобильном приложении PocketWorker (Android).")
        if (shareWorkspaceContext) {
            appendLine(
                "Можешь посмотреть контекст запроса используя инструмент workspace_get_context, " +
                    "чтобы сразу узнать открытый проект, репозиторий или файл и приступить к выполнению быстрее."
            )
        } else {
            appendLine(
                "Автоматическая передача контекста открытого окна отключена пользователем в настройках — " +
                    "при необходимости используй workspace_list_projects или уточняй проект у пользователя."
            )
        }
        appendLine("Тебе доступны локальные MCP-инструменты для управления проектами, репозиториями, файлами, структурой папок, виртуальным Git CLI (git_exec) и офлайн-проверками.")
        appendLine("Правила работы:")
        appendLine("1. Отвечай на языке пользователя, чётко и по делу.")
        appendLine("2. Перед изменением существующих файлов читай нужные строки через fs_read_file.")
        appendLine("3. Если инструмент вернул ошибку с recoverableByAgent=true — используй альтернативный путь из ответа. Если recoverableByAgent=false — не повторяй команду, а честно объясни ограничение.")
        appendLine("4. Не выдумывай результаты операций: для создания проектов, правок и Git-действий всегда вызывай соответствующие инструменты.")
    }.trimEnd()

    val cleanCustom = userCustomPrompt.trim()
    return if (cleanCustom.isNotEmpty()) {
        "$base\n$cleanCustom"
    } else {
        base
    }
}

internal fun formatLlmException(provider: AiProvider, error: Throwable): Pair<String, Int?> {
    val s = AppLocale.current
    val providerLabel = provider.displayName
    return when (error) {
        is LlmHttpException -> {
            val apiMsg = extractApiErrorMessage(error.responseBody)
            val human = when (error.statusCode) {
                401 -> s.llmErrHttp401(providerLabel)
                403 -> s.llmErrHttp403(providerLabel)
                404 -> s.llmErrHttp404(providerLabel, apiMsg.orEmpty())
                407 -> s.llmErrHttp407
                429 -> s.llmErrHttp429(providerLabel, apiMsg.orEmpty())
                in 500..599 -> s.llmErrHttp5xx(providerLabel, error.statusCode)
                else -> s.llmErrHttpGeneric(providerLabel, error.statusCode, apiMsg ?: error.responseBody.take(240))
            }
            human to error.statusCode
        }

        is UnknownHostException ->
            s.llmErrUnknownHost(providerLabel) to null

        is SocketTimeoutException ->
            s.llmErrTimeout(providerLabel) to null

        else -> {
            val raw = error.localizedMessage ?: error.javaClass.simpleName
            val msg = if ("407" in raw || "proxy" in raw.lowercase()) {
                s.llmErrProxyConnect(providerLabel)
            } else {
                s.llmErrGenericConnect(providerLabel, raw)
            }
            msg to null
        }
    }
}

internal fun sanitizeConversationHistory(messages: List<LlmMessage>): List<LlmMessage> {
    if (messages.isEmpty()) return emptyList()
    val out = mutableListOf<LlmMessage>()
    var i = 0
    while (i < messages.size) {
        when (val msg = messages[i]) {
            is LlmMessage.User -> {
                if (msg.text.isNotBlank()) {
                    out.add(msg)
                }
                i++
            }

            is LlmMessage.Assistant -> {
                if (msg.toolCalls.isEmpty()) {
                    if (msg.text.isNotBlank()) {
                        out.add(msg)
                    }
                    i++
                } else {
                    out.add(msg)
                    i++
                    val expectedIds = msg.toolCalls.map { it.id }.toSet()
                    val collectedById = linkedMapOf<String, LlmMessage.ToolResult>()
                    while (i < messages.size && messages[i] is LlmMessage.ToolResult) {
                        val tr = messages[i] as LlmMessage.ToolResult
                        if (tr.toolCallId in expectedIds && !collectedById.containsKey(tr.toolCallId)) {
                            collectedById[tr.toolCallId] = tr
                        }
                        i++
                    }
                    // Ensure every tool_call has a matching tool_result in exact order (required by Claude & OpenAI)
                    for (call in msg.toolCalls) {
                        val existing = collectedById[call.id]
                        if (existing != null) {
                            out.add(existing)
                        } else {
                            out.add(
                                LlmMessage.ToolResult(
                                    toolCallId = call.id,
                                    toolName = call.name,
                                    contentJson = """{"ok":false,"error":{"errorCode":"USER_REJECTED","message":"Выполнение было прервано."}}""",
                                    isError = true
                                )
                            )
                        }
                    }
                }
            }

            is LlmMessage.ToolResult -> {
                // Skip orphan tool results that don't follow a matching Assistant(toolCalls)
                i++
            }
        }
    }
    return out
}

/**
 * Null-safe string reader for [JSONObject].
 * On Android's `org.json.JSONObject`, `optString(key, "")` returns the literal `"null"` when the
 * JSON value is explicitly `null`; checking [JSONObject.isNull] prevents `"null"` corruption.
 */
internal fun JSONObject.optNonNullString(key: String, fallback: String = ""): String =
    if (has(key) && !isNull(key)) optString(key, fallback) else fallback

internal fun extractApiErrorMessage(rawBody: String): String? {
    val trimmed = rawBody.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        val json = when {
            trimmed.startsWith("[") -> JSONArray(trimmed).optJSONObject(0) ?: return null
            else -> JSONObject(trimmed)
        }
        json.optJSONObject("error")?.optNonNullString("message")?.takeIf { it.isNotBlank() }
            ?: json.optNonNullString("message").takeIf { it.isNotBlank() }
    }.getOrNull()
}
