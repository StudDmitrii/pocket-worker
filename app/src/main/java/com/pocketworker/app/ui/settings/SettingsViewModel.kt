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
import com.pocketworker.app.core.git.GitOAuthAccount
import com.pocketworker.app.core.git.GitOAuthDefaults
import com.pocketworker.app.core.git.GitOAuthFlowState
import com.pocketworker.app.core.git.GitOAuthManager
import com.pocketworker.app.core.git.GitOAuthProvider
import com.pocketworker.app.core.git.JGitEngine
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.git.extractHostFromGitUrl
import com.pocketworker.app.core.git.normalizeGitBaseUrl
import com.pocketworker.app.core.i18n.AppLanguage
import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.i18n.AppStrings
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

enum class SettingsTab {
    GENERAL,
    AGENT;

    fun localizedTitle(s: AppStrings): String = when (this) {
        GENERAL -> s.settingsTabGeneral
        AGENT -> s.settingsTabAgent
    }
}

enum class OAuthAutoBindScope {
    NONE,
    PROJECT,
    REPO
}

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val projectId: String? = null,
    val projectName: String? = null,
    val repoId: String? = null,
    val repoEntry: RepoEntry? = null,
    val isGitRepo: Boolean = false,
    val oauthAccounts: List<GitOAuthAccount> = emptyList(),
    val defaultOAuthAccountIdsByHost: Map<String, String> = emptyMap(),
    val oauthFlowState: GitOAuthFlowState = GitOAuthFlowState.Idle,
    val lastOAuthCompletedEventId: Long = 0L,
    val githubClientId: String = "",
    val gitlabCloudClientId: String = "",
    val repoCredentials: GitCredentials? = null,
    val repoOAuthAccount: GitOAuthAccount? = null,
    val repoHasCustomAuthor: Boolean = false,
    val repoAuthorName: String = GitEngine.DEFAULT_AUTHOR_NAME,
    val repoAuthorEmail: String = GitEngine.DEFAULT_AUTHOR_EMAIL,
    val projectCredentials: GitCredentials? = null,
    val projectOAuthAccount: GitOAuthAccount? = null,
    val projectAuthorName: String? = null,
    val projectAuthorEmail: String? = null,
    val defaultCredentials: GitCredentials? = null,
    val hasCustomGlobalAuthor: Boolean = false,
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val oauthManager: GitOAuthManager? = null
) : ViewModel() {

    private val localState = MutableStateFlow(SettingsUiState(projectId = projectId, repoId = repoId))
    private val emptyLocalModelsFlow = MutableStateFlow<Map<String, LocalModelDownloadState>>(emptyMap())
    private val idleOAuthFlow = MutableStateFlow<GitOAuthFlowState>(GitOAuthFlowState.Idle)
    private var pendingAutoBindScope: OAuthAutoBindScope = OAuthAutoBindScope.NONE

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesStore.preferences,
        localState,
        localModelManager?.states ?: emptyLocalModelsFlow,
        oauthManager?.flowState ?: idleOAuthFlow
    ) { prefs, local, modelStates, oauthFlow ->
        local.copy(
            preferences = prefs,
            localModelStates = modelStates,
            oauthFlowState = oauthFlow
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(
            preferences = preferencesStore.preferences.value,
            projectId = projectId,
            repoId = repoId,
            localModelStates = localModelManager?.states?.value.orEmpty(),
            oauthFlowState = oauthManager?.flowState?.value ?: GitOAuthFlowState.Idle
        )
    )

    init {
        loadData()
        observeOAuthEvents()
    }

    private fun observeOAuthEvents() {
        val manager = oauthManager ?: return
        viewModelScope.launch {
            manager.accountsVersion.collect { version ->
                if (version > 0L) {
                    loadData()
                }
            }
        }
        viewModelScope.launch {
            manager.flowState.collect { state ->
                if (state is GitOAuthFlowState.Completed) {
                    val scope = pendingAutoBindScope
                    pendingAutoBindScope = OAuthAutoBindScope.NONE
                    withContext(ioDispatcher) {
                        when (scope) {
                            OAuthAutoBindScope.PROJECT ->
                                projectId?.let { credentialStore.setProjectOAuthAccountId(it, state.account.id) }
                            OAuthAutoBindScope.REPO ->
                                repoId?.let { credentialStore.setRepoOAuthAccountId(it, state.account.id) }
                            OAuthAutoBindScope.NONE -> Unit
                        }
                    }
                    val s = AppLocale.current
                    val label = state.account.shortLabel()
                    val msg = if (state.autoFilledAuthor) {
                        s.oauthSignedInWithAuthorInfo(label)
                    } else {
                        s.oauthSignedInInfo(label)
                    }
                    localState.update {
                        it.copy(
                            infoMessage = msg,
                            lastOAuthCompletedEventId = it.lastOAuthCompletedEventId + 1L
                        )
                    }
                    loadData()
                    manager.resetFlow()
                }
            }
        }
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
                val oauthAccounts = credentialStore.getOAuthAccounts()
                val hostDefaults = credentialStore.getDefaultOAuthAccountIdsByHost()
                val repoOAuthAccount = repoId?.let { id ->
                    credentialStore.getRepoOAuthAccountId(id)?.let { accId ->
                        oauthAccounts.firstOrNull { it.id == accId }
                    }
                }
                val projectOAuthAccount = projectId?.let { id ->
                    credentialStore.getProjectOAuthAccountId(id)?.let { accId ->
                        oauthAccounts.firstOrNull { it.id == accId }
                    }
                }
                val githubClientId = oauthManager?.effectiveClientId(GitOAuthProvider.GITHUB)
                    ?: credentialStore.getOAuthClientId(GitOAuthProvider.GITHUB)
                    ?: GitOAuthDefaults.buildConfigClientId(GitOAuthProvider.GITHUB)
                val gitlabCloudClientId = oauthManager?.effectiveClientId(GitOAuthProvider.GITLAB)
                    ?: credentialStore.getOAuthClientId(GitOAuthProvider.GITLAB)
                    ?: GitOAuthDefaults.buildConfigClientId(GitOAuthProvider.GITLAB)
                val repoCreds = repoId?.let { credentialStore.getRepoCredentials(it) }
                val projectCreds = projectId?.let { credentialStore.getProjectCredentials(it) }
                val defaultCreds = credentialStore.getDefaultCredentials()
                val aiApiKeys = credentialStore.getAllAiApiKeys()
                val aiProxyUrl = credentialStore.getAiProxyUrl().orEmpty()
                val (authorName, authorEmail, hasCustomGlobalAuthor) = readGlobalAuthor()

                SettingsUiState(
                    projectId = projectId,
                    projectName = project?.name,
                    repoId = repoId,
                    repoEntry = repoEntry,
                    isGitRepo = isGitRepo,
                    oauthAccounts = oauthAccounts,
                    defaultOAuthAccountIdsByHost = hostDefaults,
                    githubClientId = githubClientId,
                    gitlabCloudClientId = gitlabCloudClientId,
                    repoCredentials = repoCreds,
                    repoOAuthAccount = repoOAuthAccount,
                    repoHasCustomAuthor = localRepoAuthor != null,
                    repoAuthorName = repoAuthorName,
                    repoAuthorEmail = repoAuthorEmail,
                    projectCredentials = projectCreds,
                    projectOAuthAccount = projectOAuthAccount,
                    projectAuthorName = project?.authorName,
                    projectAuthorEmail = project?.authorEmail,
                    defaultCredentials = defaultCreds,
                    hasCustomGlobalAuthor = hasCustomGlobalAuthor,
                    globalAuthorName = authorName,
                    globalAuthorEmail = authorEmail,
                    aiApiKeys = aiApiKeys,
                    aiProxyUrl = aiProxyUrl
                )
            }
            localState.update { current ->
                loaded.copy(
                    infoMessage = current.infoMessage,
                    lastOAuthCompletedEventId = current.lastOAuthCompletedEventId
                )
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferencesStore.setThemeMode(mode)
    }

    fun setLanguage(language: AppLanguage) {
        AppLocale.setLanguage(language)
        preferencesStore.setLanguage(language)
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
            val s = AppLocale.current
            localState.update {
                it.copy(
                    defaultCredentials = updated,
                    infoMessage = if (token.isBlank()) {
                        s.homeInfoDefaultTokenCleared
                    } else {
                        s.homeInfoDefaultTokenSaved
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
            val s = AppLocale.current
            localState.update {
                it.copy(
                    projectCredentials = updated,
                    projectOAuthAccount = if (token.isBlank()) it.projectOAuthAccount else null,
                    infoMessage = if (token.isBlank()) {
                        s.projectInfoProjectTokenCleared
                    } else {
                        s.projectInfoProjectTokenSaved
                    }
                )
            }
        }
    }

    fun bindProjectOAuthAccount(accountId: String?) {
        val targetProjectId = projectId ?: return
        viewModelScope.launch(ioDispatcher) {
            if (accountId.isNullOrBlank()) {
                credentialStore.setProjectOAuthAccountId(targetProjectId, null)
                credentialStore.removeProjectCredentials(targetProjectId)
            } else {
                credentialStore.setProjectOAuthAccountId(targetProjectId, accountId)
            }
            val bound = credentialStore.getProjectOAuthAccountId(targetProjectId)
                ?.let { credentialStore.getOAuthAccount(it) }
            val updatedCreds = credentialStore.getProjectCredentials(targetProjectId)
            val s = AppLocale.current
            localState.update {
                it.copy(
                    projectCredentials = updatedCreds,
                    projectOAuthAccount = bound,
                    infoMessage = if (bound != null) {
                        s.oauthProjectAccountBoundInfo
                    } else {
                        s.projectInfoProjectTokenCleared
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
            val s = AppLocale.current
            localState.update {
                it.copy(
                    repoCredentials = updated,
                    repoOAuthAccount = if (token.isBlank()) it.repoOAuthAccount else null,
                    infoMessage = if (token.isBlank()) {
                        s.workspaceInfoRepoTokenCleared
                    } else {
                        s.workspaceInfoRepoTokenSaved
                    }
                )
            }
        }
    }

    fun bindRepoOAuthAccount(accountId: String?) {
        val targetRepoId = repoId ?: return
        viewModelScope.launch(ioDispatcher) {
            if (accountId.isNullOrBlank()) {
                credentialStore.setRepoOAuthAccountId(targetRepoId, null)
                credentialStore.removeRepoCredentials(targetRepoId)
            } else {
                credentialStore.setRepoOAuthAccountId(targetRepoId, accountId)
            }
            val bound = credentialStore.getRepoOAuthAccountId(targetRepoId)
                ?.let { credentialStore.getOAuthAccount(it) }
            val updatedCreds = credentialStore.getRepoCredentials(targetRepoId)
            val s = AppLocale.current
            localState.update {
                it.copy(
                    repoCredentials = updatedCreds,
                    repoOAuthAccount = bound,
                    infoMessage = if (bound != null) {
                        s.oauthRepoAccountBoundInfo
                    } else {
                        s.workspaceInfoRepoTokenCleared
                    }
                )
            }
        }
    }

    fun startGitHubOAuth(
        clientId: String,
        bindScope: OAuthAutoBindScope = OAuthAutoBindScope.NONE
    ) {
        pendingAutoBindScope = bindScope
        oauthManager?.startGitHubDeviceFlow(clientId)
    }

    fun prepareGitLabOAuth(
        baseUrl: String,
        clientId: String,
        bindScope: OAuthAutoBindScope = OAuthAutoBindScope.NONE
    ): String? {
        pendingAutoBindScope = bindScope
        return oauthManager?.prepareGitLabAuthorization(baseUrl, clientId)
    }

    fun cancelOAuthFlow() {
        pendingAutoBindScope = OAuthAutoBindScope.NONE
        oauthManager?.resetFlow()
    }

    fun getSavedGitLabClientIdForUrl(rawUrl: String): String {
        val normalized = normalizeGitBaseUrl(rawUrl)
        val host = extractHostFromGitUrl(normalized) ?: GitOAuthProvider.GITLAB.defaultHost
        return oauthManager?.effectiveClientId(GitOAuthProvider.GITLAB, host)
            ?: credentialStore.getOAuthClientId(GitOAuthProvider.GITLAB, host)
            ?: if (host == GitOAuthProvider.GITLAB.defaultHost) {
                GitOAuthDefaults.buildConfigClientId(GitOAuthProvider.GITLAB)
            } else {
                ""
            }
    }

    fun setDefaultOAuthAccountForHost(host: String, accountId: String) {
        viewModelScope.launch(ioDispatcher) {
            credentialStore.setDefaultOAuthAccountForHost(host, accountId)
            oauthManager?.notifyAccountsChanged()
            val accounts = credentialStore.getOAuthAccounts()
            val target = accounts.firstOrNull { it.id == accountId }
            val defaults = credentialStore.getDefaultOAuthAccountIdsByHost()
            localState.update {
                it.copy(
                    oauthAccounts = accounts,
                    defaultOAuthAccountIdsByHost = defaults,
                    infoMessage = target?.let { acc ->
                        AppLocale.current.oauthHostDefaultUpdatedInfo(acc.shortLabel())
                    }
                )
            }
        }
    }

    fun removeOAuthAccount(accountId: String) {
        viewModelScope.launch(ioDispatcher) {
            val target = credentialStore.getOAuthAccount(accountId)
            credentialStore.removeOAuthAccount(accountId)
            oauthManager?.notifyAccountsChanged()
            val accounts = credentialStore.getOAuthAccounts()
            val defaults = credentialStore.getDefaultOAuthAccountIdsByHost()
            val projBound = projectId?.let { credentialStore.getProjectOAuthAccountId(it) }
                ?.let { id -> accounts.firstOrNull { it.id == id } }
            val repoBound = repoId?.let { credentialStore.getRepoOAuthAccountId(it) }
                ?.let { id -> accounts.firstOrNull { it.id == id } }
            localState.update {
                it.copy(
                    oauthAccounts = accounts,
                    defaultOAuthAccountIdsByHost = defaults,
                    projectOAuthAccount = projBound,
                    repoOAuthAccount = repoBound,
                    infoMessage = target?.let { acc ->
                        AppLocale.current.oauthAccountRemovedInfo(acc.shortLabel())
                    }
                )
            }
        }
    }

    fun notifyInfo(message: String) {
        if (message.isNotBlank()) {
            localState.update { it.copy(infoMessage = message) }
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
            val s = AppLocale.current
            localState.update {
                it.copy(
                    projectAuthorName = updatedProject?.authorName,
                    projectAuthorEmail = updatedProject?.authorEmail,
                    repoAuthorName = effRepoName,
                    repoAuthorEmail = effRepoEmail,
                    infoMessage = if (clearing) {
                        s.settingsInfoProjectAuthorReset
                    } else {
                        s.settingsInfoProjectAuthorSaved
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
            val (globalName, globalEmail, hasGlobalAuthor) = readGlobalAuthor()
            localState.update {
                it.copy(
                    repoHasCustomAuthor = true,
                    repoAuthorName = cleanName,
                    repoAuthorEmail = cleanEmail,
                    hasCustomGlobalAuthor = hasGlobalAuthor,
                    globalAuthorName = globalName,
                    globalAuthorEmail = globalEmail,
                    infoMessage = AppLocale.current.settingsInfoRepoAuthorUpdated
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
                    infoMessage = AppLocale.current.settingsInfoRepoAuthorReset
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
                    hasCustomGlobalAuthor = true,
                    globalAuthorName = cleanName,
                    globalAuthorEmail = cleanEmail,
                    repoAuthorName = effRepoName,
                    repoAuthorEmail = effRepoEmail,
                    infoMessage = AppLocale.current.settingsInfoGlobalAuthorSaved
                )
            }
        }
    }

    fun clearGlobalAuthor() {
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                val userConfig = SystemReader.getInstance().openUserConfig(null, FS.DETECTED)
                userConfig.load()
                userConfig.unset("user", null, "name")
                userConfig.unset("user", null, "email")
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
                    hasCustomGlobalAuthor = false,
                    globalAuthorName = GitEngine.DEFAULT_AUTHOR_NAME,
                    globalAuthorEmail = GitEngine.DEFAULT_AUTHOR_EMAIL,
                    repoAuthorName = effRepoName,
                    repoAuthorEmail = effRepoEmail,
                    infoMessage = AppLocale.current.settingsInfoGlobalAuthorReset
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
        localState.update { it.copy(infoMessage = AppLocale.current.settingsInfoLocalModelDeleted) }
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
            val s = AppLocale.current
            val message = if (settings.provider == AiProvider.LOCAL) {
                val modelOption = settings.provider.findModelOption(settings.modelName)
                if (localModelManager?.isModelDownloaded(modelOption.id) == true) {
                    s.settingsInfoLocalModelActive(modelOption.label)
                } else {
                    s.settingsInfoAgentSavedDownloadLocal
                }
            } else {
                s.settingsInfoAgentSaved
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

    private fun readGlobalAuthor(): Triple<String, String, Boolean> {
        return runCatching {
            val userConfig = SystemReader.getInstance().openUserConfig(null, FS.DETECTED)
            userConfig.load()
            val rawName = userConfig.getString("user", null, "name")?.takeIf { it.isNotBlank() }
            val rawEmail = userConfig.getString("user", null, "email")?.takeIf { it.isNotBlank() }
            val hasCustom = rawName != null || rawEmail != null
            Triple(
                rawName ?: GitEngine.DEFAULT_AUTHOR_NAME,
                rawEmail ?: GitEngine.DEFAULT_AUTHOR_EMAIL,
                hasCustom
            )
        }.getOrDefault(Triple(GitEngine.DEFAULT_AUTHOR_NAME, GitEngine.DEFAULT_AUTHOR_EMAIL, false))
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
            onLocalProviderDeselected: () -> Unit = {},
            oauthManager: GitOAuthManager? = null
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
                    onLocalProviderDeselected = onLocalProviderDeselected,
                    oauthManager = oauthManager
                ) as T
            }
        }
    }
}
