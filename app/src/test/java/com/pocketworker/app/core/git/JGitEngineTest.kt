package com.pocketworker.app.core.git

import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JGitEngineTest {

    private lateinit var tempRoot: File
    private lateinit var engine: JGitEngine

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("pocketworker-jgit-test").toFile()
        AndroidSystemReader.install(File(tempRoot, "sysconfig"))
        engine = JGitEngine()
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun `initProject without git creates plain project`() {
        val dir = File(tempRoot, "plain-proj")
        engine.initProject(
            targetDir = dir,
            initGit = false,
            createReadmeAndCommit = true,
            projectName = "PlainProj"
        )

        assertFalse(engine.isGitRepository(dir))
        assertTrue(File(dir, "README.md").exists())
        val status = engine.getStatus(dir)
        assertFalse(status.isGitRepo)
    }

    @Test
    fun `initProject unborn git supports stage and unstage before first commit`() {
        val dir = File(tempRoot, "unborn-proj")
        engine.initProject(
            targetDir = dir,
            initialBranch = "dev",
            initGit = true,
            createReadmeAndCommit = false,
            projectName = "UnbornProj"
        )

        assertTrue(engine.isGitRepository(dir))
        var status = engine.getStatus(dir)
        assertTrue(status.isGitRepo)
        assertTrue(status.isUnborn)
        assertEquals("dev", status.currentBranch)

        File(dir, "hello.txt").writeText("hello")
        engine.stageFile(dir, "hello.txt")
        status = engine.getStatus(dir)
        assertEquals(listOf("hello.txt"), status.stagedChanges.map { it.path })

        engine.unstageFile(dir, "hello.txt")
        status = engine.getStatus(dir)
        assertTrue(status.stagedChanges.isEmpty())
        assertEquals(listOf("hello.txt"), status.unstagedChanges.map { it.path })
    }

    @Test
    fun `stage commit diff branches and history workflow`() {
        val dir = File(tempRoot, "repo-workflow")
        engine.initProject(
            targetDir = dir,
            initialBranch = "main",
            initGit = true,
            createReadmeAndCommit = true,
            projectName = "Workflow",
            authorName = "Tester",
            authorEmail = "tester@example.com"
        )

        // Create branch feature and commit on it
        engine.createBranch(dir, "feature", checkout = true)
        File(dir, "feature.kt").writeText("fun feature() = 42\n")
        engine.stageAll(dir)

        val stagedDiff = engine.getWorkingDiff(dir, "feature.kt", staged = true)
        assertTrue(stagedDiff.contains("fun feature() = 42"))

        val featureCommit = engine.commit(dir, "Add feature", "Tester", "tester@example.com")
        assertEquals("Add feature", featureCommit.shortMessage)

        val detail = engine.getCommitDetail(dir, featureCommit.id)
        assertEquals(listOf("feature.kt"), detail.changedFiles.map { it.path })
        assertEquals(FileChangeType.ADDED, detail.changedFiles.first().changeType)

        // History of main should still have 1 commit, feature should have 2
        val mainHistory = engine.getCommitHistory(dir, "refs/heads/main")
        val featureHistory = engine.getCommitHistory(dir, "refs/heads/feature")
        assertEquals(1, mainHistory.size)
        assertEquals(2, featureHistory.size)

        // Switch to main and merge feature
        engine.checkoutBranch(dir, "main")
        val mergeRes = engine.mergeBranch(dir, "feature")
        assertTrue(mergeRes.isSuccessful)
        assertTrue(File(dir, "feature.kt").exists())
        assertEquals(2, engine.getCommitHistory(dir, "refs/heads/main").size)

        // Rename and delete branch
        engine.renameBranch(dir, "feature", "feature-done")
        val branches = engine.getBranches(dir)
        assertTrue(branches.any { it.name == "feature-done" })
        engine.deleteBranch(dir, "feature-done")
        assertFalse(engine.getBranches(dir).any { it.name == "feature-done" })
    }

    @Test
    fun `clone push and pull roundtrip against bare repository`() {
        val bareDir = File(tempRoot, "remote.git")
        Git.init().setDirectory(bareDir).setBare(true).call().close()

        // Seed remote using first project
        val seedDir = File(tempRoot, "seed")
        engine.initProject(
            targetDir = seedDir,
            initialBranch = "dev",
            initGit = true,
            createReadmeAndCommit = true,
            projectName = "Seed",
            remoteUrl = bareDir.toURI().toString()
        )
        engine.push(seedDir, remoteName = "origin", branchName = "dev")

        // Clone into second directory using JGitEngine.cloneRepository
        val clone1Dir = File(tempRoot, "clone1")
        var sawProgress = false
        val cloneStatus = engine.cloneRepository(
            url = bareDir.toURI().toString(),
            targetDir = clone1Dir,
            branch = "dev",
            onProgress = { sawProgress = true }
        )
        assertTrue(sawProgress)
        assertTrue(cloneStatus.isGitRepo)
        assertEquals("dev", cloneStatus.currentBranch)
        assertTrue(File(clone1Dir, "README.md").exists())

        // Clone into third directory and make a change + push
        val clone2Dir = File(tempRoot, "clone2")
        engine.cloneRepository(url = bareDir.toURI().toString(), targetDir = clone2Dir, branch = "dev")
        File(clone2Dir, "notes.txt").writeText("from android\n")
        engine.stageAll(clone2Dir)
        engine.commit(clone2Dir, "Add notes from phone", "Phone", "phone@example.com")
        engine.push(clone2Dir, remoteName = "origin")

        // Pull in clone1 and verify notes.txt arrived
        val pullResult = engine.pull(clone1Dir, remoteName = "origin")
        assertTrue(pullResult.isSuccessful)
        assertTrue(File(clone1Dir, "notes.txt").exists())
        assertEquals("from android\n", File(clone1Dir, "notes.txt").readText())
    }

    @Test
    fun `remote management and deleted file staging`() {
        val dir = File(tempRoot, "remotes-test")
        engine.initProject(
            targetDir = dir,
            initialBranch = "main",
            initGit = true,
            createReadmeAndCommit = true
        )

        engine.addRemote(dir, "origin", "https://example.com/a.git")
        assertEquals("https://example.com/a.git", engine.getRemotes(dir).first().fetchUrl)

        engine.updateRemoteUrl(dir, "origin", "https://example.com/b.git")
        assertEquals("https://example.com/b.git", engine.getRemotes(dir).first().fetchUrl)

        // Delete tracked README.md and stage deletion
        assertTrue(File(dir, "README.md").delete())
        var status = engine.getStatus(dir)
        assertEquals(FileChangeType.DELETED, status.unstagedChanges.first().changeType)

        engine.stageFile(dir, "README.md")
        status = engine.getStatus(dir)
        assertEquals(FileChangeType.DELETED, status.stagedChanges.first().changeType)
        assertNotNull(engine.commit(dir, "Remove README", "A", "a@b.c"))

        engine.removeRemote(dir, "origin")
        assertTrue(engine.getRemotes(dir).isEmpty())
    }

    @Test
    fun `stash create apply pop drop and clear workflow`() {
        val dir = File(tempRoot, "stash-test")
        engine.initProject(
            targetDir = dir,
            initialBranch = "main",
            initGit = true,
            createReadmeAndCommit = true,
            projectName = "StashProj"
        )

        // Modify tracked file and create untracked file
        File(dir, "README.md").appendText("local edit\n")
        File(dir, "draft.txt").writeText("untracked draft\n")

        val created = engine.createStash(dir, message = "WIP feature", includeUntracked = true)
        assertNotNull(created)

        // Working directory should now be clean
        var status = engine.getStatus(dir)
        assertFalse(status.hasChanges)
        assertFalse(File(dir, "draft.txt").exists())

        val stashes = engine.getStashes(dir)
        assertEquals(1, stashes.size)
        assertTrue(stashes.first().message.contains("WIP feature"))

        // Apply keeps stash entry and restores changes + untracked
        engine.applyStash(dir, index = 0)
        status = engine.getStatus(dir)
        assertTrue(status.hasChanges)
        assertTrue(File(dir, "draft.txt").exists())
        assertEquals(1, engine.getStashes(dir).size)

        // Discard all and pop stash (restores and drops entry)
        engine.discardAllChanges(dir)
        assertFalse(engine.getStatus(dir).hasChanges)
        assertFalse(File(dir, "draft.txt").exists())

        engine.popStash(dir, index = 0)
        assertTrue(engine.getStatus(dir).hasChanges)
        assertTrue(File(dir, "draft.txt").exists())
        assertTrue(engine.getStashes(dir).isEmpty())

        // Create two stashes and test drop + clear
        engine.createStash(dir, message = "First", includeUntracked = true)
        File(dir, "second.txt").writeText("2")
        engine.createStash(dir, message = "Second", includeUntracked = true)
        assertEquals(2, engine.getStashes(dir).size)

        engine.dropStash(dir, index = 0)
        assertEquals(1, engine.getStashes(dir).size)
        engine.clearStashes(dir)
        assertTrue(engine.getStashes(dir).isEmpty())
    }

    @Test
    fun `tags create list push and delete workflow`() {
        val bareDir = File(tempRoot, "tags-remote.git")
        Git.init().setDirectory(bareDir).setBare(true).call().close()

        val dir = File(tempRoot, "tags-local")
        engine.initProject(
            targetDir = dir,
            initialBranch = "main",
            initGit = true,
            createReadmeAndCommit = true,
            remoteUrl = bareDir.toURI().toString()
        )
        engine.push(dir, remoteName = "origin", branchName = "main")

        engine.createTag(dir, name = "v1.0.0", message = "First release")
        engine.createTag(dir, name = "v0.9.0-beta", message = null)

        val tags = engine.getTags(dir)
        assertEquals(2, tags.size)
        val releaseTag = tags.first { it.name == "v1.0.0" }
        assertTrue(releaseTag.isAnnotated)
        assertEquals("First release", releaseTag.message)
        val betaTag = tags.first { it.name == "v0.9.0-beta" }
        assertFalse(betaTag.isAnnotated)

        val pushTagsMsg = engine.pushTags(dir, remoteName = "origin")
        assertTrue(pushTagsMsg.contains("Push"))

        // Verify bare remote received both tags
        Git.open(bareDir).use { bareGit ->
            val remoteTags = bareGit.tagList().call().map { it.name }
            assertTrue("refs/tags/v1.0.0" in remoteTags)
            assertTrue("refs/tags/v0.9.0-beta" in remoteTags)
        }

        engine.deleteTag(dir, "v0.9.0-beta")
        assertEquals(1, engine.getTags(dir).size)
    }

    @Test
    fun `discardAllChanges cleans tracked and untracked while keeping gitignored files`() {
        val dir = File(tempRoot, "discard-all-test")
        engine.initProject(
            targetDir = dir,
            initialBranch = "main",
            initGit = true,
            createReadmeAndCommit = true
        )

        File(dir, ".gitignore").writeText("ignored.log\n")
        engine.stageAll(dir)
        engine.commit(dir, "Add gitignore", "User", "u@e.com")

        File(dir, "README.md").writeText("modified")
        File(dir, "staged.txt").writeText("staged")
        engine.stageFile(dir, "staged.txt")
        File(dir, "untracked.txt").writeText("temp")
        File(dir, "ignored.log").writeText("keep me")

        engine.discardAllChanges(dir)

        val status = engine.getStatus(dir)
        assertFalse(status.hasChanges)
        assertFalse(File(dir, "staged.txt").exists())
        assertFalse(File(dir, "untracked.txt").exists())
        assertTrue(File(dir, "ignored.log").exists())
    }
}
