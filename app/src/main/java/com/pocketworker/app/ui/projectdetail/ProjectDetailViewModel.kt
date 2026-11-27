package com.pocketworker.app.ui.projectdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketworker.app.core.git.CloneProgress
import com.pocketworker.app.core.git.DiscoveredRepo
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.GitOAuthManager
import com.pocketworker.app.core.git.HttpRepoGroupDiscoveryService
import com.pocketworker.app.core.git.RepoCardInfo
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.git.RepoGroupDiscoveryService
import com.pocketworker.app.core.git.WorkspaceProject
import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import com.pocketworker.app.ui.common.formatGitTransportError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class DiscoveredRepoItem(
    val repo: DiscoveredRepo,
    val selected: Boolean,
    val alreadyAdded: Boolean
)

data class ProjectDetailUiState(
    val project: WorkspaceProject? = null,
    val repos: List<RepoCardInfo> = emptyList(),
    val newlyAddedRepoIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isCloning: Boolean = false,
    val cloneProgress: CloneProgress? = null,
    val cloneError: String? = null,
    val isDiscoveringGroup: Boolean = false,
    val groupDiscoveryError: String? = null,
    val discoveredRepos: List<DiscoveredRepoItem> = emptyList(),
    val isGroupCloning: Boolean = false,
    val groupCloneCurrentIndex: Int = 0,
    val groupCloneTotal: Int = 0,
    val groupCloneCurrentRepoName: String = "",
    val groupCloneProgress: CloneProgress? = null,
    val groupCloneError: String? = null,
    val isCreatingRepo: Boolean = false,
    val createRepoError: String? = null,
    val projectCredentials: GitCredentials? = null,
    val defaultCredentials: GitCredentials? = null,
    val hasConnectedOAuthAccounts: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class ProjectDetailViewModel(
    private val projectId: String,
    private val gitEngine: GitEngine,
    private val registry: ProjectRegistry,
    private val credentialStore: CredentialStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val groupDiscoveryService: RepoGroupDiscoveryService = HttpRepoGroupDiscoveryService(),
    private val oauthManager: GitOAuthManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    private var cloneJob: Job? = null
    private var cloneCancelFlag = AtomicBoolean(false)
    private var groupJob: Job? = null
    private var groupCancelFlag = AtomicBoolean(false)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = withContext(ioDispatcher) {
                runCatching {
                    val project = registry.findProjectById(projectId)
                        ?: throw IllegalStateException(AppLocale.current.projectErrNotFound)
                    val repos = registry.listRepositories(projectId)
                    val cards = repos.map { gitEngine.getRepoSummary(it) }
                    val projCreds = credentialStore.getProjectCredentials(projectId)
                    val defCreds = credentialStore.getDefaultCredentials()
                    val hasOAuth = credentialStore.getProjectOAuthAccountId(projectId) != null ||
                        credentialStore.getOAuthAccounts().isNotEmpty()
                    LoadedProjectDetail(project, cards, projCreds, defCreds, hasOAuth)
                }
            }
            result.onSuccess { data ->
                val pendingNewRepos = registry.getPendingNewRepoIds()
                _uiState.update {
                    it.copy(
                        project = data.project,
                        repos = data.repos,
                        newlyAddedRepoIds = pendingNewRepos,
                        projectCredentials = data.projectCreds,
                        defaultCredentials = data.defaultCreds,
                        hasConnectedOAuthAccounts = data.hasConnectedOAuthAccounts,
                        isLoading = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: AppLocale.current.projectErrLoadFailed
                    )
                }
            }
        }
    }

    fun cloneRepository(
        url: String,
        customName: String,
        branch: String,
        useAuth: Boolean,
        username: String,
        token: String,
        saveCredentialsForRepo: Boolean,
        onSuccess: (String) -> Unit
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            _uiState.update { it.copy(cloneError = AppLocale.current.projectErrEnterRepoUrl) }
            return
        }

        val derivedName = customName.trim().ifEmpty { deriveRepoNameFromUrl(cleanUrl) }
        cloneCancelFlag = AtomicBoolean(false)

        cloneJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCloning = true,
                    cloneError = null,
                    cloneProgress = CloneProgress(AppLocale.current.projectCloneConnecting, 0, 0)
                )
            }

            val creds = resolveCloneCredentials(cleanUrl, useAuth, username, token)

            val outcome = withContext(ioDispatcher) {
                runCatching {
                    cloneAndRegisterSingleRepo(
                        url = cleanUrl,
                        repoName = derivedName,
                        branch = branch.trim().ifEmpty { null },
                        credentials = creds,
                        saveCredentialsForRepo = useAuth && saveCredentialsForRepo && token.isNotBlank(),
                        savedCredentialsToStore = if (useAuth && token.isNotBlank()) {
                            GitCredentials(username.trim(), token.trim())
                        } else null,
                        onProgress = { progress ->
                            _uiState.update { state -> state.copy(cloneProgress = progress) }
                        },
                        isCancelled = { cloneCancelFlag.get() }
                    )
                }
            }

            outcome.onSuccess { repoId ->
                _uiState.update {
                    it.copy(
                        isCloning = false,
                        cloneProgress = null,
                        cloneError = null,
                        infoMessage = AppLocale.current.projectInfoRepoAdded(derivedName)
                    )
                }
                refresh()
                onSuccess(repoId)
            }.onFailure { err ->
                val cancelled = cloneCancelFlag.get()
                _uiState.update {
                    it.copy(
                        isCloning = false,
                        cloneProgress = null,
                        cloneError = if (cancelled) {
                            AppLocale.current.projectCloneCancelled
                        } else {
                            formatGitTransportError(err)
                        }
                    )
                }
            }
        }
    }

    fun cancelClone() {
        cloneCancelFlag.set(true)
        cloneJob?.cancel()
    }

    fun clearCloneError() {
        _uiState.update { it.copy(cloneError = null) }
    }

    fun discoverGroupRepositories(
        url: String,
        useAuth: Boolean,
        username: String,
        token: String
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            _uiState.update { it.copy(groupDiscoveryError = AppLocale.current.projectErrEnterGroupUrl) }
            return
        }

        groupJob?.cancel()
        groupJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDiscoveringGroup = true,
                    groupDiscoveryError = null,
                    groupCloneError = null,
                    discoveredRepos = emptyList()
                )
            }

            val creds = resolveCloneCredentials(cleanUrl, useAuth, username, token)
            val outcome = withContext(ioDispatcher) {
                runCatching {
                    groupDiscoveryService.discoverRepositories(cleanUrl, creds)
                }
            }

            outcome.onSuccess { found ->
                val existingUrls = _uiState.value.repos
                    .mapNotNull { it.entry.remoteUrl?.trim()?.removeSuffix(".git")?.lowercase() }
                    .toSet()
                val existingNames = _uiState.value.repos
                    .map { it.entry.name.trim().lowercase() }
                    .toSet()

                val items = found.map { repo ->
                    val normUrl = repo.cloneUrl.trim().removeSuffix(".git").lowercase()
                    val alreadyAdded = normUrl in existingUrls || repo.name.trim().lowercase() in existingNames
                    DiscoveredRepoItem(
                        repo = repo,
                        selected = !alreadyAdded,
                        alreadyAdded = alreadyAdded
                    )
                }

                _uiState.update {
                    it.copy(
                        isDiscoveringGroup = false,
                        discoveredRepos = items,
                        groupDiscoveryError = if (items.isEmpty()) {
                            AppLocale.current.projectErrGroupNoRepos
                        } else null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDiscoveringGroup = false,
                        groupDiscoveryError = formatGitTransportError(err)
                    )
                }
            }
        }
    }

    fun toggleDiscoveredRepoSelection(repoId: String) {
        if (_uiState.value.isGroupCloning) return
        _uiState.update { state ->
            state.copy(
                discoveredRepos = state.discoveredRepos.map { item ->
                    if (item.repo.id == repoId) item.copy(selected = !item.selected) else item
                }
            )
        }
    }

    fun setAllDiscoveredReposSelected(selected: Boolean) {
        if (_uiState.value.isGroupCloning) return
        _uiState.update { state ->
            state.copy(
                discoveredRepos = state.discoveredRepos.map { it.copy(selected = selected) }
            )
        }
    }

    fun cloneSelectedGroupRepositories(
        useAuth: Boolean,
        username: String,
        token: String,
        saveCredentialsForRepos: Boolean,
        onAllDone: () -> Unit
    ) {
        val selectedItems = _uiState.value.discoveredRepos.filter { it.selected }
        if (selectedItems.isEmpty()) {
            _uiState.update { it.copy(groupCloneError = AppLocale.current.projectErrSelectAtLeastOneRepo) }
            return
        }

        groupCancelFlag = AtomicBoolean(false)
        groupJob = viewModelScope.launch {
            val total = selectedItems.size
            val explicitTokenCreds = if (useAuth && token.isNotBlank()) {
                GitCredentials(username.trim(), token.trim())
            } else null

            _uiState.update {
                it.copy(
                    isGroupCloning = true,
                    groupCloneError = null,
                    groupCloneTotal = total,
                    groupCloneCurrentIndex = 0,
                    groupCloneCurrentRepoName = "",
                    groupCloneProgress = null
                )
            }

            var succeededCount = 0
            val succeededIds = mutableSetOf<String>()
            val failures = mutableListOf<String>()

            for ((idx, item) in selectedItems.withIndex()) {
                if (groupCancelFlag.get()) break

                val repo = item.repo
                _uiState.update {
                    it.copy(
                        groupCloneCurrentIndex = idx + 1,
                        groupCloneCurrentRepoName = repo.name,
                        groupCloneProgress = CloneProgress(AppLocale.current.projectGroupConnecting(repo.name), 0, 0)
                    )
                }

                val creds = resolveCloneCredentials(repo.cloneUrl, useAuth, username, token)
                val outcome = withContext(ioDispatcher) {
                    runCatching {
                        cloneAndRegisterSingleRepo(
                            url = repo.cloneUrl,
                            repoName = repo.name,
                            branch = repo.defaultBranch,
                            credentials = creds,
                            saveCredentialsForRepo = useAuth && saveCredentialsForRepos && explicitTokenCreds != null,
                            savedCredentialsToStore = explicitTokenCreds,
                            onProgress = { progress ->
                                _uiState.update { state -> state.copy(groupCloneProgress = progress) }
                            },
                            isCancelled = { groupCancelFlag.get() }
                        )
                    }
                }

                outcome.onSuccess {
                    succeededCount++
                    succeededIds.add(repo.id)
                }.onFailure { err ->
                    if (!groupCancelFlag.get()) {
                        failures.add("${repo.name}: ${formatGitTransportError(err)}")
                    }
                }
            }

            val wasCancelled = groupCancelFlag.get()
            refresh()

            val s = AppLocale.current
            _uiState.update { state ->
                val updatedItems = state.discoveredRepos.map { item ->
                    if (item.repo.id in succeededIds) {
                        item.copy(selected = false, alreadyAdded = true)
                    } else {
                        item
                    }
                }
                val errMsg = when {
                    wasCancelled -> s.projectGroupCancelled(succeededCount, total)
                    failures.isNotEmpty() -> buildString {
                        append(s.projectGroupPartialErrorsHeader(succeededCount, total))
                        append(failures.take(3).joinToString("\n"))
                        if (failures.size > 3) append(s.projectGroupMoreErrors(failures.size - 3))
                    }
                    else -> null
                }
                state.copy(
                    isGroupCloning = false,
                    groupCloneProgress = null,
                    discoveredRepos = updatedItems,
                    groupCloneError = errMsg,
                    infoMessage = if (!wasCancelled && failures.isEmpty()) {
                        s.projectGroupAddedCount(succeededCount)
                    } else null
                )
            }

            if (!wasCancelled && failures.isEmpty()) {
                onAllDone()
            }
        }
    }

    fun cancelGroupClone() {
        groupCancelFlag.set(true)
        groupJob?.cancel()
    }

    fun clearGroupError() {
        _uiState.update { it.copy(groupDiscoveryError = null, groupCloneError = null) }
    }

    fun resetGroupDialogState() {
        if (_uiState.value.isGroupCloning) return
        groupJob?.cancel()
        _uiState.update {
            it.copy(
                isDiscoveringGroup = false,
                groupDiscoveryError = null,
                discoveredRepos = emptyList(),
                groupCloneError = null,
                groupCloneProgress = null,
                groupCloneCurrentIndex = 0,
                groupCloneTotal = 0,
                groupCloneCurrentRepoName = ""
            )
        }
    }

    private suspend fun resolveCloneCredentials(
        targetUrl: String?,
        useAuth: Boolean,
        username: String,
        token: String
    ): GitCredentials? {
        return when {
            useAuth && token.isNotBlank() -> GitCredentials(username.trim(), token.trim())
            useAuth -> oauthManager?.resolveFreshCredentials(projectId, null, targetUrl)
                ?: credentialStore.resolveForRepo(projectId, null, targetUrl)
            else -> null
        }
    }

    private fun cloneAndRegisterSingleRepo(
        url: String,
        repoName: String,
        branch: String?,
        credentials: GitCredentials?,
        saveCredentialsForRepo: Boolean,
        savedCredentialsToStore: GitCredentials?,
        onProgress: (CloneProgress) -> Unit,
        isCancelled: () -> Boolean
    ): String {
        val (repoId, targetDir) = registry.allocateRepositoryDir(projectId, repoName)
        gitEngine.cloneRepository(
            url = url,
            targetDir = targetDir,
            branch = branch,
            credentials = credentials,
            onProgress = onProgress,
            isCancelled = isCancelled
        )
        val entry = RepoEntry(
            id = repoId,
            projectId = projectId,
            name = repoName,
            directoryPath = targetDir.absolutePath,
            createdAt = System.currentTimeMillis(),
            remoteUrl = url
        )
        registry.registerRepository(entry)
        if (saveCredentialsForRepo && savedCredentialsToStore != null) {
            credentialStore.saveRepoCredentials(repoId, savedCredentialsToStore)
        }
        return entry.id
    }

    fun createEmptyRepository(
        repoName: String,
        initGit: Boolean,
        initialBranch: String,
        createReadmeAndCommit: Boolean,
        remoteUrl: String,
        authorName: String,
        authorEmail: String,
        onSuccess: (String) -> Unit
    ) {
        val cleanName = repoName.trim()
        if (cleanName.isEmpty()) {
            _uiState.update { it.copy(createRepoError = AppLocale.current.projectErrEnterRepoName) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingRepo = true, createRepoError = null) }
            val outcome = withContext(ioDispatcher) {
                runCatching {
                    val (repoId, targetDir) = registry.allocateRepositoryDir(projectId, cleanName)
                    val cleanRemote = remoteUrl.trim().ifEmpty { null }
                    gitEngine.initProject(
                        targetDir = targetDir,
                        initialBranch = initialBranch.trim().ifEmpty { "main" },
                        initGit = initGit,
                        createReadmeAndCommit = createReadmeAndCommit,
                        projectName = cleanName,
                        remoteUrl = cleanRemote,
                        authorName = authorName.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_NAME },
                        authorEmail = authorEmail.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_EMAIL }
                    )
                    val entry = RepoEntry(
                        id = repoId,
                        projectId = projectId,
                        name = cleanName,
                        directoryPath = targetDir.absolutePath,
                        createdAt = System.currentTimeMillis(),
                        remoteUrl = cleanRemote
                    )
                    registry.registerRepository(entry)
                    entry.id
                }
            }

            outcome.onSuccess { repoId ->
                _uiState.update {
                    it.copy(
                        isCreatingRepo = false,
                        createRepoError = null,
                        infoMessage = AppLocale.current.projectInfoRepoCreated(cleanName)
                    )
                }
                refresh()
                onSuccess(repoId)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCreatingRepo = false,
                        createRepoError = err.localizedMessage ?: AppLocale.current.projectErrCreateRepo
                    )
                }
            }
        }
    }

    fun clearCreateRepoError() {
        _uiState.update { it.copy(createRepoError = null) }
    }

    fun deleteRepository(repoId: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                registry.deleteRepository(projectId, repoId, deleteFilesOnDisk = true)
                credentialStore.removeRepoCredentials(repoId)
            }
            _uiState.update { it.copy(infoMessage = AppLocale.current.projectInfoRepoDeleted) }
            refresh()
        }
    }

    fun saveProjectCredentials(username: String, token: String) {
        viewModelScope.launch(ioDispatcher) {
            if (token.isBlank()) {
                credentialStore.removeProjectCredentials(projectId)
            } else {
                credentialStore.saveProjectCredentials(
                    projectId,
                    GitCredentials(username.trim(), token.trim())
                )
            }
            val updated = credentialStore.getProjectCredentials(projectId)
            val s = AppLocale.current
            _uiState.update {
                it.copy(
                    projectCredentials = updated,
                    infoMessage = if (token.isBlank()) {
                        s.projectInfoProjectTokenCleared
                    } else {
                        s.projectInfoProjectTokenSaved
                    }
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun consumeNewlyAddedRepo(repoId: String) {
        registry.markRepoSeen(repoId)
        _uiState.update {
            it.copy(newlyAddedRepoIds = it.newlyAddedRepoIds - repoId)
        }
    }

    private data class LoadedProjectDetail(
        val project: WorkspaceProject,
        val repos: List<RepoCardInfo>,
        val projectCreds: GitCredentials?,
        val defaultCreds: GitCredentials?,
        val hasConnectedOAuthAccounts: Boolean
    )

    companion object {
        fun deriveRepoNameFromUrl(url: String): String {
            val cleaned = url.trim().trimEnd('/')
            val lastSegment = cleaned.substringAfterLast('/').substringAfterLast(':')
            return lastSegment.removeSuffix(".git").ifBlank { "repository" }
        }

        fun factory(
            projectId: String,
            gitEngine: GitEngine,
            registry: ProjectRegistry,
            credentialStore: CredentialStore,
            groupDiscoveryService: RepoGroupDiscoveryService = HttpRepoGroupDiscoveryService(),
            oauthManager: GitOAuthManager? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProjectDetailViewModel(
                    projectId = projectId,
                    gitEngine = gitEngine,
                    registry = registry,
                    credentialStore = credentialStore,
                    groupDiscoveryService = groupDiscoveryService,
                    oauthManager = oauthManager
                ) as T
            }
        }
    }
}
