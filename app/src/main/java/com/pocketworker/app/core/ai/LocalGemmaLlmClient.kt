package com.pocketworker.app.core.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * On-device [LlmStreamingClient] powered by Google LiteRT-LM (`litertlm-android`),
 * running local Gemma 4 `.litertlm` models with memory-safe CPU (XNNPACK) default
 * and optional GPU (OpenCL) acceleration, plus manual MCP tool-calling (`automaticToolCalling = false`).
 */
class LocalGemmaLlmClient(
    private val modelManager: LocalModelManager,
    private val cacheDir: File,
    private val useGpuProvider: () -> Boolean = { false },
    private val cpuMaxNumTokens: Int = DEFAULT_CPU_MAX_TOKENS,
    private val gpuMaxNumTokens: Int = DEFAULT_GPU_MAX_TOKENS
) : LlmStreamingClient {

    private val engineMutex = Mutex()
    @Volatile
    private var cachedModelId: String? = null
    @Volatile
    private var cachedModelPath: String? = null
    @Volatile
    private var cachedUseGpu: Boolean? = null
    private var cachedEngine: Engine? = null

    fun isModelLoaded(modelId: String): Boolean {
        return cachedEngine != null &&
            cachedModelId == modelId &&
            cachedUseGpu == useGpuProvider()
    }

    fun releaseEngine(onlyModelId: String? = null) {
        synchronized(this) {
            if (onlyModelId != null && cachedModelId != onlyModelId) return
            runCatching { cachedEngine?.close() }
            cachedEngine = null
            cachedModelId = null
            cachedModelPath = null
            cachedUseGpu = null
        }
    }

    override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val modelFile = modelManager.getDownloadedModelFile(request.model)
        if (modelFile == null) {
            emit(
                LlmStreamEvent.Error(
                    message = "Локальная модель «${request.model}» не скачана. Откройте «Настройки → Агент» и нажмите «Скачать модель».",
                    statusCode = 404
                )
            )
            return@flow
        }

        val sanitized = sanitizeConversationHistory(request.messages)
        val convertedMessages = buildLiteRtMessages(sanitized)
        if (convertedMessages.isEmpty()) {
            emit(LlmStreamEvent.Error("Пустая история сообщений для локальной модели."))
            return@flow
        }

        val initialMessages = convertedMessages.dropLast(1)
        val latestMessage = convertedMessages.last()
        val toolProviders = buildOpenApiToolProviders(request.tools)

        engineMutex.withLock {
            val engine = try {
                getOrInitEngineLocked(request.model, modelFile)
            } catch (ex: Throwable) {
                emit(
                    LlmStreamEvent.Error(
                        message = "Не удалось загрузить локальную модель Gemma 4: ${ex.localizedMessage ?: ex.javaClass.simpleName}. " +
                            "Убедитесь, что на устройстве достаточно свободной ОЗУ."
                    )
                )
                return@flow
            }

            val conversation = try {
                engine.createConversation(
                    ConversationConfig(
                        systemInstruction = request.systemPrompt
                            .takeIf { it.isNotBlank() }
                            ?.let { Contents.of(it) },
                        initialMessages = initialMessages,
                        tools = toolProviders,
                        automaticToolCalling = false
                    )
                )
            } catch (ex: Throwable) {
                emit(
                    LlmStreamEvent.Error(
                        message = "Ошибка инициализации диалога локальной модели: ${ex.localizedMessage ?: ex.javaClass.simpleName}"
                    )
                )
                return@flow
            }

            var hadToolCalls = false
            try {
                conversation.sendMessageAsync(latestMessage).collect { chunk ->
                    chunk.contents.contents.forEach { content ->
                        if (content is Content.Text && content.text.isNotEmpty()) {
                            emit(LlmStreamEvent.TextDelta(content.text))
                        }
                    }
                    chunk.toolCalls.forEach { tc ->
                        hadToolCalls = true
                        val callId = "local_${UUID.randomUUID().toString().replace("-", "").take(10)}"
                        val argsJson = mapToJsonObject(tc.arguments).toString()
                        emit(LlmStreamEvent.ToolUseStarted(callId, tc.name, isWebSearch = false))
                        emit(
                            LlmStreamEvent.ToolUseCompleted(
                                LlmToolCall(
                                    id = callId,
                                    name = tc.name,
                                    argumentsJson = argsJson
                                )
                            )
                        )
                    }
                }
                emit(LlmStreamEvent.MessageFinished(if (hadToolCalls) "tool_calls" else "stop"))
            } catch (ce: CancellationException) {
                runCatching { conversation.cancelProcess() }
                throw ce
            } catch (ex: Throwable) {
                emit(
                    LlmStreamEvent.Error(
                        message = "Ошибка генерации локальной модели: ${ex.localizedMessage ?: ex.javaClass.simpleName}"
                    )
                )
            } finally {
                runCatching { conversation.close() }
            }
        }
    }

    private fun getOrInitEngineLocked(modelId: String, modelFile: File): Engine {
        val path = modelFile.absolutePath
        val preferGpu = useGpuProvider()
        synchronized(this) {
            val existing = cachedEngine
            if (existing != null &&
                cachedModelId == modelId &&
                cachedModelPath == path &&
                cachedUseGpu == preferGpu
            ) {
                return existing
            }
            runCatching { existing?.close() }
            cachedEngine = null
            cachedModelId = null
            cachedModelPath = null
            cachedUseGpu = null
        }

        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // Default to CPU (XNNPACK): dynamic KV-cache uses ~2 GB less RAM and avoids OpenCL/Mali SIGABRT crashes.
        // When user explicitly enables GPU, keep maxNumTokens tight (3072) to prevent static VRAM OOM, with CPU fallback.
        val initializedEngine = if (preferGpu) {
            runCatching {
                Engine(
                    EngineConfig(
                        modelPath = path,
                        backend = Backend.GPU(),
                        maxNumTokens = gpuMaxNumTokens,
                        cacheDir = cacheDir.absolutePath
                    )
                ).apply { initialize() }
            }.getOrElse {
                Engine(
                    EngineConfig(
                        modelPath = path,
                        backend = Backend.CPU(),
                        maxNumTokens = cpuMaxNumTokens,
                        cacheDir = cacheDir.absolutePath
                    )
                ).apply { initialize() }
            }
        } else {
            Engine(
                EngineConfig(
                    modelPath = path,
                    backend = Backend.CPU(),
                    maxNumTokens = cpuMaxNumTokens,
                    cacheDir = cacheDir.absolutePath
                )
            ).apply { initialize() }
        }

        synchronized(this) {
            cachedEngine = initializedEngine
            cachedModelId = modelId
            cachedModelPath = path
            cachedUseGpu = preferGpu
        }
        return initializedEngine
    }

    companion object {
        private const val DEFAULT_CPU_MAX_TOKENS = 4096
        private const val DEFAULT_GPU_MAX_TOKENS = 3072
        private const val MAX_LOCAL_TOOL_DESC_CHARS = 90

        internal fun buildOpenApiToolSchemaJson(spec: LlmToolSpec): String {
            return JSONObject().apply {
                put("name", spec.name)
                put("description", compactToolDescription(spec.description))
                put("parameters", compactJsonSchema(spec.inputSchema))
            }.toString()
        }

        private fun compactToolDescription(raw: String): String {
            val cleaned = raw.trim().replace(Regex("\\s+"), " ")
            if (cleaned.length <= MAX_LOCAL_TOOL_DESC_CHARS) return cleaned
            val firstSentence = cleaned.substringBefore(". ").trim()
            val candidate = if (firstSentence.length in 20..MAX_LOCAL_TOOL_DESC_CHARS) {
                firstSentence
            } else {
                cleaned.take(MAX_LOCAL_TOOL_DESC_CHARS - 1).substringBeforeLast(' ', cleaned.take(MAX_LOCAL_TOOL_DESC_CHARS - 1)).trimEnd(',', ';', ':') + "…"
            }
            return candidate
        }

        private fun compactJsonSchema(schema: JSONObject): JSONObject {
            val out = JSONObject()
            schema.optString("type", "").takeIf { it.isNotEmpty() }?.let { out.put("type", it) }
            schema.optJSONArray("required")?.takeIf { it.length() > 0 }?.let { out.put("required", it) }
            schema.optJSONArray("enum")?.takeIf { it.length() > 0 }?.let { out.put("enum", it) }
            schema.optJSONObject("items")?.let { out.put("items", compactJsonSchema(it)) }
            schema.optJSONObject("properties")?.let { props ->
                val compactProps = JSONObject()
                props.keys().forEach { key ->
                    val propSchema = props.optJSONObject(key)
                    if (propSchema != null) {
                        compactProps.put(key, compactJsonSchema(propSchema))
                    }
                }
                out.put("properties", compactProps)
            }
            return out
        }

        internal fun buildOpenApiToolProviders(tools: List<LlmToolSpec>): List<ToolProvider> {
            return tools.map { spec ->
                val schemaJson = buildOpenApiToolSchemaJson(spec)
                tool(
                    object : OpenApiTool {
                        override fun getToolDescriptionJsonString(): String = schemaJson
                        override fun execute(paramsJsonString: String): String = "{}"
                    }
                )
            }
        }

        internal fun buildLiteRtMessages(history: List<LlmMessage>): List<Message> {
            val out = mutableListOf<Message>()
            var idx = 0
            while (idx < history.size) {
                when (val msg = history[idx]) {
                    is LlmMessage.User -> {
                        out.add(Message.user(msg.text))
                        idx++
                    }

                    is LlmMessage.Assistant -> {
                        if (msg.toolCalls.isEmpty()) {
                            out.add(Message.model(msg.text))
                        } else {
                            val contents = if (msg.text.isNotEmpty()) {
                                Contents.of(msg.text)
                            } else {
                                Contents.of(emptyList())
                            }
                            val calls = msg.toolCalls.map { c ->
                                ToolCall(
                                    name = c.name,
                                    arguments = jsonStringToPlainMap(c.argumentsJson)
                                )
                            }
                            out.add(Message.model(contents = contents, toolCalls = calls))
                        }
                        idx++
                    }

                    is LlmMessage.ToolResult -> {
                        val responses = mutableListOf<Content.ToolResponse>()
                        while (idx < history.size && history[idx] is LlmMessage.ToolResult) {
                            val tr = history[idx] as LlmMessage.ToolResult
                            responses.add(
                                Content.ToolResponse(
                                    name = tr.toolName,
                                    response = jsonStringToPlainValue(tr.contentJson)
                                )
                            )
                            idx++
                        }
                        out.add(Message.tool(Contents.of(responses)))
                    }
                }
            }
            return out
        }

        internal fun jsonStringToPlainMap(rawJson: String): Map<String, Any?> {
            val obj = runCatching { JSONObject(rawJson.ifBlank { "{}" }) }.getOrElse { JSONObject() }
            return jsonObjectToPlainMap(obj)
        }

        internal fun jsonStringToPlainValue(rawJson: String): Any {
            val trimmed = rawJson.trim()
            if (trimmed.startsWith("{")) {
                val parsed = runCatching { jsonObjectToPlainMap(JSONObject(trimmed)) }.getOrNull()
                if (parsed != null) return parsed
            }
            if (trimmed.startsWith("[")) {
                val parsed = runCatching { jsonArrayToPlainList(JSONArray(trimmed)) }.getOrNull()
                if (parsed != null) return parsed
            }
            return mapOf("result" to trimmed)
        }

        private fun jsonObjectToPlainMap(obj: JSONObject): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            obj.keys().forEach { key ->
                map[key] = convertJsonValue(obj.opt(key))
            }
            return map
        }

        private fun jsonArrayToPlainList(arr: JSONArray): List<Any?> {
            return (0 until arr.length()).map { idx -> convertJsonValue(arr.opt(idx)) }
        }

        private fun convertJsonValue(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> jsonObjectToPlainMap(value)
            is JSONArray -> jsonArrayToPlainList(value)
            else -> value
        }

        internal fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
            val out = JSONObject()
            map.forEach { (k, v) -> out.put(k, toJsonValue(v)) }
            return out
        }

        private fun toJsonValue(value: Any?): Any = when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (k, v) ->
                    if (k != null) put(k.toString(), toJsonValue(v))
                }
            }
            is Collection<*> -> JSONArray().apply {
                value.forEach { item -> put(toJsonValue(item)) }
            }
            is Array<*> -> JSONArray().apply {
                value.forEach { item -> put(toJsonValue(item)) }
            }
            is Number, is Boolean, is String -> value
            else -> value.toString()
        }
    }
}
