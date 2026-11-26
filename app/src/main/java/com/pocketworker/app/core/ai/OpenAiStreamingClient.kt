package com.pocketworker.app.core.ai

import com.pocketworker.app.core.storage.AiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Streaming client for OpenAI-compatible Chat Completions APIs (`POST /chat/completions`, SSE).
 * Powers both direct OpenAI models and Google Gemini models routed through OpenRouter
 * (preserving OpenRouter `reasoning_details` across multi-turn MCP tool calls).
 */
class OpenAiStreamingClient(
    private val transport: SseHttpTransport = UrlConnectionSseTransport(),
    private val provider: AiProvider = AiProvider.OPENAI,
    private val endpointUrl: String = "${provider.defaultBaseUrl}/chat/completions",
    private val extraHeaders: Map<String, String> = defaultExtraHeadersFor(provider),
    private val useMaxCompletionTokens: Boolean = (provider == AiProvider.OPENAI)
) : LlmStreamingClient {

    private data class ToolCallAccumulator(
        var id: String = "",
        var name: String = "",
        val argsBuffer: StringBuilder = StringBuilder(),
        var startedEmitted: Boolean = false,
        var completedEmitted: Boolean = false
    )

    override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val bodyJson = buildRequestBody(request).toString()
        val headers = LinkedHashMap<String, String>().apply {
            put("Authorization", "Bearer ${request.apiKey}")
            put("Content-Type", "application/json")
            put("Accept", "text/event-stream")
            putAll(extraHeaders)
        }

        val accumulators = sortedMapOf<Int, ToolCallAccumulator>()
        val reasoningDetails = mutableListOf<JSONObject>()
        var finishReason: String? = null
        var finishedEmitted = false
        var reasoningEmitted = false

        suspend fun flushCompletedTools() {
            for ((idx, acc) in accumulators) {
                if (!acc.completedEmitted && acc.name.isNotBlank()) {
                    acc.completedEmitted = true
                    val effectiveId = acc.id.ifBlank { "call_$idx" }
                    val rawArgs = acc.argsBuffer.toString().trim().ifEmpty { "{}" }
                    emit(
                        LlmStreamEvent.ToolUseCompleted(
                            LlmToolCall(
                                id = effectiveId,
                                name = acc.name,
                                argumentsJson = rawArgs
                            )
                        )
                    )
                }
            }
        }

        suspend fun flushReasoningDetails() {
            if (!reasoningEmitted && reasoningDetails.isNotEmpty()) {
                reasoningEmitted = true
                val arr = JSONArray()
                reasoningDetails.forEach { arr.put(it) }
                emit(LlmStreamEvent.ReasoningDetailsCaptured(arr.toString()))
            }
        }

        try {
            transport.postStream(
                url = endpointUrl,
                headers = headers,
                bodyJson = bodyJson,
                proxy = request.proxy
            ) { line ->
                if (!line.startsWith("data:")) return@postStream
                val rawData = line.removePrefix("data:").trim()
                if (rawData.isEmpty()) return@postStream

                if (rawData == "[DONE]") {
                    flushReasoningDetails()
                    flushCompletedTools()
                    if (!finishedEmitted) {
                        finishedEmitted = true
                        emit(LlmStreamEvent.MessageFinished(finishReason))
                    }
                    return@postStream
                }

                val payload = runCatching { JSONObject(rawData) }.getOrNull() ?: return@postStream

                val errorObj = payload.optJSONObject("error")
                if (errorObj != null) {
                    val msg = errorObj.optString("message", "").ifBlank {
                        "Неизвестная ошибка потока ${provider.displayName}"
                    }
                    emit(LlmStreamEvent.Error(message = msg))
                    finishedEmitted = true
                    return@postStream
                }

                val choices = payload.optJSONArray("choices") ?: return@postStream
                if (choices.length() == 0) return@postStream
                val choice = choices.optJSONObject(0) ?: return@postStream

                val reason = choice.optString("finish_reason", "").takeIf { it.isNotBlank() && it != "null" }
                if (reason != null) finishReason = reason

                val delta = choice.optJSONObject("delta")
                if (delta != null) {
                    // Capture OpenRouter reasoning_details blocks (critical for Gemini 3/4 tool calling)
                    val rdArray = delta.optJSONArray("reasoning_details")
                    if (rdArray != null) {
                        mergeReasoningDetailChunks(reasoningDetails, rdArray)
                    }

                    if (delta.has("content") && !delta.isNull("content")) {
                        val content = delta.optString("content", "")
                        if (content.isNotEmpty()) {
                            emit(LlmStreamEvent.TextDelta(content))
                        }
                    }

                    val tcArray = delta.optJSONArray("tool_calls")
                    if (tcArray != null) {
                        for (i in 0 until tcArray.length()) {
                            val tc = tcArray.optJSONObject(i) ?: continue
                            val idx = tc.optInt("index", i)
                            val acc = accumulators.getOrPut(idx) { ToolCallAccumulator() }

                            val idPart = tc.optString("id", "")
                            if (idPart.isNotBlank()) acc.id = idPart

                            val fn = tc.optJSONObject("function")
                            if (fn != null) {
                                val namePart = fn.optString("name", "")
                                if (namePart.isNotBlank()) {
                                    acc.name += namePart
                                }
                                if (!acc.startedEmitted && acc.name.isNotBlank()) {
                                    acc.startedEmitted = true
                                    val effectiveId = acc.id.ifBlank { "call_$idx" }
                                    emit(
                                        LlmStreamEvent.ToolUseStarted(
                                            id = effectiveId,
                                            name = acc.name,
                                            isWebSearch = acc.name.contains("web_search", ignoreCase = true)
                                        )
                                    )
                                }
                                val argChunk = fn.optString("arguments", "")
                                if (argChunk.isNotEmpty()) {
                                    acc.argsBuffer.append(argChunk)
                                    val effectiveId = acc.id.ifBlank { "call_$idx" }
                                    emit(LlmStreamEvent.ToolUseInputDelta(effectiveId, argChunk))
                                }
                            }
                        }
                    }
                }

                if (reason == "tool_calls") {
                    flushReasoningDetails()
                    flushCompletedTools()
                }
            }

            if (!finishedEmitted) {
                flushReasoningDetails()
                flushCompletedTools()
                emit(LlmStreamEvent.MessageFinished(finishReason))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ex: Throwable) {
            val (msg, code) = formatLlmException(provider, ex)
            emit(LlmStreamEvent.Error(message = msg, statusCode = code))
        }
    }

    internal fun buildRequestBody(request: LlmRequest): JSONObject {
        val cleanMessages = sanitizeConversationHistory(request.messages)
        val messagesJson = JSONArray()
        if (request.systemPrompt.isNotBlank()) {
            messagesJson.put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", request.systemPrompt)
                }
            )
        }

        cleanMessages.forEach { msg ->
            when (msg) {
                is LlmMessage.User -> {
                    messagesJson.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", msg.text)
                        }
                    )
                }

                is LlmMessage.Assistant -> {
                    val obj = JSONObject().apply {
                        put("role", "assistant")
                        put("content", if (msg.text.isEmpty() && msg.toolCalls.isNotEmpty()) JSONObject.NULL else msg.text)
                    }
                    if (msg.toolCalls.isNotEmpty()) {
                        val tcArr = JSONArray()
                        msg.toolCalls.forEach { call ->
                            tcArr.put(
                                JSONObject().apply {
                                    put("id", call.id)
                                    put("type", "function")
                                    put(
                                        "function",
                                        JSONObject().apply {
                                            put("name", call.name)
                                            put("arguments", call.argumentsJson)
                                        }
                                    )
                                }
                            )
                        }
                        obj.put("tool_calls", tcArr)
                    }
                    if (!msg.reasoningDetailsJson.isNullOrBlank()) {
                        runCatching { JSONArray(msg.reasoningDetailsJson) }.getOrNull()?.let { rd ->
                            if (rd.length() > 0) {
                                obj.put("reasoning_details", rd)
                            }
                        }
                    }
                    messagesJson.put(obj)
                }

                is LlmMessage.ToolResult -> {
                    messagesJson.put(
                        JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", msg.toolCallId)
                            put("content", msg.contentJson)
                        }
                    )
                }
            }
        }

        val toolsJson = JSONArray()
        request.tools.forEach { tool ->
            toolsJson.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.inputSchema)
                        }
                    )
                }
            )
        }

        return JSONObject().apply {
            put("model", request.model)
            put("stream", true)
            if (useMaxCompletionTokens) {
                put("max_completion_tokens", request.maxTokens)
            } else {
                put("max_tokens", request.maxTokens)
            }
            put("messages", messagesJson)
            if (toolsJson.length() > 0) {
                put("tools", toolsJson)
                put("tool_choice", "auto")
            }
        }
    }

    private fun mergeReasoningDetailChunks(
        acc: MutableList<JSONObject>,
        incoming: JSONArray
    ) {
        for (i in 0 until incoming.length()) {
            val chunk = incoming.optJSONObject(i) ?: continue
            val type = chunk.optString("type", "")
            val id = chunk.optString("id", "")
            val index = chunk.optInt("index", -1)
            val prev = acc.lastOrNull()
            val canMerge = prev != null &&
                prev.optString("type", "") == type &&
                prev.optString("id", "") == id &&
                prev.optInt("index", -1) == index

            if (canMerge && type == "reasoning.text") {
                val mergedText = prev!!.optString("text", "") + chunk.optString("text", "")
                prev.put("text", mergedText)
                val sig = chunk.optString("signature", "")
                if (sig.isNotBlank()) prev.put("signature", sig)
            } else if (canMerge && type == "reasoning.summary") {
                val mergedSummary = prev!!.optString("summary", "") + chunk.optString("summary", "")
                prev.put("summary", mergedSummary)
            } else {
                // Keep encrypted blobs and distinct items completely intact (deep copy)
                acc.add(JSONObject(chunk.toString()))
            }
        }
    }

    companion object {
        fun forOpenRouter(
            transport: SseHttpTransport = UrlConnectionSseTransport()
        ): OpenAiStreamingClient = OpenAiStreamingClient(
            transport = transport,
            provider = AiProvider.OPENROUTER,
            endpointUrl = "${AiProvider.OPENROUTER.defaultBaseUrl}/chat/completions",
            extraHeaders = defaultExtraHeadersFor(AiProvider.OPENROUTER),
            useMaxCompletionTokens = false
        )

        fun forOpenRouterGemini(
            transport: SseHttpTransport = UrlConnectionSseTransport()
        ): OpenAiStreamingClient = forOpenRouter(transport)

        private fun defaultExtraHeadersFor(provider: AiProvider): Map<String, String> =
            if (provider == AiProvider.OPENROUTER) {
                mapOf(
                    "HTTP-Referer" to "https://pocketworker.app",
                    "X-Title" to "PocketWorker"
                )
            } else {
                emptyMap()
            }
    }
}
