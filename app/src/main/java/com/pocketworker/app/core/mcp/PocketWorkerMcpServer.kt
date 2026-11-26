package com.pocketworker.app.core.mcp

import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.ProjectFileManager
import com.pocketworker.app.core.git.RepoGroupDiscoveryService
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import com.pocketworker.app.ui.ai.AiLocationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local in-process MCP server exposing PocketWorker workspace, filesystem,
 * virtual Git CLI (JGit), and offline verification tools.
 *
 * Supports both direct Kotlin calls (`listTools`, `callTool`) and standard
 * JSON-RPC 2.0 MCP messages (`initialize`, `tools/list`, `tools/call`).
 */
class PocketWorkerMcpServer(
    projectRegistry: ProjectRegistry,
    fileManager: ProjectFileManager,
    gitEngine: GitEngine,
    credentialStore: CredentialStore,
    groupDiscoveryService: RepoGroupDiscoveryService,
    private val currentLocationProvider: () -> AiLocationContext = { AiLocationContext.AllProjects },
    private val confirmFileEditsProvider: () -> Boolean = { true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    syntaxCheckEngine: SyntaxCheckEngine = NoOpSyntaxCheckEngine
) {
    private val resolver = McpWorkspaceResolver(projectRegistry)
    private val toolsByName: LinkedHashMap<String, McpTool> = LinkedHashMap()

    init {
        val allTools = buildList {
            addAll(createWorkspaceTools(projectRegistry, gitEngine, credentialStore, groupDiscoveryService, resolver))
            addAll(createFileSystemTools(projectRegistry, fileManager, gitEngine, resolver))
            add(VirtualGitCommandTool(fileManager, gitEngine, credentialStore, resolver))
            addAll(createCheckTools(fileManager, gitEngine, credentialStore, resolver, syntaxCheckEngine))
        }
        allTools.forEach { tool ->
            require(!toolsByName.containsKey(tool.definition.name)) {
                "Duplicate MCP tool name: ${tool.definition.name}"
            }
            toolsByName[tool.definition.name] = tool
        }
    }

    fun listTools(): List<McpToolDefinition> = toolsByName.values.map { it.definition }

    fun listToolsJson(): JSONArray = JSONArray().apply {
        toolsByName.values.forEach { put(it.definition.toMcpJson()) }
    }

    fun getToolDefinition(name: String): McpToolDefinition? = toolsByName[name]?.definition

    fun classifyToolCall(name: String, arguments: JSONObject = JSONObject()): McpPermissionTier? {
        val tool = toolsByName[name] ?: return null
        return tool.classifyTier(arguments)
    }

    suspend fun callTool(
        name: String,
        arguments: JSONObject = JSONObject(),
        confirmationHandler: McpConfirmationHandler? = null
    ): McpToolResult {
        val tool = toolsByName[name] ?: return McpToolResult.error(
            errorCode = McpErrorCodes.TOOL_NOT_FOUND,
            message = "Инструмент '$name' не зарегистрирован в локальном MCP-сервере.",
            recoverableByAgent = true,
            suggestedAlternatives = listOf("Вызови tools/list, чтобы получить список доступных инструментов")
        )

        val tier = runCatching { tool.classifyTier(arguments) }.getOrDefault(tool.definition.defaultTier)
        val requiresApproval = when (tier) {
            McpPermissionTier.AUTO -> false
            McpPermissionTier.CONTROLLED_WRITE -> confirmFileEditsProvider()
            McpPermissionTier.ALWAYS_CONFIRM -> true
        }

        if (requiresApproval) {
            val summary = runCatching { tool.buildConfirmationSummary(arguments) }
                .getOrDefault("Выполнить $name")
            val request = McpConfirmationRequest(
                toolName = name,
                tier = tier,
                summary = summary,
                arguments = arguments
            )
            if (confirmationHandler == null) {
                return McpToolResult(
                    ok = false,
                    error = McpErrorPayload(
                        errorCode = McpErrorCodes.CONFIRMATION_REQUIRED,
                        message = "Требуется подтверждение пользователя: $summary",
                        recoverableByAgent = false,
                        userMessageHint = "Для этого действия необходимо явное подтверждение пользователя."
                    ),
                    requiresConfirmation = request
                )
            }
            val approved = confirmationHandler.requestApproval(request)
            if (!approved) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.USER_REJECTED,
                    message = "Пользователь отклонил выполнение операции: $summary",
                    recoverableByAgent = false,
                    userMessageHint = "Операция отменена пользователем."
                )
            }
        }

        val execContext = McpExecutionContext(
            currentLocationProvider = currentLocationProvider,
            confirmFileEditsProvider = confirmFileEditsProvider
        )

        return withContext(ioDispatcher) {
            try {
                tool.execute(arguments, execContext)
            } catch (ex: IllegalArgumentException) {
                McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = ex.message ?: "Некорректные аргументы инструмента '$name'.",
                    recoverableByAgent = true
                )
            } catch (ex: Throwable) {
                McpToolResult.error(
                    errorCode = McpErrorCodes.INTERNAL_ERROR,
                    message = ex.localizedMessage ?: ex.message ?: ex.javaClass.simpleName,
                    recoverableByAgent = false,
                    userMessageHint = "Внутренняя ошибка при выполнении '$name': ${ex.localizedMessage ?: ex.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * Standard JSON-RPC 2.0 MCP request handler (`initialize`, `ping`, `tools/list`, `tools/call`).
     */
    suspend fun handleJsonRpc(
        rawJson: String,
        confirmationHandler: McpConfirmationHandler? = null
    ): String {
        val req = runCatching { JSONObject(rawJson) }.getOrElse { ex ->
            return jsonRpcError(JSONObject.NULL, -32700, "Parse error: ${ex.message}").toString()
        }

        val id: Any = if (req.has("id")) req.get("id") else JSONObject.NULL
        val method = req.optString("method", "").trim()
        val params = req.optJSONObject("params") ?: JSONObject()

        return when (method) {
            "initialize" -> {
                jsonRpcResult(
                    id = id,
                    result = JSONObject().apply {
                        put("protocolVersion", PROTOCOL_VERSION)
                        put(
                            "capabilities",
                            JSONObject().apply {
                                put("tools", JSONObject().apply { put("listChanged", false) })
                            }
                        )
                        put(
                            "serverInfo",
                            JSONObject().apply {
                                put("name", SERVER_NAME)
                                put("version", SERVER_VERSION)
                            }
                        )
                    }
                ).toString()
            }

            "notifications/initialized", "ping" -> {
                jsonRpcResult(id = id, result = JSONObject()).toString()
            }

            "tools/list" -> {
                jsonRpcResult(
                    id = id,
                    result = JSONObject().apply {
                        put("tools", listToolsJson())
                    }
                ).toString()
            }

            "tools/call" -> {
                val toolName = params.optNonBlankString("name")
                if (toolName == null) {
                    return jsonRpcError(id, -32602, "Invalid params: missing 'name'").toString()
                }
                val args = params.optJSONObject("arguments") ?: JSONObject()
                val toolResult = callTool(toolName, args, confirmationHandler)
                val payloadJson = toolResult.toJson()
                val contentArray = JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("type", "text")
                            put("text", payloadJson.toString())
                        }
                    )
                }
                jsonRpcResult(
                    id = id,
                    result = JSONObject().apply {
                        put("content", contentArray)
                        put("isError", !toolResult.ok)
                        put("structuredContent", payloadJson)
                    }
                ).toString()
            }

            else -> jsonRpcError(id, -32601, "Method not found: $method").toString()
        }
    }

    private fun jsonRpcResult(id: Any, result: JSONObject): JSONObject = JSONObject().apply {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun jsonRpcError(id: Any, code: Int, message: String): JSONObject = JSONObject().apply {
        put("jsonrpc", "2.0")
        put("id", id)
        put(
            "error",
            JSONObject().apply {
                put("code", code)
                put("message", message)
            }
        )
    }

    companion object {
        const val PROTOCOL_VERSION = "2024-11-05"
        const val SERVER_NAME = "pocket-worker-local-mcp"
        const val SERVER_VERSION = "1.0.0"
    }
}
