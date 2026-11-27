package com.pocketworker.app.ui.projectdetail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketworker.app.core.git.CloneProgress
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.i18n.LocalAppStrings
import com.pocketworker.app.ui.common.FormFieldShape
import com.pocketworker.app.ui.common.FormOptionCard
import com.pocketworker.app.ui.common.PocketFormDialog
import com.pocketworker.app.ui.common.appSwitchColors
import com.pocketworker.app.ui.common.formTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoChoiceSheet(
    onDismiss: () -> Unit,
    onConnectClick: () -> Unit,
    onAddGroupClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    val s = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = s.projectAddRepo,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = s.repoAddSheetSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onConnectClick),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = s.repoConnectTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = s.repoConnectSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddGroupClick),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = s.repoAddGroupTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = s.repoAddGroupSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreateClick),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = s.repoCreateTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = s.repoCreateSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CloneRepoDialog(
    isCloning: Boolean,
    cloneProgress: CloneProgress?,
    errorMessage: String?,
    fallbackCredentials: GitCredentials?,
    hasConnectedOAuthAccounts: Boolean = false,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onCancelClone: () -> Unit,
    onClone: (
        url: String,
        customName: String,
        branch: String,
        useAuth: Boolean,
        username: String,
        token: String,
        saveForRepo: Boolean
    ) -> Unit
) {
    val s = LocalAppStrings.current
    var url by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var nameManuallyEdited by remember { mutableStateOf(false) }
    var branch by remember { mutableStateOf("") }
    var useAuth by remember { mutableStateOf(fallbackCredentials != null || hasConnectedOAuthAccounts) }
    var username by remember { mutableStateOf(fallbackCredentials?.username ?: "") }
    var token by remember { mutableStateOf(fallbackCredentials?.tokenOrPassword ?: "") }
    var saveCreds by remember { mutableStateOf(true) }
    var showToken by remember { mutableStateOf(false) }

    PocketFormDialog(
        title = s.repoConnectTitle,
        subtitle = s.repoCloneDialogSubtitle,
        icon = Icons.Rounded.CloudDownload,
        onDismiss = onDismiss,
        dismissEnabled = !isCloning,
        pinnedBanner = if (!errorMessage.isNullOrBlank()) {
            {
                DialogErrorBanner(
                    message = errorMessage,
                    onDismissError = onClearError
                )
            }
        } else null,
        footerActions = {
            if (isCloning) {
                OutlinedButton(
                    onClick = onCancelClone,
                    shape = FormFieldShape,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(s.commonCancel)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(s.commonClose)
                }
                Button(
                    onClick = {
                        onClone(
                            url,
                            customName,
                            branch,
                            useAuth,
                            username,
                            token,
                            useAuth && saveCreds
                        )
                    },
                    enabled = url.isNotBlank(),
                    shape = FormFieldShape,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(s.repoCloneAction)
                }
            }
        }
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { newUrl ->
                url = newUrl
                onClearError()
                if (!nameManuallyEdited) {
                    customName = if (newUrl.isBlank()) "" else {
                        ProjectDetailViewModel.deriveRepoNameFromUrl(newUrl)
                    }
                }
            },
            label = { Text(s.repoUrlLabel) },
            placeholder = { Text("https://github.com/user/repo.git") },
            singleLine = true,
            enabled = !isCloning,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = customName,
            onValueChange = {
                customName = it
                nameManuallyEdited = it.isNotBlank()
                onClearError()
            },
            label = { Text(s.repoFolderNameLabel) },
            placeholder = { Text(s.repoFolderNamePlaceholder) },
            singleLine = true,
            enabled = !isCloning,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = branch,
            onValueChange = {
                branch = it
                onClearError()
            },
            label = { Text(s.repoBranchOptionalLabel) },
            placeholder = { Text(s.repoBranchOptionalPlaceholder) },
            singleLine = true,
            enabled = !isCloning,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        AuthCredentialsFields(
            useAuth = useAuth,
            onUseAuthChange = {
                useAuth = it
                onClearError()
            },
            username = username,
            onUsernameChange = {
                username = it
                onClearError()
            },
            token = token,
            onTokenChange = {
                token = it
                onClearError()
            },
            showToken = showToken,
            onToggleShowToken = { showToken = !showToken },
            saveCreds = saveCreds,
            onSaveCredsChange = { saveCreds = it },
            saveCredsLabel = s.repoSaveTokenForRepo,
            enabled = !isCloning,
            hasConnectedOAuthAccounts = hasConnectedOAuthAccounts
        )

        if (isCloning && cloneProgress != null) {
            CloneProgressBlock(cloneProgress = cloneProgress)
        }
    }
}

