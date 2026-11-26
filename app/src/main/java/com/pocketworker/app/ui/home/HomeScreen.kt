package com.pocketworker.app.ui.home

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.pocketworker.app.core.git.ProjectSummary
import com.pocketworker.app.core.git.WorkspaceProject
import com.pocketworker.app.ui.common.AnimatedRefreshIconButton
import com.pocketworker.app.ui.common.FormFieldShape
import com.pocketworker.app.ui.common.FormOptionCard
import com.pocketworker.app.ui.common.OnLifecycleResume
import com.pocketworker.app.ui.common.PocketFormDialog
import com.pocketworker.app.ui.common.WorkspaceBackdrop
import com.pocketworker.app.ui.common.appSwitchColors
import com.pocketworker.app.ui.common.backdropCardBorderColor
import com.pocketworker.app.ui.common.backdropCardContainerColor
import com.pocketworker.app.ui.common.formTextFieldColors
import com.pocketworker.app.ui.common.formatShortTimestamp
import com.pocketworker.app.ui.theme.LocalIsDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    animatedBackgroundEnabled: Boolean,
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = LocalIsDarkTheme.current

    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var projectToEdit by remember { mutableStateOf<WorkspaceProject?>(null) }
    var projectToDelete by remember { mutableStateOf<ProjectSummary?>(null) }

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
                    title = {
                        Column {
                            Text(
                                text = "Проекты",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (state.projects.isEmpty()) {
                                    "Рабочая папка"
                                } else {
                                    "Все проекты (${state.projects.size})"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        AnimatedRefreshIconButton(
                            isRefreshing = state.isLoading,
                            onClick = { viewModel.refreshProjects() },
                            contentDescription = "Обновить список"
                        )
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.clearCreateProjectError()
                        showCreateProjectDialog = true
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Новый проект") }
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
                                    isDark = isDark,
                                    onClick = { onOpenProject(summary.project.id) },
                                    onEditClick = { projectToEdit = summary.project },
                                    onDeleteClick = { projectToDelete = summary }
                                )
                            }
                        }
                    }
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
internal fun WorkspaceProjectCard(
    summary: ProjectSummary,
    isDark: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    interactive: Boolean = true
) {
    var menuExpanded by remember { mutableStateOf(false) }
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
                            imageVector = Icons.Rounded.FolderCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = summary.project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { if (interactive) menuExpanded = true },
                        enabled = interactive,
                        modifier = Modifier.size(32.dp)
                    ) {
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
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "С изменениями: ${summary.dirtyRepoCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
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

    PocketFormDialog(
        title = title,
        subtitle = if (showOpenImmediatelyOption) {
            "Папка-контейнер для ваших репозиториев"
        } else {
            "Измените название или заметку о проекте"
        },
        icon = if (showOpenImmediatelyOption) {
            Icons.Rounded.CreateNewFolder
        } else {
            Icons.Rounded.DriveFileRenameOutline
        },
        onDismiss = onDismiss,
        dismissEnabled = !isSaving,
        pinnedBanner = if (!errorMessage.isNullOrBlank()) {
            {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
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
        } else null,
        footerActions = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Отмена")
            }
            Button(
                onClick = { onConfirm(name, description, openImmediately) },
                enabled = !isSaving && name.isNotBlank(),
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(confirmLabel)
            }
        }
    ) {
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
            shape = FormFieldShape,
            colors = formTextFieldColors(),
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
            minLines = 2,
            maxLines = 4,
            enabled = !isSaving,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        if (showOpenImmediatelyOption) {
            FormOptionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSaving) {
                            openImmediately = !openImmediately
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Сразу открыть проект",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Перейти к добавлению репозиториев после создания",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = openImmediately,
                        onCheckedChange = { openImmediately = it },
                        enabled = !isSaving,
                        colors = appSwitchColors()
                    )
                }
            }
        }

        if (isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
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
