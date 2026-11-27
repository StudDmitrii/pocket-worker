package com.pocketworker.app.core.mcp

import com.pocketworker.app.core.git.FileChangeType
import com.pocketworker.app.core.git.FileReadResult
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.ProjectFileManager
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.storage.ProjectRegistry
import com.pocketworker.app.ui.workspace.CodeLanguage
import com.pocketworker.app.ui.workspace.CodeSyntaxHighlighter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

private val DEFAULT_IGNORED_DIRS = setOf(
    ".git", ".gradle", ".idea", "build", "node_modules", "dist", "target", "__pycache__", ".venv", "venv"
)

internal fun isProtectedGitPath(relativePath: String): Boolean {
    val norm = relativePath.trim().replace('\\', '/').trimStart('/')
    return norm == ".git" || norm.startsWith(".git/") || norm.split('/').any { it == ".git" }
}

internal fun resolveWritableSafe(
    fileManager: ProjectFileManager,
    repoDir: File,
    relativePath: String
): Pair<File?, McpToolResult?> {
    if (isProtectedGitPath(relativePath)) {
        return null to McpToolResult.error(
            errorCode = McpErrorCodes.PROTECTED_GIT_PATH,
            message = "Прямое изменение служебной папки .git запрещено: '$relativePath'.",
            recoverableByAgent = true,
            reason = "Метаданные .git изменяются только через инструмент git_exec.",
            suggestedAlternatives = listOf("Используй git_exec для любых операций с Git-индексом, ветками и конфигом")
        )
    }
    return runCatching { fileManager.resolveSafe(repoDir, relativePath) to null }
        .getOrElse { ex ->
            null to McpToolResult.error(
                errorCode = McpErrorCodes.PATH_OUT_OF_SANDBOX,
                message = ex.message ?: "Путь выходит за пределы репозитория: $relativePath",
                recoverableByAgent = false,
                reason = "Доступ ограничен песочницей текущего репозитория."
            )
        }
}

internal fun globToRegex(glob: String): Regex {
    val cleaned = glob.trim().replace('\\', '/')
    val sb = StringBuilder("^")
    var i = 0
    while (i < cleaned.length) {
        val c = cleaned[i]
        when {
            c == '*' && i + 1 < cleaned.length && cleaned[i + 1] == '*' -> {
                if (i + 2 < cleaned.length && cleaned[i + 2] == '/') {
                    sb.append("(?:.*/)?")
                    i += 3
                } else {
                    sb.append(".*")
                    i += 2
                }
            }
            c == '*' -> {
                sb.append("[^/]*")
                i++
            }
            c == '?' -> {
                sb.append("[^/]")
                i++
            }
            c == '{' -> {
                val close = cleaned.indexOf('}', i + 1)
                if (close > i) {
                    val options = cleaned.substring(i + 1, close)
                        .split(',')
                        .joinToString("|") { Regex.escape(it.trim()) }
                    sb.append("(?:").append(options).append(")")
                    i = close + 1
                } else {
                    sb.append("\\{")
                    i++
                }
            }
            c in ".()[]+^$|\\" -> {
                sb.append('\\').append(c)
                i++
            }
            else -> {
                sb.append(c)
                i++
            }
        }
    }
    sb.append('$')
    return Regex(sb.toString(), RegexOption.IGNORE_CASE)
}

internal fun matchesFileFilter(relativePath: String, pattern: String): Boolean {
    val normPath = relativePath.replace('\\', '/')
    val fileName = normPath.substringAfterLast('/')
    val hasWildcard = pattern.any { it == '*' || it == '?' || it == '{' }
    if (!hasWildcard) {
        return normPath.contains(pattern, ignoreCase = true) ||
            fileName.contains(pattern, ignoreCase = true)
    }
    val regex = globToRegex(pattern)
    return regex.matches(normPath) || (!pattern.contains('/') && regex.matches(fileName))
}

internal fun buildCompactUnifiedDiff(
    relativePath: String,
    oldText: String,
    newText: String,
    maxDiffLines: Int = 120
): String {
    if (oldText == newText) return ""
    val oldLines = if (oldText.isEmpty()) {
        emptyList()
    } else {
        oldText.removeSuffix("\r\n").removeSuffix("\n").split('\n').map { it.removeSuffix("\r") }
    }
    val newLines = if (newText.isEmpty()) {
        emptyList()
    } else {
        newText.removeSuffix("\r\n").removeSuffix("\n").split('\n').map { it.removeSuffix("\r") }
    }

    var prefix = 0
    val minLen = minOf(oldLines.size, newLines.size)
    while (prefix < minLen && oldLines[prefix] == newLines[prefix]) prefix++

    var suffix = 0
    while (suffix < minLen - prefix &&
        oldLines[oldLines.size - 1 - suffix] == newLines[newLines.size - 1 - suffix]
    ) {
        suffix++
    }

    val oldEnd = oldLines.size - suffix
    val newEnd = newLines.size - suffix
    val ctxStart = (prefix - 2).coerceAtLeast(0)
    val oldCtxEnd = (oldEnd + 2).coerceAtMost(oldLines.size)
    val newCtxEnd = (newEnd + 2).coerceAtMost(newLines.size)

    val out = mutableListOf<String>()
    out.add("--- a/$relativePath")
    out.add("+++ b/$relativePath")
    out.add("@@ -${ctxStart + 1},${oldCtxEnd - ctxStart} +${ctxStart + 1},${newCtxEnd - ctxStart} @@")
    for (i in ctxStart until prefix) out.add(" ${oldLines[i]}")
    for (i in prefix until oldEnd) out.add("-${oldLines[i]}")
    for (i in prefix until newEnd) out.add("+${newLines[i]}")
    for (i in oldEnd until oldCtxEnd) out.add(" ${oldLines[i]}")

    return if (out.size > maxDiffLines) {
        out.take(maxDiffLines).joinToString("\n") + "\n... (diff сокращён: всего ${out.size} строк)"
    } else {
        out.joinToString("\n")
    }
}

