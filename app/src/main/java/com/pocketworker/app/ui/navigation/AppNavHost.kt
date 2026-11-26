package com.pocketworker.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocketworker.app.AppContainer
import com.pocketworker.app.ui.ai.AiChatViewModel
import com.pocketworker.app.ui.ai.AiChatWindow
import com.pocketworker.app.ui.ai.AiLocationContext
import com.pocketworker.app.ui.ai.FloatingAiButtonOverlay
import com.pocketworker.app.ui.ai.rememberFloatingAiButtonState
import com.pocketworker.app.ui.home.HomeScreen
import com.pocketworker.app.ui.home.HomeViewModel
import com.pocketworker.app.ui.onboarding.OnboardingGuideScreen
import com.pocketworker.app.ui.projectdetail.ProjectDetailScreen
import com.pocketworker.app.ui.projectdetail.ProjectDetailViewModel
import com.pocketworker.app.ui.settings.SettingsScreen
import com.pocketworker.app.ui.settings.SettingsTab
import com.pocketworker.app.ui.settings.SettingsViewModel
import com.pocketworker.app.ui.workspace.WorkspaceScreen
import com.pocketworker.app.ui.workspace.WorkspaceTab
import com.pocketworker.app.ui.workspace.WorkspaceViewModel

private object Routes {
    const val HOME = "home"
    const val PROJECT = "project/{projectId}"
    const val REPO_WORKSPACE = "project/{projectId}/repo/{repoId}"
    const val SETTINGS = "settings?projectId={projectId}&repoId={repoId}&tab={tab}"

    fun project(projectId: String): String = "project/${Uri.encode(projectId)}"

    fun repoWorkspace(projectId: String, repoId: String): String =
        "project/${Uri.encode(projectId)}/repo/${Uri.encode(repoId)}"

    fun settings(
        projectId: String? = null,
        repoId: String? = null,
        tab: SettingsTab = SettingsTab.GENERAL
    ): String {
        val params = buildList {
            if (!projectId.isNullOrBlank()) add("projectId=${Uri.encode(projectId)}")
            if (!repoId.isNullOrBlank()) add("repoId=${Uri.encode(repoId)}")
            if (tab != SettingsTab.GENERAL) add("tab=${tab.name}")
        }
        return if (params.isEmpty()) "settings" else "settings?${params.joinToString("&")}"
    }
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val contextByEntryId = remember { mutableStateMapOf<String, AiLocationContext>() }
    val floatingOrbState = rememberFloatingAiButtonState()
    val appPreferences by container.preferencesStore.preferences.collectAsState()

    var isAiChatOpen by rememberSaveable { mutableStateOf(false) }
    var manualShowOnboardingPdf by rememberSaveable { mutableStateOf(false) }
    var aiRevealOrigin by remember { mutableStateOf<Offset?>(null) }
    val revealAnimatable = remember { Animatable(if (isAiChatOpen) 1f else 0f) }

