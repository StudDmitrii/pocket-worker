package com.pocketworker.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketworker.app.core.git.CommitDetail
import com.pocketworker.app.core.git.FileChangeType
import com.pocketworker.app.core.git.GitBranch
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitRemote
import com.pocketworker.app.core.i18n.LocalAppStrings
import com.pocketworker.app.ui.common.FormFieldShape
import com.pocketworker.app.ui.common.FormOptionCard
import com.pocketworker.app.ui.common.PocketFormDialog
import com.pocketworker.app.ui.common.appSwitchColors
import com.pocketworker.app.ui.common.formTextFieldColors
import com.pocketworker.app.ui.common.formatShortTimestamp
import com.pocketworker.app.ui.theme.GitSemanticColors
import com.pocketworker.app.ui.theme.LocalIsDarkTheme

@Composable
fun DiffViewerDialog(
    state: DiffDialogState,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
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
                Text(LocalAppStrings.current.commonClose)
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
    val s = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current
    val commit = detail.commit
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
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
                        contentDescription = s.gitCopyShaDesc,
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
                    text = s.gitCommitParents(commit.parentIds.joinToString(", ") { it.take(7) }),
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
                Text(s.gitCreateBranchFromCommit)
            }

            HorizontalDivider()

            Text(
                text = s.gitChangedFilesHeader(detail.changedFiles.size),
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
                                    text = s.gitRenamedFrom(change.oldPath),
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
    val isDark = LocalIsDarkTheme.current
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
    val s = LocalAppStrings.current
    var branchName by remember { mutableStateOf("") }
    var checkout by remember { mutableStateOf(true) }

    PocketFormDialog(
        title = s.gitNewBranchTitle,
        subtitle = s.gitNewBranchBase(startPointLabel),
        icon = Icons.Rounded.AccountTree,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            Button(
                onClick = {
                    onCreate(branchName, checkout)
                    onDismiss()
                },
                enabled = branchName.isNotBlank(),
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(s.commonCreate)
            }
        }
    ) {
        OutlinedTextField(
            value = branchName,
            onValueChange = { branchName = it },
            label = { Text(s.gitBranchNameLabel) },
            placeholder = { Text("feature/my-change") },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        FormOptionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { checkout = !checkout }
            ) {
                Text(
                    text = s.gitCheckoutImmediately,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = checkout,
                    onCheckedChange = { checkout = it },
                    colors = appSwitchColors()
                )
            }
        }
    }
}