internal fun createFileSystemTools(
    registry: ProjectRegistry,
    fileManager: ProjectFileManager,
    gitEngine: GitEngine,
    resolver: McpWorkspaceResolver
): List<McpTool> = listOf(
    FsGetRepoOverviewTool(fileManager, gitEngine, resolver),
    FsListDirTool(fileManager, gitEngine, resolver),
    FsFindFilesTool(registry, resolver),
    FsGrepTool(registry, fileManager, resolver),
    FsReadFileTool(fileManager, resolver),
    FsEditFileTool(fileManager, resolver),
    FsWriteFileTool(fileManager, resolver),
    FsReplaceAcrossFilesTool(fileManager, resolver),
    FsManageStructureTool(fileManager, resolver),
    FsDeleteEntryTool(fileManager, resolver)
)

// region Read tools

private class FsGetRepoOverviewTool(
    private val fileManager: ProjectFileManager,
    private val gitEngine: GitEngine,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "fs_get_repo_overview",
        description = "Стартовая карта репозитория за 1 вызов: дерево верхних уровней, список ключевых конфигов (README, Gradle, package.json и т.д.), статус Git и 5 последних коммитов.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("treeDepth", McpSchemaBuilder.intProp("Глубина дерева (1..3, по умолчанию 2)"))
            }
        ),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!

        val depth = arguments.optInt("treeDepth", 2).coerceIn(1, 3)
        val status = runCatching { gitEngine.getStatus(resolved.dir) }.getOrNull()
        val statusMap = buildStatusMap(status)
        val tree = collectTreeEntries(fileManager, resolved.dir, "", depth, false, statusMap, 120)

        val keyConfigNames = setOf(
            "readme.md", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "package.json", "tsconfig.json", "pyproject.toml", "requirements.txt", "cargo.toml",
            "go.mod", "pom.xml", "dockerfile", "docker-compose.yml", "docker-compose.yaml", ".gitignore"
        )
        val keyFiles = resolved.dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.lowercase() in keyConfigNames }
            .map { it.name }
            .sorted()

        val recentCommits = if (status?.isGitRepo == true && !status.isUnborn) {
            runCatching { gitEngine.getCommitHistory(resolved.dir, null, limit = 5, skip = 0) }
                .getOrDefault(emptyList())
        } else emptyList()

        return McpToolResult.success {
            put("projectName", resolved.project.name)
            put("repoName", resolved.repo.name)
            put("keyFiles", JSONArray(keyFiles))
            put("tree", tree)
            put("git", JSONObject().apply {
                put("isGitRepo", status?.isGitRepo == true)
                put("currentBranch", status?.currentBranch ?: JSONObject.NULL)
                put("isDetachedHead", status?.isDetachedHead == true)
                put("isUnborn", status?.isUnborn == true)
                put("stagedCount", status?.stagedChanges?.size ?: 0)
                put("unstagedCount", status?.unstagedChanges?.size ?: 0)
                put("aheadCount", status?.aheadCount ?: 0)
                put("behindCount", status?.behindCount ?: 0)
            })
            put("recentCommits", JSONArray().apply {
                for (c in recentCommits) {
                    put(JSONObject().apply {
                        put("shortId", c.shortId)
                        put("message", c.shortMessage)
                        put("author", c.authorName)
                    })
                }
            })
        }
    }
}

private class FsListDirTool(
    private val fileManager: ProjectFileManager,
    private val gitEngine: GitEngine,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "fs_list_dir",
        description = "Показывает содержимое директории или дерево папок/файлов до указанной глубины (1..4) с размерами и Git-статусами.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("relativeDir", McpSchemaBuilder.stringProp("Относительный путь папки (пусто = корень репозитория)"))
                put("depth", McpSchemaBuilder.intProp("Глубина обхода 1..4 (по умолчанию 1)"))
                put("includeHidden", McpSchemaBuilder.boolProp("Показывать скрытые файлы и .git (по умолчанию false)"))
            }
        ),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!

        val relDir = arguments.optString("relativeDir", "").trim()
        val depth = arguments.optInt("depth", 1).coerceIn(1, 4)
        val includeHidden = arguments.optBoolean("includeHidden", false)

        val targetDir = runCatching { fileManager.resolveSafe(resolved.dir, relDir) }.getOrElse { ex ->
            return McpToolResult.error(
                errorCode = McpErrorCodes.PATH_OUT_OF_SANDBOX,
                message = ex.message ?: "Путь выходит за пределы репозитория",
                recoverableByAgent = false
            )
        }
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.FILE_NOT_FOUND,
                message = "Папка '$relDir' не найдена.",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Вызови fs_list_dir с пустым relativeDir, чтобы увидеть корень репозитория")
            )
        }

        val status = runCatching { gitEngine.getStatus(resolved.dir) }.getOrNull()
        val statusMap = buildStatusMap(status)
        val entries = collectTreeEntries(fileManager, resolved.dir, relDir, depth, includeHidden, statusMap, 250)

        return McpToolResult.success {
            put("relativeDir", relDir)
            put("depth", depth)
            put("count", entries.length())
            put("entries", entries)
        }
    }
}

