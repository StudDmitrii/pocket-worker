package com.pocketworker.app.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketworker.app.core.git.CommitDetail
import com.pocketworker.app.core.git.FileChangeType
import com.pocketworker.app.core.git.FileReadResult
import com.pocketworker.app.core.git.GitBranch
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.GitRemote
import com.pocketworker.app.core.git.GitStatusSummary
import com.pocketworker.app.core.git.GitCommit
import com.pocketworker.app.core.git.ProjectFileItem
import com.pocketworker.app.core.git.ProjectFileManager
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import com.pocketworker.app.ui.common.formatGitTransportError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class WorkspaceTab {
    FILES,
    GIT,
    HISTORY
}

data class FileEditorState(
    val relativePath: String,
    val content: String,
    val isBinary: Boolean = false,
    val isTooLarge: Boolean = false,
    val sizeBytes: Long = 0L,
    val isModifiedInEditor: Boolean = false,
    val isSaving: Boolean = false
)

data class DiffDialogState(
    val title: String,
    val diffText: String
)

data class WorkspaceUiState(
    val parentProjectName: String = "",
    val project: RepoEntry? = null,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val activeTab: WorkspaceTab = WorkspaceTab.FILES,

    // Files tab
    val currentDir: String = "",
    val files: List<ProjectFileItem> = emptyList(),
    val showHiddenGit: Boolean = false,
    val fileSearchQuery: String = "",
    val fileSearchResults: List<ProjectFileItem> = emptyList(),
    val fileEditor: FileEditorState? = null,

    // Git tab
    val gitStatus: GitStatusSummary? = null,
    val branches: List<GitBranch> = emptyList(),
    val remotes: List<GitRemote> = emptyList(),
    val repoCredentials: GitCredentials? = null,
    val hasFallbackCredentials: Boolean = false,

    // History tab
    val selectedHistoryRef: String? = null,
    val commits: List<GitCommit> = emptyList(),
    val canLoadMoreCommits: Boolean = false,
    val selectedCommitDetail: CommitDetail? = null,

    // Overlays / Messages
    val diffDialog: DiffDialogState? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class WorkspaceViewModel(
    private val projectId: String,
    private val repoId: String,
    private val gitEngine: GitEngine,
    private val fileManager: ProjectFileManager,
    private val registry: ProjectRegistry,
    private val credentialStore: CredentialStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        loadAll(resetHistoryRef = true)
    }

    fun setActiveTab(tab: WorkspaceTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun loadAll(resetHistoryRef: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val parentProject = registry.findProjectById(projectId)
                    val entry = registry.findRepository(projectId, repoId)
                        ?: throw IllegalStateException("Репозиторий не найден")
                    val root = File(entry.directoryPath)
                    val status = gitEngine.getStatus(root)
                    val branches = if (status.isGitRepo) gitEngine.getBranches(root) else emptyList()
                    val remotes = if (status.isGitRepo) gitEngine.getRemotes(root) else emptyList()
                    val statusMap = buildStatusMap(status)
                    val curState = _uiState.value
                    val currentDir = curState.currentDir
                    val files = fileManager.listDirectory(
                        projectRoot = root,
                        relativeDir = currentDir,
                        showHiddenGit = curState.showHiddenGit,
                        statusByPath = statusMap
                    )

                    val historyRef = resolveHistoryRef(
                        requested = if (resetHistoryRef) null else curState.selectedHistoryRef,
                        status = status,
                        branches = branches
                    )
                    val commits = if (status.isGitRepo) {
                        gitEngine.getCommitHistory(root, historyRef, limit = HISTORY_PAGE_SIZE, skip = 0)
                    } else {
                        emptyList()
                    }
                    val repoCreds = credentialStore.getRepoCredentials(repoId)
                    val hasFallbackCreds = credentialStore.getProjectCredentials(projectId) != null ||
                        credentialStore.getDefaultCredentials() != null

                    LoadedWorkspace(
                        parentProjectName = parentProject?.name ?: "",
                        project = entry,
                        status = status,
                        branches = branches,
                        remotes = remotes,
                        files = files,
                        historyRef = historyRef,
                        commits = commits,
                        repoCredentials = repoCreds,
                        hasFallbackCredentials = hasFallbackCreds
                    )
                }
            }

            result.onSuccess { data ->
                _uiState.update {
                    it.copy(
                        parentProjectName = data.parentProjectName,
                        project = data.project,
                        gitStatus = data.status,
                        branches = data.branches,
                        remotes = data.remotes,
                        files = data.files,
                        selectedHistoryRef = data.historyRef,
                        commits = data.commits,
                        canLoadMoreCommits = data.commits.size >= HISTORY_PAGE_SIZE,
                        repoCredentials = data.repoCredentials,
                        hasFallbackCredentials = data.hasFallbackCredentials,
                        isLoading = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Ошибка загрузки репозитория"
                    )
                }
            }
        }
    }

    // region File browser & editor

    fun openDirectory(relativeDir: String) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                val statusMap = buildStatusMap(_uiState.value.gitStatus)
                fileManager.listDirectory(
                    projectRoot = File(project.directoryPath),
                    relativeDir = relativeDir,
                    showHiddenGit = _uiState.value.showHiddenGit,
                    statusByPath = statusMap
                )
            }
            _uiState.update {
                it.copy(
                    currentDir = relativeDir,
                    files = files
                )
            }
        }
    }

    fun navigateUpDirectory(): Boolean {
        val cur = _uiState.value.currentDir
        if (cur.isEmpty()) return false
        val parent = cur.substringBeforeLast('/', "")
        openDirectory(parent)
        return true
    }

    fun toggleShowHiddenGit() {
        val next = !_uiState.value.showHiddenGit
        _uiState.update { it.copy(showHiddenGit = next) }
        openDirectory(_uiState.value.currentDir)
        if (_uiState.value.fileSearchQuery.isNotBlank()) {
            updateFileSearch(_uiState.value.fileSearchQuery)
        }
    }

    fun updateFileSearch(query: String) {
        val project = _uiState.value.project ?: return
        _uiState.update { it.copy(fileSearchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(fileSearchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                fileManager.searchFiles(
                    projectRoot = File(project.directoryPath),
                    query = query,
                    includeGitDir = _uiState.value.showHiddenGit
                )
            }
            _uiState.update { it.copy(fileSearchResults = results) }
        }
    }

    fun openFile(relativePath: String) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    fileManager.readTextFile(File(project.directoryPath), relativePath)
                }
            }
            outcome.onSuccess { res ->
                val editorState = when (res) {
                    is FileReadResult.Text -> FileEditorState(
                        relativePath = relativePath,
                        content = res.content,
                        sizeBytes = res.sizeBytes
                    )
                    is FileReadResult.Binary -> FileEditorState(
                        relativePath = relativePath,
                        content = "",
                        isBinary = true,
                        sizeBytes = res.sizeBytes
                    )
                    is FileReadResult.TooLarge -> FileEditorState(
                        relativePath = relativePath,
                        content = "",
                        isTooLarge = true,
                        sizeBytes = res.sizeBytes
                    )
                }
                _uiState.update { it.copy(fileEditor = editorState) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Не удалось открыть файл") }
            }
        }
    }

    fun updateEditorContent(newContent: String) {
        _uiState.update { state ->
            val current = state.fileEditor ?: return@update state
            state.copy(fileEditor = current.copy(content = newContent, isModifiedInEditor = true))
        }
    }

    fun saveEditorFile() {
        val project = _uiState.value.project ?: return
        val editor = _uiState.value.fileEditor ?: return
        if (editor.isBinary || editor.isTooLarge) return

        viewModelScope.launch {
            _uiState.update { it.copy(fileEditor = editor.copy(isSaving = true)) }
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    fileManager.writeTextFile(File(project.directoryPath), editor.relativePath, editor.content)
                }
            }
            outcome.onSuccess {
                _uiState.update {
                    it.copy(
                        fileEditor = editor.copy(isModifiedInEditor = false, isSaving = false),
                        infoMessage = "Файл сохранён: ${editor.relativePath}"
                    )
                }
                loadAll()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        fileEditor = editor.copy(isSaving = false),
                        errorMessage = err.localizedMessage ?: "Ошибка сохранения файла"
                    )
                }
            }
        }
    }

    fun closeFileEditor() {
        _uiState.update { it.copy(fileEditor = null) }
    }

    fun createNewFile(name: String) {
        val project = _uiState.value.project ?: return
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val relPath = joinRelPath(_uiState.value.currentDir, cleanName)
        runGitOrFileAction("Создание файла...") {
            fileManager.createFile(File(project.directoryPath), relPath, "")
            withContext(Dispatchers.Main) {
                openFile(relPath)
            }
        }
    }

    fun createNewDirectory(name: String) {
        val project = _uiState.value.project ?: return
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val relPath = joinRelPath(_uiState.value.currentDir, cleanName)
        runGitOrFileAction("Создание папки...") {
            fileManager.createDirectory(File(project.directoryPath), relPath)
        }
    }

    fun renameFileEntry(relativePath: String, newName: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Переименование...") {
            fileManager.renameEntry(File(project.directoryPath), relativePath, newName)
        }
    }

    fun deleteFileEntry(relativePath: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Удаление...") {
            fileManager.deleteEntry(File(project.directoryPath), relativePath)
        }
    }

    // endregion

    // region Git operations

    fun initGitInProject(initialBranch: String = "main") {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Инициализация Git...", successMessage = "Git инициализирован") {
            gitEngine.initGitInExistingProject(File(project.directoryPath), initialBranch)
        }
    }

    fun stageFile(relativePath: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Индексация...") {
            gitEngine.stageFile(File(project.directoryPath), relativePath)
        }
    }

    fun stageAll() {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Индексация всех изменений...") {
            gitEngine.stageAll(File(project.directoryPath))
        }
    }

    fun unstageFile(relativePath: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Снятие индексации...") {
            gitEngine.unstageFile(File(project.directoryPath), relativePath)
        }
    }

    fun unstageAll() {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Снятие индексации всех файлов...") {
            gitEngine.unstageAll(File(project.directoryPath))
        }
    }

    fun discardFileChanges(relativePath: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Откат изменений...", successMessage = "Изменения в $relativePath отменены") {
            gitEngine.discardFileChanges(File(project.directoryPath), relativePath)
        }
    }

    fun commit(message: String, amend: Boolean, pushAfter: Boolean) {
        val project = _uiState.value.project ?: return
        val status = _uiState.value.gitStatus ?: return
        runGitOrFileAction(
            busyText = if (pushAfter) "Коммит и отправка..." else "Создание коммита...",
            resetHistoryRef = false
        ) {
            val repoDir = File(project.directoryPath)
            val commit = gitEngine.commit(
                repoDir = repoDir,
                message = message,
                authorName = status.authorName,
                authorEmail = status.authorEmail,
                amend = amend
            )
            if (pushAfter) {
                val creds = resolveEffectiveCredentials()
                val pushMsg = gitEngine.push(repoDir, credentials = creds)
                "Коммит ${commit.shortId} создан. $pushMsg"
            } else {
                "Создан коммит ${commit.shortId}: ${commit.shortMessage}"
            }
        }
    }

    fun createBranch(branchName: String, startPoint: String? = null, checkout: Boolean = true) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Создание ветки...", resetHistoryRef = checkout) {
            gitEngine.createBranch(File(project.directoryPath), branchName, startPoint, checkout)
            if (checkout) "Переключено на новую ветку «$branchName»" else "Ветка «$branchName» создана"
        }
    }

    fun checkoutBranch(branchNameOrRef: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Переключение ветки...", resetHistoryRef = true) {
            gitEngine.checkoutBranch(File(project.directoryPath), branchNameOrRef)
            "Текущая ветка изменена"
        }
    }

    fun renameBranch(oldName: String, newName: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Переименование ветки...", resetHistoryRef = true) {
            gitEngine.renameBranch(File(project.directoryPath), oldName, newName)
            "Ветка «$oldName» переименована в «$newName»"
        }
    }

    fun deleteBranch(branchName: String, force: Boolean = false) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Удаление ветки...") {
            gitEngine.deleteBranch(File(project.directoryPath), branchName, force)
            "Ветка «$branchName» удалена"
        }
    }

    fun mergeBranch(sourceBranch: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Слияние ветки «$sourceBranch»...") {
            val result = gitEngine.mergeBranch(File(project.directoryPath), sourceBranch)
            if (result.isSuccessful) {
                "Merge ($sourceBranch): ${result.statusName}"
            } else {
                val conflictNote = if (result.conflicts.isNotEmpty()) {
                    " Конфликты: ${result.conflicts.joinToString(", ")}"
                } else ""
                throw IllegalStateException("Слияние завершилось со статусом ${result.statusName}.$conflictNote")
            }
        }
    }

    fun addRemote(name: String, url: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Добавление remote...") {
            gitEngine.addRemote(File(project.directoryPath), name, url)
            if (name.trim() == "origin") {
                registry.updateRepositoryRemoteUrl(projectId, repoId, url.trim())
            }
            "Remote «$name» добавлен"
        }
    }

    fun updateRemoteUrl(name: String, newUrl: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Обновление remote...") {
            gitEngine.updateRemoteUrl(File(project.directoryPath), name, newUrl)
            if (name == "origin") {
                registry.updateRepositoryRemoteUrl(projectId, repoId, newUrl.trim())
            }
            "URL для «$name» обновлён"
        }
    }

    fun removeRemote(name: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Удаление remote...") {
            gitEngine.removeRemote(File(project.directoryPath), name)
            if (name == "origin") {
                registry.updateRepositoryRemoteUrl(projectId, repoId, null)
            }
            "Remote «$name» удалён"
        }
    }

    fun fetch(remoteName: String = "origin") {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Fetch из $remoteName...") {
            gitEngine.fetch(File(project.directoryPath), remoteName, resolveEffectiveCredentials())
        }
    }

    fun pull(remoteName: String = "origin") {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Pull из $remoteName...") {
            val res = gitEngine.pull(File(project.directoryPath), remoteName, resolveEffectiveCredentials())
            if (!res.isSuccessful) {
                throw IllegalStateException(res.summary)
            }
            res.summary
        }
    }

    fun push(remoteName: String = "origin", branchName: String? = null) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Push в $remoteName...") {
            gitEngine.push(File(project.directoryPath), remoteName, branchName, resolveEffectiveCredentials())
        }
    }

    fun saveRepoCredentials(username: String, token: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (token.isBlank()) {
                    credentialStore.removeRepoCredentials(repoId)
                } else {
                    credentialStore.saveRepoCredentials(
                        repoId,
                        GitCredentials(username.trim(), token.trim())
                    )
                }
            }
            _uiState.update {
                it.copy(
                    repoCredentials = if (token.isBlank()) null else GitCredentials(username.trim(), token.trim()),
                    infoMessage = if (token.isBlank()) {
                        "Токен репозитория удалён (будет использоваться токен проекта или по умолчанию)"
                    } else {
                        "Токен доступа репозитория сохранён"
                    }
                )
            }
        }
    }

    fun saveAuthorConfig(name: String, email: String, saveGlobally: Boolean) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction("Сохранение настроек автора...") {
            gitEngine.setAuthorConfig(File(project.directoryPath), name, email, saveGlobally)
            "Автор коммитов обновлён"
        }
    }

    fun openWorkingDiff(relativePath: String, staged: Boolean) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    gitEngine.getWorkingDiff(File(project.directoryPath), relativePath, staged)
                }
            }
            outcome.onSuccess { diff ->
                _uiState.update {
                    it.copy(
                        diffDialog = DiffDialogState(
                            title = if (staged) "Staged: $relativePath" else "Diff: $relativePath",
                            diffText = diff.ifBlank { "Нет текстовых отличий" }
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Не удалось построить diff") }
            }
        }
    }

    fun closeDiffDialog() {
        _uiState.update { it.copy(diffDialog = null) }
    }

    // endregion

    // region History

    fun selectHistoryBranch(branchRef: String) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Загрузка истории...") }
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    gitEngine.getCommitHistory(
                        repoDir = File(project.directoryPath),
                        branchRef = branchRef,
                        limit = HISTORY_PAGE_SIZE,
                        skip = 0
                    )
                }
            }
            outcome.onSuccess { commits ->
                _uiState.update {
                    it.copy(
                        selectedHistoryRef = branchRef,
                        commits = commits,
                        canLoadMoreCommits = commits.size >= HISTORY_PAGE_SIZE,
                        isBusy = false,
                        busyMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null,
                        errorMessage = err.localizedMessage ?: "Ошибка загрузки истории"
                    )
                }
            }
        }
    }

    fun loadMoreCommits() {
        val project = _uiState.value.project ?: return
        val currentCommits = _uiState.value.commits
        val branchRef = _uiState.value.selectedHistoryRef
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    gitEngine.getCommitHistory(
                        repoDir = File(project.directoryPath),
                        branchRef = branchRef,
                        limit = HISTORY_PAGE_SIZE,
                        skip = currentCommits.size
                    )
                }
            }
            outcome.onSuccess { more ->
                _uiState.update {
                    it.copy(
                        commits = currentCommits + more,
                        canLoadMoreCommits = more.size >= HISTORY_PAGE_SIZE
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun openCommitDetail(commitId: String) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    gitEngine.getCommitDetail(File(project.directoryPath), commitId)
                }
            }
            outcome.onSuccess { detail ->
                _uiState.update { it.copy(selectedCommitDetail = detail) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Не удалось загрузить коммит") }
            }
        }
    }

    fun openCommitFileDiff(commitId: String, relativePath: String) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    gitEngine.getCommitFileDiff(File(project.directoryPath), commitId, relativePath)
                }
            }
            outcome.onSuccess { diff ->
                _uiState.update {
                    it.copy(
                        diffDialog = DiffDialogState(
                            title = "${commitId.take(7)} • $relativePath",
                            diffText = diff.ifBlank { "Нет текстовых отличий" }
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Ошибка diff") }
            }
        }
    }

    fun closeCommitDetail() {
        _uiState.update { it.copy(selectedCommitDetail = null) }
    }

    // endregion

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private fun resolveEffectiveCredentials(): GitCredentials? {
        return credentialStore.resolveForRepo(projectId, repoId)
    }

    private fun runGitOrFileAction(
        busyText: String,
        successMessage: String? = null,
        resetHistoryRef: Boolean = false,
        block: suspend () -> Any?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = busyText) }
            val outcome = withContext(Dispatchers.IO) {
                runCatching { block() }
            }
            outcome.onSuccess { res ->
                val messageFromBlock = res as? String
                val info = successMessage ?: messageFromBlock
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null,
                        infoMessage = info
                    )
                }
                loadAll(resetHistoryRef = resetHistoryRef)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null,
                        errorMessage = formatGitTransportError(err)
                    )
                }
            }
        }
    }

    private fun resolveHistoryRef(
        requested: String?,
        status: GitStatusSummary,
        branches: List<GitBranch>
    ): String? {
        if (!status.isGitRepo || status.isUnborn) return null
        if (!requested.isNullOrBlank() && branches.any { it.fullName == requested }) {
            return requested
        }
        return branches.firstOrNull { it.isCurrent }?.fullName ?: "HEAD"
    }

    private fun buildStatusMap(status: GitStatusSummary?): Map<String, FileChangeType> {
        if (status == null || !status.isGitRepo) return emptyMap()
        val map = mutableMapOf<String, FileChangeType>()
        status.stagedChanges.forEach { map[it.path] = it.changeType }
        status.unstagedChanges.forEach { map[it.path] = it.changeType }
        return map
    }

    private fun joinRelPath(base: String, name: String): String {
        val clean = name.trim().trimStart('/')
        return if (base.isBlank()) clean else "$base/$clean"
    }

    private data class LoadedWorkspace(
        val parentProjectName: String,
        val project: RepoEntry,
        val status: GitStatusSummary,
        val branches: List<GitBranch>,
        val remotes: List<GitRemote>,
        val files: List<ProjectFileItem>,
        val historyRef: String?,
        val commits: List<GitCommit>,
        val repoCredentials: GitCredentials?,
        val hasFallbackCredentials: Boolean
    )

    companion object {
        const val HISTORY_PAGE_SIZE = 50

        fun factory(
            projectId: String,
            repoId: String,
            gitEngine: GitEngine,
            fileManager: ProjectFileManager,
            registry: ProjectRegistry,
            credentialStore: CredentialStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WorkspaceViewModel(projectId, repoId, gitEngine, fileManager, registry, credentialStore) as T
            }
        }
    }
}
