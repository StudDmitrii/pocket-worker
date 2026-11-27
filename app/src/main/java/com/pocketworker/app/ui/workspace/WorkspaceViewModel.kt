package com.pocketworker.app.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketworker.app.core.git.CommitDetail
import com.pocketworker.app.core.git.FileChangeType
import com.pocketworker.app.core.git.FileReadResult
import com.pocketworker.app.core.git.GitBranch
import com.pocketworker.app.core.git.GitCommit
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.GitOAuthManager
import com.pocketworker.app.core.git.GitRemote
import com.pocketworker.app.core.git.GitStashEntry
import com.pocketworker.app.core.git.GitStatusSummary
import com.pocketworker.app.core.git.GitTag
import com.pocketworker.app.core.git.ProjectFileItem
import com.pocketworker.app.core.git.ProjectFileManager
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.mcp.FileSyntaxValidator
import com.pocketworker.app.core.mcp.NoOpSyntaxCheckEngine
import com.pocketworker.app.core.mcp.SyntaxCheckEngine
import com.pocketworker.app.core.mcp.walkProjectFiles
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import com.pocketworker.app.ui.common.formatGitTransportError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val diskContent: String = content,
    val isBinary: Boolean = false,
    val isTooLarge: Boolean = false,
    val sizeBytes: Long = 0L,
    val isModifiedInEditor: Boolean = false,
    val isSaving: Boolean = false,
    val isGitRepo: Boolean = false,
    val isGitIgnored: Boolean = false,
    val headContent: String? = null
)

data class DiffDialogState(
    val title: String,
    val diffText: String
)

data class SyntaxIssueUiItem(
    val line: Int,
    val column: Int,
    val severity: String,
    val message: String,
    val snippet: String? = null
)

data class SyntaxFileReportUiItem(
    val relativePath: String,
    val languageName: String,
    val engine: String,
    val errorCount: Int,
    val warningCount: Int,
    val issues: List<SyntaxIssueUiItem>
)

data class SyntaxCheckDialogState(
    val scopeLabel: String,
    val singleTargetPath: String? = null,
    val isRunning: Boolean = true,
    val checkedFilesCount: Int = 0,
    val skippedFilesCount: Int = 0,
    val totalErrors: Int = 0,
    val totalWarnings: Int = 0,
    val fileReportsWithIssues: List<SyntaxFileReportUiItem> = emptyList(),
    val errorMessage: String? = null
)