private class FsFindFilesTool(
    private val registry: ProjectRegistry,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "fs_find_files",
        description = "Ищет файлы по имени или glob-маске (например '**/*.kt', '*Auth*') внутри указанного репозитория или по всем репозиториям проекта.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "pattern"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория (если не указан — поиск по всем репозиториям проекта)"))
                put("pattern", McpSchemaBuilder.stringProp("Подстрока имени/пути или glob-маска (например '**/*.kt')"))
                put("maxResults", McpSchemaBuilder.intProp("Максимум результатов (1..200, по умолчанию 80)"))
            }
        ),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.optNonBlankString("repoId")
        val pattern = arguments.requireNonBlankString("pattern")
        val maxResults = arguments.optInt("maxResults", 80).coerceIn(1, 200)

        val reposToScan = resolveReposForSearch(registry, resolver, projectId, repoId)
            .let { (repos, err) -> if (err != null) return err else repos }

        val matches = JSONArray()
        var truncated = false
        for (repo in reposToScan) {
            val root = File(repo.directoryPath).canonicalFile
            if (!root.exists()) continue
            walkProjectFiles(root, skipIgnoredDirs = true) { file, relPath ->
                if (matches.length() >= maxResults) {
                    truncated = true
                    return@walkProjectFiles false
                }
                if (matchesFileFilter(relPath, pattern)) {
                    matches.put(
                        JSONObject().apply {
                            put("repoId", repo.id)
                            put("repoName", repo.name)
                            put("path", relPath)
                            put("sizeBytes", file.length())
                        }
                    )
                }
                true
            }
            if (truncated) break
        }

        return McpToolResult.success {
            put("pattern", pattern)
            put("count", matches.length())
            put("truncated", truncated)
            put("matches", matches)
        }
    }
}

private class FsGrepTool(
    private val registry: ProjectRegistry,
    private val fileManager: ProjectFileManager,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "fs_grep",
        description = "Ищет текст или регулярное выражение внутри файлов репозитория или всего проекта. Возвращает файл, номер строки и фрагмент контекста.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "query"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория (необязательно; без него поиск идёт по всему проекту)"))
                put("query", McpSchemaBuilder.stringProp("Искомая строка или регулярное выражение"))
                put("isRegex", McpSchemaBuilder.boolProp("Трактовать query как RegExp (по умолчанию false)"))
                put("caseSensitive", McpSchemaBuilder.boolProp("Учитывать регистр (по умолчанию false)"))
                put("fileGlob", McpSchemaBuilder.stringProp("Фильтр по файлам, например '*.kt' или 'src/**/*.ts'"))
                put("contextLines", McpSchemaBuilder.intProp("Строк контекста вокруг совпадения (0..3, по умолчанию 0)"))
                put("maxMatches", McpSchemaBuilder.intProp("Лимит совпадений (1..150, по умолчанию 50)"))
            }
        ),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.optNonBlankString("repoId")
        val query = arguments.requireNonBlankString("query")
        val isRegex = arguments.optBoolean("isRegex", false)
        val caseSensitive = arguments.optBoolean("caseSensitive", false)
        val fileGlob = arguments.optNonBlankString("fileGlob")
        val contextLines = arguments.optInt("contextLines", 0).coerceIn(0, 3)
        val maxMatches = arguments.optInt("maxMatches", 50).coerceIn(1, 150)

        val matcherRegex = if (isRegex) {
            runCatching {
                val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(query, opts)
            }.getOrElse { ex ->
                return McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Некорректное регулярное выражение '$query': ${ex.message}",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf("Исправь синтаксис RegExp или передай isRegex=false")
                )
            }
        } else null

        val reposToScan = resolveReposForSearch(registry, resolver, projectId, repoId)
            .let { (repos, err) -> if (err != null) return err else repos }

        val matches = JSONArray()
        var truncated = false
        var scannedFiles = 0

        for (repo in reposToScan) {
            val root = File(repo.directoryPath).canonicalFile
            if (!root.exists()) continue
            walkProjectFiles(root, skipIgnoredDirs = true) { file, relPath ->
                if (matches.length() >= maxMatches) {
                    truncated = true
                    return@walkProjectFiles false
                }
                if (fileGlob != null && !matchesFileFilter(relPath, fileGlob)) {
                    return@walkProjectFiles true
                }
                if (file.length() > MAX_GREP_FILE_BYTES) return@walkProjectFiles true
                val read = runCatching { fileManager.readTextFile(root, relPath, MAX_GREP_FILE_BYTES) }.getOrNull()
                if (read !is FileReadResult.Text) return@walkProjectFiles true
                scannedFiles++

                val lines = read.content.split('\n').map { it.removeSuffix("\r") }
                for (idx in lines.indices) {
                    val line = lines[idx]
                    val hit = if (matcherRegex != null) {
                        matcherRegex.containsMatchIn(line)
                    } else {
                        line.contains(query, ignoreCase = !caseSensitive)
                    }
                    if (hit) {
                        val obj = JSONObject().apply {
                            put("repoId", repo.id)
                            put("repoName", repo.name)
                            put("path", relPath)
                            put("lineNumber", idx + 1)
                            put("line", line.take(300))
                            if (contextLines > 0) {
                                val from = (idx - contextLines).coerceAtLeast(0)
                                val to = (idx + contextLines).coerceAtMost(lines.lastIndex)
                                val ctx = JSONArray()
                                for (c in from..to) {
                                    ctx.put("${c + 1}: ${lines[c].take(240)}")
                                }
                                put("context", ctx)
                            }
                        }
                        matches.put(obj)
                        if (matches.length() >= maxMatches) {
                            truncated = true
                            return@walkProjectFiles false
                        }
                    }
                }
                true
            }
            if (truncated) break
        }

        return McpToolResult.success {
            put("query", query)
            put("scannedFiles", scannedFiles)
            put("matchCount", matches.length())
            put("truncated", truncated)
            put("matches", matches)
        }
    }

    companion object {
        private const val MAX_GREP_FILE_BYTES = 512 * 1024L
    }
}

