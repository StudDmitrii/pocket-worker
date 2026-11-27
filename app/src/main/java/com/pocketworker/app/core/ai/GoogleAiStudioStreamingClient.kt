package com.pocketworker.app.core.ai

import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.storage.AiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Direct Google AI Studio (Gemini Developer API v1beta) streaming SSE client.
 *
 * Uses `POST /v1beta/models/{model}:streamGenerateContent?alt=sse` with `x-goog-api-key`,
 * supports MCP function declarations via `parametersJsonSchema`, skips internal `thought: true`
 * text chunks from visible output, and preserves `thoughtSignature` across multi-turn
 * tool calls required by Gemini 3/4 thinking models.
 */
class GoogleAiStudioStreamingClient(
    private val transport: SseHttpTransport = UrlConnectionSseTransport(),
    private val baseUrl: String = AiProvider.GEMINI.defaultBaseUrl
) : LlmStreamingClient {

    override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val modelSegment = request.model.trim().removePrefix("models/")
        val endpointUrl = "${baseUrl.trimEnd('/')}/models/$modelSegment:streamGenerateContent?alt=sse"
        val headers = mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Accept" to "text/event-stream",
            "x-goog-api-key" to request.apiKey
        )
        val bodyJson = buildRequestBody(request).toString()

        val capturedParts = mutableListOf<JSONObject>()
        var hadToolCalls = false
        var hasThoughtOrSignature = false
        var finishReason: String? = null
        var callCounter = 0

        try {
            transport.postStream(
                url = endpointUrl,
                headers = headers,
                bodyJson = bodyJson,
                proxy = request.proxy
            ) { rawLine ->
                val line = rawLine.trimEnd('\r')
                if (line.isBlank() || line.startsWith(":")) return@postStream
                if (!line.startsWith("data:")) return@postStream
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") return@postStream

                val chunk = runCatching { JSONObject(data) }.getOrNull() ?: return@postStream
                val inlineError = chunk.optJSONObject("error")
                if (inlineError != null) {
                    val code = inlineError.optInt("code", 0).takeIf { it > 0 }
                    val msg = inlineError.optNonNullString("message").ifBlank { AppLocale.current.llmErrGoogleAiStudio }
                    emit(LlmStreamEvent.Error(message = msg, statusCode = code))
                    return@postStream
                }

                val candidates = chunk.optJSONArray("candidates") ?: return@postStream
                val candidate = candidates.optJSONObject(0) ?: return@postStream

                val fr = candidate.optNonNullString("finishReason")
                if (fr.isNotBlank()) {
                    finishReason = fr
                }

                val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return@postStream
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val isThought = part.optBoolean("thought", false)
                    val hasSignature = part.optNonNullString("thoughtSignature").isNotBlank()
                    if (isThought || hasSignature) {
                        hasThoughtOrSignature = true
                    }

                    appendMergedRawPart(capturedParts, part)

                    if (part.has("functionCall") && !part.isNull("functionCall")) {
                        val fc = part.optJSONObject("functionCall")
                        if (fc != null) {
                            val name = fc.optNonNullString("name")
                            if (name.isNotBlank()) {
                                val nativeId = fc.optNonNullString("id").takeIf { it.isNotBlank() }
                                val callId = nativeId
                                    ?: "$SYNTHETIC_CALL_PREFIX${++callCounter}_${UUID.randomUUID().toString().take(8)}"
                                val argsJson = fc.optJSONObject("args")?.toString() ?: "{}"
                                hadToolCalls = true
                                emit(LlmStreamEvent.ToolUseStarted(callId, name, isWebSearch = false))
                                emit(
                                    LlmStreamEvent.ToolUseCompleted(
                                        LlmToolCall(
                                            id = callId,
                                            name = name,
                                            argumentsJson = argsJson
                                        )
                                    )
                                )
                            }
                        }
                    } else if (!isThought && part.has("text") && !part.isNull("text")) {
                        val text = part.optNonNullString("text")
                        if (text.isNotEmpty()) {
                            emit(LlmStreamEvent.TextDelta(text))
                        }
                    }
                }
            }

            if (capturedParts.isNotEmpty() && (hadToolCalls || hasThoughtOrSignature)) {
                val wrapper = JSONObject().apply {
                    put(GEMINI_PARTS_KEY, JSONArray(capturedParts))
                }
                emit(LlmStreamEvent.ReasoningDetailsCaptured(wrapper.toString()))
            }

            emit(LlmStreamEvent.MessageFinished(if (hadToolCalls) "tool_calls" else finishReason))
        } catch (ce: CancellationException) {
            throw ce
        } catch (ex: Throwable) {
            val (msg, code) = formatLlmException(AiProvider.GEMINI, ex)
            emit(LlmStreamEvent.Error(message = msg, statusCode = code))
        }
    }

    internal fun buildRequestBody(request: LlmRequest): JSONObject {
        val cleanMessages = sanitizeConversationHistory(request.messages)
        val contents = JSONArray()

        var idx = 0
        while (idx < cleanMessages.size) {
            when (val msg = cleanMessages[idx]) {
                is LlmMessage.User -> {
                    val userParts = JSONArray().put(JSONObject().put("text", msg.text))
                    appendOrMergeContent(contents, role = "user", parts = userParts)
                    idx++
                }

                is LlmMessage.Assistant -> {
                    val modelParts = resolveModelParts(msg)
                    if (modelParts.length() > 0) {
                        appendOrMergeContent(contents, role = "model", parts = modelParts)
                    }
                    idx++
                }

                is LlmMessage.ToolResult -> {
                    val responseParts = JSONArray()
                    while (idx < cleanMessages.size && cleanMessages[idx] is LlmMessage.ToolResult) {
                        val tr = cleanMessages[idx] as LlmMessage.ToolResult
                        val responseObj = parseToolResultAsObject(tr.contentJson)
                        val fnResp = JSONObject().apply {
                            put("name", tr.toolName)
                            put("response", responseObj)
                            if (tr.toolCallId.isNotBlank() && !tr.toolCallId.startsWith(SYNTHETIC_CALL_PREFIX)) {
                                put("id", tr.toolCallId)
                            }
                        }
                        responseParts.put(JSONObject().put("functionResponse", fnResp))
                        idx++
                    }
                    if (responseParts.length() > 0) {
                        appendOrMergeContent(contents, role = "user", parts = responseParts)
                    }
                }
            }
        }

        return JSONObject().apply {
            if (request.systemPrompt.isNotBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", request.systemPrompt))
                    )
                )
            }
            put("contents", contents)
            if (request.tools.isNotEmpty()) {
                val declarations = JSONArray()
                request.tools.forEach { tool ->
                    declarations.put(
                        JSONObject().apply {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parametersJsonSchema", tool.inputSchema)
                        }
                    )
                }
                put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations)))
                put(
                    "toolConfig",
                    JSONObject().put(
                        "functionCallingConfig",
                        JSONObject().put("mode", "AUTO")
                    )
                )
            }
            put(
                "generationConfig",
                JSONObject().put("maxOutputTokens", request.maxTokens)
            )
        }
    }

    private fun resolveModelParts(msg: LlmMessage.Assistant): JSONArray {
        val preserved = extractPreservedGeminiParts(msg.reasoningDetailsJson)
        if (preserved != null && partsMatchToolCalls(preserved, msg.toolCalls)) {
            return preserved
        }

        val parts = JSONArray()
        if (msg.text.isNotEmpty()) {
            parts.put(JSONObject().put("text", msg.text))
        }
        msg.toolCalls.forEach { call ->
            val argsObj = runCatching { JSONObject(call.argumentsJson.ifBlank { "{}" }) }
                .getOrElse { JSONObject() }
            val fc = JSONObject().apply {
                put("name", call.name)
                put("args", argsObj)
                if (call.id.isNotBlank() && !call.id.startsWith(SYNTHETIC_CALL_PREFIX)) {
                    put("id", call.id)
                }
            }
            parts.put(JSONObject().put("functionCall", fc))
        }
        return parts
    }

    private fun extractPreservedGeminiParts(rawJson: String?): JSONArray? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(rawJson)
            val arr = obj.optJSONArray(GEMINI_PARTS_KEY)
            if (arr != null && arr.length() > 0) arr else null
        }.getOrNull()
    }

    private fun partsMatchToolCalls(parts: JSONArray, toolCalls: List<LlmToolCall>): Boolean {
        val partFnNames = mutableListOf<String>()
        for (i in 0 until parts.length()) {
            val fc = parts.optJSONObject(i)?.optJSONObject("functionCall") ?: continue
            partFnNames.add(fc.optString("name", ""))
        }
        return partFnNames == toolCalls.map { it.name }
    }

    private fun appendMergedRawPart(acc: MutableList<JSONObject>, incoming: JSONObject) {
        val copy = JSONObject(incoming.toString())
        val isFunctionCall = copy.has("functionCall")
        if (isFunctionCall || !copy.has("text")) {
            acc.add(copy)
            return
        }

        val isThought = copy.optBoolean("thought", false)
        val prev = acc.lastOrNull()
        val canMerge = prev != null &&
            !prev.has("functionCall") &&
            prev.has("text") && !prev.isNull("text") &&
            prev.optBoolean("thought", false) == isThought &&
            prev.optNonNullString("thoughtSignature").isEmpty()

        if (canMerge && prev != null) {
            prev.put("text", prev.optNonNullString("text") + copy.optNonNullString("text"))
            val sig = copy.optNonNullString("thoughtSignature")
            if (sig.isNotBlank()) {
                prev.put("thoughtSignature", sig)
            }
        } else {
            acc.add(copy)
        }
    }

    private fun appendOrMergeContent(contents: JSONArray, role: String, parts: JSONArray) {
        val last = if (contents.length() > 0) contents.optJSONObject(contents.length() - 1) else null
        if (last != null && last.optString("role") == role) {
            val existingParts = last.optJSONArray("parts") ?: JSONArray().also { last.put("parts", it) }
            for (i in 0 until parts.length()) {
                existingParts.put(parts.get(i))
            }
        } else {
            contents.put(
                JSONObject().apply {
                    put("role", role)
                    put("parts", parts)
                }
            )
        }
    }

    private fun parseToolResultAsObject(rawJson: String): JSONObject {
        val trimmed = rawJson.trim()
        if (trimmed.startsWith("{")) {
            val parsed = runCatching { JSONObject(trimmed) }.getOrNull()
            if (parsed != null) return parsed
        }
        if (trimmed.startsWith("[")) {
            val parsed = runCatching { JSONArray(trimmed) }.getOrNull()
            if (parsed != null) return JSONObject().put("result", parsed)
        }
        return JSONObject().put("result", trimmed)
    }

    companion object {
        internal const val GEMINI_PARTS_KEY = "_geminiParts"
        private const val SYNTHETIC_CALL_PREFIX = "gemini_call_"
    }
}
