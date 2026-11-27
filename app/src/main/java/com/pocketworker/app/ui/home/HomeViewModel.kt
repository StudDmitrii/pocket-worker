package com.pocketworker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.ProjectSummary
import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val projects: List<ProjectSummary> = emptyList(),
    val newlyAddedProjectIds: Set<String> = emptySet(),
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
            val pendingNew = registry.getPendingNewProjectIds()
            _uiState.update {
                it.copy(
                    projects = result.first,
                    newlyAddedProjectIds = pendingNew,
                    defaultCredentials = result.second,
                    isLoading = false
                )
            }
        }
    }

    fun consumeNewlyAddedProject(projectId: String) {
        registry.markProjectSeen(projectId)
        _uiState.update {
            it.copy(newlyAddedProjectIds = it.newlyAddedProjectIds - projectId)
        }
    }

    fun createProject(
        name: String,
        description: String,
        onSuccess: (String) -> Unit
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _uiState.update { it.copy(createProjectError = AppLocale.current.homeErrEnterProjectName) }
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
                        infoMessage = AppLocale.current.homeInfoProjectCreated(created.name)
                    )
                }
                refreshProjects()
                onSuccess(created.id)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCreatingProject = false,
                        createProjectError = err.localizedMessage ?: AppLocale.current.homeErrCreateProject
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
            _uiState.update { it.copy(errorMessage = AppLocale.current.homeErrEmptyProjectName) }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                registry.updateProject(projectId, cleanName, description)
            }
            _uiState.update { it.copy(infoMessage = AppLocale.current.homeInfoProjectUpdated) }
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
            _uiState.update { it.copy(infoMessage = AppLocale.current.homeInfoProjectDeleted) }
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
            val s = AppLocale.current
            _uiState.update {
                it.copy(
                    defaultCredentials = updated,
                    infoMessage = if (token.isBlank()) s.homeInfoDefaultTokenCleared else s.homeInfoDefaultTokenSaved
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