private class FsReadFileTool(
    private val fileManager: ProjectFileManager,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "fs_read_file",
        description = "Читает текстовый файл по диапазону строк (с нумерацией, до 300 строк за вызов) или возвращает его краткую структуру (outlineOnly=true: классы, функции, заголовки, ключи с номерами строк).",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId", "relativePath"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("relativePath", McpSchemaBuilder.stringProp("Относительный путь к файлу"))
                put("startLine", McpSchemaBuilder.intProp("Начальная строка (1-based, по умолчанию 1)"))
                put("endLine", McpSchemaBuilder.intProp("Конечная строка включительно (макс. 300 строк за вызов)"))
                put("outlineOnly", McpSchemaBuilder.boolProp("Вернуть только структуру файла с номерами строк вместо текста"))
            }
        ),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val relativePath = arguments.requireNonBlankString("relativePath")
        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!

        val readResult = runCatching { fileManager.readTextFile(resolved.dir, relativePath) }.getOrElse { ex ->
            val code = if (ex is SecurityException) McpErrorCodes.PATH_OUT_OF_SANDBOX else McpErrorCodes.FILE_NOT_FOUND
            return McpToolResult.error(
                errorCode = code,
                message = ex.message ?: "Не удалось прочитать файл '$relativePath'",
                recoverableByAgent = code == McpErrorCodes.FILE_NOT_FOUND,
                suggestedAlternatives = if (code == McpErrorCodes.FILE_NOT_FOUND) {
                    listOf("Проверь путь через fs_find_files или fs_list_dir")
                } else emptyList()
            )
        }

        return when (readResult) {
            is FileReadResult.Binary -> McpToolResult.error(
                errorCode = McpErrorCodes.BINARY_FILE,
                message = "Файл '$relativePath' бинарный (${readResult.sizeBytes} Б) и не может быть прочитан как текст.",
                recoverableByAgent = false,
                userMessageHint = AppLocale.current.mcpHintBinaryNotEditable
            )
            is FileReadResult.TooLarge -> McpToolResult.error(
                errorCode = McpErrorCodes.FILE_TOO_LARGE,
                message = "Файл '$relativePath' слишком большой (${readResult.sizeBytes} Б, лимит ${readResult.maxBytes} Б).",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Используй fs_grep с fileGlob для точечного поиска нужных строк")
            )
            is FileReadResult.Text -> {
                val language = CodeSyntaxHighlighter.detectLanguage(relativePath)
                val allLines = readResult.content.split('\n').map { it.removeSuffix("\r") }
                val totalLines = allLines.size
                val outlineOnly = arguments.optBoolean("outlineOnly", false)

                if (outlineOnly) {
                    val outline = extractFileOutline(allLines, language)
                    return McpToolResult.success {
                        put("path", relativePath)
                        put("language", language.displayName)
                        put("sizeBytes", readResult.sizeBytes)
                        put("totalLines", totalLines)
                        put("outline", outline)
                    }
                }

                val startLine = arguments.optInt("startLine", 1).coerceIn(1, totalLines.coerceAtLeast(1))
                val defaultEnd = (startLine + MAX_LINES_PER_READ - 1).coerceAtMost(totalLines)
                val requestedEnd = if (arguments.has("endLine")) {
                    arguments.optInt("endLine", defaultEnd)
                } else defaultEnd
                val endLine = requestedEnd.coerceIn(startLine, minOf(totalLines, startLine + MAX_LINES_PER_READ - 1))

                val numberedContent = buildString {
                    for (lineNum in startLine..endLine) {
                        if (lineNum > startLine) append('\n')
                        append(lineNum).append(": ").append(allLines[lineNum - 1])
                    }
                }

                McpToolResult.success {
                    put("path", relativePath)
                    put("language", language.displayName)
                    put("sizeBytes", readResult.sizeBytes)
                    put("totalLines", totalLines)
                    put("startLine", startLine)
                    put("endLine", endLine)
                    put("hasMore", endLine < totalLines)
                    put("content", numberedContent)
                }
            }
        }
    }

    companion object {
        private const val MAX_LINES_PER_READ = 300
    }
}

// endregion

// region Write & structure tools