data class GitOutputLogEntry(
    val timestampMillis: Long,
    val command: String,
    val output: String,
    val isError: Boolean
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
    val isAutoFetching: Boolean = false,
    val branches: List<GitBranch> = emptyList(),
    val remotes: List<GitRemote> = emptyList(),
    val stashes: List<GitStashEntry> = emptyList(),
    val tags: List<GitTag> = emptyList(),
    val viewChangesAsTree: Boolean = false,
    val gitOutputLogs: List<GitOutputLogEntry> = emptyList(),
    val repoCredentials: GitCredentials? = null,
    val hasFallbackCredentials: Boolean = false,

    // History tab
    val selectedHistoryRef: String? = null,
    val commits: List<GitCommit> = emptyList(),
    val canLoadMoreCommits: Boolean = false,
    val selectedCommitDetail: CommitDetail? = null,

    // Overlays / Messages
    val diffDialog: DiffDialogState? = null,
    val syntaxCheckDialog: SyntaxCheckDialogState? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class WorkspaceViewModel(
    private val projectId: String,
    private val repoId: String,
    private val gitEngine: GitEngine,
    private val fileManager: ProjectFileManager,
    private val registry: ProjectRegistry,
    private val credentialStore: CredentialStore,
    private val syntaxCheckEngine: SyntaxCheckEngine = NoOpSyntaxCheckEngine,
    private val oauthManager: GitOAuthManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var autoFetchJob: Job? = null
    private var syntaxCheckJob: Job? = null
    private var lastAutoFetchMillis: Long = 0L
    private var lastAutoFetchBranch: String? = null

    init {
        loadAll(resetHistoryRef = true)
    }

    fun setActiveTab(tab: WorkspaceTab) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == WorkspaceTab.GIT) {
            autoFetchCurrentBranch()
        }
    }

    /**
     * Silently fetches only the currently checked-out branch in the background when opening
     * the Git tab (VS Code style) so the user immediately sees remote ahead/behind counts.
     */
    fun autoFetchCurrentBranch(force: Boolean = false) {
        val cur = _uiState.value
        val repoEntry = cur.project ?: return
        val status = cur.gitStatus
        if (status != null && (!status.isGitRepo || status.isDetachedHead || status.isUnborn)) return
        if (cur.remotes.isEmpty() && status != null) return
        if (cur.isBusy || cur.isAutoFetching) return
        if (autoFetchJob?.isActive == true) return

        val branchKey = status?.currentBranch
        val now = System.currentTimeMillis()
        if (!force && branchKey != null && branchKey == lastAutoFetchBranch &&
            (now - lastAutoFetchMillis) < AUTO_FETCH_THROTTLE_MS
        ) {
            return
        }

        autoFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isAutoFetching = true) }
            try {
                val root = File(repoEntry.directoryPath)
                val fetched = withContext(ioDispatcher) {
                    runCatching {
                        gitEngine.fetchCurrentBranch(
                            repoDir = root,
                            credentials = resolveEffectiveCredentials()
                        )
                    }.getOrDefault(false)
                }
                if (fetched) {
                    val refreshed = withContext(ioDispatcher) {
                        runCatching {
                            val parentProject = registry.findProjectById(projectId)
                            val rawStatus = gitEngine.getStatus(root)
                            val updatedStatus = if (rawStatus.isGitRepo) {
                                val (effName, effEmail) = GitEngine.resolveEffectiveAuthor(
                                    gitEngine,
                                    root,
                                    parentProject
                                )
                                rawStatus.copy(authorName = effName, authorEmail = effEmail)
                            } else {
                                rawStatus
                            }
                            val branches = if (updatedStatus.isGitRepo) gitEngine.getBranches(root) else emptyList()
                            val historyRef = resolveHistoryRef(
                                requested = _uiState.value.selectedHistoryRef,
                                status = updatedStatus,
                                branches = branches
                            )
                            val commits = if (updatedStatus.isGitRepo) {
                                gitEngine.getCommitHistory(root, historyRef, limit = HISTORY_PAGE_SIZE, skip = 0)
                            } else {
                                emptyList()
                            }
                            Triple(updatedStatus, branches, commits)
                        }.getOrNull()
                    }
                    if (refreshed != null) {
                        lastAutoFetchMillis = System.currentTimeMillis()
                        lastAutoFetchBranch = refreshed.first.currentBranch
                        _uiState.update {
                            it.copy(
                                gitStatus = refreshed.first,
                                branches = refreshed.second,
                                commits = refreshed.third
                            )
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isAutoFetching = false) }
            }
        }
    }

    fun loadAll(resetHistoryRef: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = withContext(ioDispatcher) {
                runCatching {
                    val parentProject = registry.findProjectById(projectId)
                    val entry = registry.findRepository(projectId, repoId)
                        ?: throw IllegalStateException(AppLocale.current.workspaceErrRepoNotFound)
                    val root = File(entry.directoryPath)
                    val rawStatus = gitEngine.getStatus(root)
                    val status = if (rawStatus.isGitRepo) {
                        val (effName, effEmail) = GitEngine.resolveEffectiveAuthor(gitEngine, root, parentProject)
                        rawStatus.copy(authorName = effName, authorEmail = effEmail)
                    } else {
                        rawStatus
                    }
                    val branches = if (status.isGitRepo) gitEngine.getBranches(root) else emptyList()
                    val remotes = if (status.isGitRepo) gitEngine.getRemotes(root) else emptyList()
                    val stashes = if (status.isGitRepo) gitEngine.getStashes(root) else emptyList()
                    val tags = if (status.isGitRepo) gitEngine.getTags(root) else emptyList()
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
                    val hasFallbackCreds = credentialStore.getProjectOAuthAccountId(projectId) != null ||
                        credentialStore.getProjectCredentials(projectId) != null ||
                        credentialStore.getOAuthAccounts().isNotEmpty() ||
                        credentialStore.getDefaultCredentials() != null

                    LoadedWorkspace(
                        parentProjectName = parentProject?.name ?: "",
                        project = entry,
                        status = status,
                        branches = branches,
                        remotes = remotes,
                        stashes = stashes,
                        tags = tags,
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
                        stashes = data.stashes,
                        tags = data.tags,
                        files = data.files,
                        selectedHistoryRef = data.historyRef,
                        commits = data.commits,
                        canLoadMoreCommits = data.commits.size >= HISTORY_PAGE_SIZE,
                        repoCredentials = data.repoCredentials,
                        hasFallbackCredentials = data.hasFallbackCredentials,
                        isLoading = false
                    )
                }
                if (_uiState.value.fileEditor != null) {
                    reloadOpenFileFromDisk()
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: AppLocale.current.workspaceErrLoadRepo
                    )
                }
            }
        }
    }

    // region File browser & editor

    fun openDirectory(relativeDir: String) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val files = withContext(ioDispatcher) {
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
            val results = withContext(ioDispatcher) {
                fileManager.searchFiles(
                    projectRoot = File(project.directoryPath),
                    query = query,
                    includeGitDir = _uiState.value.showHiddenGit
                )
            }
            _uiState.update { it.copy(fileSearchResults = results) }
        }
    }

    private fun resolveEditorGitBaseline(rootDir: File, relativePath: String): Triple<Boolean, Boolean, String?> {
        val normalized = relativePath.trim().replace('\\', '/').trimStart('/')
        if (normalized == ".git" || normalized.startsWith(".git/")) {
            return Triple(false, true, null)
        }
        val isGit = gitEngine.isGitRepository(rootDir)
        if (!isGit) return Triple(false, false, null)
        val headContent = gitEngine.getHeadFileContent(rootDir, normalized)
        val status = _uiState.value.gitStatus
        val isIgnored = if (headContent != null || status == null) {
            false
        } else {
            val knownPaths = buildSet {
                status.stagedChanges.forEach { add(it.path) }
                status.unstagedChanges.forEach { add(it.path) }
            }
            normalized !in knownPaths
        }
        return Triple(true, isIgnored, headContent)
    }

    fun openFile(relativePath: String) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val outcome = withContext(ioDispatcher) {
                runCatching {
                    val rootDir = File(project.directoryPath)
                    val readResult = fileManager.readTextFile(rootDir, relativePath)
                    val baseline = resolveEditorGitBaseline(rootDir, relativePath)
                    readResult to baseline
                }
            }
            outcome.onSuccess { (res, baseline) ->
                val (isGit, isIgnored, headContent) = baseline
                val editorState = when (res) {
                    is FileReadResult.Text -> FileEditorState(
                        relativePath = relativePath,
                        content = res.content,
                        diskContent = res.content,
                        sizeBytes = res.sizeBytes,
                        isGitRepo = isGit,
                        isGitIgnored = isIgnored,
                        headContent = headContent
                    )
                    is FileReadResult.Binary -> FileEditorState(
                        relativePath = relativePath,
                        content = "",
                        diskContent = "",
                        isBinary = true,
                        sizeBytes = res.sizeBytes,
                        isGitRepo = isGit,
                        isGitIgnored = isIgnored,
                        headContent = headContent
                    )
                    is FileReadResult.TooLarge -> FileEditorState(
                        relativePath = relativePath,
                        content = "",
                        diskContent = "",
                        isTooLarge = true,
                        sizeBytes = res.sizeBytes,
                        isGitRepo = isGit,
                        isGitIgnored = isIgnored,
                        headContent = headContent
                    )
                }
                _uiState.update { it.copy(fileEditor = editorState) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage ?: AppLocale.current.workspaceErrOpenFile) }
            }
        }
    }

    /**
     * Re-reads the currently open file from disk (e.g. after the AI agent edited or rolled it back)
     * so the viewer/editor displays updated content immediately without closing and reopening the file.
     */
    fun reloadOpenFileFromDisk() {
        val project = _uiState.value.project ?: return
        val openEditor = _uiState.value.fileEditor ?: return
        if (openEditor.isSaving) return
        val targetPath = openEditor.relativePath

        viewModelScope.launch {
            val outcome = withContext(ioDispatcher) {
                runCatching {
                    val rootDir = File(project.directoryPath)
                    val diskFile = fileManager.resolveSafe(rootDir, targetPath)
                    if (!diskFile.exists() || !diskFile.isFile) {
                        null to Triple(false, false, null as String?)
                    } else {
                        val readRes = fileManager.readTextFile(rootDir, targetPath)
                        val baseline = resolveEditorGitBaseline(rootDir, targetPath)
                        readRes to baseline
                    }
                }
            }
            outcome.onSuccess { (readResult, baseline) ->
                val (isGit, isIgnored, headContent) = baseline
                _uiState.update { state ->
                    val current = state.fileEditor ?: return@update state
                    if (current.relativePath != targetPath || current.isSaving) return@update state
                    when (readResult) {
                        null -> state.copy(
                            fileEditor = null,
                            infoMessage = AppLocale.current.workspaceInfoFileDeletedExternally(targetPath)
                        )
                        is FileReadResult.Text -> {
                            val diskChanged = readResult.content != current.diskContent
                            val needsSync = (!current.isModifiedInEditor &&
                                (readResult.content != current.content || current.isBinary || current.isTooLarge)) ||
                                (current.isModifiedInEditor && diskChanged)
                            if (needsSync) {
                                state.copy(
                                    fileEditor = current.copy(
                                        content = readResult.content,
                                        diskContent = readResult.content,
                                        isBinary = false,
                                        isTooLarge = false,
                                        sizeBytes = readResult.sizeBytes,
                                        isModifiedInEditor = false,
                                        isGitRepo = isGit,
                                        isGitIgnored = isIgnored,
                                        headContent = headContent
                                    )
                                )
                            } else if (current.headContent != headContent ||
                                current.isGitRepo != isGit ||
                                current.isGitIgnored != isIgnored
                            ) {
                                state.copy(
                                    fileEditor = current.copy(
                                        isGitRepo = isGit,
                                        isGitIgnored = isIgnored,
                                        headContent = headContent
                                    )
                                )
                            } else {
                                state
                            }
                        }
                        is FileReadResult.Binary -> state.copy(
                            fileEditor = current.copy(
                                content = "",
                                diskContent = "",
                                isBinary = true,
                                isTooLarge = false,
                                sizeBytes = readResult.sizeBytes,
                                isModifiedInEditor = false,
                                isGitRepo = isGit,
                                isGitIgnored = isIgnored,
                                headContent = headContent
                            )
                        )
                        is FileReadResult.TooLarge -> state.copy(
                            fileEditor = current.copy(
                                content = "",
                                diskContent = "",
                                isBinary = false,
                                isTooLarge = true,
                                sizeBytes = readResult.sizeBytes,
                                isModifiedInEditor = false,
                                isGitRepo = isGit,
                                isGitIgnored = isIgnored,
                                headContent = headContent
                            )
                        )
                    }
                }
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
            val outcome = withContext(ioDispatcher) {
                runCatching {
                    fileManager.writeTextFile(File(project.directoryPath), editor.relativePath, editor.content)
                }
            }
            outcome.onSuccess {
                _uiState.update {
                    it.copy(
                        fileEditor = editor.copy(
                            diskContent = editor.content,
                            isModifiedInEditor = false,
                            isSaving = false
                        ),
                        infoMessage = AppLocale.current.workspaceInfoFileSaved(editor.relativePath)
                    )
                }
                loadAll()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        fileEditor = editor.copy(isSaving = false),
                        errorMessage = err.localizedMessage ?: AppLocale.current.workspaceErrSaveFile
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
        runGitOrFileAction(AppLocale.current.workspaceBusyCreateFile) {
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
        runGitOrFileAction(AppLocale.current.workspaceBusyCreateFolder) {
            fileManager.createDirectory(File(project.directoryPath), relPath)
        }
    }

    fun renameFileEntry(relativePath: String, newName: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction(AppLocale.current.workspaceBusyRename) {
            fileManager.renameEntry(File(project.directoryPath), relativePath, newName)
        }
    }

    fun deleteFileEntry(relativePath: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction(AppLocale.current.workspaceBusyDelete) {
            fileManager.deleteEntry(File(project.directoryPath), relativePath)
        }
    }

    // endregion

    // region Git operations

    fun toggleViewChangesAsTree() {
        _uiState.update { it.copy(viewChangesAsTree = !it.viewChangesAsTree) }
    }

    fun clearGitOutputLogs() {
        _uiState.update { it.copy(gitOutputLogs = emptyList()) }
    }

    fun initGitInProject(initialBranch: String = "main") {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyInitGit,
            successMessage = s.workspaceInfoGitInitialized,
            logCommand = "git init -b $initialBranch"
        ) {
            gitEngine.initGitInExistingProject(File(project.directoryPath), initialBranch)
        }
    }

    fun stageFile(relativePath: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction(AppLocale.current.workspaceBusyStageFile, logCommand = "git add $relativePath") {
            gitEngine.stageFile(File(project.directoryPath), relativePath)
        }
    }

    fun stageAll() {
        val project = _uiState.value.project ?: return
        runGitOrFileAction(AppLocale.current.workspaceBusyStageAll, logCommand = "git add -A") {
            gitEngine.stageAll(File(project.directoryPath))
        }
    }

    fun unstageFile(relativePath: String) {
        val project = _uiState.value.project ?: return
        runGitOrFileAction(AppLocale.current.workspaceBusyUnstageFile, logCommand = "git reset -- $relativePath") {
            gitEngine.unstageFile(File(project.directoryPath), relativePath)
        }
    }

    fun unstageAll() {
        val project = _uiState.value.project ?: return
        runGitOrFileAction(AppLocale.current.workspaceBusyUnstageAll, logCommand = "git reset") {
            gitEngine.unstageAll(File(project.directoryPath))
        }
    }

    fun discardFileChanges(relativePath: String) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyDiscardFile,
            successMessage = s.workspaceInfoDiscardFileDone(relativePath),
            logCommand = "git checkout -- $relativePath"
        ) {
            gitEngine.discardFileChanges(File(project.directoryPath), relativePath)
        }
    }

    fun discardAllChanges() {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyDiscardAll,
            successMessage = s.workspaceInfoDiscardAllDone,
            logCommand = "git reset --hard && clean untracked"
        ) {
            gitEngine.discardAllChanges(File(project.directoryPath))
        }
    }

    fun commit(message: String, amend: Boolean, pushAfter: Boolean, stageAllFirst: Boolean = false) {
        val project = _uiState.value.project ?: return
        val status = _uiState.value.gitStatus ?: return
        val s = AppLocale.current
        val cmdLabel = buildString {
            if (stageAllFirst) append("git add -A && ")
            append("git commit")
            if (amend) append(" --amend")
            if (pushAfter) append(" && git push")
        }
        runGitOrFileAction(
            busyText = if (pushAfter) s.workspaceBusyCommitAndPush else s.workspaceBusyCommit,
            resetHistoryRef = false,
            logCommand = cmdLabel
        ) {
            val repoDir = File(project.directoryPath)
            if (stageAllFirst) {
                gitEngine.stageAll(repoDir)
            }
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
                s.workspaceInfoCommitAndPushDone(commit.shortId, pushMsg)
            } else {
                s.workspaceInfoCommitDone(commit.shortId, commit.shortMessage)
            }
        }
    }

    fun createBranch(branchName: String, startPoint: String? = null, checkout: Boolean = true) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyCreateBranch,
            resetHistoryRef = checkout,
            logCommand = if (checkout) "git checkout -b $branchName" else "git branch $branchName"
        ) {
            gitEngine.createBranch(File(project.directoryPath), branchName, startPoint, checkout)
            if (checkout) {
                s.workspaceInfoBranchCreatedAndCheckedOut(branchName)
            } else {
                s.workspaceInfoBranchCreated(branchName)
            }
        }
    }

    fun checkoutBranch(branchNameOrRef: String) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyCheckoutBranch,
            resetHistoryRef = true,
            logCommand = "git checkout $branchNameOrRef"
        ) {
            gitEngine.checkoutBranch(File(project.directoryPath), branchNameOrRef)
            s.workspaceInfoBranchSwitched
        }
    }

    fun renameBranch(oldName: String, newName: String) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyRenameBranch,
            resetHistoryRef = true,
            logCommand = "git branch -m $oldName $newName"
        ) {
            gitEngine.renameBranch(File(project.directoryPath), oldName, newName)
            s.workspaceInfoBranchRenamed(oldName, newName)
        }
    }

    fun deleteBranch(branchName: String, force: Boolean = false) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyDeleteBranch,
            logCommand = "git branch ${if (force) "-D" else "-d"} $branchName"
        ) {
            gitEngine.deleteBranch(File(project.directoryPath), branchName, force)
            s.workspaceInfoBranchDeleted(branchName)
        }
    }

    fun mergeBranch(sourceBranch: String) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyMergeBranch(sourceBranch),
            logCommand = "git merge $sourceBranch"
        ) {
            val result = gitEngine.mergeBranch(File(project.directoryPath), sourceBranch)
            if (result.isSuccessful) {
                "Merge ($sourceBranch): ${result.statusName}"
            } else {
                val conflictNote = if (result.conflicts.isNotEmpty()) {
                    s.workspaceMergeConflictsNote(result.conflicts.joinToString(", "))
                } else ""
                throw IllegalStateException(s.workspaceMergeFailedStatus(result.statusName, conflictNote))
            }
        }
    }

    fun addRemote(name: String, url: String) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyAddRemote,
            logCommand = "git remote add $name $url"
        ) {
            gitEngine.addRemote(File(project.directoryPath), name, url)
            if (name.trim() == "origin") {
                registry.updateRepositoryRemoteUrl(projectId, repoId, url.trim())
            }
            s.workspaceInfoRemoteAdded(name)
        }
    }

    fun updateRemoteUrl(name: String, newUrl: String) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyUpdateRemote,
            logCommand = "git remote set-url $name $newUrl"
        ) {
            gitEngine.updateRemoteUrl(File(project.directoryPath), name, newUrl)
            if (name == "origin") {
                registry.updateRepositoryRemoteUrl(projectId, repoId, newUrl.trim())
            }
            s.workspaceInfoRemoteUpdated(name)
        }
    }

    fun removeRemote(name: String) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyRemoveRemote,
            logCommand = "git remote remove $name"
        ) {
            gitEngine.removeRemote(File(project.directoryPath), name)
            if (name == "origin") {
                registry.updateRepositoryRemoteUrl(projectId, repoId, null)
            }
            s.workspaceInfoRemoteRemoved(name)
        }
    }

    fun fetch(remoteName: String = "origin") {
        val project = _uiState.value.project ?: return
        runGitOrFileAction(
            busyText = AppLocale.current.workspaceBusyFetch(remoteName),
            logCommand = "git fetch $remoteName"
        ) {
            gitEngine.fetch(File(project.directoryPath), remoteName, resolveEffectiveCredentials(remoteName))
        }
    }

    fun pull(remoteName: String = "origin", rebase: Boolean = false) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = if (rebase) s.workspaceBusyPullRebase(remoteName) else s.workspaceBusyPull(remoteName),
            logCommand = if (rebase) "git pull --rebase $remoteName" else "git pull $remoteName"
        ) {
            val res = gitEngine.pull(
                repoDir = File(project.directoryPath),
                remoteName = remoteName,
                credentials = resolveEffectiveCredentials(remoteName),
                rebase = rebase
            )
            if (!res.isSuccessful) {
                throw IllegalStateException(res.summary)
            }
            res.summary
        }
    }

    fun push(remoteName: String = "origin", branchName: String? = null, force: Boolean = false) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = if (force) s.workspaceBusyForcePush(remoteName) else s.workspaceBusyPush(remoteName),
            logCommand = if (force) "git push --force $remoteName" else "git push $remoteName"
        ) {
            gitEngine.push(
                repoDir = File(project.directoryPath),
                remoteName = remoteName,
                branchName = branchName,
                credentials = resolveEffectiveCredentials(remoteName),
                force = force
            )
        }
    }

    fun syncRemote(remoteName: String = "origin") {
        val project = _uiState.value.project ?: return
        runGitOrFileAction(
            busyText = AppLocale.current.workspaceBusySync(remoteName),
            logCommand = "git pull $remoteName && git push $remoteName"
        ) {
            val repoDir = File(project.directoryPath)
            val creds = resolveEffectiveCredentials(remoteName)
            val pullRes = gitEngine.pull(repoDir, remoteName, creds)
            if (!pullRes.isSuccessful) {
                throw IllegalStateException(pullRes.summary)
            }
            val status = gitEngine.getStatus(repoDir)
            if (!status.isUnborn) {
                val pushMsg = gitEngine.push(repoDir, remoteName, credentials = creds)
                "${pullRes.summary}. $pushMsg"
            } else {
                pullRes.summary
            }
        }
    }

    fun createStash(message: String? = null, includeUntracked: Boolean = true) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        val flag = if (includeUntracked) " -u" else ""
        runGitOrFileAction(
            busyText = s.workspaceBusyStashCreate,
            logCommand = "git stash push$flag"
        ) {
            val entry = gitEngine.createStash(
                repoDir = File(project.directoryPath),
                message = message,
                includeUntracked = includeUntracked
            )
            if (entry == null) {
                s.workspaceInfoStashEmpty
            } else {
                s.workspaceInfoStashCreated(entry.message)
            }
        }
    }

    fun applyStash(index: Int = 0) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyStashApply(index),
            logCommand = "git stash apply stash@{$index}"
        ) {
            gitEngine.applyStash(File(project.directoryPath), index)
            s.workspaceInfoStashApplied(index)
        }
    }

    fun popStash(index: Int = 0) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyStashPop(index),
            logCommand = "git stash pop stash@{$index}"
        ) {
            gitEngine.popStash(File(project.directoryPath), index)
            s.workspaceInfoStashPopped(index)
        }
    }

    fun dropStash(index: Int) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyStashDrop(index),
            logCommand = "git stash drop stash@{$index}"
        ) {
            gitEngine.dropStash(File(project.directoryPath), index)
            s.workspaceInfoStashDropped(index)
        }
    }

    fun clearStashes() {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyStashClear,
            logCommand = "git stash clear"
        ) {
            gitEngine.clearStashes(File(project.directoryPath))
            s.workspaceInfoStashCleared
        }
    }

    fun createTag(name: String, message: String? = null) {
        val project = _uiState.value.project ?: return
        val status = _uiState.value.gitStatus
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyCreateTag(name),
            logCommand = "git tag $name"
        ) {
            gitEngine.createTag(
                repoDir = File(project.directoryPath),
                name = name,
                message = message,
                authorName = status?.authorName ?: GitEngine.DEFAULT_AUTHOR_NAME,
                authorEmail = status?.authorEmail ?: GitEngine.DEFAULT_AUTHOR_EMAIL
            )
            s.workspaceInfoTagCreated(name.trim())
        }
    }

    fun deleteTag(name: String) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(
            busyText = s.workspaceBusyDeleteTag(name),
            logCommand = "git tag -d $name"
        ) {
            gitEngine.deleteTag(File(project.directoryPath), name)
            s.workspaceInfoTagDeleted(name)
        }
    }

    fun pushTags(remoteName: String = "origin") {
        val project = _uiState.value.project ?: return
        runGitOrFileAction(
            busyText = AppLocale.current.workspaceBusyPushTags(remoteName),
            logCommand = "git push $remoteName --tags"
        ) {
            gitEngine.pushTags(File(project.directoryPath), remoteName, resolveEffectiveCredentials(remoteName))
        }
    }

    fun saveRepoCredentials(username: String, token: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                if (token.isBlank()) {
                    credentialStore.removeRepoCredentials(repoId)
                } else {
                    credentialStore.saveRepoCredentials(
                        repoId,
                        GitCredentials(username.trim(), token.trim())
                    )
                }
            }
            val s = AppLocale.current
            _uiState.update {
                it.copy(
                    repoCredentials = if (token.isBlank()) null else GitCredentials(username.trim(), token.trim()),
                    infoMessage = if (token.isBlank()) {
                        s.workspaceInfoRepoTokenCleared
                    } else {
                        s.workspaceInfoRepoTokenSaved
                    }
                )
            }
        }
    }

    fun saveAuthorConfig(name: String, email: String, saveGlobally: Boolean) {
        val project = _uiState.value.project ?: return
        val s = AppLocale.current
        runGitOrFileAction(s.workspaceBusySaveAuthor) {
            gitEngine.setAuthorConfig(File(project.directoryPath), name, email, saveGlobally)
            s.workspaceInfoAuthorUpdated
        }
    }

    fun openWorkingDiff(relativePath: String, staged: Boolean) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val outcome = withContext(ioDispatcher) {
                runCatching {
                    gitEngine.getWorkingDiff(File(project.directoryPath), relativePath, staged)
                }
            }
            outcome.onSuccess { diff ->
                _uiState.update {
                    it.copy(
                        diffDialog = DiffDialogState(
                            title = if (staged) "Staged: $relativePath" else "Diff: $relativePath",
                            diffText = diff.ifBlank { AppLocale.current.workspaceDiffEmpty }
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage ?: AppLocale.current.workspaceErrDiffBuild) }
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
            _uiState.update { it.copy(isBusy = true, busyMessage = AppLocale.current.workspaceBusyLoadHistory) }
            val outcome = withContext(ioDispatcher) {
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
                        errorMessage = err.localizedMessage ?: AppLocale.current.workspaceErrLoadHistory
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
            val outcome = withContext(ioDispatcher) {
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
            val outcome = withContext(ioDispatcher) {
                runCatching {
                    gitEngine.getCommitDetail(File(project.directoryPath), commitId)
                }
            }
            outcome.onSuccess { detail ->
                _uiState.update { it.copy(selectedCommitDetail = detail) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage ?: AppLocale.current.workspaceErrLoadCommit) }
            }
        }
    }

    fun openCommitFileDiff(commitId: String, relativePath: String) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val outcome = withContext(ioDispatcher) {
                runCatching {
                    gitEngine.getCommitFileDiff(File(project.directoryPath), commitId, relativePath)
                }
            }
            outcome.onSuccess { diff ->
                _uiState.update {
                    it.copy(
                        diffDialog = DiffDialogState(
                            title = "${commitId.take(7)} • $relativePath",
                            diffText = diff.ifBlank { AppLocale.current.workspaceDiffEmpty }
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.localizedMessage ?: AppLocale.current.workspaceErrDiff) }
            }
        }
    }

    fun closeCommitDetail() {
        _uiState.update { it.copy(selectedCommitDetail = null) }
    }

    // endregion

    // region Syntax Check Tool

    fun runSyntaxCheck(singleRelativePath: String? = null) {
        val state = _uiState.value
        val repo = state.project ?: return
        val root = File(repo.directoryPath)
        val currentDir = state.currentDir
        val normalizedSingle = singleRelativePath?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() }
        val scopeLabel = normalizedSingle ?: currentDir.ifBlank { repo.name }

        syntaxCheckJob?.cancel()
        _uiState.update {
            it.copy(
                syntaxCheckDialog = SyntaxCheckDialogState(
                    scopeLabel = scopeLabel,
                    singleTargetPath = normalizedSingle,
                    isRunning = true
                )
            )
        }

        syntaxCheckJob = viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    val targetPaths = mutableListOf<String>()
                    var skippedFiles = 0

                    if (normalizedSingle != null) {
                        val file = fileManager.resolveSafe(root, normalizedSingle)
                        if (file.exists() && file.isFile) {
                            targetPaths.add(normalizedSingle)
                        }
                    } else {
                        val baseDir = if (currentDir.isBlank()) root else fileManager.resolveSafe(root, currentDir)
                        val rootCanonical = root.canonicalFile
                        if (baseDir.exists() && baseDir.isDirectory) {
                            walkProjectFiles(baseDir, skipIgnoredDirs = true) { file, _ ->
                                val ext = file.extension.lowercase()
                                if (ext in KNOWN_BINARY_EXTENSIONS) {
                                    skippedFiles++
                                    return@walkProjectFiles true
                                }
                                val rel = file.canonicalFile.relativeTo(rootCanonical).invariantSeparatorsPath
                                targetPaths.add(rel)
                                targetPaths.size < MAX_SYNTAX_CHECK_FILES
                            }
                        }
                    }

                    val validator = FileSyntaxValidator(syntaxCheckEngine)
                    var checkedFiles = 0
                    var totalErrors = 0
                    var totalWarnings = 0
                    val reportsWithIssues = mutableListOf<SyntaxFileReportUiItem>()

                    for (relPath in targetPaths) {
                        when (val read = fileManager.readTextFile(root, relPath, maxBytes = 2 * 1024 * 1024)) {
                            is FileReadResult.Binary,
                            is FileReadResult.TooLarge -> skippedFiles++
                            is FileReadResult.Text -> {
                                checkedFiles++
                                val lang = CodeSyntaxHighlighter.detectLanguage(relPath)
                                val outcome = validator.validate(relPath, read.content, lang)
                                if (outcome.issues.isNotEmpty()) {
                                    val lines = read.content.split('\n').map { it.removeSuffix("\r") }
                                    val errCount = outcome.issues.count { it.severity == "error" }
                                    val warnCount = outcome.issues.count { it.severity == "warning" }
                                    totalErrors += errCount
                                    totalWarnings += warnCount
                                    val uiIssues = outcome.issues.take(50).map { iss ->
                                        SyntaxIssueUiItem(
                                            line = iss.line,
                                            column = iss.column,
                                            severity = iss.severity,
                                            message = iss.message,
                                            snippet = validator.buildLineSnippet(lines, iss.line, iss.column)
                                        )
                                    }
                                    reportsWithIssues.add(
                                        SyntaxFileReportUiItem(
                                            relativePath = relPath,
                                            languageName = lang.displayName,
                                            engine = outcome.engine,
                                            errorCount = errCount,
                                            warningCount = warnCount,
                                            issues = uiIssues
                                        )
                                    )
                                }
                            }
                        }
                    }

                    reportsWithIssues.sortWith(
                        compareByDescending<SyntaxFileReportUiItem> { it.errorCount }
                            .thenByDescending { it.warningCount }
                            .thenBy { it.relativePath.lowercase() }
                    )

                    SyntaxCheckDialogState(
                        scopeLabel = scopeLabel,
                        singleTargetPath = normalizedSingle,
                        isRunning = false,
                        checkedFilesCount = checkedFiles,
                        skippedFilesCount = skippedFiles,
                        totalErrors = totalErrors,
                        totalWarnings = totalWarnings,
                        fileReportsWithIssues = reportsWithIssues
                    )
                }

                _uiState.update { it.copy(syntaxCheckDialog = result) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                _uiState.update {
                    it.copy(
                        syntaxCheckDialog = SyntaxCheckDialogState(
                            scopeLabel = scopeLabel,
                            singleTargetPath = normalizedSingle,
                            isRunning = false,
                            errorMessage = err.localizedMessage ?: err.message ?: err.javaClass.simpleName
                        )
                    )
                }
            }
        }
    }

    fun closeSyntaxCheckDialog() {
        syntaxCheckJob?.cancel()
        syntaxCheckJob = null
        _uiState.update { it.copy(syntaxCheckDialog = null) }
    }

    // endregion

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private suspend fun resolveEffectiveCredentials(remoteName: String? = null): GitCredentials? {
        val state = _uiState.value
        val matchedRemote = when {
            !remoteName.isNullOrBlank() -> state.remotes.firstOrNull { it.name == remoteName }
            else -> state.remotes.firstOrNull { it.name == "origin" } ?: state.remotes.firstOrNull()
        }
        val remoteUrl = matchedRemote?.pushUrl
            ?: matchedRemote?.fetchUrl
            ?: state.project?.remoteUrl
        return oauthManager?.resolveFreshCredentials(projectId, repoId, remoteUrl)
            ?: credentialStore.resolveForRepo(projectId, repoId, remoteUrl)
    }

    private fun runGitOrFileAction(
        busyText: String,
        successMessage: String? = null,
        resetHistoryRef: Boolean = false,
        logCommand: String? = null,
        block: suspend () -> Any?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = busyText) }
            val outcome = withContext(ioDispatcher) {
                runCatching { block() }
            }
            outcome.onSuccess { res ->
                val messageFromBlock = res as? String
                val info = successMessage ?: messageFromBlock
                _uiState.update { current ->
                    val updatedLogs = if (logCommand != null) {
                        val entry = GitOutputLogEntry(
                            timestampMillis = System.currentTimeMillis(),
                            command = logCommand,
                            output = info ?: "OK",
                            isError = false
                        )
                        (listOf(entry) + current.gitOutputLogs).take(100)
                    } else {
                        current.gitOutputLogs
                    }
                    current.copy(
                        isBusy = false,
                        busyMessage = null,
                        infoMessage = info,
                        gitOutputLogs = updatedLogs
                    )
                }
                loadAll(resetHistoryRef = resetHistoryRef)
            }.onFailure { err ->
                val errorText = formatGitTransportError(err)
                _uiState.update { current ->
                    val updatedLogs = if (logCommand != null) {
                        val entry = GitOutputLogEntry(
                            timestampMillis = System.currentTimeMillis(),
                            command = logCommand,
                            output = errorText,
                            isError = true
                        )
                        (listOf(entry) + current.gitOutputLogs).take(100)
                    } else {
                        current.gitOutputLogs
                    }
                    current.copy(
                        isBusy = false,
                        busyMessage = null,
                        errorMessage = errorText,
                        gitOutputLogs = updatedLogs
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
        val stashes: List<GitStashEntry>,
        val tags: List<GitTag>,
        val files: List<ProjectFileItem>,
        val historyRef: String?,
        val commits: List<GitCommit>,
        val repoCredentials: GitCredentials?,
        val hasFallbackCredentials: Boolean
    )

    companion object {
        const val HISTORY_PAGE_SIZE = 50
        private const val AUTO_FETCH_THROTTLE_MS = 15_000L
        private const val MAX_SYNTAX_CHECK_FILES = 150
        private val KNOWN_BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "heic", "avif",
            "pdf", "zip", "gz", "tgz", "tar", "bz2", "xz", "7z", "rar",
            "jar", "war", "ear", "apk", "aab", "aar", "so", "dll", "exe", "dylib",
            "class", "dex", "o", "a", "pyc", "pyo",
            "mp3", "mp4", "mkv", "mov", "avi", "wav", "ogg", "flac", "webm",
            "ttf", "otf", "woff", "woff2", "eot",
            "wasm", "gguf", "bin", "dat", "db", "sqlite", "sqlite3", "keystore", "jks"
        )

        fun factory(
            projectId: String,
            repoId: String,
            gitEngine: GitEngine,
            fileManager: ProjectFileManager,
            registry: ProjectRegistry,
            credentialStore: CredentialStore,
            syntaxCheckEngine: SyntaxCheckEngine = NoOpSyntaxCheckEngine,
            oauthManager: GitOAuthManager? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WorkspaceViewModel(
                    projectId = projectId,
                    repoId = repoId,
                    gitEngine = gitEngine,
                    fileManager = fileManager,
                    registry = registry,
                    credentialStore = credentialStore,
                    syntaxCheckEngine = syntaxCheckEngine,
                    oauthManager = oauthManager
                ) as T
            }
        }
    }
}
