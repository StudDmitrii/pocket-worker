package com.pocketworker.app.core.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicStreamingClientTest {

    @Test
    fun `builds valid Messages API payload and merges consecutive tool_result blocks`() {
        val client = AnthropicStreamingClient()
        val req = LlmRequest(
            model = "claude-sonnet-4-6",
            systemPrompt = "System prompt line 1\nCustom rule",
            messages = listOf(
                LlmMessage.User("Проверь проекты"),
                LlmMessage.Assistant(
                    text = "Сейчас посмотрю.",
                    toolCalls = listOf(
                        LlmToolCall("toolu_1", "workspace_get_context", "{}"),
                        LlmToolCall("toolu_2", "workspace_list_projects", "{}")
                    )
                ),
                LlmMessage.ToolResult("toolu_1", "workspace_get_context", """{"ok":true}""", isError = false),
                LlmMessage.ToolResult("toolu_2", "workspace_list_projects", """{"ok":true,"count":0}""", isError = false)
            ),
            tools = listOf(
                LlmToolSpec(
                    name = "workspace_list_projects",
                    description = "Lists projects",
                    inputSchema = JSONObject().put("type", "object").put("properties", JSONObject())
                )
            ),
            apiKey = "sk-ant-test"
        )

        val body = client.buildRequestBody(req)
        assertEquals("claude-sonnet-4-6", body.getString("model"))
        assertTrue(body.getBoolean("stream"))
        assertEquals("System prompt line 1\nCustom rule", body.getString("system"))

        val messages = body.getJSONArray("messages")
        // user -> assistant -> single merged user containing 2 tool_result blocks
        assertEquals(3, messages.length())
        val mergedUser = messages.getJSONObject(2)
        assertEquals("user", mergedUser.getString("role"))
        val blocks = mergedUser.getJSONArray("content")
        assertEquals(2, blocks.length())
        assertEquals("tool_result", blocks.getJSONObject(0).getString("type"))
        assertEquals("toolu_1", blocks.getJSONObject(0).getString("tool_use_id"))
        assertEquals("toolu_2", blocks.getJSONObject(1).getString("tool_use_id"))

        val tools = body.getJSONArray("tools")
        assertEquals(1, tools.length())
        assertTrue(tools.getJSONObject(0).has("input_schema"))
    }

    @Test
    fun `parses SSE stream with text deltas, chunked tool_use, server web search, and stop`() = runTest {
        val sseLines = listOf(
            "event: message_start",
            """data: {"type":"message_start","message":{"id":"msg_1"}}""",
            "",
            "event: content_block_start",
            """data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            "",
            "event: content_block_delta",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Создаю "}}""",
            "",
            "event: content_block_delta",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"проект!"}}""",
            "",
            "event: content_block_stop",
            """data: {"type":"content_block_stop","index":0}""",
            "",
            "event: content_block_start",
            """data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_abc","name":"workspace_create_project","input":{}}}""",
            "",
            "event: content_block_delta",
            """data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"name\":"}}""",
            "",
            "event: content_block_delta",
            """data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"DemoApp\"}"}}""",
            "",
            "event: content_block_stop",
            """data: {"type":"content_block_stop","index":1}""",
            "",
            "event: message_delta",
            """data: {"type":"message_delta","delta":{"stop_reason":"tool_use"}}""",
            "",
            "event: message_stop",
            """data: {"type":"message_stop"}"""
        )

        var capturedHeaders: Map<String, String> = emptyMap()
        var capturedProxy: AiProxyConfig? = null
        val fakeTransport = object : SseHttpTransport {
            override suspend fun postStream(
                url: String,
                headers: Map<String, String>,
                bodyJson: String,
                proxy: AiProxyConfig?,
                onLine: suspend (String) -> Unit
            ) {
                capturedHeaders = headers
                capturedProxy = proxy
                sseLines.forEach { onLine(it) }
            }
        }

        val proxyCfg = (parseAiProxyUrl("http://u:p@1.2.3.4:8080") as ProxyParseResult.Valid).config
        val client = AnthropicStreamingClient(transport = fakeTransport)
        val events = client.streamChat(
            LlmRequest(
                model = "claude-sonnet-5-5",
                systemPrompt = "sys",
                messages = listOf(LlmMessage.User("hi")),
                tools = emptyList(),
                apiKey = "sk-ant-key",
                proxy = proxyCfg
            )
        ).toList()

        assertEquals("sk-ant-key", capturedHeaders["x-api-key"])
        assertEquals("2023-06-01", capturedHeaders["anthropic-version"])
        assertEquals(proxyCfg, capturedProxy)

        val text = events.filterIsInstance<LlmStreamEvent.TextDelta>().joinToString("") { it.text }
        assertEquals("Создаю проект!", text)

        val started = events.filterIsInstance<LlmStreamEvent.ToolUseStarted>().single()
        assertEquals("toolu_abc", started.id)
        assertEquals("workspace_create_project", started.name)

        val completed = events.filterIsInstance<LlmStreamEvent.ToolUseCompleted>().single()
        assertEquals("toolu_abc", completed.call.id)
        assertEquals("DemoApp", JSONObject(completed.call.argumentsJson).getString("name"))

        val finished = events.filterIsInstance<LlmStreamEvent.MessageFinished>().single()
        assertEquals("tool_use", finished.stopReason)
    }

    @Test
    fun `maps HTTP 401 to clear Russian error event`() = runTest {
        val failingTransport = object : SseHttpTransport {
            override suspend fun postStream(
                url: String,
                headers: Map<String, String>,
                bodyJson: String,
                proxy: AiProxyConfig?,
                onLine: suspend (String) -> Unit
            ) {
                throw LlmHttpException(401, """{"error":{"message":"invalid x-api-key"}}""")
            }
        }
        val client = AnthropicStreamingClient(transport = failingTransport)
        val events = client.streamChat(
            LlmRequest("claude-sonnet-4-6", "", listOf(LlmMessage.User("hi")), emptyList(), "bad")
        ).toList()

        val err = events.filterIsInstance<LlmStreamEvent.Error>().single()
        assertEquals(401, err.statusCode)
        assertTrue(err.message.contains("Claude"))
    }

    @Test
    fun `sanitizes interrupted tool calls and merges consecutive user messages for Anthropic API`() {
        val client = AnthropicStreamingClient()
        val body = client.buildRequestBody(
            LlmRequest(
                model = "claude-sonnet-4-6",
                systemPrompt = "sys",
                messages = listOf(
                    LlmMessage.User("Шаг 1"),
                    LlmMessage.Assistant(
                        text = "Выполняю",
                        toolCalls = listOf(LlmToolCall("toolu_orphan", "fs_read_file", """{"relativePath":"a.kt"}"""))
                    ),
                    // Missing ToolResult + next user message directly!
                    LlmMessage.User("Тут переделай")
                ),
                tools = emptyList(),
                apiKey = "sk-ant"
            )
        )

        val msgs = body.getJSONArray("messages")
        // Expected strictly alternating roles: user -> assistant(tool_use) -> user([tool_result, text])
        assertEquals(3, msgs.length())
        assertEquals("user", msgs.getJSONObject(0).getString("role"))
        assertEquals("assistant", msgs.getJSONObject(1).getString("role"))
        assertEquals("user", msgs.getJSONObject(2).getString("role"))
        val finalBlocks = msgs.getJSONObject(2).getJSONArray("content")
        assertEquals(2, finalBlocks.length())
        assertEquals("tool_result", finalBlocks.getJSONObject(0).getString("type"))
        assertEquals("toolu_orphan", finalBlocks.getJSONObject(0).getString("tool_use_id"))
        assertEquals("text", finalBlocks.getJSONObject(1).getString("type"))
        assertEquals("Тут переделай", finalBlocks.getJSONObject(1).getString("text"))
    }
}
