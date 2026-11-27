package com.pocketworker.app.ui.settings

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketworker.app.core.ai.LocalModelDownloadState
import com.pocketworker.app.core.ai.LocalModelStatus
import com.pocketworker.app.core.ai.ProxyParseResult
import com.pocketworker.app.core.ai.parseAiProxyUrl
import com.pocketworker.app.core.git.GitOAuthAccount
import com.pocketworker.app.core.git.GitOAuthProvider
import com.pocketworker.app.core.i18n.AppLanguage
import com.pocketworker.app.core.i18n.LocalAppStrings
import com.pocketworker.app.core.storage.AiAgentSettings
import com.pocketworker.app.core.storage.AiProvider
import com.pocketworker.app.core.storage.AppThemeMode
import com.pocketworker.app.ui.common.CredentialsEditorDialog
import com.pocketworker.app.ui.common.FormFieldShape
import com.pocketworker.app.ui.common.PocketFormDialog
import com.pocketworker.app.ui.common.TopNotificationHost
import com.pocketworker.app.ui.common.WorkspaceBackdrop
import com.pocketworker.app.ui.common.appSwitchColors
import com.pocketworker.app.ui.common.backdropCardBorderColor
import com.pocketworker.app.ui.common.backdropCardContainerColor
import com.pocketworker.app.ui.common.formTextFieldColors
import com.pocketworker.app.ui.common.formatFileSize
import com.pocketworker.app.ui.common.rememberTopNotificationState
import com.pocketworker.app.ui.theme.GitSemanticColors
import com.pocketworker.app.ui.theme.LocalIsDarkTheme
import com.pocketworker.app.ui.workspace.AuthorConfigDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    initialTab: SettingsTab = SettingsTab.GENERAL,
    onOpenGuidePdf: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val notificationState = rememberTopNotificationState()
    val isDark = LocalIsDarkTheme.current
    val s = LocalAppStrings.current

    var activeTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }

    var showDefaultCredentialsDialog by remember { mutableStateOf(false) }
    var showProjectCredentialsDialog by remember { mutableStateOf(false) }
    var showProjectAuthorDialog by remember { mutableStateOf(false) }
    var showRepoCredentialsDialog by remember { mutableStateOf(false) }
    var showRepoAuthorDialog by remember { mutableStateOf(false) }
    var showAuthorDialog by remember { mutableStateOf(false) }
    var showGitHubOAuthDialog by remember { mutableStateOf(false) }
    var showGitLabOAuthDialog by remember { mutableStateOf(false) }
    var pendingOAuthBindScope by remember { mutableStateOf(OAuthAutoBindScope.NONE) }

    LaunchedEffect(state.lastOAuthCompletedEventId) {
        if (state.lastOAuthCompletedEventId > 0L) {
            showGitHubOAuthDialog = false
            showGitLabOAuthDialog = false
            pendingOAuthBindScope = OAuthAutoBindScope.NONE
        }
    }

    LaunchedEffect(state.infoMessage) {
        val msg = state.infoMessage
        if (msg != null) {
            notificationState.show(msg)
            viewModel.clearMessage()
        }
    }

    WorkspaceBackdrop(animatedCubesEnabled = state.preferences.animatedBackgroundEnabled) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            actionIconContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = s.commonBack
                                )
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = s.settingsTitle,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        activeTab == SettingsTab.AGENT -> s.settingsSubtitleAgent
                                        state.repoEntry != null -> s.settingsSubtitleRepo(state.repoEntry!!.name)
                                        state.projectName != null -> s.settingsSubtitleProject(state.projectName!!)
                                        else -> s.settingsSubtitleGeneral
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )

                    PrimaryTabRow(
                        selectedTabIndex = activeTab.ordinal,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        SettingsTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            Tab(
                                selected = selected,
                                onClick = { activeTab = tab },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = {
                                    Text(
                                        text = tab.localizedTitle(s),
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
            ) {
                when (activeTab) {
                    SettingsTab.GENERAL -> {
                        GeneralSettingsTabContent(
                            state = state,
                            isDark = isDark,
                            onThemeModeSelected = { viewModel.setThemeMode(it) },
                            onLanguageSelected = { viewModel.setLanguage(it) },
                            onAnimatedBackgroundToggle = { viewModel.setAnimatedBackgroundEnabled(it) },
                            onOpenDefaultCredentials = { showDefaultCredentialsDialog = true },
                            onOpenGlobalAuthor = { showAuthorDialog = true },
                            onOpenGitHubOAuth = {
                                pendingOAuthBindScope = OAuthAutoBindScope.NONE
                                viewModel.cancelOAuthFlow()
                                showGitHubOAuthDialog = true
                            },
                            onOpenGitLabOAuth = {
                                pendingOAuthBindScope = OAuthAutoBindScope.NONE
                                viewModel.cancelOAuthFlow()
                                showGitLabOAuthDialog = true
                            },
                            onSetDefaultOAuthAccount = { host, id ->
                                viewModel.setDefaultOAuthAccountForHost(host, id)
                            },
                            onRemoveOAuthAccount = { id ->
                                viewModel.removeOAuthAccount(id)
                            },
                            onOpenProjectCredentials = { showProjectCredentialsDialog = true },
                            onOpenProjectAuthor = { showProjectAuthorDialog = true },
                            onOpenRepoCredentials = { showRepoCredentialsDialog = true },
                            onOpenRepoAuthor = { showRepoAuthorDialog = true },
                            onOpenGuidePdf = onOpenGuidePdf,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    SettingsTab.AGENT -> {
                        AgentSettingsTabContent(
                            initialSettings = state.preferences.aiSettings,
                            initialApiKeys = state.aiApiKeys,
                            initialProxyUrl = state.aiProxyUrl,
                            localModelStates = state.localModelStates,
                            isDark = isDark,
                            onDownloadLocalModel = { modelId, useProxy, proxyUrl ->
                                viewModel.downloadLocalModel(modelId, useProxy, proxyUrl)
                            },
                            onCancelLocalModelDownload = { modelId ->
                                viewModel.cancelLocalModelDownload(modelId)
                            },
                            onDeleteLocalModel = { modelId ->
                                viewModel.deleteLocalModel(modelId)
                            },
                            onSave = { updatedSettings, updatedKeysByProvider, updatedProxyUrl ->
                                viewModel.saveAiSettings(updatedSettings, updatedKeysByProvider, updatedProxyUrl)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding()
                        )
                    }
                }

                TopNotificationHost(
                    state = notificationState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }

    if (showDefaultCredentialsDialog) {
        CredentialsEditorDialog(
            title = s.settingsDefaultTokenDialogTitle,
            description = s.settingsDefaultTokenDialogDesc,
            currentCredentials = state.defaultCredentials,
            onDismiss = { showDefaultCredentialsDialog = false },
            onSave = { user, token -> viewModel.saveDefaultCredentials(user, token) }
        )
    }

    if (showProjectCredentialsDialog && state.projectName != null) {
        ScopedGitAuthDialog(
            title = s.settingsProjectTokenTitle,
            description = s.settingsProjectTokenDialogDesc(state.projectName!!),
            connectedAccounts = state.oauthAccounts,
            selectedAccountId = state.projectOAuthAccount?.id,
            currentManualCredentials = state.projectCredentials,
            onSelectAccount = { accId -> viewModel.bindProjectOAuthAccount(accId) },
            onSaveManual = { user, token -> viewModel.saveProjectCredentials(user, token) },
            onClearOverride = { viewModel.bindProjectOAuthAccount(null) },
            onAddGitHubAccount = {
                pendingOAuthBindScope = OAuthAutoBindScope.PROJECT
                viewModel.cancelOAuthFlow()
                showGitHubOAuthDialog = true
            },
            onAddGitLabAccount = {
                pendingOAuthBindScope = OAuthAutoBindScope.PROJECT
                viewModel.cancelOAuthFlow()
                showGitLabOAuthDialog = true
            },
            onDismiss = { showProjectCredentialsDialog = false }
        )
    }

    val currentProjectName = state.projectName
    if (showProjectAuthorDialog && currentProjectName != null) {
        ProjectAuthorDialog(
            projectName = currentProjectName,
            currentName = state.projectAuthorName.orEmpty(),
            currentEmail = state.projectAuthorEmail.orEmpty(),
            hasOverride = !state.projectAuthorName.isNullOrBlank() || !state.projectAuthorEmail.isNullOrBlank(),
            onDismiss = { showProjectAuthorDialog = false },
            onSave = { name, email -> viewModel.saveProjectAuthor(name, email) },
            onReset = { viewModel.saveProjectAuthor("", "") }
        )
    }

    if (showRepoCredentialsDialog && state.repoEntry != null) {
        ScopedGitAuthDialog(
            title = s.settingsRepoTokenTitle,
            description = if (state.projectCredentials != null || state.projectOAuthAccount != null ||
                state.defaultCredentials != null || state.oauthAccounts.isNotEmpty()
            ) {
                s.gitRepoAuthFallbackHint
            } else {
                s.gitRepoAuthDirectHint
            },
            connectedAccounts = state.oauthAccounts,
            selectedAccountId = state.repoOAuthAccount?.id,
            currentManualCredentials = state.repoCredentials,
            onSelectAccount = { accId -> viewModel.bindRepoOAuthAccount(accId) },
            onSaveManual = { user, token -> viewModel.saveRepoCredentials(user, token) },
            onClearOverride = { viewModel.bindRepoOAuthAccount(null) },
            onAddGitHubAccount = {
                pendingOAuthBindScope = OAuthAutoBindScope.REPO
                viewModel.cancelOAuthFlow()
                showGitHubOAuthDialog = true
            },
            onAddGitLabAccount = {
                pendingOAuthBindScope = OAuthAutoBindScope.REPO
                viewModel.cancelOAuthFlow()
                showGitLabOAuthDialog = true
            },
            onDismiss = { showRepoCredentialsDialog = false }
        )
    }

    if (showGitHubOAuthDialog) {
        GitHubOAuthDialog(
            initialClientId = state.githubClientId,
            flowState = state.oauthFlowState,
            onStart = { clientId -> viewModel.startGitHubOAuth(clientId, pendingOAuthBindScope) },
            onDismiss = {
                showGitHubOAuthDialog = false
                pendingOAuthBindScope = OAuthAutoBindScope.NONE
                viewModel.cancelOAuthFlow()
            },
            onNotify = { msg -> viewModel.notifyInfo(msg) }
        )
    }

    if (showGitLabOAuthDialog) {
        GitLabOAuthDialog(
            initialCloudClientId = state.gitlabCloudClientId,
            flowState = state.oauthFlowState,
            resolveSavedClientIdForUrl = { url -> viewModel.getSavedGitLabClientIdForUrl(url) },
            onPrepareAuthUrl = { baseUrl, clientId ->
                viewModel.prepareGitLabOAuth(baseUrl, clientId, pendingOAuthBindScope)
            },
            onDismiss = {
                showGitLabOAuthDialog = false
                pendingOAuthBindScope = OAuthAutoBindScope.NONE
                viewModel.cancelOAuthFlow()
            },
            onNotify = { msg -> viewModel.notifyInfo(msg) }
        )
    }

    if (showRepoAuthorDialog && state.repoEntry != null) {
        AuthorConfigDialog(
            currentName = state.repoAuthorName,
            currentEmail = state.repoAuthorEmail,
            onDismiss = { showRepoAuthorDialog = false },
            onSave = { name, email, saveGlobally ->
                viewModel.saveRepoAuthor(name, email, saveGlobally)
            },
            onResetToDefault = if (state.repoHasCustomAuthor) {
                { viewModel.clearRepoAuthor() }
            } else {
                null
            }
        )
    }

    if (showAuthorDialog) {
        GlobalAuthorDialog(
            currentName = if (state.hasCustomGlobalAuthor) state.globalAuthorName else "",
            currentEmail = if (state.hasCustomGlobalAuthor) state.globalAuthorEmail else "",
            hasOverride = state.hasCustomGlobalAuthor,
            onDismiss = { showAuthorDialog = false },
            onSave = { name, email -> viewModel.saveGlobalAuthor(name, email) },
            onReset = { viewModel.clearGlobalAuthor() }
        )
    }
}

@Composable
private fun GeneralSettingsTabContent(
    state: SettingsUiState,
    isDark: Boolean,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onAnimatedBackgroundToggle: (Boolean) -> Unit,
    onOpenDefaultCredentials: () -> Unit,
    onOpenGlobalAuthor: () -> Unit,
    onOpenGitHubOAuth: () -> Unit,
    onOpenGitLabOAuth: () -> Unit,
    onSetDefaultOAuthAccount: (host: String, accountId: String) -> Unit,
    onRemoveOAuthAccount: (accountId: String) -> Unit,
    onOpenProjectCredentials: () -> Unit,
    onOpenProjectAuthor: () -> Unit,
    onOpenRepoCredentials: () -> Unit,
    onOpenRepoAuthor: () -> Unit,
    onOpenGuidePdf: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "appearance_card") {
            SettingsSectionCard(
                title = s.settingsAppearanceTitle,
                isDark = isDark
            ) {
                Text(
                    text = s.settingsThemeTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionTile(
                        icon = Icons.Rounded.BrightnessAuto,
                        label = s.settingsThemeAuto,
                        selected = state.preferences.themeMode == AppThemeMode.SYSTEM,
                        onClick = { onThemeModeSelected(AppThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionTile(
                        icon = Icons.Rounded.LightMode,
                        label = s.settingsThemeLight,
                        selected = state.preferences.themeMode == AppThemeMode.LIGHT,
                        onClick = { onThemeModeSelected(AppThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionTile(
                        icon = Icons.Rounded.DarkMode,
                        label = s.settingsThemeDark,
                        selected = state.preferences.themeMode == AppThemeMode.DARK,
                        onClick = { onThemeModeSelected(AppThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )

                Text(
                    text = s.settingsLanguageTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactOptionPill(
                        label = s.settingsLanguageSystem,
                        selected = state.preferences.language == AppLanguage.SYSTEM,
                        onClick = { onLanguageSelected(AppLanguage.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                    CompactOptionPill(
                        label = s.settingsLanguageRussian,
                        selected = state.preferences.language == AppLanguage.RU,
                        onClick = { onLanguageSelected(AppLanguage.RU) },
                        modifier = Modifier.weight(1f)
                    )
                    CompactOptionPill(
                        label = s.settingsLanguageEnglish,
                        selected = state.preferences.language == AppLanguage.EN,
                        onClick = { onLanguageSelected(AppLanguage.EN) },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onAnimatedBackgroundToggle(!state.preferences.animatedBackgroundEnabled)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIconBadge(icon = Icons.Rounded.AutoAwesome)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.settingsAnimatedBgTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = s.settingsAnimatedBgSubtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = state.preferences.animatedBackgroundEnabled,
                        onCheckedChange = onAnimatedBackgroundToggle,
                        colors = appSwitchColors()
                    )
                }
            }
        }

        item(key = "git_card") {
            val sectionDividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            SettingsSectionCard(
                title = s.settingsGitAndAuthTitle,
                isDark = isDark
            ) {
                // 1. Default
                Text(
                    text = s.settingsScopeDefault,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Layer 1: Connected OAuth accounts (GitHub & GitLab / Self-hosted)
                OAuthAccountsSection(
                    accounts = state.oauthAccounts,
                    defaultIdsByHost = state.defaultOAuthAccountIdsByHost,
                    onSignInGitHub = onOpenGitHubOAuth,
                    onSignInGitLab = onOpenGitLabOAuth,
                    onSetDefaultForHost = onSetDefaultOAuthAccount,
                    onRemoveAccount = onRemoveOAuthAccount
                )

                SettingsActionRow(
                    icon = Icons.Rounded.Key,
                    title = s.settingsDefaultTokenTitle,
                    subtitle = if (state.defaultCredentials != null) {
                        s.settingsTokenSavedInKeystore(state.defaultCredentials.username.ifBlank { "token" })
                    } else {
                        s.commonNotSet
                    },
                    onClick = onOpenDefaultCredentials
                )

                SettingsActionRow(
                    icon = Icons.Rounded.Person,
                    title = s.settingsDefaultAuthorTitle,
                    subtitle = if (state.hasCustomGlobalAuthor) {
                        "${state.globalAuthorName} <${state.globalAuthorEmail}>"
                    } else {
                        s.commonNotSet
                    },
                    onClick = onOpenGlobalAuthor
                )

                // 2. Project
                if (state.projectId != null && state.projectName != null) {
                    HorizontalDivider(
                        thickness = 1.5.dp,
                        color = sectionDividerColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Text(
                        text = s.settingsScopeProject(state.projectName),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    SettingsActionRow(
                        icon = Icons.Rounded.Key,
                        title = s.settingsProjectTokenTitle,
                        subtitle = when {
                            state.projectOAuthAccount != null -> state.projectOAuthAccount.shortLabel()
                            state.projectCredentials != null ->
                                s.settingsTokenSet(state.projectCredentials.username.ifBlank { "token" })
                            else -> s.commonNotSet
                        },
                        onClick = onOpenProjectCredentials
                    )

                    val hasProjectAuthor = !state.projectAuthorName.isNullOrBlank() ||
                        !state.projectAuthorEmail.isNullOrBlank()
                    SettingsActionRow(
                        icon = Icons.Rounded.Person,
                        title = s.settingsProjectAuthorTitle,
                        subtitle = if (hasProjectAuthor) {
                            "${state.projectAuthorName ?: state.globalAuthorName} <${state.projectAuthorEmail ?: state.globalAuthorEmail}>"
                        } else {
                            s.commonNotSet
                        },
                        onClick = onOpenProjectAuthor
                    )
                }

                // 3. Repository
                state.repoEntry?.let { repo ->
                    HorizontalDivider(
                        thickness = 1.5.dp,
                        color = sectionDividerColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Text(
                        text = s.settingsScopeRepo(repo.name),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    SettingsActionRow(
                        icon = Icons.Rounded.Key,
                        title = s.settingsRepoTokenTitle,
                        subtitle = when {
                            state.repoOAuthAccount != null -> state.repoOAuthAccount.shortLabel()
                            state.repoCredentials != null ->
                                s.settingsTokenSet(state.repoCredentials.username.ifBlank { "token" })
                            else -> s.commonNotSet
                        },
                        onClick = onOpenRepoCredentials
                    )

                    if (state.isGitRepo) {
                        SettingsActionRow(
                            icon = Icons.Rounded.Person,
                            title = s.settingsRepoAuthorTitle,
                            subtitle = if (state.repoHasCustomAuthor) {
                                "${state.repoAuthorName} <${state.repoAuthorEmail}>"
                            } else {
                                s.commonNotSet
                            },
                            onClick = onOpenRepoAuthor
                        )
                    }
                }
            }
        }

        item(key = "about_card") {
            SettingsSectionCard(
                title = s.settingsAboutTitle,
                isDark = isDark
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    SettingsIconBadge(icon = Icons.Rounded.Info)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PocketWorker",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = s.settingsVersionSubtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (onOpenGuidePdf != null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                    SettingsActionRow(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = s.settingsGuideTitle,
                        subtitle = s.settingsGuideSubtitle,
                        onClick = onOpenGuidePdf
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentSettingsTabContent(
    initialSettings: AiAgentSettings,
    initialApiKeys: Map<AiProvider, String>,
    initialProxyUrl: String,
    localModelStates: Map<String, LocalModelDownloadState>,
    isDark: Boolean,
    onDownloadLocalModel: (modelId: String, proxyEnabled: Boolean, proxyUrl: String) -> Unit,
    onCancelLocalModelDownload: (modelId: String) -> Unit,
    onDeleteLocalModel: (modelId: String) -> Unit,
    onSave: (AiAgentSettings, Map<AiProvider, String>, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var provider by remember(initialSettings) { mutableStateOf(initialSettings.provider) }
    var modelsDraft by remember(initialSettings) {
        mutableStateOf(
            AiProvider.entries.associateWith { item ->
                if (item == initialSettings.provider) {
                    item.resolveModelOrDefault(initialSettings.modelName)
                } else {
                    item.defaultModel
                }
            }
        )
    }
    var systemPrompt by remember(initialSettings) { mutableStateOf(initialSettings.systemPrompt) }
    var shareWorkspaceContext by remember(initialSettings) { mutableStateOf(initialSettings.shareWorkspaceContext) }
    var confirmFileEdits by remember(initialSettings) { mutableStateOf(initialSettings.confirmFileEdits) }
    var proxyEnabled by remember(initialSettings) { mutableStateOf(initialSettings.proxyEnabled) }
    var localUseGpu by remember(initialSettings) { mutableStateOf(initialSettings.localUseGpu) }
    var apiKeysDraft by remember(initialApiKeys) { mutableStateOf(initialApiKeys) }
    var proxyUrlDraft by remember(initialProxyUrl) { mutableStateOf(initialProxyUrl) }
    var keyVisible by remember { mutableStateOf(false) }
    var proxyVisible by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val s = LocalAppStrings.current
    val selectedModelOption = provider.findModelOption(modelsDraft[provider])
    val currentApiKey = apiKeysDraft[provider].orEmpty()
    val hasAnyLocalDownloaded = remember(localModelStates) {
        localModelStates.values.any { it.status == LocalModelStatus.DOWNLOADED }
    }
    val selectedLocalState = localModelStates[selectedModelOption.id]
        ?: LocalModelDownloadState(modelId = selectedModelOption.id)
    val proxyValidationError = remember(proxyEnabled, proxyUrlDraft, s) {
        if (!proxyEnabled || proxyUrlDraft.isBlank()) {
            null
        } else {
            (parseAiProxyUrl(proxyUrlDraft) as? ProxyParseResult.Invalid)?.message
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "agent_model_card") {
            SettingsSectionCard(
                title = s.settingsModelAndConnectionTitle,
                isDark = isDark
            ) {
                Text(
                    text = s.settingsProviderLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProvider.entries.chunked(2).forEach { rowProviders ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowProviders.forEach { item ->
                                val selected = provider == item
                                val isReadyBadge = if (item == AiProvider.LOCAL) {
                                    hasAnyLocalDownloaded
                                } else {
                                    !apiKeysDraft[item].isNullOrBlank()
                                }
                                val containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                                val contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Surface(
                                    color = containerColor,
                                    shape = RoundedCornerShape(14.dp),
                                    border = if (selected) {
                                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        null
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (provider != item) {
                                                provider = item
                                                keyVisible = false
                                                modelMenuExpanded = false
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 11.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isReadyBadge) {
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Icon(
                                                imageVector = if (item == AiProvider.LOCAL) {
                                                    Icons.Rounded.DownloadDone
                                                } else {
                                                    Icons.Rounded.Key
                                                },
                                                contentDescription = if (item == AiProvider.LOCAL) {
                                                    s.settingsProviderLocalDownloadedDesc
                                                } else {
                                                    s.settingsProviderKeySetDesc
                                                },
                                                tint = if (item == AiProvider.LOCAL) {
                                                    if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
                                                } else if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    contentColor.copy(alpha = 0.75f)
                                                },
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = modelMenuExpanded,
                    onExpandedChange = { modelMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedModelOption.label,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(s.settingsModelLabel) },
                        supportingText = {
                            Text(
                                text = "${selectedModelOption.id} • ${selectedModelOption.subtitle}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded)
                        },
                        shape = FormFieldShape,
                        colors = formTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        provider.models.forEach { option ->
                            val isSelected = option.id == selectedModelOption.id
                            val isLocalDownloaded = provider == AiProvider.LOCAL &&
                                localModelStates[option.id]?.status == LocalModelStatus.DOWNLOADED
                            DropdownMenuItem(
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = option.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                            )
                                            if (isLocalDownloaded) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DownloadDone,
                                                    contentDescription = s.settingsDownloadedDesc,
                                                    tint = if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = option.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = s.settingsSelectedDesc,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    modelsDraft = modelsDraft + (provider to option.id)
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (provider == AiProvider.LOCAL) {
                    LocalModelDownloadSection(
                        modelLabel = selectedModelOption.label,
                        downloadState = selectedLocalState,
                        localUseGpu = localUseGpu,
                        onLocalUseGpuChange = { localUseGpu = it },
                        isDark = isDark,
                        onDownload = {
                            onDownloadLocalModel(selectedModelOption.id, proxyEnabled, proxyUrlDraft)
                        },
                        onCancel = {
                            onCancelLocalModelDownload(selectedModelOption.id)
                        },
                        onDelete = {
                            onDeleteLocalModel(selectedModelOption.id)
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = currentApiKey,
                        onValueChange = { updated ->
                            apiKeysDraft = apiKeysDraft + (provider to updated)
                        },
                        label = { Text(provider.apiKeyLabel) },
                        placeholder = { Text(provider.apiKeyPlaceholder) },
                        supportingText = when (provider) {
                            AiProvider.GEMINI -> {
                                { Text(s.settingsGeminiDirectHint) }
                            }
                            AiProvider.OPENROUTER -> {
                                { Text(s.settingsOpenRouterHint) }
                            }
                            else -> null
                        },
                        singleLine = true,
                        shape = FormFieldShape,
                        colors = formTextFieldColors(),
                        visualTransformation = if (keyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = if (keyVisible) s.settingsHideKeyDesc else s.settingsShowKeyDesc
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { proxyEnabled = !proxyEnabled }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = proxyEnabled,
                        onCheckedChange = { proxyEnabled = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.settingsProxyTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (provider == AiProvider.LOCAL) {
                                s.settingsProxyLocalSubtitle
                            } else {
                                s.settingsProxyCloudSubtitle
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(
                    visible = proxyEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = proxyUrlDraft,
                        onValueChange = { proxyUrlDraft = it },
                        label = { Text(s.settingsProxyAddressLabel) },
                        placeholder = { Text("http://user:password@ip:port") },
                        isError = proxyValidationError != null,
                        supportingText = {
                            Text(
                                text = proxyValidationError ?: s.settingsProxyFormatHint
                            )
                        },
                        singleLine = true,
                        shape = FormFieldShape,
                        colors = formTextFieldColors(),
                        visualTransformation = if (proxyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { proxyVisible = !proxyVisible }) {
                                Icon(
                                    imageVector = if (proxyVisible) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = if (proxyVisible) s.settingsHideProxyDesc else s.settingsShowProxyDesc
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item(key = "agent_behavior_card") {
            SettingsSectionCard(
                title = s.settingsAgentBehaviorTitle,
                isDark = isDark
            ) {
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(s.settingsSystemPromptLabel) },
                    placeholder = { Text(s.settingsSystemPromptPlaceholder) },
                    minLines = 3,
                    maxLines = 6,
                    shape = FormFieldShape,
                    colors = formTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { shareWorkspaceContext = !shareWorkspaceContext }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.settingsShareContextTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = s.settingsShareContextSubtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = shareWorkspaceContext,
                        onCheckedChange = { shareWorkspaceContext = it },
                        colors = appSwitchColors()
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = s.settingsPermissionsTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactOptionPill(
                            label = s.settingsPermissionsAsk,
                            selected = confirmFileEdits,
                            onClick = { confirmFileEdits = true },
                            modifier = Modifier.weight(1f)
                        )
                        CompactOptionPill(
                            label = s.settingsPermissionsAuto,
                            selected = !confirmFileEdits,
                            onClick = { confirmFileEdits = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = s.settingsPermissionsSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "agent_save_btn") {
            Button(
                onClick = {
                    onSave(
                        AiAgentSettings(
                            provider = provider,
                            modelName = selectedModelOption.id,
                            systemPrompt = systemPrompt.trim(),
                            shareWorkspaceContext = shareWorkspaceContext,
                            confirmFileEdits = confirmFileEdits,
                            proxyEnabled = proxyEnabled,
                            localUseGpu = localUseGpu
                        ),
                        apiKeysDraft,
                        proxyUrlDraft
                    )
                },
                enabled = !proxyEnabled || (proxyUrlDraft.isNotBlank() && proxyValidationError == null),
                shape = FormFieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            ) {
                Text(s.settingsSaveAgentButton)
            }
        }
    }
}

@Composable
private fun LocalModelDownloadSection(
    modelLabel: String,
    downloadState: LocalModelDownloadState,
    localUseGpu: Boolean,
    onLocalUseGpuChange: (Boolean) -> Unit,
    isDark: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val memorySummary = remember(context) {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val info = ActivityManager.MemoryInfo()
                am.getMemoryInfo(info)
                info.totalMem to info.availMem
            } else null
        }.getOrNull()
    }
    val addedColor = if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
    var confirmDeleteOpen by remember(modelLabel) { mutableStateOf(false) }
    val borderColor = when (downloadState.status) {
        LocalModelStatus.DOWNLOADED -> addedColor.copy(alpha = 0.65f)
        LocalModelStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val surfaceColor = when (downloadState.status) {
        LocalModelStatus.DOWNLOADED -> if (isDark) {
            Color(0xFF0D2818).copy(alpha = 0.72f)
        } else {
            Color(0xFFECFDF5).copy(alpha = 0.9f)
        }
        LocalModelStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = surfaceColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (downloadState.status) {
                LocalModelStatus.DOWNLOADING -> {
                    val percent = (downloadState.progress * 100f).toInt().coerceIn(0, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = s.settingsLocalDownloading(modelLabel),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                    )

                    Text(
                        text = "${formatFileSize(downloadState.downloadedBytes)} / ${formatFileSize(downloadState.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onCancel,
                        shape = FormFieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.settingsLocalCancelDownload)
                    }
                }

                LocalModelStatus.DOWNLOADED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = addedColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.settingsLocalReadyTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = s.settingsLocalReadySubtitle(formatFileSize(downloadState.totalBytes)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { confirmDeleteOpen = true },
                        shape = FormFieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.settingsLocalDeleteButton)
                    }
                }

                LocalModelStatus.NOT_DOWNLOADED, LocalModelStatus.ERROR -> {
                    Text(
                        text = s.settingsLocalOfflineInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (downloadState.downloadedBytes > 0L && downloadState.totalBytes > 0L) {
                        Text(
                            text = s.settingsLocalResumeHint(
                                formatFileSize(downloadState.downloadedBytes),
                                formatFileSize(downloadState.totalBytes)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (downloadState.status == LocalModelStatus.ERROR && !downloadState.errorMessage.isNullOrBlank()) {
                        Text(
                            text = downloadState.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = onDownload,
                        shape = FormFieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (downloadState.downloadedBytes > 0L) {
                                s.settingsLocalContinueDownload
                            } else {
                                s.settingsLocalDownloadButton
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            )

            if (memorySummary != null) {
                val (totalBytes, availBytes) = memorySummary
                val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
                val availGb = availBytes / (1024.0 * 1024.0 * 1024.0)
                val totalGbStr = String.format(java.util.Locale.US, "%.1f", totalGb)
                val availGbStr = String.format(java.util.Locale.US, "%.1f", availGb)
                val ramHint = if (totalGb < 10.0) {
                    s.settingsLocalRamLowHint(totalGbStr, availGbStr)
                } else {
                    s.settingsLocalRamNormalHint(totalGbStr, availGbStr)
                }
                Text(
                    text = ramHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLocalUseGpuChange(!localUseGpu) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.settingsLocalGpuTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = s.settingsLocalGpuSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = localUseGpu,
                    onCheckedChange = onLocalUseGpuChange,
                    colors = appSwitchColors()
                )
            }
        }
    }

    if (confirmDeleteOpen) {
        AlertDialog(
            onDismissRequest = { confirmDeleteOpen = false },
            title = { Text(s.settingsLocalDeleteDialogTitle) },
            text = {
                Text(
                    text = s.settingsLocalDeleteDialogBody(
                        modelLabel,
                        formatFileSize(downloadState.totalBytes)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteOpen = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(s.commonDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteOpen = false }) {
                    Text(s.commonCancel)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    Surface(
        color = backdropCardContainerColor(isDark),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, backdropCardBorderColor(isDark)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun CompactOptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp, horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ThemeOptionTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsIconBadge(icon: ImageVector) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    interactive: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (interactive) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBadge(icon = icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GlobalAuthorDialog(
    currentName: String,
    currentEmail: String,
    hasOverride: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String) -> Unit,
    onReset: () -> Unit
) {
    val s = LocalAppStrings.current
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }

    PocketFormDialog(
        title = s.gitAuthorTitle,
        subtitle = s.settingsGlobalAuthorSubtitle,
        icon = Icons.Rounded.Person,
        onDismiss = onDismiss,
        footerActions = {
            if (hasOverride) {
                TextButton(
                    onClick = {
                        onReset()
                        onDismiss()
                    }
                ) {
                    Text(s.commonReset, color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            Button(
                onClick = {
                    onSave(name, email)
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
            label = { Text(s.repoAuthorNameLabel) },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(s.repoAuthorEmailLabel) },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProjectAuthorDialog(
    projectName: String,
    currentName: String,
    currentEmail: String,
    hasOverride: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String) -> Unit,
    onReset: () -> Unit
) {
    val s = LocalAppStrings.current
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }

    PocketFormDialog(
        title = s.settingsProjectAuthorTitle,
        subtitle = s.settingsProjectAuthorSubtitle(projectName),
        icon = Icons.Rounded.Person,
        onDismiss = onDismiss,
        footerActions = {
            if (hasOverride) {
                TextButton(
                    onClick = {
                        onReset()
                        onDismiss()
                    }
                ) {
                    Text(s.commonReset, color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onDismiss) { Text(s.commonCancel) }
            Button(
                onClick = {
                    onSave(name, email)
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
            label = { Text(s.repoAuthorNameLabel) },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(s.repoAuthorEmailLabel) },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OAuthAccountsSection(
    accounts: List<GitOAuthAccount>,
    defaultIdsByHost: Map<String, String>,
    onSignInGitHub: () -> Unit,
    onSignInGitLab: () -> Unit,
    onSetDefaultForHost: (host: String, accountId: String) -> Unit,
    onRemoveAccount: (accountId: String) -> Unit
) {
    val s = LocalAppStrings.current
    val countsByHost = remember(accounts) {
        accounts.groupingBy { it.normalizedHost }.eachCount()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = s.oauthAccountsTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (accounts.isEmpty()) {
            Text(
                text = s.oauthAccountsEmptyHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            accounts.forEach { account ->
                val sameHostCount = countsByHost[account.normalizedHost] ?: 1
                val isHostDefault = defaultIdsByHost[account.normalizedHost] == account.id ||
                    (defaultIdsByHost[account.normalizedHost] == null &&
                        accounts.firstOrNull { it.normalizedHost == account.normalizedHost }?.id == account.id)

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (sameHostCount > 1 && isHostDefault) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (sameHostCount > 1 && !isHostDefault) {
                                Modifier.clickable {
                                    onSetDefaultForHost(account.normalizedHost, account.id)
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(icon = Icons.Rounded.AccountCircle)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = if (account.provider == GitOAuthProvider.GITHUB) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = account.provider.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (account.provider == GitOAuthProvider.GITHUB) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = "@${account.username}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (sameHostCount > 1 && isHostDefault) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = s.oauthDefaultForHostBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            val subtitleText = buildString {
                                if (account.isSelfHosted) {
                                    append(account.normalizedHost)
                                    if (!account.email.isNullOrBlank()) append(" • ")
                                }
                                if (!account.email.isNullOrBlank()) {
                                    append(account.email)
                                }
                                if (sameHostCount > 1 && !isHostDefault) {
                                    if (isNotEmpty()) append(" • ")
                                    append(s.oauthMakeDefaultForHost)
                                }
                            }
                            if (subtitleText.isNotBlank()) {
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onRemoveAccount(account.id) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = s.oauthDisconnectAccountDesc,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSignInGitHub,
                shape = FormFieldShape,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GitHub",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = onSignInGitLab,
                shape = FormFieldShape,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GitLab",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}
