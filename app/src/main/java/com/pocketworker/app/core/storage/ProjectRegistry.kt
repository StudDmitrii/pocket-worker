package com.pocketworker.app.core.storage

import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.git.WorkspaceProject
import com.pocketworker.app.core.i18n.AppLocale
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class ProjectRegistry(
    private val metadataFile: File,
    val projectsRootDir: File
) {

    private data class StoredProject(
        val project: WorkspaceProject,
        val repositories: List<RepoEntry>
    )

    // In-memory tracking of projects/repositories created during this app session
    // (manually or by AI via MCP) so their cards animate with a "Новый" entrance highlight.
    private val pendingNewProjectIds = linkedSetOf<String>()
    private val pendingNewRepoIds = linkedSetOf<String>()

    @Synchronized
    fun getPendingNewProjectIds(): Set<String> = pendingNewProjectIds.toSet()

    @Synchronized
    fun markProjectSeen(projectId: String) {
        pendingNewProjectIds.remove(projectId)
    }

    @Synchronized
    fun getPendingNewRepoIds(): Set<String> = pendingNewRepoIds.toSet()

    @Synchronized
    fun markRepoSeen(repoId: String) {
        pendingNewRepoIds.remove(repoId)
    }

    init {
        if (!projectsRootDir.exists()) {
            projectsRootDir.mkdirs()
        }
        metadataFile.parentFile?.mkdirs()
    }

    @Synchronized
    fun listProjects(): List<WorkspaceProject> {
        val records = loadAndReconcileAll()
        return records.map { it.project }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun findProjectById(projectId: String): WorkspaceProject? {
        return loadAndReconcileAll().firstOrNull { it.project.id == projectId }?.project
    }

    @Synchronized
    fun createProject(name: String, description: String = ""): WorkspaceProject {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { AppLocale.current.homeErrEmptyProjectName }

        val records = loadAndReconcileAll().toMutableList()
        val dir = allocateUniqueChildDir(projectsRootDir, cleanName, fallback = "project")
        if (!dir.mkdirs() && !dir.exists()) {
            throw IllegalStateException(AppLocale.current.registryErrCreateDirFailed(dir.absolutePath))
        }

        val project = WorkspaceProject(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            directoryPath = dir.absolutePath,
            createdAt = System.currentTimeMillis(),
            description = description.trim()
        )
        val targetCanonical = dir.canonicalPath
        records.removeAll { File(it.project.directoryPath).canonicalPath == targetCanonical }
        records.add(StoredProject(project, emptyList()))
        writeRecords(records)
        pendingNewProjectIds.add(project.id)
        return project
    }

    @Synchronized
    fun updateProject(projectId: String, newName: String, newDescription: String) {
        val cleanName = newName.trim()
        require(cleanName.isNotEmpty()) { AppLocale.current.homeErrEmptyProjectName }
        val records = loadAndReconcileAll().map { stored ->
            if (stored.project.id == projectId) {
                stored.copy(
                    project = stored.project.copy(
                        name = cleanName,
                        description = newDescription.trim()
                    )
                )
            } else {
                stored
            }
        }
        writeRecords(records)
    }

    @Synchronized
    fun updateProjectAuthor(projectId: String, authorName: String?, authorEmail: String?) {
        val cleanName = authorName?.trim()?.ifEmpty { null }
        val cleanEmail = authorEmail?.trim()?.ifEmpty { null }
        val records = loadAndReconcileAll().map { stored ->
            if (stored.project.id == projectId) {
                stored.copy(
                    project = stored.project.copy(
                        authorName = cleanName,
                        authorEmail = cleanEmail
                    )
                )
            } else {
                stored
            }
        }
        writeRecords(records)
    }

    @Synchronized
    fun deleteProject(projectId: String, deleteFilesOnDisk: Boolean = true): List<String> {
        val records = loadAndReconcileAll()
        val target = records.firstOrNull { it.project.id == projectId }
        val remaining = records.filterNot { it.project.id == projectId }
        writeRecords(remaining)
        pendingNewProjectIds.remove(projectId)
        target?.repositories?.forEach { pendingNewRepoIds.remove(it.id) }
        if (deleteFilesOnDisk && target != null) {
            File(target.project.directoryPath).deleteRecursively()
        }
        return target?.repositories?.map { it.id }.orEmpty()
    }

    @Synchronized
    fun listRepositories(projectId: String): List<RepoEntry> {
        val records = loadAndReconcileAll()
        val target = records.firstOrNull { it.project.id == projectId } ?: return emptyList()
        return target.repositories.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun findRepository(projectId: String, repoId: String): RepoEntry? {
        return listRepositories(projectId).firstOrNull { it.id == repoId }
    }

    @Synchronized
    fun allocateRepositoryDir(projectId: String, preferredName: String): Pair<String, File> {
        val project = findProjectById(projectId)
            ?: throw IllegalStateException(AppLocale.current.registryErrProjectNotFound(projectId))
        val projectDir = File(project.directoryPath)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        val repoDir = allocateUniqueChildDir(projectDir, preferredName, fallback = "repository")
        return UUID.randomUUID().toString() to repoDir
    }

    @Synchronized
    fun registerRepository(entry: RepoEntry) {
        val targetCanonical = File(entry.directoryPath).canonicalPath
        val records = loadAndReconcileAll().map { stored ->
            if (stored.project.id == entry.projectId) {
                val updatedRepos = stored.repositories.filterNot {
                    it.id == entry.id || File(it.directoryPath).canonicalPath == targetCanonical
                } + entry
                stored.copy(repositories = updatedRepos)
            } else {
                stored
            }
        }
        writeRecords(records)
        pendingNewRepoIds.add(entry.id)
    }

    @Synchronized
    fun updateRepositoryRemoteUrl(projectId: String, repoId: String, remoteUrl: String?) {
        val records = loadAndReconcileAll().map { stored ->
            if (stored.project.id == projectId) {
                val updatedRepos = stored.repositories.map { repo ->
                    if (repo.id == repoId) repo.copy(remoteUrl = remoteUrl) else repo
                }
                stored.copy(repositories = updatedRepos)
            } else {
                stored
            }
        }
        writeRecords(records)
    }

    @Synchronized
    fun deleteRepository(
        projectId: String,
        repoId: String,
        deleteFilesOnDisk: Boolean = true
    ) {
        val records = loadAndReconcileAll()
        var targetRepo: RepoEntry? = null
        val updated = records.map { stored ->
            if (stored.project.id == projectId) {
                targetRepo = stored.repositories.firstOrNull { it.id == repoId }
                stored.copy(repositories = stored.repositories.filterNot { it.id == repoId })
            } else {
                stored
            }
        }
        writeRecords(updated)
        pendingNewRepoIds.remove(repoId)
        if (deleteFilesOnDisk && targetRepo != null) {
            File(targetRepo!!.directoryPath).deleteRecursively()
        }
    }

    private fun allocateUniqueChildDir(parentDir: File, preferredName: String, fallback: String): File {
        val baseSlug = preferredName
            .trim()
            .lowercase()
            .replace(Regex("[^a-zа-яё0-9._-]+"), "-")
            .trim('-', '.')
            .ifEmpty { fallback }
        var candidate = File(parentDir, baseSlug)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(parentDir, "$baseSlug-$suffix")
            suffix++
        }
        return candidate
    }

    private fun loadAndReconcileAll(): List<StoredProject> {
        val rawRecords = readStoredRecords()
        var dirty = false

        // Deduplicate any duplicate records pointing to the same canonical project folder
        val recordsByCanonical = LinkedHashMap<String, StoredProject>()
        for (entry in rawRecords) {
            val canonical = File(entry.project.directoryPath).canonicalPath
            val existing = recordsByCanonical[canonical]
            if (existing == null) {
                recordsByCanonical[canonical] = entry
            } else {
                dirty = true
                val preferNew = when {
                    entry.repositories.size != existing.repositories.size ->
                        entry.repositories.size > existing.repositories.size
                    entry.project.description.isNotBlank() != existing.project.description.isNotBlank() ->
                        entry.project.description.isNotBlank()
                    else -> entry.project.createdAt >= existing.project.createdAt
                }
                val winner = if (preferNew) entry else existing
                val mergedRepos = (existing.repositories + entry.repositories)
                    .distinctBy { File(it.directoryPath).canonicalPath }
                    .map { it.copy(projectId = winner.project.id) }
                recordsByCanonical[canonical] = winner.copy(repositories = mergedRepos)
            }
        }
        val records = recordsByCanonical.values.toMutableList()

        // Discover untracked folders directly under projectsRootDir
        val knownProjectPaths = recordsByCanonical.keys
        val topLevelDirs = projectsRootDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }.orEmpty()

        for (dir in topLevelDirs) {
            if (dir.canonicalPath !in knownProjectPaths) {
                // If an untracked folder directly contains .git, wrap it into a repo subfolder?
                // Keep directory untouched if it's a container folder, or discover inner folders as repos.
                val projectId = UUID.randomUUID().toString()
                val newProject = WorkspaceProject(
                    id = projectId,
                    name = dir.name,
                    directoryPath = dir.absolutePath,
                    createdAt = dir.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                )
                records.add(StoredProject(newProject, emptyList()))
                dirty = true
            }
        }

        // Reconcile repository subfolders inside each project directory
        val reconciled = records.map { stored ->
            val projectDir = File(stored.project.directoryPath)
            if (!projectDir.exists()) {
                stored
            } else {
                val repoDirs = projectDir.listFiles()
                    ?.filter { it.isDirectory && !it.name.startsWith(".") }
                    .orEmpty()
                val existingByPath = stored.repositories.associateBy { File(it.directoryPath).canonicalPath }
                val updatedRepos = stored.repositories
                    .filter { File(it.directoryPath).exists() }
                    .distinctBy { File(it.directoryPath).canonicalPath }
                    .toMutableList()
                if (updatedRepos.size != stored.repositories.size) {
                    dirty = true
                }
                for (repoDir in repoDirs) {
                    val canonical = repoDir.canonicalPath
                    if (canonical !in existingByPath) {
                        updatedRepos.add(
                            RepoEntry(
                                id = UUID.randomUUID().toString(),
                                projectId = stored.project.id,
                                name = repoDir.name,
                                directoryPath = repoDir.absolutePath,
                                createdAt = repoDir.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                            )
                        )
                        dirty = true
                    }
                }
                stored.copy(repositories = updatedRepos)
            }
        }

        if (dirty) {
            writeRecords(reconciled)
        }
        return reconciled
    }

    private fun readStoredRecords(): List<StoredProject> {
        if (!metadataFile.exists()) return emptyList()
        val raw = runCatching { metadataFile.readText(StandardCharsets.UTF_8) }.getOrNull()
            ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                // Legacy v1 flat array migration: wrap each old entry into a project container
                migrateLegacyV1Array(JSONArray(trimmed))
            } else {
                val root = JSONObject(trimmed)
                val projectsArray = root.optJSONArray("projects") ?: JSONArray()
                buildList {
                    for (i in 0 until projectsArray.length()) {
                        val pObj = projectsArray.getJSONObject(i)
                        val projectId = pObj.getString("id")
                        val project = WorkspaceProject(
                            id = projectId,
                            name = pObj.getString("name"),
                            directoryPath = pObj.getString("directoryPath"),
                            createdAt = pObj.optLong("createdAt", System.currentTimeMillis()),
                            description = pObj.optString("description", ""),
                            authorName = pObj.optString("authorName", "").ifBlank { null },
                            authorEmail = pObj.optString("authorEmail", "").ifBlank { null }
                        )
                        val reposArray = pObj.optJSONArray("repositories") ?: JSONArray()
                        val repos = buildList {
                            for (j in 0 until reposArray.length()) {
                                val rObj = reposArray.getJSONObject(j)
                                val remoteRaw = rObj.optString("remoteUrl", "")
                                add(
                                    RepoEntry(
                                        id = rObj.getString("id"),
                                        projectId = projectId,
                                        name = rObj.getString("name"),
                                        directoryPath = rObj.getString("directoryPath"),
                                        createdAt = rObj.optLong("createdAt", System.currentTimeMillis()),
                                        remoteUrl = remoteRaw.ifBlank { null }
                                    )
                                )
                            }
                        }
                        add(StoredProject(project, repos))
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun migrateLegacyV1Array(array: JSONArray): List<StoredProject> {
        val migrated = mutableListOf<StoredProject>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val oldId = obj.getString("id")
            val name = obj.getString("name")
            val oldPath = File(obj.getString("directoryPath"))
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            val remoteUrl = obj.optString("remoteUrl", "").ifBlank { null }

            val projectId = UUID.randomUUID().toString()
            val containerDir = allocateUniqueChildDir(projectsRootDir, "$name-workspace", fallback = "project")
            containerDir.mkdirs()
            val newRepoDir = File(containerDir, oldPath.name)
            if (oldPath.exists()) {
                oldPath.renameTo(newRepoDir)
            }
            val project = WorkspaceProject(
                id = projectId,
                name = name,
                directoryPath = containerDir.absolutePath,
                createdAt = createdAt
            )
            val repo = RepoEntry(
                id = oldId,
                projectId = projectId,
                name = name,
                directoryPath = newRepoDir.absolutePath,
                createdAt = createdAt,
                remoteUrl = remoteUrl
            )
            migrated.add(StoredProject(project, listOf(repo)))
        }
        writeRecords(migrated)
        return migrated
    }

    private fun writeRecords(records: List<StoredProject>) {
        val projectsArray = JSONArray()
        for (stored in records) {
            val reposArray = JSONArray()
            for (repo in stored.repositories) {
                reposArray.put(
                    JSONObject().apply {
                        put("id", repo.id)
                        put("name", repo.name)
                        put("directoryPath", repo.directoryPath)
                        put("createdAt", repo.createdAt)
                        if (!repo.remoteUrl.isNullOrBlank()) {
                            put("remoteUrl", repo.remoteUrl)
                        }
                    }
                )
            }
            projectsArray.put(
                JSONObject().apply {
                    put("id", stored.project.id)
                    put("name", stored.project.name)
                    put("directoryPath", stored.project.directoryPath)
                    put("createdAt", stored.project.createdAt)
                    if (stored.project.description.isNotBlank()) {
                        put("description", stored.project.description)
                    }
                    if (!stored.project.authorName.isNullOrBlank()) {
                        put("authorName", stored.project.authorName)
                    }
                    if (!stored.project.authorEmail.isNullOrBlank()) {
                        put("authorEmail", stored.project.authorEmail)
                    }
                    put("repositories", reposArray)
                }
            )
        }
        val root = JSONObject().apply {
            put("version", 2)
            put("projects", projectsArray)
        }
        metadataFile.writeText(root.toString(2), StandardCharsets.UTF_8)
    }
}
