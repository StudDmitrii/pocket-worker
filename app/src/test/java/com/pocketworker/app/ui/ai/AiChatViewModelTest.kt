package com.pocketworker.app.ui.ai

import com.pocketworker.app.core.ai.AiAgentBackgroundCoordinator
import com.pocketworker.app.core.ai.AiAgentNotificationPresenter
import com.pocketworker.app.core.ai.LlmMessage
import com.pocketworker.app.core.ai.LlmRequest
import com.pocketworker.app.core.ai.LlmStreamEvent
import com.pocketworker.app.core.ai.LlmStreamingClient
import com.pocketworker.app.core.ai.LlmToolCall
import com.pocketworker.app.core.ai.LocalModelManager
import com.pocketworker.app.core.ai.McpUndoManager
import com.pocketworker.app.core.ai.buildAgentSystemPrompt
import com.pocketworker.app.core.mcp.McpTestHarness
import com.pocketworker.app.core.storage.AiAgentSettings
import com.pocketworker.app.core.storage.AiProvider
import com.pocketworker.app.core.storage.AppPreferences
import com.pocketworker.app.core.storage.FileAiChatHistoryStore
import com.pocketworker.app.core.storage.InMemoryAppPreferencesStore
import com.pocketworker.app.ui.workspace.DiffDialogState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var harness: McpTestHarness

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        harness = McpTestHarness(confirmFileEdits = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        harness.cleanup()
    }

    @Test
    fun `system prompt includes workspace_get_context hint and appends user custom prompt on new line`() {
        val prompt = buildAgentSystemPrompt(
            shareWorkspaceContext = true,
            userCustomPrompt = "Всегда пиши комментарии на русском."
        )
        assertTrue(prompt.contains("workspace_get_context"))
        assertTrue(prompt.endsWith("\nВсегда пиши комментарии на русском."))
    }

    @Test
    fun `end-to-end streaming agent executes workspace_create_project after confirmation and notifies workspace`() = runTest(testDispatcher) {
        val prefsStore = InMemoryAppPreferencesStore(
            AppPreferences(
                aiSettings = AiAgentSettings(
                    provider = AiProvider.ANTHROPIC,
                    modelName = "claude-sonnet-4-6",
                    systemPrompt = "Отвечай кратко.",
                    shareWorkspaceContext = true,
                    confirmFileEdits = true
                )
            )
        )
        harness.confirmFileEdits = true
        harness.credentialStore.saveAiApiKey(AiProvider.ANTHROPIC, "sk-ant-live-test")

        val recordedRequests = mutableListOf<LlmRequest>()
        val fakeClient = object : LlmStreamingClient {
            override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
                recordedRequests.add(request)
                if (recordedRequests.size == 1) {
                    emit(LlmStreamEvent.TextDelta("Сейчас создам проект."))
                    emit(LlmStreamEvent.ToolUseStarted("tool_1", "workspace_create_project"))
                    emit(LlmStreamEvent.ToolUseInputDelta("tool_1", """{"name":"SuperApp"}"""))
                    emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("tool_1", "workspace_create_project", """{"name":"SuperApp"}""")))
                    emit(LlmStreamEvent.MessageFinished("tool_use"))
                } else {
                    emit(LlmStreamEvent.TextDelta("Готово! "))
                    emit(LlmStreamEvent.TextDelta("Проект «SuperApp» создан."))
                    emit(LlmStreamEvent.MessageFinished("end_turn"))
                }
            }
        }

        var mutationNotifications = 0
        val vm = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore,
            mcpServer = harness.server,
            onWorkspaceMutated = { mutationNotifications++ },
            clientResolver = { fakeClient },
            ioDispatcher = testDispatcher
        )

        vm.sendMessage("Создай проект SuperApp")
        advanceUntilIdle()

        // First request included system prompt with custom rule and all 21 MCP tools
        assertEquals(1, recordedRequests.size)
        assertTrue(recordedRequests[0].systemPrompt.contains("workspace_get_context"))
        assertTrue(recordedRequests[0].systemPrompt.endsWith("\nОтвечай кратко."))
        assertEquals(21, recordedRequests[0].tools.size)

        // Agent paused waiting for user confirmation of workspace_create_project
        val pending = vm.uiState.value.pendingConfirmation
        assertNotNull(pending)
        assertEquals("workspace_create_project", pending!!.request.toolName)

        // User approves!
        vm.resolveConfirmation(true)
        advanceUntilIdle()

        // Project actually created on disk in ProjectRegistry + workspace refresh callback fired!
        assertEquals(1, mutationNotifications)
        val createdProjects = harness.registry.listProjects()
        assertEquals(1, createdProjects.size)
        assertEquals("SuperApp", createdProjects[0].name)

        // Second LLM request received the tool result
        assertEquals(2, recordedRequests.size)
        assertTrue(recordedRequests[1].messages.any { it is LlmMessage.ToolResult && !it.isError })

        // Chat timeline contains: UserMessage -> AssistantText -> single ToolActivity (not grouped when <= 1) -> AssistantText(final)
        val items = vm.uiState.value.activeMessages
        assertEquals(4, items.size)
        assertTrue(items[0] is AiChatMessageItem.UserMessage)
        assertTrue(items[1] is AiChatMessageItem.AssistantText)
        val toolCard = items[2] as AiChatMessageItem.ToolActivity
        assertEquals(ToolActivityStatus.SUCCEEDED, toolCard.status)
        assertEquals("Проект создан", toolCard.title)
        assertTrue(items.none { it is AiChatMessageItem.ActionsGroup })
        val finalReply = items[3] as AiChatMessageItem.AssistantText
        assertEquals("Готово! Проект «SuperApp» создан.", finalReply.text)
        assertFalse(finalReply.isStreaming)
    }

    @Test
    fun `shows clear error notice when API key is missing or proxy URL is invalid, and passes proxy when valid`() = runTest(testDispatcher) {
        val prefsStore = InMemoryAppPreferencesStore(
            AppPreferences(
                aiSettings = AiAgentSettings(provider = AiProvider.OPENROUTER, modelName = "particle-ai/particle-1:free")
            )
        )
        val capturedRequests = mutableListOf<LlmRequest>()
        val fakeClient = object : LlmStreamingClient {
            override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
                capturedRequests.add(request)
                emit(LlmStreamEvent.TextDelta("Привет из OpenRouter!"))
                emit(LlmStreamEvent.MessageFinished("stop"))
            }
        }
        val vm = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore,
            mcpServer = harness.server,
            clientResolver = { fakeClient },
            ioDispatcher = testDispatcher
        )

        // OpenRouter without API key -> asks for OpenRouter key
        vm.sendMessage("Привет")
        advanceUntilIdle()
        val openRouterErr = vm.uiState.value.activeMessages.last() as AiChatMessageItem.ErrorNotice
        assertTrue(openRouterErr.showSettingsAction)
        assertTrue(openRouterErr.message.contains("OpenRouter"))

        // Enable proxy with invalid URL -> blocks cleanly before sending
        harness.credentialStore.saveAiApiKey(AiProvider.OPENROUTER, "sk-or-v1-abc")
        prefsStore.updateAiSettings(
            AiAgentSettings(provider = AiProvider.OPENROUTER, modelName = "particle-ai/particle-1:free", proxyEnabled = true)
        )
        harness.credentialStore.saveAiProxyUrl("ftp://bad-proxy")
        advanceUntilIdle()
        vm.sendMessage("Тест прокси")
        advanceUntilIdle()
        val proxyErr = vm.uiState.value.activeMessages.last() as AiChatMessageItem.ErrorNotice
        assertTrue(proxyErr.showSettingsAction)
        assertTrue(proxyErr.message.contains("Прокси"))
        assertEquals(0, capturedRequests.size)

        // Valid HTTP proxy -> forwarded on LlmRequest
        harness.credentialStore.saveAiProxyUrl("http://u:p@1.2.3.4:8080")
        vm.sendMessage("Теперь через прокси")
        advanceUntilIdle()
        assertEquals(1, capturedRequests.size)
        assertNotNull(capturedRequests[0].proxy)
        assertEquals("1.2.3.4", capturedRequests[0].proxy!!.host)
        assertEquals(8080, capturedRequests[0].proxy!!.port)
        assertEquals("u", capturedRequests[0].proxy!!.username)
    }

    @Test
    fun `preserves full multi-turn history across follow-up messages and recovers cleanly after API error`() = runTest(testDispatcher) {
        val prefsStore = InMemoryAppPreferencesStore(
            AppPreferences(
                aiSettings = AiAgentSettings(
                    provider = AiProvider.OPENAI,
                    modelName = "gpt-5",
                    confirmFileEdits = false
                )
            )
        )
        harness.confirmFileEdits = false
        harness.credentialStore.saveAiApiKey(AiProvider.OPENAI, "sk-openai-multi")

        val recordedRequests = mutableListOf<LlmRequest>()
        var callCount = 0
        val fakeClient = object : LlmStreamingClient {
            override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
                recordedRequests.add(request)
                callCount++
                when (callCount) {
                    1 -> {
                        emit(LlmStreamEvent.ToolUseStarted("call_1", "workspace_create_project"))
                        emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("call_1", "workspace_create_project", """{"name":"NotesApp"}""")))
                        emit(LlmStreamEvent.MessageFinished("tool_calls"))
                    }
                    2 -> {
                        emit(LlmStreamEvent.TextDelta("Создал проект NotesApp."))
                        emit(LlmStreamEvent.MessageFinished("stop"))
                    }
                    3 -> {
                        // Simulate transient 429 error on turn 2
                        emit(LlmStreamEvent.Error("Rate limit (429)", statusCode = 429))
                    }
                    4 -> {
                        emit(LlmStreamEvent.TextDelta("Переделал как просили!"))
                        emit(LlmStreamEvent.MessageFinished("stop"))
                    }
                }
            }
        }

        val vm = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore,
            mcpServer = harness.server,
            clientResolver = { fakeClient },
            ioDispatcher = testDispatcher
        )

        // Turn 1: create project
        vm.sendMessage("Создай проект NotesApp")
        advanceUntilIdle()

        // Turn 2: fails with transient 429
        vm.sendMessage("Тут переделай")
        advanceUntilIdle()

        // Turn 3: retry follow-up -> history must contain Turn 1 (User + Assistant(tool) + ToolResult + Assistant) + Turn 3 User (NO dangling failed Turn 2 User!)
        vm.sendMessage("Повтори: тут переделай описание")
        advanceUntilIdle()

        assertEquals(4, recordedRequests.size)
        val turn3Messages = recordedRequests[3].messages
        assertEquals(5, turn3Messages.size)
        assertEquals("Создай проект NotesApp", (turn3Messages[0] as LlmMessage.User).text)
        assertTrue((turn3Messages[1] as LlmMessage.Assistant).toolCalls.size == 1)
        assertTrue(turn3Messages[2] is LlmMessage.ToolResult)
        assertEquals("Создал проект NotesApp.", (turn3Messages[3] as LlmMessage.Assistant).text)
        assertEquals("Повтори: тут переделай описание", (turn3Messages[4] as LlmMessage.User).text)
    }

    @Test
    fun `persists chat history permanently, emits Codex ChangedFilesCard with Git diff stats, and rewinds message with full MCP rollback`() = runTest(testDispatcher) {
        val (project, repo) = harness.createProjectAndRepo(projectName = "Demo", repoName = "app-repo", initGit = true, createReadme = true)
        val repoDir = File(repo.directoryPath)
        File(repoDir, "README.md").writeText("line1\nline2\nline3\n")
        harness.gitEngine.stageFile(repoDir, "README.md")
        harness.gitEngine.commit(repoDir, "Set baseline README", "Test User", "test@example.com")

        val historyFile = File(harness.tempRoot, "ai_chat_history.json")
        val blobsDir = File(harness.tempRoot, "ai_undo_blobs")
        val historyStore = FileAiChatHistoryStore(historyFile)
        val undoManager = McpUndoManager(
            projectRegistry = harness.registry,
            fileManager = harness.fileManager,
            gitEngine = harness.gitEngine,
            blobsDir = blobsDir
        )
        val prefsStore = InMemoryAppPreferencesStore(
            AppPreferences(
                aiSettings = AiAgentSettings(
                    provider = AiProvider.OPENAI,
                    modelName = "gpt-5.4",
                    confirmFileEdits = false // Tier 1 auto-approved
                )
            )
        )
        harness.confirmFileEdits = false
        harness.credentialStore.saveAiApiKey(AiProvider.OPENAI, "sk-openai-rewind")

        val recordedRequests = mutableListOf<LlmRequest>()
        var callCount = 0
        val fakeClient = object : LlmStreamingClient {
            override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
                recordedRequests.add(request)
                callCount++
                when (callCount) {
                    1 -> {
                        // Turn 1: simple reply without tool mutations
                        emit(LlmStreamEvent.TextDelta("Привет! Готов помочь с репозиторием."))
                        emit(LlmStreamEvent.MessageFinished("stop"))
                    }
                    2 -> {
                        // Turn 2: edit README.md AND create Notes.txt
                        val editArgs = """{"projectId":"${project.id}","repoId":"${repo.id}","relativePath":"README.md","edits":[{"oldText":"line2","newText":"line2_updated\nline2_extra"}]}"""
                        val writeArgs = """{"projectId":"${project.id}","repoId":"${repo.id}","relativePath":"Notes.txt","content":"hello world\nsecond line\n"}"""
                        emit(LlmStreamEvent.ToolUseStarted("t_edit", "fs_edit_file"))
                        emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("t_edit", "fs_edit_file", editArgs)))
                        emit(LlmStreamEvent.ToolUseStarted("t_write", "fs_write_file"))
                        emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("t_write", "fs_write_file", writeArgs)))
                        emit(LlmStreamEvent.MessageFinished("tool_calls"))
                    }
                    3 -> {
                        emit(LlmStreamEvent.TextDelta("Обновил README.md и создал Notes.txt."))
                        emit(LlmStreamEvent.MessageFinished("stop"))
                    }
                    4 -> {
                        // After rewind + re-send
                        emit(LlmStreamEvent.TextDelta("Выполнил обновлённый запрос!"))
                        emit(LlmStreamEvent.MessageFinished("stop"))
                    }
                }
            }
        }

        val vm1 = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore,
            mcpServer = harness.server,
            chatHistoryStore = historyStore,
            undoManager = undoManager,
            clientResolver = { fakeClient },
            ioDispatcher = testDispatcher
        )

        // Turn 1
        vm1.sendMessage("Привет, посмотри проект")
        advanceUntilIdle()

        // Turn 2 (mutates README.md and creates Notes.txt automatically because Tier 1 auto-approve is ON)
        vm1.sendMessage("Обнови README и добавь Notes.txt")
        advanceUntilIdle()

        assertEquals("line1\nline2_updated\nline2_extra\nline3\n", File(repoDir, "README.md").readText())
        assertTrue(File(repoDir, "Notes.txt").isFile)

        // Verify collapsed ActionsGroup + Codex ChangedFilesCard with Git +added/-deleted stats
        val turn2Items = vm1.uiState.value.activeMessages
        val actionsGroup = turn2Items.filterIsInstance<AiChatMessageItem.ActionsGroup>().single()
        assertEquals(2, actionsGroup.actions.size)
        val changedCard = turn2Items.filterIsInstance<AiChatMessageItem.ChangedFilesCard>().single()
        assertEquals(2, changedCard.files.size)
        val readmeSummary = changedCard.files.first { it.relativePath == "README.md" }
        assertEquals(2, readmeSummary.addedLines)
        assertEquals(1, readmeSummary.deletedLines)
        val notesSummary = changedCard.files.first { it.relativePath == "Notes.txt" }
        assertEquals(2, notesSummary.addedLines)
        assertEquals(0, notesSummary.deletedLines)

        // Verify live Git diff loads for tapping the changed file row
        var loadedDiff: DiffDialogState? = null
        vm1.loadChangedFileDiff(readmeSummary) { loadedDiff = it }
        advanceUntilIdle()
        assertNotNull(loadedDiff)
        assertTrue(loadedDiff!!.diffText.contains("+line2_updated"))
        assertTrue(loadedDiff!!.diffText.contains("-line2"))

        // Simulate full app restart by instantiating a brand-new AiChatViewModel reading the same file!
        assertTrue(historyFile.isFile)
        val vm2 = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore,
            mcpServer = harness.server,
            chatHistoryStore = FileAiChatHistoryStore(historyFile),
            undoManager = undoManager,
            clientResolver = { fakeClient },
            ioDispatcher = testDispatcher
        )

        // Everything survived restart (sessions, messages, ActionsGroup, ChangedFilesCard, checkpoints!)
        val restoredMessages = vm2.uiState.value.activeMessages
        assertEquals(turn2Items.size, restoredMessages.size)
        assertEquals(1, restoredMessages.filterIsInstance<AiChatMessageItem.ActionsGroup>().size)
        assertEquals(1, restoredMessages.filterIsInstance<AiChatMessageItem.ChangedFilesCard>().size)

        // Click "Повторить" on Turn 2's user message AFTER restart!
        val turn2UserMsg = restoredMessages.filterIsInstance<AiChatMessageItem.UserMessage>()[1]
        var restoredInputText = ""
        vm2.rewindToUserMessage(turn2UserMsg.id) { restoredInputText = it }
        advanceUntilIdle()

        // 1) Original prompt returned into composer input field
        assertEquals("Обнови README и добавь Notes.txt", restoredInputText)

        // 2) Files rolled back to exact pre-Turn-2 state!
        assertEquals("line1\nline2\nline3\n", File(repoDir, "README.md").readText())
        assertFalse(File(repoDir, "Notes.txt").exists())

        // 3) Chat history truncated strictly before Turn 2 (Turn 1 user + Turn 1 AI answer preserved!)
        val afterRewind = vm2.uiState.value.activeMessages
        assertEquals(2, afterRewind.size)
        assertEquals("Привет, посмотри проект", (afterRewind[0] as AiChatMessageItem.UserMessage).text)
        assertEquals("Привет! Готов помочь с репозиторием.", (afterRewind[1] as AiChatMessageItem.AssistantText).text)

        // 4) Sending edited Turn 2 prompt sends ONLY Turn 1 + new Turn 2 to LLM (Turn 2's rolled-back tool calls are gone!)
        vm2.sendMessage("Только посмотри README")
        advanceUntilIdle()
        val lastReqMsgs = recordedRequests.last().messages
        assertEquals(3, lastReqMsgs.size)
        assertEquals("Привет, посмотри проект", (lastReqMsgs[0] as LlmMessage.User).text)
        assertEquals("Привет! Готов помочь с репозиторием.", (lastReqMsgs[1] as LlmMessage.Assistant).text)
        assertEquals("Только посмотри README", (lastReqMsgs[2] as LlmMessage.User).text)
    }

    @Test
    fun `Tier 1 auto-approve runs Tier 1 without confirmation while Tier 2 always asks for confirmation`() = runTest(testDispatcher) {
        val (project, repo) = harness.createProjectAndRepo(projectName = "TierProj", repoName = "tier-repo", initGit = true, createReadme = true)
        val repoDir = File(repo.directoryPath)
        File(repoDir, "keep.txt").writeText("important\n")
        val prefsStore = InMemoryAppPreferencesStore(
            AppPreferences(
                aiSettings = AiAgentSettings(
                    provider = AiProvider.OPENAI,
                    modelName = "gpt-5.4",
                    confirmFileEdits = false // Auto-approve Tier 1
                )
            )
        )
        harness.confirmFileEdits = false
        harness.credentialStore.saveAiApiKey(AiProvider.OPENAI, "sk-openai-tier")

        var step = 0
        val fakeClient = object : LlmStreamingClient {
            override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
                step++
                if (step == 1) {
                    val writeArgs = """{"projectId":"${project.id}","repoId":"${repo.id}","relativePath":"new.txt","content":"ok\n"}"""
                    val delArgs = """{"projectId":"${project.id}","repoId":"${repo.id}","relativePath":"keep.txt"}"""
                    emit(LlmStreamEvent.ToolUseStarted("t1", "fs_write_file"))
                    emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("t1", "fs_write_file", writeArgs)))
                    emit(LlmStreamEvent.ToolUseStarted("t2", "fs_delete_entry"))
                    emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("t2", "fs_delete_entry", delArgs)))
                    emit(LlmStreamEvent.MessageFinished("tool_calls"))
                } else {
                    emit(LlmStreamEvent.TextDelta("Готово"))
                    emit(LlmStreamEvent.MessageFinished("stop"))
                }
            }
        }

        val vm = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore,
            mcpServer = harness.server,
            clientResolver = { fakeClient },
            ioDispatcher = testDispatcher
        )

        vm.sendMessage("Создай new.txt и удали keep.txt")
        advanceUntilIdle()

        // Tier 1 fs_write_file executed automatically WITHOUT prompting!
        assertTrue(File(repoDir, "new.txt").isFile)
        // Tier 2 fs_delete_entry PAUSED waiting for explicit user approval!
        val pending = vm.uiState.value.pendingConfirmation
        assertNotNull(pending)
        assertEquals("fs_delete_entry", pending!!.request.toolName)
        assertTrue(File(repoDir, "keep.txt").isFile)

        // Reject Tier 2 -> keep.txt remains intact!
        vm.resolveConfirmation(false)
        advanceUntilIdle()
        assertTrue(File(repoDir, "keep.txt").isFile)
    }

    @Test
    fun `renames chat permanently and ignores blank chat titles`() = runTest(testDispatcher) {
        val historyFile = File(harness.tempRoot, "rename_history.json")
        val prefsStore = InMemoryAppPreferencesStore(
            AppPreferences(
                aiSettings = AiAgentSettings(provider = AiProvider.OPENAI, modelName = "gpt-5.4")
            )
        )
        harness.credentialStore.saveAiApiKey(AiProvider.OPENAI, "sk-openai-ctx")

        val fakeClient = object : LlmStreamingClient {
            override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
                emit(LlmStreamEvent.TextDelta("Принял!"))
                emit(LlmStreamEvent.MessageFinished("stop"))
            }
        }

        val vm = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore,
            mcpServer = harness.server,
            chatHistoryStore = FileAiChatHistoryStore(historyFile),
            clientResolver = { fakeClient },
            ioDispatcher = testDispatcher
        )

        val ctxProjectA = AiLocationContext.ProjectFolder(projectId = "pA", projectName = "Alpha")
        vm.sendMessage("Привет из Alpha", locationContext = ctxProjectA)
        advanceUntilIdle()

        val firstChatId = vm.uiState.value.activeChatId
        vm.renameChat(firstChatId, "  Работа над Alpha  ")
        vm.renameChat(firstChatId, "   ") // blank rename ignored
        advanceUntilIdle()
        assertEquals("Работа над Alpha", vm.uiState.value.sessions.first { it.id == firstChatId }.title)

        // Start a new chat and verify restart preserves custom title of first chat
        vm.startNewChat()
        advanceUntilIdle()
        val vmRestored = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore,
            mcpServer = harness.server,
            chatHistoryStore = FileAiChatHistoryStore(historyFile),
            clientResolver = { fakeClient },
            ioDispatcher = testDispatcher
        )
        assertEquals("Работа над Alpha", vmRestored.uiState.value.sessions.first { it.id == firstChatId }.title)
    }

    @Test
    fun `groups contiguous actions preserving intermediate AI text and places interrupting error strictly after ChangedFilesCard`() = runTest(testDispatcher) {
        val (project, repo) = harness.createProjectAndRepo(projectName = "OrderProj", repoName = "order-repo", initGit = true, createReadme = true)
        val repoDir = File(repo.directoryPath)
        File(repoDir, "README.md").writeText("alpha\nbeta\n")
        harness.gitEngine.stageFile(repoDir, "README.md")
        harness.gitEngine.commit(repoDir, "Init README", "Tester", "test@example.com")

        val undoManager = McpUndoManager(
            projectRegistry = harness.registry,
            fileManager = harness.fileManager,
            gitEngine = harness.gitEngine,
            blobsDir = File(harness.tempRoot, "order_blobs")
        )
        val prefsStore = InMemoryAppPreferencesStore(
            AppPreferences(
                aiSettings = AiAgentSettings(
                    provider = AiProvider.OPENAI,
                    modelName = "gpt-5.4",
                    confirmFileEdits = false
                )
            )
        )
        harness.confirmFileEdits = false
        harness.credentialStore.saveAiApiKey(AiProvider.OPENAI, "sk-openai-order")

        var step = 0
        val fakeClient = object : LlmStreamingClient {
            override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
                step++
                when (step) {
                    1 -> {
                        // 2 actions first (no text before them, only leading whitespace which must not split groups)
                        emit(LlmStreamEvent.TextDelta("\n  "))
                        emit(LlmStreamEvent.ToolUseStarted("a1", "workspace_get_context"))
                        emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("a1", "workspace_get_context", "{}")))
                        emit(LlmStreamEvent.ToolUseStarted("a2", "fs_read_file"))
                        emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("a2", "fs_read_file", """{"projectId":"${project.id}","repoId":"${repo.id}","relativePath":"README.md"}""")))
                        emit(LlmStreamEvent.MessageFinished("tool_calls"))
                    }
                    2 -> {
                        // Intermediate AI text, followed by 5 actions (including editing README.md)
                        emit(LlmStreamEvent.TextDelta("Изучил файл, теперь внесу 5 шагов правок."))
                        val editArgs = """{"projectId":"${project.id}","repoId":"${repo.id}","relativePath":"README.md","edits":[{"oldText":"beta","newText":"beta_v2"}]}"""
                        emit(LlmStreamEvent.ToolUseStarted("b1", "fs_edit_file"))
                        emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("b1", "fs_edit_file", editArgs)))
                        for (i in 2..5) {
                            emit(LlmStreamEvent.ToolUseStarted("b$i", "workspace_list_projects"))
                            emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("b$i", "workspace_list_projects", "{}")))
                        }
                        emit(LlmStreamEvent.MessageFinished("tool_calls"))
                    }
                    3 -> {
                        // Agent interrupted by API error!
                        emit(LlmStreamEvent.Error("Соединение прервано (502)", statusCode = 502))
                    }
                }
            }
        }

        val vm = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore,
            mcpServer = harness.server,
            undoManager = undoManager,
            clientResolver = { fakeClient },
            ioDispatcher = testDispatcher
        )

        vm.sendMessage("Обнови README и проверь всё")
        advanceUntilIdle()

        val items = vm.uiState.value.activeMessages
        // Expected strict chronological order:
        // 0: UserMessage
        // 1: ActionsGroup (2 actions)
        // 2: AssistantText (intermediate message)
        // 3: ActionsGroup (5 actions)
        // 4: ChangedFilesCard (README.md)
        // 5: ErrorNotice (VERY LAST!)
        assertEquals(6, items.size)
        assertTrue(items[0] is AiChatMessageItem.UserMessage)

        val group1 = items[1] as AiChatMessageItem.ActionsGroup
        assertEquals(2, group1.actions.size)

        val midText = items[2] as AiChatMessageItem.AssistantText
        assertEquals("Изучил файл, теперь внесу 5 шагов правок.", midText.text)

        val group2 = items[3] as AiChatMessageItem.ActionsGroup
        assertEquals(5, group2.actions.size)

        val changedFiles = items[4] as AiChatMessageItem.ChangedFilesCard
        assertEquals(1, changedFiles.files.size)
        assertEquals("README.md", changedFiles.files.single().relativePath)
        assertEquals(1, changedFiles.files.single().addedLines)
        assertEquals(1, changedFiles.files.single().deletedLines)

        val finalError = items[5] as AiChatMessageItem.ErrorNotice
        assertEquals("Соединение прервано (502)", finalError.message)
    }

    @Test
    fun `streams live status to background notification coordinator, notifies on finish, cancels on task removal, and opens target chat from notification`() = runTest(testDispatcher) {
        val ongoingStatuses = mutableListOf<Pair<String, String>>()
        val finishedNotifs = mutableListOf<Triple<String, String, String>>()
        val clearedNotifs = mutableListOf<String>()
        val presenter = object : AiAgentNotificationPresenter {
            override fun showOrUpdateOngoing(chatId: String, chatTitle: String, statusText: String) {
                ongoingStatuses.add(chatId to statusText)
            }
            override fun hideOngoing() = Unit
            override fun showFinished(chatId: String, chatTitle: String, title: String, contentText: String) {
                finishedNotifs.add(Triple(chatId, title, contentText))
            }
            override fun cancelFinished(chatId: String) {
                clearedNotifs.add(chatId)
            }
        }
        val coordinator = AiAgentBackgroundCoordinator(presenter)
        val prefsStore = InMemoryAppPreferencesStore(
            AppPreferences(aiSettings = AiAgentSettings(provider = AiProvider.OPENAI, modelName = "gpt-5.4"))
        )
        harness.credentialStore.saveAiApiKey(AiProvider.OPENAI, "sk-openai-bg")

        var callNum = 0
        val fakeClient = object : LlmStreamingClient {
            override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
                callNum++
                when (callNum) {
                    1 -> {
                        emit(LlmStreamEvent.ToolUseStarted("tc1", "workspace_get_context"))
                        emit(LlmStreamEvent.ToolUseCompleted(LlmToolCall("tc1", "workspace_get_context", "{}")))
                        emit(LlmStreamEvent.MessageFinished("tool_calls"))
                    }
                    2 -> {
                        emit(LlmStreamEvent.TextDelta("Задача выполнена полностью!"))
                        emit(LlmStreamEvent.MessageFinished("stop"))
                    }
                }
            }
        }

        val vm = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore,
            mcpServer = harness.server,
            backgroundCoordinator = coordinator,
            clientResolver = { fakeClient },
            ioDispatcher = testDispatcher
        )

        val chat1Id = vm.uiState.value.activeChatId
        val ctxA = AiLocationContext.ProjectFolder(projectId = "p1", projectName = "ProjA")
        val ctxB = AiLocationContext.ProjectFolder(projectId = "p2", projectName = "ProjB")

        // Minimize app right after user triggers a task
        coordinator.onAppMovedToBackground()
        vm.sendMessage("Проверь контекст", locationContext = ctxA)
        advanceUntilIdle()

        // Ongoing statuses mirrored loader texts while backgrounded
        assertTrue(ongoingStatuses.any { it.first == chat1Id && it.second == "Думает..." })
        assertTrue(ongoingStatuses.any { it.first == chat1Id && it.second == "Смотрю контекст..." })
        assertTrue(ongoingStatuses.any { it.first == chat1Id && it.second == "Отвечает..." })

        // Finished notification fired with exact title & chatId
        assertEquals(1, finishedNotifs.size)
        assertEquals(chat1Id, finishedNotifs.single().first)
        assertEquals("Агент закончил задачу", finishedNotifs.single().second)
        assertTrue(finishedNotifs.single().third.contains("Задача выполнена полностью!"))

        // Switch to another chat, then tap the notification for chat1Id from a different screen context ->
        // jumps straight to chat1Id, clears notification, and does NOT show a spurious context-change prompt
        vm.startNewChat()
        val chat2Id = vm.uiState.value.activeChatId
        assertTrue(chat2Id != chat1Id)

        coordinator.onAppMovedToForeground()
        vm.openChatFromNotification(chat1Id)
        vm.onChatWindowOpened(ctxB)
        assertEquals(chat1Id, vm.uiState.value.activeChatId)
        assertTrue(clearedNotifs.contains(chat1Id))
    }

    @Test
    fun `local Gemma 4 provider requires downloaded model instead of API key and runs offline without key`() = runTest(testDispatcher) {
        val modelsDir = File(harness.tempRoot, "local_models_vm")
        val localManager = LocalModelManager(
            modelsDir = modelsDir,
            scope = this,
            ioDispatcher = testDispatcher
        )
        val prefsStore = InMemoryAppPreferencesStore(
            AppPreferences(
                aiSettings = AiAgentSettings(
                    provider = AiProvider.LOCAL,
                    modelName = LocalModelManager.MODEL_GEMMA_4_E2B_ID
                )
            )
        )
        var capturedRequest: LlmRequest? = null
        val fakeLocalClient = object : LlmStreamingClient {
            override fun streamChat(request: LlmRequest): Flow<LlmStreamEvent> = flow {
                capturedRequest = request
                emit(LlmStreamEvent.TextDelta("Привет с локальной Gemma 4!"))
                emit(LlmStreamEvent.MessageFinished("stop"))
            }
        }

        val vm = AiChatViewModel(
            preferencesStore = prefsStore,
            credentialStore = harness.credentialStore, // No API key saved!
            mcpServer = harness.server,
            localModelManager = localManager,
            clientResolver = { fakeLocalClient },
            ioDispatcher = testDispatcher
        )

        // 1) Not downloaded yet -> shows clear notice pointing to Settings
        vm.sendMessage("Привет офлайн")
        advanceUntilIdle()
        val missingNotice = vm.uiState.value.activeMessages.last() as AiChatMessageItem.ErrorNotice
        assertTrue(missingNotice.showSettingsAction)
        assertTrue(missingNotice.message.contains("Скачать модель"))
        assertEquals(null, capturedRequest)

        // 2) Model downloaded on disk -> sends immediately WITHOUT any API key!
        modelsDir.mkdirs()
        File(modelsDir, "gemma-4-E2B-it.litertlm").writeBytes(byteArrayOf(1, 2, 3, 4))
        localManager.refreshStates()
        vm.refreshProviderBadge()
        assertTrue(vm.uiState.value.hasApiKey)

        vm.sendMessage("Теперь работай локально")
        advanceUntilIdle()
        assertNotNull(capturedRequest)
        assertEquals(LocalModelManager.MODEL_GEMMA_4_E2B_ID, capturedRequest!!.model)
        assertEquals("", capturedRequest!!.apiKey)
        val reply = vm.uiState.value.activeMessages.last() as AiChatMessageItem.AssistantText
        assertEquals("Привет с локальной Gemma 4!", reply.text)
    }
}