@Composable
fun CreateRepoDialog(
    isCreating: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        initGit: Boolean,
        initialBranch: String,
        createReadmeAndCommit: Boolean,
        remoteUrl: String,
        authorName: String,
        authorEmail: String
    ) -> Unit
) {
    val s = LocalAppStrings.current
    var name by remember { mutableStateOf("") }
    var initGit by remember { mutableStateOf(true) }
    var initialBranch by remember { mutableStateOf("main") }
    var createReadme by remember { mutableStateOf(true) }
    var remoteUrl by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf(GitEngine.DEFAULT_AUTHOR_NAME) }
    var authorEmail by remember { mutableStateOf(GitEngine.DEFAULT_AUTHOR_EMAIL) }

    PocketFormDialog(
        title = s.repoCreateTitle,
        subtitle = s.repoCreateDialogSubtitle,
        icon = Icons.Rounded.AddCircleOutline,
        onDismiss = onDismiss,
        dismissEnabled = !isCreating,
        pinnedBanner = if (!errorMessage.isNullOrBlank()) {
            {
                DialogErrorBanner(
                    message = errorMessage,
                    onDismissError = onClearError
                )
            }
        } else null,
        footerActions = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text(s.commonCancel)
            }
            Button(
                onClick = {
                    onCreate(
                        name,
                        initGit,
                        initialBranch,
                        createReadme,
                        remoteUrl,
                        authorName,
                        authorEmail
                    )
                },
                enabled = !isCreating && name.isNotBlank(),
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(s.commonCreate)
            }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                onClearError()
            },
            label = { Text(s.repoNameLabel) },
            placeholder = { Text("backend-api") },
            singleLine = true,
            enabled = !isCreating,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        FormOptionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isCreating) { initGit = !initGit },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.repoInitGitTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = s.repoInitGitSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = initGit,
                    onCheckedChange = { initGit = it },
                    enabled = !isCreating,
                    colors = appSwitchColors()
                )
            }

            if (initGit) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCreating) { createReadme = !createReadme },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.repoCreateReadmeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = s.repoCreateReadmeSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = createReadme,
                        onCheckedChange = { createReadme = it },
                        enabled = !isCreating,
                        colors = appSwitchColors()
                    )
                }
            }
        }

        if (initGit) {
            OutlinedTextField(
                value = initialBranch,
                onValueChange = { initialBranch = it },
                label = { Text(s.repoInitialBranchLabel) },
                singleLine = true,
                enabled = !isCreating,
                shape = FormFieldShape,
                colors = formTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("main", "dev", "master").forEach { preset ->
                    FilterChip(
                        selected = initialBranch == preset,
                        onClick = { initialBranch = preset },
                        label = { Text(preset) }
                    )
                }
            }

            OutlinedTextField(
                value = remoteUrl,
                onValueChange = { remoteUrl = it },
                label = { Text(s.repoRemoteUrlOptionalLabel) },
                placeholder = { Text("https://github.com/user/repo.git") },
                singleLine = true,
                enabled = !isCreating,
                shape = FormFieldShape,
                colors = formTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = authorName,
                onValueChange = { authorName = it },
                label = { Text(s.repoAuthorNameLabel) },
                singleLine = true,
                enabled = !isCreating,
                shape = FormFieldShape,
                colors = formTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = authorEmail,
                onValueChange = { authorEmail = it },
                label = { Text(s.repoAuthorEmailLabel) },
                singleLine = true,
                enabled = !isCreating,
                shape = FormFieldShape,
                colors = formTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isCreating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DialogErrorBanner(
    message: String,
    onDismissError: () -> Unit
) {
    val s = LocalAppStrings.current
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismissError,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = s.commonHideError,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AuthCredentialsFields(
    useAuth: Boolean,
    onUseAuthChange: (Boolean) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    showToken: Boolean,
    onToggleShowToken: () -> Unit,
    saveCreds: Boolean,
    onSaveCredsChange: (Boolean) -> Unit,
    saveCredsLabel: String,
    enabled: Boolean,
    hasConnectedOAuthAccounts: Boolean = false
) {
    val s = LocalAppStrings.current
    FormOptionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onUseAuthChange(!useAuth) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s.repoAuthTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = s.repoAuthSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = useAuth,
                onCheckedChange = onUseAuthChange,
                enabled = enabled,
                colors = appSwitchColors()
            )
        }

        if (useAuth) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

            if (hasConnectedOAuthAccounts && token.isBlank()) {
                Text(
                    text = s.oauthCloneUsingConnectedAccountsHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text(s.repoAuthUserLabel) },
                placeholder = { Text("x-access-token") },
                singleLine = true,
                enabled = enabled,
                shape = FormFieldShape,
                colors = formTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                label = { Text(s.repoAuthTokenLabel) },
                singleLine = true,
                enabled = enabled,
                shape = FormFieldShape,
                colors = formTextFieldColors(),
                visualTransformation = if (showToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = onToggleShowToken) {
                        Icon(
                            imageVector = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = s.commonShowHideToken
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onSaveCredsChange(!saveCreds) }
            ) {
                Checkbox(
                    checked = saveCreds,
                    onCheckedChange = onSaveCredsChange,
                    enabled = enabled
                )
                Text(
                    text = saveCredsLabel,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun CloneProgressBlock(cloneProgress: CloneProgress) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = cloneProgress.stage,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
    if (cloneProgress.total > 0) {
        val fraction = (cloneProgress.completed.toFloat() / cloneProgress.total.toFloat())
            .coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupSheet(
    state: ProjectDetailUiState,
    fallbackCredentials: GitCredentials?,
    hasConnectedOAuthAccounts: Boolean = false,
    onClearError: () -> Unit,
    onDiscover: (url: String, useAuth: Boolean, username: String, token: String) -> Unit,
    onToggleRepo: (repoId: String) -> Unit,
    onSetAllSelected: (Boolean) -> Unit,
    onCloneSelected: (useAuth: Boolean, username: String, token: String, saveForRepos: Boolean) -> Unit,
    onCancelClone: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var url by remember { mutableStateOf("") }
    var useAuth by remember { mutableStateOf(fallbackCredentials != null || hasConnectedOAuthAccounts) }
    var username by remember { mutableStateOf(fallbackCredentials?.username ?: "") }
    var token by remember { mutableStateOf(fallbackCredentials?.tokenOrPassword ?: "") }
    var saveCreds by remember { mutableStateOf(true) }
    var showToken by remember { mutableStateOf(false) }

    val activeError = state.groupDiscoveryError ?: state.groupCloneError
    val selectedCount = state.discoveredRepos.count { it.selected }
    val allSelected = state.discoveredRepos.isNotEmpty() && selectedCount == state.discoveredRepos.size
    val isBusy = state.isDiscoveringGroup || state.isGroupCloning

    ModalBottomSheet(
        onDismissRequest = { if (!state.isGroupCloning) onDismiss() },
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.repoAddGroupTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = s.repoGroupDialogSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!state.isGroupCloning) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = s.commonClose)
                    }
                }
            }

            if (!activeError.isNullOrBlank()) {
                DialogErrorBanner(
                    message = activeError,
                    onDismissError = onClearError
                )
            }

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    onClearError()
                },
                label = { Text(s.repoGroupUrlLabel) },
                placeholder = { Text(s.repoGroupUrlPlaceholder) },
                singleLine = true,
                enabled = !isBusy,
                shape = FormFieldShape,
                colors = formTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            AuthCredentialsFields(
                useAuth = useAuth,
                onUseAuthChange = {
                    useAuth = it
                    onClearError()
                },
                username = username,
                onUsernameChange = {
                    username = it
                    onClearError()
                },
                token = token,
                onTokenChange = {
                    token = it
                    onClearError()
                },
                showToken = showToken,
                onToggleShowToken = { showToken = !showToken },
                saveCreds = saveCreds,
                onSaveCredsChange = { saveCreds = it },
                saveCredsLabel = s.repoGroupSaveTokenLabel,
                enabled = !isBusy,
                hasConnectedOAuthAccounts = hasConnectedOAuthAccounts
            )

            OutlinedButton(
                onClick = { onDiscover(url, useAuth, username, token) },
                enabled = url.isNotBlank() && !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (state.discoveredRepos.isEmpty()) s.repoGroupFindRepos else s.repoGroupRefreshList
                )
            }

            if (state.isDiscoveringGroup) {
                Text(
                    text = s.repoGroupSearching,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.discoveredRepos.isNotEmpty()) {
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = s.repoGroupFoundSelected(state.discoveredRepos.size, selectedCount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { onSetAllSelected(!allSelected) },
                        enabled = !state.isGroupCloning
                    ) {
                        Text(if (allSelected) s.repoGroupUnselectAll else s.repoGroupSelectAll)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(state.discoveredRepos, key = { it.repo.id }) { item ->
                        Surface(
                            color = if (item.selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !state.isGroupCloning) {
                                    onToggleRepo(item.repo.id)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { onToggleRepo(item.repo.id) },
                                    enabled = !state.isGroupCloning
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.repo.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (item.repo.isPrivate) {
                                            Icon(
                                                imageVector = Icons.Rounded.Lock,
                                                contentDescription = s.repoGroupPrivateDesc,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        if (item.alreadyAdded) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = s.repoGroupAlreadyAdded,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = buildString {
                                            append(item.repo.fullName)
                                            item.repo.defaultBranch?.let { append(" • $it") }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.repo.description.isNotBlank()) {
                                        Text(
                                            text = item.repo.description,
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
                }
            }

            if (state.isGroupCloning) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = s.repoGroupDownloading(
                                state.groupCloneCurrentIndex,
                                state.groupCloneTotal,
                                state.groupCloneCurrentRepoName
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        state.groupCloneProgress?.let { CloneProgressBlock(it) }
                    }
                }
                OutlinedButton(
                    onClick = onCancelClone,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.commonCancel)
                }
            } else if (state.discoveredRepos.isNotEmpty()) {
                Button(
                    onClick = {
                        onCloneSelected(
                            useAuth,
                            username,
                            token,
                            useAuth && saveCreds
                        )
                    },
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(s.repoGroupDownloadSelected(selectedCount))
                }
            }
        }
    }
}
