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

    fun getRepoSummary(entry: RepoEntry): RepoCardInfo

    fun getStatus(repoDir: File): GitStatusSummary

    fun stageFile(repoDir: File, relativePath: String)

    fun stageAll(repoDir: File)

    fun unstageFile(repoDir: File, relativePath: String)

    fun unstageAll(repoDir: File)

    fun discardFileChanges(repoDir: File, relativePath: String)

    fun discardAllChanges(repoDir: File)

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
        credentials: GitCredentials? = null,
        rebase: Boolean = false
    ): PullResultInfo

    fun push(
        repoDir: File,
        remoteName: String = "origin",
        branchName: String? = null,
        credentials: GitCredentials? = null,
        force: Boolean = false
    ): String

    fun getStashes(repoDir: File): List<GitStashEntry>

    fun createStash(
        repoDir: File,
        message: String? = null,
        includeUntracked: Boolean = true
    ): GitStashEntry?

    fun applyStash(repoDir: File, index: Int = 0)

    fun popStash(repoDir: File, index: Int = 0)

    fun dropStash(repoDir: File, index: Int)

    fun clearStashes(repoDir: File)

    fun getTags(repoDir: File): List<GitTag>

    fun createTag(
        repoDir: File,
        name: String,
        message: String? = null,
        authorName: String = DEFAULT_AUTHOR_NAME,
        authorEmail: String = DEFAULT_AUTHOR_EMAIL
    )

    fun deleteTag(repoDir: File, name: String)

    fun pushTags(
        repoDir: File,
        remoteName: String = "origin",
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

    /**
     * Returns explicit repository-local `user.name` / `user.email` from `.git/config`,
     * or `null` when the repository does not define its own local override.
     */
    fun getLocalAuthorConfig(repoDir: File): Pair<String, String>? = null

    fun setAuthorConfig(
        repoDir: File,
        name: String,
        email: String,
        saveGlobally: Boolean = false
    )

    /**
     * Clears repository-local `user.name` / `user.email` in `.git/config` so the repo
     * inherits from project-level or global default author settings.
     */
    fun clearLocalAuthorConfig(repoDir: File) = Unit

    companion object {
        const val DEFAULT_AUTHOR_NAME = "PocketWorker User"
        const val DEFAULT_AUTHOR_EMAIL = "user@pocketworker.local"

        /**
         * Resolves effective Git commit author with strict 3-level precedence:
         * 1. Repository local `.git/config` override
         * 2. Project-level author (`WorkspaceProject.authorName` / `authorEmail`)
         * 3. Global default Git author (`~/.gitconfig` / built-in fallback)
         */
        fun resolveEffectiveAuthor(
            gitEngine: GitEngine,
            repoDir: File,
            project: WorkspaceProject?
        ): Pair<String, String> {
            val local = gitEngine.getLocalAuthorConfig(repoDir)
            val fallback = gitEngine.getAuthorConfig(repoDir)
            if (local != null) {
                val resolvedName = local.first.ifBlank {
                    project?.authorName?.takeIf { it.isNotBlank() } ?: fallback.first
                }
                val resolvedEmail = local.second.ifBlank {
                    project?.authorEmail?.takeIf { it.isNotBlank() } ?: fallback.second
                }
                return resolvedName to resolvedEmail
            }
            val projName = project?.authorName?.trim()?.ifEmpty { null }
            val projEmail = project?.authorEmail?.trim()?.ifEmpty { null }
            if (projName != null || projEmail != null) {
                return (projName ?: fallback.first) to (projEmail ?: fallback.second)
            }
            return fallback
        }
    }
}