    LaunchedEffect(isAiChatOpen) {
        if (isAiChatOpen) {
            revealAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing)
            )
        } else {
            revealAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
            )
        }
    }

    val aiChatViewModel: AiChatViewModel = viewModel(
        factory = AiChatViewModel.factory(
            preferencesStore = container.preferencesStore,
            credentialStore = container.credentialStore,
            mcpServer = container.mcpServer,
            chatHistoryStore = container.aiChatHistoryStore,
            undoManager = container.mcpUndoManager,
            backgroundCoordinator = container.agentBackgroundCoordinator,
            localModelManager = container.localModelManager,
            localGemmaClient = container.localGemmaClient,
            onWorkspaceMutated = { container.notifyWorkspaceChanged() }
        )
    )
    val workspaceMutationVersion by container.workspaceMutationVersion.collectAsState()
    val pendingOpenChatId by container.pendingOpenChatId.collectAsState()

    LaunchedEffect(pendingOpenChatId) {
        val targetChatId = pendingOpenChatId ?: return@LaunchedEffect
        aiChatViewModel.openChatFromNotification(targetChatId)
        isAiChatOpen = true
        container.consumeOpenAiChatRequest()
    }

    val activeLocationContext = currentBackStackEntry?.id?.let { contextByEntryId[it] }
        ?: AiLocationContext.AllProjects
    LaunchedEffect(activeLocationContext) {
        container.activeLocationContext = activeLocationContext
    }
    val (chatProjectId, chatRepoId) = when (val ctx = activeLocationContext) {
        is AiLocationContext.AllProjects -> null to null
        is AiLocationContext.ProjectFolder -> ctx.projectId to null
        is AiLocationContext.Repository -> ctx.projectId to ctx.repoId
        is AiLocationContext.File -> ctx.projectId to ctx.repoId
    }
    val showOnboardingPdf = !appPreferences.hasSeenOnboardingPdf || manualShowOnboardingPdf
    val isOnSettingsRoute = currentBackStackEntry?.destination?.route?.startsWith("settings") == true
    val showAiButton = !isAiChatOpen && !isOnSettingsRoute && !showOnboardingPdf && revealAnimatable.value <= 0.01f

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME
        ) {
            composable(Routes.HOME) { backStackEntry ->
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(
                        gitEngine = container.gitEngine,
                        registry = container.projectRegistry,
                        credentialStore = container.credentialStore
                    )
                )
                LaunchedEffect(workspaceMutationVersion) {
                    if (workspaceMutationVersion > 0L) {
                        homeViewModel.refreshProjects()
                    }
                }
                BindNavEntryAiContext(
                    backStackEntry = backStackEntry,
                    context = AiLocationContext.AllProjects,
                    contextByEntryId = contextByEntryId
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    animatedBackgroundEnabled = appPreferences.animatedBackgroundEnabled,
                    onOpenProject = { projectId ->
                        navController.safeNavigate(backStackEntry, Routes.project(projectId))
                    },
                    onOpenSettings = {
                        navController.safeNavigate(backStackEntry, Routes.settings())
                    }
                )
            }

            composable(
                route = Routes.PROJECT,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
                val detailViewModel: ProjectDetailViewModel = viewModel(
                    factory = ProjectDetailViewModel.factory(
                        projectId = projectId,
                        gitEngine = container.gitEngine,
                        registry = container.projectRegistry,
                        credentialStore = container.credentialStore,
                        groupDiscoveryService = container.groupDiscoveryService
                    )
                )
                LaunchedEffect(workspaceMutationVersion) {
                    if (workspaceMutationVersion > 0L) {
                        detailViewModel.refresh()
                    }
                }
                val detailState by detailViewModel.uiState.collectAsState()
                BindNavEntryAiContext(
                    backStackEntry = backStackEntry,
                    context = AiLocationContext.ProjectFolder(
                        projectId = projectId,
                        projectName = detailState.project?.name.orEmpty()
                    ),
                    contextByEntryId = contextByEntryId
                )
                ProjectDetailScreen(
                    viewModel = detailViewModel,
                    animatedBackgroundEnabled = appPreferences.animatedBackgroundEnabled,
                    onBack = { navController.safePopBackStack(backStackEntry) },
                    onOpenRepo = { repoId ->
                        navController.safeNavigate(backStackEntry, Routes.repoWorkspace(projectId, repoId))
                    },
                    onOpenSettings = {
                        navController.safeNavigate(backStackEntry, Routes.settings(projectId))
                    }
                )
            }

            composable(
                route = Routes.REPO_WORKSPACE,
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType },
                    navArgument("repoId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
                val repoId = backStackEntry.arguments?.getString("repoId") ?: return@composable
                val workspaceViewModel: WorkspaceViewModel = viewModel(
                    factory = WorkspaceViewModel.factory(
                        projectId = projectId,
                        repoId = repoId,
                        gitEngine = container.gitEngine,
                        fileManager = container.fileManager,
                        registry = container.projectRegistry,
                        credentialStore = container.credentialStore
                    )
                )
                LaunchedEffect(workspaceMutationVersion) {
                    if (workspaceMutationVersion > 0L) {
                        workspaceViewModel.loadAll(resetHistoryRef = false)
                    }
                }
                var wasAiChatOpenInWorkspace by remember { mutableStateOf(false) }
                LaunchedEffect(isAiChatOpen) {
                    if (wasAiChatOpenInWorkspace && !isAiChatOpen) {
                        workspaceViewModel.reloadOpenFileFromDisk()
                    }
                    wasAiChatOpenInWorkspace = isAiChatOpen
                }
                val workspaceState by workspaceViewModel.uiState.collectAsState()
                val activeContext = workspaceState.fileEditor?.let { editor ->
                    AiLocationContext.File(
                        projectId = projectId,
                        projectName = workspaceState.parentProjectName,
                        repoId = repoId,
                        repoName = workspaceState.project?.name.orEmpty(),
                        filePath = editor.relativePath
                    )
                } ?: AiLocationContext.Repository(
                    projectId = projectId,
                    projectName = workspaceState.parentProjectName,
                    repoId = repoId,
                    repoName = workspaceState.project?.name.orEmpty(),
                    currentDir = if (workspaceState.activeTab == WorkspaceTab.FILES) {
                        workspaceState.currentDir
                    } else {
                        ""
                    }
                )
                BindNavEntryAiContext(
                    backStackEntry = backStackEntry,
                    context = activeContext,
                    contextByEntryId = contextByEntryId
                )
                WorkspaceScreen(
                    viewModel = workspaceViewModel,
                    onBackToProjects = { navController.safePopBackStack(backStackEntry) },
                    onOpenSettings = {
                        navController.safeNavigate(
                            backStackEntry,
                            Routes.settings(projectId = projectId, repoId = repoId)
                        )
                    }
                )
            }

            composable(
                route = Routes.SETTINGS,
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("repoId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("tab") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId")
                val repoId = backStackEntry.arguments?.getString("repoId")
                val initialTab = backStackEntry.arguments?.getString("tab")
                    ?.let { raw -> runCatching { SettingsTab.valueOf(raw) }.getOrNull() }
                    ?: SettingsTab.GENERAL
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(
                        projectId = projectId,
                        repoId = repoId,
                        preferencesStore = container.preferencesStore,
                        credentialStore = container.credentialStore,
                        registry = container.projectRegistry,
                        gitEngine = container.gitEngine,
                        localModelManager = container.localModelManager,
                        onLocalProviderDeselected = { container.localGemmaClient.releaseEngine() }
                    )
                )
                val settingsState by settingsViewModel.uiState.collectAsState()
                val fallbackContext = when {
                    !projectId.isNullOrBlank() && !repoId.isNullOrBlank() ->
                        AiLocationContext.Repository(
                            projectId = projectId,
                            projectName = settingsState.projectName.orEmpty(),
                            repoId = repoId,
                            repoName = settingsState.repoEntry?.name.orEmpty(),
                            currentDir = ""
                        )
                    !projectId.isNullOrBlank() ->
                        AiLocationContext.ProjectFolder(
                            projectId = projectId,
                            projectName = settingsState.projectName.orEmpty()
                        )
                    else -> AiLocationContext.AllProjects
                }
                BindNavEntryAiContext(
                    backStackEntry = backStackEntry,
                    context = fallbackContext,
                    contextByEntryId = contextByEntryId
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    initialTab = initialTab,
                    onOpenGuidePdf = { manualShowOnboardingPdf = true },
                    onBack = { navController.safePopBackStack(backStackEntry) }
                )
            }
        }

        if (showAiButton) {
            FloatingAiButtonOverlay(
                state = floatingOrbState,
                onOpenAiChat = { originCenterPx ->
                    aiRevealOrigin = originCenterPx
                    isAiChatOpen = true
                },
                modifier = Modifier.zIndex(100f)
            )
        }

        AiChatWindow(
            isOpen = isAiChatOpen,
            revealProgress = revealAnimatable.value,
            revealOriginPx = aiRevealOrigin,
            animatedBackgroundEnabled = appPreferences.animatedBackgroundEnabled,
            locationContext = activeLocationContext,
            viewModel = aiChatViewModel,
            folderButtonState = floatingOrbState,
            onReturnToFolders = { originCenterPx ->
                (originCenterPx ?: floatingOrbState.lastCenterPx)?.let { aiRevealOrigin = it }
                isAiChatOpen = false
            },
            settingsOverlayContent = { onBack ->
                val chatSettingsViewModel: SettingsViewModel = viewModel(
                    key = "chat_settings_${chatProjectId.orEmpty()}_${chatRepoId.orEmpty()}",
                    factory = SettingsViewModel.factory(
                        projectId = chatProjectId,
                        repoId = chatRepoId,
                        preferencesStore = container.preferencesStore,
                        credentialStore = container.credentialStore,
                        registry = container.projectRegistry,
                        gitEngine = container.gitEngine,
                        localModelManager = container.localModelManager,
                        onLocalProviderDeselected = { container.localGemmaClient.releaseEngine() }
                    )
                )
                LaunchedEffect(Unit) {
                    chatSettingsViewModel.loadData()
                }
                SettingsScreen(
                    viewModel = chatSettingsViewModel,
                    initialTab = SettingsTab.AGENT,
                    onOpenGuidePdf = { manualShowOnboardingPdf = true },
                    onBack = onBack
                )
            },
            modifier = Modifier.zIndex(200f)
        )

        AnimatedVisibility(
            visible = showOnboardingPdf,
            enter = fadeIn(animationSpec = tween(durationMillis = 220)),
            exit = fadeOut(animationSpec = tween(durationMillis = 280)),
            modifier = Modifier.zIndex(300f)
        ) {
            OnboardingGuideScreen(
                onFinish = {
                    if (!appPreferences.hasSeenOnboardingPdf) {
                        container.preferencesStore.setOnboardingPdfSeen(true)
                    }
                    manualShowOnboardingPdf = false
                }
            )
        }
    }
}

