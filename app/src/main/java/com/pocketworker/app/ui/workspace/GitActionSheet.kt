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
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
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
import com.pocketworker.app.ui.common.appSwitchColors
import com.pocketworker.app.ui.common.formatShortTimestamp

enum class GitActionSubmenu(val title: String) {
    ROOT("Действия Git"),
    COMMIT("Коммит (Commit)"),
    CHANGES("Изменения (Changes)"),
    PULL_PUSH("Синхронизация (Pull, Push)"),
    REMOTE("Удалённые (Remote)"),
    STASH("Отложенные (Stash)"),
    TAGS("Теги (Tags)")
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
    onShowGitOutput: () -> Unit,
    onOpenCredentialsDialog: () -> Unit,
    onOpenAuthorDialog: () -> Unit
) {
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
                            contentDescription = "Назад"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentMenu.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("Ветка: ${status.currentBranch ?: "HEAD"}")
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
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
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
                                                text = "Вид изменений: дерево",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = if (state.viewChangesAsTree) "Группировка по папкам" else "Плоский список файлов",
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
                                    subtitle = "Управление подключениями Git",
                                    badgeText = state.remotes.size.takeIf { it > 0 }?.toString(),
                                    trailingChevron = true,
                                    onClick = { currentMenu = GitActionSubmenu.REMOTE }
                                )
                            }

                            item(key = "cat_stash") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Archive,
                                    title = "Stash",
                                    subtitle = "Отложить изменения, Pop, Apply, Drop",
                                    badgeText = state.stashes.size.takeIf { it > 0 }?.toString(),
                                    trailingChevron = true,
                                    onClick = { currentMenu = GitActionSubmenu.STASH }
                                )
                            }

                            item(key = "cat_tags") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Sell,
                                    title = "Tags",
                                    subtitle = "Создать тег, удалить, отправить теги",
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
                                    subtitle = "Журнал выполненных Git-команд",
                                    badgeText = state.gitOutputLogs.size.takeIf { it > 0 }?.toString(),
                                    onClick = {
                                        onDismiss()
                                        onShowGitOutput()
                                    }
                                )
                            }

                            item(key = "credentials") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Key,
                                    title = "Токен доступа (PAT / пароль)",
                                    subtitle = when {
                                        state.repoCredentials != null -> "Задан токен репозитория"
                                        state.hasFallbackCredentials -> "Используется токен проекта / по умолчанию"
                                        else -> "Не задан"
                                    },
                                    onClick = {
                                        onDismiss()
                                        onOpenCredentialsDialog()
                                    }
                                )
                            }

                            item(key = "author") {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Person,
                                    title = "Автор коммитов",
                                    subtitle = "${status.authorName} <${status.authorEmail}>",
                                    onClick = {
                                        onDismiss()
                                        onOpenAuthorDialog()
                                    }
                                )
                            }
                        }

                        GitActionSubmenu.COMMIT -> {
                            item {
                                GitMenuActionRow(
                                    icon = Icons.Rounded.Check,
                                    title = "Commit (подготовленные)",
                                    subtitle = if (canCommitStaged) {
                                        "Создать коммит из индекса (${status.stagedChanges.size})"
                                    } else {
                                        "Введите сообщение и подготовьте файлы"
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
                                    subtitle = "Индексировать все изменения и создать коммит",
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
                                    subtitle = if (hasRemote) "Создать коммит и сразу отправить в $primaryRemote" else "Нет подключённого remote",
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
                                        title = if (amendCommit) "Выключить режим Amend" else "Включить режим Amend",
                                        subtitle = "Дополнить последний коммит вместо создания нового",
                                        badgeText = if (amendCommit) "ВКЛ" else null,
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
                                    subtitle = "Добавить все изменения в индекс (${status.unstagedChanges.size})",
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
                                    subtitle = "Убрать все файлы из индекса (${status.stagedChanges.size})",
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
                                    subtitle = "Откатить все несохранённые изменения и удалить untracked файлы",
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
                                    subtitle = "Сначала получить изменения, затем отправить свои",
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
                                    subtitle = "Загрузить и объединить изменения из ${primaryRemote ?: "remote"}",
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
                                    subtitle = "Перебазировать локальные коммиты поверх удалённых",
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
                                        subtitle = "Выбрать удалённый репозиторий для Pull",
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
                                    subtitle = "Отправить коммиты в ${primaryRemote ?: "remote"}",
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
                                        subtitle = "Выбрать удалённый репозиторий для Push",
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
                                    subtitle = "Принудительно перезаписать удалённую ветку",
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
                                    subtitle = "Обновить информацию об удалённых ветках без слияния",
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
                                    title = "Добавить remote...",
                                    subtitle = "Привязать новый удалённый репозиторий по URL",
                                    onClick = {
                                        onDismiss()
                                        onAddRemoteClick()
                                    }
                                )
                            }
                            if (state.remotes.isEmpty()) {
                                item {
                                    Text(
                                        text = "Удалённые репозитории не настроены.",
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
                                                    Icon(Icons.Rounded.Edit, contentDescription = "Изменить")
                                                }
                                                IconButton(
                                                    onClick = {
                                                        onDismiss()
                                                        onDeleteRemoteClick(remote)
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить")
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
                                    title = "Stash (включая untracked)...",
                                    subtitle = "Отложить все текущие изменения и новые файлы",
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
                                    title = "Stash (только отслеживаемые)...",
                                    subtitle = "Не трогать новые (untracked) файлы",
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
                                    title = "Pop последний Stash",
                                    subtitle = "Применить stash@{0} и удалить его из списка",
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
                                    title = "Apply последний Stash",
                                    subtitle = "Применить stash@{0}, оставив копию в списке",
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
                                        title = "Очистить все Stash",
                                        subtitle = "Удалить все отложенные записи (${state.stashes.size})",
                                        isDestructive = true,
                                        onClick = {
                                            onDismiss()
                                            onRequestClearStashes()
                                        }
                                    )
                                }
                                item {
                                    Text(
                                        text = "Сохранённые Stash (${state.stashes.size})",
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
                                    title = "Создать тег...",
                                    subtitle = "Отметить текущий коммит (${status.currentBranch ?: "HEAD"})",
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
                                    title = "Отправить все теги (Push Tags)",
                                    subtitle = if (hasRemote) "Отправить локальные теги в $primaryRemote" else "Нет подключённого remote",
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
                                        text = "Тегов пока нет.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            } else {
                                item {
                                    Text(
                                        text = "Локальные теги (${state.tags.size})",
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
                                                    contentDescription = "Удалить тег",
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
