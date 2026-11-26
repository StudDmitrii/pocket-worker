package com.pocketworker.app.core.mcp

import com.pocketworker.app.core.git.AndroidSystemReader
import com.pocketworker.app.core.git.DiscoveredRepo
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.JGitEngine
import com.pocketworker.app.core.git.ProjectFileManager
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.git.RepoGroupDiscoveryService
import com.pocketworker.app.core.git.WorkspaceProject
import com.pocketworker.app.core.storage.InMemoryCredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import com.pocketworker.app.ui.ai.AiLocationContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Files

internal class FakeRepoGroupDiscoveryService(
    var results: List<DiscoveredRepo> = emptyList()
) : RepoGroupDiscoveryService {
    override fun discoverRepositories(groupUrl: String, credentials: GitCredentials?): List<DiscoveredRepo> {
        return results
    }
}

internal class McpTestHarness(
    var confirmFileEdits: Boolean = false,
    var activeLocation: AiLocationContext = AiLocationContext.AllProjects,
    syntaxCheckEngine: SyntaxCheckEngine = NoOpSyntaxCheckEngine
) {
    val tempRoot: File = Files.createTempDirectory("pocketworker-mcp-test").toFile()
    val gitEngine = JGitEngine()
    val fileManager = ProjectFileManager()
    val credentialStore = InMemoryCredentialStore()
    val discoveryService = FakeRepoGroupDiscoveryService()
    val registry: ProjectRegistry

    init {
        AndroidSystemReader.install(File(tempRoot, "sysconfig"))
        registry = ProjectRegistry(
            metadataFile = File(tempRoot, "registry.json"),
            projectsRootDir = File(tempRoot, "projects")
        )
    }

    val server = PocketWorkerMcpServer(
        projectRegistry = registry,
        fileManager = fileManager,
        gitEngine = gitEngine,
        credentialStore = credentialStore,
        groupDiscoveryService = discoveryService,
        currentLocationProvider = { activeLocation },
        confirmFileEditsProvider = { confirmFileEdits },
        ioDispatcher = Dispatchers.Unconfined,
        syntaxCheckEngine = syntaxCheckEngine
    )

    fun createProjectAndRepo(
        projectName: String = "DemoProject",
        repoName: String = "demo-repo",
        initGit: Boolean = true,
        createReadme: Boolean = true
    ): Pair<WorkspaceProject, RepoEntry> {
        val project = registry.createProject(projectName)
        val (repoId, repoDir) = registry.allocateRepositoryDir(project.id, repoName)
        gitEngine.initProject(
            targetDir = repoDir,
            initialBranch = "main",
            initGit = initGit,
            createReadmeAndCommit = createReadme,
            projectName = repoName
        )
        val entry = RepoEntry(
            id = repoId,
            projectId = project.id,
            name = repoName,
            directoryPath = repoDir.absolutePath,
            createdAt = System.currentTimeMillis(),
            remoteUrl = null
        )
        registry.registerRepository(entry)
        return project to entry
    }

    fun cleanup() {
        tempRoot.deleteRecursively()
    }
}
