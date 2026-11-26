package com.pocketworker.app.ui.workspace

import com.pocketworker.app.core.mcp.McpTestHarness
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

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceFileReloadTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var harness: McpTestHarness

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        harness = McpTestHarness(confirmFileEdits = false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        harness.cleanup()
    }

    @Test
    fun `reloads open file immediately when changed on disk and closes cleanly if deleted`() = runTest(testDispatcher) {
        val (project, repo) = harness.createProjectAndRepo(projectName = "EditProj", repoName = "edit-repo")
        val repoDir = File(repo.directoryPath)
        val file = File(repoDir, "hello.kt").apply { writeText("val x = 1\n") }

        val vm = WorkspaceViewModel(
            projectId = project.id,
            repoId = repo.id,
            gitEngine = harness.gitEngine,
            fileManager = harness.fileManager,
            registry = harness.registry,
            credentialStore = harness.credentialStore
        )

        // Wait for initial loadAll IO coroutine
        var attempts = 0
        while (vm.uiState.value.project == null && attempts < 50) {
            Thread.sleep(20)
            advanceUntilIdle()
            attempts++
        }
        assertNotNull(vm.uiState.value.project)

        // Open file in viewer/editor
        vm.openFile("hello.kt")
        attempts = 0
        while (vm.uiState.value.fileEditor == null && attempts < 50) {
            Thread.sleep(20)
            advanceUntilIdle()
            attempts++
        }
        assertEquals("val x = 1\n", vm.uiState.value.fileEditor?.content)

        // External edit (as if AI agent edited the file in chat)
        file.writeText("val x = 42\nval y = 99\n")
        vm.reloadOpenFileFromDisk()
        attempts = 0
        while (vm.uiState.value.fileEditor?.content != "val x = 42\nval y = 99\n" && attempts < 50) {
            Thread.sleep(20)
            advanceUntilIdle()
            attempts++
        }
        assertEquals("val x = 42\nval y = 99\n", vm.uiState.value.fileEditor?.content)
        assertFalse(vm.uiState.value.fileEditor!!.isModifiedInEditor)

        // User edits manually in editor, disk does NOT change -> reload preserves manual unsaved edits
        vm.updateEditorContent("val x = 42\nval localDraft = true\n")
        vm.reloadOpenFileFromDisk()
        Thread.sleep(60)
        advanceUntilIdle()
        assertEquals("val x = 42\nval localDraft = true\n", vm.uiState.value.fileEditor?.content)
        assertTrue(vm.uiState.value.fileEditor!!.isModifiedInEditor)

        // File deleted externally (e.g. rollback) -> editor closes cleanly
        file.delete()
        vm.reloadOpenFileFromDisk()
        attempts = 0
        while (vm.uiState.value.fileEditor != null && attempts < 50) {
            Thread.sleep(20)
            advanceUntilIdle()
            attempts++
        }
        assertNull(vm.uiState.value.fileEditor)
    }
}
