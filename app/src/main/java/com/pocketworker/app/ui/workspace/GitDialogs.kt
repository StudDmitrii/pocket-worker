package com.pocketworker.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketworker.app.core.git.CommitDetail
import com.pocketworker.app.core.git.FileChangeType
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitRemote
import com.pocketworker.app.ui.common.formatShortTimestamp
import com.pocketworker.app.ui.theme.GitSemanticColors

@Composable
fun DiffViewerDialog(
    state: DiffDialogState,
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val lines = remember(state.diffText) { state.diffText.lines() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                lines.forEach { line ->
                    val bgColor = when {
                        line.startsWith("+") && !line.startsWith("+++") ->
                            if (isDark) Color(0xFF143524) else Color(0xFFDCFCE7)
                        line.startsWith("-") && !line.startsWith("---") ->
                            if (isDark) Color(0xFF3E181A) else Color(0xFFFEE2E2)
                        line.startsWith("@@") ->
                            if (isDark) Color(0xFF172B4D) else Color(0xFFE0F2FE)
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        line.startsWith("+") && !line.startsWith("+++") ->
                            if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
                        line.startsWith("-") && !line.startsWith("---") ->
                            if (isDark) GitSemanticColors.DeletedDark else GitSemanticColors.DeletedLight
                        line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = line.ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailSheet(
    detail: CommitDetail,
    onDismiss: () -> Unit,
    onViewFileDiff: (commitId: String, path: String) -> Unit,
    onCreateBranchFromCommit: (commitId: String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val commit = detail.commit

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = commit.shortId,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(commit.id)) }) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Копировать SHA",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = commit.fullMessage,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "${commit.authorName} <${commit.authorEmail}> • ${formatShortTimestamp(commit.timestampMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (commit.parentIds.isNotEmpty()) {
                Text(
                    text = "Родители: ${commit.parentIds.joinToString(", ") { it.take(7) }}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { onCreateBranchFromCommit(commit.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Создать ветку от этого коммита")
            }

            HorizontalDivider()

            Text(
                text = "Изменённые файлы (${detail.changedFiles.size})",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(detail.changedFiles) { change ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewFileDiff(commit.id, change.path) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GitChangeBadge(changeType = change.changeType)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = change.path,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                            if (change.oldPath != null) {
                                Text(
                                    text = "из ${change.oldPath}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GitChangeBadge(changeType: FileChangeType) {
    val isDark = isSystemInDarkTheme()
    val (label, color) = when (changeType) {
        FileChangeType.ADDED -> "A" to if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
        FileChangeType.MODIFIED -> "M" to if (isDark) GitSemanticColors.ModifiedDark else GitSemanticColors.ModifiedLight
        FileChangeType.DELETED -> "D" to if (isDark) GitSemanticColors.DeletedDark else GitSemanticColors.DeletedLight
        FileChangeType.UNTRACKED -> "U" to if (isDark) GitSemanticColors.UntrackedDark else GitSemanticColors.UntrackedLight
        FileChangeType.RENAMED -> "R" to if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
        FileChangeType.CONFLICTED -> "!" to if (isDark) GitSemanticColors.ConflictedDark else GitSemanticColors.ConflictedLight
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun CreateBranchDialog(
    startPointLabel: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, checkout: Boolean) -> Unit
) {
    var branchName by remember { mutableStateOf("") }
    var checkout by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AccountTree, contentDescription = null) },
        title = { Text("Новая ветка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "От базы: $startPointLabel",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    label = { Text("Имя ветки") },
                    placeholder = { Text("feature/my-change") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { checkout = !checkout }
                ) {
                    Checkbox(checked = checkout, onCheckedChange = { checkout = it })
                    Text("Сразу переключиться на ветку", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(branchName, checkout)
                    onDismiss()
                },
                enabled = branchName.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun RenameBranchDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать ветку") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Новое имя ветки") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onRename(newName)
                    onDismiss()
                },
                enabled = newName.isNotBlank() && newName.trim() != currentName
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun RemoteEditorDialog(
    existingRemote: GitRemote?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf(existingRemote?.name ?: "origin") }
    var url by remember { mutableStateOf(existingRemote?.fetchUrl ?: "") }
    val isEdit = existingRemote != null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        title = { Text(if (isEdit) "Редактировать remote" else "Добавить remote") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя remote") },
                    enabled = !isEdit,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Ссылка на Git-репозиторий") },
                    placeholder = { Text("https://github.com/user/repo.git") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, url)
                    onDismiss()
                },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun ProjectCredentialsDialog(
    currentCredentials: GitCredentials?,
    hasDefaultCredentials: Boolean,
    onDismiss: () -> Unit,
    onSave: (username: String, token: String) -> Unit
) {
    var username by remember { mutableStateOf(currentCredentials?.username ?: "") }
    var token by remember { mutableStateOf(currentCredentials?.tokenOrPassword ?: "") }
    var showToken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Key, contentDescription = null) },
        title = { Text("Авторизация репозитория") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (hasDefaultCredentials) {
                        "Оставьте поле токена пустым, чтобы использовать токен проекта или токен по умолчанию."
                    } else {
                        "Укажите логин и Personal Access Token (PAT) для fetch, pull и push."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Имя пользователя") },
                    placeholder = { Text("x-access-token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Personal Access Token") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(username, token)
                onDismiss()
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun AuthorConfigDialog(
    currentName: String,
    currentEmail: String,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String, saveGlobally: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }
    var saveGlobally by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
        title = { Text("Автор коммитов") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("user.name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("user.email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { saveGlobally = !saveGlobally }
                ) {
                    Checkbox(checked = saveGlobally, onCheckedChange = { saveGlobally = it })
                    Text("Сохранить также глобально", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, email, saveGlobally)
                    onDismiss()
                },
                enabled = name.isNotBlank() && email.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