private class FsEditFileTool(
    private val fileManager: ProjectFileManager,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "fs_edit_file",
        description = "Точечное редактирование текстового файла: замена точного фрагмента (oldText -> newText) либо замена диапазона строк (startLine..endLine). Поддерживает несколько правок за один вызов и dryRun-превью diff.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId", "relativePath", "edits"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("relativePath", McpSchemaBuilder.stringProp("Относительный путь файла"))
                put("dryRun", McpSchemaBuilder.boolProp("Если true — не записывать на диск, вернуть только diff"))
                put(
                    "edits",
                    McpSchemaBuilder.arrayProp(
                        description = "Список правок, применяемых последовательно",
                        items = McpSchemaBuilder.objectSchema(
                            required = listOf("newText"),
                            properties = {
                                put("oldText", McpSchemaBuilder.stringProp("Точный заменяемый фрагмент (альтернатива startLine/endLine)"))
                                put("newText", McpSchemaBuilder.stringProp("Новый текст"))
                                put("replaceAll", McpSchemaBuilder.boolProp("Заменить все вхождения oldText (по умолчанию false — требуется уникальность)"))
                                put("startLine", McpSchemaBuilder.intProp("Начальная строка 1-based для строчной замены"))
                                put("endLine", McpSchemaBuilder.intProp("Конечная строка 1-based включительно (если endLine < startLine — вставка перед startLine)"))
                            }
                        )
                    )
                )
            }
        ),
        defaultTier = McpPermissionTier.CONTROLLED_WRITE
    )

    override fun classifyTier(arguments: JSONObject): McpPermissionTier =
        if (arguments.optBoolean("dryRun", false)) McpPermissionTier.AUTO else McpPermissionTier.CONTROLLED_WRITE

    override fun buildConfirmationSummary(arguments: JSONObject): String =
        AppLocale.current.mcpConfirmEditFile(arguments.optString("relativePath"))

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val relativePath = arguments.requireNonBlankString("relativePath")
        val dryRun = arguments.optBoolean("dryRun", false)
        val editsArray = arguments.optJSONArray("edits")
        if (editsArray == null || editsArray.length() == 0) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Массив 'edits' должен содержать хотя бы одну правку.",
                recoverableByAgent = true
            )
        }

        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!
        val (_, writeErr) = resolveWritableSafe(fileManager, resolved.dir, relativePath)
        if (writeErr != null) return writeErr

        val readResult = runCatching { fileManager.readTextFile(resolved.dir, relativePath) }.getOrElse { ex ->
            return McpToolResult.error(
                errorCode = McpErrorCodes.FILE_NOT_FOUND,
                message = ex.message ?: "Файл не найден: $relativePath",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Проверь путь через fs_find_files или создай файл через fs_write_file")
            )
        }
        if (readResult !is FileReadResult.Text) {
            return McpToolResult.error(
                errorCode = if (readResult is FileReadResult.Binary) McpErrorCodes.BINARY_FILE else McpErrorCodes.FILE_TOO_LARGE,
                message = "Файл '$relativePath' не может быть отредактирован как текст.",
                recoverableByAgent = false
            )
        }

        val originalContent = readResult.content
        val lineSeparator = if (originalContent.contains("\r\n")) "\r\n" else "\n"
        var working = originalContent.replace("\r\n", "\n")

        for (idx in 0 until editsArray.length()) {
            val edit = editsArray.optJSONObject(idx) ?: continue
            val newText = edit.optString("newText", "").replace("\r\n", "\n")
            val hasOldText = edit.has("oldText") && !edit.isNull("oldText")
            val hasStartLine = edit.has("startLine") && !edit.isNull("startLine")

            if (hasOldText) {
                val oldText = edit.optString("oldText", "").replace("\r\n", "\n")
                if (oldText.isEmpty()) {
                    return McpToolResult.error(
                        errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                        message = "Правка #$idx: параметр 'oldText' не может быть пустым.",
                        recoverableByAgent = true
                    )
                }
                val replaceAll = edit.optBoolean("replaceAll", false)
                val firstIdx = working.indexOf(oldText)
                if (firstIdx == -1) {
                    return McpToolResult.error(
                        errorCode = McpErrorCodes.PATCH_TARGET_NOT_FOUND,
                        message = "Правка #$idx: фрагмент oldText не найден в файле '$relativePath'.",
                        recoverableByAgent = true,
                        suggestedAlternatives = listOf("Перечитай нужные строки через fs_read_file и повтори fs_edit_file с точным текстом")
                    )
                }
                val secondIdx = working.indexOf(oldText, firstIdx + oldText.length)
                if (!replaceAll && secondIdx != -1) {
                    return McpToolResult.error(
                        errorCode = McpErrorCodes.PATCH_TARGET_AMBIGUOUS,
                        message = "Правка #$idx: фрагмент oldText встречается в '$relativePath' несколько раз.",
                        recoverableByAgent = true,
                        suggestedAlternatives = listOf(
                            "Добавь больше окружающих строк в oldText, чтобы совпадение стало уникальным",
                            "Либо передай replaceAll=true или используй startLine/endLine"
                        )
                    )
                }
                working = if (replaceAll) {
                    working.replace(oldText, newText)
                } else {
                    working.substring(0, firstIdx) + newText + working.substring(firstIdx + oldText.length)
                }
            } else if (hasStartLine) {
                val lines = working.split('\n').toMutableList()
                val startLine = edit.optInt("startLine")
                val endLine = edit.optInt("endLine", startLine)
                if (startLine < 1 || startLine > lines.size + 1) {
                    return McpToolResult.error(
                        errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                        message = "Правка #$idx: startLine=$startLine вне диапазона 1..${lines.size + 1}.",
                        recoverableByAgent = true
                    )
                }
                val insertLines = if (newText.isEmpty()) emptyList() else newText.split('\n')
                if (endLine < startLine) {
                    lines.addAll(startLine - 1, insertLines)
                } else {
                    val clampedEnd = endLine.coerceAtMost(lines.size)
                    val prefix = lines.subList(0, startLine - 1).toList()
                    val suffix = lines.subList(clampedEnd, lines.size).toList()
                    val rebuilt = ArrayList<String>(prefix.size + insertLines.size + suffix.size)
                    rebuilt.addAll(prefix)
                    rebuilt.addAll(insertLines)
                    rebuilt.addAll(suffix)
                    working = rebuilt.joinToString("\n")
                    continue
                }
                working = lines.joinToString("\n")
            } else {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Правка #$idx: нужно указать либо 'oldText', либо 'startLine'.",
                    recoverableByAgent = true
                )
            }
        }

        val finalContent = if (lineSeparator == "\r\n") working.replace("\n", "\r\n") else working
        val diff = buildCompactUnifiedDiff(relativePath, originalContent, finalContent)

        if (!dryRun && finalContent != originalContent) {
            fileManager.writeTextFile(resolved.dir, relativePath, finalContent)
        }

        return McpToolResult.success {
            put("path", relativePath)
            put("dryRun", dryRun)
            put("changed", finalContent != originalContent)
            put("editsApplied", editsArray.length())
            put("diff", diff)
        }
    }
}

private class FsWriteFileTool(
    private val fileManager: ProjectFileManager,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "fs_write_file",
        description = "Создаёт новый файл (с автоматическим созданием родительских папок) или полностью перезаписывает существующий файл.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId", "relativePath", "content"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("relativePath", McpSchemaBuilder.stringProp("Относительный путь файла"))
                put("content", McpSchemaBuilder.stringProp("Полное текстовое содержимое файла"))
                put("overwrite", McpSchemaBuilder.boolProp("Разрешить перезапись существующего файла (по умолчанию false)"))
            }
        ),
        defaultTier = McpPermissionTier.CONTROLLED_WRITE
    )

    override fun buildConfirmationSummary(arguments: JSONObject): String =
        AppLocale.current.mcpConfirmWriteFile(arguments.optString("relativePath"))

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val relativePath = arguments.requireNonBlankString("relativePath")
        val content = arguments.optString("content", "")
        val overwrite = arguments.optBoolean("overwrite", false)

        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!
        val (targetFile, writeErr) = resolveWritableSafe(fileManager, resolved.dir, relativePath)
        if (writeErr != null || targetFile == null) return writeErr!!

        val existed = targetFile.exists()
        if (existed && !overwrite) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.ALREADY_EXISTS,
                message = "Файл '$relativePath' уже существует (overwrite=false).",
                recoverableByAgent = true,
                suggestedAlternatives = listOf(
                    "Используй fs_edit_file для точечного изменения существующего файла",
                    "Либо передай overwrite=true, если действительно нужна полная перезапись"
                )
            )
        }

        val previous = if (existed && targetFile.isFile) {
            runCatching { targetFile.readText(StandardCharsets.UTF_8) }.getOrNull()
        } else null

        fileManager.writeTextFile(resolved.dir, relativePath, content)

        return McpToolResult.success {
            put("path", relativePath)
            put("created", !existed)
            put("lineCount", content.count { it == '\n' } + 1)
            put("sizeBytes", targetFile.length())
            if (previous != null) {
                put("diff", buildCompactUnifiedDiff(relativePath, previous, content))
            }
        }
    }
}

