package com.pocketworker.app.ui.projectdetail

import com.pocketworker.app.core.git.AndroidSystemReader
import com.pocketworker.app.core.git.DiscoveredRepo
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.JGitEngine
import com.pocketworker.app.core.git.RepoGroupDiscoveryService
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

    @Test
    fun `group discovery and selective batch cloning adds only checked repositories`() = runTest(testDispatcher) {
        val project = registry.createProject("Suite")

        val bare1 = File(tempRoot, "repo-one.git")
        val bare2 = File(tempRoot, "repo-two.git")
        val bare3 = File(tempRoot, "repo-three.git")
        listOf(bare1, bare2, bare3).forEach { dir ->
            Git.init().setDirectory(dir).setBare(true).call().close()
            // Add one commit so defaultBranch works cleanly
            val seed = File(tempRoot, "seed-${dir.name}")
            gitEngine.initProject(seed, initialBranch = "main", initGit = true, createReadmeAndCommit = true, remoteUrl = dir.toURI().toString())
            gitEngine.push(seed, remoteName = "origin", branchName = "main")
        }

        val fakeDiscovery = object : RepoGroupDiscoveryService {
            override fun discoverRepositories(groupUrl: String, credentials: GitCredentials?): List<DiscoveredRepo> {
                return listOf(
                    DiscoveredRepo("1", "repo-one", "org/repo-one", bare1.toURI().toString(), "main", "One", false),
                    DiscoveredRepo("2", "repo-two", "org/repo-two", bare2.toURI().toString(), "main", "Two", false),
                    DiscoveredRepo("3", "repo-three", "org/repo-three", bare3.toURI().toString(), "main", "Three", true)
                )
            }
        }

        val vm = ProjectDetailViewModel(
            projectId = project.id,
            gitEngine = gitEngine,
            registry = registry,
            credentialStore = credentialStore,
            ioDispatcher = testDispatcher,
            groupDiscoveryService = fakeDiscovery
        )
        advanceUntilIdle()

        vm.discoverGroupRepositories("https://github.com/org", false, "", "")
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.discoveredRepos.size)
        assertTrue(vm.uiState.value.discoveredRepos.all { it.selected })

        // Uncheck repo-two
        vm.toggleDiscoveredRepoSelection("2")
        assertFalse(vm.uiState.value.discoveredRepos.first { it.repo.id == "2" }.selected)

        var allDoneCalled = false
        vm.cloneSelectedGroupRepositories(
            useAuth = false,
            username = "",
            token = "",
            saveCredentialsForRepos = false,
            onAllDone = { allDoneCalled = true }
        )
        advanceUntilIdle()

        assertTrue(allDoneCalled)
        assertNull(vm.uiState.value.groupCloneError)
        val savedRepos = registry.listRepositories(project.id).map { it.name }.toSet()
        assertEquals(setOf("repo-one", "repo-three"), savedRepos)
    }
}
