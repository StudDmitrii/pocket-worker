package com.pocketworker.app.core.git

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

sealed interface FileReadResult {
    data class Text(val content: String, val sizeBytes: Long) : FileReadResult
    data class Binary(val sizeBytes: Long) : FileReadResult
    data class TooLarge(val sizeBytes: Long, val maxBytes: Long) : FileReadResult
}

class ProjectFileManager {

    fun resolveSafe(projectRoot: File, relativePath: String): File {
        val rootCanonical = projectRoot.canonicalFile
        val trimmed = relativePath.trim().trimStart('/', '\\')
        val candidate = if (trimmed.isEmpty()) {
            rootCanonical
        } else {
            File(rootCanonical, trimmed).canonicalFile
        }
        val rootPath = rootCanonical.path
        val candidatePath = candidate.path
        val isSameOrChild = candidatePath == rootPath ||
            candidatePath.startsWith(rootPath + File.separator)
        if (!isSameOrChild) {
            throw SecurityException("Путь выходит за пределы проекта: $relativePath")
        }
        return candidate
    }

    fun listDirectory(
        projectRoot: File,
        relativeDir: String = "",
        showHiddenGit: Boolean = false,
        statusByPath: Map<String, FileChangeType> = emptyMap()
    ): List<ProjectFileItem> {
        val targetDir = resolveSafe(projectRoot, relativeDir)
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return emptyList()
        }
        val rootCanonical = projectRoot.canonicalFile
        val children = targetDir.listFiles().orEmpty()

        return children
            .filter { file -> showHiddenGit || file.name != ".git" }
            .map { file ->
                val rel = file.canonicalFile.relativeTo(rootCanonical).invariantSeparatorsPath
                val status = if (file.isDirectory) {
                    resolveDirectoryStatus(rel, statusByPath)
                } else {
                    statusByPath[rel]
                }
                ProjectFileItem(
                    name = file.name,
                    relativePath = rel,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    lastModifiedMillis = file.lastModified(),
                    gitChangeType = status
                )
            }
            .sortedWith(compareBy<ProjectFileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun resolveDirectoryStatus(
        dirRelPath: String,
        statusByPath: Map<String, FileChangeType>
    ): FileChangeType? {
        val prefix = "$dirRelPath/"
        val matching = statusByPath.entries.filter { it.key.startsWith(prefix) }.map { it.value }
        if (matching.isEmpty()) return null
        return when {
            FileChangeType.CONFLICTED in matching -> FileChangeType.CONFLICTED
            FileChangeType.MODIFIED in matching -> FileChangeType.MODIFIED
            FileChangeType.ADDED in matching -> FileChangeType.ADDED
            FileChangeType.DELETED in matching -> FileChangeType.DELETED
            FileChangeType.UNTRACKED in matching -> FileChangeType.UNTRACKED
            else -> matching.first()
        }
    }

    fun readTextFile(
        projectRoot: File,
        relativePath: String,
        maxBytes: Long = DEFAULT_MAX_EDITABLE_BYTES
    ): FileReadResult {
        val file = resolveSafe(projectRoot, relativePath)
        if (!file.exists() || !file.isFile) {
            throw IOException("Файл не найден: $relativePath")
        }
        val size = file.length()
        if (size > maxBytes) {
            return FileReadResult.TooLarge(size, maxBytes)
        }
        val bytes = file.readBytes()
        val sampleLength = minOf(bytes.size, 8192)
        for (i in 0 until sampleLength) {
            if (bytes[i].toInt() == 0) {
                return FileReadResult.Binary(size)
            }
        }
        return FileReadResult.Text(String(bytes, StandardCharsets.UTF_8), size)
    }

    fun writeTextFile(projectRoot: File, relativePath: String, content: String) {
        val file = resolveSafe(projectRoot, relativePath)
        if (file.isDirectory) {
            throw IOException("Нельзя записать текст в директорию: $relativePath")
        }
        file.parentFile?.mkdirs()
        file.writeText(content, StandardCharsets.UTF_8)
    }

    fun createFile(projectRoot: File, relativePath: String, initialContent: String = "") {
        val file = resolveSafe(projectRoot, relativePath)
        if (file.exists()) {
            throw IOException("Файл или папка уже существует: $relativePath")
        }
        file.parentFile?.mkdirs()
        file.writeText(initialContent, StandardCharsets.UTF_8)
    }

    fun createDirectory(projectRoot: File, relativePath: String) {
        val dir = resolveSafe(projectRoot, relativePath)
        if (dir.exists()) {
            throw IOException("Папка или файл уже существует: $relativePath")
        }
        if (!dir.mkdirs()) {
            throw IOException("Не удалось создать папку: $relativePath")
        }
    }

    fun renameEntry(projectRoot: File, relativePath: String, newName: String): String {
        val cleanNewName = newName.trim()
        require(cleanNewName.isNotEmpty() && !cleanNewName.contains('/') && !cleanNewName.contains('\\')) {
            "Некорректное имя: $newName"
        }
        val source = resolveSafe(projectRoot, relativePath)
        if (!source.exists()) {
            throw IOException("Элемент не найден: $relativePath")
        }
        val parent = source.parentFile ?: projectRoot.canonicalFile
        val target = resolveSafe(
            projectRoot,
            parent.canonicalFile.relativeTo(projectRoot.canonicalFile).invariantSeparatorsPath
                .let { if (it.isEmpty()) cleanNewName else "$it/$cleanNewName" }
        )
        if (target.exists()) {
            throw IOException("Элемент с именем '$cleanNewName' уже существует")
        }
        if (!source.renameTo(target)) {
            throw IOException("Не удалось переименовать '$relativePath'")
        }
        return target.canonicalFile.relativeTo(projectRoot.canonicalFile).invariantSeparatorsPath
    }

    fun deleteEntry(projectRoot: File, relativePath: String) {
        val target = resolveSafe(projectRoot, relativePath)
        if (target == projectRoot.canonicalFile) {
            throw SecurityException("Нельзя удалить корень проекта через файловый менеджер")
        }
        if (!target.exists()) {
            throw IOException("Элемент не найден: $relativePath")
        }
        if (!target.deleteRecursively()) {
            throw IOException("Не удалось удалить: $relativePath")
        }
    }

    fun searchFiles(
        projectRoot: File,
        query: String,
        includeGitDir: Boolean = false,
        maxResults: Int = 200
    ): List<ProjectFileItem> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val rootCanonical = projectRoot.canonicalFile
        if (!rootCanonical.exists()) return emptyList()

        val results = mutableListOf<ProjectFileItem>()
        fun walk(dir: File) {
            if (results.size >= maxResults) return
            val children = dir.listFiles().orEmpty()
                .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            for (child in children) {
                if (results.size >= maxResults) return
                if (!includeGitDir && child.name == ".git") continue
                val rel = child.canonicalFile.relativeTo(rootCanonical).invariantSeparatorsPath
                if (child.name.lowercase().contains(needle) || rel.lowercase().contains(needle)) {
                    results.add(
                        ProjectFileItem(
                            name = child.name,
                            relativePath = rel,
                            isDirectory = child.isDirectory,
                            sizeBytes = if (child.isFile) child.length() else 0L,
                            lastModifiedMillis = child.lastModified()
                        )
                    )
                }
                if (child.isDirectory) {
                    walk(child)
                }
            }
        }
        walk(rootCanonical)
        return results
    }

    companion object {
        const val DEFAULT_MAX_EDITABLE_BYTES = 1_048_576L
    }
}
