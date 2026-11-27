package com.pocketworker.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketworker.app.core.ai.AiAgentBackgroundCoordinator
import com.pocketworker.app.core.ai.AiProxyConfig
import com.pocketworker.app.core.ai.AnthropicStreamingClient
import com.pocketworker.app.core.ai.ChangedFileSummary
import com.pocketworker.app.core.ai.GoogleAiStudioStreamingClient
import com.pocketworker.app.core.ai.LlmMessage
import com.pocketworker.app.core.ai.LlmRequest
import com.pocketworker.app.core.ai.LlmStreamEvent
import com.pocketworker.app.core.ai.LlmStreamingClient
import com.pocketworker.app.core.ai.LlmToolCall
import com.pocketworker.app.core.ai.LlmToolSpec
import com.pocketworker.app.core.ai.LocalGemmaLlmClient
import com.pocketworker.app.core.ai.LocalModelManager
import com.pocketworker.app.core.ai.McpUndoManager
import com.pocketworker.app.core.ai.McpUndoOperation
import com.pocketworker.app.core.ai.OpenAiStreamingClient
import com.pocketworker.app.core.ai.ProxyParseResult
import com.pocketworker.app.core.ai.TouchedFileRef
import com.pocketworker.app.core.ai.buildAgentSystemPrompt
import com.pocketworker.app.core.ai.parseAiProxyUrl
import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.mcp.McpConfirmationHandler
import com.pocketworker.app.core.mcp.McpConfirmationRequest
import com.pocketworker.app.core.mcp.McpErrorCodes
import com.pocketworker.app.core.mcp.McpPermissionTier
import com.pocketworker.app.core.mcp.PocketWorkerMcpServer
import com.pocketworker.app.core.storage.AiChatHistoryStore
import com.pocketworker.app.core.storage.AiProvider
import com.pocketworker.app.core.storage.AiTurnCheckpoint
import com.pocketworker.app.core.storage.AppPreferencesStore
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.InMemoryAiChatHistoryStore
import com.pocketworker.app.core.storage.PersistedAiChatData
import com.pocketworker.app.ui.workspace.DiffDialogState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

enum class ToolActivityCategory {
    SEARCHING,
    WEB_SEARCH,
    READING,
    DOING,
    GIT,
    CHECKING
}

enum class ToolActivityStatus {
    STREAMING_ARGS,
    AWAITING_CONFIRMATION,
    RUNNING,
    SUCCEEDED,
    FAILED,
    REJECTED
}

sealed interface AiChatMessageItem {
    val id: String
    val timestampMillis: Long

