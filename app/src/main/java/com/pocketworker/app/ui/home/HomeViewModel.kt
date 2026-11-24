package com.pocketworker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.ProjectSummary
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HomeTab {
    PROJECTS,
    AGENTS
}

data class HomeUiState(
    val activeTab: HomeTab = HomeTab.PROJECTS,
    val projects: List<ProjectSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isCreatingProject: Boolean = false,
    val createProjectError: String? = null,
    val defaultCredentials: GitCredentials? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class HomeViewModel(
    private val gitEngine: GitEngine,
    private val registry: ProjectRegistry,
    private val credentialStore: CredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshProjects()
    }

    fun setActiveTab(tab: HomeTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = withContext(Dispatchers.IO) {
                val projects = registry.listProjects()
                val summaries = projects.map { project ->
                    val repos = registry.listRepositories(project.id)
                    val dirtyCount = repos.count { repo ->
                        val summary = gitEngine.getRepoSummary(repo)
                        summary.changedFilesCount + summary.untrackedCount > 0
                    }
                    ProjectSummary(
                        project = project,
                        repoCount = repos.size,
                        repoNamesPreview = repos.take(4).map { it.name },
                        dirtyRepoCount = dirtyCount
                    )
                }
                val defaultCreds = credentialStore.getDefaultCredentials()
                summaries to defaultCreds
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

    fun createProject(
        name: String,
        description: String,
        onSuccess: (String) -> Unit
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _uiState.update { it.copy(createProjectError = "Введите название проекта") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingProject = true, createProjectError = null) }
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    registry.createProject(cleanName, description)
                }
            }
            outcome.onSuccess { created ->
                _uiState.update {
                    it.copy(
                        isCreatingProject = false,
                        createProjectError = null,
                        infoMessage = "Проект «${created.name}» создан"
                    )
                }
                refreshProjects()
                onSuccess(created.id)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCreatingProject = false,
                        createProjectError = err.localizedMessage ?: "Не удалось создать проект"
                    )
                }
            }
        }
    }

    fun clearCreateProjectError() {
        _uiState.update { it.copy(createProjectError = null) }
    }

    fun updateProject(projectId: String, name: String, description: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Название проекта не может быть пустым") }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                registry.updateProject(projectId, cleanName, description)
            }
            _uiState.update { it.copy(infoMessage = "Проект обновлён") }
            refreshProjects()
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val removedRepoIds = registry.deleteProject(projectId, deleteFilesOnDisk = true)
                credentialStore.removeProjectCredentials(projectId)
                removedRepoIds.forEach { credentialStore.removeRepoCredentials(it) }
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
        fun factory(
            gitEngine: GitEngine,
            registry: ProjectRegistry,
            credentialStore: CredentialStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(gitEngine, registry, credentialStore) as T
            }
        }
    }
}
