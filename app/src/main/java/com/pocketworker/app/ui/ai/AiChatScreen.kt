package com.pocketworker.app.ui.ai

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketworker.app.core.ai.AiAgentNotificationConstants
import com.pocketworker.app.core.ai.ChangedFileSummary
import com.pocketworker.app.core.mcp.McpPermissionTier
import com.pocketworker.app.ui.common.WorkspaceBackdrop
import com.pocketworker.app.ui.common.backdropCardBorderColor
import com.pocketworker.app.ui.common.backdropCardContainerColor
import com.pocketworker.app.ui.common.formatFileSize
import com.pocketworker.app.ui.theme.GitSemanticColors
import com.pocketworker.app.ui.theme.LocalIsDarkTheme
import com.pocketworker.app.ui.workspace.DiffDialogState
import com.pocketworker.app.ui.workspace.DiffViewerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import kotlin.math.hypot
import kotlin.math.max

data class AttachedFile(
    val uriString: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?
)

private class CircularRevealShape(
    private val progress: Float,
    private val originPx: Offset?,
    private val startRadiusPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = originPx?.x ?: (size.width / 2f)
        val cy = originPx?.y ?: (size.height / 2f)
        val maxDistanceX = max(cx, size.width - cx)
        val maxDistanceY = max(cy, size.height - cy)
        val endRadius = hypot(maxDistanceX, maxDistanceY) + 8f
        val clamped = progress.coerceIn(0f, 1f)
        val currentRadius = startRadiusPx + (endRadius - startRadiusPx) * clamped

        val path = Path().apply {
            addOval(Rect(center = Offset(cx, cy), radius = currentRadius))
        }
        return Outline.Generic(path)
    }
}

/**
 * Full-screen AI assistant window that morphs smoothly out of the circular floating AI button,
 * renders the soft gradient + floating cubes backdrop, side history drawer, live streaming
 * conversation with expandable MCP tool activity cards and confirmation gates, unified Settings
 * overlay (defaulting to Agent tab), and the calm circular Folder return button.
 */
