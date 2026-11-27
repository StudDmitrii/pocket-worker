package com.pocketworker.app.core.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiStreamingClientTest {

    @Test
    fun `builds valid OpenAI Chat Completions payload with system message, tools, and tool results`() {
        val client = OpenAiStreamingClient()
        val req = LlmRequest(
            model = "gpt-5",
            systemPrompt = "Context hint\nUser prompt",
            messages = listOf(
                LlmMessage.User("Создай проект"),
                LlmMessage.Assistant(
                    text = "",
                    toolCalls = listOf(LlmToolCall("call_1", "workspace_create_project", """{"name":"Alpha"}"""))
                ),
                LlmMessage.ToolResult("call_1", "workspace_create_project", """{"ok":true}""", isError = false)
            ),
            tools = listOf(
                LlmToolSpec(
                    name = "workspace_create_project",
                    description = "Creates a project",
                    inputSchema = JSONObject().put("type", "object").put("properties", JSONObject())
                )
            ),
            apiKey = "sk-openai-key"
        )

        val body = client.buildRequestBody(req)
        assertEquals("gpt-5", body.getString("model"))
        assertTrue(body.getBoolean("stream"))
        assertEquals(4096, body.getInt("max_completion_tokens"))

        val messages = body.getJSONArray("messages")
        assertEquals(4, messages.length()) // system + user + assistant(tool_calls) + tool
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals("call_1", messages.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertEquals("tool", messages.getJSONObject(3).getString("role"))
        assertEquals("call_1", messages.getJSONObject(3).getString("tool_call_id"))

        val toolDef = body.getJSONArray("tools").getJSONObject(0)
        assertEquals("function", toolDef.getString("type"))
        assertEquals("workspace_create_project", toolDef.getJSONObject("function").getString("name"))
    }

    @Test
    fun `parses OpenAI SSE stream with text deltas and parallel chunked tool_calls`() = runTest {
        val sseLines = listOf(
            """data: {"choices":[{"index":0,"delta":{"role":"assistant","content":"Хорошо, "},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"content":"выполняю!"},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_a","type":"function","function":{"name":"workspace_get_context","arguments":""}}]},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"id":"call_b","type":"function","function":{"name":"workspace_create_project","arguments":"{\"name\":"}}]},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\"Rocket\"}"}}]},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""",
            "data: [DONE]"
        )

        var capturedAuth: String? = null
        val fakeTransport = object : SseHttpTransport {
            override suspend fun postStream(
                url: String,
                headers: Map<String, String>,
                bodyJson: String,
                proxy: AiProxyConfig?,
                onLine: suspend (String) -> Unit
            ) {
                capturedAuth = headers["Authorization"]
                sseLines.forEach { onLine(it) }
            }
        }

        val client = OpenAiStreamingClient(transport = fakeTransport)
        val events = client.streamChat(
            LlmRequest("gpt-6.2-sol", "sys", listOf(LlmMessage.User("go")), emptyList(), "sk-123")
        ).toList()

        assertEquals("Bearer sk-123", capturedAuth)

        val text = events.filterIsInstance<LlmStreamEvent.TextDelta>().joinToString("") { it.text }
        assertEquals("Хорошо, выполняю!", text)

        val completed = events.filterIsInstance<LlmStreamEvent.ToolUseCompleted>()
        assertEquals(2, completed.size)
        assertEquals("call_a", completed[0].call.id)
        assertEquals("workspace_get_context", completed[0].call.name)
        assertEquals("call_b", completed[1].call.id)
        assertEquals("workspace_create_project", completed[1].call.name)
        assertEquals("Rocket", JSONObject(completed[1].call.argumentsJson).getString("name"))

        val finished = events.filterIsInstance<LlmStreamEvent.MessageFinished>().single()
        assertEquals("tool_calls", finished.stopReason)
    }

    @Test
    fun `OpenRouter Gemini client routes to OpenRouter, preserves reasoning_details, and replays them`() = runTest {
        val sseLines = listOf(
            """data: {"choices":[{"index":0,"delta":{"reasoning_details":[{"type":"reasoning.text","id":"r1","index":0,"text":"Думаю... "}]},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"reasoning_details":[{"type":"reasoning.text","id":"r1","index":0,"text":"вызову инструмент"},{"type":"reasoning.encrypted","id":"sig1","index":1,"data":"ENC_SIG_ABC"}]},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_g1","type":"function","function":{"name":"workspace_create_project","arguments":"{\"name\":\"GemApp\"}"}}]},"finish_reason":"tool_calls"}]}""",
            "data: [DONE]"
        )

        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
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
                sseLines.forEach { onLine(it) }
            }
        }

        val client = OpenAiStreamingClient.forOpenRouterGemini(transport = transport)
        val events = client.streamChat(
            LlmRequest("google/gemini-4-neon", "sys", listOf(LlmMessage.User("create")), emptyList(), "sk-or-v1-test")
        ).toList()

        assertEquals("https://openrouter.ai/api/v1/chat/completions", capturedUrl)
        assertEquals("Bearer sk-or-v1-test", capturedHeaders["Authorization"])
        assertEquals("PocketWorker", capturedHeaders["X-Title"])

        val rdEvent = events.filterIsInstance<LlmStreamEvent.ReasoningDetailsCaptured>().single()
        val replayBody = client.buildRequestBody(
            LlmRequest(
                model = "google/gemini-4-neon",
                systemPrompt = "sys",
                messages = listOf(
                    LlmMessage.User("create"),
                    LlmMessage.Assistant(
                        text = "",
                        toolCalls = listOf(LlmToolCall("call_g1", "workspace_create_project", """{"name":"GemApp"}""")),
                        reasoningDetailsJson = rdEvent.reasoningDetailsJson
                    ),
                    LlmMessage.ToolResult("call_g1", "workspace_create_project", """{"ok":true}""", isError = false)
                ),
                tools = emptyList(),
                apiKey = "sk-or-v1-test"
            )
        )

        assertTrue(replayBody.has("max_tokens"))
        val assistantMsg = replayBody.getJSONArray("messages").getJSONObject(2)
        val replayedDetails = assistantMsg.getJSONArray("reasoning_details")
        assertEquals(2, replayedDetails.length())
        assertEquals("Думаю... вызову инструмент", replayedDetails.getJSONObject(0).getString("text"))
        assertEquals("ENC_SIG_ABC", replayedDetails.getJSONObject(1).getString("data"))
    }

    @Test
    fun `ignores explicit JSON null fields in chunked tool_calls and reasoning_details`() = runTest {
        val nullObj = JSONObject("""{"id":null,"name":null,"arguments":null,"text":"ok"}""")
        assertEquals("", nullObj.optNonNullString("id"))
        assertEquals("", nullObj.optNonNullString("name"))
        assertEquals("", nullObj.optNonNullString("arguments"))
        assertEquals("ok", nullObj.optNonNullString("text"))

        val sseLines = listOf(
            """data: {"choices":[{"index":0,"delta":{"content":null,"reasoning_details":[{"type":"reasoning.text","id":"r1","index":0,"text":"Шаг 1","signature":null}]},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"reasoning_details":[{"type":"reasoning.text","id":"r1","index":0,"text":null,"signature":"sig_ok"}]},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_null_safe","type":"function","function":{"name":"git_exec","arguments":""}}]},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":null,"type":null,"function":{"name":null,"arguments":"{\"command\":\"status\"}"}}]},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":null,"function":{"name":null,"arguments":null}}]},"finish_reason":"tool_calls"}]}""",
            "data: [DONE]"
        )

        val transport = object : SseHttpTransport {
            override suspend fun postStream(
                url: String,
                headers: Map<String, String>,
                bodyJson: String,
                proxy: AiProxyConfig?,
                onLine: suspend (String) -> Unit
            ) {
                sseLines.forEach { onLine(it) }
            }
        }

        val client = OpenAiStreamingClient.forOpenRouter(transport = transport)
        val events = client.streamChat(
            LlmRequest("baidu/ernie-5.5-flash-preview:free", "sys", listOf(LlmMessage.User("status")), emptyList(), "sk-or-test")
        ).toList()

        assertTrue(events.filterIsInstance<LlmStreamEvent.TextDelta>().isEmpty())
        val completed = events.filterIsInstance<LlmStreamEvent.ToolUseCompleted>().single()
        assertEquals("call_null_safe", completed.call.id)
        assertEquals("git_exec", completed.call.name)
        assertEquals("status", JSONObject(completed.call.argumentsJson).getString("command"))

        val rdEvent = events.filterIsInstance<LlmStreamEvent.ReasoningDetailsCaptured>().single()
        val rdArr = org.json.JSONArray(rdEvent.reasoningDetailsJson)
        assertEquals(1, rdArr.length())
        assertEquals("Шаг 1", rdArr.getJSONObject(0).getString("text"))
        assertEquals("sig_ok", rdArr.getJSONObject(0).getString("signature"))
    }
}
