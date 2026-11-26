package com.pocketworker.app.core.storage

import com.pocketworker.app.core.ai.ChangedFileSummary
import com.pocketworker.app.core.ai.LlmMessage
import com.pocketworker.app.core.ai.LlmToolCall
import com.pocketworker.app.core.ai.McpUndoOperation
import com.pocketworker.app.ui.ai.AiChatMessageItem
import com.pocketworker.app.ui.ai.AiChatSession
import com.pocketworker.app.ui.ai.ToolActivityCategory
import com.pocketworker.app.ui.ai.ToolActivityStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

data class AiTurnCheckpoint(
    val userMessageId: String,
    val llmHistoryStartIndex: Int,
    val undoOperations: List<McpUndoOperation> = emptyList()
)

data class PersistedAiChatData(
    val sessions: List<AiChatSession>,
    val activeChatId: String,
    val messagesByChat: Map<String, List<AiChatMessageItem>>,
    val llmHistoryByChat: Map<String, List<LlmMessage>>,
    val checkpointsByChat: Map<String, List<AiTurnCheckpoint>>
)

interface AiChatHistoryStore {
    fun load(): PersistedAiChatData?
    fun save(data: PersistedAiChatData)
}

class InMemoryAiChatHistoryStore(
    private var snapshot: PersistedAiChatData? = null
) : AiChatHistoryStore {
    override fun load(): PersistedAiChatData? = snapshot
    override fun save(data: PersistedAiChatData) {
        snapshot = data
    }
}

