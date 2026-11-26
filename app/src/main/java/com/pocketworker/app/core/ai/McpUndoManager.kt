package com.pocketworker.app.core.ai

import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.ProjectFileManager
import com.pocketworker.app.core.mcp.McpToolResult
import com.pocketworker.app.core.mcp.buildCompactUnifiedDiff
import com.pocketworker.app.core.mcp.matchesFileFilter
import com.pocketworker.app.core.mcp.walkProjectFiles
import com.pocketworker.app.core.storage.ProjectRegistry
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

data class ChangedFileSummary(
    val projectId: String,
    val repoId: String,
    val repoName: String,
    val relativePath: String,
    val addedLines: Int,
    val deletedLines: Int,
    val diffSnapshot: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("projectId", projectId)
        put("repoId", repoId)
        put("repoName", repoName)
        put("relativePath", relativePath)
        put("addedLines", addedLines)
        put("deletedLines", deletedLines)
        put("diffSnapshot", diffSnapshot)
    }

    companion object {
        fun fromJson(obj: JSONObject): ChangedFileSummary = ChangedFileSummary(
            projectId = obj.optString("projectId", ""),
            repoId = obj.optString("repoId", ""),
            repoName = obj.optString("repoName", ""),
            relativePath = obj.optString("relativePath", ""),
            addedLines = obj.optInt("addedLines", 0),
            deletedLines = obj.optInt("deletedLines", 0),
            diffSnapshot = obj.optString("diffSnapshot", "")
        )
    }
}

data class ArchivedTreeEntry(
    val relativePath: String,
    val isDirectory: Boolean,
    val blobId: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("relativePath", relativePath)
        put("isDirectory", isDirectory)
        if (blobId != null) put("blobId", blobId)
    }

    companion object {
        fun fromJson(obj: JSONObject): ArchivedTreeEntry = ArchivedTreeEntry(
            relativePath = obj.optString("relativePath", ""),
            isDirectory = obj.optBoolean("isDirectory", false),
            blobId = obj.optString("blobId", "").takeIf { it.isNotBlank() }
        )
    }
}

data class DirtyFileSnapshot(
    val relativePath: String,
    val existedOnDisk: Boolean,
    val blobId: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("relativePath", relativePath)
        put("existedOnDisk", existedOnDisk)
        if (blobId != null) put("blobId", blobId)
    }

    companion object {
        fun fromJson(obj: JSONObject): DirtyFileSnapshot = DirtyFileSnapshot(
            relativePath = obj.optString("relativePath", ""),
            existedOnDisk = obj.optBoolean("existedOnDisk", false),
            blobId = obj.optString("blobId", "").takeIf { it.isNotBlank() }
        )
    }
}

sealed interface McpUndoOperation {
    fun toJson(): JSONObject

