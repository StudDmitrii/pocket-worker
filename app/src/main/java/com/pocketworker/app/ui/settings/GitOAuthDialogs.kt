package com.pocketworker.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitOAuthAccount
import com.pocketworker.app.core.git.GitOAuthDefaults
import com.pocketworker.app.core.git.GitOAuthFlowState
import com.pocketworker.app.core.git.GitOAuthProvider
import com.pocketworker.app.core.i18n.LocalAppStrings
import com.pocketworker.app.ui.common.FormFieldShape
import com.pocketworker.app.ui.common.PocketFormDialog
import com.pocketworker.app.ui.common.formTextFieldColors

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun openUrlInBrowser(context: Context, url: String): Boolean {
    return runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

@Composable
internal fun GitHubOAuthDialog(
    initialClientId: String,
    flowState: GitOAuthFlowState,
    onStart: (clientId: String) -> Unit,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var clientId by remember(initialClientId) { mutableStateOf(initialClientId) }
    var editingClientId by remember(initialClientId) { mutableStateOf(initialClientId.isBlank()) }
    var autoStarted by remember { mutableStateOf(false) }

    // Auto-request Device Flow code immediately if Client ID is already configured
    LaunchedEffect(initialClientId, flowState) {
        if (!autoStarted && initialClientId.isNotBlank() && flowState is GitOAuthFlowState.Idle) {
            autoStarted = true
            onStart(initialClientId)
        }
    }

    val isBusy = flowState is GitOAuthFlowState.Starting || flowState is GitOAuthFlowState.ExchangingToken

    PocketFormDialog(
        title = s.oauthGitHubDialogTitle,
        subtitle = s.oauthGitHubDialogSubtitle,
        icon = Icons.Rounded.VerifiedUser,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            if (flowState is GitOAuthFlowState.Idle ||
                flowState is GitOAuthFlowState.Error ||
                editingClientId
            ) {
                Button(
                    onClick = {
                        editingClientId = false
                        onStart(clientId)
                    },
                    enabled = clientId.isNotBlank() && !isBusy,
                    shape = FormFieldShape,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(s.oauthGitHubStartButton)
                }
            }
        }
    ) {
        if (flowState is GitOAuthFlowState.Error && flowState.provider == GitOAuthProvider.GITHUB) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = flowState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        when {
            editingClientId || clientId.isBlank() -> {
                Text(
                    text = s.oauthGitHubClientIdHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text(s.oauthGitHubClientIdLabel) },
                    placeholder = { Text("Ov23li...") },
                    singleLine = true,
                    shape = FormFieldShape,
                    colors = formTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            flowState is GitOAuthFlowState.Starting && flowState.provider == GitOAuthProvider.GITHUB -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                    Text(
                        text = s.oauthGitHubStartButton,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            flowState is GitOAuthFlowState.GitHubDeviceCodeReady -> {
                Text(
                    text = s.oauthGitHubCodeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            copyToClipboard(context, "GitHub code", flowState.userCode)
                            onNotify(s.oauthGitHubCodeCopiedInfo)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = flowState.userCode,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        copyToClipboard(context, "GitHub code", flowState.userCode)
                        onNotify(s.oauthGitHubCodeCopiedInfo)
                        if (!openUrlInBrowser(context, flowState.verificationUri)) {
                            onNotify(s.oauthErrBrowserUnavailable)
                        }
                    },
                    shape = FormFieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(s.oauthGitHubCopyAndOpenButton)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text(
                        text = s.oauthGitHubWaitingHint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            flowState is GitOAuthFlowState.ExchangingToken && flowState.provider == GitOAuthProvider.GITHUB -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                    Text(
                        text = s.oauthGitLabExchangingHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                Text(
                    text = s.oauthGitHubClientIdHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!editingClientId && clientId.isNotBlank() && flowState !is GitOAuthFlowState.ExchangingToken) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { editingClientId = true }) {
                    Text(
                        text = s.oauthGitHubChangeClientId,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
internal fun GitLabOAuthDialog(
    initialCloudClientId: String,
    flowState: GitOAuthFlowState,
    resolveSavedClientIdForUrl: (String) -> String,
    onPrepareAuthUrl: (baseUrl: String, clientId: String) -> String?,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current

    var isSelfHosted by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("") }
    var cloudClientId by remember(initialCloudClientId) { mutableStateOf(initialCloudClientId) }
    var selfHostedClientId by remember { mutableStateOf("") }
    var editingCloudClientId by remember(initialCloudClientId) {
        mutableStateOf(initialCloudClientId.isBlank())
    }

    val activeClientId = if (isSelfHosted) selfHostedClientId else cloudClientId
    val activeBaseUrl = if (isSelfHosted) serverUrl else GitOAuthProvider.GITLAB.defaultBaseUrl
    val isWaiting = flowState is GitOAuthFlowState.WaitingForGitLabRedirect
    val isExchanging = flowState is GitOAuthFlowState.ExchangingToken && flowState.provider == GitOAuthProvider.GITLAB

    PocketFormDialog(
        title = s.oauthGitLabDialogTitle,
        subtitle = s.oauthGitLabDialogSubtitle,
        icon = Icons.Rounded.VerifiedUser,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            if (!isExchanging) {
                Button(
                    onClick = {
                        val url = onPrepareAuthUrl(activeBaseUrl, activeClientId)
                        if (url != null && !openUrlInBrowser(context, url)) {
                            onNotify(s.oauthErrBrowserUnavailable)
                        }
                    },
                    enabled = activeClientId.isNotBlank() && (!isSelfHosted || serverUrl.isNotBlank()),
                    shape = FormFieldShape,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(s.oauthGitLabOpenBrowserButton)
                }
            }
        }
    ) {
        if (flowState is GitOAuthFlowState.Error && flowState.provider == GitOAuthProvider.GITLAB) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = flowState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (isExchanging) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                Text(
                    text = s.oauthGitLabExchangingHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Mode Switcher: gitlab.com vs Self-hosted
            TwoOptionToggleRow(
                leftLabel = s.oauthGitLabModeCloud,
                rightLabel = s.oauthGitLabModeSelfHosted,
                rightSelected = isSelfHosted,
                onSelect = { selfHosted ->
                    isSelfHosted = selfHosted
                    if (selfHosted && serverUrl.isNotBlank() && selfHostedClientId.isBlank()) {
                        selfHostedClientId = resolveSavedClientIdForUrl(serverUrl)
                    }
                }
            )

            if (isSelfHosted) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { updated ->
                        serverUrl = updated
                        val saved = resolveSavedClientIdForUrl(updated)
                        if (saved.isNotBlank()) {
                            selfHostedClientId = saved
                        }
                    },
                    label = { Text(s.oauthGitLabServerUrlLabel) },
                    placeholder = { Text(s.oauthGitLabServerUrlPlaceholder) },
                    singleLine = true,
                    shape = FormFieldShape,
                    colors = formTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isSelfHosted || editingCloudClientId || cloudClientId.isBlank()) {
                OutlinedTextField(
                    value = activeClientId,
                    onValueChange = { value ->
                        if (isSelfHosted) selfHostedClientId = value else cloudClientId = value
                    },
                    label = { Text(s.oauthGitLabClientIdLabel) },
                    singleLine = true,
                    shape = FormFieldShape,
                    colors = formTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = s.oauthGitLabSetupHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Copyable Redirect URI chip
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            copyToClipboard(context, "Redirect URI", GitOAuthDefaults.REDIRECT_URI_GITLAB)
                            onNotify(s.oauthGitLabRedirectUriCopiedInfo)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.oauthGitLabRedirectUriLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = GitOAuthDefaults.REDIRECT_URI_GITLAB,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = s.oauthGitLabCopyRedirectUriDesc,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { editingCloudClientId = true }) {
                        Text(
                            text = s.oauthGitHubChangeClientId,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (isWaiting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text(
                        text = s.oauthGitLabWaitingHint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScopedGitAuthDialog(
    title: String,
    description: String,
    connectedAccounts: List<GitOAuthAccount>,
    selectedAccountId: String?,
    currentManualCredentials: GitCredentials?,
    onSelectAccount: (String) -> Unit,
    onSaveManual: (username: String, token: String) -> Unit,
    onClearOverride: () -> Unit,
    onAddGitHubAccount: () -> Unit,
    onAddGitLabAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalAppStrings.current
    val hasManualInitial = currentManualCredentials != null && selectedAccountId == null
    var manualMode by remember(selectedAccountId, currentManualCredentials) {
        mutableStateOf(hasManualInitial)
    }
    var pickedAccountId by remember(selectedAccountId) { mutableStateOf(selectedAccountId) }
    var username by remember(currentManualCredentials) {
        mutableStateOf(currentManualCredentials?.username.orEmpty())
    }
    var token by remember(currentManualCredentials) {
        mutableStateOf(currentManualCredentials?.tokenOrPassword.orEmpty())
    }
    var showToken by remember { mutableStateOf(false) }

    val hasAnyOverride = selectedAccountId != null || currentManualCredentials != null

    PocketFormDialog(
        title = title,
        icon = Icons.Rounded.Key,
        onDismiss = onDismiss,
        footerActions = {
            if (hasAnyOverride) {
                TextButton(
                    onClick = {
                        onClearOverride()
                        onDismiss()
                    }
                ) {
                    Text(s.oauthScopedClearOverride, color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            Button(
                onClick = {
                    if (manualMode) {
                        onSaveManual(username, token)
                        onDismiss()
                    } else if (!pickedAccountId.isNullOrBlank()) {
                        onSelectAccount(pickedAccountId!!)
                        onDismiss()
                    }
                },
                enabled = if (manualMode) token.isNotBlank() else !pickedAccountId.isNullOrBlank(),
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(s.commonSave)
            }
        }
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TwoOptionToggleRow(
            leftLabel = s.oauthScopedModeAccount,
            rightLabel = s.oauthScopedModeManual,
            rightSelected = manualMode,
            onSelect = { manualMode = it }
        )

        if (!manualMode) {
            if (connectedAccounts.isEmpty()) {
                Text(
                    text = s.oauthScopedNoAccountsHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    connectedAccounts.forEach { account ->
                        val selected = pickedAccountId == account.id
                        Surface(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickedAccountId = account.id }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (selected) {
                                        Icons.Rounded.CheckCircle
                                    } else {
                                        Icons.Rounded.RadioButtonUnchecked
                                    },
                                    contentDescription = null,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.shortLabel(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!account.email.isNullOrBlank()) {
                                        Text(
                                            text = account.email,
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Text(
                text = s.oauthScopedAddAccountTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onAddGitHubAccount()
                    },
                    shape = FormFieldShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GitHub", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onAddGitLabAccount()
                    },
                    shape = FormFieldShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GitLab", maxLines = 1)
                }
            }
        } else {
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
                label = { Text("Personal Access Token (PAT)") },
                singleLine = true,
                shape = FormFieldShape,
                colors = formTextFieldColors(),
                visualTransformation = if (showToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
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
}

@Composable
private fun TwoOptionToggleRow(
    leftLabel: String,
    rightLabel: String,
    rightSelected: Boolean,
    onSelect: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(false to leftLabel, true to rightLabel).forEach { (isRight, label) ->
            val selected = rightSelected == isRight
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(isRight) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
