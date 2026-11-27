package com.pocketworker.app.ui.workspace

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketworker.app.core.git.GitRemote
import com.pocketworker.app.core.git.GitStashEntry
import com.pocketworker.app.core.git.GitStatusSummary
import com.pocketworker.app.core.git.GitTag
import com.pocketworker.app.core.i18n.AppStrings
import com.pocketworker.app.core.i18n.LocalAppStrings
import com.pocketworker.app.ui.common.appSwitchColors
import com.pocketworker.app.ui.common.formatShortTimestamp

enum class GitActionSubmenu {
    ROOT,
    COMMIT,
    CHANGES,
    PULL_PUSH,
    REMOTE,
    STASH,
    TAGS;

    fun localizedTitle(s: AppStrings): String = when (this) {
        ROOT -> s.gitSubmenuRoot
        COMMIT -> s.gitSubmenuCommit
        CHANGES -> s.gitSubmenuChanges
        PULL_PUSH -> s.gitSubmenuPullPush
        REMOTE -> s.gitSubmenuRemote
        STASH -> s.gitSubmenuStash
        TAGS -> s.gitSubmenuTags
    }
}

/**
 * Finger-friendly VS Code-inspired Git "..." action sheet with categorized submenus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitActionSheet(
    state: WorkspaceUiState,
    status: GitStatusSummary,
    primaryRemote: String?,
    amendCommit: Boolean,
    canCommitStaged: Boolean,
    canStageAndCommit: Boolean,
    onDismiss: () -> Unit,
    onToggleViewAsTree: () -> Unit,
    onPull: (remote: String, rebase: Boolean) -> Unit,
    onRequestPullFrom: () -> Unit,
    onPush: (remote: String, force: Boolean) -> Unit,
    onRequestPushTo: () -> Unit,
    onRequestForcePush: (remote: String) -> Unit,
    onFetch: (remote: String) -> Unit,
    onSync: (remote: String) -> Unit,
    onCommitStaged: (pushAfter: Boolean) -> Unit,
    onStageAndCommit: () -> Unit,
    onToggleAmend: () -> Unit,
    onStageAll: () -> Unit,
    onUnstageAll: () -> Unit,
    onRequestDiscardAll: () -> Unit,
    onAddRemoteClick: () -> Unit,
    onEditRemoteClick: (GitRemote) -> Unit,
    onDeleteRemoteClick: (GitRemote) -> Unit,
    onOpenStashDialog: (includeUntracked: Boolean) -> Unit,
    onPopStash: (index: Int) -> Unit,
    onApplyStash: (index: Int) -> Unit,
    onDropStash: (GitStashEntry) -> Unit,
    onRequestClearStashes: () -> Unit,
    onCreateTagClick: () -> Unit,
    onDeleteTagClick: (GitTag) -> Unit,
    onPushTags: (remote: String) -> Unit,
    onShowGitOutput: () -> Unit
) {
    val s = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentMenu by remember { mutableStateOf(GitActionSubmenu.ROOT) }

    val hasRemote = primaryRemote != null
    val hasChanges = status.stagedChanges.isNotEmpty() || status.unstagedChanges.isNotEmpty()
    val hasStashes = state.stashes.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentMenu != GitActionSubmenu.ROOT) {
                    IconButton(
                        onClick = { currentMenu = GitActionSubmenu.ROOT },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = s.commonBack
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentMenu.localizedTitle(s),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(s.gitBranchPrefix(status.currentBranch ?: "HEAD"))
                            if (primaryRemote != null) append(" • $primaryRemote")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = s.commonClose)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            AnimatedContent(
                targetState = currentMenu,
                transitionSpec = {
                    val forward = targetState != GitActionSubmenu.ROOT
                    if (forward) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                },
                label = "git_menu_level"
            ) { menu ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (menu) {
                        GitActionSubmenu.ROOT -> {
                            item(key = "quick_tiles") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QuickGitTile(
                                        icon = Icons.Rounded.CloudDownload,
                                        label = "Pull",
                                        enabled = hasRemote,
                                        onClick = {
                                            onDismiss()
                                            primaryRemote?.let { onPull(it, false) }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    QuickGitTile(
                                        icon = Icons.Rounded.CloudUpload,
                                        label = "Push",
                                        enabled = hasRemote && !status.isUnborn,
                                        onClick = {
                                            onDismiss()
                                            primaryRemote?.let { onPush(it, false) }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    QuickGitTile(
                                        icon = Icons.Rounded.CloudSync,
                                        label = "Fetch",
                                        enabled = hasRemote,
                                        onClick = {
                                            onDismiss()
                                            primaryRemote?.let(onFetch)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    QuickGitTile(
                                        icon = Icons.Rounded.Sync,
                                        label = "Sync",
                                        enabled = hasRemote,
                                        onClick = {
                                            onDismiss()
                                            primaryRemote?.let(onSync)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            item(key = "view_as_tree") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onToggleViewAsTree)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MenuIconBadge(icon = Icons.Rounded.FolderCopy)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = s.gitViewAsTreeTitle,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = if (state.viewChangesAsTree) {
                                                    s.gitViewAsTreeSubtitleOn
                                                } else {
                                                    s.gitViewAsTreeSubtitleOff
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = state.viewChangesAsTree,
                                            onCheckedChange = { onToggleViewAsTree() },
                                            colors = appSwitchColors()
                                        )
                                    }
                                }
                            }

                            item(key = "div_categories") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            }

                            item(key = "cat_commit") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Check,
                                    title = "Commit",
                                    subtitle = "Commit, Stage All & Commit, Amend, Commit & Push",
                                    trailingChevron = true,
                                    onClick = { currentMenu = GitActionSubmenu.COMMIT }
                                )
                            }

                            item(key = "cat_changes") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.DoneAll,
                                    title = "Changes",
                                    subtitle = "Stage All, Unstage All, Discard All",
                                    badgeText = (status.stagedChanges.size + status.unstagedChanges.size)
                                        .takeIf { it > 0 }?.toString(),
                                    trailingChevron = true,
                                    onClick = { currentMenu = GitActionSubmenu.CHANGES }
                                )
                            }

                            item(key = "cat_pull_push") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Sync,
                                    title = "Pull, Push",
                                    subtitle = "Sync, Pull (Rebase), Pull from, Push to, Fetch",
                                    trailingChevron = true,
                                    onClick = { currentMenu = GitActionSubmenu.PULL_PUSH }
                                )
                            }

                            item(key = "cat_remote") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Cloud,
                                    title = "Remote",
                                    subtitle = s.gitMenuRemoteSubtitle,
                                    badgeText = state.remotes.size.takeIf { it > 0 }?.toString(),
                                    trailingChevron = true,
                                    onClick = { currentMenu = GitActionSubmenu.REMOTE }
                                )
                            }

                            item(key = "cat_stash") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Archive,
                                    title = "Stash",
                                    subtitle = s.gitMenuStashSubtitle,
                                    badgeText = state.stashes.size.takeIf { it > 0 }?.toString(),
                                    trailingChevron = true,
                                    onClick = { currentMenu = GitActionSubmenu.STASH }
                                )
                            }

                            item(key = "cat_tags") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Sell,
                                    title = "Tags",
                                    subtitle = s.gitMenuTagsSubtitle,
                                    badgeText = state.tags.size.takeIf { it > 0 }?.toString(),
                                    trailingChevron = true,
                                    onClick = { currentMenu = GitActionSubmenu.TAGS }
                                )
                            }

                            item(key = "div_tools") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            }

                            item(key = "git_output") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Terminal,
                                    title = "Show Git Output",
                                    subtitle = s.gitMenuOutputSubtitle,
                                    badgeText = state.gitOutputLogs.size.takeIf { it > 0 }?.toString(),
                                    onClick = {
                                        onDismiss()
                                        onShowGitOutput()
                                    }
                                )
                            }
                        }

                        GitActionSubmenu.COMMIT -> {
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Check,
                                    title = s.gitCommitStaged,
                                    subtitle = if (canCommitStaged) {
                                        s.gitCommitStagedSubtitleReady(status.stagedChanges.size)
                                    } else {
                                        s.gitCommitStagedSubtitleHint
                                    },
                                    enabled = canCommitStaged,
                                    onClick = {
                                        onDismiss()
                                        onCommitStaged(false)
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.DoneAll,
                                    title = "Stage All & Commit",
                                    subtitle = s.gitCommitAllSubtitle,
                                    enabled = canStageAndCommit,
                                    onClick = {
                                        onDismiss()
                                        onStageAndCommit()
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.CloudUpload,
                                    title = "Commit & Push",
                                    subtitle = if (primaryRemote != null) {
                                        s.gitCommitPushSubtitleReady(primaryRemote)
                                    } else {
                                        s.gitNoRemoteConnected
                                    },
                                    enabled = canCommitStaged && hasRemote,
                                    onClick = {
                                        onDismiss()
                                        onCommitStaged(true)
                                    }
                                )
                            }
                            if (!status.isUnborn) {
                                item {
                                    GitMenuActionRow(
                                        icon = Icons.Rounded.Edit,
                                        title = if (amendCommit) s.gitAmendToggleOn else s.gitAmendToggleOff,
                                        subtitle = s.gitAmendToggleSubtitle,
                                        badgeText = if (amendCommit) s.gitOnBadge else null,
                                        onClick = {
                                            onToggleAmend()
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }

                        GitActionSubmenu.CHANGES -> {
                            item {
                                GitMenuActionRow(
                                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    title = "Stage All Changes",
                                    subtitle = s.gitStageAllSubtitle(status.unstagedChanges.size),
                                    enabled = status.unstagedChanges.isNotEmpty(),
                                    onClick = {
                                        onDismiss()
                                        onStageAll()
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.RemoveDone,
                                    title = "Unstage All Changes",
                                    subtitle = s.gitUnstageAllSubtitle(status.stagedChanges.size),
                                    enabled = status.stagedChanges.isNotEmpty(),
                                    onClick = {
                                        onDismiss()
                                        onUnstageAll()
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Restore,
                                    title = "Discard All Changes",
                                    subtitle = s.gitDiscardAllSubtitle,
                                    isDestructive = true,
                                    enabled = hasChanges,
                                    onClick = {
                                        onDismiss()
                                        onRequestDiscardAll()
                                    }
                                )
                            }
                        }

                        GitActionSubmenu.PULL_PUSH -> {
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Sync,
                                    title = "Sync (Pull + Push)",
                                    subtitle = s.gitSyncSubtitle,
                                    enabled = hasRemote,
                                    onClick = {
                                        onDismiss()
                                        primaryRemote?.let(onSync)
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.CloudDownload,
                                    title = "Pull",
                                    subtitle = s.gitPullSubtitle(primaryRemote ?: "remote"),
                                    enabled = hasRemote,
                                    onClick = {
                                        onDismiss()
                                        primaryRemote?.let { onPull(it, false) }
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.CloudDownload,
                                    title = "Pull (Rebase)",
                                    subtitle = s.gitPullRebaseSubtitle,
                                    enabled = hasRemote && !status.isUnborn,
                                    onClick = {
                                        onDismiss()
                                        primaryRemote?.let { onPull(it, true) }
                                    }
                                )
                            }
                            if (state.remotes.size > 1) {
                                item {
                                    GitMenuActionRow(
                                        icon = Icons.Rounded.CloudDownload,
                                        title = "Pull from...",
                                        subtitle = s.gitPullFromSubtitle,
                                        enabled = hasRemote,
                                        onClick = {
                                            onDismiss()
                                            onRequestPullFrom()
                                        }
                                    )
                                }
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.CloudUpload,
                                    title = "Push",
                                    subtitle = s.gitPushSubtitle(primaryRemote ?: "remote"),
                                    enabled = hasRemote && !status.isUnborn,
                                    onClick = {
                                        onDismiss()
                                        primaryRemote?.let { onPush(it, false) }
                                    }
                                )
                            }
                            if (state.remotes.size > 1) {
                                item {
                                    GitMenuActionRow(
                                        icon = Icons.Rounded.CloudUpload,
                                        title = "Push to...",
                                        subtitle = s.gitPushToSubtitle,
                                        enabled = hasRemote && !status.isUnborn,
                                        onClick = {
                                            onDismiss()
                                            onRequestPushTo()
                                        }
                                    )
                                }
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.WarningAmber,
                                    title = "Push (Force)",
                                    subtitle = s.gitForcePushSubtitle,
                                    isDestructive = true,
                                    enabled = hasRemote && !status.isUnborn,
                                    onClick = {
                                        onDismiss()
                                        primaryRemote?.let(onRequestForcePush)
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.CloudSync,
                                    title = "Fetch",
                                    subtitle = s.gitFetchSubtitle,
                                    enabled = hasRemote,
                                    onClick = {
                                        onDismiss()
                                        primaryRemote?.let(onFetch)
                                    }
                                )
                            }
                        }

                        GitActionSubmenu.REMOTE -> {
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Add,
                                    title = s.gitAddRemoteTitle,
                                    subtitle = s.gitAddRemoteSubtitle,
                                    onClick = {
                                        onDismiss()
                                        onAddRemoteClick()
                                    }
                                )
                            }
                            if (state.remotes.isEmpty()) {
                                item {
                                    Text(
                                        text = s.gitNoRemotesConfigured,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(state.remotes, key = { it.name }) { remote ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Cloud,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = remote.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        onDismiss()
                                                        onEditRemoteClick(remote)
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = s.commonEdit)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        onDismiss()
                                                        onDeleteRemoteClick(remote)
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = s.commonDelete)
                                                }
                                            }
                                            Text(
                                                text = remote.fetchUrl,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        onDismiss()
                                                        onFetch(remote.name)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Fetch")
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        onDismiss()
                                                        onPush(remote.name, false)
                                                    },
                                                    enabled = !status.isUnborn,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Push")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        GitActionSubmenu.STASH -> {
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Archive,
                                    title = s.gitStashIncludeUntrackedTitle,
                                    subtitle = s.gitStashIncludeUntrackedSubtitle,
                                    enabled = !status.isUnborn && hasChanges,
                                    onClick = {
                                        onDismiss()
                                        onOpenStashDialog(true)
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Archive,
                                    title = s.gitStashTrackedOnlyTitle,
                                    subtitle = s.gitStashTrackedOnlySubtitle,
                                    enabled = !status.isUnborn && hasChanges,
                                    onClick = {
                                        onDismiss()
                                        onOpenStashDialog(false)
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Unarchive,
                                    title = s.gitStashPopLatestTitle,
                                    subtitle = s.gitStashPopLatestSubtitle,
                                    enabled = hasStashes,
                                    onClick = {
                                        onDismiss()
                                        onPopStash(0)
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Unarchive,
                                    title = s.gitStashApplyLatestTitle,
                                    subtitle = s.gitStashApplyLatestSubtitle,
                                    enabled = hasStashes,
                                    onClick = {
                                        onDismiss()
                                        onApplyStash(0)
                                    }
                                )
                            }
                            if (hasStashes) {
                                item {
                                    GitMenuActionRow(
                                        icon = Icons.Rounded.DeleteOutline,
                                        title = s.gitStashClearAllTitle,
                                        subtitle = s.gitStashClearAllSubtitle(state.stashes.size),
                                        isDestructive = true,
                                        onClick = {
                                            onDismiss()
                                            onRequestClearStashes()
                                        }
                                    )
                                }
                                item {
                                    Text(
                                        text = s.gitStashSavedHeader(state.stashes.size),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                                items(state.stashes, key = { "${it.index}_${it.commitId}" }) { stash ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "stash@{${stash.index}} • ${stash.message}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatShortTimestamp(stash.timestampMillis),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(
                                                    onClick = {
                                                        onDismiss()
                                                        onPopStash(stash.index)
                                                    }
                                                ) { Text("Pop") }
                                                TextButton(
                                                    onClick = {
                                                        onDismiss()
                                                        onApplyStash(stash.index)
                                                    }
                                                ) { Text("Apply") }
                                                TextButton(
                                                    onClick = {
                                                        onDismiss()
                                                        onDropStash(stash)
                                                    }
                                                ) {
                                                    Text("Drop", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        GitActionSubmenu.TAGS -> {
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Add,
                                    title = s.gitCreateTagTitle,
                                    subtitle = s.gitCreateTagSubtitle(status.currentBranch ?: "HEAD"),
                                    enabled = !status.isUnborn,
                                    onClick = {
                                        onDismiss()
                                        onCreateTagClick()
                                    }
                                )
                            }
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.CloudUpload,
                                    title = s.gitPushTagsTitle,
                                    subtitle = if (primaryRemote != null) {
                                        s.gitPushTagsSubtitle(primaryRemote)
                                    } else {
                                        s.gitNoRemoteConnected
                                    },
                                    enabled = hasRemote && state.tags.isNotEmpty(),
                                    onClick = {
                                        onDismiss()
                                        primaryRemote?.let(onPushTags)
                                    }
                                )
                            }
                            if (state.tags.isEmpty()) {
                                item {
                                    Text(
                                        text = s.gitNoTagsYet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            } else {
                                item {
                                    Text(
                                        text = s.gitLocalTagsHeader(state.tags.size),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                                items(state.tags, key = { it.fullName }) { tag ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Sell,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tag.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                val details = buildString {
                                                    tag.targetCommitId?.take(7)?.let { append(it) }
                                                    if (!tag.message.isNullOrBlank()) {
                                                        if (isNotEmpty()) append(" • ")
                                                        append(tag.message)
                                                    }
                                                }
                                                if (details.isNotBlank()) {
                                                    Text(
                                                        text = details,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    onDismiss()
                                                    onDeleteTagClick(tag)
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = s.gitDeleteTagDesc,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickGitTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (enabled) 0.7f else 0.25f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .heightIn(min = 66.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MenuIconBadge(
    icon: ImageVector,
    isDestructive: Boolean = false
) {
    Surface(
        color = if (isDestructive) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun GitMenuActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    trailingChevron: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.45f else 0.22f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.48f)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MenuIconBadge(icon = icon, isDestructive = isDestructive)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!badgeText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            if (trailingChevron) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
