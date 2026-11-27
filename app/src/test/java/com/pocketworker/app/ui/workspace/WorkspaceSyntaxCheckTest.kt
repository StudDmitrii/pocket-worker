package com.pocketworker.app.ui.workspace

import com.pocketworker.app.core.mcp.McpTestHarness
import com.pocketworker.app.core.mcp.SyntaxCheckEngine
import com.pocketworker.app.core.mcp.SyntaxEngineResult
import com.pocketworker.app.core.mcp.SyntaxIssue
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
class WorkspaceSyntaxCheckTest {

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
    fun `checks repository, single file, and current subfolder scopes accurately`() = runTest(testDispatcher) {
        val (project, repo) = harness.createProjectAndRepo(projectName = "SyntaxProj", repoName = "syntax-repo")
        val repoDir = File(repo.directoryPath)
        File(repoDir, "broken.json").writeText("{\n  \"name\": \"app\",\n}\n")
        File(repoDir, "bad.kt").writeText("fun demo() {\n    val x = (1 + 2\n}\n")
        File(repoDir, "sub").mkdirs()
        File(repoDir, "sub/clean.json").writeText("{\"ok\": true}\n")

        val vm = WorkspaceViewModel(
            projectId = project.id,
            repoId = repo.id,
            gitEngine = harness.gitEngine,
            fileManager = harness.fileManager,
            registry = harness.registry,
            credentialStore = harness.credentialStore,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.project)

        // 1. Full repo check finds both broken.json and bad.kt, skips clean.json from issues
        vm.runSyntaxCheck()
        advanceUntilIdle()
        val repoDialog = vm.uiState.value.syntaxCheckDialog!!
        assertFalse(repoDialog.isRunning)
        assertEquals("syntax-repo", repoDialog.scopeLabel)
        assertNull(repoDialog.singleTargetPath)
        assertNull(repoDialog.errorMessage)
        assertTrue(repoDialog.checkedFilesCount >= 3)
        assertTrue(repoDialog.totalErrors >= 2)
        val issuePaths = repoDialog.fileReportsWithIssues.map { it.relativePath }.toSet()
        assertTrue("broken.json" in issuePaths)
        assertTrue("bad.kt" in issuePaths)
        assertFalse("sub/clean.json" in issuePaths)
        assertTrue(repoDialog.fileReportsWithIssues.all { it.issues.isNotEmpty() })

        // 2. Single-file check inspects only the target file
        vm.runSyntaxCheck("broken.json")
        advanceUntilIdle()
        val singleDialog = vm.uiState.value.syntaxCheckDialog!!
        assertFalse(singleDialog.isRunning)
        assertEquals("broken.json", singleDialog.scopeLabel)
        assertEquals("broken.json", singleDialog.singleTargetPath)
        assertEquals(1, singleDialog.checkedFilesCount)
        assertEquals(1, singleDialog.fileReportsWithIssues.size)
        assertEquals("broken.json", singleDialog.fileReportsWithIssues.first().relativePath)
        assertNotNull(singleDialog.fileReportsWithIssues.first().issues.first().snippet)

        // 3. Subfolder check scopes to currentDir ("sub") -> 0 errors
        vm.openDirectory("sub")
        advanceUntilIdle()
        assertEquals("sub", vm.uiState.value.currentDir)

        vm.runSyntaxCheck()
        advanceUntilIdle()
        val subDialog = vm.uiState.value.syntaxCheckDialog!!
        assertFalse(subDialog.isRunning)
        assertEquals("sub", subDialog.scopeLabel)
        assertEquals(1, subDialog.checkedFilesCount)
        assertEquals(0, subDialog.totalErrors)
        assertTrue(subDialog.fileReportsWithIssues.isEmpty())

        // 4. Closing dialog clears state
        vm.closeSyntaxCheckDialog()
        assertNull(vm.uiState.value.syntaxCheckDialog)
    }

    @Test
    fun `delegates to configured AST SyntaxCheckEngine when available`() = runTest(testDispatcher) {
        val (project, repo) = harness.createProjectAndRepo(projectName = "AstProj", repoName = "ast-repo")
        val repoDir = File(repo.directoryPath)
        File(repoDir, "app.py").writeText("def broken(:\n    pass\n")

        val fakeAstEngine = object : SyntaxCheckEngine {
            override suspend fun check(relativePath: String, content: String): SyntaxEngineResult {
                return if (relativePath.endsWith(".py")) {
                    SyntaxEngineResult(
                        handled = true,
                        engine = "tree-sitter-python",
                        issues = listOf(SyntaxIssue(line = 1, column = 12, severity = "error", message = "SyntaxError: invalid syntax"))
                    )
                } else {
                    SyntaxEngineResult(handled = false)
                }
            }
        }

        val vm = WorkspaceViewModel(
            projectId = project.id,
            repoId = repo.id,
            gitEngine = harness.gitEngine,
            fileManager = harness.fileManager,
            registry = harness.registry,
            credentialStore = harness.credentialStore,
            syntaxCheckEngine = fakeAstEngine,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.project)

        vm.runSyntaxCheck("app.py")
        advanceUntilIdle()

        val dialog = vm.uiState.value.syntaxCheckDialog!!
        assertFalse(dialog.isRunning)
        assertEquals(1, dialog.totalErrors)
        assertEquals(1, dialog.fileReportsWithIssues.size)
        val report = dialog.fileReportsWithIssues.first()
        assertEquals("tree-sitter-python", report.engine)
        assertEquals("SyntaxError: invalid syntax", report.issues.first().message)
        assertEquals(1, report.issues.first().line)
        assertEquals(12, report.issues.first().column)
    }
}
