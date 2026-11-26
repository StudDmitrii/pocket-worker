package com.pocketworker.app.core.git

import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

class JGitEngine : GitEngine {

    override fun isGitRepository(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        return File(dir, Constants.DOT_GIT).exists()
    }

    override fun cloneRepository(
        url: String,
        targetDir: File,
        branch: String?,
        credentials: GitCredentials?,
        onProgress: (CloneProgress) -> Unit,
        isCancelled: () -> Boolean
    ): GitStatusSummary {
        val cleanUrl = url.trim()
        require(cleanUrl.isNotEmpty()) { "URL репозитория не может быть пустым" }
        if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            throw IllegalStateException("Целевая папка уже существует и не пуста: ${targetDir.name}")
        }
        targetDir.mkdirs()

        try {
            val command = Git.cloneRepository()
                .setURI(cleanUrl)
                .setDirectory(targetDir)
                .setCloneAllBranches(true)
                .setProgressMonitor(object : ProgressMonitor {
                    private var currentTask = "Подключение..."
                    private var totalWork = 0
                    private var completedWork = 0

                    override fun start(totalTasks: Int) {
                        onProgress(CloneProgress("Инициализация клонирования...", 0, totalTasks))
                    }

                    override fun beginTask(title: String?, totalWork: Int) {
                        currentTask = title ?: "Загрузка..."
                        this.totalWork = totalWork
                        this.completedWork = 0
                        onProgress(CloneProgress(currentTask, 0, totalWork))
                    }

                    override fun update(completed: Int) {
                        completedWork += completed
                        onProgress(CloneProgress(currentTask, completedWork, totalWork))
                    }

                    override fun endTask() {
                        onProgress(CloneProgress(currentTask, totalWork, totalWork))
                    }

                    override fun isCancelled(): Boolean = isCancelled()
                })

            if (!branch.isNullOrBlank()) {
                command.setBranch(branch.trim())
            }

            buildCredentialsProvider(credentials)?.let { command.setCredentialsProvider(it) }

            command.call().use { git ->
                configureAndroidRepo(git.repository)
            }
            return getStatus(targetDir)
        } catch (t: Throwable) {
            targetDir.deleteRecursively()
            throw t
        }
    }

    override fun initProject(
        targetDir: File,
        initialBranch: String,
        initGit: Boolean,
        createReadmeAndCommit: Boolean,
        projectName: String,
        remoteUrl: String?,
        authorName: String,
        authorEmail: String
    ) {
        if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            throw IllegalStateException("Папка проекта уже существует: ${targetDir.name}")
        }
        targetDir.mkdirs()

        if (!initGit) {
            if (createReadmeAndCommit) {
                File(targetDir, "README.md").writeText("# $projectName\n", StandardCharsets.UTF_8)
            }
            return
        }

        val branch = initialBranch.trim().ifEmpty { "main" }
        Git.init()
            .setDirectory(targetDir)
            .setInitialBranch(branch)
            .call()
            .use { git ->
                configureAndroidRepo(git.repository)
                setAuthorConfig(targetDir, authorName, authorEmail, saveGlobally = false)

                if (!remoteUrl.isNullOrBlank()) {
                    addRemote(targetDir, Constants.DEFAULT_REMOTE_NAME, remoteUrl.trim())
                }

                if (createReadmeAndCommit) {
                    val readme = File(targetDir, "README.md")
                    readme.writeText("# $projectName\n\nСоздано в PocketWorker.\n", StandardCharsets.UTF_8)
                    git.add().addFilepattern("README.md").call()
                    val ident = PersonIdent(
                        authorName.ifBlank { GitEngine.DEFAULT_AUTHOR_NAME },
                        authorEmail.ifBlank { GitEngine.DEFAULT_AUTHOR_EMAIL }
                    )
                    git.commit()
                        .setAuthor(ident)
                        .setCommitter(ident)
                        .setMessage("Initial commit")
                        .call()
                }
            }
    }

    override fun initGitInExistingProject(targetDir: File, initialBranch: String) {
        if (isGitRepository(targetDir)) return
        val branch = initialBranch.trim().ifEmpty { "main" }
        Git.init()
            .setDirectory(targetDir)
            .setInitialBranch(branch)
            .call()
            .use { git ->
                configureAndroidRepo(git.repository)
            }
    }

    override fun getRepoSummary(entry: RepoEntry): RepoCardInfo {
        val dir = File(entry.directoryPath)
        if (!dir.exists()) {
            return RepoCardInfo(
                entry = entry,
                isGitRepo = false,
                currentBranch = null,
                isUnborn = false,
                changedFilesCount = 0,
                untrackedCount = 0,
                aheadCount = 0,
                behindCount = 0,
                lastCommitMessage = null,
                lastCommitTime = null,
                remoteUrl = entry.remoteUrl,
                errorMessage = "Папка репозитория не найдена на диске"
            )
        }
        if (!isGitRepository(dir)) {
            return RepoCardInfo(
                entry = entry,
                isGitRepo = false,
                currentBranch = null,
                isUnborn = false,
                changedFilesCount = 0,
                untrackedCount = 0,
                aheadCount = 0,
                behindCount = 0,
                lastCommitMessage = null,
                lastCommitTime = dir.lastModified(),
                remoteUrl = entry.remoteUrl
            )
        }

        return try {
            Git.open(dir).use { git ->
                val repo = git.repository
                val status = getStatus(dir)
                val lastCommit = getCommitHistory(dir, limit = 1).firstOrNull()
                val originUrl = repo.config.getString("remote", "origin", "url") ?: entry.remoteUrl
                val changedSet = (status.stagedChanges.map { it.path } +
                    status.unstagedChanges.filter { it.changeType != FileChangeType.UNTRACKED }.map { it.path }).toSet()
                val untrackedCount = status.unstagedChanges.count { it.changeType == FileChangeType.UNTRACKED }
                RepoCardInfo(
                    entry = entry,
                    isGitRepo = true,
                    currentBranch = status.currentBranch,
                    isUnborn = status.isUnborn,
                    changedFilesCount = changedSet.size,
                    untrackedCount = untrackedCount,
                    aheadCount = status.aheadCount,
                    behindCount = status.behindCount,
                    lastCommitMessage = lastCommit?.shortMessage,
                    lastCommitTime = lastCommit?.timestampMillis ?: dir.lastModified(),
                    remoteUrl = originUrl
                )
            }
        } catch (t: Throwable) {
            RepoCardInfo(
                entry = entry,
                isGitRepo = true,
                currentBranch = null,
                isUnborn = false,
                changedFilesCount = 0,
                untrackedCount = 0,
                aheadCount = 0,
                behindCount = 0,
                lastCommitMessage = null,
                lastCommitTime = dir.lastModified(),
                remoteUrl = entry.remoteUrl,
                errorMessage = t.localizedMessage ?: "Ошибка чтения Git"
            )
        }
    }

    override fun getStatus(repoDir: File): GitStatusSummary {
        if (!isGitRepository(repoDir)) {
            return GitStatusSummary(
                isGitRepo = false,
                currentBranch = null,
                isDetachedHead = false,
                isUnborn = false,
                headCommitId = null,
                stagedChanges = emptyList(),
                unstagedChanges = emptyList(),
                aheadCount = 0,
                behindCount = 0,
                authorName = GitEngine.DEFAULT_AUTHOR_NAME,
                authorEmail = GitEngine.DEFAULT_AUTHOR_EMAIL
            )
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            val headRef = repo.exactRef(Constants.HEAD)
            val headObjId = repo.resolve(Constants.HEAD)
            val isUnborn = headObjId == null
            val isDetached = headRef != null && !headRef.isSymbolic
            val currentBranch = when {
                isDetached -> headObjId?.abbreviate(7)?.name() ?: "HEAD"
                else -> repo.branch
            }

            var ahead = 0
            var behind = 0
            if (!isDetached && !isUnborn && currentBranch != null) {
                val tracking = runCatching { BranchTrackingStatus.of(repo, currentBranch) }.getOrNull()
                if (tracking != null) {
                    ahead = tracking.aheadCount
                    behind = tracking.behindCount
                }
            }

            val status = git.status().call()
            val staged = mutableListOf<GitFileChange>()
            status.added.forEach { staged.add(GitFileChange(it, FileChangeType.ADDED, isStaged = true)) }
            status.changed.forEach { staged.add(GitFileChange(it, FileChangeType.MODIFIED, isStaged = true)) }
            status.removed.forEach { staged.add(GitFileChange(it, FileChangeType.DELETED, isStaged = true)) }

            val unstaged = mutableListOf<GitFileChange>()
            status.conflicting.forEach { unstaged.add(GitFileChange(it, FileChangeType.CONFLICTED, isStaged = false)) }
            status.modified.forEach { unstaged.add(GitFileChange(it, FileChangeType.MODIFIED, isStaged = false)) }
            status.missing.forEach { unstaged.add(GitFileChange(it, FileChangeType.DELETED, isStaged = false)) }
            status.untracked.forEach { unstaged.add(GitFileChange(it, FileChangeType.UNTRACKED, isStaged = false)) }

            val (authorName, authorEmail) = readAuthorFromRepo(repo)

            return GitStatusSummary(
                isGitRepo = true,
                currentBranch = currentBranch,
                isDetachedHead = isDetached,
                isUnborn = isUnborn,
                headCommitId = headObjId?.name,
                stagedChanges = staged.sortedBy { it.path.lowercase() },
                unstagedChanges = unstaged.sortedBy { it.path.lowercase() },
                aheadCount = ahead,
                behindCount = behind,
                authorName = authorName,
                authorEmail = authorEmail
            )
        }
    }

    override fun stageFile(repoDir: File, relativePath: String) {
        Git.open(repoDir).use { git ->
            val target = File(repoDir, relativePath)
            if (target.exists()) {
                git.add().addFilepattern(relativePath).call()
            } else {
                git.add().setUpdate(true).addFilepattern(relativePath).call()
            }
        }
    }

    override fun stageAll(repoDir: File) {
        Git.open(repoDir).use { git ->
            git.add().addFilepattern(".").call()
            git.add().setUpdate(true).addFilepattern(".").call()
        }
    }

    override fun unstageFile(repoDir: File, relativePath: String) {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            if (repo.resolve(Constants.HEAD) == null) {
                git.rm().setCached(true).addFilepattern(relativePath).call()
            } else {
                git.reset().addPath(relativePath).call()
            }
        }
    }

    override fun unstageAll(repoDir: File) {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            if (repo.resolve(Constants.HEAD) == null) {
                val status = git.status().call()
                val staged = status.added + status.changed + status.removed
                if (staged.isNotEmpty()) {
                    val rm = git.rm().setCached(true)
                    staged.forEach { rm.addFilepattern(it) }
                    rm.call()
                }
            } else {
                git.reset().setMode(ResetCommand.ResetType.MIXED).call()
            }
        }
    }

    override fun discardFileChanges(repoDir: File, relativePath: String) {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val status = git.status().addPath(relativePath).call()
            val isUntracked = relativePath in status.untracked
            val isAdded = relativePath in status.added

            if (isUntracked || isAdded) {
                if (isAdded) {
                    unstageFile(repoDir, relativePath)
                }
                val target = File(repoDir, relativePath)
                if (target.exists()) {
                    target.deleteRecursively()
                }
                return
            }

            if (repo.resolve(Constants.HEAD) != null) {
                git.reset().addPath(relativePath).call()
                git.checkout().addPath(relativePath).call()
            }
        }
    }

    override fun discardAllChanges(repoDir: File) {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val repoCanonical = repoDir.canonicalPath
            val status = git.status().call()
            val hasHead = repo.resolve(Constants.HEAD) != null

            val pathsToDelete = if (hasHead) {
                status.untracked
            } else {
                status.untracked + status.added
            }

            if (!hasHead) {
                unstageAll(repoDir)
            }

            pathsToDelete.forEach { relPath ->
                val target = File(repoDir, relPath)
                if (target.exists() && target.canonicalPath.startsWith(repoCanonical)) {
                    target.deleteRecursively()
                }
            }

            if (hasHead) {
                git.reset().setMode(ResetCommand.ResetType.HARD).call()
            }
        }
    }

    override fun commit(
        repoDir: File,
        message: String,
        authorName: String,
        authorEmail: String,
        amend: Boolean
    ): GitCommit {
        val cleanMsg = message.trim()
        require(cleanMsg.isNotEmpty()) { "Сообщение коммита не может быть пустым" }

        Git.open(repoDir).use { git ->
            val ident = PersonIdent(
                authorName.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_NAME },
                authorEmail.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_EMAIL }
            )
            val revCommit = git.commit()
                .setAuthor(ident)
                .setCommitter(ident)
                .setMessage(cleanMsg)
                .setAmend(amend)
                .call()
            return revCommit.toGitCommit(emptyList())
        }
    }

    override fun getBranches(repoDir: File): List<GitBranch> {
        if (!isGitRepository(repoDir)) return emptyList()
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val currentFull = repo.exactRef(Constants.HEAD)?.target?.name
            val refs = git.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call()

            return refs
                .filter { !it.name.endsWith("/HEAD") }
                .map { ref ->
                    val isRemote = ref.name.startsWith(Constants.R_REMOTES)
                    val shortName = Repository.shortenRefName(ref.name)
                    val isCurrent = !isRemote && ref.name == currentFull
                    val upstream = if (!isRemote) {
                        BranchConfig(repo.config, shortName).trackingBranch?.let {
                            Repository.shortenRefName(it)
                        }
                    } else null
                    val tracking = if (!isRemote && ref.objectId != null) {
                        runCatching { BranchTrackingStatus.of(repo, shortName) }.getOrNull()
                    } else null
                    GitBranch(
                        name = shortName,
                        fullName = ref.name,
                        isCurrent = isCurrent,
                        isRemote = isRemote,
                        targetCommitId = ref.objectId?.name,
                        upstreamName = upstream,
                        aheadCount = tracking?.aheadCount ?: 0,
                        behindCount = tracking?.behindCount ?: 0
                    )
                }
                .sortedWith(
                    compareBy<GitBranch> { it.isRemote }
                        .thenByDescending { it.isCurrent }
                        .thenBy { it.name.lowercase() }
                )
        }
    }

    override fun createBranch(
        repoDir: File,
        branchName: String,
        startPoint: String?,
        checkout: Boolean
    ) {
        val cleanName = branchName.trim()
        require(cleanName.isNotEmpty()) { "Имя ветки не может быть пустым" }

        Git.open(repoDir).use { git ->
            if (git.repository.resolve(Constants.HEAD) == null) {
                throw IllegalStateException("В репозитории ещё нет ни одного коммита для создания ветки")
            }
            if (checkout) {
                val cmd = git.checkout()
                    .setCreateBranch(true)
                    .setName(cleanName)
                if (!startPoint.isNullOrBlank()) {
                    cmd.setStartPoint(startPoint)
                }
                cmd.call()
            } else {
                val cmd = git.branchCreate().setName(cleanName)
                if (!startPoint.isNullOrBlank()) {
                    cmd.setStartPoint(startPoint)
                }
                cmd.call()
            }
        }
    }

    override fun checkoutBranch(repoDir: File, branchNameOrRef: String) {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            if (branchNameOrRef.startsWith(Constants.R_REMOTES)) {
                val shortRemote = Repository.shortenRefName(branchNameOrRef) // e.g. origin/dev
                val localName = shortRemote.substringAfter('/')
                val existingLocal = repo.findRef(Constants.R_HEADS + localName)
                if (existingLocal != null) {
                    git.checkout().setName(localName).call()
                } else {
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(localName)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .setStartPoint(branchNameOrRef)
                        .call()
                }
            } else {
                val target = if (branchNameOrRef.startsWith(Constants.R_HEADS)) {
                    branchNameOrRef.removePrefix(Constants.R_HEADS)
                } else {
                    branchNameOrRef
                }
                git.checkout().setName(target).call()
            }
        }
    }

    override fun renameBranch(repoDir: File, oldName: String, newName: String) {
        val cleanNew = newName.trim()
        require(cleanNew.isNotEmpty()) { "Новое имя ветки не может быть пустым" }
        Git.open(repoDir).use { git ->
            git.branchRename()
                .setOldName(oldName)
                .setNewName(cleanNew)
                .call()
        }
    }

    override fun deleteBranch(repoDir: File, branchName: String, force: Boolean) {
        Git.open(repoDir).use { git ->
            git.branchDelete()
                .setBranchNames(branchName)
                .setForce(force)
                .call()
        }
    }

    override fun mergeBranch(repoDir: File, sourceBranch: String): BranchMergeResult {
        Git.open(repoDir).use { git ->
            val targetId = git.repository.resolve(sourceBranch)
                ?: throw IllegalArgumentException("Ветка не найдена: $sourceBranch")
            val result = git.merge()
                .include(targetId)
                .call()
            val conflicts = result.conflicts?.keys?.toList().orEmpty()
            return BranchMergeResult(
                statusName = result.mergeStatus.toString(),
                isSuccessful = result.mergeStatus.isSuccessful,
                conflicts = conflicts
            )
        }
    }

    override fun getRemotes(repoDir: File): List<GitRemote> {
        if (!isGitRepository(repoDir)) return emptyList()
        Git.open(repoDir).use { git ->
            return git.remoteList().call().map { remote ->
                val fetch = remote.urIs.firstOrNull()?.toString().orEmpty()
                val push = remote.pushURIs.firstOrNull()?.toString() ?: fetch
                GitRemote(
                    name = remote.name,
                    fetchUrl = fetch,
                    pushUrl = push
                )
            }.sortedBy { it.name }
        }
    }

    override fun addRemote(repoDir: File, name: String, url: String) {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        require(cleanName.isNotEmpty()) { "Имя remote не может быть пустым" }
        require(cleanUrl.isNotEmpty()) { "URL remote не может быть пустым" }
        Git.open(repoDir).use { git ->
            git.remoteAdd()
                .setName(cleanName)
                .setUri(URIish(cleanUrl))
                .call()
        }
    }

    override fun updateRemoteUrl(repoDir: File, name: String, newUrl: String) {
        val cleanUrl = newUrl.trim()
        require(cleanUrl.isNotEmpty()) { "URL remote не может быть пустым" }
        Git.open(repoDir).use { git ->
            git.remoteSetUrl()
                .setRemoteName(name)
                .setRemoteUri(URIish(cleanUrl))
                .call()
        }
    }

    override fun removeRemote(repoDir: File, name: String) {
        Git.open(repoDir).use { git ->
            git.remoteRemove()
                .setRemoteName(name)
                .call()
        }
    }

    override fun fetch(
        repoDir: File,
        remoteName: String,
        credentials: GitCredentials?
    ): String {
        Git.open(repoDir).use { git ->
            val cmd = git.fetch()
                .setRemote(remoteName)
                .setRemoveDeletedRefs(true)
            buildCredentialsProvider(credentials)?.let { cmd.setCredentialsProvider(it) }
            val result = cmd.call()
            val updatesCount = result.trackingRefUpdates.size
            return if (updatesCount == 0) {
                "Fetch ($remoteName): всё актуально"
            } else {
                "Fetch ($remoteName): обновлено ссылок — $updatesCount"
            }
        }
    }

    override fun pull(
        repoDir: File,
        remoteName: String,
        credentials: GitCredentials?,
        rebase: Boolean
    ): PullResultInfo {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val currentBranch = repo.branch
            val cmd = git.pull()
                .setRemote(remoteName)
                .setRebase(rebase)
            if (currentBranch != null && BranchConfig(repo.config, currentBranch).trackingBranch == null) {
                cmd.setRemoteBranchName(currentBranch)
            }
            buildCredentialsProvider(credentials)?.let { cmd.setCredentialsProvider(it) }
            val result = cmd.call()
            val mergeStatus = result.mergeResult?.mergeStatus?.toString()
            val fetchCount = result.fetchResult?.trackingRefUpdates?.size ?: 0
            val summary = when {
                result.rebaseResult != null -> "Pull (rebase): ${result.rebaseResult.status}"
                mergeStatus != null -> "Pull: $mergeStatus"
                else -> if (result.isSuccessful) "Pull выполнен" else "Pull не выполнен"
            }
            return PullResultInfo(
                isSuccessful = result.isSuccessful,
                summary = summary,
                fetchSummary = "Обновлено ссылок: $fetchCount"
            )
        }
    }

    override fun push(
        repoDir: File,
        remoteName: String,
        branchName: String?,
        credentials: GitCredentials?,
        force: Boolean
    ): String {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            if (repo.resolve(Constants.HEAD) == null) {
                throw IllegalStateException("Нечего отправлять: сделайте хотя бы один коммит")
            }
            val targetBranch = branchName ?: repo.branch
                ?: throw IllegalStateException("Не удалось определить текущую ветку")

            val refSpec = RefSpec("${Constants.R_HEADS}$targetBranch:${Constants.R_HEADS}$targetBranch")
            val cmd = git.push()
                .setRemote(remoteName)
                .setRefSpecs(refSpec)
                .setForce(force)
            buildCredentialsProvider(credentials)?.let { cmd.setCredentialsProvider(it) }
            val results = cmd.call()
            val msg = validateAndFormatPushResults(results, remoteName, targetBranch)

            // Ensure upstream tracking is configured after a successful push
            val config = repo.config
            if (config.getString("branch", targetBranch, "remote") == null) {
                config.setString("branch", targetBranch, "remote", remoteName)
                config.setString("branch", targetBranch, "merge", Constants.R_HEADS + targetBranch)
                config.save()
            }

            return msg
        }
    }

    override fun getStashes(repoDir: File): List<GitStashEntry> {
        if (!isGitRepository(repoDir)) return emptyList()
        Git.open(repoDir).use { git ->
            return git.stashList().call().mapIndexed { index, commit ->
                GitStashEntry(
                    index = index,
                    commitId = commit.id.name,
                    message = commit.shortMessage,
                    timestampMillis = commit.commitTime.toLong() * 1000L
                )
            }
        }
    }

    override fun createStash(
        repoDir: File,
        message: String?,
        includeUntracked: Boolean
    ): GitStashEntry? {
        Git.open(repoDir).use { git ->
            if (git.repository.resolve(Constants.HEAD) == null) {
                throw IllegalStateException("Нельзя создать stash в репозитории без коммитов")
            }
            val cmd = git.stashCreate().setIncludeUntracked(includeUntracked)
            val cleanMsg = message?.trim()?.ifEmpty { null }
            if (cleanMsg != null) {
                cmd.setWorkingDirectoryMessage(cleanMsg)
            }
            val commit = cmd.call() ?: return null
            return GitStashEntry(
                index = 0,
                commitId = commit.id.name,
                message = commit.shortMessage,
                timestampMillis = commit.commitTime.toLong() * 1000L
            )
        }
    }

    override fun applyStash(repoDir: File, index: Int) {
        Git.open(repoDir).use { git ->
            git.stashApply()
                .setStashRef("stash@{$index}")
                .setRestoreIndex(true)
                .setRestoreUntracked(true)
                .call()
        }
    }

    override fun popStash(repoDir: File, index: Int) {
        Git.open(repoDir).use { git ->
            git.stashApply()
                .setStashRef("stash@{$index}")
                .setRestoreIndex(true)
                .setRestoreUntracked(true)
                .call()
            git.stashDrop()
                .setStashRef(index)
                .call()
        }
    }

    override fun dropStash(repoDir: File, index: Int) {
        Git.open(repoDir).use { git ->
            git.stashDrop()
                .setStashRef(index)
                .call()
        }
    }

    override fun clearStashes(repoDir: File) {
        Git.open(repoDir).use { git ->
            git.stashDrop()
                .setAll(true)
                .call()
        }
    }

    override fun getTags(repoDir: File): List<GitTag> {
        if (!isGitRepository(repoDir)) return emptyList()
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val refs = git.tagList().call()
            RevWalk(repo).use { revWalk ->
                return refs.map { ref ->
                    val peeled = repo.refDatabase.peel(ref)
                    val parsed = ref.objectId?.let { runCatching { revWalk.parseAny(it) }.getOrNull() }
                    if (parsed is RevTag) {
                        GitTag(
                            name = Repository.shortenRefName(ref.name),
                            fullName = ref.name,
                            targetCommitId = (peeled.peeledObjectId ?: parsed.`object`?.id)?.name,
                            isAnnotated = true,
                            message = parsed.fullMessage.trim().ifBlank { null }
                        )
                    } else {
                        GitTag(
                            name = Repository.shortenRefName(ref.name),
                            fullName = ref.name,
                            targetCommitId = (peeled.peeledObjectId ?: ref.objectId)?.name,
                            isAnnotated = false,
                            message = null
                        )
                    }
                }.sortedByDescending { it.name.lowercase() }
            }
        }
    }

    override fun createTag(
        repoDir: File,
        name: String,
        message: String?,
        authorName: String,
        authorEmail: String
    ) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Имя тега не может быть пустым" }
        Git.open(repoDir).use { git ->
            if (git.repository.resolve(Constants.HEAD) == null) {
                throw IllegalStateException("Нельзя создать тег в репозитории без коммитов")
            }
            val cleanMsg = message?.trim()?.ifEmpty { null }
            val cmd = git.tag().setName(cleanName)
            if (cleanMsg != null) {
                cmd.setAnnotated(true)
                    .setMessage(cleanMsg)
                    .setTagger(
                        PersonIdent(
                            authorName.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_NAME },
                            authorEmail.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_EMAIL }
                        )
                    )
            } else {
                cmd.setAnnotated(false)
            }
            cmd.call()
        }
    }

    override fun deleteTag(repoDir: File, name: String) {
        Git.open(repoDir).use { git ->
            git.tagDelete()
                .setTags(name)
                .call()
        }
    }

    override fun pushTags(
        repoDir: File,
        remoteName: String,
        credentials: GitCredentials?
    ): String {
        Git.open(repoDir).use { git ->
            val cmd = git.push()
                .setRemote(remoteName)
                .setPushTags()
            buildCredentialsProvider(credentials)?.let { cmd.setCredentialsProvider(it) }
            val results = cmd.call()
            return validateAndFormatPushResults(results, remoteName, "теги")
        }
    }

    private fun validateAndFormatPushResults(
        results: Iterable<PushResult>,
        remoteName: String,
        fallbackTarget: String
    ): String {
        val errors = mutableListOf<String>()
        val okMessages = mutableListOf<String>()
        for (pushResult in results) {
            for (update in pushResult.remoteUpdates) {
                when (update.status) {
                    RemoteRefUpdate.Status.OK,
                    RemoteRefUpdate.Status.UP_TO_DATE -> {
                        okMessages.add("${Repository.shortenRefName(update.remoteName)}: ${update.status}")
                    }
                    else -> {
                        errors.add(
                            "${Repository.shortenRefName(update.remoteName)}: ${update.status}" +
                                (update.message?.let { " ($it)" } ?: "")
                        )
                    }
                }
            }
        }
        if (errors.isNotEmpty()) {
            throw IllegalStateException("Ошибка Push: ${errors.joinToString("; ")}")
        }
        return if (okMessages.isEmpty()) {
            "Push ($fallbackTarget) в $remoteName выполнен"
        } else {
            "Push в $remoteName: ${okMessages.joinToString(", ")}"
        }
    }

    override fun getCommitHistory(
        repoDir: File,
        branchRef: String?,
        limit: Int,
        skip: Int
    ): List<GitCommit> {
        if (!isGitRepository(repoDir)) return emptyList()
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val startId: ObjectId = if (branchRef.isNullOrBlank()) {
                repo.resolve(Constants.HEAD) ?: return emptyList()
            } else {
                repo.resolve(branchRef) ?: return emptyList()
            }

            val badgesByCommit = buildRefBadgesMap(repo)
            val commits = mutableListOf<GitCommit>()
            RevWalk(repo).use { revWalk ->
                val startCommit = revWalk.parseCommit(startId)
                revWalk.markStart(startCommit)
                var index = 0
                for (rev in revWalk) {
                    if (index < skip) {
                        index++
                        continue
                    }
                    if (commits.size >= limit) break
                    val commitId = rev.id.name
                    commits.add(rev.toGitCommit(badgesByCommit[commitId].orEmpty()))
                    index++
                }
            }
            return commits
        }
    }

    override fun getCommitDetail(repoDir: File, commitId: String): CommitDetail {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val objId = repo.resolve(commitId)
                ?: throw IllegalArgumentException("Коммит не найден: $commitId")
            val badgesByCommit = buildRefBadgesMap(repo)

            RevWalk(repo).use { revWalk ->
                val commit = revWalk.parseCommit(objId)
                val parent = if (commit.parentCount > 0) {
                    revWalk.parseCommit(commit.getParent(0).id)
                } else null

                val changedFiles = ByteArrayOutputStream().use { out ->
                    DiffFormatter(out).use { df ->
                        df.setRepository(repo)
                        df.isDetectRenames = true
                        val entries = repo.newObjectReader().use { reader ->
                            val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }
                            val oldTree = if (parent != null) {
                                CanonicalTreeParser().apply { reset(reader, parent.tree) }
                            } else {
                                EmptyTreeIterator()
                            }
                            df.scan(oldTree, newTree)
                        }
                        entries.map { entry ->
                            val changeType = when (entry.changeType) {
                                DiffEntry.ChangeType.ADD -> FileChangeType.ADDED
                                DiffEntry.ChangeType.DELETE -> FileChangeType.DELETED
                                DiffEntry.ChangeType.RENAME -> FileChangeType.RENAMED
                                DiffEntry.ChangeType.COPY -> FileChangeType.ADDED
                                DiffEntry.ChangeType.MODIFY -> FileChangeType.MODIFIED
                            }
                            val path = if (changeType == FileChangeType.DELETED) entry.oldPath else entry.newPath
                            val oldPath = if (changeType == FileChangeType.RENAMED) entry.oldPath else null
                            GitFileChange(
                                path = path,
                                changeType = changeType,
                                isStaged = true,
                                oldPath = oldPath
                            )
                        }
                    }
                }
                return CommitDetail(
                    commit = commit.toGitCommit(badgesByCommit[commit.id.name].orEmpty()),
                    changedFiles = changedFiles
                )
            }
        }
    }

    override fun getWorkingDiff(repoDir: File, relativePath: String, staged: Boolean): String {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val status = git.status().addPath(relativePath).call()
            if (!staged && relativePath in status.untracked) {
                val file = File(repoDir, relativePath)
                if (!file.exists() || !file.isFile) return ""
                val bytes = file.readBytes()
                if (bytes.take(4096).any { it.toInt() == 0 }) {
                    return "Бинарный файл (${bytes.size} байт)"
                }
                val text = String(bytes, StandardCharsets.UTF_8)
                val lines = if (text.isEmpty()) {
                    emptyList()
                } else {
                    text.removeSuffix("\r\n").removeSuffix("\n").lines()
                }
                return buildString {
                    appendLine("--- /dev/null")
                    appendLine("+++ b/$relativePath")
                    appendLine("@@ -0,0 +1,${lines.size} @@")
                    lines.forEach { appendLine("+$it") }
                }
            }

            return ByteArrayOutputStream().use { out ->
                DiffFormatter(out).use { df ->
                    df.setRepository(repo)
                    df.setPathFilter(PathFilter.create(relativePath))
                    if (staged) {
                        val headId = repo.resolve("${Constants.HEAD}^{tree}")
                        val oldTree = repo.newObjectReader().use { reader ->
                            if (headId != null) {
                                CanonicalTreeParser().apply { reset(reader, headId) }
                            } else {
                                EmptyTreeIterator()
                            }
                        }
                        val indexTree = DirCacheIterator(repo.readDirCache())
                        df.format(oldTree, indexTree)
                    } else {
                        val indexTree = DirCacheIterator(repo.readDirCache())
                        val workTree = FileTreeIterator(repo)
                        df.format(indexTree, workTree)
                    }
                    df.flush()
                    out.toString(StandardCharsets.UTF_8.name())
                }
            }
        }
    }

    override fun getCommitFileDiff(repoDir: File, commitId: String, relativePath: String): String {
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val objId = repo.resolve(commitId)
                ?: throw IllegalArgumentException("Коммит не найден: $commitId")
            RevWalk(repo).use { revWalk ->
                val commit = revWalk.parseCommit(objId)
                val parent = if (commit.parentCount > 0) {
                    revWalk.parseCommit(commit.getParent(0).id)
                } else null

                return ByteArrayOutputStream().use { out ->
                    DiffFormatter(out).use { df ->
                        df.setRepository(repo)
                        df.isDetectRenames = true
                        df.setPathFilter(PathFilter.create(relativePath))
                        repo.newObjectReader().use { reader ->
                            val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }
                            val oldTree = if (parent != null) {
                                CanonicalTreeParser().apply { reset(reader, parent.tree) }
                            } else {
                                EmptyTreeIterator()
                            }
                            df.format(oldTree, newTree)
                        }
                        df.flush()
                        out.toString(StandardCharsets.UTF_8.name())
                    }
                }
            }
        }
    }

    override fun getAuthorConfig(repoDir: File): Pair<String, String> {
        if (!isGitRepository(repoDir)) {
            return GitEngine.DEFAULT_AUTHOR_NAME to GitEngine.DEFAULT_AUTHOR_EMAIL
        }
        Git.open(repoDir).use { git ->
            return readAuthorFromRepo(git.repository)
        }
    }

    override fun getLocalAuthorConfig(repoDir: File): Pair<String, String>? {
        if (!isGitRepository(repoDir)) return null
        return runCatching {
            Git.open(repoDir).use { git ->
                val localConfigFile = File(git.repository.directory, "config")
                val localOnly = org.eclipse.jgit.storage.file.FileBasedConfig(
                    localConfigFile,
                    org.eclipse.jgit.util.FS.DETECTED
                )
                localOnly.load()
                val name = localOnly.getString("user", null, "name")?.trim().orEmpty()
                val email = localOnly.getString("user", null, "email")?.trim().orEmpty()
                if (name.isEmpty() && email.isEmpty()) null else (name to email)
            }
        }.getOrNull()
    }

    override fun clearLocalAuthorConfig(repoDir: File) {
        if (!isGitRepository(repoDir)) return
        Git.open(repoDir).use { git ->
            val repoConfig = git.repository.config
            repoConfig.unset("user", null, "name")
            repoConfig.unset("user", null, "email")
            repoConfig.save()
        }
    }

    override fun setAuthorConfig(
        repoDir: File,
        name: String,
        email: String,
        saveGlobally: Boolean
    ) {
        val cleanName = name.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_NAME }
        val cleanEmail = email.trim().ifEmpty { GitEngine.DEFAULT_AUTHOR_EMAIL }
        Git.open(repoDir).use { git ->
            val repoConfig = git.repository.config
            repoConfig.setString("user", null, "name", cleanName)
            repoConfig.setString("user", null, "email", cleanEmail)
            repoConfig.save()
        }
        if (saveGlobally) {
            runCatching {
                val userConfig = org.eclipse.jgit.util.SystemReader.getInstance()
                    .openUserConfig(null, org.eclipse.jgit.util.FS.DETECTED)
                userConfig.load()
                userConfig.setString("user", null, "name", cleanName)
                userConfig.setString("user", null, "email", cleanEmail)
                userConfig.save()
            }
        }
    }

    private fun readAuthorFromRepo(repo: Repository): Pair<String, String> {
        val config = repo.config
        val name = config.getString("user", null, "name")
            ?.takeIf { it.isNotBlank() }
            ?: GitEngine.DEFAULT_AUTHOR_NAME
        val email = config.getString("user", null, "email")
            ?.takeIf { it.isNotBlank() }
            ?: GitEngine.DEFAULT_AUTHOR_EMAIL
        return name to email
    }

    private fun configureAndroidRepo(repo: Repository) {
        val config = repo.config
        config.setBoolean("core", null, "filemode", false)
        config.setBoolean("core", null, "symlinks", false)
        config.setString("core", null, "autocrlf", "false")
        config.save()
    }

    private fun buildCredentialsProvider(credentials: GitCredentials?): CredentialsProvider? {
        if (credentials == null || credentials.tokenOrPassword.isBlank()) return null
        val user = credentials.username.trim().ifEmpty { "x-access-token" }
        return UsernamePasswordCredentialsProvider(user, credentials.tokenOrPassword.trim())
    }

    private fun buildRefBadgesMap(repo: Repository): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        val allRefs: Collection<Ref> = repo.refDatabase.getRefsByPrefix(Constants.R_REFS)
        for (ref in allRefs) {
            if (ref.name.endsWith("/HEAD")) continue
            val peeled = repo.refDatabase.peel(ref)
            val id = (peeled.peeledObjectId ?: ref.objectId)?.name ?: continue
            val label = Repository.shortenRefName(ref.name)
            map.getOrPut(id) { mutableListOf() }.add(label)
        }
        return map
    }

    private fun RevCommit.toGitCommit(badges: List<String>): GitCommit {
        return GitCommit(
            id = id.name,
            shortId = id.abbreviate(7).name(),
            fullMessage = fullMessage.trimEnd(),
            shortMessage = shortMessage,
            authorName = authorIdent?.name ?: "Unknown",
            authorEmail = authorIdent?.emailAddress ?: "",
            timestampMillis = commitTime.toLong() * 1000L,
            parentIds = parents.map { it.id.name },
            refBadges = badges
        )
    }
}
