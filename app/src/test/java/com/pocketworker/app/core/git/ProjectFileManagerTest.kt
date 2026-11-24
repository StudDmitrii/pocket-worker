package com.pocketworker.app.core.git

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectFileManagerTest {

    private lateinit var tempRoot: File
    private lateinit var fileManager: ProjectFileManager

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("pocketworker-files-test").toFile()
        fileManager = ProjectFileManager()
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun `listDirectory sorts folders first and hides dot git by default`() {
        File(tempRoot, ".git").mkdirs()
        File(tempRoot, "src").mkdirs()
        File(tempRoot, "src/Main.kt").writeText("fun main() {}")
        File(tempRoot, "README.md").writeText("# Hi")

        val visible = fileManager.listDirectory(
            projectRoot = tempRoot,
            showHiddenGit = false,
            statusByPath = mapOf("src/Main.kt" to FileChangeType.MODIFIED)
        )
        assertEquals(listOf("src", "README.md"), visible.map { it.name })
        assertEquals(FileChangeType.MODIFIED, visible.first().gitChangeType)

        val withGit = fileManager.listDirectory(tempRoot, showHiddenGit = true)
        assertTrue(withGit.any { it.name == ".git" })
    }

    @Test(expected = SecurityException::class)
    fun `resolveSafe blocks directory traversal`() {
        fileManager.resolveSafe(tempRoot, "../outside.txt")
    }

    @Test
    fun `create read write rename search and delete work cleanly`() {
        fileManager.createDirectory(tempRoot, "app/src")
        fileManager.createFile(tempRoot, "app/src/hello.txt", "v1")

        val read1 = fileManager.readTextFile(tempRoot, "app/src/hello.txt")
        assertTrue(read1 is FileReadResult.Text)
        assertEquals("v1", (read1 as FileReadResult.Text).content)

        fileManager.writeTextFile(tempRoot, "app/src/hello.txt", "v2")
        val renamed = fileManager.renameEntry(tempRoot, "app/src/hello.txt", "world.txt")
        assertEquals("app/src/world.txt", renamed)

        val found = fileManager.searchFiles(tempRoot, "world")
        assertEquals(1, found.size)

        fileManager.deleteEntry(tempRoot, "app/src/world.txt")
        assertFalse(File(tempRoot, "app/src/world.txt").exists())
    }

    @Test
    fun `binary files are detected cleanly`() {
        File(tempRoot, "data.bin").writeBytes(byteArrayOf(0x01, 0x00, 0x02, 0x03))
        val res = fileManager.readTextFile(tempRoot, "data.bin")
        assertTrue(res is FileReadResult.Binary)
    }
}
