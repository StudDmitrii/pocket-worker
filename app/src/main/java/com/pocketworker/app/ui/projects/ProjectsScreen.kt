package com.pocketworker.app.ui.projects

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.pocketworker.app.core.git.ProjectCardInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddChoiceSheet by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCredentialsDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<ProjectCardInfo?>(null) }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val msg = state.errorMessage ?: state.infoMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PocketWorker", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Локальные проекты и Git",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCredentialsDialog = true }) {
                        Icon(Icons.Rounded.Key, contentDescription = "Токен Git по умолчанию")
                    }
                    IconButton(onClick = { viewModel.refreshProjects() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Обновить список")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddChoiceSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Добавить проект") }
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
                state.isLoading && state.projects.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.projects.isEmpty() -> {
                    EmptyProjectsPlaceholder(
                        onAddClick = { showAddChoiceSheet = true },
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
                        items(state.projects, key = { it.entry.id }) { card ->
                            ProjectCard(
                                info = card,
                                onClick = { onOpenProject(card.entry.id) },
                                onDeleteClick = { projectToDelete = card }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddChoiceSheet) {
        AddProjectChoiceSheet(
            onDismiss = { showAddChoiceSheet = false },
            onConnectClick = {
                showAddChoiceSheet = false
                showCloneDialog = true
            },
            onCreateClick = {
                showAddChoiceSheet = false
                showCreateDialog = true
            }
        )
    }

    if (showCloneDialog) {
        CloneProjectDialog(
            isCloning = state.isCloning,
            cloneProgress = state.cloneProgress,
            defaultCredentials = state.defaultCredentials,
            onDismiss = { showCloneDialog = false },
            onCancelClone = { viewModel.cancelClone() },
            onClone = { url, name, branch, user, token, saveCreds ->
                viewModel.cloneProject(
                    url = url,
                    customName = name,
                    branch = branch,
                    username = user,
                    token = token,
                    saveCredentialsForProject = saveCreds,
                    onSuccess = { projectId ->
                        showCloneDialog = false
                        onOpenProject(projectId)
                    }
                )
            }
        )
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            isCreating = state.isCreating,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, initGit, branch, readme, remoteUrl, authorName, authorEmail ->
                viewModel.createEmptyProject(
                    projectName = name,
                    initGit = initGit,
                    initialBranch = branch,
                    createReadmeAndCommit = readme,
                    remoteUrl = remoteUrl,
                    authorName = authorName,
                    authorEmail = authorEmail,
                    onSuccess = { projectId ->
                        showCreateDialog = false
                        onOpenProject(projectId)
                    }
                )
            }
        )
    }

    if (showCredentialsDialog) {
        DefaultCredentialsDialog(
            currentCredentials = state.defaultCredentials,
            onDismiss = { showCredentialsDialog = false },
            onSave = { user, token -> viewModel.saveDefaultCredentials(user, token) }
        )
    }

    projectToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Удалить проект?") },
            text = {
                Text("Локальная папка проекта «${card.entry.name}» и вся история на устройстве будут удалены. Удалённый репозиторий в облаке не пострадает.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(card.entry.id)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun EmptyProjectsPlaceholder(
    onAddClick: () -> Unit,
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
            text = "Пока нет проектов",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Нажмите «Добавить проект», чтобы клонировать репозиторий по ссылке или создать новый пустой проект.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onAddClick) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Добавить проект")
        }
    }
}

@Composable
private fun ProjectCard(
    info: ProjectCardInfo,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = info.entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Удалить проект",
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
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
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
                    text = "Без Git (можно инициализировать внутри проекта)",
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

fun formatShortTimestamp(timestampMillis: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampMillis))
}