private class FsReplaceAcrossFilesTool(
    private val fileManager: ProjectFileManager,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "fs_replace_across_files",
        description = "Массовый текстовый/RegExp поиск и замена по файлам репозитория под управлением ИИ (например, обновление импортов или переименование символа). Рекомендуется сначала запускать с dryRun=true.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId", "query", "replacement"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("query", McpSchemaBuilder.stringProp("Искомый текст или RegExp"))
                put("replacement", McpSchemaBuilder.stringProp("Текст замены"))
                put("isRegex", McpSchemaBuilder.boolProp("Использовать RegExp (по умолчанию false)"))
                put("caseSensitive", McpSchemaBuilder.boolProp("Учитывать регистр (по умолчанию true)"))
                put("fileGlob", McpSchemaBuilder.stringProp("Фильтр файлов, например '**/*.kt'"))
                put("dryRun", McpSchemaBuilder.boolProp("Если true — только превью затронутых файлов без записи (по умолчанию false)"))
                put("maxFiles", McpSchemaBuilder.intProp("Максимум изменяемых файлов за вызов (1..100, по умолчанию 40)"))
            }
        ),
        defaultTier = McpPermissionTier.CONTROLLED_WRITE
    )

    override fun classifyTier(arguments: JSONObject): McpPermissionTier =
        if (arguments.optBoolean("dryRun", false)) McpPermissionTier.AUTO else McpPermissionTier.CONTROLLED_WRITE

    override fun buildConfirmationSummary(arguments: JSONObject): String =
        AppLocale.current.mcpConfirmReplaceAll(arguments.optString("query"), arguments.optString("replacement"))

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val query = arguments.requireNonBlankString("query")
        val replacement = arguments.optString("replacement", "")
        val isRegex = arguments.optBoolean("isRegex", false)
        val caseSensitive = arguments.optBoolean("caseSensitive", true)
        val fileGlob = arguments.optNonBlankString("fileGlob")
        val dryRun = arguments.optBoolean("dryRun", false)
        val maxFiles = arguments.optInt("maxFiles", 40).coerceIn(1, 100)

        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!

        val regex = if (isRegex) {
            runCatching {
                val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(query, opts)
            }.getOrElse { ex ->
                return McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Некорректный RegExp '$query': ${ex.message}",
                    recoverableByAgent = true
                )
            }
        } else {
            val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(Regex.escape(query), opts)
        }

        val root = resolved.dir.canonicalFile
        val affected = JSONArray()
        var totalReplacements = 0
        var truncated = false

        walkProjectFiles(root, skipIgnoredDirs = true) { file, relPath ->
            if (affected.length() >= maxFiles) {
                truncated = true
                return@walkProjectFiles false
            }
            if (fileGlob != null && !matchesFileFilter(relPath, fileGlob)) return@walkProjectFiles true
            if (file.length() > MAX_REPLACE_FILE_BYTES) return@walkProjectFiles true

            val read = runCatching { fileManager.readTextFile(root, relPath, MAX_REPLACE_FILE_BYTES) }.getOrNull()
            if (read !is FileReadResult.Text) return@walkProjectFiles true

            val count = regex.findAll(read.content).count()
            if (count == 0) return@walkProjectFiles true

            val updated = if (isRegex) {
                regex.replace(read.content, replacement)
            } else {
                regex.replace(read.content) { replacement }
            }
            if (!dryRun && updated != read.content) {
                fileManager.writeTextFile(root, relPath, updated)
            }
            totalReplacements += count
            affected.put(
                JSONObject().apply {
                    put("path", relPath)
                    put("replacements", count)
                    put("diff", buildCompactUnifiedDiff(relPath, read.content, updated, maxDiffLines = 30))
                }
            )
            true
        }

        return McpToolResult.success {
            put("dryRun", dryRun)
            put("filesAffected", affected.length())
            put("totalReplacements", totalReplacements)
            put("truncated", truncated)
            put("files", affected)
        }
    }

    companion object {
        private const val MAX_REPLACE_FILE_BYTES = 512 * 1024L
    }
}