@Composable
fun AiChatWindow(
    isOpen: Boolean,
    revealProgress: Float,
    revealOriginPx: Offset?,
    animatedBackgroundEnabled: Boolean,
    locationContext: AiLocationContext,
    viewModel: AiChatViewModel,
    folderButtonState: FloatingAiButtonState,
    onReturnToFolders: (originPx: Offset?) -> Unit,
    settingsOverlayContent: @Composable (onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen && revealProgress <= 0.001f) return

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val startRadiusPx = with(density) { 30.dp.toPx() }
    val draftTextByChat = remember { mutableStateMapOf<String, String>() }
    val attachedFilesByChat = remember { mutableStateMapOf<String, List<AttachedFile>>() }
    var showSettings by remember { mutableStateOf(false) }
    var activeDiffDialog by remember { mutableStateOf<DiffDialogState?>(null) }
    var notificationPermissionRequested by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            showSettings = false
            viewModel.refreshProviderBadge()
            viewModel.onChatWindowOpened(locationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !notificationPermissionRequested &&
                !AiAgentNotificationConstants.hasNotificationPermission(context)
            ) {
                notificationPermissionRequested = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val contentAlpha = ((revealProgress - 0.22f) / 0.52f).coerceIn(0f, 1f)
    val orbVeilAlpha = (1f - (revealProgress / 0.55f)).coerceIn(0f, 1f)

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxSize()
            .clip(
                CircularRevealShape(
                    progress = revealProgress,
                    originPx = revealOriginPx,
                    startRadiusPx = startRadiusPx
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha }
            ) {
                AiChatContent(
                    isWindowOpen = isOpen,
                    revealProgress = revealProgress,
                    animatedBackgroundEnabled = animatedBackgroundEnabled,
                    locationContext = locationContext,
                    uiState = uiState,
                    showSettings = showSettings,
                    onShowSettingsChange = { visible ->
                        showSettings = visible
                        if (!visible) {
                            viewModel.refreshProviderBadge()
                        }
                    },
                    folderButtonState = folderButtonState,
                    inputText = draftTextByChat[uiState.activeChatId].orEmpty(),
                    onInputTextChange = { draftTextByChat[uiState.activeChatId] = it },
                    attachedFiles = attachedFilesByChat[uiState.activeChatId].orEmpty(),
                    onAttachedFilesChange = { attachedFilesByChat[uiState.activeChatId] = it },
                    onSelectChat = { viewModel.selectChat(it) },
                    onStartNewChat = { viewModel.startNewChat(initialContextKey = locationContext.stableContextKey()) },
                    onRenameChat = { chatId, newTitle -> viewModel.renameChat(chatId, newTitle) },
                    onDeleteChat = { chatId ->
                        draftTextByChat.remove(chatId)
                        attachedFilesByChat.remove(chatId)
                        viewModel.deleteChat(chatId)
                    },
                    onSendMessage = { text, names, attachmentContext ->
                        draftTextByChat[uiState.activeChatId] = ""
                        attachedFilesByChat[uiState.activeChatId] = emptyList()
                        viewModel.sendMessage(
                            rawText = text,
                            attachmentsSummary = names,
                            attachmentsContextText = attachmentContext,
                            locationContext = locationContext
                        )
                    },
                    onStopGeneration = { viewModel.stopGeneration() },
                    onResolveConfirmation = { approved -> viewModel.resolveConfirmation(approved) },
                    onRetryUserMessage = { userMessageId ->
                        val targetChatId = uiState.activeChatId
                        viewModel.rewindToUserMessage(userMessageId) { restoredText ->
                            draftTextByChat[targetChatId] = restoredText
                        }
                    },
                    onOpenChangedFileDiff = { fileSummary ->
                        viewModel.loadChangedFileDiff(fileSummary) { dialogState ->
                            activeDiffDialog = dialogState
                        }
                    },
                    onClearInfoMessage = { viewModel.clearInfoMessage() },
                    onReturnToFolders = onReturnToFolders,
                    settingsOverlayContent = settingsOverlayContent
                )
            }

            activeDiffDialog?.let { diffState ->
                DiffViewerDialog(
                    state = diffState,
                    onDismiss = { activeDiffDialog = null }
                )
            }

            if (orbVeilAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = orbVeilAlpha }
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0EA5E9),
                                    Color(0xFF6366F1),
                                    Color(0xFFA855F7),
                                    Color(0xFF2DD4BF)
                                )
                            )
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatContent(
    isWindowOpen: Boolean,
    revealProgress: Float,
    animatedBackgroundEnabled: Boolean,
    locationContext: AiLocationContext,
    uiState: AiChatUiState,
    showSettings: Boolean,
    onShowSettingsChange: (Boolean) -> Unit,
    folderButtonState: FloatingAiButtonState,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    attachedFiles: List<AttachedFile>,
    onAttachedFilesChange: (List<AttachedFile>) -> Unit,
    onSelectChat: (String) -> Unit,
    onStartNewChat: () -> Unit,
    onRenameChat: (String, String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onSendMessage: (text: String, attachmentNames: List<String>, attachmentContext: String) -> Unit,
    onStopGeneration: () -> Unit,
    onResolveConfirmation: (Boolean) -> Unit,
    onRetryUserMessage: (String) -> Unit,
    onOpenChangedFileDiff: (ChangedFileSummary) -> Unit,
    onClearInfoMessage: () -> Unit,
    onReturnToFolders: (originPx: Offset?) -> Unit,
    settingsOverlayContent: @Composable (onBack: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()

    val activeChat = remember(uiState.sessions, uiState.activeChatId) {
        uiState.sessions.firstOrNull { it.id == uiState.activeChatId }
            ?: uiState.sessions.firstOrNull()
            ?: AiChatSession(id = uiState.activeChatId, title = AiChatSession.DEFAULT_TITLE)
    }
    val messages = uiState.activeMessages
    val greetingMessage = remember(locationContext) {
        buildAiGreetingMessage(locationContext)
    }
    val contextSubtitle = remember(locationContext) {
        formatAiContextSubtitle(locationContext)
    }
    val showLoaderBubble = uiState.isActiveChatBusy &&
        (uiState.pendingConfirmation == null || uiState.pendingConfirmation.chatId != uiState.activeChatId)

    LaunchedEffect(uiState.infoMessage) {
        val msg = uiState.infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onClearInfoMessage()
    }

    // Detect when the user has scrolled up away from the latest messages
    val showScrollToBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalCount = layoutInfo.totalItemsCount
            if (totalCount == 0) return@derivedStateOf false
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisible.index < totalCount - 1 ||
                (lastVisible.offset + lastVisible.size) > (layoutInfo.viewportEndOffset + 48)
        }
    }

    // Always jump to bottom when switching chats
    LaunchedEffect(uiState.activeChatId) {
        val totalItems = messages.size + 1 + if (showLoaderBubble) 1 else 0
        if (totalItems > 1) {
            listState.scrollToItem(totalItems - 1)
        }
    }

    // Auto-scroll smoothly as messages, streaming deltas, and loader arrive (unless user scrolled up to read history)
    val lastMessageSignature = remember(messages) {
        val last = messages.lastOrNull()
        when (last) {
            is AiChatMessageItem.AssistantText -> "${last.id}:${last.text.length}"
            is AiChatMessageItem.ToolActivity -> "${last.id}:${last.status}:${last.title}"
            is AiChatMessageItem.ActionsGroup -> "${last.id}:${last.actions.size}"
            is AiChatMessageItem.ChangedFilesCard -> "${last.id}:${last.files.size}"
            else -> last?.id ?: "empty"
        }
    }
    LaunchedEffect(
        messages.size,
        lastMessageSignature,
        showLoaderBubble,
        uiState.loadingStatusText
    ) {
        val totalItems = messages.size + 1 + if (showLoaderBubble) 1 else 0
        val userJustSent = messages.lastOrNull() is AiChatMessageItem.UserMessage
        if (totalItems > 1 && (userJustSent || !showScrollToBottom)) {
            listState.scrollToItem(totalItems - 1)
        }
    }

    LaunchedEffect(isWindowOpen) {
        if (isWindowOpen) {
            drawerState.snapTo(DrawerValue.Closed)
        }
    }

    BackHandler(enabled = isWindowOpen) {
        when {
            showSettings -> onShowSettingsChange(false)
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            else -> onReturnToFolders(folderButtonState.lastCenterPx)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                uris.map { uri -> resolveAttachedFile(context, uri) }
            }
            val existingUris = attachedFiles.map { it.uriString }.toSet()
            val newUnique = resolved.filter { it.uriString !in existingUris }
            if (newUnique.isNotEmpty()) {
                onAttachedFilesChange(attachedFiles + newUnique)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !showSettings && revealProgress > 0.85f,
            drawerContent = {
                AiChatHistoryDrawerSheet(
                    chatSessions = uiState.sessions,
                    activeChatId = activeChat.id,
                    onSelectChat = { chatId ->
                        onSelectChat(chatId)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onStartNewChat = {
                        onStartNewChat()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onRenameChat = onRenameChat,
                    onDeleteChat = onDeleteChat
                )
            }
        ) {
            WorkspaceBackdrop(animatedCubesEnabled = animatedBackgroundEnabled) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    contentWindowInsets = WindowInsets.navigationBars.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    ),
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent,
                                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                actionIconContentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        coroutineScope.launch { drawerState.open() }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Menu,
                                        contentDescription = "Меню чатов"
                                    )
                                }
                            },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IridescentAiOrb(
                                        size = 30.dp,
                                        label = "AI",
                                        textSize = 10.sp,
                                        glowIntensity = 0.4f
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = activeChat.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = contextSubtitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        onShowSettingsChange(true)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = "Настройки"
                                    )
                                }
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .consumeWindowInsets(padding)
                            .imePadding()
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 14.dp,
                                    end = 14.dp,
                                    top = 14.dp,
                                    bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item(key = "greeting_${activeChat.id}") {
                                    AiGreetingBubble(
                                        message = greetingMessage,
                                        modelBadge = "${uiState.activeProvider.displayName} • ${uiState.activeModelLabel}"
                                    )
                                }

                                items(messages, key = { it.id }) { item ->
                                    when (item) {
                                        is AiChatMessageItem.UserMessage -> UserMessageBubble(
                                            item = item,
                                            retryEnabled = !uiState.isActiveChatBusy,
                                            onRetryClick = {
                                                focusManager.clearFocus()
                                                onRetryUserMessage(item.id)
                                            }
                                        )
                                        is AiChatMessageItem.AssistantText -> AssistantTextBubble(item)
                                        is AiChatMessageItem.ToolActivity -> ToolActivityMessageCard(item)
                                        is AiChatMessageItem.ActionsGroup -> {
                                            if (item.actions.size == 1) {
                                                ToolActivityMessageCard(item.actions.single())
                                            } else {
                                                ActionsGroupCard(item)
                                            }
                                        }
                                        is AiChatMessageItem.ChangedFilesCard -> ChangedFilesCardSection(
                                            item = item,
                                            onFileClick = onOpenChangedFileDiff
                                        )
                                        is AiChatMessageItem.ErrorNotice -> ErrorNoticeBubble(
                                            item = item,
                                            onOpenSettings = {
                                                focusManager.clearFocus()
                                                onShowSettingsChange(true)
                                            }
                                        )
                                    }
                                }

                                if (showLoaderBubble) {
                                    item(key = "ai_loading_indicator") {
                                        AiThinkingLoaderBubble(statusText = uiState.loadingStatusText)
                                    }
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = showScrollToBottom,
                                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
                                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        coroutineScope.launch {
                                            val count = listState.layoutInfo.totalItemsCount
                                            if (count > 0) {
                                                listState.animateScrollToItem(count - 1)
                                            }
                                        }
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                    ),
                                    shadowElevation = 6.dp,
                                    tonalElevation = 4.dp
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Прокрутить вниз",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Вниз",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        val pending = uiState.pendingConfirmation
                        if (pending != null && pending.chatId == uiState.activeChatId) {
                            ToolConfirmationCard(
                                pending = pending,
                                onApprove = { onResolveConfirmation(true) },
                                onReject = { onResolveConfirmation(false) }
                            )
                        }

                        AiMessageComposer(
                            text = inputText,
                            onTextChange = onInputTextChange,
                            attachedFiles = attachedFiles,
                            isBusy = uiState.isActiveChatBusy,
                            onAttachClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            onRemoveAttachment = { uriString ->
                                onAttachedFilesChange(
                                    attachedFiles.filterNot { it.uriString == uriString }
                                )
                            },
                            onSendClick = {
                                focusManager.clearFocus()
                                val textToSend = inputText
                                val filesToSend = attachedFiles.toList()
                                coroutineScope.launch {
                                    val attachmentContext = if (filesToSend.isEmpty()) {
                                        ""
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            buildAttachmentContextText(context, filesToSend)
                                        }
                                    }
                                    onSendMessage(
                                        textToSend,
                                        filesToSend.map { it.name },
                                        attachmentContext
                                    )
                                }
                            },
                            onStopClick = onStopGeneration
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 240)
            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 220)
            ) + fadeOut(animationSpec = tween(durationMillis = 160))
        ) {
            settingsOverlayContent { onShowSettingsChange(false) }
        }

        if (!showSettings && !drawerState.isOpen && revealProgress > 0.68f) {
            FloatingFolderButtonOverlay(
                state = folderButtonState,
                onReturnToFolders = { originCenterPx ->
                    focusManager.clearFocus()
                    onReturnToFolders(originCenterPx)
                },
                modifier = Modifier.graphicsLayer {
                    alpha = ((revealProgress - 0.68f) / 0.32f).coerceIn(0f, 1f)
                }
            )
        }
    }
}

