package com.pocketworker.app.core.ai

import com.pocketworker.app.core.storage.AiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Streaming client for the Anthropic Messages API (`POST /v1/messages`, SSE).
 * Supports text deltas, parallel MCP tool_use calls, and server-side web_search status blocks.
 */
class AnthropicStreamingClient(
    private val transport: SseHttpTransport = UrlConnectionSseTransport(),
    private val endpointUrl: String = "${AiProvider.ANTHROPIC.defaultBaseUrl}/v1/messages"
) : LlmStreamingClient {

    private data class ActiveBlock(
        val type: String,
        val id: String = "",
        val name: String = "",
        val inputBuffer: StringBuilder = StringBuilder(),
        val isServerTool: Boolean = false
    )

    override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val bodyJson = buildRequestBody(request).toString()
        val headers = mapOf(
            "x-api-key" to request.apiKey,
            "anthropic-version" to ANTHROPIC_VERSION,
            "content-type" to "application/json",
            "accept" to "text/event-stream"
        )

        val blocks = mutableMapOf<Int, ActiveBlock>()
        var currentEventName: String? = null
        var stopReason: String? = null
        var finishedEmitted = false

        try {
            transport.postStream(
                url = endpointUrl,
                headers = headers,
                bodyJson = bodyJson,
                proxy = request.proxy
            ) { line ->
                when {
                    line.isBlank() -> {
                        currentEventName = null
                    }
                    line.startsWith("event:") -> {
                        currentEventName = line.removePrefix("event:").trim()
                    }
                    line.startsWith("data:") -> {
                        val rawData = line.removePrefix("data:").trim()
                        if (rawData.isEmpty() || rawData == "[DONE]") return@postStream
                        val payload = runCatching { JSONObject(rawData) }.getOrNull() ?: return@postStream
                        val eventType = payload.optString("type", currentEventName.orEmpty())

                        when (eventType) {
                            "content_block_start" -> {
                                val index = payload.optInt("index", -1)
                                val block = payload.optJSONObject("content_block") ?: return@postStream
                                val blockType = block.optString("type", "")
                                when (blockType) {
                                    "text" -> {
                                        blocks[index] = ActiveBlock(type = "text")
                                        val initialText = block.optString("text", "")
                                        if (initialText.isNotEmpty()) {
                                            emit(LlmStreamEvent.TextDelta(initialText))
                                        }
                                    }
                                    "tool_use" -> {
                                        val id = block.optString("id", "tool_$index")
                                        val name = block.optString("name", "")
                                        val initialInput = block.optJSONObject("input")
                                        val buf = StringBuilder()
                                        if (initialInput != null && initialInput.length() > 0) {
                                            buf.append(initialInput.toString())
                                        }
                                        blocks[index] = ActiveBlock(
                                            type = "tool_use",
                                            id = id,
                                            name = name,
                                            inputBuffer = buf
                                        )
                                        emit(LlmStreamEvent.ToolUseStarted(id = id, name = name, isWebSearch = false))
                                    }
                                    "server_tool_use" -> {
                                        val id = block.optString("id", "server_tool_$index")
                                        val name = block.optString("name", "web_search")
                                        val isWeb = name.contains("search", ignoreCase = true)
                                        blocks[index] = ActiveBlock(
                                            type = "server_tool_use",
                                            id = id,
                                            name = name,
                                            isServerTool = true
                                        )
                                        emit(LlmStreamEvent.ToolUseStarted(id = id, name = name, isWebSearch = isWeb))
                                    }
                                    else -> {
                                        blocks[index] = ActiveBlock(type = blockType)
                                    }
                                }
                            }

                            "content_block_delta" -> {
                                val index = payload.optInt("index", -1)
                                val delta = payload.optJSONObject("delta") ?: return@postStream
                                val deltaType = delta.optString("type", "")
                                when (deltaType) {
                                    "text_delta" -> {
                                        val text = delta.optString("text", "")
                                        if (text.isNotEmpty()) {
                                            emit(LlmStreamEvent.TextDelta(text))
                                        }
                                    }
                                    "input_json_delta" -> {
                                        val partial = delta.optString("partial_json", "")
                                        val active = blocks[index]
                                        if (active != null && partial.isNotEmpty()) {
                                            active.inputBuffer.append(partial)
                                            emit(LlmStreamEvent.ToolUseInputDelta(active.id, partial))
                                        }
                                    }
                                }
                            }

                            "content_block_stop" -> {
                                val index = payload.optInt("index", -1)
                                val active = blocks.remove(index) ?: return@postStream
                                when (active.type) {
                                    "tool_use" -> {
                                        val rawArgs = active.inputBuffer.toString().trim().ifEmpty { "{}" }
                                        emit(
                                            LlmStreamEvent.ToolUseCompleted(
                                                LlmToolCall(
                                                    id = active.id,
                                                    name = active.name,
                                                    argumentsJson = rawArgs
                                                )
                                            )
                                        )
                                    }
                                    "server_tool_use" -> {
                                        val query = runCatching {
                                            JSONObject(active.inputBuffer.toString()).optString("query", "").takeIf { it.isNotBlank() }
                                        }.getOrNull()
                                        emit(LlmStreamEvent.WebSearchUpdate(active.id, query, finished = true))
                                    }
                                }
                            }

                            "message_delta" -> {
                                val delta = payload.optJSONObject("delta")
                                val reason = delta?.optString("stop_reason", "")?.takeIf { it.isNotBlank() && it != "null" }
                                if (reason != null) stopReason = reason
                            }

                            "message_stop" -> {
                                finishedEmitted = true
                                emit(LlmStreamEvent.MessageFinished(stopReason))
                            }

                            "error" -> {
                                val errObj = payload.optJSONObject("error")
                                val msg = errObj?.optString("message", "").orEmpty()
                                    .ifBlank { "Неизвестная ошибка потока Claude" }
                                emit(LlmStreamEvent.Error(message = msg))
                                finishedEmitted = true
                            }
                        }
                    }
                }
            }

            if (!finishedEmitted) {
                emit(LlmStreamEvent.MessageFinished(stopReason))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ex: Throwable) {
            val (msg, code) = formatLlmException(AiProvider.ANTHROPIC, ex)
            emit(LlmStreamEvent.Error(message = msg, statusCode = code))
        }
    }

    internal fun buildRequestBody(request: LlmRequest): JSONObject {
        val cleanMessages = sanitizeConversationHistory(request.messages)
        val messagesJson = JSONArray()

        fun appendUserBlocks(newBlocks: List<JSONObject>) {
            if (newBlocks.isEmpty()) return
            val lastIdx = messagesJson.length() - 1
            if (lastIdx >= 0 && messagesJson.getJSONObject(lastIdx).optString("role") == "user") {
                val prev = messagesJson.getJSONObject(lastIdx)
                val existingContent = prev.get("content")
                val arr = when (existingContent) {
                    is JSONArray -> existingContent
                    is String -> JSONArray().put(
                        JSONObject().put("type", "text").put("text", existingContent)
                    )
                    else -> JSONArray()
                }
                newBlocks.forEach { arr.put(it) }
                prev.put("content", arr)
            } else if (newBlocks.size == 1 && newBlocks[0].optString("type") == "text") {
                messagesJson.put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", newBlocks[0].getString("text"))
                    }
                )
            } else {
                val arr = JSONArray()
                newBlocks.forEach { arr.put(it) }
                messagesJson.put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", arr)
                    }
                )
            }
        }

        var i = 0
        while (i < cleanMessages.size) {
            when (val msg = cleanMessages[i]) {
                is LlmMessage.User -> {
                    appendUserBlocks(listOf(JSONObject().put("type", "text").put("text", msg.text)))
                    i++
                }

                is LlmMessage.Assistant -> {
                    val contentBlocks = JSONArray()
                    if (msg.text.isNotEmpty()) {
                        contentBlocks.put(
                            JSONObject().apply {
                                put("type", "text")
                                put("text", msg.text)
                            }
                        )
                    }
                    msg.toolCalls.forEach { call ->
                        val parsedInput = runCatching { JSONObject(call.argumentsJson) }.getOrElse { JSONObject() }
                        contentBlocks.put(
                            JSONObject().apply {
                                put("type", "tool_use")
                                put("id", call.id)
                                put("name", call.name)
                                put("input", parsedInput)
                            }
                        )
                    }
                    if (contentBlocks.length() > 0) {
                        messagesJson.put(
                            JSONObject().apply {
                                put("role", "assistant")
                                put("content", contentBlocks)
                            }
                        )
                    }
                    i++
                }

                is LlmMessage.ToolResult -> {
                    // Anthropic requires all tool_result blocks for an assistant turn in a single user message.
                    val resultBlocks = mutableListOf<JSONObject>()
                    while (i < cleanMessages.size && cleanMessages[i] is LlmMessage.ToolResult) {
                        val tr = cleanMessages[i] as LlmMessage.ToolResult
                        resultBlocks.add(
                            JSONObject().apply {
                                put("type", "tool_result")
                                put("tool_use_id", tr.toolCallId)
                                put("content", tr.contentJson)
                                if (tr.isError) put("is_error", true)
                            }
                        )
                        i++
                    }
                    appendUserBlocks(resultBlocks)
                }
            }
        }

        val toolsJson = JSONArray()
        request.tools.forEach { tool ->
            toolsJson.put(
                JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("input_schema", tool.inputSchema)
                }
            )
        }

        return JSONObject().apply {
            put("model", request.model)
            put("max_tokens", request.maxTokens)
            put("stream", true)
            if (request.systemPrompt.isNotBlank()) {
                put("system", request.systemPrompt)
            }
            put("messages", messagesJson)
            if (toolsJson.length() > 0) {
                put("tools", toolsJson)
            }
        }
    }

    companion object {
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
