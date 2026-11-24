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
}
