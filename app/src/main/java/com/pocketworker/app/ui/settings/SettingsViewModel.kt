package com.pocketworker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketworker.app.core.ai.LocalModelDownloadState
import com.pocketworker.app.core.ai.LocalModelManager
import com.pocketworker.app.core.ai.ProxyParseResult
import com.pocketworker.app.core.ai.parseAiProxyUrl
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.JGitEngine
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.storage.AiAgentSettings
import com.pocketworker.app.core.storage.AiProvider
import com.pocketworker.app.core.storage.AppPreferences
import com.pocketworker.app.core.storage.AppPreferencesStore
import com.pocketworker.app.core.storage.AppThemeMode
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader
import java.io.File

enum class SettingsTab(val title: String) {
    GENERAL("Общие"),
    AGENT("Агент")
}

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val projectId: String? = null,
    val projectName: String? = null,
    val repoId: String? = null,
    val repoEntry: RepoEntry? = null,
    val isGitRepo: Boolean = false,
    val repoCredentials: GitCredentials? = null,
    val repoHasCustomAuthor: Boolean = false,
    val repoAuthorName: String = GitEngine.DEFAULT_AUTHOR_NAME,
    val repoAuthorEmail: String = GitEngine.DEFAULT_AUTHOR_EMAIL,
    val projectCredentials: GitCredentials? = null,
    val projectAuthorName: String? = null,
    val projectAuthorEmail: String? = null,
    val defaultCredentials: GitCredentials? = null,
    val globalAuthorName: String = GitEngine.DEFAULT_AUTHOR_NAME,
    val globalAuthorEmail: String = GitEngine.DEFAULT_AUTHOR_EMAIL,
    val aiApiKeys: Map<AiProvider, String> = emptyMap(),
    val aiProxyUrl: String = "",
    val localModelStates: Map<String, LocalModelDownloadState> = emptyMap(),
    val infoMessage: String? = null
)

