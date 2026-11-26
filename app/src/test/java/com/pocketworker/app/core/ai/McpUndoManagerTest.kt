package com.pocketworker.app.core.ai

import com.pocketworker.app.core.mcp.McpConfirmationHandler
import com.pocketworker.app.core.mcp.McpTestHarness
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class McpUndoManagerTest {

    private lateinit var harness: McpTestHarness
    private lateinit var undoManager: McpUndoManager
    private val alwaysApprove = McpConfirmationHandler { true }

    @Before
    fun setUp() {
        harness = McpTestHarness(confirmFileEdits = false)
        undoManager = McpUndoManager(
            projectRegistry = harness.registry,
            fileManager = harness.fileManager,
            gitEngine = harness.gitEngine,
            blobsDir = File(harness.tempRoot, "undo_blobs")
        )
    }

    @After
    fun tearDown() {
        harness.cleanup()
    }

    private fun runTrackedTool(
        toolName: String,
        args: JSONObject,
        opsOut: MutableList<McpUndoOperation>
    ) = runBlocking {
        val prepared = undoManager.captureBeforeTool(toolName, args)
        val result = harness.server.callTool(toolName, args, alwaysApprove)
        assertTrue("Expected tool $toolName to succeed, got: ${result.error}", result.ok)
        val (ops, _) = undoManager.finalizeAfterTool(prepared, result)
        opsOut.addAll(ops)
        result
    }

    @Test
    fun `rolls back created project and created repository completely`() {
        val ops = mutableListOf<McpUndoOperation>()
        val projRes = runTrackedTool(
            "workspace_create_project",
            JSONObject().put("name", "TempProj"),
            ops
        )
        val projectId = projRes.data!!.getString("projectId")
        val repoRes = runTrackedTool(
            "workspace_create_repo",
            JSONObject().put("projectId", projectId).put("name", "temp-repo").put("initGit", true),
            ops
        )
        val repoId = repoRes.data!!.getString("repoId")
        val repoDir = File(harness.registry.findRepository(projectId, repoId)!!.directoryPath)
        assertTrue(repoDir.exists())
        assertEquals(1, harness.registry.listProjects().size)

        // Round-trip ops through JSON (simulating persistence across restarts)
        val persistedOps = ops.mapNotNull { McpUndoOperation.fromJson(it.toJson()) }
        val summary = undoManager.rollbackOperations(persistedOps)

        assertTrue(summary.appliedOperations >= 2)
        assertTrue(harness.registry.listProjects().isEmpty())
        assertFalse(repoDir.exists())
    }

    @Test
    fun `rolls back file edit, file creation, replace across files, move, copy, mkdir, and folder deletion`() {
        val (project, repo) = harness.createProjectAndRepo()
        val repoDir = File(repo.directoryPath)
        File(repoDir, "src/Main.kt").apply {
            parentFile?.mkdirs()
            writeText("fun main() { println(\"old\") }\n")
        }
        File(repoDir, "assets/icon.txt").apply {
            parentFile?.mkdirs()
            writeText("ICON_DATA")
        }

        val ops = mutableListOf<McpUndoOperation>()

        // 1) Replace across files
        runTrackedTool(
            "fs_replace_across_files",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("query", "old")
                .put("replacement", "new"),
            ops
        )
        assertTrue(File(repoDir, "src/Main.kt").readText().contains("new"))

        // 2) Create new file
        runTrackedTool(
            "fs_write_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "docs/guide.md")
                .put("content", "# Guide\n"),
            ops
        )
        assertTrue(File(repoDir, "docs/guide.md").isFile)

        // 3) Structure ops: mkdir + move + copy
        runTrackedTool(
            "fs_manage_structure",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put(
                    "operations",
                    JSONArray()
                        .put(JSONObject().put("type", "mkdir").put("path", "empty_folder"))
                        .put(JSONObject().put("type", "move").put("path", "src/Main.kt").put("toPath", "src/App.kt"))
                        .put(JSONObject().put("type", "copy").put("path", "README.md").put("toPath", "README_COPY.md"))
                ),
            ops
        )
        assertTrue(File(repoDir, "empty_folder").isDirectory)
        assertFalse(File(repoDir, "src/Main.kt").exists())
        assertTrue(File(repoDir, "src/App.kt").isFile)
        assertTrue(File(repoDir, "README_COPY.md").isFile)

        // 4) Delete folder subtree
        runTrackedTool(
            "fs_delete_entry",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "assets"),
            ops
        )
        assertFalse(File(repoDir, "assets").exists())

        // Rollback ALL operations via JSON round-trip!
        val restoredOps = ops.mapNotNull { McpUndoOperation.fromJson(it.toJson()) }
        undoManager.rollbackOperations(restoredOps)

        assertEquals("fun main() { println(\"old\") }\n", File(repoDir, "src/Main.kt").readText())
        assertFalse(File(repoDir, "src/App.kt").exists())
        assertFalse(File(repoDir, "docs/guide.md").exists())
        assertFalse(File(repoDir, "empty_folder").exists())
        assertFalse(File(repoDir, "README_COPY.md").exists())
        assertEquals("ICON_DATA", File(repoDir, "assets/icon.txt").readText())
    }

    @Test
    fun `rolls back git_exec branch creation and commits cleanly back to original branch and HEAD`() {
        val (project, repo) = harness.createProjectAndRepo(initGit = true, createReadme = true)
        val repoDir = File(repo.directoryPath)
        val baselineBranch = harness.gitEngine.getStatus(repoDir).currentBranch
        val baselineLogCount = harness.gitEngine.getCommitHistory(repoDir, limit = 20).size
        assertEquals("main", baselineBranch)
        assertEquals(1, baselineLogCount)

        val ops = mutableListOf<McpUndoOperation>()

        // Create branch feature/ai-test
        runTrackedTool(
            "git_exec",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("command", "checkout -b feature/ai-test"),
            ops
        )
        // Write file and commit it
        runTrackedTool(
            "fs_write_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "feature.txt")
                .put("content", "feature code\n"),
            ops
        )
        runTrackedTool(
            "git_exec",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("command", "add feature.txt"),
            ops
        )
        runTrackedTool(
            "git_exec",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("command", "commit -m \"Add feature\""),
            ops
        )

        assertEquals("feature/ai-test", harness.gitEngine.getStatus(repoDir).currentBranch)
        assertEquals(2, harness.gitEngine.getCommitHistory(repoDir, limit = 20).size)
        assertTrue(File(repoDir, "feature.txt").isFile)

        // Rollback everything via JSON round-trip
        val persistedOps = ops.mapNotNull { McpUndoOperation.fromJson(it.toJson()) }
        undoManager.rollbackOperations(persistedOps)

        assertEquals("main", harness.gitEngine.getStatus(repoDir).currentBranch)
        assertEquals(1, harness.gitEngine.getCommitHistory(repoDir, limit = 20).size)
        assertFalse(File(repoDir, "feature.txt").exists())
        val branches = harness.gitEngine.getBranches(repoDir).map { it.name }
        assertEquals(listOf("main"), branches)
    }
}
