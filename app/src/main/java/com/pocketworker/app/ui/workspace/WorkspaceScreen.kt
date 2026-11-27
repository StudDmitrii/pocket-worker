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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketworker.app.core.i18n.LocalAppStrings
import com.pocketworker.app.ui.common.AnimatedRefreshIconButton
import com.pocketworker.app.ui.common.OnLifecycleResume
import com.pocketworker.app.ui.common.TopNotificationHost
import com.pocketworker.app.ui.common.rememberTopNotificationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onBackToProjects: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val notificationState = rememberTopNotificationState()
    val s = LocalAppStrings.current

    var commitIdForBranch by remember { mutableStateOf<String?>(null) }

    OnLifecycleResume {
        viewModel.loadAll()
    }

    LaunchedEffect(state.activeTab, state.project?.id, state.gitStatus?.currentBranch) {
        if (state.activeTab == WorkspaceTab.GIT && state.project != null && state.gitStatus?.isGitRepo == true) {
            viewModel.autoFetchCurrentBranch()
        }
    }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val err = state.errorMessage
        val info = state.infoMessage
        val msg = err ?: info
        if (msg != null) {
            notificationState.show(message = msg, isError = err != null)
            viewModel.clearMessages()
        }
    }

    val activeEditor = state.fileEditor
    if (activeEditor != null) {
        FileEditorScreen(
            editorState = activeEditor,
            notificationState = notificationState,
            onContentChange = { viewModel.updateEditorContent(it) },
            onSave = { viewModel.saveEditorFile() },
            onClose = { viewModel.closeFileEditor() },
            onOpenSettings = onOpenSettings
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.workspaceBackToProjectRepos)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.project?.name ?: s.workspaceRepoFallbackTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val branch = state.gitStatus?.currentBranch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.parentProjectName.isNotBlank()) {
                                Text(
                                    text = state.parentProjectName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (state.gitStatus?.isGitRepo == true && branch != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                            }
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
                    }
                },
                actions = {
                    AnimatedRefreshIconButton(
                        isRefreshing = state.isLoading || state.isBusy,
                        onClick = { viewModel.loadAll() },
                        contentDescription = s.commonRefresh
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = s.commonSettings)
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
                    label = { Text(s.workspaceTabFiles) }
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
                    label = { Text(s.workspaceTabHistory) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                            onDeleteEntry = viewModel::deleteFileEntry,
                            onRunSyntaxCheck = viewModel::runSyntaxCheck
                        )
                        WorkspaceTab.GIT -> GitControlTab(
                            state = state,
                            onInitGit = { viewModel.initGitInProject() },
                            onToggleViewAsTree = viewModel::toggleViewChangesAsTree,
                            onFetch = viewModel::fetch,
                            onPull = viewModel::pull,
                            onPush = { remote, force -> viewModel.push(remoteName = remote, force = force) },
                            onSync = viewModel::syncRemote,
                            onStageFile = viewModel::stageFile,
                            onStageAll = viewModel::stageAll,
                            onUnstageFile = viewModel::unstageFile,
                            onUnstageAll = viewModel::unstageAll,
                            onDiscardFile = viewModel::discardFileChanges,
                            onDiscardAll = viewModel::discardAllChanges,
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
                            onCreateStash = viewModel::createStash,
                            onApplyStash = viewModel::applyStash,
                            onPopStash = viewModel::popStash,
                            onDropStash = viewModel::dropStash,
                            onClearStashes = viewModel::clearStashes,
                            onCreateTag = viewModel::createTag,
                            onDeleteTag = viewModel::deleteTag,
                            onPushTags = viewModel::pushTags,
                            onClearGitOutputLogs = viewModel::clearGitOutputLogs
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

            TopNotificationHost(
                state = notificationState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    state.syntaxCheckDialog?.let { syntaxState ->
        SyntaxCheckResultsDialog(
            state = syntaxState,
            onDismiss = viewModel::closeSyntaxCheckDialog,
            onRecheck = { viewModel.runSyntaxCheck(syntaxState.singleTargetPath) },
            onOpenFile = { path ->
                viewModel.closeSyntaxCheckDialog()
                viewModel.openFile(path)
            }
        )
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
}