    data class UserMessage(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val attachmentNames: List<String> = emptyList(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AiChatMessageItem

    data class AssistantText(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val modelLabel: String,
        val isStreaming: Boolean = false,
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AiChatMessageItem

    data class ToolActivity(
        override val id: String = UUID.randomUUID().toString(),
        val toolCallId: String,
        val toolName: String,
        val category: ToolActivityCategory,
        val title: String,
        val subtitle: String = "",
        val status: ToolActivityStatus = ToolActivityStatus.STREAMING_ARGS,
        val argumentsPreview: String = "",
        val resultPreview: String? = null,
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AiChatMessageItem

    data class ActionsGroup(
        override val id: String = UUID.randomUUID().toString(),
        val turnId: String,
        val actions: List<ToolActivity>,
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AiChatMessageItem

    data class ChangedFilesCard(
        override val id: String = UUID.randomUUID().toString(),
        val turnId: String,
        val files: List<ChangedFileSummary>,
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AiChatMessageItem

    data class ErrorNotice(
        override val id: String = UUID.randomUUID().toString(),
        val message: String,
        val showSettingsAction: Boolean = false,
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AiChatMessageItem
}

data class PendingToolConfirmation(
    val chatId: String,
    val toolCallId: String,
    val request: McpConfirmationRequest
)

data class AiChatUiState(
    val sessions: List<AiChatSession> = listOf(
        AiChatSession(id = DEFAULT_CHAT_ID, title = AiChatSession.DEFAULT_TITLE)
    ),
    val activeChatId: String = DEFAULT_CHAT_ID,
    val messagesByChat: Map<String, List<AiChatMessageItem>> = emptyMap(),
    val busyChatId: String? = null,
    val loadingStatusText: String = "",
    val isRollingBack: Boolean = false,
    val pendingConfirmation: PendingToolConfirmation? = null,
    val activeProvider: AiProvider = AiProvider.ANTHROPIC,
    val activeModelLabel: String = AiProvider.ANTHROPIC.findModelOption(null).label,
    val hasApiKey: Boolean = false,
    val infoMessage: String? = null
) {
    val activeMessages: List<AiChatMessageItem>
        get() = messagesByChat[activeChatId].orEmpty()

    val isActiveChatBusy: Boolean
        get() = (busyChatId != null && busyChatId == activeChatId) || isRollingBack

    companion object {
        const val DEFAULT_CHAT_ID = "default_new_chat"
    }
}

class AiChatViewModel(
    private val preferencesStore: AppPreferencesStore,
    private val credentialStore: CredentialStore,
    private val mcpServer: PocketWorkerMcpServer,
    private val chatHistoryStore: AiChatHistoryStore = InMemoryAiChatHistoryStore(),
    private val undoManager: McpUndoManager? = null,
    private val backgroundCoordinator: AiAgentBackgroundCoordinator? = null,
    private val localModelManager: LocalModelManager? = null,
    private val localGemmaClient: LocalGemmaLlmClient? = null,
    private val onWorkspaceMutated: () -> Unit = {},
    private val clientResolver: ((AiProvider) -> LlmStreamingClient?)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private fun resolveClient(provider: AiProvider): LlmStreamingClient? {
        clientResolver?.let { return it(provider) }
        return when (provider) {
            AiProvider.ANTHROPIC -> AnthropicStreamingClient()
            AiProvider.GEMINI -> GoogleAiStudioStreamingClient()
            AiProvider.OPENROUTER -> OpenAiStreamingClient.forOpenRouter()
            AiProvider.OPENAI -> OpenAiStreamingClient()
            AiProvider.LOCAL -> localGemmaClient
        }
    }

    private fun isProviderReady(provider: AiProvider, modelId: String): Boolean {
        return if (provider == AiProvider.LOCAL) {
            localModelManager?.isModelDownloaded(modelId) == true
        } else {
            !credentialStore.getAiApiKey(provider).isNullOrBlank()
        }
    }

    private val llmHistoryByChat = mutableMapOf<String, MutableList<LlmMessage>>()
    private val checkpointsByChat = mutableMapOf<String, MutableList<AiTurnCheckpoint>>()
    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeConfirmationDeferred: CompletableDeferred<Boolean>? = null

    init {
        backgroundCoordinator?.registerCancelCallback { stopGeneration() }
        viewModelScope.launch {
            preferencesStore.preferences.collect {
                refreshProviderBadge()
            }
        }
    }

    override fun onCleared() {
        backgroundCoordinator?.registerCancelCallback(null)
        super.onCleared()
    }

    fun refreshProviderBadge() {
        val aiSettings = preferencesStore.preferences.value.aiSettings
        val provider = aiSettings.provider
        val modelOption = provider.findModelOption(aiSettings.modelName)
        val ready = isProviderReady(provider, modelOption.id)
        _uiState.update {
            it.copy(
                activeProvider = provider,
                activeModelLabel = modelOption.label,
                hasApiKey = ready
            )
        }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun selectChat(chatId: String) {
        if (_uiState.value.sessions.any { it.id == chatId }) {
            _uiState.update { it.copy(activeChatId = chatId) }
            backgroundCoordinator?.clearFinishedNotification(chatId)
            persistChatHistoryAsync()
        }
    }

    fun openChatFromNotification(chatId: String) {
        if (_uiState.value.sessions.any { it.id == chatId }) {
            _uiState.update { it.copy(activeChatId = chatId) }
            backgroundCoordinator?.clearFinishedNotification(chatId)
            persistChatHistoryAsync()
        }
    }

    fun renameChat(chatId: String, newTitle: String) {
        val clean = newTitle.replace('\n', ' ').trim().take(80)
        if (clean.isEmpty()) return
        var changed = false
        _uiState.update { current ->
            val updated = current.sessions.map { session ->
                if (session.id == chatId && session.title != clean) {
                    changed = true
                    session.copy(title = clean)
                } else {
                    session
                }
            }
            if (changed) current.copy(sessions = updated) else current
        }
        if (changed) {
            persistChatHistoryAsync()
        }
    }

    fun startNewChat(initialContextKey: String = "") {
        val existingEmpty = _uiState.value.sessions.firstOrNull { session ->
            session.isUntitledDefault() &&
                _uiState.value.messagesByChat[session.id].orEmpty().isEmpty()
        }
        if (existingEmpty != null) {
            _uiState.update { current ->
                val updatedSessions = if (initialContextKey.isNotBlank()) {
                    current.sessions.map { s ->
                        if (s.id == existingEmpty.id) s.copy(lastContextKey = initialContextKey) else s
                    }
                } else {
                    current.sessions
                }
                current.copy(
                    sessions = updatedSessions,
                    activeChatId = existingEmpty.id
                )
            }
            persistChatHistoryAsync()
            return
        }
        val created = AiChatSession(
            id = UUID.randomUUID().toString(),
            title = AiChatSession.DEFAULT_TITLE,
            lastContextKey = initialContextKey
        )
        _uiState.update { current ->
            current.copy(
                sessions = listOf(created) + current.sessions,
                activeChatId = created.id
            )
        }
        persistChatHistoryAsync()
    }

    fun onChatWindowOpened(locationContext: AiLocationContext) {
        val activeChatId = _uiState.value.activeChatId
        backgroundCoordinator?.clearFinishedNotification(activeChatId)
    }

    fun deleteChat(chatId: String) {
        if (_uiState.value.busyChatId == chatId) {
            stopGeneration()
        }
        llmHistoryByChat.remove(chatId)
        checkpointsByChat.remove(chatId)
        _uiState.update { current ->
            val remaining = current.sessions.filterNot { it.id == chatId }
            val nextSessions = remaining.ifEmpty {
                listOf(AiChatSession(id = UUID.randomUUID().toString(), title = AiChatSession.DEFAULT_TITLE))
            }
            val nextActive = if (current.activeChatId == chatId) nextSessions.first().id else current.activeChatId
            current.copy(
                sessions = nextSessions,
                activeChatId = nextActive,
                messagesByChat = current.messagesByChat - chatId
            )
        }
        persistChatHistoryAsync()
    }

    fun resolveConfirmation(approved: Boolean) {
        val deferred = activeConfirmationDeferred
        activeConfirmationDeferred = null
        _uiState.update { it.copy(pendingConfirmation = null) }
        deferred?.complete(approved)
    }

    fun stopGeneration() {
        activeConfirmationDeferred?.complete(false)
        activeConfirmationDeferred = null
        activeJob?.cancel()
        activeJob = null
        val busyId = _uiState.value.busyChatId
        if (busyId != null) {
            finishStreamingBubbles(busyId)
            backgroundCoordinator?.onAgentFinished(
                chatId = busyId,
                chatTitle = currentSessionTitle(busyId),
                wasCancelled = true,
                hadError = false
            )
        }
        _uiState.update { it.copy(busyChatId = null, pendingConfirmation = null) }
        persistChatHistoryAsync()
    }

    /**
     * Rolls back all workspace / file / Git changes recorded from [userMessageId] onwards,
     * truncates UI and LLM history strictly before that user message, and delivers the original
     * message text back into the composer via [onTextRestored].
     */
    fun rewindToUserMessage(
        userMessageId: String,
        onTextRestored: (String) -> Unit
    ) {
        if (_uiState.value.busyChatId != null || _uiState.value.isRollingBack) return

        val chatId = _uiState.value.activeChatId
        val items = _uiState.value.messagesByChat[chatId].orEmpty()
        val targetIndex = items.indexOfFirst { it is AiChatMessageItem.UserMessage && it.id == userMessageId }
        if (targetIndex < 0) return
        val targetUserMsg = items[targetIndex] as AiChatMessageItem.UserMessage

        val checkpoints = checkpointsByChat[chatId].orEmpty()
        val affectedUserIds = items.subList(targetIndex, items.size)
            .filterIsInstance<AiChatMessageItem.UserMessage>()
            .map { it.id }
            .toSet()
        val cpIndex = checkpoints.indexOfFirst { it.userMessageId in affectedUserIds }
        val opsToRollback = if (cpIndex >= 0) {
            checkpoints.subList(cpIndex, checkpoints.size).flatMap { it.undoOperations }
        } else {
            emptyList()
        }

        viewModelScope.launch {
            val s = AppLocale.current
            _uiState.update { it.copy(isRollingBack = true, loadingStatusText = s.aiRollingBack) }
            val rollbackSummary = if (opsToRollback.isNotEmpty() && undoManager != null) {
                withContext(ioDispatcher) {
                    undoManager.rollbackOperations(opsToRollback)
                }
            } else {
                null
            }

            // Truncate UI messages strictly before targetUserMsg (keeps AI answer right before it)
            val truncatedItems = items.subList(0, targetIndex)

            // Truncate LLM history strictly before targetUserMsg
            val history = llmHistoryByChat.getOrPut(chatId) { mutableListOf() }
            if (cpIndex >= 0) {
                val targetLlmStart = checkpoints[cpIndex].llmHistoryStartIndex.coerceIn(0, history.size)
                while (history.size > targetLlmStart) {
                    history.removeAt(history.lastIndex)
                }
                val mutableCps = checkpointsByChat[chatId]
                if (mutableCps != null) {
                    while (mutableCps.size > cpIndex) {
                        mutableCps.removeAt(mutableCps.lastIndex)
                    }
                }
            } else if (truncatedItems.isEmpty()) {
                history.clear()
            }

            val updatedPreview = truncatedItems
                .lastOrNull { it is AiChatMessageItem.AssistantText || it is AiChatMessageItem.UserMessage }
                ?.let {
                    when (it) {
                        is AiChatMessageItem.AssistantText -> it.text
                        is AiChatMessageItem.UserMessage -> it.text
                        else -> ""
                    }
                }.orEmpty().replace('\n', ' ').trim().take(64)

            val notice = buildString {
                append(s.aiReturnedToInput)
                if (rollbackSummary != null && rollbackSummary.appliedOperations > 0) {
                    append(s.aiRollbackCountSuffix(rollbackSummary.appliedOperations))
                }
                if (rollbackSummary != null && rollbackSummary.remoteWarnings.isNotEmpty()) {
                    append("\n${rollbackSummary.remoteWarnings.joinToString("; ")}")
                }
            }

            _uiState.update { current ->
                val updatedSessions = current.sessions.map { s ->
                    if (s.id == chatId) {
                        val nextTitle = if (truncatedItems.isEmpty()) AiChatSession.DEFAULT_TITLE else s.title
                        s.copy(title = nextTitle, preview = updatedPreview)
                    } else s
                }
                current.copy(
                    sessions = updatedSessions,
                    messagesByChat = current.messagesByChat + (chatId to truncatedItems),
                    isRollingBack = false,
                    infoMessage = notice
                )
            }

            if (rollbackSummary != null && rollbackSummary.appliedOperations > 0) {
                onWorkspaceMutated()
            }
            persistChatHistoryAsync()
            onTextRestored(targetUserMsg.text)
        }
    }

    fun loadChangedFileDiff(
        file: ChangedFileSummary,
        onLoaded: (DiffDialogState) -> Unit
    ) {
        viewModelScope.launch {
            val s = AppLocale.current
            val diffText = withContext(ioDispatcher) {
                undoManager?.loadLiveDiff(file) ?: file.diffSnapshot.ifBlank {
                    s.aiNoSavedDiff(file.relativePath)
                }
            }
            onLoaded(
                DiffDialogState(
                    title = "${file.relativePath} (+${file.addedLines} -${file.deletedLines})",
                    diffText = diffText
                )
            )
        }
    }

    fun sendMessage(
        rawText: String,
        attachmentsSummary: List<String> = emptyList(),
        attachmentsContextText: String = "",
        locationContext: AiLocationContext? = null
    ) {
        val cleanText = rawText.trim()
        if (cleanText.isEmpty() && attachmentsSummary.isEmpty()) return
        if (_uiState.value.busyChatId != null || _uiState.value.isRollingBack) return

        val s = AppLocale.current
        val chatId = _uiState.value.activeChatId
        val aiSettings = preferencesStore.preferences.value.aiSettings
        val provider = aiSettings.provider
        val modelOption = provider.findModelOption(aiSettings.modelName)

        if (locationContext != null) {
            val ctxKey = locationContext.stableContextKey()
            _uiState.update { current ->
                val updated = current.sessions.map { sess ->
                    if (sess.id == chatId) sess.copy(lastContextKey = ctxKey) else sess
                }
                current.copy(sessions = updated)
            }
        }

        val userDisplay = cleanText.ifEmpty { s.aiAttachedFilesSummary(attachmentsSummary.size) }
        val userMessageItem = AiChatMessageItem.UserMessage(
            text = userDisplay,
            attachmentNames = attachmentsSummary
        )
        appendMessage(chatId = chatId, item = userMessageItem)
        updateSessionMeta(chatId, userDisplay)

        val client = resolveClient(provider)
        if (client == null) {
            appendMessage(
                chatId = chatId,
                item = AiChatMessageItem.ErrorNotice(
                    message = s.aiProviderNotConnected(provider.displayName),
                    showSettingsAction = true
                )
            )
            persistChatHistoryAsync()
            return
        }

        val apiKey: String
        val activeProxy: AiProxyConfig?
        if (provider == AiProvider.LOCAL) {
            if (localModelManager != null && !localModelManager.isModelDownloaded(modelOption.id)) {
                appendMessage(
                    chatId = chatId,
                    item = AiChatMessageItem.ErrorNotice(
                        message = s.aiLocalModelNotDownloaded(modelOption.label),
                        showSettingsAction = true
                    )
                )
                persistChatHistoryAsync()
                return
            }
            apiKey = ""
            activeProxy = null
        } else {
            apiKey = credentialStore.getAiApiKey(provider)?.trim().orEmpty()
            if (apiKey.isEmpty()) {
                val keyHint = provider.displayName
                appendMessage(
                    chatId = chatId,
                    item = AiChatMessageItem.ErrorNotice(
                        message = s.aiMissingApiKey(keyHint),
                        showSettingsAction = true
                    )
                )
                persistChatHistoryAsync()
                return
            }

            activeProxy = if (aiSettings.proxyEnabled) {
                val rawProxy = credentialStore.getAiProxyUrl().orEmpty()
                when (val parsed = parseAiProxyUrl(rawProxy)) {
                    is ProxyParseResult.Valid -> parsed.config
                    is ProxyParseResult.Invalid -> {
                        appendMessage(
                            chatId = chatId,
                            item = AiChatMessageItem.ErrorNotice(
                                message = s.aiInvalidProxy(parsed.message),
                                showSettingsAction = true
                            )
                        )
                        persistChatHistoryAsync()
                        return
                    }
                }
            } else {
                null
            }
        }

        val promptForLlm = buildString {
            if (cleanText.isNotEmpty()) append(cleanText)
            if (attachmentsContextText.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(attachmentsContextText.trim())
            }
        }

        val history = llmHistoryByChat.getOrPut(chatId) { mutableListOf() }
        val historyIndexBeforeTurn = history.size
        history.add(LlmMessage.User(promptForLlm))

        val turnCheckpoint = AiTurnCheckpoint(
            userMessageId = userMessageItem.id,
            llmHistoryStartIndex = historyIndexBeforeTurn,
            undoOperations = emptyList()
        )
        val chatCheckpoints = checkpointsByChat.getOrPut(chatId) { mutableListOf() }
        chatCheckpoints.add(turnCheckpoint)
        persistChatHistoryAsync()

        val systemPrompt = buildAgentSystemPrompt(
            shareWorkspaceContext = aiSettings.shareWorkspaceContext,
            userCustomPrompt = aiSettings.systemPrompt
        )
        val tools = mcpServer.listTools()
            .filter { def -> aiSettings.shareWorkspaceContext || def.name != "workspace_get_context" }
            .map { LlmToolSpec.fromMcp(it) }

        val turnId = userMessageItem.id
        val turnToolCardIds = mutableListOf<String>()
        val turnUndoOps = mutableListOf<McpUndoOperation>()
        val turnTouchedFiles = linkedMapOf<String, TouchedFileRef>()

        val initialLoadingText = if (provider == AiProvider.LOCAL && localGemmaClient?.isModelLoaded(modelOption.id) == false) {
            s.aiLoadingLocalModel
        } else {
            s.aiThinking
        }

        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyChatId = chatId,
                    loadingStatusText = initialLoadingText,
                    activeProvider = provider,
                    activeModelLabel = modelOption.label,
                    hasApiKey = true
                )
            }
            backgroundCoordinator?.onAgentStarted(
                chatId = chatId,
                chatTitle = currentSessionTitle(chatId),
                statusText = initialLoadingText
            )
            var interruptingError: AiChatMessageItem.ErrorNotice? = null
            var wasCancelled = false
            try {
                interruptingError = runAgentLoop(
                    chatId = chatId,
                    turnId = turnId,
                    client = client,
                    modelId = modelOption.id,
                    modelLabel = modelOption.label,
                    apiKey = apiKey,
                    proxy = activeProxy,
                    systemPrompt = systemPrompt,
                    tools = tools,
                    history = history,
                    turnToolCardIds = turnToolCardIds,
                    turnUndoOps = turnUndoOps,
                    turnTouchedFiles = turnTouchedFiles
                )
            } catch (_: CancellationException) {
                wasCancelled = true
                finishStreamingBubbles(chatId)
            } catch (ex: Throwable) {
                finishStreamingBubbles(chatId)
                interruptingError = AiChatMessageItem.ErrorNotice(
                    message = ex.localizedMessage ?: s.aiUnexpectedError,
                    showSettingsAction = false
                )
            } finally {
                // Save accumulated undo operations on this turn's checkpoint
                val cpList = checkpointsByChat[chatId]
                val cpIdx = cpList?.indexOfLast { it.userMessageId == turnId } ?: -1
                if (cpList != null && cpIdx >= 0) {
                    cpList[cpIdx] = cpList[cpIdx].copy(undoOperations = turnUndoOps.toList())
                }

                // Collapse contiguous tool runs into chronological "Действия (N)" groups preserving intermediate AI messages
                if (turnToolCardIds.isNotEmpty()) {
                    collapseTurnActions(chatId, turnId, turnToolCardIds)
                }

                // Compute Codex-style changed files card (+added / -deleted from Git)
                if (turnTouchedFiles.isNotEmpty() && undoManager != null) {
                    val summaries = runCatching {
                        withContext(NonCancellable + ioDispatcher) {
                            turnTouchedFiles.values.map { ref ->
                                undoManager.computeChangedFileSummary(ref)
                            }
                        }
                    }.getOrDefault(emptyList())
                    if (summaries.isNotEmpty()) {
                        appendMessage(
                            chatId = chatId,
                            item = AiChatMessageItem.ChangedFilesCard(
                                turnId = turnId,
                                files = summaries
                            )
                        )
                    }
                }

                // Any error interrupting the agent is always appended at the very end, even after ChangedFilesCard
                interruptingError?.let { errNotice ->
                    appendMessage(chatId = chatId, item = errNotice)
                }

                val finalPreview = interruptingError?.message
                    ?: _uiState.value.messagesByChat[chatId]
                        .orEmpty()
                        .lastOrNull { it is AiChatMessageItem.AssistantText }
                        ?.let { (it as AiChatMessageItem.AssistantText).text }
                        .orEmpty()

                backgroundCoordinator?.onAgentFinished(
                    chatId = chatId,
                    chatTitle = currentSessionTitle(chatId),
                    wasCancelled = wasCancelled,
                    hadError = interruptingError != null,
                    previewText = finalPreview
                )

                activeConfirmationDeferred = null
                _uiState.update { it.copy(busyChatId = null, pendingConfirmation = null) }
                activeJob = null
                persistChatHistoryAsync()
            }
        }
    }

    private fun currentSessionTitle(chatId: String): String {
        val s = AppLocale.current
        return _uiState.value.sessions.firstOrNull { it.id == chatId }?.localizedTitle(s) ?: s.aiNewChat
    }

    private fun updateLoadingStatus(chatId: String, statusText: String) {
        _uiState.update { it.copy(loadingStatusText = statusText) }
        backgroundCoordinator?.onAgentStatusChanged(
            chatId = chatId,
            chatTitle = currentSessionTitle(chatId),
            statusText = statusText
        )
    }

    private suspend fun runAgentLoop(
        chatId: String,
        turnId: String,
        client: LlmStreamingClient,
        modelId: String,
        modelLabel: String,
        apiKey: String,
        proxy: AiProxyConfig?,
        systemPrompt: String,
        tools: List<LlmToolSpec>,
        history: MutableList<LlmMessage>,
        turnToolCardIds: MutableList<String>,
        turnUndoOps: MutableList<McpUndoOperation>,
        turnTouchedFiles: MutableMap<String, TouchedFileRef>
    ): AiChatMessageItem.ErrorNotice? {
        var step = 0
        while (step < MAX_TOOL_STEPS) {
            step++
            if (step == 1) {
                val s = AppLocale.current
                val firstStatus = if (localGemmaClient != null &&
                    preferencesStore.preferences.value.aiSettings.provider == AiProvider.LOCAL &&
                    !localGemmaClient.isModelLoaded(modelId)
                ) {
                    s.aiLoadingLocalModel
                } else {
                    s.aiThinking
                }
                updateLoadingStatus(chatId, firstStatus)
            } else {
                updateLoadingStatus(chatId, AppLocale.current.aiAnalyzingResult)
            }
            val request = LlmRequest(
                model = modelId,
                systemPrompt = systemPrompt,
                messages = history.toList(),
                tools = tools,
                apiKey = apiKey,
                proxy = proxy
            )

            val turnText = StringBuilder()
            var streamingBubbleId: String? = null
            val completedToolCalls = mutableListOf<LlmToolCall>()
            val toolCardIdsByCallId = mutableMapOf<String, String>()
            val toolArgBuffers = mutableMapOf<String, StringBuilder>()
            var capturedReasoningDetails: String? = null
            var streamError: LlmStreamEvent.Error? = null

            try {
                client.streamChat(request)
                    .flowOn(ioDispatcher)
                    .collect { event ->
                        when (event) {
                            is LlmStreamEvent.TextDelta -> {
                                turnText.append(event.text)
                                val currentText = turnText.toString()
                                val bubbleId = streamingBubbleId
                                if (bubbleId == null) {
                                    // Avoid creating empty whitespace-only AI bubbles before tool calls
                                    if (currentText.isNotBlank()) {
                                        updateLoadingStatus(chatId, AppLocale.current.aiResponding)
                                        val newId = UUID.randomUUID().toString()
                                        streamingBubbleId = newId
                                        appendMessage(
                                            chatId = chatId,
                                            item = AiChatMessageItem.AssistantText(
                                                id = newId,
                                                text = currentText.trimStart(),
                                                modelLabel = modelLabel,
                                                isStreaming = true
                                            )
                                        )
                                    }
                                } else {
                                    updateLoadingStatus(chatId, AppLocale.current.aiResponding)
                                    updateMessage(chatId, bubbleId) { item ->
                                        if (item is AiChatMessageItem.AssistantText) {
                                            item.copy(text = currentText.trimStart(), isStreaming = true)
                                        } else item
                                    }
                                }
                            }

                            is LlmStreamEvent.ToolUseStarted -> {
                                streamingBubbleId?.let { id ->
                                    updateMessage(chatId, id) { item ->
                                        if (item is AiChatMessageItem.AssistantText) item.copy(isStreaming = false) else item
                                    }
                                    streamingBubbleId = null
                                }

                                val cardId = UUID.randomUUID().toString()
                                toolCardIdsByCallId[event.id] = cardId
                                turnToolCardIds.add(cardId)
                                toolArgBuffers[event.id] = StringBuilder()
                                val meta = describeToolStart(event.name, event.isWebSearch)
                                updateLoadingStatus(chatId, meta.runningTitle)
                                appendMessage(
                                    chatId = chatId,
                                    item = AiChatMessageItem.ToolActivity(
                                        id = cardId,
                                        toolCallId = event.id,
                                        toolName = event.name,
                                        category = meta.category,
                                        title = meta.runningTitle,
                                        subtitle = "",
                                        status = ToolActivityStatus.STREAMING_ARGS
                                    )
                                )
                            }

                            is LlmStreamEvent.ToolUseInputDelta -> {
                                val buf = toolArgBuffers.getOrPut(event.id) { StringBuilder() }
                                buf.append(event.partialJson)
                                val cardId = toolCardIdsByCallId[event.id] ?: return@collect
                                updateMessage(chatId, cardId) { item ->
                                    if (item is AiChatMessageItem.ToolActivity) {
                                        item.copy(argumentsPreview = buf.toString())
                                    } else item
                                }
                            }

                            is LlmStreamEvent.ToolUseCompleted -> {
                                completedToolCalls.add(event.call)
                                val cardId = toolCardIdsByCallId[event.call.id]
                                val parsedArgs = runCatching { JSONObject(event.call.argumentsJson) }.getOrElse { JSONObject() }
                                val prettyArgs = runCatching { parsedArgs.toString(2) }.getOrDefault(event.call.argumentsJson)
                                val subtitle = extractToolSubtitle(event.call.name, parsedArgs)
                                val meta = describeToolStart(event.call.name, isWebSearch = false)
                                if (cardId != null) {
                                    updateMessage(chatId, cardId) { item ->
                                        if (item is AiChatMessageItem.ToolActivity) {
                                            item.copy(
                                                toolName = event.call.name,
                                                category = meta.category,
                                                title = meta.runningTitle,
                                                subtitle = subtitle,
                                                argumentsPreview = prettyArgs,
                                                status = ToolActivityStatus.RUNNING
                                            )
                                        } else item
                                    }
                                } else {
                                    val newCardId = UUID.randomUUID().toString()
                                    toolCardIdsByCallId[event.call.id] = newCardId
                                    turnToolCardIds.add(newCardId)
                                    appendMessage(
                                        chatId = chatId,
                                        item = AiChatMessageItem.ToolActivity(
                                            id = newCardId,
                                            toolCallId = event.call.id,
                                            toolName = event.call.name,
                                            category = meta.category,
                                            title = meta.runningTitle,
                                            subtitle = subtitle,
                                            status = ToolActivityStatus.RUNNING,
                                            argumentsPreview = prettyArgs
                                        )
                                    )
                                }
                            }

                            is LlmStreamEvent.ReasoningDetailsCaptured -> {
                                capturedReasoningDetails = event.reasoningDetailsJson
                            }

                            is LlmStreamEvent.WebSearchUpdate -> {
                                val cardId = toolCardIdsByCallId[event.id] ?: return@collect
                                val s = AppLocale.current
                                updateMessage(chatId, cardId) { item ->
                                    if (item is AiChatMessageItem.ToolActivity) {
                                        item.copy(
                                            title = if (event.finished) s.aiWebSearchDone else s.aiWebSearchRunning,
                                            subtitle = event.query?.let { "«$it»" } ?: item.subtitle,
                                            status = if (event.finished) ToolActivityStatus.SUCCEEDED else ToolActivityStatus.RUNNING
                                        )
                                    } else item
                                }
                            }

                            is LlmStreamEvent.MessageFinished -> {
                                finishStreamingBubbles(chatId)
                            }

                            is LlmStreamEvent.Error -> {
                                streamError = event
                                finishStreamingBubbles(chatId)
                            }
                        }
                    }
            } catch (ce: CancellationException) {
                val partial = turnText.toString()
                if (partial.isNotBlank()) {
                    history.add(LlmMessage.Assistant(text = partial, reasoningDetailsJson = capturedReasoningDetails))
                    updateSessionPreview(chatId, partial)
                } else if (step == 1 && history.lastOrNull() is LlmMessage.User) {
                    history.removeAt(history.lastIndex)
                }
                throw ce
            }

            val err = streamError
            if (err != null) {
                val partial = turnText.toString()
                if (partial.isNotBlank()) {
                    history.add(LlmMessage.Assistant(text = partial, reasoningDetailsJson = capturedReasoningDetails))
                } else if (step == 1 && history.lastOrNull() is LlmMessage.User) {
                    history.removeAt(history.lastIndex)
                }
                val isAuthErr = err.statusCode == 401 || err.statusCode == 403 || err.statusCode == 404 || err.statusCode == 407
                return AiChatMessageItem.ErrorNotice(
                    message = err.message,
                    showSettingsAction = isAuthErr
                )
            }

            val assistantTextStr = turnText.toString()
            if (assistantTextStr.isNotEmpty() || completedToolCalls.isNotEmpty()) {
                history.add(
                    LlmMessage.Assistant(
                        text = assistantTextStr,
                        toolCalls = completedToolCalls.toList(),
                        reasoningDetailsJson = capturedReasoningDetails
                    )
                )
                if (assistantTextStr.isNotBlank()) {
                    updateSessionPreview(chatId, assistantTextStr)
                }
            }

            if (completedToolCalls.isEmpty()) {
                return null
            }

            for (call in completedToolCalls) {
                val cardId = toolCardIdsByCallId[call.id]
                val argsJson = runCatching { JSONObject(call.argumentsJson) }.getOrElse { JSONObject() }
                val tier = mcpServer.classifyToolCall(call.name, argsJson)
                val meta = describeToolStart(call.name, isWebSearch = false)
                updateLoadingStatus(chatId, meta.runningTitle)

                val preparedUndo = if (undoManager != null) {
                    withContext(ioDispatcher) {
                        undoManager.captureBeforeTool(call.name, argsJson)
                    }
                } else null

                val confirmationHandler = McpConfirmationHandler { req ->
                    val s = AppLocale.current
                    updateLoadingStatus(chatId, s.aiWaitingConfirmationStatus(req.summary))
                    if (cardId != null) {
                        updateMessage(chatId, cardId) { item ->
                            if (item is AiChatMessageItem.ToolActivity) {
                                item.copy(
                                    title = s.aiWaitingConfirmationTitle,
                                    subtitle = req.summary,
                                    status = ToolActivityStatus.AWAITING_CONFIRMATION
                                )
                            } else item
                        }
                    }
                    val deferred = CompletableDeferred<Boolean>()
                    activeConfirmationDeferred = deferred
                    _uiState.update {
                        it.copy(
                            pendingConfirmation = PendingToolConfirmation(
                                chatId = chatId,
                                toolCallId = call.id,
                                request = req
                            )
                        )
                    }
                    val approved = deferred.await()
                    if (approved) {
                        updateLoadingStatus(chatId, meta.runningTitle)
                    }
                    if (cardId != null && approved) {
                        updateMessage(chatId, cardId) { item ->
                            if (item is AiChatMessageItem.ToolActivity) {
                                item.copy(
                                    title = meta.runningTitle,
                                    status = ToolActivityStatus.RUNNING
                                )
                            } else item
                        }
                    }
                    approved
                }

                val result = mcpServer.callTool(
                    name = call.name,
                    arguments = argsJson,
                    confirmationHandler = confirmationHandler
                )

                if (preparedUndo != null && undoManager != null) {
                    val (undoOps, touchedFiles) = withContext(ioDispatcher) {
                        undoManager.finalizeAfterTool(preparedUndo, result)
                    }
                    if (undoOps.isNotEmpty()) {
                        turnUndoOps.addAll(undoOps)
                    }
                    touchedFiles.forEach { ref ->
                        val key = "${ref.repoId}:${ref.relativePath}"
                        // Preserve earliest pre-turn blob snapshot for accurate full-turn diff
                        if (!turnTouchedFiles.containsKey(key)) {
                            turnTouchedFiles[key] = ref
                        }
                    }
                }

                val resultJsonStr = result.toJsonString(indentSpaces = 0)
                val prettyResult = result.toJsonString(indentSpaces = 2).take(MAX_PREVIEW_CHARS)

                if (result.ok && isMutatingToolCall(call.name, argsJson, tier)) {
                    onWorkspaceMutated()
                }

                if (cardId != null) {
                    val s = AppLocale.current
                    val (finalStatus, finalTitle) = when {
                        result.ok -> ToolActivityStatus.SUCCEEDED to meta.doneTitle
                        result.error?.errorCode == McpErrorCodes.USER_REJECTED ->
                            ToolActivityStatus.REJECTED to s.aiRejectedByUser
                        else -> ToolActivityStatus.FAILED to s.aiToolErrorTitle(result.error?.errorCode ?: call.name)
                    }
                    updateMessage(chatId, cardId) { item ->
                        if (item is AiChatMessageItem.ToolActivity) {
                            item.copy(
                                title = finalTitle,
                                status = finalStatus,
                                resultPreview = prettyResult
                            )
                        } else item
                    }
                }

                history.add(
                    LlmMessage.ToolResult(
                        toolCallId = call.id,
                        toolName = call.name,
                        contentJson = resultJsonStr.take(MAX_TOOL_RESULT_CHARS),
                        isError = !result.ok
                    )
                )
            }
        }

        return AiChatMessageItem.ErrorNotice(
            message = AppLocale.current.aiMaxStepsReached(MAX_TOOL_STEPS),
            showSettingsAction = false
        )
    }

    /**
     * Collapses each contiguous run of 2+ of this turn's [AiChatMessageItem.ToolActivity] cards into its own
     * [AiChatMessageItem.ActionsGroup] in place, preserving chronological order relative to any
     * intermediate assistant text messages (e.g. [Group(2), AssistantText, Group(5)]).
     * Runs with a single action stay as a standalone [AiChatMessageItem.ToolActivity].
     */
    private fun collapseTurnActions(
        chatId: String,
        turnId: String,
        toolCardIds: List<String>
    ) {
        val idSet = toolCardIds.toSet()
        _uiState.update { current ->
            val list = current.messagesByChat[chatId].orEmpty()
            if (list.none { it is AiChatMessageItem.ToolActivity && it.id in idSet }) {
                return@update current
            }

            val rebuilt = mutableListOf<AiChatMessageItem>()
            val pendingRun = mutableListOf<AiChatMessageItem.ToolActivity>()

            fun flushRun() {
                if (pendingRun.size > 1) {
                    rebuilt.add(
                        AiChatMessageItem.ActionsGroup(
                            turnId = turnId,
                            actions = pendingRun.toList(),
                            timestampMillis = pendingRun.first().timestampMillis
                        )
                    )
                } else if (pendingRun.size == 1) {
                    rebuilt.add(pendingRun.first())
                }
                pendingRun.clear()
            }

            for (item in list) {
                if (item is AiChatMessageItem.ToolActivity && item.id in idSet) {
                    pendingRun.add(item)
                } else {
                    flushRun()
                    rebuilt.add(item)
                }
            }
            flushRun()

            current.copy(messagesByChat = current.messagesByChat + (chatId to rebuilt))
        }
    }

    private fun buildInitialState(): AiChatUiState {
        val aiSettings = preferencesStore.preferences.value.aiSettings
        val provider = aiSettings.provider
        val modelOption = provider.findModelOption(aiSettings.modelName)
        val ready = isProviderReady(provider, modelOption.id)
        val persisted = chatHistoryStore.load()

        if (persisted != null) {
            persisted.llmHistoryByChat.forEach { (chatId, list) ->
                llmHistoryByChat[chatId] = list.toMutableList()
            }
            persisted.checkpointsByChat.forEach { (chatId, list) ->
                checkpointsByChat[chatId] = list.toMutableList()
            }
            return AiChatUiState(
                sessions = persisted.sessions,
                activeChatId = persisted.activeChatId,
                messagesByChat = persisted.messagesByChat,
                activeProvider = provider,
                activeModelLabel = modelOption.label,
                hasApiKey = ready
            )
        }

        return AiChatUiState(
            activeProvider = provider,
            activeModelLabel = modelOption.label,
            hasApiKey = ready
        )
    }

    private fun persistChatHistoryAsync() {
        val current = _uiState.value
        val snapshot = PersistedAiChatData(
            sessions = current.sessions,
            activeChatId = current.activeChatId,
            messagesByChat = current.messagesByChat,
            llmHistoryByChat = llmHistoryByChat.mapValues { it.value.toList() },
            checkpointsByChat = checkpointsByChat.mapValues { it.value.toList() }
        )
        viewModelScope.launch(ioDispatcher) {
            chatHistoryStore.save(snapshot)
        }
    }

    private fun appendMessage(chatId: String, item: AiChatMessageItem) {
        _uiState.update { current ->
            val list = current.messagesByChat[chatId].orEmpty() + item
            current.copy(messagesByChat = current.messagesByChat + (chatId to list))
        }
    }

    private fun updateMessage(
        chatId: String,
        messageId: String,
        transform: (AiChatMessageItem) -> AiChatMessageItem
    ) {
        _uiState.update { current ->
            val list = current.messagesByChat[chatId].orEmpty()
            val updated = list.map { if (it.id == messageId) transform(it) else it }
            current.copy(messagesByChat = current.messagesByChat + (chatId to updated))
        }
    }

    private fun finishStreamingBubbles(chatId: String) {
        _uiState.update { current ->
            val list = current.messagesByChat[chatId].orEmpty()
            val updated = list.map { item ->
                if (item is AiChatMessageItem.AssistantText && item.isStreaming) {
                    item.copy(isStreaming = false)
                } else {
                    item
                }
            }
            current.copy(messagesByChat = current.messagesByChat + (chatId to updated))
        }
    }

    private fun updateSessionMeta(chatId: String, firstPrompt: String) {
        val compact = firstPrompt.replace('\n', ' ').trim()
        val autoTitle = if (compact.length > 36) compact.take(36) + "…" else compact
        _uiState.update { current ->
            val updatedSessions = current.sessions.map { s ->
                if (s.id == chatId) {
                    val nextTitle = if (s.isUntitledDefault() && autoTitle.isNotBlank()) {
                        autoTitle
                    } else s.title
                    s.copy(title = nextTitle, preview = compact.take(64))
                } else s
            }
            current.copy(sessions = updatedSessions)
        }
    }

    private fun updateSessionPreview(chatId: String, reply: String) {
        val compact = reply.replace('\n', ' ').trim().take(64)
        if (compact.isEmpty()) return
        _uiState.update { current ->
            val updated = current.sessions.map { s ->
                if (s.id == chatId) s.copy(preview = compact) else s
            }
            current.copy(sessions = updated)
        }
    }

    private fun isMutatingToolCall(
        toolName: String,
        args: JSONObject,
        tier: McpPermissionTier?
    ): Boolean {
        if (args.optBoolean("dryRun", false)) return false
        return when (toolName) {
            "workspace_create_project",
            "workspace_create_repo",
            "workspace_clone_repo",
            "fs_edit_file",
            "fs_write_file",
            "fs_replace_across_files",
            "fs_manage_structure",
            "fs_delete_entry" -> true
            "git_exec" -> tier != McpPermissionTier.AUTO
            else -> false
        }
    }

    private data class ToolVisualMeta(
        val category: ToolActivityCategory,
        val runningTitle: String,
        val doneTitle: String
    )

    private fun describeToolStart(toolName: String, isWebSearch: Boolean): ToolVisualMeta {
        val s = AppLocale.current
        if (isWebSearch || toolName.contains("web_search", ignoreCase = true)) {
            return ToolVisualMeta(ToolActivityCategory.WEB_SEARCH, s.aiWebSearchRunning, s.aiWebSearchDone)
        }
        val category = when (toolName) {
            "workspace_get_context",
            "workspace_list_projects",
            "workspace_list_repos",
            "fs_get_repo_overview",
            "fs_list_dir",
            "fs_read_file" -> ToolActivityCategory.READING

            "workspace_discover_group_repos",
            "fs_find_files",
            "fs_grep" -> ToolActivityCategory.SEARCHING

            "git_exec" -> ToolActivityCategory.GIT

            "check_push_readiness",
            "check_merge_conflicts",
            "check_file_syntax" -> ToolActivityCategory.CHECKING

            else -> ToolActivityCategory.DOING
        }
        val (runningTitle, doneTitle) = s.aiToolStatus(toolName)
        return ToolVisualMeta(category, runningTitle, doneTitle)
    }

    private fun extractToolSubtitle(toolName: String, args: JSONObject): String = when (toolName) {
        "workspace_create_project", "workspace_create_repo" ->
            args.optString("name", "").takeIf { it.isNotBlank() }?.let { "«$it»" }.orEmpty()
        "workspace_clone_repo", "workspace_discover_group_repos" ->
            args.optString("url", "").take(60)
        "fs_find_files" ->
            args.optString("pattern", "")
        "fs_grep" ->
            args.optString("query", "").takeIf { it.isNotBlank() }?.let { "«${it.take(40)}»" }.orEmpty()
        "fs_read_file", "fs_edit_file", "fs_write_file", "fs_delete_entry" ->
            args.optString("relativePath", args.optString("path", ""))
        "fs_replace_across_files" -> {
            val q = args.optString("query", "")
            val r = args.optString("replacement", "")
            if (q.isNotBlank()) "«${q.take(24)}» → «${r.take(24)}»" else ""
        }
        "fs_list_dir" ->
            args.optString("relativeDir", "").ifBlank { "/" }
        "git_exec" -> {
            val cmd = args.optString("command", "").trim()
            if (cmd.lowercase().startsWith("git ")) cmd else if (cmd.isNotEmpty()) "git $cmd" else ""
        }
        "check_file_syntax" ->
            args.optString("path", "").ifBlank {
                val arr = args.optJSONArray("paths")
                val s = AppLocale.current
                if (arr != null && arr.length() > 0) s.aiSyntaxFilesCount(arr.length()) else s.aiSyntaxChangedFiles
            }
        else -> ""
    }

    companion object {
        private const val MAX_TOOL_STEPS = 12
        private const val MAX_TOOL_RESULT_CHARS = 32_000
        private const val MAX_PREVIEW_CHARS = 4_000

        fun factory(
            preferencesStore: AppPreferencesStore,
            credentialStore: CredentialStore,
            mcpServer: PocketWorkerMcpServer,
            chatHistoryStore: AiChatHistoryStore,
            undoManager: McpUndoManager,
            backgroundCoordinator: AiAgentBackgroundCoordinator,
            localModelManager: LocalModelManager,
            localGemmaClient: LocalGemmaLlmClient,
            onWorkspaceMutated: () -> Unit
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiChatViewModel(
                    preferencesStore = preferencesStore,
                    credentialStore = credentialStore,
                    mcpServer = mcpServer,
                    chatHistoryStore = chatHistoryStore,
                    undoManager = undoManager,
                    backgroundCoordinator = backgroundCoordinator,
                    localModelManager = localModelManager,
                    localGemmaClient = localGemmaClient,
                    onWorkspaceMutated = onWorkspaceMutated
                ) as T
            }
        }
    }
}