class SettingsViewModel(
    private val projectId: String?,
    private val repoId: String? = null,
    private val preferencesStore: AppPreferencesStore,
    private val credentialStore: CredentialStore,
    private val registry: ProjectRegistry,
    private val gitEngine: GitEngine = JGitEngine(),
    private val localModelManager: LocalModelManager? = null,
    private val onLocalProviderDeselected: () -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val localState = MutableStateFlow(SettingsUiState(projectId = projectId, repoId = repoId))
    private val emptyLocalModelsFlow = MutableStateFlow<Map<String, LocalModelDownloadState>>(emptyMap())

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesStore.preferences,
        localState,
        localModelManager?.states ?: emptyLocalModelsFlow
    ) { prefs, local, modelStates ->
        local.copy(preferences = prefs, localModelStates = modelStates)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(
            preferences = preferencesStore.preferences.value,
            projectId = projectId,
            repoId = repoId,
            localModelStates = localModelManager?.states?.value.orEmpty()
        )
    )

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val loaded = withContext(ioDispatcher) {
                val project = projectId?.let { registry.findProjectById(it) }
                val repoEntry = if (projectId != null && repoId != null) {
                    registry.findRepository(projectId, repoId)
                } else null
                val repoDir = repoEntry?.let { File(it.directoryPath) }
                val isGitRepo = repoDir?.let { gitEngine.isGitRepository(it) } == true
                val localRepoAuthor = if (repoDir != null && isGitRepo) {
                    gitEngine.getLocalAuthorConfig(repoDir)
                } else null
                val (repoAuthorName, repoAuthorEmail) = if (repoDir != null && isGitRepo) {
                    GitEngine.resolveEffectiveAuthor(gitEngine, repoDir, project)
                } else {
                    GitEngine.DEFAULT_AUTHOR_NAME to GitEngine.DEFAULT_AUTHOR_EMAIL
                }
                val repoCreds = repoId?.let { credentialStore.getRepoCredentials(it) }
                val projectCreds = projectId?.let { credentialStore.getProjectCredentials(it) }
                val defaultCreds = credentialStore.getDefaultCredentials()
                val aiApiKeys = credentialStore.getAllAiApiKeys()
                val aiProxyUrl = credentialStore.getAiProxyUrl().orEmpty()
                val (authorName, authorEmail) = readGlobalAuthor()

                SettingsUiState(
                    projectId = projectId,
                    projectName = project?.name,
                    repoId = repoId,
                    repoEntry = repoEntry,
                    isGitRepo = isGitRepo,
                    repoCredentials = repoCreds,
                    repoHasCustomAuthor = localRepoAuthor != null,
                    repoAuthorName = repoAuthorName,
                    repoAuthorEmail = repoAuthorEmail,
                    projectCredentials = projectCreds,
                    projectAuthorName = project?.authorName,
                    projectAuthorEmail = project?.authorEmail,
                    defaultCredentials = defaultCreds,
                    globalAuthorName = authorName,
                    globalAuthorEmail = authorEmail,
                    aiApiKeys = aiApiKeys,
                    aiProxyUrl = aiProxyUrl
                )
            }
            localState.update { current ->
                loaded.copy(infoMessage = current.infoMessage)
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferencesStore.setThemeMode(mode)
    }

    fun setAnimatedBackgroundEnabled(enabled: Boolean) {
        preferencesStore.setAnimatedBackgroundEnabled(enabled)
    }

    fun saveDefaultCredentials(username: String, token: String) {
        viewModelScope.launch(ioDispatcher) {
            if (token.isBlank()) {
                credentialStore.clearDefaultCredentials()
            } else {
                credentialStore.saveDefaultCredentials(GitCredentials(username.trim(), token.trim()))
            }
            val updated = credentialStore.getDefaultCredentials()
            localState.update {
                it.copy(
                    defaultCredentials = updated,
                    infoMessage = if (token.isBlank()) {
                        "Токен по умолчанию очищен"
                    } else {
                        "Токен по умолчанию сохранён"
                    }
                )
            }
        }
    }

    fun saveProjectCredentials(username: String, token: String) {
        val targetProjectId = projectId ?: return
        viewModelScope.launch(ioDispatcher) {
            if (token.isBlank()) {
                credentialStore.removeProjectCredentials(targetProjectId)
            } else {
                credentialStore.saveProjectCredentials(
                    targetProjectId,
                    GitCredentials(username.trim(), token.trim())
                )
            }
            val updated = credentialStore.getProjectCredentials(targetProjectId)
            localState.update {
                it.copy(
                    projectCredentials = updated,
                    infoMessage = if (token.isBlank()) {
                        "Токен проекта очищен"
                    } else {
                        "Токен проекта сохранён"
                    }
                )
            }
        }
    }

    fun saveRepoCredentials(username: String, token: String) {
        val targetRepoId = repoId ?: return
        viewModelScope.launch(ioDispatcher) {
            if (token.isBlank()) {
                credentialStore.removeRepoCredentials(targetRepoId)
            } else {
                credentialStore.saveRepoCredentials(
                    targetRepoId,
                    GitCredentials(username.trim(), token.trim())
                )
            }
            val updated = credentialStore.getRepoCredentials(targetRepoId)
            localState.update {
                it.copy(
                    repoCredentials = updated,
                    infoMessage = if (token.isBlank()) {
                        "Токен репозитория очищен"
                    } else {
                        "Токен репозитория сохранён"
                    }
                )
            }
        }
    }

    fun saveProjectAuthor(name: String, email: String) {
        val targetProjectId = projectId ?: return
        val cleanName = name.trim().ifEmpty { null }
        val cleanEmail = email.trim().ifEmpty { null }
        val clearing = cleanName == null && cleanEmail == null
        viewModelScope.launch(ioDispatcher) {
            registry.updateProjectAuthor(targetProjectId, cleanName, cleanEmail)
            val updatedProject = registry.findProjectById(targetProjectId)
            val repo = localState.value.repoEntry
            val isGitRepo = localState.value.isGitRepo
            val (effRepoName, effRepoEmail) = if (repo != null && isGitRepo) {
                GitEngine.resolveEffectiveAuthor(gitEngine, File(repo.directoryPath), updatedProject)
            } else {
                localState.value.repoAuthorName to localState.value.repoAuthorEmail
            }
            localState.update {
                it.copy(
                    projectAuthorName = updatedProject?.authorName,
                    projectAuthorEmail = updatedProject?.authorEmail,
                    repoAuthorName = effRepoName,
                    repoAuthorEmail = effRepoEmail,
                    infoMessage = if (clearing) {
                        "Автор коммитов проекта сброшен (используется по умолчанию)"
                    } else {
                        "Автор коммитов проекта сохранён"
                    }
                )
            }
        }
    }

    fun saveRepoAuthor(name: String, email: String, saveGlobally: Boolean) {
        val repo = localState.value.repoEntry ?: return
        val cleanName = name.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_NAME }
        val cleanEmail = email.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_EMAIL }
        viewModelScope.launch(ioDispatcher) {
            gitEngine.setAuthorConfig(File(repo.directoryPath), cleanName, cleanEmail, saveGlobally)
            val (globalName, globalEmail) = readGlobalAuthor()
            localState.update {
                it.copy(
                    repoHasCustomAuthor = true,
                    repoAuthorName = cleanName,
                    repoAuthorEmail = cleanEmail,
                    globalAuthorName = globalName,
                    globalAuthorEmail = globalEmail,
                    infoMessage = "Автор коммитов репозитория обновлён"
                )
            }
        }
    }

    fun clearRepoAuthor() {
        val repo = localState.value.repoEntry ?: return
        viewModelScope.launch(ioDispatcher) {
            val repoDir = File(repo.directoryPath)
            gitEngine.clearLocalAuthorConfig(repoDir)
            val project = projectId?.let { registry.findProjectById(it) }
            val (effName, effEmail) = GitEngine.resolveEffectiveAuthor(gitEngine, repoDir, project)
            localState.update {
                it.copy(
                    repoHasCustomAuthor = false,
                    repoAuthorName = effName,
                    repoAuthorEmail = effEmail,
                    infoMessage = "Автор коммитов репозитория сброшен"
                )
            }
        }
    }

    fun saveGlobalAuthor(name: String, email: String) {
        val cleanName = name.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_NAME }
        val cleanEmail = email.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_EMAIL }
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                val userConfig = SystemReader.getInstance().openUserConfig(null, FS.DETECTED)
                userConfig.load()
                userConfig.setString("user", null, "name", cleanName)
                userConfig.setString("user", null, "email", cleanEmail)
                userConfig.save()
            }
            val repo = localState.value.repoEntry
            val isGitRepo = localState.value.isGitRepo
            val project = projectId?.let { registry.findProjectById(it) }
            val (effRepoName, effRepoEmail) = if (repo != null && isGitRepo) {
                GitEngine.resolveEffectiveAuthor(gitEngine, File(repo.directoryPath), project)
            } else {
                localState.value.repoAuthorName to localState.value.repoAuthorEmail
            }
            localState.update {
                it.copy(
                    globalAuthorName = cleanName,
                    globalAuthorEmail = cleanEmail,
                    repoAuthorName = effRepoName,
                    repoAuthorEmail = effRepoEmail,
                    infoMessage = "Глобальный автор коммитов сохранён"
                )
            }
        }
    }

    fun downloadLocalModel(modelId: String, proxyEnabledOverride: Boolean? = null, proxyUrlOverride: String? = null) {
        val manager = localModelManager ?: return
        val useProxy = proxyEnabledOverride ?: preferencesStore.preferences.value.aiSettings.proxyEnabled
        val rawProxy = proxyUrlOverride ?: credentialStore.getAiProxyUrl().orEmpty()
        val parsedProxy = if (useProxy && rawProxy.isNotBlank()) {
            (parseAiProxyUrl(rawProxy) as? ProxyParseResult.Valid)?.config
        } else {
            null
        }
        manager.startDownload(modelId = modelId, proxy = parsedProxy)
    }

    fun cancelLocalModelDownload(modelId: String) {
        localModelManager?.cancelDownload(modelId)
    }

    fun deleteLocalModel(modelId: String) {
        localModelManager?.deleteModel(modelId)
        localState.update { it.copy(infoMessage = "Локальная модель удалена") }
    }

    fun saveAiSettings(
        settings: AiAgentSettings,
        apiKeysByProvider: Map<AiProvider, String>,
        proxyUrl: String = ""
    ) {
        viewModelScope.launch(ioDispatcher) {
            preferencesStore.updateAiSettings(settings)
            AiProvider.entries.filter { it.requiresApiKey }.forEach { provider ->
                val cleanKey = apiKeysByProvider[provider]?.trim().orEmpty()
                if (cleanKey.isBlank()) {
                    credentialStore.clearAiApiKey(provider)
                } else {
                    credentialStore.saveAiApiKey(provider, cleanKey)
                }
            }
            val cleanProxy = proxyUrl.trim()
            if (cleanProxy.isBlank()) {
                credentialStore.clearAiProxyUrl()
            } else {
                credentialStore.saveAiProxyUrl(cleanProxy)
            }
            if (settings.provider != AiProvider.LOCAL) {
                onLocalProviderDeselected()
            }
            val savedKeys = credentialStore.getAllAiApiKeys()
            val savedProxy = credentialStore.getAiProxyUrl().orEmpty()
            val message = if (settings.provider == AiProvider.LOCAL) {
                val modelOption = settings.provider.findModelOption(settings.modelName)
                if (localModelManager?.isModelDownloaded(modelOption.id) == true) {
                    "Локальная модель «${modelOption.label}» сохранена и активна"
                } else {
                    "Настройки агента сохранены (скачайте выбранную локальную модель)"
                }
            } else {
                "Настройки ИИ и агента сохранены"
            }
            localState.update {
                it.copy(
                    aiApiKeys = savedKeys,
                    aiProxyUrl = savedProxy,
                    infoMessage = message
                )
            }
        }
    }

    fun clearMessage() {
        localState.update { it.copy(infoMessage = null) }
    }

    private fun readGlobalAuthor(): Pair<String, String> {
        return runCatching {
            val userConfig = SystemReader.getInstance().openUserConfig(null, FS.DETECTED)
            userConfig.load()
            val name = userConfig.getString("user", null, "name")
                ?.takeIf { it.isNotBlank() }
                ?: GitEngine.DEFAULT_AUTHOR_NAME
            val email = userConfig.getString("user", null, "email")
                ?.takeIf { it.isNotBlank() }
                ?: GitEngine.DEFAULT_AUTHOR_EMAIL
            name to email
        }.getOrDefault(GitEngine.DEFAULT_AUTHOR_NAME to GitEngine.DEFAULT_AUTHOR_EMAIL)
    }

    companion object {
        fun factory(
            projectId: String?,
            repoId: String? = null,
            preferencesStore: AppPreferencesStore,
            credentialStore: CredentialStore,
            registry: ProjectRegistry,
            gitEngine: GitEngine,
            localModelManager: LocalModelManager? = null,
            onLocalProviderDeselected: () -> Unit = {}
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    projectId = projectId,
                    repoId = repoId,
                    preferencesStore = preferencesStore,
                    credentialStore = credentialStore,
                    registry = registry,
                    gitEngine = gitEngine,
                    localModelManager = localModelManager,
                    onLocalProviderDeselected = onLocalProviderDeselected
                ) as T
            }
        }
    }
}
