package com.pocketworker.app.core.storage

import com.pocketworker.app.core.git.ProjectEntry
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
    fun `register update discover and delete project`() {
        val (id1, dir1) = registry.allocateProjectDir("My App")
        dir1.mkdirs()
        registry.registerProject(
            ProjectEntry(
                id = id1,
                name = "My App",
                directoryPath = dir1.absolutePath,
                createdAt = 100L,
                remoteUrl = "https://github.com/test/app.git"
            )
        )

        val (id2, dir2) = registry.allocateProjectDir("My App")
        assertNotEquals(dir1.absolutePath, dir2.absolutePath)

        // Untracked folder on disk gets discovered automatically
        val extraDir = File(registry.projectsRootDir, "discovered-repo").apply { mkdirs() }

        val list = registry.listProjects()
        assertEquals(2, list.size)
        assertTrue(list.any { it.id == id1 && it.remoteUrl == "https://github.com/test/app.git" })
        assertTrue(list.any { it.directoryPath == extraDir.absolutePath })

        registry.updateRemoteUrl(id1, "https://github.com/test/app-new.git")
        assertEquals("https://github.com/test/app-new.git", registry.findById(id1)?.remoteUrl)

        registry.deleteProject(id1, deleteFilesOnDisk = true)
        assertNull(registry.findById(id1))
        assertFalse(dir1.exists())
        assertFalse(id2.isEmpty())
    }
}
