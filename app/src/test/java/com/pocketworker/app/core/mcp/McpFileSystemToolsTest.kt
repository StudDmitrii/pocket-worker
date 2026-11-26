package com.pocketworker.app.core.mcp

import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class McpFileSystemToolsTest {

    private lateinit var harness: McpTestHarness

    @Before
    fun setUp() {
        harness = McpTestHarness(confirmFileEdits = false)
    }

    @After
    fun tearDown() {
        harness.cleanup()
    }

    @Test
    fun `read overview, list dir, find files, grep, and read file ranges and outline`() = runTest {
        val (project, repo) = harness.createProjectAndRepo("ShopApp", "android")
        val repoDir = File(repo.directoryPath)
        File(repoDir, "src/main/App.kt").apply {
            parentFile?.mkdirs()
            writeText(
                """
                package demo

                class CartManager {
                    fun addItem(name: String) {
                        println("Adding ${'$'}name")
                    }

                    fun clearCart() {
                        println("Cleared")
                    }
                }
                """.trimIndent() + "\n"
            )
        }
        File(repoDir, "build.gradle.kts").writeText("plugins { kotlin(\"jvm\") }\n")

        val overview = harness.server.callTool(
            "fs_get_repo_overview",
            JSONObject().put("projectId", project.id).put("repoId", repo.id).put("treeDepth", 3)
        )
        assertTrue(overview.ok)
        val keyFiles = overview.data!!.getJSONArray("keyFiles")
        assertTrue((0 until keyFiles.length()).map { keyFiles.getString(it) }.contains("build.gradle.kts"))

        val listDir = harness.server.callTool(
            "fs_list_dir",
            JSONObject().put("projectId", project.id).put("repoId", repo.id).put("depth", 2)
        )
        assertTrue(listDir.ok)
        assertTrue(listDir.data!!.getInt("count") >= 2)

        val find = harness.server.callTool(
            "fs_find_files",
            JSONObject().put("projectId", project.id).put("repoId", repo.id).put("pattern", "*.kt")
        )
        assertTrue(find.ok)
        assertEquals(1, find.data!!.getInt("count"))

        val grep = harness.server.callTool(
            "fs_grep",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("query", "fun\\s+\\w+")
                .put("isRegex", true)
        )
        assertTrue(grep.ok)
        assertEquals(2, grep.data!!.getInt("matchCount"))

        val outline = harness.server.callTool(
            "fs_read_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "src/main/App.kt")
                .put("outlineOnly", true)
        )
        assertTrue(outline.ok)
        val outlineItems = outline.data!!.getJSONArray("outline")
        assertTrue(outlineItems.length() >= 3) // class + 2 funs

        val slice = harness.server.callTool(
            "fs_read_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "src/main/App.kt")
                .put("startLine", 3)
                .put("endLine", 5)
        )
        assertTrue(slice.ok)
        assertTrue(slice.data!!.getString("content").contains("class CartManager"))
    }

    @Test
    fun `fs_edit_file supports unique search-replace, dryRun, ambiguity detection, and line range`() = runTest {
        val (project, repo) = harness.createProjectAndRepo()
        val repoDir = File(repo.directoryPath)
        File(repoDir, "sample.txt").writeText("alpha\nbeta\nalpha\ngamma\n")

        // Ambiguous target returns PATCH_TARGET_AMBIGUOUS
        val ambiguous = harness.server.callTool(
            "fs_edit_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "sample.txt")
                .put("edits", JSONArray().put(JSONObject().put("oldText", "alpha").put("newText", "ALPHA")))
        )
        assertFalse(ambiguous.ok)
        assertEquals(McpErrorCodes.PATCH_TARGET_AMBIGUOUS, ambiguous.error?.errorCode)

        // Missing target returns PATCH_TARGET_NOT_FOUND
        val notFound = harness.server.callTool(
            "fs_edit_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "sample.txt")
                .put("edits", JSONArray().put(JSONObject().put("oldText", "does-not-exist").put("newText", "x")))
        )
        assertFalse(notFound.ok)
        assertEquals(McpErrorCodes.PATCH_TARGET_NOT_FOUND, notFound.error?.errorCode)

        // dryRun does not write to disk
        val dry = harness.server.callTool(
            "fs_edit_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "sample.txt")
                .put("dryRun", true)
                .put("edits", JSONArray().put(JSONObject().put("oldText", "beta").put("newText", "BETA")))
        )
        assertTrue(dry.ok)
        assertTrue(dry.data!!.getString("diff").contains("-beta"))
        assertTrue(File(repoDir, "sample.txt").readText().contains("beta"))

        // Real edit applies cleanly
        val apply = harness.server.callTool(
            "fs_edit_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "sample.txt")
                .put("edits", JSONArray().put(JSONObject().put("oldText", "beta").put("newText", "BETA")))
        )
        assertTrue(apply.ok)
        assertTrue(File(repoDir, "sample.txt").readText().contains("BETA"))

        // Line range replacement
        val rangeEdit = harness.server.callTool(
            "fs_edit_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "sample.txt")
                .put("edits", JSONArray().put(JSONObject().put("startLine", 1).put("endLine", 1).put("newText", "FIRST")))
        )
        assertTrue(rangeEdit.ok)
        assertTrue(File(repoDir, "sample.txt").readText().startsWith("FIRST\n"))
    }

    @Test
    fun `fs_replace_across_files performs pure-Kotlin dryRun and apply across matching files`() = runTest {
        val (project, repo) = harness.createProjectAndRepo()
        val repoDir = File(repo.directoryPath)
        File(repoDir, "src/A.kt").apply {
            parentFile?.mkdirs()
            writeText("val oldService = OldService()\n")
        }
        File(repoDir, "src/B.kt").writeText("class OldService { fun ping() = \"OldService\" }\n")
        File(repoDir, "docs/info.md").apply {
            parentFile?.mkdirs()
            writeText("Do not touch OldService in markdown\n")
        }

        // Dry run limited to *.kt
        val dry = harness.server.callTool(
            "fs_replace_across_files",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("query", "OldService")
                .put("replacement", "NewService")
                .put("fileGlob", "*.kt")
                .put("dryRun", true)
        )
        assertTrue(dry.ok)
        assertEquals(2, dry.data!!.getInt("filesAffected"))
        assertEquals(3, dry.data!!.getInt("totalReplacements"))
        assertTrue(File(repoDir, "src/A.kt").readText().contains("OldService"))

        // Apply
        val apply = harness.server.callTool(
            "fs_replace_across_files",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("query", "OldService")
                .put("replacement", "NewService")
                .put("fileGlob", "*.kt")
                .put("dryRun", false)
        )
        assertTrue(apply.ok)
        assertEquals(2, apply.data!!.getInt("filesAffected"))
        assertTrue(File(repoDir, "src/A.kt").readText().contains("NewService()"))
        assertTrue(File(repoDir, "src/B.kt").readText().contains("class NewService"))
        assertTrue(File(repoDir, "docs/info.md").readText().contains("OldService")) // untouched!
    }

    @Test
    fun `fs_manage_structure executes batch mkdir, copy, and move and blocks protected git metadata`() = runTest {
        val (project, repo) = harness.createProjectAndRepo()
        val repoDir = File(repo.directoryPath)
        File(repoDir, "draft.kt").writeText("class Draft\n")

        val ops = JSONArray()
            .put(JSONObject().put("type", "mkdir").put("path", "src/features/auth"))
            .put(JSONObject().put("type", "copy").put("path", "draft.kt").put("toPath", "src/features/auth/DraftCopy.kt"))
            .put(JSONObject().put("type", "move").put("path", "draft.kt").put("toPath", "src/features/auth/Auth.kt"))

        val res = harness.server.callTool(
            "fs_manage_structure",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("operations", ops)
        )
        assertTrue(res.ok)
        assertFalse(File(repoDir, "draft.kt").exists())
        assertTrue(File(repoDir, "src/features/auth/Auth.kt").exists())
        assertTrue(File(repoDir, "src/features/auth/DraftCopy.kt").exists())

        // Sandbox & .git protection checks
        val gitWrite = harness.server.callTool(
            "fs_write_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", ".git/config")
                .put("content", "hacked")
        )
        assertFalse(gitWrite.ok)
        assertEquals(McpErrorCodes.PROTECTED_GIT_PATH, gitWrite.error?.errorCode)

        val escapeWrite = harness.server.callTool(
            "fs_write_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "../../outside.txt")
                .put("content", "escape")
        )
        assertFalse(escapeWrite.ok)
        assertEquals(McpErrorCodes.PATH_OUT_OF_SANDBOX, escapeWrite.error?.errorCode)
    }
}
