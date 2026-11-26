package com.pocketworker.app.core.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAiStudioStreamingClientTest {

    @Test
    fun `streams text deltas, skips internal thought chunks, captures thoughtSignature, and replays verbatim on tool follow-up`() = runTest {
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        var capturedBody = ""

        val sseLines = listOf(
            """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Думаю про себя...","thought":true}]}}]}""",
            """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Проверяю проект."}]}}]}""",
            """data: {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"workspace_list_projects","args":{}},"thoughtSignature":"sig_enc_999"}]},"finishReason":"STOP"}]}"""
        )

        val transport = object : SseHttpTransport {
            override suspend fun postStream(
                url: String,
                headers: Map<String, String>,
                bodyJson: String,
                proxy: AiProxyConfig?,
                onLine: suspend (String) -> Unit
            ) {
                capturedUrl = url
                capturedHeaders = headers
                capturedBody = bodyJson
                sseLines.forEach { onLine(it) }
            }
        }

        val client = GoogleAiStudioStreamingClient(transport = transport)
        val events = client.streamChat(
            LlmRequest(
                model = "gemini-4-neon",
                systemPrompt = "Ты помощник.",
                messages = listOf(LlmMessage.User("Покажи проекты")),
                tools = listOf(
                    LlmToolSpec(
                        name = "workspace_list_projects",
                        description = "Возвращает список проектов",
                        inputSchema = JSONObject("""{"type":"object","properties":{},"additionalProperties":false}""")
                    )
                ),
                apiKey = "AIzaSy-test-key"
            )
        ).toList()

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-4-neon:streamGenerateContent?alt=sse",
            capturedUrl
        )
        assertEquals("AIzaSy-test-key", capturedHeaders["x-goog-api-key"])

        val reqJson = JSONObject(capturedBody)
        assertEquals("Ты помощник.", reqJson.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val fnDecl = reqJson.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").getJSONObject(0)
        assertEquals("workspace_list_projects", fnDecl.getString("name"))
        assertTrue(fnDecl.has("parametersJsonSchema"))
        assertFalse(fnDecl.getJSONObject("parametersJsonSchema").getBoolean("additionalProperties"))

        // Internal thought text must NOT be emitted as visible TextDelta
        val textDeltas = events.filterIsInstance<LlmStreamEvent.TextDelta>().joinToString("") { it.text }
        assertEquals("Проверяю проект.", textDeltas)

        val toolCall = events.filterIsInstance<LlmStreamEvent.ToolUseCompleted>().single().call
        assertEquals("workspace_list_projects", toolCall.name)
        assertEquals("{}", toolCall.argumentsJson)

        val reasoning = events.filterIsInstance<LlmStreamEvent.ReasoningDetailsCaptured>().single()
        assertTrue(reasoning.reasoningDetailsJson.contains("sig_enc_999"))
        assertEquals("tool_calls", events.filterIsInstance<LlmStreamEvent.MessageFinished>().single().stopReason)

        // Follow-up request replays the exact preserved model parts (including thoughtSignature) + functionResponse
        val followUpBody = client.buildRequestBody(
            LlmRequest(
                model = "gemini-4-neon",
                systemPrompt = "Ты помощник.",
                messages = listOf(
                    LlmMessage.User("Покажи проекты"),
                    LlmMessage.Assistant(
                        text = "Проверяю проект.",
                        toolCalls = listOf(toolCall),
                        reasoningDetailsJson = reasoning.reasoningDetailsJson
                    ),
                    LlmMessage.ToolResult(
                        toolCallId = toolCall.id,
                        toolName = "workspace_list_projects",
                        contentJson = """{"ok":true,"data":{"projects":["Alpha"]}}""",
                        isError = false
                    )
                ),
                tools = emptyList(),
                apiKey = "AIzaSy-test-key"
            )
        )

        val contents = followUpBody.getJSONArray("contents")
        assertEquals(3, contents.length())

        val modelTurn = contents.getJSONObject(1)
        assertEquals("model", modelTurn.getString("role"))
        val modelParts = modelTurn.getJSONArray("parts")
        val replayedFcPart = (0 until modelParts.length())
            .map { modelParts.getJSONObject(it) }
            .first { it.has("functionCall") }
        assertEquals("sig_enc_999", replayedFcPart.getString("thoughtSignature"))
        assertEquals("workspace_list_projects", replayedFcPart.getJSONObject("functionCall").getString("name"))

        val toolTurn = contents.getJSONObject(2)
        assertEquals("user", toolTurn.getString("role"))
        val fnResp = toolTurn.getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse")
        assertEquals("workspace_list_projects", fnResp.getString("name"))
        assertTrue(fnResp.getJSONObject("response").getBoolean("ok"))
        // Synthetic local IDs must NOT be echoed back as Google native functionResponse.id
        assertFalse(fnResp.has("id"))
    }

    @Test
    fun `parses array-wrapped Google AI Studio HTTP error messages clearly`() = runTest {
        val transport = object : SseHttpTransport {
            override suspend fun postStream(
                url: String,
                headers: Map<String, String>,
                bodyJson: String,
                proxy: AiProxyConfig?,
                onLine: suspend (String) -> Unit
            ) {
                throw LlmHttpException(
                    400,
                    """[{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}]"""
                )
            }
        }

        val client = GoogleAiStudioStreamingClient(transport = transport)
        val events = client.streamChat(
            LlmRequest(
                model = "gemini-3.5-flash",
                systemPrompt = "",
                messages = listOf(LlmMessage.User("Hi")),
                tools = emptyList(),
                apiKey = "bad-key"
            )
        ).toList()

        val err = events.single() as LlmStreamEvent.Error
        assertEquals(400, err.statusCode)
        assertTrue(err.message.contains("API key not valid"))
    }
}