/**
 * Prevents double-tap / exit-animation back clicks from popping the root destination
 * (which otherwise leaves NavHost with an empty back stack and a blank screen).
 */
private fun NavHostController.safePopBackStack(fromEntry: NavBackStackEntry) {
    if (fromEntry.lifecycle.currentState != Lifecycle.State.RESUMED) return
    if (currentBackStackEntry?.id != fromEntry.id) return
    if (previousBackStackEntry == null) return
    popBackStack()
}

/**
 * Prevents rapid double-taps on list cards or action buttons from pushing duplicate screens.
 */
private fun NavHostController.safeNavigate(fromEntry: NavBackStackEntry, route: String) {
    if (fromEntry.lifecycle.currentState != Lifecycle.State.RESUMED) return
    if (currentBackStackEntry?.id != fromEntry.id) return
    navigate(route) {
        launchSingleTop = true
    }
}

@Composable
private fun BindNavEntryAiContext(
    backStackEntry: NavBackStackEntry,
    context: AiLocationContext,
    contextByEntryId: MutableMap<String, AiLocationContext>
) {
    LaunchedEffect(backStackEntry.id, context) {
        contextByEntryId[backStackEntry.id] = context
    }
    DisposableEffect(backStackEntry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                contextByEntryId.remove(backStackEntry.id)
            }
        }
        backStackEntry.lifecycle.addObserver(observer)
        onDispose {
            backStackEntry.lifecycle.removeObserver(observer)
        }
    }
}
