package com.pocketworker.app.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketworker.app.core.git.ProjectFileItem
import com.pocketworker.app.ui.common.FormFieldShape
import com.pocketworker.app.ui.common.PocketFormDialog
import com.pocketworker.app.ui.common.formTextFieldColors
import com.pocketworker.app.ui.common.formatFileSize

@Composable
fun FilesTab(
    state: WorkspaceUiState,
    onOpenDirectory: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleShowHiddenGit: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameEntry: (relativePath: String, newName: String) -> Unit,
    onDeleteEntry: (relativePath: String) -> Unit
) {
    var showSearchBar by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var entryToRename by remember { mutableStateOf<ProjectFileItem?>(null) }
    var entryToDelete by remember { mutableStateOf<ProjectFileItem?>(null) }

    val displayedItems = if (showSearchBar && state.fileSearchQuery.isNotBlank()) {
        state.fileSearchResults
    } else {
        state.files
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar & Breadcrumbs
        Surface(tonalElevation = 2.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.currentDir.isNotEmpty() && !showSearchBar) {
                        IconButton(onClick = onNavigateUp, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Вверх"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "root",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (state.currentDir.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onOpenDirectory("") }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                        if (state.currentDir.isNotEmpty()) {
                            val parts = state.currentDir.split('/')
                            var acc = ""
                            parts.forEachIndexed { idx, part ->
                                acc = if (acc.isEmpty()) part else "$acc/$part"
                                val targetPath = acc
                                Text(
                                    text = "/",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = part,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (idx == parts.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .clickable { onOpenDirectory(targetPath) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) onSearchQueryChange("")
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (showSearchBar) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Поиск файлов"
                        )
                    }
                    IconButton(
                        onClick = onToggleShowHiddenGit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (state.showHiddenGit) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = "Показать/скрыть .git"
                        )
                    }
                    IconButton(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = "Новый файл")
                    }
                    IconButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Новая папка")
                    }
                }

                if (showSearchBar) {
                    OutlinedTextField(
                        value = state.fileSearchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Поиск по имени или пути файла...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 10.dp)
                    )
                }
            }
        }

        HorizontalDivider()

        if (displayedItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showSearchBar && state.fileSearchQuery.isNotBlank()) {
                        "Ничего не найдено"
                    } else {
                        "Папка пуста"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedItems, key = { it.relativePath }) { item ->
                    FileRowItem(
                        item = item,
                        showFullPath = showSearchBar && state.fileSearchQuery.isNotBlank(),
                        onClick = {
                            if (item.isDirectory) {
                                onOpenDirectory(item.relativePath)
                                if (showSearchBar) {
                                    showSearchBar = false
                                    onSearchQueryChange("")
                                }
                            } else {
                                onOpenFile(item.relativePath)
                            }
                        },
                        onRenameClick = { entryToRename = item },
                        onDeleteClick = { entryToDelete = item }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }

    if (showNewFileDialog) {
        SingleNameInputDialog(
            title = "Новый файл",
            icon = Icons.AutoMirrored.Rounded.NoteAdd,
            label = "Имя файла (например, notes.md)",
            initialValue = "",
            confirmLabel = "Создать",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name -> onCreateFile(name) }
        )
    }

    if (showNewFolderDialog) {
        SingleNameInputDialog(
            title = "Новая папка",
            icon = Icons.Rounded.CreateNewFolder,
            label = "Имя папки",
            initialValue = "",
            confirmLabel = "Создать",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name -> onCreateFolder(name) }
        )
    }

    entryToRename?.let { target ->
        SingleNameInputDialog(
            title = "Переименовать",
            icon = Icons.Rounded.DriveFileRenameOutline,
            label = "Новое имя",
            initialValue = target.name,
            confirmLabel = "Переименовать",
            onDismiss = { entryToRename = null },
            onConfirm = { newName -> onRenameEntry(target.relativePath, newName) }
        )
    }

    entryToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Удалить «${target.name}»?") },
            text = {
                Text(
                    if (target.isDirectory) {
                        "Папка и всё её содержимое будут удалены с устройства."
                    } else {
                        "Файл «${target.relativePath}» будет удалён."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteEntry(target.relativePath)
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun FileRowItem(
    item: ProjectFileItem,
    showFullPath: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
            contentDescription = null,
            tint = if (item.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (showFullPath) item.relativePath else item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.isDirectory) {
                Text(
                    text = formatFileSize(item.sizeBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item.gitChangeType?.let { change ->
            Spacer(modifier = Modifier.width(8.dp))
            GitChangeBadge(changeType = change)
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Действия",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Переименовать") },
                    leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRenameClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun SingleNameInputDialog(
    title: String,
    icon: ImageVector,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    PocketFormDialog(
        title = title,
        icon = icon,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
            Button(
                onClick = {
                    onConfirm(text)
                    onDismiss()
                },
                enabled = text.isNotBlank(),
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(confirmLabel)
            }
        }
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
