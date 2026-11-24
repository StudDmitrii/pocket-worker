package com.pocketworker.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketworker.app.core.git.CloneProgress
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.ProjectCardInfo
import com.pocketworker.app.core.git.ProjectEntry
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class ProjectsUiState(
    val projects: List<ProjectCardInfo> = emptyList(),
    val isLoading: Boolean = true,
    val isCloning: Boolean = false,
    val cloneProgress: CloneProgress? = null,
    val isCreating: Boolean = false,
    val defaultCredentials: GitCredentials? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class ProjectsViewModel(
    private val gitEngine: GitEngine,
    private val registry: ProjectRegistry,
    private val credentialStore: CredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    private var cloneJob: Job? = null
    private var cloneCancelFlag = AtomicBoolean(false)

    init {
        refreshProjects()
    }

    fun refreshProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = withContext(Dispatchers.IO) {
                val entries = registry.listProjects()
                val cards = entries.map { gitEngine.getProjectSummary(it) }
                val defaultCreds = credentialStore.getDefaultCredentials()
                cards to defaultCreds
            }
            _uiState.update {
                it.copy(
                    projects = result.first,
                    defaultCredentials = result.second,
                    isLoading = false
                )
            }
        }
    }

    fun cloneProject(
        url: String,
        customName: String,
        branch: String,
        username: String,
        token: String,
        saveCredentialsForProject: Boolean,
        onSuccess: (String) -> Unit
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Укажите ссылку на Git-репозиторий") }
            return
        }

        val derivedName = customName.trim().ifEmpty { deriveRepoNameFromUrl(cleanUrl) }
        cloneCancelFlag = AtomicBoolean(false)

        cloneJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCloning = true,
                    cloneProgress = CloneProgress("Подготовка...", 0, 0),
                    errorMessage = null
                )
            }

            val creds = if (token.isNotBlank()) {
                GitCredentials(username.trim(), token.trim())
            } else {
                credentialStore.getDefaultCredentials()
            }

            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val (id, targetDir) = registry.allocateProjectDir(derivedName)
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
                    val entry = ProjectEntry(
                        id = id,
                        name = derivedName,
                        directoryPath = targetDir.absolutePath,
                        createdAt = System.currentTimeMillis(),
                        remoteUrl = cleanUrl
                    )
                    registry.registerProject(entry)
                    if (saveCredentialsForProject && token.isNotBlank()) {
                        credentialStore.saveProjectCredentials(id, GitCredentials(username.trim(), token.trim()))
                    }
                    entry.id
                }
            }

            outcome.onSuccess { projectId ->
                _uiState.update {
                    it.copy(
                        isCloning = false,
                        cloneProgress = null,
                        infoMessage = "Репозиторий «$derivedName» успешно клонирован"
                    )
                }
                refreshProjects()
                onSuccess(projectId)
            }.onFailure { err ->
                val cancelled = cloneCancelFlag.get()
                _uiState.update {
                    it.copy(
                        isCloning = false,
                        cloneProgress = null,
                        errorMessage = if (cancelled) {
                            "Клонирование отменено"
                        } else {
                            "Ошибка клонирования: ${err.localizedMessage ?: err.javaClass.simpleName}"
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

    fun createEmptyProject(
        projectName: String,
        initGit: Boolean,
        initialBranch: String,
        createReadmeAndCommit: Boolean,
        remoteUrl: String,
        authorName: String,
        authorEmail: String,
        onSuccess: (String) -> Unit
    ) {
        val cleanName = projectName.trim()
        if (cleanName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Введите название проекта") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, errorMessage = null) }
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val (id, targetDir) = registry.allocateProjectDir(cleanName)
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
                    val entry = ProjectEntry(
                        id = id,
                        name = cleanName,
                        directoryPath = targetDir.absolutePath,
                        createdAt = System.currentTimeMillis(),
                        remoteUrl = cleanRemote
                    )
                    registry.registerProject(entry)
                    entry.id
                }
            }

            outcome.onSuccess { projectId ->
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        infoMessage = "Проект «$cleanName» создан"
                    )
                }
                refreshProjects()
                onSuccess(projectId)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        errorMessage = "Не удалось создать проект: ${err.localizedMessage ?: err.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                registry.deleteProject(projectId, deleteFilesOnDisk = true)
                credentialStore.removeProjectCredentials(projectId)
            }
            _uiState.update { it.copy(infoMessage = "Проект удалён") }
            refreshProjects()
        }
    }

    fun saveDefaultCredentials(username: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (token.isBlank()) {
                credentialStore.clearDefaultCredentials()
            } else {
                credentialStore.saveDefaultCredentials(GitCredentials(username.trim(), token.trim()))
            }
            val updated = credentialStore.getDefaultCredentials()
            _uiState.update {
                it.copy(
                    defaultCredentials = updated,
                    infoMessage = if (token.isBlank()) "Токен по умолчанию очищен" else "Токен по умолчанию сохранён"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    companion object {
        fun deriveRepoNameFromUrl(url: String): String {
            val cleaned = url.trim().trimEnd('/')
            val lastSegment = cleaned.substringAfterLast('/').substringAfterLast(':')
            return lastSegment.removeSuffix(".git").ifBlank { "git-project" }
        }

        fun factory(
            gitEngine: GitEngine,
            registry: ProjectRegistry,
            credentialStore: CredentialStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProjectsViewModel(gitEngine, registry, credentialStore) as T
            }
        }
    }
}
