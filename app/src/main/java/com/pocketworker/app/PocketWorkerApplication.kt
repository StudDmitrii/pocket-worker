package com.pocketworker.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.pocketworker.app.core.ai.AiAgentBackgroundCoordinator
import com.pocketworker.app.core.ai.AndroidAiAgentNotificationPresenter
import com.pocketworker.app.core.ai.LocalGemmaLlmClient
import com.pocketworker.app.core.ai.LocalModelManager
import com.pocketworker.app.core.ai.McpUndoManager
import com.pocketworker.app.core.git.AndroidSystemReader
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.GitOAuthManager
import com.pocketworker.app.core.git.HttpRepoGroupDiscoveryService
import com.pocketworker.app.core.git.JGitEngine
import com.pocketworker.app.core.git.ProjectFileManager
import com.pocketworker.app.core.git.RepoGroupDiscoveryService
import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.mcp.JsSandboxSyntaxEngine
import com.pocketworker.app.core.mcp.PocketWorkerMcpServer
import com.pocketworker.app.core.mcp.SyntaxCheckEngine
import com.pocketworker.app.core.storage.AiChatHistoryStore
import com.pocketworker.app.core.storage.AppPreferencesStore
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.FileAiChatHistoryStore
import com.pocketworker.app.core.storage.KeystoreCredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import com.pocketworker.app.core.storage.SharedPreferencesAppPreferencesStore
import com.pocketworker.app.ui.ai.AiLocationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AppContainer(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val gitEngine: GitEngine = JGitEngine()
    val fileManager: ProjectFileManager = ProjectFileManager()
    val groupDiscoveryService: RepoGroupDiscoveryService = HttpRepoGroupDiscoveryService()
    val credentialStore: CredentialStore = KeystoreCredentialStore(application)
    val oauthManager: GitOAuthManager = GitOAuthManager(
        credentialStore = credentialStore,
        scope = appScope
    )
    val preferencesStore: AppPreferencesStore = SharedPreferencesAppPreferencesStore(application).also { store ->
        AppLocale.setLanguage(store.preferences.value.language)
    }
    val projectRegistry: ProjectRegistry = ProjectRegistry(
        metadataFile = File(application.filesDir, "projects_registry.json"),
        projectsRootDir = File(application.filesDir, "projects")
    )

    @Volatile
    var activeLocationContext: AiLocationContext = AiLocationContext.AllProjects

    private val _workspaceMutationVersion = MutableStateFlow(0L)
    val workspaceMutationVersion: StateFlow<Long> = _workspaceMutationVersion.asStateFlow()

    fun notifyWorkspaceChanged() {
        _workspaceMutationVersion.value = _workspaceMutationVersion.value + 1L
    }

    val syntaxCheckEngine: SyntaxCheckEngine = JsSandboxSyntaxEngine(application.applicationContext)

    val mcpServer: PocketWorkerMcpServer = PocketWorkerMcpServer(
        projectRegistry = projectRegistry,
        fileManager = fileManager,
        gitEngine = gitEngine,
        credentialStore = credentialStore,
        groupDiscoveryService = groupDiscoveryService,
        currentLocationProvider = { activeLocationContext },
        confirmFileEditsProvider = { preferencesStore.preferences.value.aiSettings.confirmFileEdits },
        syntaxCheckEngine = syntaxCheckEngine,
        oauthManager = oauthManager
    )

    val aiChatHistoryStore: AiChatHistoryStore = FileAiChatHistoryStore(
        storageFile = File(application.filesDir, "ai_chat_history.json")
    )

    val mcpUndoManager: McpUndoManager = McpUndoManager(
        projectRegistry = projectRegistry,
        fileManager = fileManager,
        gitEngine = gitEngine,
        blobsDir = File(application.filesDir, "ai_undo_blobs")
    )

    lateinit var localGemmaClient: LocalGemmaLlmClient
        private set

    val localModelManager: LocalModelManager = LocalModelManager(
        modelsDir = File(application.filesDir, "local_models"),
        scope = appScope,
        onModelRemoved = { removedId ->
            if (::localGemmaClient.isInitialized) {
                localGemmaClient.releaseEngine(onlyModelId = removedId)
            }
        }
    )

    init {
        localGemmaClient = LocalGemmaLlmClient(
            modelManager = localModelManager,
            cacheDir = File(application.cacheDir, "litertlm_cache"),
            useGpuProvider = { preferencesStore.preferences.value.aiSettings.localUseGpu }
        )
    }

    val agentBackgroundCoordinator: AiAgentBackgroundCoordinator = AiAgentBackgroundCoordinator(
        presenter = AndroidAiAgentNotificationPresenter(application.applicationContext)
    )

    private val _pendingOpenChatId = MutableStateFlow<String?>(null)
    val pendingOpenChatId: StateFlow<String?> = _pendingOpenChatId.asStateFlow()

    fun requestOpenAiChat(chatId: String) {
        if (chatId.isNotBlank()) {
            _pendingOpenChatId.value = chatId
        }
    }

    fun consumeOpenAiChatRequest() {
        _pendingOpenChatId.value = null
    }
}

class PocketWorkerApplication : Application() {

    lateinit var container: AppContainer
        private set

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        AndroidSystemReader.install(File(filesDir, "git_config"))
        container = AppContainer(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit

            override fun onActivityStarted(activity: Activity) {
                val wasBackground = startedActivityCount == 0
                startedActivityCount++
                if (wasBackground) {
                    container.agentBackgroundCoordinator.onAppMovedToForeground()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
                    container.agentBackgroundCoordinator.onAppMovedToBackground()
                }
            }
        })
    }
}
