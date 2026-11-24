package com.pocketworker.app.core.git

data class ProjectEntry(
    val id: String,
    val name: String,
    val directoryPath: String,
    val createdAt: Long,
    val remoteUrl: String? = null
)

data class ProjectCardInfo(
    val entry: ProjectEntry,
    val isGitRepo: Boolean,
    val currentBranch: String?,
    val isUnborn: Boolean,
    val changedFilesCount: Int,
    val untrackedCount: Int,
    val aheadCount: Int,
    val behindCount: Int,
    val lastCommitMessage: String?,
    val lastCommitTime: Long?,
    val remoteUrl: String?,
    val errorMessage: String? = null
)

data class GitCredentials(
    val username: String,
    val tokenOrPassword: String
)

data class CloneProgress(
    val stage: String,
    val completed: Int,
    val total: Int
)

data class GitBranch(
    val name: String,
    val fullName: String,
    val isCurrent: Boolean,
    val isRemote: Boolean,
    val targetCommitId: String?,
    val upstreamName: String? = null,
    val aheadCount: Int = 0,
    val behindCount: Int = 0
)

data class GitRemote(
    val name: String,
    val fetchUrl: String,
    val pushUrl: String
)

enum class FileChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    UNTRACKED,
    RENAMED,
    CONFLICTED
}

data class GitFileChange(
    val path: String,
    val changeType: FileChangeType,
    val isStaged: Boolean,
    val oldPath: String? = null
)

data class GitStatusSummary(
    val isGitRepo: Boolean,
    val currentBranch: String?,
    val isDetachedHead: Boolean,
    val isUnborn: Boolean,
    val headCommitId: String?,
    val stagedChanges: List<GitFileChange>,
    val unstagedChanges: List<GitFileChange>,
    val aheadCount: Int,
    val behindCount: Int,
    val authorName: String,
    val authorEmail: String
) {
    val hasChanges: Boolean
        get() = stagedChanges.isNotEmpty() || unstagedChanges.isNotEmpty()
}

data class GitCommit(
    val id: String,
    val shortId: String,
    val fullMessage: String,
    val shortMessage: String,
    val authorName: String,
    val authorEmail: String,
    val timestampMillis: Long,
    val parentIds: List<String>,
    val refBadges: List<String> = emptyList()
)

data class CommitDetail(
    val commit: GitCommit,
    val changedFiles: List<GitFileChange>
)

data class ProjectFileItem(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val gitChangeType: FileChangeType? = null
)

data class BranchMergeResult(
    val statusName: String,
    val isSuccessful: Boolean,
    val conflicts: List<String> = emptyList()
)

data class PullResultInfo(
    val isSuccessful: Boolean,
    val summary: String,
    val fetchSummary: String? = null
)