    data class DeleteCreatedProject(val projectId: String) : McpUndoOperation {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("kind", "delete_project")
            put("projectId", projectId)
        }
    }

    data class DeleteCreatedRepo(val projectId: String, val repoId: String) : McpUndoOperation {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("kind", "delete_repo")
            put("projectId", projectId)
            put("repoId", repoId)
        }
    }

    data class DeleteCreatedPath(
        val repoDirPath: String,
        val relativePath: String
    ) : McpUndoOperation {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("kind", "delete_path")
            put("repoDirPath", repoDirPath)
            put("relativePath", relativePath)
        }
    }

    data class RestoreFileSnapshot(
        val repoDirPath: String,
        val relativePath: String,
        val existedBefore: Boolean,
        val blobId: String?
    ) : McpUndoOperation {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("kind", "restore_file")
            put("repoDirPath", repoDirPath)
            put("relativePath", relativePath)
            put("existedBefore", existedBefore)
            if (blobId != null) put("blobId", blobId)
        }
    }

    data class RestoreTreeSnapshot(
        val repoDirPath: String,
        val relativePath: String,
        val entries: List<ArchivedTreeEntry>
    ) : McpUndoOperation {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("kind", "restore_tree")
            put("repoDirPath", repoDirPath)
            put("relativePath", relativePath)
            put("entries", JSONArray().apply { entries.forEach { put(it.toJson()) } })
        }
    }

    data class MovePathBack(
        val sourceRepoDirPath: String,
        val sourcePath: String,
        val destRepoDirPath: String,
        val destPath: String
    ) : McpUndoOperation {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("kind", "move_back")
            put("sourceRepoDirPath", sourceRepoDirPath)
            put("sourcePath", sourcePath)
            put("destRepoDirPath", destRepoDirPath)
            put("destPath", destPath)
        }
    }

    data class RestoreGitCheckpoint(
        val repoDirPath: String,
        val headSymbolicRef: String?,
        val headCommitSha: String?,
        val wasUnborn: Boolean,
        val localBranches: Map<String, String>,
        val tags: Map<String, String>,
        val dirtyFiles: List<DirtyFileSnapshot>,
        val stagedPaths: List<String>,
        val untrackedBefore: List<String>
    ) : McpUndoOperation {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("kind", "restore_git")
            put("repoDirPath", repoDirPath)
            if (headSymbolicRef != null) put("headSymbolicRef", headSymbolicRef)
            if (headCommitSha != null) put("headCommitSha", headCommitSha)
            put("wasUnborn", wasUnborn)
            put("localBranches", JSONObject(localBranches))
            put("tags", JSONObject(tags))
            put("dirtyFiles", JSONArray().apply { dirtyFiles.forEach { put(it.toJson()) } })
            put("stagedPaths", JSONArray(stagedPaths))
            put("untrackedBefore", JSONArray(untrackedBefore))
        }
    }

    data class RemotePushNote(val command: String) : McpUndoOperation {
        override fun toJson(): JSONObject = JSONObject().apply {
            put("kind", "remote_push_note")
            put("command", command)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): McpUndoOperation? = when (obj.optString("kind")) {
            "delete_project" -> DeleteCreatedProject(obj.optString("projectId"))
            "delete_repo" -> DeleteCreatedRepo(obj.optString("projectId"), obj.optString("repoId"))
            "delete_path" -> DeleteCreatedPath(obj.optString("repoDirPath"), obj.optString("relativePath"))
            "restore_file" -> RestoreFileSnapshot(
                repoDirPath = obj.optString("repoDirPath"),
                relativePath = obj.optString("relativePath"),
                existedBefore = obj.optBoolean("existedBefore", false),
                blobId = obj.optString("blobId", "").takeIf { it.isNotBlank() }
            )
            "restore_tree" -> {
                val arr = obj.optJSONArray("entries") ?: JSONArray()
                val entries = (0 until arr.length()).mapNotNull { idx ->
                    arr.optJSONObject(idx)?.let { ArchivedTreeEntry.fromJson(it) }
                }
                RestoreTreeSnapshot(
                    repoDirPath = obj.optString("repoDirPath"),
                    relativePath = obj.optString("relativePath"),
                    entries = entries
                )
            }
            "move_back" -> MovePathBack(
                sourceRepoDirPath = obj.optString("sourceRepoDirPath"),
                sourcePath = obj.optString("sourcePath"),
                destRepoDirPath = obj.optString("destRepoDirPath"),
                destPath = obj.optString("destPath")
            )
            "restore_git" -> {
                val branchesObj = obj.optJSONObject("localBranches") ?: JSONObject()
                val branches = LinkedHashMap<String, String>()
                branchesObj.keys().forEach { k -> branches[k] = branchesObj.optString(k) }

                val tagsObj = obj.optJSONObject("tags") ?: JSONObject()
                val tags = LinkedHashMap<String, String>()
                tagsObj.keys().forEach { k -> tags[k] = tagsObj.optString(k) }

                val dirtyArr = obj.optJSONArray("dirtyFiles") ?: JSONArray()
                val dirty = (0 until dirtyArr.length()).mapNotNull { idx ->
                    dirtyArr.optJSONObject(idx)?.let { DirtyFileSnapshot.fromJson(it) }
                }

                val stagedArr = obj.optJSONArray("stagedPaths") ?: JSONArray()
                val staged = (0 until stagedArr.length()).map { stagedArr.optString(it) }

                val untrackedArr = obj.optJSONArray("untrackedBefore") ?: JSONArray()
                val untracked = (0 until untrackedArr.length()).map { untrackedArr.optString(it) }

                RestoreGitCheckpoint(
                    repoDirPath = obj.optString("repoDirPath"),
                    headSymbolicRef = obj.optString("headSymbolicRef", "").takeIf { it.isNotBlank() },
                    headCommitSha = obj.optString("headCommitSha", "").takeIf { it.isNotBlank() },
                    wasUnborn = obj.optBoolean("wasUnborn", false),
                    localBranches = branches,
                    tags = tags,
                    dirtyFiles = dirty,
                    stagedPaths = staged,
                    untrackedBefore = untracked
                )
            }
            "remote_push_note" -> RemotePushNote(obj.optString("command"))
            else -> null
        }
    }
}