private class FsManageStructureTool(
    private val fileManager: ProjectFileManager,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "fs_manage_structure",
        description = "Управление структурой папок и файлов (одиночно или пакетом за 1 вызов): mkdir (создать папку), move (переименовать или перенести файл/папку), copy (скопировать файл/папку).",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId", "operations"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("dryRun", McpSchemaBuilder.boolProp("Проверить операции без изменения файлов (по умолчанию false)"))
                put(
                    "operations",
                    McpSchemaBuilder.arrayProp(
                        description = "Список структурных операций",
                        items = McpSchemaBuilder.objectSchema(
                            required = listOf("type", "path"),
                            properties = {
                                put("type", McpSchemaBuilder.stringProp("Тип операции", listOf("mkdir", "move", "copy")))
                                put("path", McpSchemaBuilder.stringProp("Исходный путь (или путь новой папки для mkdir)"))
                                put("toPath", McpSchemaBuilder.stringProp("Целевой путь (для move и copy)"))
                                put("targetRepoId", McpSchemaBuilder.stringProp("Целевой репозиторий в том же проекте (необязательно, для move/copy)"))
                                put("overwrite", McpSchemaBuilder.boolProp("Перезаписать целевой путь, если существует (по умолчанию false)"))
                            }
                        )
                    )
                )
            }
        ),
        defaultTier = McpPermissionTier.CONTROLLED_WRITE
    )

    override fun classifyTier(arguments: JSONObject): McpPermissionTier =
        if (arguments.optBoolean("dryRun", false)) McpPermissionTier.AUTO else McpPermissionTier.CONTROLLED_WRITE

    override fun buildConfirmationSummary(arguments: JSONObject): String {
        val count = arguments.optJSONArray("operations")?.length() ?: 0
        return AppLocale.current.mcpConfirmFsBatch(count)
    }

    private data class PlannedOp(
        val type: String,
        val sourcePath: String,
        val sourceFile: File,
        val destPath: String?,
        val destFile: File?,
        val overwrite: Boolean
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val dryRun = arguments.optBoolean("dryRun", false)
        val opsArray = arguments.optJSONArray("operations")
        if (opsArray == null || opsArray.length() == 0) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Массив 'operations' не может быть пустым.",
                recoverableByAgent = true
            )
        }

        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!

        val planned = mutableListOf<PlannedOp>()
        for (i in 0 until opsArray.length()) {
            val op = opsArray.optJSONObject(i) ?: continue
            val type = op.requireNonBlankString("type").lowercase()
            val path = op.requireNonBlankString("path")
            val overwrite = op.optBoolean("overwrite", false)

            val (srcFile, srcErr) = resolveWritableSafe(fileManager, resolved.dir, path)
            if (srcErr != null || srcFile == null) return srcErr!!

            when (type) {
                "mkdir" -> {
                    planned.add(PlannedOp("mkdir", path, srcFile, null, null, overwrite))
                }
                "move", "copy" -> {
                    val toPath = op.requireNonBlankString("toPath")
                    val targetRepoId = op.optNonBlankString("targetRepoId") ?: repoId
                    val (targetRepo, targetErr) = resolver.resolveRepo(projectId, targetRepoId)
                    if (targetErr != null || targetRepo == null) return targetErr!!

                    val (dstFile, dstErr) = resolveWritableSafe(fileManager, targetRepo.dir, toPath)
                    if (dstErr != null || dstFile == null) return dstErr!!

                    if (!srcFile.exists()) {
                        return McpToolResult.error(
                            errorCode = McpErrorCodes.FILE_NOT_FOUND,
                            message = "Операция #$i ($type): исходный путь '$path' не найден.",
                            recoverableByAgent = true
                        )
                    }
                    if (dstFile.exists() && !overwrite) {
                        return McpToolResult.error(
                            errorCode = McpErrorCodes.ALREADY_EXISTS,
                            message = "Операция #$i ($type): целевой путь '$toPath' уже существует (overwrite=false).",
                            recoverableByAgent = true
                        )
                    }
                    if (srcFile == resolved.dir.canonicalFile) {
                        return McpToolResult.error(
                            errorCode = McpErrorCodes.PATH_OUT_OF_SANDBOX,
                            message = "Нельзя переносить или копировать корневую папку репозитория целиком.",
                            recoverableByAgent = false
                        )
                    }
                    planned.add(PlannedOp(type, path, srcFile, toPath, dstFile, overwrite))
                }
                else -> {
                    return McpToolResult.error(
                        errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                        message = "Неизвестный тип операции '$type'. Допустимы: mkdir, move, copy.",
                        recoverableByAgent = true
                    )
                }
            }
        }

        val summary = JSONArray()
        for (p in planned) {
            if (!dryRun) {
                when (p.type) {
                    "mkdir" -> {
                        if (!p.sourceFile.exists() && !p.sourceFile.mkdirs()) {
                            return McpToolResult.error(
                                errorCode = McpErrorCodes.INTERNAL_ERROR,
                                message = "Не удалось создать папку '${p.sourcePath}'.",
                                recoverableByAgent = false
                            )
                        }
                    }
                    "move" -> {
                        val dest = p.destFile!!
                        dest.parentFile?.mkdirs()
                        if (dest.exists() && p.overwrite) dest.deleteRecursively()
                        if (!p.sourceFile.renameTo(dest)) {
                            p.sourceFile.copyRecursively(dest, overwrite = true)
                            p.sourceFile.deleteRecursively()
                        }
                    }
                    "copy" -> {
                        val dest = p.destFile!!
                        dest.parentFile?.mkdirs()
                        p.sourceFile.copyRecursively(dest, overwrite = p.overwrite)
                    }
                }
            }
            summary.put(
                JSONObject().apply {
                    put("type", p.type)
                    put("path", p.sourcePath)
                    if (p.destPath != null) put("toPath", p.destPath)
                }
            )
        }

        return McpToolResult.success {
            put("dryRun", dryRun)
            put("operationsCount", planned.size)
            put("operations", summary)
        }
    }
}

private class FsDeleteEntryTool(
    private val fileManager: ProjectFileManager,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "fs_delete_entry",
        description = "Удаляет файл или папку внутри репозитория. Всегда требует явного подтверждения пользователя; корень репозитория и .git защищены.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId", "relativePath"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("relativePath", McpSchemaBuilder.stringProp("Относительный путь удаляемого файла или папки"))
            }
        ),
        defaultTier = McpPermissionTier.ALWAYS_CONFIRM
    )

    override fun buildConfirmationSummary(arguments: JSONObject): String =
        AppLocale.current.mcpConfirmDeletePath(arguments.optString("relativePath"))

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val relativePath = arguments.requireNonBlankString("relativePath")

        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!
        val (target, writeErr) = resolveWritableSafe(fileManager, resolved.dir, relativePath)
        if (writeErr != null || target == null) return writeErr!!

        if (target == resolved.dir.canonicalFile) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.PATH_OUT_OF_SANDBOX,
                message = "Нельзя удалить корень репозитория через fs_delete_entry.",
                recoverableByAgent = false
            )
        }
        if (!target.exists()) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.FILE_NOT_FOUND,
                message = "Путь '$relativePath' не найден.",
                recoverableByAgent = true
            )
        }
        val wasDir = target.isDirectory
        fileManager.deleteEntry(resolved.dir, relativePath)
        return McpToolResult.success {
            put("deletedPath", relativePath)
            put("wasDirectory", wasDir)
        }
    }
}

