package com.pocketworker.app.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketworker.app.core.git.ProjectFileItem
import com.pocketworker.app.core.i18n.LocalAppStrings
import com.pocketworker.app.ui.common.FormFieldShape
import com.pocketworker.app.ui.common.PocketFormDialog
import com.pocketworker.app.ui.common.formTextFieldColors
import com.pocketworker.app.ui.common.formatFileSize
import com.pocketworker.app.ui.theme.LocalIsDarkTheme

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
    onDeleteEntry: (relativePath: String) -> Unit,
    onRunSyntaxCheck: (singleRelativePath: String?) -> Unit
) {
    val s = LocalAppStrings.current
    var showSearchBar by remember { mutableStateOf(false) }
    var showToolsMenu by remember { mutableStateOf(false) }
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
                                contentDescription = s.filesNavigateUp
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
                            contentDescription = s.filesSearchDesc
                        )
                    }
                    IconButton(
                        onClick = onToggleShowHiddenGit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (state.showHiddenGit) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = s.filesToggleGitDirDesc
                        )
                    }
                    IconButton(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = s.filesNewFile)
                    }
                    IconButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = s.filesNewFolder)
                    }
                    Box {
                        IconButton(
                            onClick = { showToolsMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Build,
                                contentDescription = s.filesToolsDesc
                            )
                        }
                        DropdownMenu(
                            expanded = showToolsMenu,
                            onDismissRequest = { showToolsMenu = false }
                        ) {
                            val scopeDisplay = state.currentDir.ifBlank { state.project?.name ?: "root" }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = s.filesSyntaxCheckTitle,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = s.filesSyntaxCheckScopeSubtitle(scopeDisplay),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Verified, contentDescription = null)
                                },
                                onClick = {
                                    showToolsMenu = false
                                    onRunSyntaxCheck(null)
                                }
                            )
                        }
                    }
                }

                if (showSearchBar) {
                    OutlinedTextField(
                        value = state.fileSearchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(s.filesSearchPlaceholder) },
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
                        s.filesNothingFound
                    } else {
                        s.filesFolderEmpty
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
                        onCheckSyntaxClick = { onRunSyntaxCheck(item.relativePath) },
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
            title = s.filesNewFile,
            icon = Icons.AutoMirrored.Rounded.NoteAdd,
            label = s.filesNewFileNameLabel,
            initialValue = "",
            confirmLabel = s.commonCreate,
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name -> onCreateFile(name) }
        )
    }

    if (showNewFolderDialog) {
        SingleNameInputDialog(
            title = s.filesNewFolder,
            icon = Icons.Rounded.CreateNewFolder,
            label = s.filesNewFolderNameLabel,
            initialValue = "",
            confirmLabel = s.commonCreate,
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name -> onCreateFolder(name) }
        )
    }

    entryToRename?.let { target ->
        SingleNameInputDialog(
            title = s.commonRename,
            icon = Icons.Rounded.DriveFileRenameOutline,
            label = s.filesNewNameLabel,
            initialValue = target.name,
            confirmLabel = s.commonRename,
            onDismiss = { entryToRename = null },
            onConfirm = { newName -> onRenameEntry(target.relativePath, newName) }
        )
    }

    entryToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(s.filesDeleteTitle(target.name)) },
            text = {
                Text(
                    if (target.isDirectory) {
                        s.filesDeleteFolderConfirm
                    } else {
                        s.filesDeleteFileConfirm(target.relativePath)
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
                    Text(s.commonDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text(s.commonCancel) }
            }
        )
    }
}

@Composable
private fun FileRowItem(
    item: ProjectFileItem,
    showFullPath: Boolean,
    onClick: () -> Unit,
    onCheckSyntaxClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val s = LocalAppStrings.current
    var menuExpanded by remember { mutableStateOf(false) }
    val isDark = LocalIsDarkTheme.current
    val gitChangedDotColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
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
            if (item.gitChangeType != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(gitChangedDotColor)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))

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
                    contentDescription = s.commonActions,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (!item.isDirectory) {
                    DropdownMenuItem(
                        text = { Text(s.filesSyntaxCheckSingleFile) },
                        leadingIcon = { Icon(Icons.Rounded.Verified, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCheckSyntaxClick()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(s.commonRename) },
                    leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRenameClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text(s.commonDelete) },
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
    val s = LocalAppStrings.current
    var text by remember { mutableStateOf(initialValue) }

    PocketFormDialog(
        title = title,
        icon = icon,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
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

@Composable
fun SyntaxCheckResultsDialog(
    state: SyntaxCheckDialogState,
    onDismiss: () -> Unit,
    onRecheck: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val s = LocalAppStrings.current
    val isDark = LocalIsDarkTheme.current
    val okColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
    val warnColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val errorColor = MaterialTheme.colorScheme.error

    PocketFormDialog(
        title = s.filesSyntaxCheckTitle,
        subtitle = s.filesSyntaxCheckScopeSubtitle(state.scopeLabel),
        icon = Icons.Rounded.Verified,
        onDismiss = onDismiss,
        pinnedBanner = if (!state.isRunning && state.errorMessage == null && state.checkedFilesCount > 0) {
            {
                val hasErrors = state.totalErrors > 0
                val hasWarnings = state.totalWarnings > 0
                val accent = when {
                    hasErrors -> errorColor
                    hasWarnings -> warnColor
                    else -> okColor
                }
                Surface(
                    color = accent.copy(alpha = if (isDark) 0.16f else 0.10f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (hasErrors || hasWarnings) {
                                Icons.Rounded.ErrorOutline
                            } else {
                                Icons.Rounded.CheckCircleOutline
                            },
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (state.fileReportsWithIssues.isEmpty()) {
                                    s.filesSyntaxNoErrorsTitle
                                } else {
                                    s.filesSyntaxIssuesSummary(
                                        state.totalErrors,
                                        state.totalWarnings,
                                        state.fileReportsWithIssues.size
                                    )
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = s.filesSyntaxCheckedSummary(
                                    state.checkedFilesCount,
                                    state.skippedFilesCount
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
        footerActions = {
            if (!state.isRunning) {
                TextButton(onClick = onRecheck) {
                    Text(s.filesSyntaxCheckAgain)
                }
            }
            Button(
                onClick = onDismiss,
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(s.commonClose)
            }
        }
    ) {
        when {
            state.isRunning -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = s.filesSyntaxChecking,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.errorMessage != null -> {
                Surface(
                    color = errorColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = errorColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            state.checkedFilesCount == 0 -> {
                Text(
                    text = s.filesSyntaxNoFilesToCheck,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            state.fileReportsWithIssues.isEmpty() -> {
                // Summary banner above already communicates clean status cleanly.
                Spacer(modifier = Modifier.height(2.dp))
            }

            else -> {
                Text(
                    text = s.filesSyntaxOpenFileHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                state.fileReportsWithIssues.forEach { report ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFile(report.relativePath) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = report.relativePath,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = report.languageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            report.issues.forEachIndexed { idx, issue ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    )
                                }
                                val isWarning = issue.severity == "warning"
                                val badgeColor = if (isWarning) warnColor else errorColor
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = badgeColor.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(5.dp)
                                        ) {
                                            Text(
                                                text = if (isWarning) s.filesSyntaxWarningBadge else s.filesSyntaxErrorBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = badgeColor,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = s.filesSyntaxLineCol(issue.line, issue.column),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = issue.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!issue.snippet.isNullOrBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = issue.snippet,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                softWrap = false,
                                                modifier = Modifier
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
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
