package com.pocketworker.app.core.git

import java.io.File

interface GitEngine {

    fun isGitRepository(dir: File): Boolean

    fun cloneRepository(
        url: String,
        targetDir: File,
        branch: String? = null,
        credentials: GitCredentials? = null,
        onProgress: (CloneProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): GitStatusSummary

    fun initProject(
        targetDir: File,
        initialBranch: String = "main",
        initGit: Boolean = true,
        createReadmeAndCommit: Boolean = true,
        projectName: String = targetDir.name,
        remoteUrl: String? = null,
        authorName: String = DEFAULT_AUTHOR_NAME,
        authorEmail: String = DEFAULT_AUTHOR_EMAIL
    )

    fun initGitInExistingProject(
        targetDir: File,
        initialBranch: String = "main"
    )

    fun getProjectSummary(entry: ProjectEntry): ProjectCardInfo

    fun getStatus(repoDir: File): GitStatusSummary

    fun stageFile(repoDir: File, relativePath: String)

    fun stageAll(repoDir: File)

    fun unstageFile(repoDir: File, relativePath: String)

    fun unstageAll(repoDir: File)

    fun discardFileChanges(repoDir: File, relativePath: String)

    fun commit(
        repoDir: File,
        message: String,
        authorName: String,
        authorEmail: String,
        amend: Boolean = false
    ): GitCommit

    fun getBranches(repoDir: File): List<GitBranch>

    fun createBranch(
        repoDir: File,
        branchName: String,
        startPoint: String? = null,
        checkout: Boolean = true
    )

    fun checkoutBranch(repoDir: File, branchNameOrRef: String)

    fun renameBranch(repoDir: File, oldName: String, newName: String)

    fun deleteBranch(repoDir: File, branchName: String, force: Boolean = false)

    fun mergeBranch(repoDir: File, sourceBranch: String): BranchMergeResult

    fun getRemotes(repoDir: File): List<GitRemote>

    fun addRemote(repoDir: File, name: String, url: String)

    fun updateRemoteUrl(repoDir: File, name: String, newUrl: String)

    fun removeRemote(repoDir: File, name: String)

    fun fetch(
        repoDir: File,
        remoteName: String = "origin",
        credentials: GitCredentials? = null
    ): String

    fun pull(
        repoDir: File,
        remoteName: String = "origin",
        credentials: GitCredentials? = null
    ): PullResultInfo

    fun push(
        repoDir: File,
        remoteName: String = "origin",
        branchName: String? = null,
        credentials: GitCredentials? = null
    ): String

    fun getCommitHistory(
        repoDir: File,
        branchRef: String? = null,
        limit: Int = 50,
        skip: Int = 0
    ): List<GitCommit>

    fun getCommitDetail(repoDir: File, commitId: String): CommitDetail

    fun getWorkingDiff(repoDir: File, relativePath: String, staged: Boolean): String

    fun getCommitFileDiff(repoDir: File, commitId: String, relativePath: String): String

    fun getAuthorConfig(repoDir: File): Pair<String, String>

    fun setAuthorConfig(
        repoDir: File,
        name: String,
        email: String,
        saveGlobally: Boolean = false
    )

    companion object {
        const val DEFAULT_AUTHOR_NAME = "PocketWorker User"
        const val DEFAULT_AUTHOR_EMAIL = "user@pocketworker.local"
    }
}