class FileAiChatHistoryStore(
    private val storageFile: File
) : AiChatHistoryStore {

    @Synchronized
    override fun load(): PersistedAiChatData? {
        if (!storageFile.exists() || !storageFile.isFile) return null
        return runCatching {
            val raw = storageFile.readText(StandardCharsets.UTF_8)
            if (raw.isBlank()) return null
            deserialize(JSONObject(raw))
        }.getOrNull()
    }

    @Synchronized
    override fun save(data: PersistedAiChatData) {
        runCatching {
            storageFile.parentFile?.mkdirs()
            val payload = serialize(data).toString()
            val tmpFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
            tmpFile.writeText(payload, StandardCharsets.UTF_8)
            if (storageFile.exists()) {
                storageFile.delete()
            }
            if (!tmpFile.renameTo(storageFile)) {
                storageFile.writeText(payload, StandardCharsets.UTF_8)
                tmpFile.delete()
            }
        }
    }

    private fun serialize(data: PersistedAiChatData): JSONObject = JSONObject().apply {
        put("version", SCHEMA_VERSION)
        put("activeChatId", data.activeChatId)

        val sessionsArr = JSONArray()
        data.sessions.forEach { s ->
            sessionsArr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("createdAtMillis", s.createdAtMillis)
                    put("preview", s.preview)
                    if (s.lastContextKey.isNotBlank()) {
                        put("lastContextKey", s.lastContextKey)
                    }
                }
            )
        }
        put("sessions", sessionsArr)

        val msgsObj = JSONObject()
        data.messagesByChat.forEach { (chatId, list) ->
            val arr = JSONArray()
            list.forEach { item -> arr.put(serializeUiItem(item)) }
            msgsObj.put(chatId, arr)
        }
        put("messagesByChat", msgsObj)

        val llmObj = JSONObject()
        data.llmHistoryByChat.forEach { (chatId, list) ->
            val arr = JSONArray()
            list.forEach { msg -> arr.put(serializeLlmMessage(msg)) }
            llmObj.put(chatId, arr)
        }
        put("llmHistoryByChat", llmObj)

        val cpObj = JSONObject()
        data.checkpointsByChat.forEach { (chatId, list) ->
            val arr = JSONArray()
            list.forEach { cp ->
                arr.put(
                    JSONObject().apply {
                        put("userMessageId", cp.userMessageId)
                        put("llmHistoryStartIndex", cp.llmHistoryStartIndex)
                        val opsArr = JSONArray()
                        cp.undoOperations.forEach { opsArr.put(it.toJson()) }
                        put("undoOperations", opsArr)
                    }
                )
            }
            cpObj.put(chatId, arr)
        }
        put("checkpointsByChat", cpObj)
    }

    private fun deserialize(root: JSONObject): PersistedAiChatData? {
        val sessionsArr = root.optJSONArray("sessions") ?: return null
        val sessions = mutableListOf<AiChatSession>()
        for (i in 0 until sessionsArr.length()) {
            val s = sessionsArr.optJSONObject(i) ?: continue
            val id = s.optString("id", "").trim()
            if (id.isEmpty()) continue
            sessions.add(
                AiChatSession(
                    id = id,
                    title = s.optString("title", AiChatSession.DEFAULT_TITLE),
                    createdAtMillis = s.optLong("createdAtMillis", System.currentTimeMillis()),
                    preview = s.optString("preview", ""),
                    lastContextKey = s.optString("lastContextKey", "")
                )
            )
        }
        if (sessions.isEmpty()) return null

        val activeChatId = root.optString("activeChatId", sessions.first().id)
            .takeIf { id -> sessions.any { it.id == id } }
            ?: sessions.first().id

        val messagesByChat = LinkedHashMap<String, List<AiChatMessageItem>>()
        root.optJSONObject("messagesByChat")?.let { obj ->
            obj.keys().forEach { chatId ->
                val arr = obj.optJSONArray(chatId) ?: return@forEach
                val items = (0 until arr.length()).mapNotNull { idx ->
                    arr.optJSONObject(idx)?.let { deserializeUiItem(it) }
                }
                messagesByChat[chatId] = items
            }
        }

        val llmHistoryByChat = LinkedHashMap<String, List<LlmMessage>>()
        root.optJSONObject("llmHistoryByChat")?.let { obj ->
            obj.keys().forEach { chatId ->
                val arr = obj.optJSONArray(chatId) ?: return@forEach
                val msgs = (0 until arr.length()).mapNotNull { idx ->
                    arr.optJSONObject(idx)?.let { deserializeLlmMessage(it) }
                }
                llmHistoryByChat[chatId] = msgs
            }
        }

        val checkpointsByChat = LinkedHashMap<String, List<AiTurnCheckpoint>>()
        root.optJSONObject("checkpointsByChat")?.let { obj ->
            obj.keys().forEach { chatId ->
                val arr = obj.optJSONArray(chatId) ?: return@forEach
                val cps = (0 until arr.length()).mapNotNull { idx ->
                    val cpJson = arr.optJSONObject(idx) ?: return@mapNotNull null
                    val opsArr = cpJson.optJSONArray("undoOperations") ?: JSONArray()
                    val ops = (0 until opsArr.length()).mapNotNull { opIdx ->
                        opsArr.optJSONObject(opIdx)?.let { McpUndoOperation.fromJson(it) }
                    }
                    AiTurnCheckpoint(
                        userMessageId = cpJson.optString("userMessageId", ""),
                        llmHistoryStartIndex = cpJson.optInt("llmHistoryStartIndex", 0),
                        undoOperations = ops
                    ).takeIf { it.userMessageId.isNotBlank() }
                }
                checkpointsByChat[chatId] = cps
            }
        }

        return PersistedAiChatData(
            sessions = sessions,
            activeChatId = activeChatId,
            messagesByChat = messagesByChat,
            llmHistoryByChat = llmHistoryByChat,
            checkpointsByChat = checkpointsByChat
        )
    }

    private fun serializeUiItem(item: AiChatMessageItem): JSONObject = when (item) {
        is AiChatMessageItem.UserMessage -> JSONObject().apply {
            put("type", "user")
            put("id", item.id)
            put("text", item.text)
            put("attachmentNames", JSONArray(item.attachmentNames))
            put("timestampMillis", item.timestampMillis)
        }

        is AiChatMessageItem.AssistantText -> JSONObject().apply {
            put("type", "assistant")
            put("id", item.id)
            put("text", item.text)
            put("modelLabel", item.modelLabel)
            put("timestampMillis", item.timestampMillis)
        }

        is AiChatMessageItem.ToolActivity -> serializeToolActivity(item)

        is AiChatMessageItem.ActionsGroup -> JSONObject().apply {
            put("type", "actions_group")
            put("id", item.id)
            put("turnId", item.turnId)
            put("timestampMillis", item.timestampMillis)
            val arr = JSONArray()
            item.actions.forEach { arr.put(serializeToolActivity(it)) }
            put("actions", arr)
        }

        is AiChatMessageItem.ChangedFilesCard -> JSONObject().apply {
            put("type", "changed_files")
            put("id", item.id)
            put("turnId", item.turnId)
            put("timestampMillis", item.timestampMillis)
            val arr = JSONArray()
            item.files.forEach { arr.put(it.toJson()) }
            put("files", arr)
        }

        is AiChatMessageItem.ErrorNotice -> JSONObject().apply {
            put("type", "error")
            put("id", item.id)
            put("message", item.message)
            put("showSettingsAction", item.showSettingsAction)
            put("timestampMillis", item.timestampMillis)
        }
    }

    private fun serializeToolActivity(item: AiChatMessageItem.ToolActivity): JSONObject = JSONObject().apply {
        put("type", "tool_activity")
        put("id", item.id)
        put("toolCallId", item.toolCallId)
        put("toolName", item.toolName)
        put("category", item.category.name)
        put("title", item.title)
        put("subtitle", item.subtitle)
        put("status", item.status.name)
        put("argumentsPreview", item.argumentsPreview)
        if (item.resultPreview != null) put("resultPreview", item.resultPreview)
        put("timestampMillis", item.timestampMillis)
    }

    private fun deserializeUiItem(obj: JSONObject): AiChatMessageItem? = when (obj.optString("type")) {
        "user" -> {
            val attArr = obj.optJSONArray("attachmentNames") ?: JSONArray()
            AiChatMessageItem.UserMessage(
                id = obj.optString("id"),
                text = obj.optString("text"),
                attachmentNames = (0 until attArr.length()).map { attArr.optString(it) },
                timestampMillis = obj.optLong("timestampMillis", 0L)
            )
        }

        "assistant" -> AiChatMessageItem.AssistantText(
            id = obj.optString("id"),
            text = obj.optString("text"),
            modelLabel = obj.optString("modelLabel", "AI"),
            isStreaming = false,
            timestampMillis = obj.optLong("timestampMillis", 0L)
        )

        "tool_activity" -> deserializeToolActivity(obj)

        "actions_group" -> {
            val arr = obj.optJSONArray("actions") ?: JSONArray()
            val actions = (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { deserializeToolActivity(it) }
            }
            AiChatMessageItem.ActionsGroup(
                id = obj.optString("id"),
                turnId = obj.optString("turnId"),
                actions = actions,
                timestampMillis = obj.optLong("timestampMillis", 0L)
            )
        }

        "changed_files" -> {
            val arr = obj.optJSONArray("files") ?: JSONArray()
            val files = (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { ChangedFileSummary.fromJson(it) }
            }
            AiChatMessageItem.ChangedFilesCard(
                id = obj.optString("id"),
                turnId = obj.optString("turnId"),
                files = files,
                timestampMillis = obj.optLong("timestampMillis", 0L)
            )
        }

        "error" -> AiChatMessageItem.ErrorNotice(
            id = obj.optString("id"),
            message = obj.optString("message"),
            showSettingsAction = obj.optBoolean("showSettingsAction", false),
            timestampMillis = obj.optLong("timestampMillis", 0L)
        )

        else -> null
    }

    private fun deserializeToolActivity(obj: JSONObject): AiChatMessageItem.ToolActivity {
        val cat = runCatching { ToolActivityCategory.valueOf(obj.optString("category")) }
            .getOrDefault(ToolActivityCategory.DOING)
        val rawStatus = runCatching { ToolActivityStatus.valueOf(obj.optString("status")) }
            .getOrDefault(ToolActivityStatus.SUCCEEDED)
        // Restored tool cards should never appear stuck spinning after an app restart
        val settledStatus = when (rawStatus) {
            ToolActivityStatus.STREAMING_ARGS,
            ToolActivityStatus.AWAITING_CONFIRMATION,
            ToolActivityStatus.RUNNING -> ToolActivityStatus.FAILED
            else -> rawStatus
        }
        return AiChatMessageItem.ToolActivity(
            id = obj.optString("id"),
            toolCallId = obj.optString("toolCallId"),
            toolName = obj.optString("toolName"),
            category = cat,
            title = obj.optString("title"),
            subtitle = obj.optString("subtitle"),
            status = settledStatus,
            argumentsPreview = obj.optString("argumentsPreview"),
            resultPreview = obj.optString("resultPreview", "").takeIf { it.isNotBlank() },
            timestampMillis = obj.optLong("timestampMillis", 0L)
        )
    }

    private fun serializeLlmMessage(msg: LlmMessage): JSONObject = when (msg) {
        is LlmMessage.User -> JSONObject().apply {
            put("role", "user")
            put("text", msg.text)
        }

        is LlmMessage.Assistant -> JSONObject().apply {
            put("role", "assistant")
            put("text", msg.text)
            if (msg.reasoningDetailsJson != null) {
                put("reasoningDetailsJson", msg.reasoningDetailsJson)
            }
            val tcArr = JSONArray()
            msg.toolCalls.forEach { c ->
                tcArr.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("argumentsJson", c.argumentsJson)
                    }
                )
            }
            put("toolCalls", tcArr)
        }

        is LlmMessage.ToolResult -> JSONObject().apply {
            put("role", "tool_result")
            put("toolCallId", msg.toolCallId)
            put("toolName", msg.toolName)
            put("contentJson", msg.contentJson)
            put("isError", msg.isError)
        }
    }

    private fun deserializeLlmMessage(obj: JSONObject): LlmMessage? = when (obj.optString("role")) {
        "user" -> LlmMessage.User(obj.optString("text"))

        "assistant" -> {
            val tcArr = obj.optJSONArray("toolCalls") ?: JSONArray()
            val calls = (0 until tcArr.length()).mapNotNull { idx ->
                val c = tcArr.optJSONObject(idx) ?: return@mapNotNull null
                LlmToolCall(
                    id = c.optString("id"),
                    name = c.optString("name"),
                    argumentsJson = c.optString("argumentsJson", "{}")
                )
            }
            LlmMessage.Assistant(
                text = obj.optString("text"),
                toolCalls = calls,
                reasoningDetailsJson = obj.optString("reasoningDetailsJson", "").takeIf { it.isNotBlank() }
            )
        }

        "tool_result" -> LlmMessage.ToolResult(
            toolCallId = obj.optString("toolCallId"),
            toolName = obj.optString("toolName"),
            contentJson = obj.optString("contentJson"),
            isError = obj.optBoolean("isError", false)
        )

        else -> null
    }

    companion object {
        private const val SCHEMA_VERSION = 1
    }
}
