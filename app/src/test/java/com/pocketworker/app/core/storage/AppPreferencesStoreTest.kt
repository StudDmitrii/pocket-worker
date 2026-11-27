package com.pocketworker.app.core.storage

import com.pocketworker.app.core.git.AndroidSystemReader
import com.pocketworker.app.core.git.JGitEngine
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.i18n.AppLanguage
import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesStoreTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempRoot = Files.createTempDirectory("pocketworker-prefs-test").toFile()
        AndroidSystemReader.install(File(tempRoot, "sysconfig"))
    }

    @After
    fun tearDown() {
        AppLocale.setLanguage(AppLanguage.SYSTEM)
        Dispatchers.resetMain()
        tempRoot.deleteRecursively()
    }

    @Test
    fun `preferences store updates theme mode, language, and animated background flag`() {
        val store = InMemoryAppPreferencesStore()
        assertEquals(AppThemeMode.SYSTEM, store.preferences.value.themeMode)
        assertEquals(AppLanguage.SYSTEM, store.preferences.value.language)
        assertTrue(store.preferences.value.animatedBackgroundEnabled)

        store.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, store.preferences.value.themeMode)

        store.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, store.preferences.value.themeMode)

        store.setLanguage(AppLanguage.EN)
        assertEquals(AppLanguage.EN, store.preferences.value.language)
        store.setLanguage(AppLanguage.RU)
        assertEquals(AppLanguage.RU, store.preferences.value.language)
        store.setLanguage(AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, store.preferences.value.language)

        store.setAnimatedBackgroundEnabled(false)
        assertFalse(store.preferences.value.animatedBackgroundEnabled)

        assertFalse(store.preferences.value.hasSeenOnboardingPdf)
        store.setOnboardingPdfSeen(true)
        assertTrue(store.preferences.value.hasSeenOnboardingPdf)
    }

    @Test
    fun `settings viewModel loads project and updates credentials and theme`() = runTest(testDispatcher) {
        val prefsStore = InMemoryAppPreferencesStore()
        val credStore = InMemoryCredentialStore()
        val registry = ProjectRegistry(
            metadataFile = File(tempRoot, "projects.json"),
            projectsRootDir = File(tempRoot, "projects")
        )
        val project = registry.createProject("Mobile App")

        val vm = SettingsViewModel(
            projectId = project.id,
            preferencesStore = prefsStore,
            credentialStore = credStore,
            registry = registry,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        assertEquals("Mobile App", vm.uiState.value.projectName)

        vm.setThemeMode(AppThemeMode.DARK)
        vm.setLanguage(AppLanguage.EN)
        vm.setAnimatedBackgroundEnabled(false)
        vm.saveDefaultCredentials("gituser", "pat-123")
        vm.saveProjectCredentials("projuser", "pat-456")
        advanceUntilIdle()

        assertEquals(AppThemeMode.DARK, prefsStore.preferences.value.themeMode)
        assertEquals(AppLanguage.EN, prefsStore.preferences.value.language)
        assertFalse(prefsStore.preferences.value.animatedBackgroundEnabled)
        assertNotNull(credStore.getDefaultCredentials())
        assertEquals("pat-123", credStore.getDefaultCredentials()?.tokenOrPassword)
        assertEquals("pat-456", credStore.getProjectCredentials(project.id)?.tokenOrPassword)
    }

    @Test
    fun `providers order is Claude, Gemini, OpenRouter, OpenAI, Local with valid model lists and per-provider keys`() {
        assertEquals(
            listOf("Claude", "Gemini", "OpenRouter", "OpenAI", "Локальный"),
            AiProvider.entries.map { it.displayName }
        )
        assertEquals(
            listOf(
                "gemini-4-neon",
                "gemini-4-carbon",
                "gemini-4-hydrogen",
                "gemini-3.5-flash",
                "gemini-3.5-flash-lite",
                "gemini-3.1-pro-preview"
            ),
            AiProvider.GEMINI.models.map { it.id }
        )
        assertEquals("https://generativelanguage.googleapis.com/v1beta", AiProvider.GEMINI.defaultBaseUrl)
        assertEquals(
            listOf("gemma-4-E2B-it", "gemma-4-E4B-it"),
            AiProvider.LOCAL.models.map { it.id }
        )
        assertFalse(AiProvider.LOCAL.requiresApiKey)
        AiProvider.entries.forEach { p ->
            assertTrue(p.models.isNotEmpty())
            assertEquals(p.defaultModel, p.resolveModelOrDefault("unknown-legacy-model"))
            assertEquals(p.models.last().id, p.resolveModelOrDefault(p.models.last().id))
        }
        assertEquals(
            listOf(
                "particle-ai/particle-1:free",
                "nvidia/nemotron-3-ultra-550b-a55b:free",
                "google/gemma-4-31b-it:free",
                "inclusionai/ling-3.2-flash:free",
                "baidu/ernie-5.5-flash-preview:free"
            ),
            AiProvider.OPENROUTER.models.map { it.id }
        )
        assertEquals(
            "particle-ai/particle-1:free",
            AiProvider.OPENROUTER.resolveModelOrDefault("google/gemini-4-neon")
        )
        assertEquals(
            "baidu/ernie-5.5-flash-preview:free",
            AiProvider.OPENROUTER.resolveModelOrDefault("baidu/ernie-5.5-flash-preview:free")
        )

        val prefsStore = InMemoryAppPreferencesStore()
        val credStore = InMemoryCredentialStore()

        val custom = AiAgentSettings(
            provider = AiProvider.OPENAI,
            modelName = "gpt-6.2-sol",
            systemPrompt = "Always write unit tests.",
            shareWorkspaceContext = false,
            confirmFileEdits = false,
            proxyEnabled = true
        )
        prefsStore.updateAiSettings(custom)
        assertEquals(custom, prefsStore.preferences.value.aiSettings)

        assertNull(credStore.getAiApiKey(AiProvider.GEMINI))
        assertNull(credStore.getAiApiKey(AiProvider.ANTHROPIC))

        credStore.saveAiApiKey(AiProvider.GEMINI, "AIza-gemini-key")
        assertEquals("AIza-gemini-key", credStore.getAiApiKey(AiProvider.GEMINI))
        assertNull(credStore.getAiApiKey(AiProvider.ANTHROPIC))
        assertNull(credStore.getAiApiKey(AiProvider.OPENAI))

        credStore.saveAiApiKey(AiProvider.ANTHROPIC, "sk-ant-claude-key")
        assertEquals("AIza-gemini-key", credStore.getAiApiKey(AiProvider.GEMINI))
        assertEquals("sk-ant-claude-key", credStore.getAiApiKey(AiProvider.ANTHROPIC))

        credStore.clearAiApiKey(AiProvider.GEMINI)
        assertNull(credStore.getAiApiKey(AiProvider.GEMINI))
        assertEquals("sk-ant-claude-key", credStore.getAiApiKey(AiProvider.ANTHROPIC))
    }

    @Test
    fun `settings viewModel loads repository and updates repo credentials and author`() = runTest(testDispatcher) {
        val prefsStore = InMemoryAppPreferencesStore()
        val credStore = InMemoryCredentialStore()
        val registry = ProjectRegistry(
            metadataFile = File(tempRoot, "projects.json"),
            projectsRootDir = File(tempRoot, "projects")
        )
        val gitEngine = JGitEngine()
        val project = registry.createProject("Backend")
        val (repoId, repoDir) = registry.allocateRepositoryDir(project.id, "api-service")
        gitEngine.initProject(targetDir = repoDir, createReadmeAndCommit = false)
        registry.registerRepository(
            RepoEntry(
                id = repoId,
                projectId = project.id,
                name = "api-service",
                directoryPath = repoDir.absolutePath,
                remoteUrl = null,
                createdAt = System.currentTimeMillis()
            )
        )

        val vm = SettingsViewModel(
            projectId = project.id,
            repoId = repoId,
            preferencesStore = prefsStore,
            credentialStore = credStore,
            registry = registry,
            gitEngine = gitEngine,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        assertEquals("api-service", vm.uiState.value.repoEntry?.name)
        assertTrue(vm.uiState.value.isGitRepo)

        vm.saveRepoCredentials("repoUser", "repo-pat-789")
        vm.saveRepoAuthor("Alice Dev", "alice@example.com", saveGlobally = false)
        advanceUntilIdle()

        assertEquals("repo-pat-789", credStore.getRepoCredentials(repoId)?.tokenOrPassword)
        assertEquals("Alice Dev", vm.uiState.value.repoAuthorName)
        assertEquals("alice@example.com", vm.uiState.value.repoAuthorEmail)

        val aiSettings = AiAgentSettings(
            provider = AiProvider.ANTHROPIC,
            modelName = "claude-sonnet-5-5",
            systemPrompt = "Be concise.",
            shareWorkspaceContext = true,
            confirmFileEdits = false,
            proxyEnabled = true
        )
        vm.saveAiSettings(
            aiSettings,
            mapOf(
                AiProvider.GEMINI to "sk-or-v1-111",
                AiProvider.ANTHROPIC to "sk-ant-999"
            ),
            proxyUrl = "http://user:pass@1.2.3.4:8080"
        )
        advanceUntilIdle()

        assertEquals(aiSettings, prefsStore.preferences.value.aiSettings)
        assertEquals("sk-ant-999", credStore.getAiApiKey(AiProvider.ANTHROPIC))
        assertEquals("sk-or-v1-111", credStore.getAiApiKey(AiProvider.GEMINI))
        assertNull(credStore.getAiApiKey(AiProvider.OPENAI))
        assertEquals("http://user:pass@1.2.3.4:8080", credStore.getAiProxyUrl())
        assertEquals("http://user:pass@1.2.3.4:8080", vm.uiState.value.aiProxyUrl)
        assertEquals("sk-ant-999", vm.uiState.value.aiApiKeys[AiProvider.ANTHROPIC])
        assertEquals("sk-or-v1-111", vm.uiState.value.aiApiKeys[AiProvider.GEMINI])
        assertNull(vm.uiState.value.aiApiKeys[AiProvider.OPENAI])
    }

    @Test
    fun `resolves Git commit author in 3-tier precedence Default, Project, Repository and persists project author`() = runTest(testDispatcher) {
        val prefsStore = InMemoryAppPreferencesStore()
        val credStore = InMemoryCredentialStore()
        val gitEngine = JGitEngine()
        val registry = ProjectRegistry(
            metadataFile = File(tempRoot, "projects_author.json"),
            projectsRootDir = File(tempRoot, "projects_author")
        )
        val project = registry.createProject("Work")
        val (repoId, repoDir) = registry.allocateRepositoryDir(project.id, "core")
        gitEngine.initProject(
            targetDir = repoDir,
            initialBranch = "main",
            initGit = true,
            createReadmeAndCommit = false,
            projectName = "core"
        )
        gitEngine.clearLocalAuthorConfig(repoDir)
        registry.registerRepository(
            RepoEntry(
                id = repoId,
                projectId = project.id,
                name = "core",
                directoryPath = repoDir.absolutePath,
                remoteUrl = null,
                createdAt = System.currentTimeMillis()
            )
        )

        val vm = SettingsViewModel(
            projectId = project.id,
            repoId = repoId,
            preferencesStore = prefsStore,
            credentialStore = credStore,
            registry = registry,
            gitEngine = gitEngine,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        // Initially no custom global author is configured in ~/.gitconfig
        assertFalse(vm.uiState.value.hasCustomGlobalAuthor)

        // 1. Default global author applies when neither project nor repo overrides
        vm.saveGlobalAuthor("Global User", "global@example.com")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasCustomGlobalAuthor)
        assertFalse(vm.uiState.value.repoHasCustomAuthor)
        assertEquals("Global User", vm.uiState.value.repoAuthorName)
        assertEquals("global@example.com", vm.uiState.value.repoAuthorEmail)

        // 2. Project author overrides default and persists in ProjectRegistry
        vm.saveProjectAuthor("Project Lead", "lead@work.com")
        advanceUntilIdle()
        assertEquals("Project Lead", vm.uiState.value.projectAuthorName)
        assertEquals("lead@work.com", vm.uiState.value.projectAuthorEmail)
        assertEquals("Project Lead", vm.uiState.value.repoAuthorName)
        assertEquals("lead@work.com", vm.uiState.value.repoAuthorEmail)

        val reloadedProject = ProjectRegistry(
            metadataFile = File(tempRoot, "projects_author.json"),
            projectsRootDir = File(tempRoot, "projects_author")
        ).findProjectById(project.id)
        assertEquals("Project Lead", reloadedProject?.authorName)
        assertEquals("lead@work.com", reloadedProject?.authorEmail)

        // 3. Repo local author overrides project author
        vm.saveRepoAuthor("Repo Specific", "repo@work.com", saveGlobally = false)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.repoHasCustomAuthor)
        assertEquals("Repo Specific", vm.uiState.value.repoAuthorName)
        assertEquals("repo@work.com", vm.uiState.value.repoAuthorEmail)

        // 4. Clearing repo author falls back to project author
        vm.clearRepoAuthor()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.repoHasCustomAuthor)
        assertEquals("Project Lead", vm.uiState.value.repoAuthorName)
        assertEquals("lead@work.com", vm.uiState.value.repoAuthorEmail)

        // 5. Clearing project author falls back to global default author
        vm.saveProjectAuthor("", "")
        advanceUntilIdle()
        assertNull(vm.uiState.value.projectAuthorName)
        assertNull(vm.uiState.value.projectAuthorEmail)
        assertEquals("Global User", vm.uiState.value.repoAuthorName)
        assertEquals("global@example.com", vm.uiState.value.repoAuthorEmail)

        // 6. Clearing global author marks default author as not set again
        vm.clearGlobalAuthor()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasCustomGlobalAuthor)
    }
}
