package com.pocketworker.app.ui.home

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
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
import com.pocketworker.app.core.git.ProjectSummary
import com.pocketworker.app.core.git.WorkspaceProject
import com.pocketworker.app.ui.common.CredentialsEditorDialog
import com.pocketworker.app.ui.common.OnLifecycleResume
import com.pocketworker.app.ui.common.formatShortTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenProject: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var projectToEdit by remember { mutableStateOf<WorkspaceProject?>(null) }
    var projectToDelete by remember { mutableStateOf<ProjectSummary?>(null) }
    var showCredentialsDialog by remember { mutableStateOf(false) }

    OnLifecycleResume {
        viewModel.refreshProjects()
    }

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
                            text = if (state.activeTab == HomeTab.PROJECTS) {
                                "Проекты"
                            } else {
                                "Агенты"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (state.activeTab == HomeTab.PROJECTS) {
                        IconButton(onClick = { showCredentialsDialog = true }) {
                            Icon(Icons.Rounded.Key, contentDescription = "Токен Git по умолчанию")
                        }
                        IconButton(onClick = { viewModel.refreshProjects() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Обновить список")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.activeTab == HomeTab.PROJECTS,
                    onClick = { viewModel.setActiveTab(HomeTab.PROJECTS) },
                    icon = { Icon(Icons.Rounded.FolderCopy, contentDescription = null) },
                    label = { Text("Проекты") }
                )
                NavigationBarItem(
                    selected = state.activeTab == HomeTab.AGENTS,
                    onClick = { viewModel.setActiveTab(HomeTab.AGENTS) },
                    icon = { Icon(Icons.Rounded.SmartToy, contentDescription = null) },
                    label = { Text("Агенты") }
                )
            }
        },
        floatingActionButton = {
            if (state.activeTab == HomeTab.PROJECTS) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.clearCreateProjectError()
                        showCreateProjectDialog = true
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Новый проект") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state.activeTab) {
                HomeTab.PROJECTS -> {
                    when {
                        state.isLoading && state.projects.isEmpty() -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        state.projects.isEmpty() -> {
                            EmptyWorkspaceProjectsPlaceholder(
                                onCreateClick = {
                                    viewModel.clearCreateProjectError()
                                    showCreateProjectDialog = true
                                },
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
                                items(state.projects, key = { it.project.id }) { summary ->
                                    WorkspaceProjectCard(
                                        summary = summary,
                                        onClick = { onOpenProject(summary.project.id) },
                                        onEditClick = { projectToEdit = summary.project },
                                        onDeleteClick = { projectToDelete = summary }
                                    )
                                }
                            }
                        }
                    }
                }
                HomeTab.AGENTS -> {
                    // Intentionally empty Agents tab for future features
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    if (showCreateProjectDialog) {
        ProjectFormDialog(
            title = "Новый проект",
            confirmLabel = "Создать",
            initialName = "",
            initialDescription = "",
            showOpenImmediatelyOption = true,
            isSaving = state.isCreatingProject,
            errorMessage = state.createProjectError,
            onInputChanged = { viewModel.clearCreateProjectError() },
            onDismiss = {
                if (!state.isCreatingProject) {
                    viewModel.clearCreateProjectError()
                    showCreateProjectDialog = false
                }
            },
            onConfirm = { name, desc, openImmediately ->
                viewModel.createProject(name, desc) { projectId ->
                    showCreateProjectDialog = false
                    if (openImmediately) {
                        onOpenProject(projectId)
                    }
                }
            }
        )
    }

    projectToEdit?.let { target ->
        ProjectFormDialog(
            title = "Редактировать проект",
            confirmLabel = "Сохранить",
            initialName = target.name,
            initialDescription = target.description,
            showOpenImmediatelyOption = false,
            isSaving = false,
            errorMessage = null,
            onInputChanged = {},
            onDismiss = { projectToEdit = null },
            onConfirm = { name, desc, _ ->
                viewModel.updateProject(target.id, name, desc)
                projectToEdit = null
            }
        )
    }

    if (showCredentialsDialog) {
        CredentialsEditorDialog(
            title = "Токен Git по умолчанию",
            description = "Хранится в зашифрованном Android Keystore и используется, если у проекта и репозитория не задан отдельный токен.",
            currentCredentials = state.defaultCredentials,
            onDismiss = { showCredentialsDialog = false },
            onSave = { user, token -> viewModel.saveDefaultCredentials(user, token) }
        )
    }

    projectToDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Удалить проект «${summary.project.name}»?") },
            text = {
                Text(
                    "Папка проекта и все входящие в неё локальные репозитории (${summary.repoCount}) будут удалены с устройства. Удалённые репозитории в облаке не пострадают."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(summary.project.id)
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
private fun EmptyWorkspaceProjectsPlaceholder(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderCopy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "Пока нет проектов",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Создайте проект-папку, чтобы объединить внутри несколько репозиториев (клиент, бэкенд, админку, сервисы).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onCreateClick) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Создать первый проект")
        }
    }
}

@Composable
private fun WorkspaceProjectCard(
    summary: ProjectSummary,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    imageVector = Icons.Rounded.FolderCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = summary.project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Меню проекта",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Переименовать / описание") },
                            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить проект") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            if (summary.project.description.isNotBlank()) {
                Text(
                    text = summary.project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = formatRepoCountLabel(summary.repoCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (summary.dirtyRepoCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "С изменениями: ${summary.dirtyRepoCount}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (summary.repoNamesPreview.isNotEmpty()) {
                val suffix = if (summary.repoCount > summary.repoNamesPreview.size) {
                    " +${summary.repoCount - summary.repoNamesPreview.size}"
                } else {
                    ""
                }
                Text(
                    text = summary.repoNamesPreview.joinToString(", ") + suffix,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Нажмите, чтобы подключить или создать репозитории • ${formatShortTimestamp(summary.project.createdAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProjectFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialDescription: String,
    showOpenImmediatelyOption: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onInputChanged: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, openImmediately: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var openImmediately by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onInputChanged()
                    },
                    label = { Text("Название проекта") },
                    placeholder = { Text("Например: Shop Platform") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        onInputChanged()
                    },
                    label = { Text("Описание (необязательно)") },
                    placeholder = { Text("Клиент, бэкенд, админка и сервисы") },
                    maxLines = 3,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showOpenImmediatelyOption) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !isSaving) {
                            openImmediately = !openImmediately
                        }
                    ) {
                        Checkbox(
                            checked = openImmediately,
                            onCheckedChange = { openImmediately = it },
                            enabled = !isSaving
                        )
                        Text(
                            text = "Сразу открыть проект",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (isSaving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description, openImmediately) },
                enabled = !isSaving && name.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Отмена")
            }
        }
    )
}

private fun formatRepoCountLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> "репозиториев"
        mod10 == 1 -> "репозиторий"
        mod10 in 2..4 -> "репозитория"
        else -> "репозиториев"
    }
    return "$count $word"
}
