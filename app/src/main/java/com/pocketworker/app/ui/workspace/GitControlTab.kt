package com.pocketworker.app.ui.workspace

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketworker.app.core.git.GitBranch
import com.pocketworker.app.core.git.GitFileChange
import com.pocketworker.app.core.git.GitRemote
import com.pocketworker.app.core.git.GitStatusSummary

@Composable
fun GitControlTab(
    state: WorkspaceUiState,
    onInitGit: () -> Unit,
    onFetch: (String) -> Unit,
    onPull: (String) -> Unit,
    onPush: (String) -> Unit,
    onStageFile: (String) -> Unit,
    onStageAll: () -> Unit,
    onUnstageFile: (String) -> Unit,
    onUnstageAll: () -> Unit,
    onDiscardFile: (String) -> Unit,
    onViewDiff: (relativePath: String, staged: Boolean) -> Unit,
    onCommit: (message: String, amend: Boolean, pushAfter: Boolean) -> Unit,
    onCheckoutBranch: (String) -> Unit,
    onCreateBranch: (name: String, startPoint: String?, checkout: Boolean) -> Unit,
    onRenameBranch: (oldName: String, newName: String) -> Unit,
    onMergeBranch: (sourceBranch: String) -> Unit,
    onDeleteBranch: (branchName: String) -> Unit,
    onAddRemote: (name: String, url: String) -> Unit,
    onUpdateRemote: (name: String, newUrl: String) -> Unit,
    onRemoveRemote: (name: String) -> Unit,
    onOpenCredentialsDialog: () -> Unit,
    onOpenAuthorDialog: () -> Unit
) {
    val status = state.gitStatus
    if (status == null || !status.isGitRepo) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Git не инициализирован",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Этот проект был создан без Git. Инициализируйте репозиторий, чтобы управлять ветками, коммитами и синхронизацией.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onInitGit) {
                    Text("Инициализировать Git (ветка main)")
                }
            }
        }
        return
    }

    var commitMessage by remember { mutableStateOf("") }
    var amendCommit by remember { mutableStateOf(false) }

    var showCreateBranchDialog by remember { mutableStateOf(false) }
    var branchToRename by remember { mutableStateOf<GitBranch?>(null) }
    var branchToDelete by remember { mutableStateOf<GitBranch?>(null) }
    var branchToMerge by remember { mutableStateOf<GitBranch?>(null) }
    var fileToDiscard by remember { mutableStateOf<GitFileChange?>(null) }

    var showAddRemoteDialog by remember { mutableStateOf(false) }
    var remoteToEdit by remember { mutableStateOf<GitRemote?>(null) }
    var remoteToDelete by remember { mutableStateOf<GitRemote?>(null) }

    val primaryRemote = state.remotes.firstOrNull { it.name == "origin" }?.name
        ?: state.remotes.firstOrNull()?.name
    val localBranches = state.branches.filter { !it.isRemote }
    val remoteBranches = state.branches.filter { it.isRemote }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Status & Cloud Sync Card
        item {
            SyncStatusCard(
                status = status,
                primaryRemote = primaryRemote,
                onFetch = { primaryRemote?.let(onFetch) },
                onPull = { primaryRemote?.let(onPull) },
                onPush = { primaryRemote?.let(onPush) }
            )
        }

        // 2. Working Tree Changes & Commit Card
        item {
            ChangesAndCommitCard(
                status = status,
                commitMessage = commitMessage,
                onCommitMessageChange = { commitMessage = it },
                amend = amendCommit,
                onAmendChange = { amendCommit = it },
                hasRemote = primaryRemote != null,
                onStageFile = onStageFile,
                onStageAll = onStageAll,
                onUnstageFile = onUnstageFile,
                onUnstageAll = onUnstageAll,
                onRequestDiscard = { fileToDiscard = it },
                onViewDiff = onViewDiff,
                onCommit = { pushAfter ->
                    val msg = commitMessage.trim()
                    if (msg.isNotEmpty()) {
                        onCommit(msg, amendCommit, pushAfter)
                        commitMessage = ""
                        amendCommit = false
                    }
                }
            )
        }

        // 3. Branches Card
        item {
            BranchesSectionCard(
                currentBranch = status.currentBranch,
                isUnborn = status.isUnborn,
                localBranches = localBranches,
                remoteBranches = remoteBranches,
                onNewBranchClick = { showCreateBranchDialog = true },
                onCheckoutBranch = onCheckoutBranch,
                onRenameBranch = { branchToRename = it },
                onMergeBranch = { branchToMerge = it },
                onDeleteBranch = { branchToDelete = it }
            )
        }

        // 4. Cloud Remotes & Settings Card
        item {
            RemotesAndSettingsCard(
                remotes = state.remotes,
                status = status,
                hasProjectCredentials = state.repoCredentials != null,
                hasDefaultCredentials = state.hasFallbackCredentials,
                onAddRemoteClick = { showAddRemoteDialog = true },
                onEditRemoteClick = { remoteToEdit = it },
                onDeleteRemoteClick = { remoteToDelete = it },
                onFetchRemote = onFetch,
                onPushRemote = onPush,
                onOpenCredentialsDialog = onOpenCredentialsDialog,
                onOpenAuthorDialog = onOpenAuthorDialog
            )
        }
    }

    if (showCreateBranchDialog) {
        CreateBranchDialog(
            startPointLabel = status.currentBranch ?: "HEAD",
            onDismiss = { showCreateBranchDialog = false },
            onCreate = { name, checkout -> onCreateBranch(name, null, checkout) }
        )
    }

    branchToRename?.let { branch ->
        RenameBranchDialog(
            currentName = branch.name,
            onDismiss = { branchToRename = null },
            onRename = { newName -> onRenameBranch(branch.name, newName) }
        )
    }

    branchToDelete?.let { branch ->
        AlertDialog(
            onDismissRequest = { branchToDelete = null },
            title = { Text("Удалить ветку «${branch.name}»?") },
            text = { Text("Локальная ветка будет удалена. Неслитые изменения могут быть потеряны.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBranch(branch.name)
                        branchToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { branchToDelete = null }) { Text("Отмена") }
            }
        )
    }

    branchToMerge?.let { branch ->
        AlertDialog(
            onDismissRequest = { branchToMerge = null },
            title = { Text("Слить ветку?") },
            text = {
                Text("Влить изменения из «${branch.name}» в текущую ветку «${status.currentBranch}»?")
            },
            confirmButton = {
                Button(onClick = {
                    onMergeBranch(branch.name)
                    branchToMerge = null
                }) { Text("Выполнить Merge") }
            },
            dismissButton = {
                TextButton(onClick = { branchToMerge = null }) { Text("Отмена") }
            }
        )
    }

    fileToDiscard?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDiscard = null },
            title = { Text("Откатить изменения?") },
            text = {
                Text("Все несохранённые изменения в файле «${file.path}» будут потеряны.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDiscardFile(file.path)
                        fileToDiscard = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Откатить") }
            },
            dismissButton = {
                TextButton(onClick = { fileToDiscard = null }) { Text("Отмена") }
            }
        )
    }

    if (showAddRemoteDialog) {
        RemoteEditorDialog(
            existingRemote = null,
            onDismiss = { showAddRemoteDialog = false },
            onSave = onAddRemote
        )
    }

    remoteToEdit?.let { remote ->
        RemoteEditorDialog(
            existingRemote = remote,
            onDismiss = { remoteToEdit = null },
            onSave = { name, url -> onUpdateRemote(name, url) }
        )
    }

    remoteToDelete?.let { remote ->
        AlertDialog(
            onDismissRequest = { remoteToDelete = null },
            title = { Text("Удалить remote «${remote.name}»?") },
            text = { Text("Ссылка ${remote.fetchUrl} будет отвязана от локального проекта.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveRemote(remote.name)
                        remoteToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { remoteToDelete = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun SyncStatusCard(
    status: GitStatusSummary,
    primaryRemote: String?,
    onFetch: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AccountTree,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = status.currentBranch ?: "HEAD",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = when {
                                status.isUnborn -> "Ветка без коммитов"
                                status.isDetachedHead -> "Detached HEAD"
                                primaryRemote != null -> "Remote: $primaryRemote (↑${status.aheadCount} ↓${status.behindCount})"
                                else -> "Локальный репозиторий (remote не привязан)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (primaryRemote != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onFetch,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fetch")
                    }
                    OutlinedButton(
                        onClick = onPull,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pull")
                    }
                    Button(
                        onClick = onPush,
                        enabled = !status.isUnborn,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Push")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangesAndCommitCard(
    status: GitStatusSummary,
    commitMessage: String,
    onCommitMessageChange: (String) -> Unit,
    amend: Boolean,
    onAmendChange: (Boolean) -> Unit,
    hasRemote: Boolean,
    onStageFile: (String) -> Unit,
    onStageAll: () -> Unit,
    onUnstageFile: (String) -> Unit,
    onUnstageAll: () -> Unit,
    onRequestDiscard: (GitFileChange) -> Unit,
    onViewDiff: (String, Boolean) -> Unit,
    onCommit: (pushAfter: Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Изменения и коммит",
                style = MaterialTheme.typography.titleMedium
            )

            // Staged section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Подготовлено (Staged): ${status.stagedChanges.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (status.stagedChanges.isNotEmpty()) {
                    TextButton(onClick = onUnstageAll) {
                        Text("Убрать все")
                    }
                }
            }

            if (status.stagedChanges.isEmpty()) {
                Text(
                    text = "Нет файлов в индексе",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                status.stagedChanges.forEach { change ->
                    WorkingFileRow(
                        change = change,
                        onRowClick = { onViewDiff(change.path, true) },
                        primaryActionIcon = Icons.Rounded.Remove,
                        primaryActionDesc = "Убрать из индекса",
                        onPrimaryAction = { onUnstageFile(change.path) },
                        onDiscardAction = null
                    )
                }
            }

            HorizontalDivider()

            // Unstaged section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Рабочие изменения: ${status.unstagedChanges.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (status.unstagedChanges.isNotEmpty()) {
                    TextButton(onClick = onStageAll) {
                        Text("Добавить все")
                    }
                }
            }

            if (status.unstagedChanges.isEmpty()) {
                Text(
                    text = "Рабочая директория чиста",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                status.unstagedChanges.forEach { change ->
                    WorkingFileRow(
                        change = change,
                        onRowClick = { onViewDiff(change.path, false) },
                        primaryActionIcon = Icons.Rounded.Add,
                        primaryActionDesc = "Добавить в индекс",
                        onPrimaryAction = { onStageFile(change.path) },
                        onDiscardAction = { onRequestDiscard(change) }
                    )
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = commitMessage,
                onValueChange = onCommitMessageChange,
                label = { Text("Сообщение коммита") },
                placeholder = { Text("Опишите внесённые изменения...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            if (!status.isUnborn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onAmendChange(!amend) }
                ) {
                    Checkbox(checked = amend, onCheckedChange = onAmendChange)
                    Text("Amend (дополнить последний коммит)", style = MaterialTheme.typography.bodyMedium)
                }
            }

            val canCommit = commitMessage.isNotBlank() && (status.stagedChanges.isNotEmpty() || amend)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onCommit(false) },
                    enabled = canCommit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Commit")
                }
                if (hasRemote) {
                    OutlinedButton(
                        onClick = { onCommit(true) },
                        enabled = canCommit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Commit & Push")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkingFileRow(
    change: GitFileChange,
    onRowClick: () -> Unit,
    primaryActionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryActionDesc: String,
    onPrimaryAction: () -> Unit,
    onDiscardAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GitChangeBadge(changeType = change.changeType)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = change.path,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onDiscardAction != null) {
            IconButton(onClick = onDiscardAction, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = "Откатить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        IconButton(onClick = onPrimaryAction, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = primaryActionIcon,
                contentDescription = primaryActionDesc,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BranchesSectionCard(
    currentBranch: String?,
    isUnborn: Boolean,
    localBranches: List<GitBranch>,
    remoteBranches: List<GitBranch>,
    onNewBranchClick: () -> Unit,
    onCheckoutBranch: (String) -> Unit,
    onRenameBranch: (GitBranch) -> Unit,
    onMergeBranch: (GitBranch) -> Unit,
    onDeleteBranch: (GitBranch) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ветки",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(
                    onClick = onNewBranchClick,
                    enabled = !isUnborn,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Новая")
                }
            }

            if (localBranches.isEmpty()) {
                Text(
                    text = if (isUnborn) {
                        "Текущая ветка: ${currentBranch ?: "main"} (создастся после первого коммита)"
                    } else {
                        "Локальных веток нет"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Локальные (${localBranches.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                localBranches.forEach { branch ->
                    BranchRowItem(
                        branch = branch,
                        onCheckout = { onCheckoutBranch(branch.fullName) },
                        onRename = { onRenameBranch(branch) },
                        onMerge = { onMergeBranch(branch) },
                        onDelete = { onDeleteBranch(branch) }
                    )
                }
            }

            if (remoteBranches.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Удалённые (${remoteBranches.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                remoteBranches.forEach { branch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCheckoutBranch(branch.fullName) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = branch.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Checkout",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchRowItem(
    branch: GitBranch,
    onCheckout: () -> Unit,
    onRename: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = if (branch.isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !branch.isCurrent, onClick = onCheckout)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (branch.isCurrent) Icons.Rounded.Check else Icons.Rounded.AccountTree,
                contentDescription = null,
                tint = if (branch.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = branch.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (branch.isCurrent) FontWeight.Bold else FontWeight.Normal
                )
                if (branch.upstreamName != null) {
                    Text(
                        text = "-> ${branch.upstreamName} (↑${branch.aheadCount} ↓${branch.behindCount})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Действия с веткой",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (!branch.isCurrent) {
                        DropdownMenuItem(
                            text = { Text("Переключиться (Checkout)") },
                            onClick = {
                                menuExpanded = false
                                onCheckout()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Влить в текущую (Merge)") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.CallMerge, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMerge()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    if (!branch.isCurrent) {
                        DropdownMenuItem(
                            text = { Text("Удалить ветку") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemotesAndSettingsCard(
    remotes: List<GitRemote>,
    status: GitStatusSummary,
    hasProjectCredentials: Boolean,
    hasDefaultCredentials: Boolean,
    onAddRemoteClick: () -> Unit,
    onEditRemoteClick: (GitRemote) -> Unit,
    onDeleteRemoteClick: (GitRemote) -> Unit,
    onFetchRemote: (String) -> Unit,
    onPushRemote: (String) -> Unit,
    onOpenCredentialsDialog: () -> Unit,
    onOpenAuthorDialog: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Облачный Git (Remotes)",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(
                    onClick = onAddRemoteClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить")
                }
            }

            if (remotes.isEmpty()) {
                Text(
                    text = "Удалённые репозитории не настроены. Нажмите «Добавить», чтобы привязать GitHub/GitLab URL.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                remotes.forEach { remote ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cloud,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = remote.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onEditRemoteClick(remote) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Изменить", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onDeleteRemoteClick(remote) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить", modifier = Modifier.size(18.dp))
                                }
                            }
                            Text(
                                text = remote.fetchUrl,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onFetchRemote(remote.name) }) {
                                    Text("Fetch ${remote.name}")
                                }
                                TextButton(
                                    onClick = { onPushRemote(remote.name) },
                                    enabled = !status.isUnborn
                                ) {
                                    Text("Push ${remote.name}")
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCredentialsDialog)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Токен доступа (PAT / пароль)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when {
                            hasProjectCredentials -> "Задан отдельный токен репозитория"
                            hasDefaultCredentials -> "Используется токен проекта или по умолчанию"
                            else -> "Не задан (только публичное чтение)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAuthorDialog)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Автор коммитов", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${status.authorName} <${status.authorEmail}>",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
