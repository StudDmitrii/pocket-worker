package com.pocketworker.app.ui.projectdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketworker.app.core.git.CloneProgress
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.RepoCardInfo
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.git.WorkspaceProject
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

data class ProjectDetailUiState(
    val project: WorkspaceProject? = null,
    val repos: List<RepoCardInfo> = emptyList(),
    val isLoading: Boolean = true,
    val isCloning: Boolean = false,
    val cloneProgress: CloneProgress? = null,
    val cloneError: String? = null,
    val isCreatingRepo: Boolean = false,
    val createRepoError: String? = null,
    val projectCredentials: GitCredentials? = null,
    val defaultCredentials: GitCredentials? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class ProjectDetailViewModel(
    private val projectId: String,
    private val gitEngine: GitEngine,
    private val registry: ProjectRegistry,
    private val credentialStore: CredentialStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    private var cloneJob: Job? = null
    private var cloneCancelFlag = AtomicBoolean(false)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = withContext(ioDispatcher) {
                runCatching {
                    val project = registry.findProjectById(projectId)
                        ?: throw IllegalStateException("Проект не найден")
                    val repos = registry.listRepositories(projectId)
                    val cards = repos.map { gitEngine.getRepoSummary(it) }
                    val projCreds = credentialStore.getProjectCredentials(projectId)
                    val defCreds = credentialStore.getDefaultCredentials()
                    LoadedProjectDetail(project, cards, projCreds, defCreds)
                }
            }
            result.onSuccess { data ->
                _uiState.update {
                    it.copy(
                        project = data.project,
                        repos = data.repos,
                        projectCredentials = data.projectCreds,
                        defaultCredentials = data.defaultCreds,
                        isLoading = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Не удалось загрузить проект"
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
            _uiState.update { it.copy(cloneError = "Укажите ссылку на Git-репозиторий") }
            return
        }

        val derivedName = customName.trim().ifEmpty { deriveRepoNameFromUrl(cleanUrl) }
        cloneCancelFlag = AtomicBoolean(false)

        cloneJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCloning = true,
                    cloneError = null,
                    cloneProgress = CloneProgress("Подключение к репозиторию...", 0, 0)
                )
            }

            val creds = when {
                useAuth && token.isNotBlank() -> GitCredentials(username.trim(), token.trim())
                useAuth -> credentialStore.resolveForRepo(projectId, null)
                else -> null
            }

            val outcome = withContext(ioDispatcher) {
                runCatching {
                    val (repoId, targetDir) = registry.allocateRepositoryDir(projectId, derivedName)
                    gitEngine.cloneRepository(
                        url = cleanUrl,
                        targetDir = targetDir,
                        branch = branch.trim().ifEmpty { null },
                        credentials = creds,
                        onProgress = { progress ->
                            _uiState.update { state -> state.copy(cloneProgress = progress) }
                        },
                        isCancelled = { cloneCancelFlag.get() }
                    )
                    val entry = RepoEntry(
                        id = repoId,
                        projectId = projectId,
                        name = derivedName,
                        directoryPath = targetDir.absolutePath,
                        createdAt = System.currentTimeMillis(),
                        remoteUrl = cleanUrl
                    )
                    registry.registerRepository(entry)
                    if (useAuth && saveCredentialsForRepo && token.isNotBlank()) {
                        credentialStore.saveRepoCredentials(
                            repoId,
                            GitCredentials(username.trim(), token.trim())
                        )
                    }
                    entry.id
                }
            }

            outcome.onSuccess { repoId ->
                _uiState.update {
                    it.copy(
                        isCloning = false,
                        cloneProgress = null,
                        cloneError = null,
                        infoMessage = "Репозиторий «$derivedName» добавлен в проект"
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
                            "Клонирование отменено"
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
            _uiState.update { it.copy(createRepoError = "Введите название репозитория") }
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
                        infoMessage = "Репозиторий «$cleanName» создан"
                    )
                }
                refresh()
                onSuccess(repoId)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCreatingRepo = false,
                        createRepoError = err.localizedMessage ?: "Не удалось создать репозиторий"
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
            _uiState.update { it.copy(infoMessage = "Репозиторий удалён") }
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
            _uiState.update {
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

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private data class LoadedProjectDetail(
        val project: WorkspaceProject,
        val repos: List<RepoCardInfo>,
        val projectCreds: GitCredentials?,
        val defaultCreds: GitCredentials?
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
            credentialStore: CredentialStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProjectDetailViewModel(projectId, gitEngine, registry, credentialStore) as T
            }
        }
    }
}