@Composable
fun RenameBranchDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    val s = LocalAppStrings.current
    var newName by remember { mutableStateOf(currentName) }

    PocketFormDialog(
        title = s.gitRenameBranchTitle,
        subtitle = s.gitRenameBranchCurrent(currentName),
        icon = Icons.Rounded.DriveFileRenameOutline,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            Button(
                onClick = {
                    onRename(newName)
                    onDismiss()
                },
                enabled = newName.isNotBlank() && newName.trim() != currentName,
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(s.commonSave)
            }
        }
    ) {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text(s.gitNewBranchNameLabel) },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun RemoteEditorDialog(
    existingRemote: GitRemote?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String) -> Unit
) {
    val s = LocalAppStrings.current
    var name by remember { mutableStateOf(existingRemote?.name ?: "origin") }
    var url by remember { mutableStateOf(existingRemote?.fetchUrl ?: "") }
    val isEdit = existingRemote != null

    PocketFormDialog(
        title = if (isEdit) s.gitEditRemoteTitle else s.gitAddRemoteTitle.removeSuffix("..."),
        subtitle = s.gitRemoteDialogSubtitle,
        icon = Icons.Rounded.Cloud,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            Button(
                onClick = {
                    onSave(name, url)
                    onDismiss()
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(s.commonSave)
            }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(s.gitRemoteNameLabel) },
            enabled = !isEdit,
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(s.repoUrlLabel) },
            placeholder = { Text("https://github.com/user/repo.git") },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ProjectCredentialsDialog(
    currentCredentials: GitCredentials?,
    hasDefaultCredentials: Boolean,
    onDismiss: () -> Unit,
    onSave: (username: String, token: String) -> Unit
) {
    val s = LocalAppStrings.current
    var username by remember { mutableStateOf(currentCredentials?.username ?: "") }
    var token by remember { mutableStateOf(currentCredentials?.tokenOrPassword ?: "") }
    var showToken by remember { mutableStateOf(false) }

    PocketFormDialog(
        title = s.gitRepoAuthTitle,
        subtitle = s.gitRepoAuthSubtitle,
        icon = Icons.Rounded.Key,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            Button(
                onClick = {
                    onSave(username, token)
                    onDismiss()
                },
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(s.commonSave)
            }
        }
    ) {
        Text(
            text = if (hasDefaultCredentials) {
                s.gitRepoAuthFallbackHint
            } else {
                s.gitRepoAuthDirectHint
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(s.commonUsername) },
            placeholder = { Text("x-access-token") },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal Access Token") },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
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
}

@Composable
fun AuthorConfigDialog(
    currentName: String,
    currentEmail: String,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String, saveGlobally: Boolean) -> Unit,
    onResetToDefault: (() -> Unit)? = null
) {
    val s = LocalAppStrings.current
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }
    var saveGlobally by remember { mutableStateOf(false) }

    PocketFormDialog(
        title = s.gitAuthorTitle,
        subtitle = s.gitAuthorRepoSubtitle,
        icon = Icons.Rounded.Person,
        onDismiss = onDismiss,
        footerActions = {
            if (onResetToDefault != null) {
                TextButton(
                    onClick = {
                        onResetToDefault()
                        onDismiss()
                    }
                ) {
                    Text(s.commonReset, color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            Button(
                onClick = {
                    onSave(name, email, saveGlobally)
                    onDismiss()
                },
                enabled = name.isNotBlank() && email.isNotBlank(),
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(s.commonSave)
            }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("user.name") },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("user.email") },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        FormOptionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { saveGlobally = !saveGlobally }
            ) {
                Text(
                    text = s.gitAuthorSaveGlobally,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = saveGlobally,
                    onCheckedChange = { saveGlobally = it },
                    colors = appSwitchColors()
                )
            }
        }
    }
}

@Composable
fun CreateStashDialog(
    initialIncludeUntracked: Boolean = true,
    onDismiss: () -> Unit,
    onCreate: (message: String, includeUntracked: Boolean) -> Unit
) {
    val s = LocalAppStrings.current
    var message by remember { mutableStateOf("") }
    var includeUntracked by remember { mutableStateOf(initialIncludeUntracked) }

    PocketFormDialog(
        title = s.gitStashDialogTitle,
        subtitle = s.gitStashDialogSubtitle,
        icon = Icons.Rounded.Archive,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            Button(
                onClick = {
                    onCreate(message, includeUntracked)
                    onDismiss()
                },
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(s.gitStashAction)
            }
        }
    ) {
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text(s.gitStashMessageLabel) },
            placeholder = { Text(s.gitStashMessagePlaceholder) },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        FormOptionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { includeUntracked = !includeUntracked }
            ) {
                Text(
                    text = s.gitStashIncludeUntrackedOption,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = includeUntracked,
                    onCheckedChange = { includeUntracked = it },
                    colors = appSwitchColors()
                )
            }
        }
    }
}

