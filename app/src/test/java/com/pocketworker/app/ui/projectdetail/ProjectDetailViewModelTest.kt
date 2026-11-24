package com.pocketworker.app.ui.projectdetail

import com.pocketworker.app.core.git.AndroidSystemReader
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.JGitEngine
import com.pocketworker.app.core.storage.InMemoryCredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import com.pocketworker.app.ui.common.formatGitTransportError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.eclipse.jgit.api.Git
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
class ProjectDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempRoot: File
    private lateinit var registry: ProjectRegistry
    private lateinit var gitEngine: GitEngine
    private lateinit var credentialStore: InMemoryCredentialStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempRoot = Files.createTempDirectory("pocketworker-vm-test").toFile()
        AndroidSystemReader.install(File(tempRoot, "sysconfig"))
        gitEngine = JGitEngine()
        credentialStore = InMemoryCredentialStore()
        registry = ProjectRegistry(
            metadataFile = File(tempRoot, "projects.json"),
            projectsRootDir = File(tempRoot, "projects")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempRoot.deleteRecursively()
    }

    @Test
    fun `failed clone populates cloneError without losing retry capability`() = runTest(testDispatcher) {
        val project = registry.createProject("Platform")
        val vm = ProjectDetailViewModel(project.id, gitEngine, registry, credentialStore, testDispatcher)
        advanceUntilIdle()

        var succeededRepoId: String? = null
        vm.cloneRepository(
            url = File(tempRoot, "non-existent.git").toURI().toString(),
            customName = "client",
            branch = "",
            useAuth = true,
            username = "user",
            token = "bad-token",
            saveCredentialsForRepo = true,
            onSuccess = { succeededRepoId = it }
        )
        advanceUntilIdle()

        assertNull(succeededRepoId)
        assertFalse(vm.uiState.value.isCloning)
        assertNotNull(vm.uiState.value.cloneError)
        assertEquals(0, registry.listRepositories(project.id).size)

        // Create a real bare repo and retry
        val bareDir = File(tempRoot, "valid-repo.git")
        Git.init().setDirectory(bareDir).setBare(true).call().close()

        vm.clearCloneError()
        assertNull(vm.uiState.value.cloneError)

        vm.cloneRepository(
            url = bareDir.toURI().toString(),
            customName = "client",
            branch = "",
            useAuth = false,
            username = "",
            token = "",
            saveCredentialsForRepo = false,
            onSuccess = { succeededRepoId = it }
        )
        advanceUntilIdle()

        assertNotNull(succeededRepoId)
        assertNull(vm.uiState.value.cloneError)
        assertEquals(1, registry.listRepositories(project.id).size)
    }

    @Test
    fun `formatGitTransportError translates common auth and network errors`() {
        val authMsg = formatGitTransportError(RuntimeException("https://github.com/a/b.git: not authorized"))
        assertTrue(authMsg.contains("авторизации"))

        val notFoundMsg = formatGitTransportError(RuntimeException("404 Not Found"))
        assertTrue(notFoundMsg.contains("404"))
    }
}
