package com.pocketworker.app.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onBackToProjects: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCredentialsDialog by remember { mutableStateOf(false) }
    var showAuthorDialog by remember { mutableStateOf(false) }
    var commitIdForBranch by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val msg = state.errorMessage ?: state.infoMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    val activeEditor = state.fileEditor
    if (activeEditor != null) {
        FileEditorScreen(
            editorState = activeEditor,
            onContentChange = { viewModel.updateEditorContent(it) },
            onSave = { viewModel.saveEditorFile() },
            onClose = { viewModel.closeFileEditor() }
        )
        return
    }

    BackHandler(enabled = state.activeTab == WorkspaceTab.FILES && state.currentDir.isNotEmpty()) {
        viewModel.navigateUpDirectory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackToProjects) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "К проектам")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.project?.name ?: "Проект",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val branch = state.gitStatus?.currentBranch
                        if (state.gitStatus?.isGitRepo == true && branch != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.clickable { viewModel.setActiveTab(WorkspaceTab.GIT) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountTree,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = branch,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showCredentialsDialog = true }) {
                        Icon(Icons.Rounded.Key, contentDescription = "Токен доступа")
                    }
                    if (state.gitStatus?.isGitRepo == true) {
                        IconButton(onClick = { showAuthorDialog = true }) {
                            Icon(Icons.Rounded.Person, contentDescription = "Автор коммитов")
                        }
                    }
                    IconButton(onClick = { viewModel.loadAll() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.activeTab == WorkspaceTab.FILES,
                    onClick = { viewModel.setActiveTab(WorkspaceTab.FILES) },
                    icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                    label = { Text("Файлы") }
                )
                NavigationBarItem(
                    selected = state.activeTab == WorkspaceTab.GIT,
                    onClick = { viewModel.setActiveTab(WorkspaceTab.GIT) },
                    icon = { Icon(Icons.Rounded.AccountTree, contentDescription = null) },
                    label = { Text("Git") }
                )
                NavigationBarItem(
                    selected = state.activeTab == WorkspaceTab.HISTORY,
                    onClick = { viewModel.setActiveTab(WorkspaceTab.HISTORY) },
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                    label = { Text("История") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.busyMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (state.isLoading && state.project == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.activeTab) {
                    WorkspaceTab.FILES -> FilesTab(
                        state = state,
                        onOpenDirectory = viewModel::openDirectory,
                        onNavigateUp = { viewModel.navigateUpDirectory() },
                        onOpenFile = viewModel::openFile,
                        onToggleShowHiddenGit = viewModel::toggleShowHiddenGit,
                        onSearchQueryChange = viewModel::updateFileSearch,
                        onCreateFile = viewModel::createNewFile,
                        onCreateFolder = viewModel::createNewDirectory,
                        onRenameEntry = viewModel::renameFileEntry,
                        onDeleteEntry = viewModel::deleteFileEntry
                    )
                    WorkspaceTab.GIT -> GitControlTab(
                        state = state,
                        onInitGit = { viewModel.initGitInProject() },
                        onFetch = viewModel::fetch,
                        onPull = viewModel::pull,
                        onPush = { remote -> viewModel.push(remoteName = remote) },
                        onStageFile = viewModel::stageFile,
                        onStageAll = viewModel::stageAll,
                        onUnstageFile = viewModel::unstageFile,
                        onUnstageAll = viewModel::unstageAll,
                        onDiscardFile = viewModel::discardFileChanges,
                        onViewDiff = viewModel::openWorkingDiff,
                        onCommit = viewModel::commit,
                        onCheckoutBranch = viewModel::checkoutBranch,
                        onCreateBranch = viewModel::createBranch,
                        onRenameBranch = viewModel::renameBranch,
                        onMergeBranch = viewModel::mergeBranch,
                        onDeleteBranch = { branch -> viewModel.deleteBranch(branch) },
                        onAddRemote = viewModel::addRemote,
                        onUpdateRemote = viewModel::updateRemoteUrl,
                        onRemoveRemote = viewModel::removeRemote,
                        onOpenCredentialsDialog = { showCredentialsDialog = true },
                        onOpenAuthorDialog = { showAuthorDialog = true }
                    )
                    WorkspaceTab.HISTORY -> HistoryTab(
                        state = state,
                        onSelectBranchRef = viewModel::selectHistoryBranch,
                        onCommitClick = viewModel::openCommitDetail,
                        onLoadMore = viewModel::loadMoreCommits
                    )
                }
            }
        }
    }

    state.diffDialog?.let { diffState ->
        DiffViewerDialog(
            state = diffState,
            onDismiss = viewModel::closeDiffDialog
        )
    }

    state.selectedCommitDetail?.let { detail ->
        CommitDetailSheet(
            detail = detail,
            onDismiss = viewModel::closeCommitDetail,
            onViewFileDiff = viewModel::openCommitFileDiff,
            onCreateBranchFromCommit = { commitId ->
                viewModel.closeCommitDetail()
                commitIdForBranch = commitId
            }
        )
    }

    commitIdForBranch?.let { commitId ->
        CreateBranchDialog(
            startPointLabel = commitId.take(7),
            onDismiss = { commitIdForBranch = null },
            onCreate = { name, checkout ->
                viewModel.createBranch(name, startPoint = commitId, checkout = checkout)
            }
        )
    }

    if (showCredentialsDialog) {
        ProjectCredentialsDialog(
            currentCredentials = state.projectCredentials,
            hasDefaultCredentials = state.hasDefaultCredentials,
            onDismiss = { showCredentialsDialog = false },
            onSave = viewModel::saveProjectCredentials
        )
    }

    if (showAuthorDialog && state.gitStatus != null) {
        AuthorConfigDialog(
            currentName = state.gitStatus!!.authorName,
            currentEmail = state.gitStatus!!.authorEmail,
            onDismiss = { showAuthorDialog = false },
            onSave = viewModel::saveAuthorConfig
        )
    }
}
