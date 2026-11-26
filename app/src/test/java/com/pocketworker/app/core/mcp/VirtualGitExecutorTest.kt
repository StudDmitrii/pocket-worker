package com.pocketworker.app.core.mcp

import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class VirtualGitExecutorTest {

    private lateinit var harness: McpTestHarness

    @Before
    fun setUp() {
        harness = McpTestHarness(confirmFileEdits = false)
    }

    @After
    fun tearDown() {
        harness.cleanup()
    }

    private suspend fun runGit(
        projectId: String,
        repoId: String,
        command: String,
        approveConfirm: Boolean = true
    ): McpToolResult = harness.server.callTool(
        name = "git_exec",
        arguments = JSONObject()
            .put("projectId", projectId)
            .put("repoId", repoId)
            .put("command", command),
        confirmationHandler = if (approveConfirm) McpConfirmationHandler { true } else null
    )

    @Test
    fun `rejects shell syntax, interactive flags, unsupported commands, and unsupported flags with clear guidance`() = runTest {
        val (project, repo) = harness.createProjectAndRepo()

        // 1. Shell chaining
        val chained = runGit(project.id, repo.id, "status && git log")
        assertFalse(chained.ok)
        assertEquals(McpErrorCodes.SHELL_SYNTAX_NOT_SUPPORTED, chained.error?.errorCode)
        assertTrue(chained.error!!.recoverableByAgent)

        // 2. Interactive rebase -i
        val rebaseI = runGit(project.id, repo.id, "rebase -i HEAD~1")
        assertFalse(rebaseI.ok)
        assertEquals(McpErrorCodes.INTERACTIVE_MODE_UNSUPPORTED, rebaseI.error?.errorCode)
        assertTrue(rebaseI.error!!.recoverableByAgent)

        // 3. Commit without -m
        val commitNoMsg = runGit(project.id, repo.id, "commit")
        assertFalse(commitNoMsg.ok)
        assertEquals(McpErrorCodes.INTERACTIVE_MODE_UNSUPPORTED, commitNoMsg.error?.errorCode)
        assertTrue(commitNoMsg.error!!.recoverableByAgent)

        // 4. git grep -> points to fs_grep
        val gitGrep = runGit(project.id, repo.id, "grep hello")
        assertFalse(gitGrep.ok)
        assertEquals(McpErrorCodes.UNSUPPORTED_COMMAND_USE_TOOL, gitGrep.error?.errorCode)
        assertTrue(gitGrep.error!!.recoverableByAgent)
        assertTrue(gitGrep.error!!.suggestedAlternatives.any { "fs_grep" in it })

        // 5. Unsupported device-limited commands (lfs, bisect, worktree, submodule) -> recoverableByAgent = false + userMessageHint
        for (cmd in listOf("lfs pull", "bisect start", "worktree list", "submodule update")) {
            val res = runGit(project.id, repo.id, cmd)
            assertFalse("Expected $cmd to fail", res.ok)
            assertEquals(McpErrorCodes.UNSUPPORTED_GIT_COMMAND, res.error?.errorCode)
            assertFalse("Expected $cmd recoverableByAgent=false", res.error!!.recoverableByAgent)
            assertFalse(res.error!!.userMessageHint.isNullOrBlank())
        }

        // 6. Unsupported flag on supported command (log --graph)
        val logGraph = runGit(project.id, repo.id, "log --graph --oneline")
        assertFalse(logGraph.ok)
        assertEquals(McpErrorCodes.UNSUPPORTED_GIT_FLAG, logGraph.error?.errorCode)
        assertTrue(logGraph.error!!.recoverableByAgent)
        assertTrue(logGraph.error!!.supportedFlags.isNotEmpty())

        // 7. SSH remote rejected
        val sshRemote = runGit(project.id, repo.id, "remote add origin git@github.com:user/repo.git")
        assertFalse(sshRemote.ok)
        assertEquals(McpErrorCodes.SSH_TRANSPORT_UNSUPPORTED, sshRemote.error?.errorCode)
        assertFalse(sshRemote.error!!.recoverableByAgent)
    }

    @Test
    fun `executes core local workflow status, add, commit, diff, log, show, blame, branch, switch, stash, tag, check-ignore`() = runTest {
        val (project, repo) = harness.createProjectAndRepo()
        val repoDir = File(repo.directoryPath)

        File(repoDir, ".gitignore").writeText("*.log\nbuild/\n")
        File(repoDir, "app.kt").writeText("fun greet() = \"hi\"\n")
        File(repoDir, "debug.log").writeText("ignored\n")

        val checkIgnore = runGit(project.id, repo.id, "check-ignore -v debug.log app.kt")
        assertTrue(checkIgnore.ok)
        assertTrue(checkIgnore.data!!.getString("stdout").contains("debug.log"))
        assertFalse(checkIgnore.data!!.getString("stdout").contains("app.kt"))

        assertTrue(runGit(project.id, repo.id, "add .gitignore app.kt").ok)
        val stagedDiff = runGit(project.id, repo.id, "diff --staged --name-only")
        assertTrue(stagedDiff.ok)
        assertTrue(stagedDiff.data!!.getString("stdout").contains("app.kt"))

        val commitRes = runGit(project.id, repo.id, "git commit -m \"feat: add greet\"")
        assertTrue(commitRes.ok)

        val logRes = runGit(project.id, repo.id, "log --oneline -n 5")
        assertTrue(logRes.ok)
        assertTrue(logRes.data!!.getString("stdout").contains("feat: add greet"))

        val showBlob = runGit(project.id, repo.id, "show HEAD:app.kt")
        assertTrue(showBlob.ok)
        assertTrue(showBlob.data!!.getString("stdout").contains("fun greet()"))

        val blameRes = runGit(project.id, repo.id, "blame app.kt")
        assertTrue(blameRes.ok)
        assertTrue(blameRes.data!!.getString("stdout").contains("fun greet()"))

        // Branch & switch
        assertTrue(runGit(project.id, repo.id, "switch -c feature/ui").ok)
        val current = runGit(project.id, repo.id, "rev-parse --abbrev-ref HEAD")
        assertEquals("feature/ui", current.data!!.getString("stdout"))

        // Stash push + list + pop
        File(repoDir, "app.kt").writeText("fun greet() = \"wip\"\n")
        assertTrue(runGit(project.id, repo.id, "stash push -m \"wip change\"").ok)
        assertTrue(File(repoDir, "app.kt").readText().contains("\"hi\""))
        assertTrue(runGit(project.id, repo.id, "stash list").data!!.getString("stdout").contains("wip change"))
        assertTrue(runGit(project.id, repo.id, "stash pop").ok)
        assertTrue(File(repoDir, "app.kt").readText().contains("\"wip\""))

        // Commit on feature and merge into main
        assertTrue(runGit(project.id, repo.id, "commit -am \"update greet\"").ok)
        assertTrue(runGit(project.id, repo.id, "tag -a v1.0 -m \"Release 1.0\"").ok)
        assertTrue(runGit(project.id, repo.id, "tag -l").data!!.getString("stdout").contains("v1.0"))

        assertTrue(runGit(project.id, repo.id, "switch main").ok)
        assertTrue(runGit(project.id, repo.id, "merge feature/ui").ok)
        assertTrue(File(repoDir, "app.kt").readText().contains("\"wip\""))
    }

    @Test
    fun `detects NON_FAST_FORWARD on push and resolves after pull`() = runTest {
        // Create a local bare remote repository
        val bareDir = File(harness.tempRoot, "remote-bare.git")
        Git.init().setDirectory(bareDir).setBare(true).setInitialBranch("main").call().close()
        val bareUrl = bareDir.toURI().toString()

        // Create two local repos pointing to the same bare remote
        val (project, repoA) = harness.createProjectAndRepo("SyncProj", "client-a")
        assertTrue(runGit(project.id, repoA.id, "remote add origin $bareUrl").ok)
        assertTrue(runGit(project.id, repoA.id, "push -u origin main").ok)

        // Clone into client-b via GitEngine directly and push a new commit
        val (repoBId, repoBDir) = harness.registry.allocateRepositoryDir(project.id, "client-b")
        harness.gitEngine.cloneRepository(bareUrl, repoBDir, "main", null)
        val repoB = com.pocketworker.app.core.git.RepoEntry(
            id = repoBId,
            projectId = project.id,
            name = "client-b",
            directoryPath = repoBDir.absolutePath,
            createdAt = System.currentTimeMillis(),
            remoteUrl = bareUrl
        )
        harness.registry.registerRepository(repoB)

        File(repoBDir, "remote_change.txt").writeText("from B\n")
        assertTrue(runGit(project.id, repoB.id, "add remote_change.txt").ok)
        assertTrue(runGit(project.id, repoB.id, "commit -m \"B commit\"").ok)
        assertTrue(runGit(project.id, repoB.id, "push origin main").ok)

        // Make a divergent commit in client-a and try to push -> MUST fail with NON_FAST_FORWARD
        File(repoA.directoryPath, "local_change.txt").writeText("from A\n")
        assertTrue(runGit(project.id, repoA.id, "add local_change.txt").ok)
        assertTrue(runGit(project.id, repoA.id, "commit -m \"A commit\"").ok)

        val pushFail = runGit(project.id, repoA.id, "push origin main")
        assertFalse(pushFail.ok)
        assertEquals(McpErrorCodes.NON_FAST_FORWARD, pushFail.error?.errorCode)
        assertTrue(pushFail.error!!.recoverableByAgent)

        // Pull and then push succeeds!
        val pullOk = runGit(project.id, repoA.id, "pull origin main")
        assertTrue("Pull failed: ${pullOk.error?.message}", pullOk.ok)
        val pushOk = runGit(project.id, repoA.id, "push origin main")
        assertTrue("Push after pull failed: ${pushOk.error?.message}", pushOk.ok)
    }
}