// endregion

// region Internal FS helpers

private fun resolveReposForSearch(
    registry: ProjectRegistry,
    resolver: McpWorkspaceResolver,
    projectId: String,
    repoId: String?
): Pair<List<RepoEntry>, McpToolResult?> {
    if (repoId != null) {
        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return emptyList<RepoEntry>() to err
        return listOf(resolved.repo) to null
    }
    val (_, projectErr) = resolver.resolveProject(projectId)
    if (projectErr != null) return emptyList<RepoEntry>() to projectErr
    return registry.listRepositories(projectId) to null
}

private fun buildStatusMap(status: com.pocketworker.app.core.git.GitStatusSummary?): Map<String, FileChangeType> {
    if (status == null) return emptyMap()
    val map = LinkedHashMap<String, FileChangeType>()
    status.stagedChanges.forEach { map[it.path] = it.changeType }
    status.unstagedChanges.forEach { map[it.path] = it.changeType }
    return map
}

private fun collectTreeEntries(
    fileManager: ProjectFileManager,
    repoDir: File,
    relativeDir: String,
    remainingDepth: Int,
    includeHidden: Boolean,
    statusByPath: Map<String, FileChangeType>,
    limit: Int
): JSONArray {
    val out = JSONArray()
    fun visit(dirRel: String, depthLeft: Int) {
        if (out.length() >= limit || depthLeft <= 0) return
        val items = fileManager.listDirectory(
            projectRoot = repoDir,
            relativeDir = dirRel,
            showHiddenGit = includeHidden,
            statusByPath = statusByPath
        )
        for (item in items) {
            if (out.length() >= limit) return
            if (!includeHidden && item.name.startsWith(".") && item.isDirectory && item.name in DEFAULT_IGNORED_DIRS) {
                continue
            }
            out.put(
                JSONObject().apply {
                    put("path", item.relativePath)
                    put("isDirectory", item.isDirectory)
                    if (!item.isDirectory) put("sizeBytes", item.sizeBytes)
                    item.gitChangeType?.let { put("gitStatus", it.name) }
                }
            )
            if (item.isDirectory && depthLeft > 1 && (includeHidden || item.name !in DEFAULT_IGNORED_DIRS)) {
                visit(item.relativePath, depthLeft - 1)
            }
        }
    }
    visit(relativeDir, remainingDepth)
    return out
}

internal fun walkProjectFiles(
    rootDir: File,
    skipIgnoredDirs: Boolean,
    visitor: (file: File, relativePath: String) -> Boolean
) {
    val rootCanonical = rootDir.canonicalFile
    fun walk(dir: File): Boolean {
        val children = dir.listFiles().orEmpty()
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        for (child in children) {
            if (child.isDirectory) {
                if (child.name == ".git") continue
                if (skipIgnoredDirs && child.name in DEFAULT_IGNORED_DIRS) continue
                if (!walk(child)) return false
            } else {
                val rel = child.canonicalFile.relativeTo(rootCanonical).invariantSeparatorsPath
                if (!visitor(child, rel)) return false
            }
        }
        return true
    }
    walk(rootCanonical)
}

private fun extractFileOutline(lines: List<String>, language: CodeLanguage): JSONArray {
    val out = JSONArray()
    val pattern = when (language) {
        CodeLanguage.KOTLIN, CodeLanguage.JAVA, CodeLanguage.CSHARP, CodeLanguage.SWIFT, CodeLanguage.DART ->
            Regex("""^\s*(?:(?:public|private|protected|internal|open|abstract|sealed|data|inline|suspend|override|static|final|async)\s+)*(class|interface|object|enum|struct|record|fun|def|func|protocol|extension)\s+([A-Za-z_][A-Za-z0-9_<>.]*)""")
        CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT ->
            Regex("""^\s*(?:export\s+)?(?:default\s+)?(?:async\s+)?(class|interface|type|enum|function)\s+([A-Za-z_$][A-Za-z0-9_$]*)|^\s*(?:export\s+)?(?:const|let)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*(?:async\s*)?\(""")
        CodeLanguage.PYTHON ->
            Regex("""^\s*(?:async\s+)?(class|def)\s+([A-Za-z_][A-Za-z0-9_]*)""")
        CodeLanguage.GO ->
            Regex("""^\s*(func|type)\s+(?:\([^)]+\)\s*)?([A-Za-z_][A-Za-z0-9_]*)""")
        CodeLanguage.RUST ->
            Regex("""^\s*(?:pub(?:\([^)]*\))?\s+)?(?:async\s+)?(fn|struct|enum|trait|impl|mod)\s+([A-Za-z_][A-Za-z0-9_<>:]*)""")
        CodeLanguage.MARKDOWN ->
            Regex("""^(#{1,6})\s+(.+)$""")
        CodeLanguage.JSON ->
            Regex("""^\s{0,2}"([^"]+)"\s*:""")
        CodeLanguage.YAML ->
            Regex("""^([A-Za-z0-9_.-]+)\s*:""")
        else ->
            Regex("""^\s*(?:class|interface|struct|enum|fun|function|def|fn|func)\s+([A-Za-z_][A-Za-z0-9_]*)""")
    }

    for (i in lines.indices) {
        val raw = lines[i]
        val m = pattern.find(raw) ?: continue
        out.put(
            JSONObject().apply {
                put("line", i + 1)
                put("signature", raw.trim().take(120))
            }
        )
        if (out.length() >= 120) break
    }
    return out
}

// endregion
