package com.pocketworker.app.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
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
import com.pocketworker.app.core.git.GitBranch
import com.pocketworker.app.core.git.GitFileChange
import com.pocketworker.app.core.git.GitRemote
import com.pocketworker.app.core.git.GitStashEntry
import com.pocketworker.app.core.git.GitTag
import com.pocketworker.app.core.i18n.LocalAppStrings
import com.pocketworker.app.ui.common.formatShortTimestamp

@Composable
fun GitControlTab(
    state: WorkspaceUiState,
    onInitGit: () -> Unit,
    onToggleViewAsTree: () -> Unit,
    onFetch: (String) -> Unit,
    onPull: (remote: String, rebase: Boolean) -> Unit,
    onPush: (remote: String, force: Boolean) -> Unit,
    onSync: (remote: String) -> Unit,
    onStageFile: (String) -> Unit,
    onStageAll: () -> Unit,
    onUnstageFile: (String) -> Unit,
    onUnstageAll: () -> Unit,
    onDiscardFile: (String) -> Unit,
    onDiscardAll: () -> Unit,
    onViewDiff: (relativePath: String, staged: Boolean) -> Unit,
    onCommit: (message: String, amend: Boolean, pushAfter: Boolean, stageAllFirst: Boolean) -> Unit,
    onCheckoutBranch: (String) -> Unit,
    onCreateBranch: (name: String, startPoint: String?, checkout: Boolean) -> Unit,
    onRenameBranch: (oldName: String, newName: String) -> Unit,
    onMergeBranch: (sourceBranch: String) -> Unit,
    onDeleteBranch: (branchName: String) -> Unit,
    onAddRemote: (name: String, url: String) -> Unit,
    onUpdateRemote: (name: String, newUrl: String) -> Unit,
    onRemoveRemote: (name: String) -> Unit,
    onCreateStash: (message: String?, includeUntracked: Boolean) -> Unit,
    onApplyStash: (index: Int) -> Unit,
    onPopStash: (index: Int) -> Unit,
    onDropStash: (index: Int) -> Unit,
    onClearStashes: () -> Unit,
    onCreateTag: (name: String, message: String?) -> Unit,
    onDeleteTag: (name: String) -> Unit,
    onPushTags: (remote: String) -> Unit,
    onClearGitOutputLogs: () -> Unit
) {
    val s = LocalAppStrings.current
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
                    text = s.gitNotInitializedTitle,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = s.gitNotInitializedSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onInitGit,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(s.gitInitButton)
                }
            }
        }
        return
    }

    var commitMessage by remember { mutableStateOf("") }
    var amendCommit by remember { mutableStateOf(false) }

    var stagedExpanded by remember { mutableStateOf(true) }
    var changesExpanded by remember { mutableStateOf(true) }
    var stashExpanded by remember { mutableStateOf(true) }

    var showActionSheet by remember { mutableStateOf(false) }
    var showBranchPicker by remember { mutableStateOf(false) }
    var showCommitMenu by remember { mutableStateOf(false) }

    var showCreateBranchDialog by remember { mutableStateOf(false) }
    var branchToRename by remember { mutableStateOf<GitBranch?>(null) }

    var showPullFromDialog by remember { mutableStateOf(false) }
    var showPushToDialog by remember { mutableStateOf(false) }

    var fileToDiscard by remember { mutableStateOf<GitFileChange?>(null) }
    var showDiscardAllConfirm by remember { mutableStateOf(false) }
    var forcePushRemote by remember { mutableStateOf<String?>(null) }

    var showAddRemoteDialog by remember { mutableStateOf(false) }
    var remoteToEdit by remember { mutableStateOf<GitRemote?>(null) }
    var remoteToDelete by remember { mutableStateOf<GitRemote?>(null) }

    var stashDialogIncludeUntracked by remember { mutableStateOf<Boolean?>(null) }
    var stashToDrop by remember { mutableStateOf<GitStashEntry?>(null) }
    var showClearStashesConfirm by remember { mutableStateOf(false) }

    var showCreateTagDialog by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<GitTag?>(null) }
    var showGitOutputDialog by remember { mutableStateOf(false) }

    val primaryRemote = state.remotes.firstOrNull { it.name == "origin" }?.name
        ?: state.remotes.firstOrNull()?.name
    val hasRemote = primaryRemote != null

    val canCommitStaged = commitMessage.isNotBlank() && (status.stagedChanges.isNotEmpty() || amendCommit)
    val canStageAndCommit = commitMessage.isNotBlank() &&
        (status.stagedChanges.isNotEmpty() || status.unstagedChanges.isNotEmpty())
    val canPrimaryCommit = commitMessage.isNotBlank() &&
        (status.stagedChanges.isNotEmpty() || status.unstagedChanges.isNotEmpty() || amendCommit)

    fun submitCommit(pushAfter: Boolean, stageAllFirst: Boolean) {
        val msg = commitMessage.trim()
        if (msg.isEmpty()) return
        onCommit(msg, amendCommit, pushAfter, stageAllFirst)
        commitMessage = ""
        amendCommit = false
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. VS Code-inspired Source Control header bar
        item(key = "sc_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Branch selector pill (opens Checkout to...)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .clickable { showBranchPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status.currentBranch ?: "HEAD",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (hasRemote && !status.isUnborn) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "↑${status.aheadCount}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (status.aheadCount > 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (status.aheadCount > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "↓${status.behindCount}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (status.behindCount > 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (status.behindCount > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            if (state.isAutoFetching) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    strokeWidth = 1.8.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = s.gitSelectBranchDesc,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // "..." VS Code More Actions button
                FilledTonalIconButton(
                    onClick = { showActionSheet = true },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = s.gitMenuDesc
                    )
                }
            }
        }

        // 2. Commit Message + Large Finger-Friendly Split Commit Button (VS Code style)
        item(key = "sc_commit_box") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    placeholder = { Text(s.gitCommitMessagePlaceholder) },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (amendCommit && !status.isUnborn) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { amendCommit = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = s.gitAmendBanner,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = s.gitDisableAmend,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Split Commit Button
                Box(modifier = Modifier.fillMaxWidth()) {
                    val primaryColor = if (canPrimaryCommit) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val contentColor = if (canPrimaryCommit) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }

                    val commitLabel = when {
                        amendCommit -> "Commit (Amend)"
                        status.stagedChanges.isNotEmpty() -> "Commit (${status.stagedChanges.size})"
                        status.unstagedChanges.isNotEmpty() -> "Commit All (${status.unstagedChanges.size})"
                        else -> "Commit"
                    }

                    Surface(
                        color = primaryColor,
                        contentColor = contentColor,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(enabled = canPrimaryCommit) {
                                        val shouldStageAll = !amendCommit && status.stagedChanges.isEmpty()
                                        submitCommit(pushAfter = false, stageAllFirst = shouldStageAll)
                                    },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = commitLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .padding(vertical = 10.dp)
                            ) {
                                Surface(
                                    color = contentColor.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxSize()
                                ) {}
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .width(54.dp)
                                    .fillMaxHeight()
                                    .clickable { showCommitMenu = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = s.gitCommitOptionsDesc,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = showCommitMenu,
                        onDismissRequest = { showCommitMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(s.gitCommitStaged) },
                            leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                            enabled = canCommitStaged,
                            onClick = {
                                showCommitMenu = false
                                submitCommit(pushAfter = false, stageAllFirst = false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Stage All & Commit") },
                            leadingIcon = { Icon(Icons.Rounded.DoneAll, contentDescription = null) },
                            enabled = canStageAndCommit,
                            onClick = {
                                showCommitMenu = false
                                submitCommit(pushAfter = false, stageAllFirst = true)
                            }
                        )
                        if (hasRemote) {
                            DropdownMenuItem(
                                text = { Text("Commit & Push") },
                                leadingIcon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) },
                                enabled = canPrimaryCommit,
                                onClick = {
                                    showCommitMenu = false
                                    val shouldStageAll = !amendCommit && status.stagedChanges.isEmpty()
                                    submitCommit(pushAfter = true, stageAllFirst = shouldStageAll)
                                }
                            )
                        }
                        if (!status.isUnborn) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (amendCommit) s.gitDisableAmend else s.gitCommitAmend)
                                },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    showCommitMenu = false
                                    amendCommit = !amendCommit
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Staged Changes Section
        if (status.stagedChanges.isNotEmpty()) {
            item(key = "sc_staged_section") {
                ChangesSectionCard(
                    title = s.gitStagedSectionTitle,
                    count = status.stagedChanges.size,
                    expanded = stagedExpanded,
                    onToggleExpanded = { stagedExpanded = !stagedExpanded },
                    headerActions = {
                        IconButton(
                            onClick = onUnstageAll,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = s.gitUnstageAllDesc,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    ChangesListOrTreeContent(
                        changes = status.stagedChanges,
                        asTree = state.viewChangesAsTree,
                        primaryActionIcon = Icons.Rounded.Remove,
                        primaryActionDesc = s.gitUnstageDesc,
                        onRowClick = { onViewDiff(it.path, true) },
                        onPrimaryAction = { onUnstageFile(it.path) },
                        onDiscardAction = null
                    )
                }
            }
        }

        // 4. Working Changes Section
        item(key = "sc_changes_section") {
            ChangesSectionCard(
                title = s.gitChangesSectionTitle,
                count = status.unstagedChanges.size,
                expanded = changesExpanded,
                onToggleExpanded = { changesExpanded = !changesExpanded },
                headerActions = {
                    if (status.unstagedChanges.isNotEmpty()) {
                        IconButton(
                            onClick = { showDiscardAllConfirm = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = s.gitDiscardAllDesc,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onStageAll,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = s.gitStageAllDesc,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            ) {
                if (status.unstagedChanges.isEmpty()) {
                    Text(
                        text = if (status.stagedChanges.isEmpty()) {
                            s.gitWorkingTreeClean
                        } else {
                            s.gitNoUnstagedChanges
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                    )
                } else {
                    ChangesListOrTreeContent(
                        changes = status.unstagedChanges,
                        asTree = state.viewChangesAsTree,
                        primaryActionIcon = Icons.Rounded.Add,
                        primaryActionDesc = s.gitStageDesc,
                        onRowClick = { onViewDiff(it.path, false) },
                        onPrimaryAction = { onStageFile(it.path) },
                        onDiscardAction = { fileToDiscard = it }
                    )
                }
            }
        }

        // 5. Stashes Section (shown cleanly whenever stashes exist)
        if (state.stashes.isNotEmpty()) {
            item(key = "sc_stashes_section") {
                ChangesSectionCard(
                    title = s.gitStashSectionTitle,
                    count = state.stashes.size,
                    expanded = stashExpanded,
                    onToggleExpanded = { stashExpanded = !stashExpanded },
                    headerActions = {
                        TextButton(onClick = { onPopStash(0) }) {
                            Text("Pop")
                        }
                    }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        state.stashes.forEachIndexed { idx, stash ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Archive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "stash@{${stash.index}} • ${stash.message}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatShortTimestamp(stash.timestampMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onPopStash(stash.index) },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VerticalAlignTop,
                                        contentDescription = "Pop stash",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { stashToDrop = stash },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = s.gitDeleteStashDesc,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // VS Code "..." Bottom Action Sheet
    if (showActionSheet) {
        GitActionSheet(
            state = state,
            status = status,
            primaryRemote = primaryRemote,
            amendCommit = amendCommit,
            canCommitStaged = canCommitStaged,
            canStageAndCommit = canStageAndCommit,
            onDismiss = { showActionSheet = false },
            onToggleViewAsTree = onToggleViewAsTree,
            onPull = onPull,
            onRequestPullFrom = { showPullFromDialog = true },
            onPush = onPush,
            onRequestPushTo = { showPushToDialog = true },
            onRequestForcePush = { remote -> forcePushRemote = remote },
            onFetch = onFetch,
            onSync = onSync,
            onCommitStaged = { pushAfter -> submitCommit(pushAfter = pushAfter, stageAllFirst = false) },
            onStageAndCommit = { submitCommit(pushAfter = false, stageAllFirst = true) },
            onToggleAmend = { amendCommit = !amendCommit },
            onStageAll = onStageAll,
            onUnstageAll = onUnstageAll,
            onRequestDiscardAll = { showDiscardAllConfirm = true },
            onAddRemoteClick = { showAddRemoteDialog = true },
            onEditRemoteClick = { remoteToEdit = it },
            onDeleteRemoteClick = { remoteToDelete = it },
            onOpenStashDialog = { includeUntracked ->
                stashDialogIncludeUntracked = includeUntracked
            },
            onPopStash = onPopStash,
            onApplyStash = onApplyStash,
            onDropStash = { stashToDrop = it },
            onRequestClearStashes = { showClearStashesConfirm = true },
            onCreateTagClick = { showCreateTagDialog = true },
            onDeleteTagClick = { tagToDelete = it },
            onPushTags = onPushTags,
            onShowGitOutput = { showGitOutputDialog = true }
        )
    }

    if (showBranchPicker) {
        BranchPickerSheet(
            branches = state.branches,
            isUnborn = status.isUnborn,
            onDismiss = { showBranchPicker = false },
            onSelectBranch = { branch ->
                if (!branch.isCurrent) {
                    onCheckoutBranch(branch.fullName)
                }
            },
            onCreateBranchClick = { showCreateBranchDialog = true },
            onRenameBranchClick = { branch -> branchToRename = branch }
        )
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

    if (showPullFromDialog) {
        RemoteSelectorDialog(
            title = "Pull from...",
            remotes = state.remotes,
            onDismiss = { showPullFromDialog = false },
            onSelect = { remote -> onPull(remote.name, false) }
        )
    }

    if (showPushToDialog) {
        RemoteSelectorDialog(
            title = "Push to...",
            remotes = state.remotes,
            onDismiss = { showPushToDialog = false },
            onSelect = { remote -> onPush(remote.name, false) }
        )
    }

    fileToDiscard?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDiscard = null },
            title = { Text(s.gitDiscardFileTitle) },
            text = {
                Text(s.gitDiscardFileMessage(file.path))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDiscardFile(file.path)
                        fileToDiscard = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(s.gitDiscardAction) }
            },
            dismissButton = {
                TextButton(onClick = { fileToDiscard = null }) { Text(s.commonCancel) }
            }
        )
    }

    if (showDiscardAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardAllConfirm = false },
            title = { Text(s.gitDiscardAllTitle) },
            text = {
                Text(s.gitDiscardAllMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDiscardAll()
                        showDiscardAllConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(s.gitDiscardAllAction) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardAllConfirm = false }) { Text(s.commonCancel) }
            }
        )
    }

    forcePushRemote?.let { remote ->
        AlertDialog(
            onDismissRequest = { forcePushRemote = null },
            title = { Text(s.gitForcePushTitle) },
            text = {
                Text(s.gitForcePushMessage(remote))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPush(remote, true)
                        forcePushRemote = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Force Push") }
            },
            dismissButton = {
                TextButton(onClick = { forcePushRemote = null }) { Text(s.commonCancel) }
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
            title = { Text(s.gitRemoveRemoteTitle(remote.name)) },
            text = { Text(s.gitRemoveRemoteMessage(remote.fetchUrl)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveRemote(remote.name)
                        remoteToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(s.commonDelete) }
            },
            dismissButton = {
                TextButton(onClick = { remoteToDelete = null }) { Text(s.commonCancel) }
            }
        )
    }

    stashDialogIncludeUntracked?.let { initialUntracked ->
        CreateStashDialog(
            initialIncludeUntracked = initialUntracked,
            onDismiss = { stashDialogIncludeUntracked = null },
            onCreate = { message, includeUntracked ->
                onCreateStash(message, includeUntracked)
            }
        )
    }

    stashToDrop?.let { stash ->
        AlertDialog(
            onDismissRequest = { stashToDrop = null },
            title = { Text(s.gitDropStashTitle(stash.index)) },
            text = { Text(s.gitDropStashMessage(stash.message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDropStash(stash.index)
                        stashToDrop = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(s.commonDelete) }
            },
            dismissButton = {
                TextButton(onClick = { stashToDrop = null }) { Text(s.commonCancel) }
            }
        )
    }

    if (showClearStashesConfirm) {
        AlertDialog(
            onDismissRequest = { showClearStashesConfirm = false },
            title = { Text(s.gitClearStashesTitle) },
            text = { Text(s.gitClearStashesMessage(state.stashes.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearStashes()
                        showClearStashesConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(s.commonClear) }
            },
            dismissButton = {
                TextButton(onClick = { showClearStashesConfirm = false }) { Text(s.commonCancel) }
            }
        )
    }

    if (showCreateTagDialog) {
        CreateTagDialog(
            onDismiss = { showCreateTagDialog = false },
            onCreate = { name, msg -> onCreateTag(name, msg) }
        )
    }

    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text(s.gitDeleteTagTitle(tag.name)) },
            text = { Text(s.gitDeleteTagMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTag(tag.name)
                        tagToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(s.commonDelete) }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) { Text(s.commonCancel) }
            }
        )
    }

    if (showGitOutputDialog) {
        GitOutputDialog(
            logs = state.gitOutputLogs,
            onClear = onClearGitOutputLogs,
            onDismiss = { showGitOutputDialog = false }
        )
    }
}

@Composable
private fun ChangesSectionCard(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    headerActions: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clickable(onClick = onToggleExpanded)
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.ExpandMore
                    } else {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = if (count > 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (count > 0) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                headerActions()
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun ChangesListOrTreeContent(
    changes: List<GitFileChange>,
    asTree: Boolean,
    primaryActionIcon: ImageVector,
    primaryActionDesc: String,
    onRowClick: (GitFileChange) -> Unit,
    onPrimaryAction: (GitFileChange) -> Unit,
    onDiscardAction: ((GitFileChange) -> Unit)?
) {
    if (!asTree) {
        Column(modifier = Modifier.fillMaxWidth()) {
            changes.forEachIndexed { idx, change ->
                if (idx > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
                WorkingFileRow(
                    change = change,
                    showParentDir = true,
                    indentStart = false,
                    onRowClick = { onRowClick(change) },
                    primaryActionIcon = primaryActionIcon,
                    primaryActionDesc = primaryActionDesc,
                    onPrimaryAction = { onPrimaryAction(change) },
                    onDiscardAction = onDiscardAction?.let { cb -> { cb(change) } }
                )
            }
        }
    } else {
        val grouped = remember(changes) {
            changes.groupBy { change ->
                change.path.substringBeforeLast('/', "")
            }.toSortedMap(compareBy<String> { it.isNotEmpty() }.thenBy { it.lowercase() })
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            grouped.entries.forEachIndexed { groupIdx, (folder, items) ->
                if (groupIdx > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
                if (folder.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = folder,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                items.forEach { change ->
                    WorkingFileRow(
                        change = change,
                        showParentDir = false,
                        indentStart = folder.isNotEmpty(),
                        onRowClick = { onRowClick(change) },
                        primaryActionIcon = primaryActionIcon,
                        primaryActionDesc = primaryActionDesc,
                        onPrimaryAction = { onPrimaryAction(change) },
                        onDiscardAction = onDiscardAction?.let { cb -> { cb(change) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkingFileRow(
    change: GitFileChange,
    showParentDir: Boolean,
    indentStart: Boolean,
    onRowClick: () -> Unit,
    primaryActionIcon: ImageVector,
    primaryActionDesc: String,
    onPrimaryAction: () -> Unit,
    onDiscardAction: (() -> Unit)?
) {
    val fileName = change.path.substringAfterLast('/')
    val parentDir = change.path.substringBeforeLast('/', "")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onRowClick)
            .padding(
                start = if (indentStart) 28.dp else 14.dp,
                end = 6.dp,
                top = 6.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GitChangeBadge(changeType = change.changeType)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showParentDir && parentDir.isNotEmpty()) {
                Text(
                    text = parentDir,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onDiscardAction != null) {
            IconButton(
                onClick = onDiscardAction,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = LocalAppStrings.current.gitDiscardAction,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        IconButton(
            onClick = onPrimaryAction,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = primaryActionIcon,
                contentDescription = primaryActionDesc,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
