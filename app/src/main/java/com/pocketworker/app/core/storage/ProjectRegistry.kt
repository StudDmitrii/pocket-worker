package com.pocketworker.app.core.storage

import com.pocketworker.app.core.git.ProjectEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class ProjectRegistry(
    private val metadataFile: File,
    val projectsRootDir: File
) {

    init {
        if (!projectsRootDir.exists()) {
            projectsRootDir.mkdirs()
        }
        metadataFile.parentFile?.mkdirs()
    }

    @Synchronized
    fun listProjects(): List<ProjectEntry> {
        val loaded = readEntries().toMutableList()
        val knownPaths = loaded.map { File(it.directoryPath).canonicalPath }.toSet()

        // Discover any project directories on disk that are not yet tracked in metadata
        val diskDirs = projectsRootDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        var changed = false
        for (dir in diskDirs) {
            if (dir.canonicalPath !in knownPaths) {
                loaded.add(
                    ProjectEntry(
                        id = UUID.randomUUID().toString(),
                        name = dir.name,
                        directoryPath = dir.absolutePath,
                        createdAt = dir.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                    )
                )
                changed = true
            }
        }
        if (changed) {
            writeEntries(loaded)
        }
        return loaded.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun findById(projectId: String): ProjectEntry? {
        return listProjects().firstOrNull { it.id == projectId }
    }

    @Synchronized
    fun allocateProjectDir(preferredName: String): Pair<String, File> {
        val id = UUID.randomUUID().toString()
        val baseSlug = preferredName
            .trim()
            .lowercase()
            .replace(Regex("[^a-zа-яё0-9._-]+"), "-")
            .trim('-', '.')
            .ifEmpty { "project" }
        var candidate = File(projectsRootDir, baseSlug)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(projectsRootDir, "$baseSlug-$suffix")
            suffix++
        }
        return id to candidate
    }

    @Synchronized
    fun registerProject(entry: ProjectEntry) {
        val entries = readEntries().filterNot { it.id == entry.id }.toMutableList()
        entries.add(entry)
        writeEntries(entries)
    }

    @Synchronized
    fun updateRemoteUrl(projectId: String, remoteUrl: String?) {
        val entries = readEntries().map {
            if (it.id == projectId) it.copy(remoteUrl = remoteUrl) else it
        }
        writeEntries(entries)
    }

    @Synchronized
    fun deleteProject(projectId: String, deleteFilesOnDisk: Boolean = true) {
        val entries = readEntries()
        val target = entries.firstOrNull { it.id == projectId }
        val remaining = entries.filterNot { it.id == projectId }
        writeEntries(remaining)
        if (deleteFilesOnDisk && target != null) {
            File(target.directoryPath).deleteRecursively()
        }
    }

    private fun readEntries(): List<ProjectEntry> {
        if (!metadataFile.exists()) return emptyList()
        val raw = runCatching { metadataFile.readText(StandardCharsets.UTF_8) }.getOrNull()
            ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val remoteRaw = obj.optString("remoteUrl", "")
                    add(
                        ProjectEntry(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            directoryPath = obj.getString("directoryPath"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            remoteUrl = remoteRaw.ifBlank { null }
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun writeEntries(entries: List<ProjectEntry>) {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("name", entry.name)
                put("directoryPath", entry.directoryPath)
                put("createdAt", entry.createdAt)
                if (!entry.remoteUrl.isNullOrBlank()) {
                    put("remoteUrl", entry.remoteUrl)
                }
            }
            array.put(obj)
        }
        metadataFile.writeText(array.toString(2), StandardCharsets.UTF_8)
    }
}
