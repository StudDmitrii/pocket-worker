package com.pocketworker.app.core.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalGemmaLlmClientTest {

    private lateinit var tempDir: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("local-gemma-client-test").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun `emits clear 404 error when local model is not downloaded yet`() = runBlocking {
        val manager = LocalModelManager(File(tempDir, "models"), scope)
        val client = LocalGemmaLlmClient(manager, File(tempDir, "cache"))

        val events = client.streamChat(
            LlmRequest(
                model = LocalModelManager.MODEL_GEMMA_4_E2B_ID,
                systemPrompt = "You are helpful.",
                messages = listOf(LlmMessage.User("Привет")),
                tools = emptyList(),
                apiKey = ""
            )
        ).toList()

        assertEquals(1, events.size)
        val err = events.single() as LlmStreamEvent.Error
        assertEquals(404, err.statusCode)
        assertTrue(err.message.contains("не скачана"))
    }

    @Test
    fun `builds valid OpenAPI tool schema JSON and round-trips tool arguments and results`() {
        val spec = LlmToolSpec(
            name = "fs_write_file",
            description = "Writes a file",
            inputSchema = JSONObject("""{"type":"object","properties":{"relativePath":{"type":"string"}},"required":["relativePath"]}""")
        )
        val schemaJson = JSONObject(LocalGemmaLlmClient.buildOpenApiToolSchemaJson(spec))
        assertEquals("fs_write_file", schemaJson.getString("name"))
        assertEquals("Writes a file", schemaJson.getString("description"))
        assertEquals("object", schemaJson.getJSONObject("parameters").getString("type"))

        val parsedArgs = LocalGemmaLlmClient.jsonStringToPlainMap(
            """{"relativePath":"src/App.kt","lines":[1,2,3],"overwrite":true}"""
        )
        assertEquals("src/App.kt", parsedArgs["relativePath"])
        assertEquals(listOf(1, 2, 3), parsedArgs["lines"])
        assertEquals(true, parsedArgs["overwrite"])

        val roundTripped = LocalGemmaLlmClient.mapToJsonObject(parsedArgs)
        assertEquals("src/App.kt", roundTripped.getString("relativePath"))
        assertEquals(3, roundTripped.getJSONArray("lines").length())
        assertTrue(roundTripped.getBoolean("overwrite"))

        @Suppress("UNCHECKED_CAST")
        val parsedToolResult = LocalGemmaLlmClient.jsonStringToPlainValue("""{"ok":true,"count":4}""") as Map<String, Any?>
        assertEquals(true, parsedToolResult["ok"])
        assertEquals(4, parsedToolResult["count"])
    }

    @Test
    fun `compacts verbose MCP tool schemas to stay within mobile LiteRT-LM token budget`() {
        val verboseSchema = JSONObject(
            """
            {
              "type": "object",
              "required": ["projectId", "operation"],
              "properties": {
                "projectId": {
                  "type": "string",
                  "description": "Очень длинное подробное описание идентификатора проекта, которое занимает много лишних токенов на мобильном CPU"
                },
                "operation": {
                  "type": "string",
                  "enum": ["create_dir", "move", "rename"],
                  "description": "Тип операции с директориями и файлами"
                },
                "itemsList": {
                  "type": "array",
                  "description": "Список элементов",
                  "items": {
                    "type": "string",
                    "description": "Отдельный путь"
                  }
                }
              }
            }
            """.trimIndent()
        )
        val spec = LlmToolSpec(
            name = "fs_manage_structure",
            description = "Управляет структурой файлов и папок внутри репозитория. Поддерживает переименование, перемещение, копирование и создание вложенных директорий с проверкой безопасности.",
            inputSchema = verboseSchema
        )

        val compactStr = LocalGemmaLlmClient.buildOpenApiToolSchemaJson(spec)
        val compact = JSONObject(compactStr)

        assertEquals("fs_manage_structure", compact.getString("name"))
        assertTrue(compact.getString("description").length <= 90)
        val params = compact.getJSONObject("parameters")
        assertEquals("object", params.getString("type"))
        assertEquals(2, params.getJSONArray("required").length())

        val props = params.getJSONObject("properties")
        assertEquals("string", props.getJSONObject("projectId").getString("type"))
        assertTrue(!props.getJSONObject("projectId").has("description"))
        assertEquals(3, props.getJSONObject("operation").getJSONArray("enum").length())
        assertEquals("string", props.getJSONObject("itemsList").getJSONObject("items").getString("type"))
        assertTrue(!props.getJSONObject("itemsList").getJSONObject("items").has("description"))
        assertTrue(compactStr.length < verboseSchema.toString().length)
    }
}