@Composable
private fun AiChatHistoryDrawerSheet(
    chatSessions: List<AiChatSession>,
    activeChatId: String,
    onSelectChat: (String) -> Unit,
    onStartNewChat: () -> Unit,
    onRenameChat: (String, String) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    var renamingSession by remember { mutableStateOf<AiChatSession?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deletingSession by remember { mutableStateOf<AiChatSession?>(null) }

    ModalDrawerSheet(
        windowInsets = DrawerDefaults.windowInsets,
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "История чатов",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(chatSessions, key = { it.id }) { session ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(
                                text = session.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (session.preview.isNotBlank()) {
                                Text(
                                    text = session.preview,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    badge = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    renamingSession = session
                                    renameInput = session.title
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Переименовать чат",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (chatSessions.size > 1 || session.preview.isNotBlank()) {
                                IconButton(
                                    onClick = { deletingSession = session },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Удалить чат",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    },
                    selected = session.id == activeChatId,
                    onClick = { onSelectChat(session.id) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            if (chatSessions.size <= 1) {
                item(key = "history_empty_hint") {
                    Text(
                        text = "История чатов сохраняется локально на устройстве",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        Button(
            onClick = onStartNewChat,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .heightIn(min = 52.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Новый чат",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    renamingSession?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingSession = null },
            title = { Text("Переименовать чат") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Название чата") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameChat(target.id, renameInput)
                        renamingSession = null
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingSession = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    deletingSession?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            title = { Text("Удалить диалог?") },
            text = {
                Text(
                    text = "Диалог «${target.title}» и вся его история сообщений будут удалены без возможности восстановления."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteChat(target.id)
                        deletingSession = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSession = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun AiGreetingBubble(
    message: String,
    modelBadge: String
) {
    val isDark = LocalIsDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 24.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = backdropCardContainerColor(isDark),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, backdropCardBorderColor(isDark)),
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = modelBadge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun UserMessageBubble(
    item: AiChatMessageItem.UserMessage,
    retryEnabled: Boolean,
    onRetryClick: () -> Unit
) {
    var retryMenuExpanded by remember(item.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 4.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                ),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 4.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        )
                    )
                    .clickable(enabled = retryEnabled) { retryMenuExpanded = true }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    if (item.attachmentNames.isNotEmpty()) {
                        Text(
                            text = "Файлы: ${item.attachmentNames.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = retryMenuExpanded,
                onDismissRequest = { retryMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Replay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Повторить",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Откатить изменения и вернуть в поле ввода",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        retryMenuExpanded = false
                        onRetryClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun AssistantTextBubble(item: AiChatMessageItem.AssistantText) {
    val isDark = LocalIsDarkTheme.current
    val clipboardManager = LocalClipboardManager.current
    var copiedMessage by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(copiedMessage) {
        if (copiedMessage) {
            delay(1600L)
            copiedMessage = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 14.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = backdropCardContainerColor(isDark),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, backdropCardBorderColor(isDark)),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.modelLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!item.isStreaming && item.text.isNotBlank()) {
                        Icon(
                            imageVector = if (copiedMessage) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "Копировать ответ",
                            tint = if (copiedMessage) {
                                if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            },
                            modifier = Modifier
                                .size(15.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(item.text))
                                    copiedMessage = true
                                }
                        )
                    }
                }
                SelectionContainer {
                    AiMarkdownText(
                        markdown = item.text,
                        isStreaming = item.isStreaming
                    )
                }
            }
        }
    }
}

@Composable
internal fun ActionsGroupCard(
    item: AiChatMessageItem.ActionsGroup,
    interactive: Boolean = true
) {
    val isDark = LocalIsDarkTheme.current
    var expanded by remember(item.id) { mutableStateOf(false) }

    val failedCount = remember(item.actions) {
        item.actions.count {
            it.status == ToolActivityStatus.FAILED || it.status == ToolActivityStatus.REJECTED
        }
    }
    val headerAccent = if (failedCount > 0) {
        MaterialTheme.colorScheme.error
    } else {
        if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
    }
    val compactSummary = remember(item.actions) {
        item.actions.take(3).joinToString(" • ") { it.title }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 20.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isDark) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (interactive) Modifier.clickable { expanded = !expanded } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = if (failedCount > 0) Icons.Rounded.ErrorOutline else Icons.Rounded.Checklist,
                        contentDescription = null,
                        tint = headerAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Действия (${item.actions.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!expanded && compactSummary.isNotBlank()) {
                            Text(
                                text = compactSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Свернуть действия" else "Развернуть действия",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                        item.actions.forEach { action ->
                            ToolActivityMessageCard(item = action, nested = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChangedFilesCardSection(
    item: AiChatMessageItem.ChangedFilesCard,
    onFileClick: (ChangedFileSummary) -> Unit,
    interactive: Boolean = true
) {
    if (item.files.isEmpty()) return

    val isDark = LocalIsDarkTheme.current
    val greenBorder = if (isDark) {
        Color(0xFF22C55E).copy(alpha = 0.72f)
    } else {
        Color(0xFF16A34A).copy(alpha = 0.72f)
    }
    val greenSurface = if (isDark) {
        Color(0xFF0D2818).copy(alpha = 0.80f)
    } else {
        Color(0xFFECFDF5).copy(alpha = 0.94f)
    }
    val nestedSurface = if (isDark) {
        Color(0xFF103320).copy(alpha = 0.78f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.72f)
    }
    val addedColor = if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
    val deletedColor = if (isDark) GitSemanticColors.DeletedDark else GitSemanticColors.DeletedLight

    // Single changed file: render directly without a redundant group wrapper
    if (item.files.size == 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 20.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            ChangedFileRowCard(
                file = item.files.single(),
                surfaceColor = greenSurface,
                borderColor = greenBorder,
                addedColor = addedColor,
                deletedColor = deletedColor,
                nested = false,
                interactive = interactive,
                onClick = { onFileClick(item.files.single()) }
            )
        }
        return
    }

    // Multiple changed files: collapsible group card matching ActionsGroupCard
    var expanded by remember(item.id) { mutableStateOf(false) }
    val totalAdded = remember(item.files) { item.files.sumOf { it.addedLines } }
    val totalDeleted = remember(item.files) { item.files.sumOf { it.deletedLines } }
    val compactFilesSummary = remember(item.files) {
        val names = item.files.take(3).joinToString(", ") { it.relativePath.substringAfterLast('/') }
        if (item.files.size > 3) "$names и ещё ${item.files.size - 3}" else names
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 20.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = greenSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.2.dp, greenBorder),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (interactive) Modifier.clickable { expanded = !expanded } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = addedColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Изменённые файлы (${item.files.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!expanded && compactFilesSummary.isNotBlank()) {
                            Text(
                                text = compactFilesSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "+$totalAdded",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = addedColor
                        )
                        Text(
                            text = "-$totalDeleted",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = deletedColor
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Свернуть файлы" else "Развернуть файлы",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    ) {
                        HorizontalDivider(color = greenBorder.copy(alpha = 0.35f))
                        item.files.forEach { file ->
                            ChangedFileRowCard(
                                file = file,
                                surfaceColor = nestedSurface,
                                borderColor = greenBorder.copy(alpha = 0.48f),
                                addedColor = addedColor,
                                deletedColor = deletedColor,
                                nested = true,
                                onClick = { onFileClick(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangedFileRowCard(
    file: ChangedFileSummary,
    surfaceColor: Color,
    borderColor: Color,
    addedColor: Color,
    deletedColor: Color,
    nested: Boolean,
    interactive: Boolean = true,
    onClick: () -> Unit
) {
    val fileName = file.relativePath.substringAfterLast('/')
    val parentDir = file.relativePath.substringBeforeLast('/', "")
    val subtitlePath = buildString {
        if (file.repoName.isNotBlank()) append(file.repoName)
        if (parentDir.isNotBlank()) {
            if (isNotEmpty()) append(" / ")
            append(parentDir)
        }
    }
    val shape = RoundedCornerShape(if (nested) 10.dp else 12.dp)

    Surface(
        shape = shape,
        color = surfaceColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (nested) 1.dp else 1.2.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (nested) Modifier else Modifier.widthIn(max = 360.dp))
            .clip(shape)
            .then(if (interactive) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (nested) 8.dp else 10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = addedColor,
                modifier = Modifier.size(if (nested) 16.dp else 18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitlePath.isNotBlank()) {
                    Text(
                        text = subtitlePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "+${file.addedLines}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = addedColor
                )
                Text(
                    text = "-${file.deletedLines}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = deletedColor
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Открыть diff",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AiThinkingLoaderBubble(statusText: String) {
    val isDark = LocalIsDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 24.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = backdropCardContainerColor(isDark),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, backdropCardBorderColor(isDark))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = statusText.ifBlank { "Думает..." },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToolActivityMessageCard(
    item: AiChatMessageItem.ToolActivity,
    nested: Boolean = false
) {
    val isDark = LocalIsDarkTheme.current
    var expanded by remember(item.id) { mutableStateOf(false) }

    val categoryIcon = when (item.category) {
        ToolActivityCategory.SEARCHING -> Icons.Rounded.Search
        ToolActivityCategory.WEB_SEARCH -> Icons.Rounded.Public
        ToolActivityCategory.READING -> Icons.Rounded.Description
        ToolActivityCategory.GIT -> Icons.Rounded.Terminal
        ToolActivityCategory.CHECKING -> Icons.Rounded.Verified
        ToolActivityCategory.DOING -> Icons.Rounded.Build
    }

    val accentColor = when (item.status) {
        ToolActivityStatus.SUCCEEDED -> Color(0xFF16A34A)
        ToolActivityStatus.FAILED, ToolActivityStatus.REJECTED -> MaterialTheme.colorScheme.error
        ToolActivityStatus.AWAITING_CONFIRMATION -> Color(0xFFD97706)
        ToolActivityStatus.STREAMING_ARGS, ToolActivityStatus.RUNNING -> MaterialTheme.colorScheme.primary
    }

    val hasDetails = item.argumentsPreview.isNotBlank() || !item.resultPreview.isNullOrBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (nested) Modifier else Modifier.padding(end = 20.dp)),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(if (nested) 11.dp else 14.dp),
            color = if (isDark) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (nested) 0.65f else 0.82f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (nested) 0.55f else 0.72f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .clickable(enabled = hasDetails) { expanded = !expanded }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (item.status) {
                        ToolActivityStatus.STREAMING_ARGS, ToolActivityStatus.RUNNING -> {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        ToolActivityStatus.SUCCEEDED -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Выполнено",
                                tint = accentColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        ToolActivityStatus.AWAITING_CONFIRMATION -> {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = "Подтверждение",
                                tint = accentColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        ToolActivityStatus.FAILED, ToolActivityStatus.REJECTED -> {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = "Ошибка",
                                tint = accentColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.subtitle.isNotBlank()) {
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (hasDetails) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Свернуть детали" else "Показать детали",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded && hasDetails,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        Text(
                            text = "Инструмент: ${item.toolName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (item.argumentsPreview.isNotBlank()) {
                            Text(
                                text = "Параметры:\n${item.argumentsPreview.take(900)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!item.resultPreview.isNullOrBlank()) {
                            Text(
                                text = "Результат:\n${item.resultPreview.take(1200)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorNoticeBubble(
    item: AiChatMessageItem.ErrorNotice,
    onOpenSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 20.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "Ошибка",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(18.dp)
                )
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (item.showSettingsAction) {
                TextButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Открыть настройки агента",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolConfirmationCard(
    pending: PendingToolConfirmation,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val isDangerous = pending.request.tier == McpPermissionTier.ALWAYS_CONFIRM
    val borderColor = if (isDangerous) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    }
    val containerColor = if (isDangerous) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val titleColor = if (isDangerous) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.2.dp, borderColor),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = titleColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isDangerous) "Требуется подтверждение (опасное действие)" else "Подтвердите действие агента",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor
                )
            }
            Text(
                text = pending.request.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pending.request.toolName,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text("Отмена")
                }
                Button(
                    onClick = onApprove,
                    colors = if (isDangerous) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text("Разрешить")
                }
            }
        }
    }
}

@Composable
private fun AiMessageComposer(
    text: String,
    onTextChange: (String) -> Unit,
    attachedFiles: List<AttachedFile>,
    isBusy: Boolean,
    onAttachClick: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val canSend = !isBusy && (text.isNotBlank() || attachedFiles.isNotEmpty())

    Surface(
        color = backdropCardContainerColor(isDark),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            if (attachedFiles.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(attachedFiles, key = { it.uriString }) { file ->
                        AttachedFileChip(
                            file = file,
                            onRemove = { onRemoveAttachment(file.uriString) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = onAttachClick,
                    enabled = !isBusy,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = "Прикрепить файл",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(if (isBusy) "Агент работает..." else "Напишите сообщение...")
                    },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isBusy) {
                    FilledIconButton(
                        onClick = onStopClick,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Остановить"
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onSendClick,
                        enabled = canSend,
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Отправить"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachedFileChip(
    file: AttachedFile,
    onRemove: () -> Unit
) {
    val icon = when {
        file.mimeType?.startsWith("image/") == true -> Icons.Rounded.Image
        file.mimeType?.startsWith("text/") == true -> Icons.Rounded.Description
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.widthIn(max = 150.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (file.sizeBytes >= 0L) {
                    Text(
                        text = formatFileSize(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Убрать файл",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun resolveAttachedFile(context: Context, uri: Uri): AttachedFile {
    var displayName: String? = null
    var sizeBytes = -1L

    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }
    }

    val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "file"

    return AttachedFile(
        uriString = uri.toString(),
        name = displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
        sizeBytes = sizeBytes,
        mimeType = mimeType
    )
}

private fun buildAttachmentContextText(
    context: Context,
    files: List<AttachedFile>
): String {
    val sb = StringBuilder()
    files.take(3).forEach { file ->
        val isLikelyText = file.mimeType?.startsWith("text/") == true ||
            file.mimeType == "application/json" ||
            file.mimeType == "application/xml" ||
            file.name.substringAfterLast('.', "").lowercase() in setOf(
                "txt", "md", "kt", "kts", "java", "js", "ts", "tsx", "py", "json", "xml", "yml", "yaml", "toml", "gradle", "sh", "sql", "html", "css"
            )
        if (isLikelyText) {
            val snippet = runCatching {
                context.contentResolver.openInputStream(Uri.parse(file.uriString))?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.take(2048).any { it.toInt() == 0 }) null
                    else String(bytes.take(32_768).toByteArray(), StandardCharsets.UTF_8)
                }
            }.getOrNull()
            if (!snippet.isNullOrBlank()) {
                sb.appendLine("[Прикреплённый файл: ${file.name}]")
                sb.appendLine("```")
                sb.appendLine(snippet)
                sb.appendLine("```")
            } else {
                sb.appendLine("[Прикреплённый файл: ${file.name}]")
            }
        } else {
            sb.appendLine("[Прикреплённый файл: ${file.name} (${file.mimeType ?: "binary"})]")
        }
    }
    return sb.toString().trim()
}
