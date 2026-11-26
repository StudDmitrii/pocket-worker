package com.pocketworker.app.core.mcp

import com.pocketworker.app.ui.ai.AiLocationContext
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PocketWorkerMcpServerTest {

    private lateinit var harness: McpTestHarness

    @Before
    fun setUp() {
        harness = McpTestHarness()
    }

    @After
    fun tearDown() {
        harness.cleanup()
    }

    @Test
    fun `registers all 21 tools with valid schemas`() {
        val tools = harness.server.listTools()
        assertEquals(21, tools.size)

        val expectedNames = setOf(
            "workspace_get_context",
            "workspace_list_projects",
            "workspace_create_project",
            "workspace_list_repos",
            "workspace_create_repo",
            "workspace_clone_repo",
            "workspace_discover_group_repos",
            "fs_get_repo_overview",
            "fs_list_dir",
            "fs_find_files",
            "fs_grep",
            "fs_read_file",
            "fs_edit_file",
            "fs_write_file",
            "fs_replace_across_files",
            "fs_manage_structure",
            "fs_delete_entry",
            "git_exec",
            "check_push_readiness",
            "check_merge_conflicts",
            "check_file_syntax"
        )
        assertEquals(expectedNames, tools.map { it.name }.toSet())
        tools.forEach { def ->
            assertEquals("object", def.inputSchema.getString("type"))
            assertTrue(def.description.isNotBlank())
        }
    }

    @Test
    fun `json-rpc supports initialize, tools list, and tools call`() = runTest {
        val (project, repo) = harness.createProjectAndRepo("Alpha", "backend")
        harness.activeLocation = AiLocationContext.Repository(
            projectId = project.id,
            projectName = project.name,
            repoId = repo.id,
            repoName = repo.name,
            currentDir = "src"
        )

        val initResp = JSONObject(
            harness.server.handleJsonRpc("""{"jsonrpc":"2.0","id":1,"method":"initialize"}""")
        )
        assertEquals("2.0", initResp.getString("jsonrpc"))
        assertEquals(1, initResp.getInt("id"))
        assertEquals(PocketWorkerMcpServer.SERVER_NAME, initResp.getJSONObject("result").getJSONObject("serverInfo").getString("name"))

        val listResp = JSONObject(
            harness.server.handleJsonRpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        )
        assertEquals(21, listResp.getJSONObject("result").getJSONArray("tools").length())

        val callResp = JSONObject(
            harness.server.handleJsonRpc("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"workspace_get_context","arguments":{}}}""")
        )
        val resultObj = callResp.getJSONObject("result")
        assertFalse(resultObj.getBoolean("isError"))
        val structured = resultObj.getJSONObject("structuredContent")
        assertTrue(structured.getBoolean("ok"))
        val data = structured.getJSONObject("data")
        assertEquals("repository", data.getString("scope"))
        assertEquals(project.id, data.getString("projectId"))
        assertEquals(repo.id, data.getString("repoId"))
    }

    @Test
    fun `permission tiers enforce ALWAYS_CONFIRM even when confirmFileEdits is false`() = runTest {
        harness.confirmFileEdits = false
        val (project, repo) = harness.createProjectAndRepo()
        val repoDir = File(repo.directoryPath)
        File(repoDir, "temp.txt").writeText("hello")

        // CONTROLLED_WRITE executes without prompt when confirmFileEdits = false
        val writeRes = harness.server.callTool(
            "fs_write_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "notes.txt")
                .put("content", "auto allowed")
        )
        assertTrue(writeRes.ok)

        // ALWAYS_CONFIRM (fs_delete_entry) MUST require confirmation even with confirmFileEdits = false
        val delNoHandler = harness.server.callTool(
            "fs_delete_entry",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "temp.txt")
        )
        assertFalse(delNoHandler.ok)
        assertEquals(McpErrorCodes.CONFIRMATION_REQUIRED, delNoHandler.error?.errorCode)
        assertNotNull(delNoHandler.requiresConfirmation)
        assertEquals(McpPermissionTier.ALWAYS_CONFIRM, delNoHandler.requiresConfirmation?.tier)
        assertTrue(File(repoDir, "temp.txt").exists())

        // ALWAYS_CONFIRM (git push) MUST require confirmation even with confirmFileEdits = false
        val pushNoHandler = harness.server.callTool(
            "git_exec",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("command", "push origin main")
        )
        assertFalse(pushNoHandler.ok)
        assertEquals(McpErrorCodes.CONFIRMATION_REQUIRED, pushNoHandler.error?.errorCode)
        assertEquals(McpPermissionTier.ALWAYS_CONFIRM, pushNoHandler.requiresConfirmation?.tier)

        // ALWAYS_CONFIRM (git reset --hard) MUST require confirmation even with confirmFileEdits = false
        val resetHardNoHandler = harness.server.callTool(
            "git_exec",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("command", "reset --hard HEAD")
        )
        assertFalse(resetHardNoHandler.ok)
        assertEquals(McpErrorCodes.CONFIRMATION_REQUIRED, resetHardNoHandler.error?.errorCode)

        // Rejection returns USER_REJECTED and preserves file
        val delRejected = harness.server.callTool(
            "fs_delete_entry",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "temp.txt"),
            confirmationHandler = McpConfirmationHandler { false }
        )
        assertFalse(delRejected.ok)
        assertEquals(McpErrorCodes.USER_REJECTED, delRejected.error?.errorCode)
        assertTrue(File(repoDir, "temp.txt").exists())

        // Approval executes deletion
        val delApproved = harness.server.callTool(
            "fs_delete_entry",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "temp.txt"),
            confirmationHandler = McpConfirmationHandler { true }
        )
        assertTrue(delApproved.ok)
        assertFalse(File(repoDir, "temp.txt").exists())
    }

    @Test
    fun `confirmFileEdits true gates CONTROLLED_WRITE while AUTO runs freely`() = runTest {
        harness.confirmFileEdits = true
        val (project, repo) = harness.createProjectAndRepo()

        // AUTO runs without handler
        val statusRes = harness.server.callTool(
            "git_exec",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("command", "status --short")
        )
        assertTrue(statusRes.ok)

        // CONTROLLED_WRITE requires approval when confirmFileEdits = true
        var promptedSummary: String? = null
        val writeRes = harness.server.callTool(
            "fs_write_file",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("relativePath", "hello.kt")
                .put("content", "fun main() {}\n"),
            confirmationHandler = McpConfirmationHandler { req ->
                promptedSummary = req.summary
                true
            }
        )
        assertTrue(writeRes.ok)
        assertNotNull(promptedSummary)
        assertTrue(promptedSummary!!.contains("hello.kt"))
    }

    @Test
    fun `unknown tool returns TOOL_NOT_FOUND`() = runTest {
        val res = harness.server.callTool("non_existent_tool", JSONObject())
        assertFalse(res.ok)
        assertEquals(McpErrorCodes.TOOL_NOT_FOUND, res.error?.errorCode)
        assertTrue(res.error!!.recoverableByAgent)
    }

    @Test
    fun `workspace tools create project, create repo, list, discover group, and enforce clone safety`() = runTest {
        harness.confirmFileEdits = false

        val createdProject = harness.server.callTool(
            "workspace_create_project",
            JSONObject().put("name", "Mobile Suite")
        )
        assertTrue(createdProject.ok)
        val projectId = createdProject.data!!.getString("projectId")

        val createdRepo = harness.server.callTool(
            "workspace_create_repo",
            JSONObject()
                .put("projectId", projectId)
                .put("name", "app-core")
                .put("initGit", true)
                .put("initialBranch", "main")
        )
        assertTrue(createdRepo.ok)

        val projectsList = harness.server.callTool("workspace_list_projects", JSONObject())
        assertTrue(projectsList.ok)
        assertEquals(1, projectsList.data!!.getInt("count"))

        val reposList = harness.server.callTool(
            "workspace_list_repos",
            JSONObject().put("projectId", projectId)
        )
        assertTrue(reposList.ok)
        assertEquals(1, reposList.data!!.getInt("count"))

        // workspace_clone_repo ALWAYS requires confirmation
        val cloneUnconfirmed = harness.server.callTool(
            "workspace_clone_repo",
            JSONObject()
                .put("projectId", projectId)
                .put("url", "git@github.com:org/private.git")
        )
        assertFalse(cloneUnconfirmed.ok)
        assertEquals(McpErrorCodes.CONFIRMATION_REQUIRED, cloneUnconfirmed.error?.errorCode)

        // When approved, SSH URL returns SSH_TRANSPORT_UNSUPPORTED
        val cloneSsh = harness.server.callTool(
            "workspace_clone_repo",
            JSONObject()
                .put("projectId", projectId)
                .put("url", "git@github.com:org/private.git"),
            confirmationHandler = McpConfirmationHandler { true }
        )
        assertFalse(cloneSsh.ok)
        assertEquals(McpErrorCodes.SSH_TRANSPORT_UNSUPPORTED, cloneSsh.error?.errorCode)
        assertFalse(cloneSsh.error!!.recoverableByAgent)

        // workspace_discover_group_repos returns discovered entries
        harness.discoveryService.results = listOf(
            com.pocketworker.app.core.git.DiscoveredRepo(
                id = "1",
                name = "service-a",
                fullName = "org/service-a",
                cloneUrl = "https://github.com/org/service-a.git",
                defaultBranch = "main",
                description = "Service A",
                isPrivate = true
            )
        )
        val discovered = harness.server.callTool(
            "workspace_discover_group_repos",
            JSONObject().put("url", "https://github.com/org")
        )
        assertTrue(discovered.ok)
        assertEquals(1, discovered.data!!.getInt("count"))
    }
}
