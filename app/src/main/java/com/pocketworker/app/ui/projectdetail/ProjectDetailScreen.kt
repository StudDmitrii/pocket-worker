package com.pocketworker.app.ui.projectdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketworker.app.core.git.RepoCardInfo
import com.pocketworker.app.ui.common.AnimatedRefreshIconButton
import com.pocketworker.app.ui.common.OnLifecycleResume
import com.pocketworker.app.ui.common.WorkspaceBackdrop
import com.pocketworker.app.ui.common.backdropCardBorderColor
import com.pocketworker.app.ui.common.backdropCardContainerColor
import com.pocketworker.app.ui.common.formatShortTimestamp
import com.pocketworker.app.ui.theme.LocalIsDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel,
    animatedBackgroundEnabled: Boolean,
    onBack: () -> Unit,
    onOpenRepo: (repoId: String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = LocalIsDarkTheme.current

    var showAddChoiceSheet by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<RepoCardInfo?>(null) }

    OnLifecycleResume {
        viewModel.refresh()
    }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val msg = state.errorMessage ?: state.infoMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    WorkspaceBackdrop(animatedCubesEnabled = animatedBackgroundEnabled) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "К проектам")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = state.project?.name ?: "Проект",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Репозитории (${state.repos.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        AnimatedRefreshIconButton(
                            isRefreshing = state.isLoading,
                            onClick = { viewModel.refresh() },
                            contentDescription = "Обновить"
                        )
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddChoiceSheet = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Добавить репозиторий") }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when {
                    state.isLoading && state.repos.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.repos.isEmpty() -> {
                        EmptyReposPlaceholder(
                            projectName = state.project?.name ?: "проекте",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.repos, key = { it.entry.id }) { card ->
                                RepoCard(
                                    info = card,
                                    isDark = isDark,
                                    onClick = { onOpenRepo(card.entry.id) },
                                    onDeleteClick = { repoToDelete = card }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddChoiceSheet) {
        AddRepoChoiceSheet(
            onDismiss = { showAddChoiceSheet = false },
            onConnectClick = {
                showAddChoiceSheet = false
                viewModel.clearCloneError()
                showCloneDialog = true
            },
            onAddGroupClick = {
                showAddChoiceSheet = false
                viewModel.resetGroupDialogState()
                showAddGroupDialog = true
            },
            onCreateClick = {
                showAddChoiceSheet = false
                viewModel.clearCreateRepoError()
                showCreateRepoDialog = true
            }
        )
    }

    if (showAddGroupDialog) {
        AddGroupSheet(
            state = state,
            fallbackCredentials = state.projectCredentials ?: state.defaultCredentials,
            onClearError = { viewModel.clearGroupError() },
            onDiscover = { url, useAuth, user, token ->
                viewModel.discoverGroupRepositories(
                    url = url,
                    useAuth = useAuth,
                    username = user,
                    token = token
                )
            },
            onToggleRepo = { repoId -> viewModel.toggleDiscoveredRepoSelection(repoId) },
            onSetAllSelected = { selected -> viewModel.setAllDiscoveredReposSelected(selected) },
            onCloneSelected = { useAuth, user, token, saveCreds ->
                viewModel.cloneSelectedGroupRepositories(
                    useAuth = useAuth,
                    username = user,
                    token = token,
                    saveCredentialsForRepos = saveCreds,
                    onAllDone = {
                        viewModel.resetGroupDialogState()
                        showAddGroupDialog = false
                    }
                )
            },
            onCancelClone = { viewModel.cancelGroupClone() },
            onDismiss = {
                viewModel.resetGroupDialogState()
                showAddGroupDialog = false
            }
        )
    }

    if (showCloneDialog) {
        CloneRepoDialog(
            isCloning = state.isCloning,
            cloneProgress = state.cloneProgress,
            errorMessage = state.cloneError,
            fallbackCredentials = state.projectCredentials ?: state.defaultCredentials,
            onClearError = { viewModel.clearCloneError() },
            onDismiss = {
                viewModel.clearCloneError()
                showCloneDialog = false
            },
            onCancelClone = { viewModel.cancelClone() },
            onClone = { url, name, branch, useAuth, user, token, saveCreds ->
                viewModel.cloneRepository(
                    url = url,
                    customName = name,
                    branch = branch,
                    useAuth = useAuth,
                    username = user,
                    token = token,
                    saveCredentialsForRepo = saveCreds,
                    onSuccess = { repoId ->
                        showCloneDialog = false
                        onOpenRepo(repoId)
                    }
                )
            }
        )
    }

    if (showCreateRepoDialog) {
        CreateRepoDialog(
            isCreating = state.isCreatingRepo,
            errorMessage = state.createRepoError,
            onClearError = { viewModel.clearCreateRepoError() },
            onDismiss = {
                viewModel.clearCreateRepoError()
                showCreateRepoDialog = false
            },
            onCreate = { name, initGit, branch, readme, remoteUrl, authorName, authorEmail ->
                viewModel.createEmptyRepository(
                    repoName = name,
                    initGit = initGit,
                    initialBranch = branch,
                    createReadmeAndCommit = readme,
                    remoteUrl = remoteUrl,
                    authorName = authorName,
                    authorEmail = authorEmail,
                    onSuccess = { repoId ->
                        showCreateRepoDialog = false
                        onOpenRepo(repoId)
                    }
                )
            }
        )
    }

    repoToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            title = { Text("Удалить репозиторий «${card.entry.name}»?") },
            text = {
                Text("Локальная папка репозитория и его история на устройстве будут удалены. Удалённый репозиторий в облаке не пострадает.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRepository(card.entry.id)
                        repoToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun EmptyReposPlaceholder(
    projectName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "В проекте «$projectName» пока пусто",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Добавьте сюда репозитории (например, client, backend, admin, services): клонируйте по Git-ссылке, загрузите группу или создайте новый.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun RepoCard(
    info: RepoCardInfo,
    isDark: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    interactive: Boolean = true
) {
    val cardShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(if (interactive) Modifier.clickable(onClick = onClick) else Modifier),
        color = backdropCardContainerColor(isDark),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = cardShape,
        border = BorderStroke(1.dp, backdropCardBorderColor(isDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = info.entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDeleteClick,
                    enabled = interactive,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Удалить репозиторий",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (info.isGitRepo) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountTree,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = info.currentBranch ?: "HEAD",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    val dirtyTotal = info.changedFilesCount + info.untrackedCount
                    val statusLabel = when {
                        info.isUnborn && dirtyTotal == 0 -> "Нет коммитов"
                        dirtyTotal == 0 -> "Чисто"
                        else -> "Изменений: $dirtyTotal"
                    }
                    Surface(
                        color = if (dirtyTotal > 0) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (dirtyTotal > 0) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (dirtyTotal > 0) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (info.aheadCount > 0 || info.behindCount > 0) {
                        Text(
                            text = "↑${info.aheadCount} ↓${info.behindCount}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Text(
                    text = "Без Git (можно инициализировать внутри)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!info.remoteUrl.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = info.remoteUrl,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!info.lastCommitMessage.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = info.lastCommitMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    info.lastCommitTime?.let { ts ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatShortTimestamp(ts),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!info.errorMessage.isNullOrBlank()) {
                Text(
                    text = info.errorMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