@Composable
fun CreateTagDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, message: String) -> Unit
) {
    val s = LocalAppStrings.current
    var tagName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    PocketFormDialog(
        title = s.gitTagDialogTitle,
        subtitle = s.gitTagDialogSubtitle,
        icon = Icons.Rounded.Sell,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            Button(
                onClick = {
                    onCreate(tagName, message)
                    onDismiss()
                },
                enabled = tagName.isNotBlank(),
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(s.commonCreate)
            }
        }
    ) {
        OutlinedTextField(
            value = tagName,
            onValueChange = { tagName = it },
            label = { Text(s.gitTagNameLabel) },
            placeholder = { Text("v1.0.0") },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text(s.gitTagAnnotationLabel) },
            placeholder = { Text(s.gitTagAnnotationPlaceholder) },
            minLines = 2,
            maxLines = 4,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchPickerSheet(
    branches: List<GitBranch>,
    isUnborn: Boolean,
    onDismiss: () -> Unit,
    onSelectBranch: (GitBranch) -> Unit,
    onCreateBranchClick: () -> Unit,
    onRenameBranchClick: (GitBranch) -> Unit
) {
    val s = LocalAppStrings.current
    var search by remember { mutableStateOf("") }
    val filtered = remember(branches, search) {
        if (search.isBlank()) {
            branches
        } else {
            val q = search.trim().lowercase()
            branches.filter { it.name.lowercase().contains(q) }
        }
    }
    val local = filtered.filter { !it.isRemote }
    val remote = filtered.filter { it.isRemote }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = s.gitSwitchBranchTitle,
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onCreateBranchClick()
                    },
                    enabled = !isUnborn,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(s.gitNewBranchShort)
                }
            }

            if (branches.size > 4) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(s.gitSearchBranchPlaceholder) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (local.isNotEmpty()) {
                    item(key = "header_local") {
                        Text(
                            text = s.gitLocalBranches,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(local, key = { it.fullName }) { branch ->
                        Surface(
                            color = if (branch.isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectBranch(branch)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (branch.isCurrent) Icons.Rounded.Check else Icons.Rounded.AccountTree,
                                    contentDescription = null,
                                    tint = if (branch.isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = branch.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (branch.isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (branch.upstreamName != null) {
                                        Text(
                                            text = "${branch.upstreamName} (↑${branch.aheadCount} ↓${branch.behindCount})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onRenameBranchClick(branch)
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DriveFileRenameOutline,
                                        contentDescription = s.gitRenameBranchDesc(branch.name),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (remote.isNotEmpty()) {
                    item(key = "header_remote") {
                        Text(
                            text = s.gitRemoteBranches,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(remote, key = { it.fullName }) { branch ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectBranch(branch)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cloud,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = branch.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "empty_branches") {
                        Text(
                            text = if (isUnborn) {
                                s.gitUnbornBranchHint
                            } else {
                                s.gitNoMatchingBranches
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BranchSelectorDialog(
    title: String,
    emptyText: String,
    branches: List<GitBranch>,
    onDismiss: () -> Unit,
    onSelect: (GitBranch) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AccountTree, contentDescription = null) },
        title = { Text(title) },
        text = {
            if (branches.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(branches, key = { it.fullName }) { branch ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(branch)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountTree,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = branch.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(LocalAppStrings.current.commonClose) }
        }
    )
}

@Composable
fun GitOutputDialog(
    logs: List<GitOutputLogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Git Output")
                if (logs.isNotEmpty()) {
                    Row {
                        IconButton(
                            onClick = {
                                val fullLog = logs.joinToString("\n\n") { entry ->
                                    "[${formatShortTimestamp(entry.timestampMillis)}] $ ${entry.command}\n${entry.output}"
                                }
                                clipboard.setText(AnnotatedString(fullLog))
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = s.gitCopyLogDesc)
                        }
                        IconButton(onClick = onClear) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = s.gitClearLogDesc)
                        }
                    }
                }
            }
        },
        text = {
            if (logs.isEmpty()) {
                Text(
                    text = s.gitOutputEmptyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs) { entry ->
                        Surface(
                            color = if (entry.isError) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${formatShortTimestamp(entry.timestampMillis)} • ${entry.command}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (entry.isError) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                                Text(
                                    text = entry.output,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.commonClose) }
        }
    )
}

@Composable
fun RemoteSelectorDialog(
    title: String,
    remotes: List<GitRemote>,
    onDismiss: () -> Unit,
    onSelect: (GitRemote) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(remotes, key = { it.name }) { remote ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onSelect(remote)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = remote.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = remote.fetchUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LocalAppStrings.current.commonCancel) }
        }
    )
}