data class TouchedFileRef(
    val projectId: String,
    val repoId: String,
    val repoName: String,
    val repoDirPath: String,
    val relativePath: String,
    val preTurnBlobId: String? = null
)

data class PreparedToolUndo(
    val toolName: String,
    val arguments: JSONObject,
    val preCapturedOps: List<McpUndoOperation> = emptyList(),
    val preTouchedFiles: List<TouchedFileRef> = emptyList(),
    val gitWasRepoBefore: Boolean = false
)

data class RollbackSummary(
    val appliedOperations: Int,
    val remoteWarnings: List<String> = emptyList()
)

class McpUndoManager(
    private val projectRegistry: ProjectRegistry,
    private val fileManager: ProjectFileManager,
    private val gitEngine: GitEngine,
    private val blobsDir: File
) {
    init {
        if (!blobsDir.exists()) {
            blobsDir.mkdirs()
        }
    }

    fun captureBeforeTool(toolName: String, arguments: JSONObject): PreparedToolUndo {
        if (arguments.optBoolean("dryRun", false)) {
            return PreparedToolUndo(toolName, arguments)
        }

        return runCatching {
            when (toolName) {
                "fs_write_file", "fs_edit_file" -> {
                    val projectId = arguments.optString("projectId", "")
                    val repoId = arguments.optString("repoId", "")
                    val relPath = arguments.optString("relativePath", arguments.optString("path", "")).trim().trimStart('/')
                    val repo = projectRegistry.findRepository(projectId, repoId)
                        ?: return PreparedToolUndo(toolName, arguments)
                    val repoDir = File(repo.directoryPath)
                    val target = runCatching { fileManager.resolveSafe(repoDir, relPath) }.getOrNull()
                        ?: return PreparedToolUndo(toolName, arguments)

                    val existed = target.exists() && target.isFile
                    val blobId = if (existed) saveFileBlob(target) else null
                    PreparedToolUndo(
                        toolName = toolName,
                        arguments = arguments,
                        preCapturedOps = listOf(
                            McpUndoOperation.RestoreFileSnapshot(
                                repoDirPath = repoDir.absolutePath,
                                relativePath = relPath,
                                existedBefore = existed,
                                blobId = blobId
                            )
                        ),
                        preTouchedFiles = listOf(
                            TouchedFileRef(projectId, repoId, repo.name, repoDir.absolutePath, relPath, blobId)
                        )
                    )
                }

                "fs_replace_across_files" -> {
                    val projectId = arguments.optString("projectId", "")
                    val repoId = arguments.optString("repoId", "")
                    val query = arguments.optString("query", "")
                    val isRegex = arguments.optBoolean("isRegex", false)
                    val caseSensitive = arguments.optBoolean("caseSensitive", true)
                    val fileGlob = arguments.optString("fileGlob", "").takeIf { it.isNotBlank() }
                    val maxFiles = arguments.optInt("maxFiles", 40).coerceIn(1, 100)
                    val repo = projectRegistry.findRepository(projectId, repoId)
                        ?: return PreparedToolUndo(toolName, arguments)
                    val repoDir = File(repo.directoryPath)

                    val regex = if (isRegex) {
                        runCatching {
                            Regex(query, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                        }.getOrNull()
                    } else {
                        Regex(Regex.escape(query), if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
                    } ?: return PreparedToolUndo(toolName, arguments)

                    val ops = mutableListOf<McpUndoOperation>()
                    val touched = mutableListOf<TouchedFileRef>()
                    walkProjectFiles(repoDir, skipIgnoredDirs = true) { file, rel ->
                        if (ops.size >= maxFiles) return@walkProjectFiles false
                        if (fileGlob != null && !matchesFileFilter(rel, fileGlob)) return@walkProjectFiles true
                        if (file.length() > MAX_BLOB_BYTES) return@walkProjectFiles true
                        val text = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
                            ?: return@walkProjectFiles true
                        if (regex.containsMatchIn(text)) {
                            val blobId = saveFileBlob(file)
                            ops.add(
                                McpUndoOperation.RestoreFileSnapshot(
                                    repoDirPath = repoDir.absolutePath,
                                    relativePath = rel,
                                    existedBefore = true,
                                    blobId = blobId
                                )
                            )
                            touched.add(TouchedFileRef(projectId, repoId, repo.name, repoDir.absolutePath, rel, blobId))
                        }
                        true
                    }
                    PreparedToolUndo(toolName, arguments, ops, touched)
                }

                "fs_manage_structure" -> {
                    val projectId = arguments.optString("projectId", "")
                    val repoId = arguments.optString("repoId", "")
                    val repo = projectRegistry.findRepository(projectId, repoId)
                        ?: return PreparedToolUndo(toolName, arguments)
                    val repoDir = File(repo.directoryPath)
                    val opsArr = arguments.optJSONArray("operations") ?: JSONArray()
                    val undoOps = mutableListOf<McpUndoOperation>()
                    val touched = mutableListOf<TouchedFileRef>()

                    for (i in 0 until opsArr.length()) {
                        val op = opsArr.optJSONObject(i) ?: continue
                        val type = op.optString("type", "").lowercase()
                        val srcRel = op.optString("path", "").trim().trimStart('/')
                        when (type) {
                            "mkdir" -> {
                                val target = runCatching { fileManager.resolveSafe(repoDir, srcRel) }.getOrNull()
                                if (target != null && !target.exists()) {
                                    undoOps.add(McpUndoOperation.DeleteCreatedPath(repoDir.absolutePath, srcRel))
                                }
                            }
                            "copy", "move" -> {
                                val toRel = op.optString("toPath", "").trim().trimStart('/')
                                val targetRepoId = op.optString("targetRepoId", "").ifBlank { repoId }
                                val targetRepo = projectRegistry.findRepository(projectId, targetRepoId) ?: continue
                                val targetRepoDir = File(targetRepo.directoryPath)
                                val srcFile = runCatching { fileManager.resolveSafe(repoDir, srcRel) }.getOrNull() ?: continue
                                val dstFile = runCatching { fileManager.resolveSafe(targetRepoDir, toRel) }.getOrNull() ?: continue

                                // Capture overwritten destination first so LIFO rollback restores it AFTER moving/deleting back
                                if (dstFile.exists()) {
                                    undoOps.add(captureEntryUndo(targetRepoDir, toRel, dstFile))
                                }
                                if (type == "move") {
                                    undoOps.add(
                                        McpUndoOperation.MovePathBack(
                                            sourceRepoDirPath = repoDir.absolutePath,
                                            sourcePath = srcRel,
                                            destRepoDirPath = targetRepoDir.absolutePath,
                                            destPath = toRel
                                        )
                                    )
                                    if (srcFile.isFile) {
                                        touched.add(TouchedFileRef(projectId, targetRepoId, targetRepo.name, targetRepoDir.absolutePath, toRel, null))
                                    }
                                } else {
                                    if (!dstFile.exists()) {
                                        undoOps.add(McpUndoOperation.DeleteCreatedPath(targetRepoDir.absolutePath, toRel))
                                    }
                                    if (srcFile.isFile) {
                                        touched.add(TouchedFileRef(projectId, targetRepoId, targetRepo.name, targetRepoDir.absolutePath, toRel, null))
                                    }
                                }
                            }
                        }
                    }
                    PreparedToolUndo(toolName, arguments, undoOps, touched)
                }

                "fs_delete_entry" -> {
                    val projectId = arguments.optString("projectId", "")
                    val repoId = arguments.optString("repoId", "")
                    val relPath = arguments.optString("relativePath", arguments.optString("path", "")).trim().trimStart('/')
                    val repo = projectRegistry.findRepository(projectId, repoId)
                        ?: return PreparedToolUndo(toolName, arguments)
                    val repoDir = File(repo.directoryPath)
                    val target = runCatching { fileManager.resolveSafe(repoDir, relPath) }.getOrNull()
                        ?: return PreparedToolUndo(toolName, arguments)
                    if (!target.exists()) return PreparedToolUndo(toolName, arguments)

                    val op = captureEntryUndo(repoDir, relPath, target)
                    val touched = if (target.isFile) {
                        val blob = (op as? McpUndoOperation.RestoreFileSnapshot)?.blobId
                        listOf(TouchedFileRef(projectId, repoId, repo.name, repoDir.absolutePath, relPath, blob))
                    } else emptyList()
                    PreparedToolUndo(toolName, arguments, listOf(op), touched)
                }

                "git_exec" -> {
                    val projectId = arguments.optString("projectId", "")
                    val repoId = arguments.optString("repoId", "")
                    val command = arguments.optString("command", "").trim()
                    val repo = projectRegistry.findRepository(projectId, repoId)
                        ?: return PreparedToolUndo(toolName, arguments)
                    val repoDir = File(repo.directoryPath)
                    val wasGit = gitEngine.isGitRepository(repoDir)
                    val tokens = command.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val sub = tokens.firstOrNull()?.let {
                        if (it.equals("git", ignoreCase = true)) tokens.getOrNull(1) else it
                    }?.lowercase().orEmpty()

                    if (sub in READ_ONLY_GIT_SUBCOMMANDS) {
                        return PreparedToolUndo(toolName, arguments, gitWasRepoBefore = wasGit)
                    }
                    if (sub == "push") {
                        return PreparedToolUndo(
                            toolName = toolName,
                            arguments = arguments,
                            preCapturedOps = listOf(McpUndoOperation.RemotePushNote(command)),
                            gitWasRepoBefore = wasGit
                        )
                    }
                    if (!wasGit && sub == "init") {
                        return PreparedToolUndo(
                            toolName = toolName,
                            arguments = arguments,
                            preCapturedOps = listOf(McpUndoOperation.DeleteCreatedPath(repoDir.absolutePath, ".git")),
                            gitWasRepoBefore = false
                        )
                    }
                    if (wasGit) {
                        val checkpoint = captureGitCheckpoint(repoDir)
                        PreparedToolUndo(
                            toolName = toolName,
                            arguments = arguments,
                            preCapturedOps = listOfNotNull(checkpoint),
                            gitWasRepoBefore = true
                        )
                    } else {
                        PreparedToolUndo(toolName, arguments, gitWasRepoBefore = false)
                    }
                }

                else -> PreparedToolUndo(toolName, arguments)
            }
        }.getOrDefault(PreparedToolUndo(toolName, arguments))
    }

    fun finalizeAfterTool(
        prepared: PreparedToolUndo,
        result: McpToolResult
    ): Pair<List<McpUndoOperation>, List<TouchedFileRef>> {
        if (!result.ok) {
            return emptyList<McpUndoOperation>() to emptyList()
        }
        val data = result.data ?: JSONObject()

        return when (prepared.toolName) {
            "workspace_create_project" -> {
                val createdId = data.optString("projectId", "")
                val ops = if (createdId.isNotBlank()) listOf(McpUndoOperation.DeleteCreatedProject(createdId)) else emptyList()
                ops to emptyList()
            }

            "workspace_create_repo", "workspace_clone_repo" -> {
                val projectId = data.optString("projectId", prepared.arguments.optString("projectId", ""))
                val repoId = data.optString("repoId", "")
                val ops = if (projectId.isNotBlank() && repoId.isNotBlank()) {
                    listOf(McpUndoOperation.DeleteCreatedRepo(projectId, repoId))
                } else emptyList()
                val touched = mutableListOf<TouchedFileRef>()
                if (prepared.toolName == "workspace_create_repo" && prepared.arguments.optBoolean("createReadme", true)) {
                    val repo = projectRegistry.findRepository(projectId, repoId)
                    if (repo != null && File(repo.directoryPath, "README.md").isFile) {
                        touched.add(TouchedFileRef(projectId, repoId, repo.name, repo.directoryPath, "README.md", null))
                    }
                }
                ops to touched
            }

            else -> prepared.preCapturedOps to prepared.preTouchedFiles
        }
    }

    fun computeChangedFileSummary(ref: TouchedFileRef): ChangedFileSummary {
        val repoDir = File(ref.repoDirPath)
        var diffText = ""

        if (repoDir.exists() && gitEngine.isGitRepository(repoDir)) {
            diffText = runCatching { gitEngine.getWorkingDiff(repoDir, ref.relativePath, staged = false) }
                .getOrDefault("")
            if (diffText.isBlank()) {
                diffText = runCatching { gitEngine.getWorkingDiff(repoDir, ref.relativePath, staged = true) }
                    .getOrDefault("")
            }
            if (diffText.isBlank()) {
                diffText = runCatching { gitEngine.getCommitFileDiff(repoDir, Constants.HEAD, ref.relativePath) }
                    .getOrDefault("")
            }
        }

        if (diffText.isBlank()) {
            val oldText = ref.preTurnBlobId?.let { readBlobText(it) }.orEmpty()
            val curFile = File(repoDir, ref.relativePath)
            val newText = if (curFile.isFile) runCatching { curFile.readText(StandardCharsets.UTF_8) }.getOrDefault("") else ""
            if (oldText != newText) {
                diffText = buildCompactUnifiedDiff(ref.relativePath, oldText, newText, maxDiffLines = 400)
            }
        }

        val (added, deleted) = countDiffLineStats(diffText)
        return ChangedFileSummary(
            projectId = ref.projectId,
            repoId = ref.repoId,
            repoName = ref.repoName,
            relativePath = ref.relativePath,
            addedLines = added,
            deletedLines = deleted,
            diffSnapshot = diffText.take(MAX_DIFF_SNAPSHOT_CHARS)
        )
    }

    fun loadLiveDiff(summary: ChangedFileSummary): String {
        val repo = projectRegistry.findRepository(summary.projectId, summary.repoId)
        if (repo != null) {
            val repoDir = File(repo.directoryPath)
            if (repoDir.exists() && gitEngine.isGitRepository(repoDir)) {
                val unstaged = runCatching { gitEngine.getWorkingDiff(repoDir, summary.relativePath, staged = false) }
                    .getOrDefault("")
                if (unstaged.isNotBlank()) return unstaged
                val staged = runCatching { gitEngine.getWorkingDiff(repoDir, summary.relativePath, staged = true) }
                    .getOrDefault("")
                if (staged.isNotBlank()) return staged
                val headDiff = runCatching { gitEngine.getCommitFileDiff(repoDir, Constants.HEAD, summary.relativePath) }
                    .getOrDefault("")
                if (headDiff.isNotBlank()) return headDiff
            }
        }
        return summary.diffSnapshot.ifBlank { "Нет видимых изменений в Git для файла ${summary.relativePath}." }
    }

    fun rollbackOperations(operationsChronological: List<McpUndoOperation>): RollbackSummary {
        var applied = 0
        val remoteWarnings = mutableListOf<String>()

        for (op in operationsChronological.asReversed()) {
            when (op) {
                is McpUndoOperation.DeleteCreatedProject -> {
                    runCatching {
                        projectRegistry.deleteProject(op.projectId, deleteFilesOnDisk = true)
                        applied++
                    }
                }

                is McpUndoOperation.DeleteCreatedRepo -> {
                    runCatching {
                        projectRegistry.deleteRepository(op.projectId, op.repoId, deleteFilesOnDisk = true)
                        applied++
                    }
                }

                is McpUndoOperation.DeleteCreatedPath -> {
                    runCatching {
                        val repoDir = File(op.repoDirPath)
                        val target = File(repoDir, op.relativePath)
                        if (target.exists() && target.canonicalPath.startsWith(repoDir.canonicalPath)) {
                            target.deleteRecursively()
                            applied++
                        }
                    }
                }

                is McpUndoOperation.RestoreFileSnapshot -> {
                    runCatching {
                        val repoDir = File(op.repoDirPath)
                        val target = fileManager.resolveSafe(repoDir, op.relativePath)
                        if (op.existedBefore) {
                            val bytes = op.blobId?.let { readBlobBytes(it) }
                            if (bytes != null) {
                                target.parentFile?.mkdirs()
                                target.writeBytes(bytes)
                                applied++
                            }
                        } else {
                            if (target.exists()) {
                                target.deleteRecursively()
                            }
                            pruneEmptyParents(target.parentFile, repoDir)
                            applied++
                        }
                    }
                }

                is McpUndoOperation.RestoreTreeSnapshot -> {
                    runCatching {
                        val repoDir = File(op.repoDirPath)
                        val rootTarget = fileManager.resolveSafe(repoDir, op.relativePath)
                        if (rootTarget.exists()) rootTarget.deleteRecursively()
                        for (entry in op.entries) {
                            val f = fileManager.resolveSafe(repoDir, entry.relativePath)
                            if (entry.isDirectory) {
                                f.mkdirs()
                            } else {
                                val bytes = entry.blobId?.let { readBlobBytes(it) } ?: ByteArray(0)
                                f.parentFile?.mkdirs()
                                f.writeBytes(bytes)
                            }
                        }
                        applied++
                    }
                }

                is McpUndoOperation.MovePathBack -> {
                    runCatching {
                        val srcDir = File(op.sourceRepoDirPath)
                        val dstDir = File(op.destRepoDirPath)
                        val originalFile = fileManager.resolveSafe(srcDir, op.sourcePath)
                        val movedFile = fileManager.resolveSafe(dstDir, op.destPath)
                        if (movedFile.exists()) {
                            originalFile.parentFile?.mkdirs()
                            if (originalFile.exists()) originalFile.deleteRecursively()
                            if (!movedFile.renameTo(originalFile)) {
                                movedFile.copyRecursively(originalFile, overwrite = true)
                                movedFile.deleteRecursively()
                            }
                            pruneEmptyParents(movedFile.parentFile, dstDir)
                            applied++
                        }
                    }
                }

                is McpUndoOperation.RestoreGitCheckpoint -> {
                    runCatching {
                        restoreGitCheckpoint(op)
                        applied++
                    }
                }

                is McpUndoOperation.RemotePushNote -> {
                    remoteWarnings.add("Удалённый push (${op.command}) нельзя отменить локально; локальное состояние откачено.")
                }
            }
        }

        return RollbackSummary(appliedOperations = applied, remoteWarnings = remoteWarnings)
    }

    private fun captureEntryUndo(repoDir: File, relPath: String, target: File): McpUndoOperation {
        if (target.isFile) {
            return McpUndoOperation.RestoreFileSnapshot(
                repoDirPath = repoDir.absolutePath,
                relativePath = relPath,
                existedBefore = true,
                blobId = saveFileBlob(target)
            )
        }
        val entries = mutableListOf<ArchivedTreeEntry>()
        entries.add(ArchivedTreeEntry(relPath, isDirectory = true))
        val rootCanonical = repoDir.canonicalFile
        fun walk(dir: File) {
            dir.listFiles().orEmpty().forEach { child ->
                if (child.name == ".git") return@forEach
                val childRel = child.canonicalFile.relativeTo(rootCanonical).invariantSeparatorsPath
                if (child.isDirectory) {
                    entries.add(ArchivedTreeEntry(childRel, isDirectory = true))
                    walk(child)
                } else if (entries.size < 250) {
                    entries.add(ArchivedTreeEntry(childRel, isDirectory = false, blobId = saveFileBlob(child)))
                }
            }
        }
        walk(target)
        return McpUndoOperation.RestoreTreeSnapshot(repoDir.absolutePath, relPath, entries)
    }

    private fun captureGitCheckpoint(repoDir: File): McpUndoOperation.RestoreGitCheckpoint? {
        return runCatching {
            Git.open(repoDir).use { git ->
                val repo = git.repository
                val headRef = repo.exactRef(Constants.HEAD)
                val headObjId = repo.resolve(Constants.HEAD)
                val headSymbolic = if (headRef != null && headRef.isSymbolic) headRef.target.name else null

                val localBranches = LinkedHashMap<String, String>()
                repo.refDatabase.getRefsByPrefix(Constants.R_HEADS).forEach { r ->
                    r.objectId?.name?.let { sha -> localBranches[r.name] = sha }
                }
                val tags = LinkedHashMap<String, String>()
                repo.refDatabase.getRefsByPrefix(Constants.R_TAGS).forEach { r ->
                    r.objectId?.name?.let { sha -> tags[r.name] = sha }
                }

                val status = git.status().call()
                val stagedPaths = (status.added + status.changed + status.removed).sorted()
                val dirtySet = (stagedPaths + status.modified + status.missing + status.untracked + status.conflicting).toSortedSet()
                val dirtySnapshots = dirtySet.take(200).map { rel ->
                    val f = File(repoDir, rel)
                    if (f.isFile) {
                        DirtyFileSnapshot(rel, existedOnDisk = true, blobId = saveFileBlob(f))
                    } else {
                        DirtyFileSnapshot(rel, existedOnDisk = false, blobId = null)
                    }
                }

                McpUndoOperation.RestoreGitCheckpoint(
                    repoDirPath = repoDir.absolutePath,
                    headSymbolicRef = headSymbolic,
                    headCommitSha = headObjId?.name,
                    wasUnborn = headObjId == null,
                    localBranches = localBranches,
                    tags = tags,
                    dirtyFiles = dirtySnapshots,
                    stagedPaths = stagedPaths,
                    untrackedBefore = status.untracked.sorted()
                )
            }
        }.getOrNull()
    }

    private fun restoreGitCheckpoint(cp: McpUndoOperation.RestoreGitCheckpoint) {
        val repoDir = File(cp.repoDirPath)
        if (!repoDir.exists() || !gitEngine.isGitRepository(repoDir)) return

        Git.open(repoDir).use { git ->
            val repo = git.repository
            // Clear any in-progress merge/cherry-pick state
            runCatching { repo.writeMergeHeads(null) }
            runCatching { repo.writeMergeCommitMsg(null) }
            runCatching { repo.writeCherryPickHead(null) }

            // Point HEAD back BEFORE deleting branches created after checkpoint
            if (cp.headSymbolicRef != null) {
                val headUpd = repo.updateRef(Constants.HEAD)
                headUpd.link(cp.headSymbolicRef)
            } else if (cp.headCommitSha != null) {
                val headUpd = repo.updateRef(Constants.HEAD, true)
                headUpd.setNewObjectId(ObjectId.fromString(cp.headCommitSha))
                headUpd.forceUpdate()
            }

            // Restore local branch refs and delete any branches created after checkpoint
            val currentBranches = repo.refDatabase.getRefsByPrefix(Constants.R_HEADS).map { it.name }.toSet()
            cp.localBranches.forEach { (refName, sha) ->
                val upd = repo.updateRef(refName)
                upd.setNewObjectId(ObjectId.fromString(sha))
                upd.forceUpdate()
            }
            (currentBranches - cp.localBranches.keys).forEach { extraBranch ->
                if (cp.wasUnborn || extraBranch != cp.headSymbolicRef) {
                    val del = repo.updateRef(extraBranch)
                    del.isForceUpdate = true
                    del.delete()
                }
            }

            // Restore tags
            val currentTags = repo.refDatabase.getRefsByPrefix(Constants.R_TAGS).map { it.name }.toSet()
            cp.tags.forEach { (refName, sha) ->
                val upd = repo.updateRef(refName)
                upd.setNewObjectId(ObjectId.fromString(sha))
                upd.forceUpdate()
            }
            (currentTags - cp.tags.keys).forEach { extraTag ->
                val del = repo.updateRef(extraTag)
                del.isForceUpdate = true
                del.delete()
            }

            // Reset working tree & index to checkpoint HEAD (or clear index if unborn)
            if (!cp.wasUnborn && cp.headCommitSha != null) {
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef(cp.headCommitSha).call()
            } else if (cp.wasUnborn) {
                runCatching { File(repo.directory, "index").delete() }
            }

            // Remove untracked files created after checkpoint
            val postStatus = git.status().call()
            val allowedUntracked = cp.untrackedBefore.toSet()
            postStatus.untracked.forEach { rel ->
                if (rel !in allowedUntracked) {
                    val f = File(repoDir, rel)
                    if (f.exists() && f.canonicalPath.startsWith(repoDir.canonicalPath)) {
                        f.deleteRecursively()
                    }
                }
            }

            // Restore exact dirty file contents from checkpoint
            cp.dirtyFiles.forEach { snap ->
                val f = fileManager.resolveSafe(repoDir, snap.relativePath)
                if (snap.existedOnDisk) {
                    val bytes = snap.blobId?.let { readBlobBytes(it) }
                    if (bytes != null) {
                        f.parentFile?.mkdirs()
                        f.writeBytes(bytes)
                    }
                } else if (f.exists()) {
                    f.deleteRecursively()
                }
            }

            // Restore staged paths
            cp.stagedPaths.forEach { rel ->
                val f = File(repoDir, rel)
                if (f.exists()) {
                    git.add().addFilepattern(rel).call()
                } else {
                    git.add().setUpdate(true).addFilepattern(rel).call()
                }
            }
        }
    }

    private fun saveFileBlob(file: File): String? {
        if (!file.isFile || file.length() > MAX_BLOB_BYTES) return null
        return runCatching {
            if (!blobsDir.exists()) blobsDir.mkdirs()
            val id = UUID.randomUUID().toString()
            val outFile = File(blobsDir, id)
            outFile.writeBytes(file.readBytes())
            id
        }.getOrNull()
    }

    private fun readBlobBytes(blobId: String): ByteArray? {
        val f = File(blobsDir, blobId)
        return if (f.isFile) runCatching { f.readBytes() }.getOrNull() else null
    }

    private fun readBlobText(blobId: String): String? {
        val bytes = readBlobBytes(blobId) ?: return null
        if (bytes.take(2048).any { it.toInt() == 0 }) return null
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun pruneEmptyParents(startDir: File?, stopAtDir: File) {
        var cur = startDir?.canonicalFile
        val root = stopAtDir.canonicalFile
        while (cur != null && cur != root && cur.path.startsWith(root.path)) {
            val children = cur.listFiles()
            if (children == null || children.isEmpty()) {
                cur.delete()
                cur = cur.parentFile?.canonicalFile
            } else {
                break
            }
        }
    }

    companion object {
        private const val MAX_BLOB_BYTES = 3L * 1024L * 1024L // 3 MB
        private const val MAX_DIFF_SNAPSHOT_CHARS = 24_000

        private val READ_ONLY_GIT_SUBCOMMANDS = setOf(
            "status", "diff", "log", "show", "blame", "rev-parse", "ls-files", "check-ignore", "fetch"
        )

        fun countDiffLineStats(diffText: String): Pair<Int, Int> {
            var added = 0
            var deleted = 0
            diffText.lineSequence().forEach { line ->
                when {
                    line.startsWith("+") && !line.startsWith("+++") -> added++
                    line.startsWith("-") && !line.startsWith("---") -> deleted++
                }
            }
            return added to deleted
        }
    }
}
