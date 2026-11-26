package com.pocketworker.app.core.storage

import com.pocketworker.app.core.git.RepoEntry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectRegistryTest {

    private lateinit var tempRoot: File
    private lateinit var registry: ProjectRegistry

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("pocketworker-registry-test").toFile()
        registry = ProjectRegistry(
            metadataFile = File(tempRoot, "projects.json"),
            projectsRootDir = File(tempRoot, "projects")
        )
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun `create project creates strictly one project without duplicating on reconcile`() {
        val created = registry.createProject("My First App", "Mobile workspace")
        val projects = registry.listProjects()

        assertEquals(1, projects.size)
        assertEquals(created.id, projects.first().id)
        assertEquals("My First App", projects.first().name)
        assertEquals("Mobile workspace", projects.first().description)

        val second = registry.createProject("Backend")
        assertEquals(2, registry.listProjects().size)
        assertTrue(registry.listProjects().any { it.id == second.id })
    }

    @Test
    fun `reconcile heals pre-existing duplicate project entries pointing to same directory`() {
        val dir = File(registry.projectsRootDir, "my-app").apply { mkdirs() }
        val duplicatedJson = JSONObject().apply {
            put("version", 2)
            put(
                "projects",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("id", "auto-discovered-id")
                            put("name", "my-app")
                            put("directoryPath", dir.absolutePath)
                            put("createdAt", 100L)
                            put("repositories", JSONArray())
                        }
                    )
                    .put(
                        JSONObject().apply {
                            put("id", "real-project-id")
                            put("name", "My App")
                            put("description", "Real description")
                            put("directoryPath", dir.absolutePath)
                            put("createdAt", 200L)
                            put("repositories", JSONArray())
                        }
                    )
            )
        }
        File(tempRoot, "projects.json").writeText(duplicatedJson.toString(2))

        val projects = registry.listProjects()
        assertEquals(1, projects.size)
        assertEquals("real-project-id", projects.first().id)
        assertEquals("My App", projects.first().name)
        assertEquals("Real description", projects.first().description)
    }

    @Test
    fun `create project and manage multiple repositories inside it`() {
        val project = registry.createProject("E-Commerce", "Client, Backend, Admin")
        assertTrue(File(project.directoryPath).exists())

        val (clientId, clientDir) = registry.allocateRepositoryDir(project.id, "client")
        clientDir.mkdirs()
        registry.registerRepository(
            RepoEntry(
                id = clientId,
                projectId = project.id,
                name = "client",
                directoryPath = clientDir.absolutePath,
                createdAt = 100L,
                remoteUrl = "https://github.com/org/client.git"
            )
        )

        val (backendId, backendDir) = registry.allocateRepositoryDir(project.id, "backend")
        backendDir.mkdirs()
        registry.registerRepository(
            RepoEntry(
                id = backendId,
                projectId = project.id,
                name = "backend",
                directoryPath = backendDir.absolutePath,
                createdAt = 200L,
                remoteUrl = "https://github.com/org/backend.git"
            )
        )

        val (_, duplicateDir) = registry.allocateRepositoryDir(project.id, "client")
        assertNotEquals(clientDir.absolutePath, duplicateDir.absolutePath)

        // Untracked folder dropped directly inside project directory gets discovered automatically
        val servicesDir = File(project.directoryPath, "services").apply { mkdirs() }

        val repos = registry.listRepositories(project.id)
        assertEquals(3, repos.size)
        assertTrue(repos.any { it.id == clientId && it.remoteUrl == "https://github.com/org/client.git" })
        assertTrue(repos.any { it.id == backendId })
        assertTrue(repos.any { it.directoryPath == servicesDir.absolutePath })

        registry.updateRepositoryRemoteUrl(project.id, clientId, "https://github.com/org/client-v2.git")
        assertEquals(
            "https://github.com/org/client-v2.git",
            registry.findRepository(project.id, clientId)?.remoteUrl
        )

        registry.deleteRepository(project.id, clientId, deleteFilesOnDisk = true)
        assertNull(registry.findRepository(project.id, clientId))
        assertFalse(clientDir.exists())

        val deletedIds = registry.deleteProject(project.id, deleteFilesOnDisk = true)
        assertTrue(backendId in deletedIds)
        assertNull(registry.findProjectById(project.id))
        assertFalse(File(project.directoryPath).exists())
    }

    @Test
    fun `legacy v1 flat json migrates into workspace project wrapper`() {
        val oldRepoDir = File(registry.projectsRootDir, "legacy-repo").apply {
            mkdirs()
            File(this, ".git").mkdirs()
        }
        val legacyArray = JSONArray().put(
            JSONObject().apply {
                put("id", "old-1")
                put("name", "LegacyRepo")
                put("directoryPath", oldRepoDir.absolutePath)
                put("createdAt", 500L)
                put("remoteUrl", "https://github.com/test/legacy.git")
            }
        )
        File(tempRoot, "projects.json").writeText(legacyArray.toString())

        val projects = registry.listProjects()
        assertEquals(1, projects.size)
        val project = projects.first()
        val repos = registry.listRepositories(project.id)
        assertEquals(1, repos.size)
        assertEquals("old-1", repos.first().id)
        assertEquals("https://github.com/test/legacy.git", repos.first().remoteUrl)
        assertTrue(File(repos.first().directoryPath, ".git").exists())
    }
}
