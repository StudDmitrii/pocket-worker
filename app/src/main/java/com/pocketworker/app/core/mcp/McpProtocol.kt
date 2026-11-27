package com.pocketworker.app.core.mcp

import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.ui.ai.AiLocationContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Safety & user-approval classification for MCP tool calls.
 */
enum class McpPermissionTier {
    /** Read-only inspections, searches, checks, and git fetch — runs automatically. */
    AUTO,

    /** Local file edits, folder changes, git commit/branch/pull — confirms when confirmFileEdits is enabled. */
    CONTROLLED_WRITE,

    /** High-risk, destructive, or remote-publishing actions (push, reset --hard, delete, clone) — always confirms. */
    ALWAYS_CONFIRM
}

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
    val defaultTier: McpPermissionTier
) {
    fun toMcpJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
        put("inputSchema", inputSchema)
        put("permissionTier", defaultTier.name)
    }
}

data class McpConfirmationRequest(
    val toolName: String,
    val tier: McpPermissionTier,
    val summary: String,
    val arguments: JSONObject
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("toolName", toolName)
        put("tier", tier.name)
        put("summary", summary)
        put("arguments", arguments)
    }
}

fun interface McpConfirmationHandler {
    suspend fun requestApproval(request: McpConfirmationRequest): Boolean
}

data class McpErrorPayload(
    val errorCode: String,
    val message: String,
    val recoverableByAgent: Boolean,
    val reason: String? = null,
    val supportedFlags: List<String> = emptyList(),
    val suggestedAlternatives: List<String> = emptyList(),
    val userMessageHint: String? = null,
    val details: JSONObject? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("errorCode", errorCode)
        put("message", message)
        put("recoverableByAgent", recoverableByAgent)
        if (!reason.isNullOrBlank()) put("reason", reason)
        if (supportedFlags.isNotEmpty()) put("supportedFlags", JSONArray(supportedFlags))
        if (suggestedAlternatives.isNotEmpty()) put("suggestedAlternatives", JSONArray(suggestedAlternatives))
        if (!userMessageHint.isNullOrBlank()) put("userMessageHint", userMessageHint)
        if (details != null) put("details", details)
    }
}

data class McpToolResult(
    val ok: Boolean,
    val data: JSONObject? = null,
    val error: McpErrorPayload? = null,
    val requiresConfirmation: McpConfirmationRequest? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ok", ok)
        if (data != null) put("data", data)
        if (error != null) put("error", error.toJson())
        if (requiresConfirmation != null) put("requiresConfirmation", requiresConfirmation.toJson())
    }

    fun toJsonString(indentSpaces: Int = 2): String = toJson().toString(indentSpaces)

    companion object {
        fun success(data: JSONObject = JSONObject()): McpToolResult =
            McpToolResult(ok = true, data = data)

        fun success(block: JSONObject.() -> Unit): McpToolResult =
            McpToolResult(ok = true, data = JSONObject().apply(block))

        fun error(
            errorCode: String,
            message: String,
            recoverableByAgent: Boolean,
            reason: String? = null,
            supportedFlags: List<String> = emptyList(),
            suggestedAlternatives: List<String> = emptyList(),
            userMessageHint: String? = null,
            details: JSONObject? = null,
            data: JSONObject? = null
        ): McpToolResult = McpToolResult(
            ok = false,
            data = data,
            error = McpErrorPayload(
                errorCode = errorCode,
                message = message,
                recoverableByAgent = recoverableByAgent,
                reason = reason,
                supportedFlags = supportedFlags,
                suggestedAlternatives = suggestedAlternatives,
                userMessageHint = userMessageHint,
                details = details
            )
        )
    }
}

data class McpExecutionContext(
    val currentLocationProvider: () -> AiLocationContext = { AiLocationContext.AllProjects },
    val confirmFileEditsProvider: () -> Boolean = { true }
)

interface McpTool {
    val definition: McpToolDefinition

    fun classifyTier(arguments: JSONObject): McpPermissionTier = definition.defaultTier

    fun buildConfirmationSummary(arguments: JSONObject): String =
        AppLocale.current.mcpConfirmGenericTool(definition.name)

    suspend fun execute(
        arguments: JSONObject,
        context: McpExecutionContext
    ): McpToolResult
}

internal object McpSchemaBuilder {
    fun objectSchema(
        required: List<String> = emptyList(),
        properties: JSONObject.() -> Unit = {}
    ): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply(properties))
        if (required.isNotEmpty()) {
            put("required", JSONArray(required))
        }
    }

    fun stringProp(description: String, enumValues: List<String>? = null): JSONObject = JSONObject().apply {
        put("type", "string")
        put("description", description)
        if (!enumValues.isNullOrEmpty()) {
            put("enum", JSONArray(enumValues))
        }
    }

    fun intProp(description: String): JSONObject = JSONObject().apply {
        put("type", "integer")
        put("description", description)
    }

    fun boolProp(description: String): JSONObject = JSONObject().apply {
        put("type", "boolean")
        put("description", description)
    }

    fun arrayProp(description: String, items: JSONObject): JSONObject = JSONObject().apply {
        put("type", "array")
        put("description", description)
        put("items", items)
    }
}

internal fun JSONObject.optNonBlankString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key, "").trim().takeIf { it.isNotEmpty() }
}

internal fun JSONObject.requireNonBlankString(key: String): String {
    return optNonBlankString(key)
        ?: throw IllegalArgumentException("Обязательный строковый параметр '$key' не задан или пуст")
}
