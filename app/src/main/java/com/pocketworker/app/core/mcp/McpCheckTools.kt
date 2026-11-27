package com.pocketworker.app.core.mcp

import com.pocketworker.app.core.git.FileReadResult
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.GitOAuthManager
import com.pocketworker.app.core.git.ProjectFileManager
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.ui.workspace.CodeLanguage
import com.pocketworker.app.ui.workspace.CodeSyntaxHighlighter
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory

internal fun createCheckTools(
    fileManager: ProjectFileManager,
    gitEngine: GitEngine,
    credentialStore: CredentialStore,
    resolver: McpWorkspaceResolver,
    syntaxCheckEngine: SyntaxCheckEngine = NoOpSyntaxCheckEngine,
    oauthManager: GitOAuthManager? = null
): List<McpTool> = listOf(
    CheckPushReadinessTool(fileManager, gitEngine, credentialStore, resolver, oauthManager),
    CheckMergeConflictsTool(fileManager, gitEngine, resolver),
    CheckFileSyntaxTool(fileManager, gitEngine, resolver, syntaxCheckEngine)
)

// region check_push_readiness

private data class SecretPattern(
    val name: String,
    val regex: Regex
)

private val SECRET_PATTERNS = listOf(
    SecretPattern("GitHub Personal Access Token", Regex("""ghp_[A-Za-z0-9]{20,}""")),
    SecretPattern("GitHub Fine-Grained PAT", Regex("""github_pat_[A-Za-z0-9_]{20,}""")),
    SecretPattern("GitLab Personal Access Token", Regex("""glpat-[A-Za-z0-9_-]{16,}""")),
    SecretPattern("OpenAI / API Secret Key", Regex("""\bsk-[A-Za-z0-9_-]{20,}""")),
    SecretPattern("Google API Key", Regex("""AIza[0-9A-Za-z_-]{25,}""")),
    SecretPattern("AWS Access Key ID", Regex("""\bAKIA[0-9A-Z]{16}\b""")),
    SecretPattern("Private Key PEM", Regex("""-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"""))
)

private const val LARGE_FILE_WARNING_BYTES = 5L * 1024L * 1024L // 5 MB

private class CheckPushReadinessTool(
    private val fileManager: ProjectFileManager,
    private val gitEngine: GitEngine,
    private val credentialStore: CredentialStore,
    private val resolver: McpWorkspaceResolver,
    private val oauthManager: GitOAuthManager? = null
) : McpTool {

    override val definition = McpToolDefinition(
        name = "check_push_readiness",
        description = "Комплексная предполётная проверка репозитория перед git push: состояние ветки/HEAD, наличие remote и PAT-доступа, отставание от сервера (риск non-fast-forward), конфликты, крупные файлы (>5 МБ) и поиск случайно попавших секретов/токенов.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("remoteName", McpSchemaBuilder.stringProp("Имя remote (по умолчанию 'origin')"))
                put("fetchFirst", McpSchemaBuilder.boolProp("Выполнить git fetch перед проверкой ahead/behind (по умолчанию false)"))
                put("scanSecrets", McpSchemaBuilder.boolProp("Проверять файлы на случайные секреты/токены (по умолчанию true)"))
            }
        ),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val remoteName = arguments.optNonBlankString("remoteName") ?: "origin"
        val fetchFirst = arguments.optBoolean("fetchFirst", false)
        val scanSecrets = arguments.optBoolean("scanSecrets", true)

        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!

        if (!gitEngine.isGitRepository(resolved.dir)) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.NOT_A_GIT_REPO,
                message = "Папка '${resolved.repo.name}' не является Git-репозиторием.",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Сначала инициализируй Git через git_exec(..., command=\"init -b main\")")
            )
        }

        val blockers = mutableListOf<JSONObject>()
        val warnings = mutableListOf<JSONObject>()
        val nextSteps = mutableListOf<String>()

        val remotes = gitEngine.getRemotes(resolved.dir)
        val targetRemote = remotes.firstOrNull { it.name == remoteName }
        val targetRemoteUrl = targetRemote?.pushUrl ?: resolved.repo.remoteUrl

        val credentialScope = when {
            credentialStore.getRepoOAuthAccountId(repoId) != null ||
                credentialStore.getRepoCredentials(repoId) != null -> "repo"
            credentialStore.getProjectOAuthAccountId(projectId) != null ||
                credentialStore.getProjectCredentials(projectId) != null -> "project"
            credentialStore.resolveOAuthAccountForRepo(projectId, repoId, targetRemoteUrl) != null -> "oauth"
            credentialStore.getDefaultCredentials() != null -> "default"
            else -> "none"
        }
        val creds = oauthManager?.resolveFreshCredentials(projectId, repoId, targetRemoteUrl)
            ?: credentialStore.resolveForRepo(projectId, repoId, targetRemoteUrl)

        if (targetRemote == null) {
            blockers.add(issue(McpErrorCodes.REMOTE_NOT_FOUND, "Удалённый репозиторий '$remoteName' не настроен."))
            nextSteps.add("Добавь remote через git remote add $remoteName <https-url>")
        } else if (isSshGitUrl(targetRemote.pushUrl)) {
            blockers.add(issue(McpErrorCodes.SSH_TRANSPORT_UNSUPPORTED, "Remote '$remoteName' использует SSH URL (${targetRemote.pushUrl}), который не поддерживается на Android."))
            nextSteps.add("Попроси пользователя переключить remote на HTTPS URL с Personal Access Token")
        } else {
            val isHttp = targetRemote.pushUrl.startsWith("http://", ignoreCase = true) ||
                targetRemote.pushUrl.startsWith("https://", ignoreCase = true)
            if (isHttp && creds == null) {
                warnings.add(issue(McpErrorCodes.AUTH_REQUIRED, "Токен авторизации (PAT) не настроен для '$remoteName'. Для приватного репозитория push завершится ошибкой 401."))
            }
        }

        var fetchError: String? = null
        if (fetchFirst && targetRemote != null && !isSshGitUrl(targetRemote.fetchUrl)) {
            runCatching { gitEngine.fetch(resolved.dir, remoteName, creds) }
                .onFailure { ex ->
                    fetchError = ex.localizedMessage ?: ex.message ?: "Ошибка fetch"
                    warnings.add(issue(McpErrorCodes.NETWORK_UNREACHABLE, "Не удалось выполнить fetch перед проверкой: $fetchError"))
                }
        }

        val status = gitEngine.getStatus(resolved.dir)
        val repoState = Git.open(resolved.dir).use { it.repository.repositoryState }
        if (repoState != RepositoryState.SAFE) {
            blockers.add(issue(McpErrorCodes.MERGE_CONFLICT, "Репозиторий находится в незавершённом состоянии: ${repoState.description}."))
            nextSteps.add("Заверши или отмени текущую операцию (${repoState.name}) перед push")
        }
        if (status.isUnborn) {
            blockers.add(issue(McpErrorCodes.NOTHING_TO_COMMIT, "В репозитории ещё нет ни одного коммита."))
            nextSteps.add("Сделай хотя бы один коммит через git add и git commit -m \"...\"")
        }
        if (status.isDetachedHead) {
            blockers.add(issue(McpErrorCodes.DETACHED_HEAD, "HEAD откреплён (detached HEAD) — push текущей позиции невозможен без ветки."))
            nextSteps.add("Создай ветку через git checkout -b <branch-name>")
        }
        if (status.behindCount > 0) {
            blockers.add(
                issue(
                    McpErrorCodes.NON_FAST_FORWARD,
                    "Локальная ветка отстаёт от удалённой на ${status.behindCount} коммит(ов) — обычный push будет отклонён (non-fast-forward)."
                )
            )
            nextSteps.add("Выполни git pull $remoteName ${status.currentBranch.orEmpty()} и проверь конфликты")
        }

        val conflictEntries = status.unstagedChanges.filter { it.changeType.name == "CONFLICTED" }
        if (conflictEntries.isNotEmpty()) {
            blockers.add(issue(McpErrorCodes.MERGE_CONFLICT, "Есть неразрешённые конфликты слияния (${conflictEntries.size} файлов)."))
            nextSteps.add("Вызови check_merge_conflicts и разреши конфликты")
        }

        if (status.stagedChanges.isNotEmpty() || status.unstagedChanges.isNotEmpty()) {
            warnings.add(
                issue(
                    McpErrorCodes.DIRTY_WORKING_TREE,
                    "Есть незакоммиченные изменения (staged: ${status.stagedChanges.size}, unstaged/untracked: ${status.unstagedChanges.size}). Они не попадут в push до коммита."
                )
            )
        }

        // Collect candidate files to inspect for secrets, conflict markers, and large size:
        // staged + unstaged + untracked + files touched in recent/unpushed commits
        val candidatePaths = linkedSetOf<String>()
        status.stagedChanges.forEach { candidatePaths.add(it.path) }
        status.unstagedChanges.forEach { candidatePaths.add(it.path) }
        if (!status.isUnborn) {
            collectRecentCommitPaths(resolved.dir, maxCommits = maxOf(status.aheadCount, 3).coerceAtMost(20))
                .forEach { candidatePaths.add(it) }
        }

        val largeFiles = JSONArray()
        val secretFindings = JSONArray()
        val markerFiles = mutableListOf<String>()

        for (relPath in candidatePaths) {
            val file = File(resolved.dir, relPath)
            if (!file.exists() || !file.isFile) continue
            if (file.length() > LARGE_FILE_WARNING_BYTES) {
                largeFiles.put(
                    JSONObject().apply {
                        put("path", relPath)
                        put("sizeBytes", file.length())
                    }
                )
            }
            if (!scanSecrets && markerFiles.size > 10) continue
            val read = fileManager.readTextFile(resolved.dir, relPath, maxBytes = 1024 * 1024)
            if (read is FileReadResult.Text) {
                val lines = read.content.lines()
                var hasMarker = false
                for (idx in lines.indices) {
                    val line = lines[idx]
                    if (!hasMarker && (line.startsWith("<<<<<<<") || line.startsWith(">>>>>>>"))) {
                        hasMarker = true
                        markerFiles.add(relPath)
                    }
                    if (scanSecrets && secretFindings.length() < 30) {
                        for (pattern in SECRET_PATTERNS) {
                            val m = pattern.regex.find(line)
                            if (m != null) {
                                secretFindings.put(
                                    JSONObject().apply {
                                        put("path", relPath)
                                        put("line", idx + 1)
                                        put("type", pattern.name)
                                        put("maskedPreview", maskSecret(m.value))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (markerFiles.isNotEmpty()) {
            blockers.add(issue(McpErrorCodes.MERGE_CONFLICT, "В файлах обнаружены маркеры конфликтов (<<<<<<< / >>>>>>>): ${markerFiles.joinToString(", ")}."))
            nextSteps.add("Вызови check_merge_conflicts и удали маркеры конфликтов")
        }
        if (secretFindings.length() > 0) {
            blockers.add(issue(McpErrorCodes.SECRET_LEAK_WARNING, "Обнаружены возможные секреты/токены (${secretFindings.length()} совпадений)! Не отправляй их в удалённый репозиторий."))
            nextSteps.add("Удали или вынеси секреты из файлов перед коммитом и push")
        }
        if (largeFiles.length() > 0) {
            warnings.add(issue(McpErrorCodes.FILE_TOO_LARGE, "Обнаружены крупные файлы (>5 МБ): ${largeFiles.length()} шт."))
        }

        val readyToPush = blockers.isEmpty()
        if (readyToPush && nextSteps.isEmpty()) {
            if (status.aheadCount > 0 || targetRemote != null) {
                nextSteps.add("Репозиторий готов к отправке: можно запросить подтверждение на git push $remoteName ${status.currentBranch.orEmpty()}")
            }
        }

        return McpToolResult.success {
            put("readyToPush", readyToPush)
            put("branch", status.currentBranch ?: JSONObject.NULL)
            put("isDetachedHead", status.isDetachedHead)
            put("isUnborn", status.isUnborn)
            put("repositoryState", repoState.name)
            put("remote", remoteName)
            put("remoteUrl", targetRemote?.pushUrl ?: JSONObject.NULL)
            put("hasCredentials", creds != null)
            put("credentialScope", credentialScope)
            put("aheadCount", status.aheadCount)
            put("behindCount", status.behindCount)
            put("stagedCount", status.stagedChanges.size)
            put("unstagedCount", status.unstagedChanges.size)
            put("blockers", JSONArray(blockers))
            put("warnings", JSONArray(warnings))
            put("secretFindings", secretFindings)
            put("largeFiles", largeFiles)
            put("recommendedNextSteps", JSONArray(nextSteps))
        }
    }

    private fun issue(code: String, message: String): JSONObject = JSONObject().apply {
        put("code", code)
        put("message", message)
    }

    private fun maskSecret(secret: String): String {
        if (secret.length <= 10) return "****"
        return secret.take(6) + "…" + secret.takeLast(3)
    }

    private fun collectRecentCommitPaths(repoDir: File, maxCommits: Int): Set<String> {
        val result = linkedSetOf<String>()
        runCatching {
            Git.open(repoDir).use { git ->
                val repo = git.repository
                val headId = repo.resolve(Constants.HEAD) ?: return@use
                RevWalk(repo).use { rw ->
                    rw.markStart(rw.parseCommit(headId))
                    var count = 0
                    for (commit in rw) {
                        if (count >= maxCommits) break
                        val parent = if (commit.parentCount > 0) rw.parseCommit(commit.getParent(0).id) else null
                        ByteArrayOutputStream().use { out ->
                            DiffFormatter(out).use { df ->
                                df.setRepository(repo)
                                val entries = repo.newObjectReader().use { reader ->
                                    val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }
                                    val oldTree = if (parent != null) {
                                        CanonicalTreeParser().apply { reset(reader, parent.tree) }
                                    } else EmptyTreeIterator()
                                    df.scan(oldTree, newTree)
                                }
                                entries.forEach { e ->
                                    if (e.newPath != "/dev/null") result.add(e.newPath)
                                }
                            }
                        }
                        count++
                    }
                }
            }
        }
        return result
    }
}

// endregion

// region check_merge_conflicts

private class CheckMergeConflictsTool(
    private val fileManager: ProjectFileManager,
    private val gitEngine: GitEngine,
    private val resolver: McpWorkspaceResolver
) : McpTool {

    override val definition = McpToolDefinition(
        name = "check_merge_conflicts",
        description = "Находит и разбирает все конфликты слияния: неразрешённые записи в Git-индексе и точные блоки <<<<<<< / ======= / >>>>>>> с номерами строк и содержимым сторон ours/theirs.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("path", McpSchemaBuilder.stringProp("Опциональный путь к одному файлу (если не указан — проверяется весь репозиторий)"))
                put("verifyResolved", McpSchemaBuilder.boolProp("Режим проверки, что все конфликты уже устранены перед git add / commit"))
            }
        ),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val singlePath = arguments.optNonBlankString("path")?.trimStart('/')
        val verifyResolved = arguments.optBoolean("verifyResolved", false)

        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!

        val unmergedInIndex = if (gitEngine.isGitRepository(resolved.dir)) {
            runCatching {
                Git.open(resolved.dir).use { it.status().call().conflicting.sorted() }
            }.getOrDefault(emptyList())
        } else emptyList()

        val filesWithMarkers = JSONArray()
        var totalHunks = 0

        val scanFile: (File, String) -> Unit = { file, relPath ->
            if (file.isFile && file.length() <= 2L * 1024L * 1024L) {
                val read = fileManager.readTextFile(resolved.dir, relPath, maxBytes = 2 * 1024 * 1024)
                if (read is FileReadResult.Text) {
                    val hunks = parseConflictHunks(read.content)
                    if (hunks.length() > 0) {
                        totalHunks += hunks.length()
                        filesWithMarkers.put(
                            JSONObject().apply {
                                put("path", relPath)
                                put("hunkCount", hunks.length())
                                put("inGitIndexAsConflicting", relPath in unmergedInIndex)
                                put("hunks", hunks)
                            }
                        )
                    }
                }
            }
        }

        if (singlePath != null) {
            val file = runCatching { fileManager.resolveSafe(resolved.dir, singlePath) }.getOrNull()
                ?: return McpToolResult.error(
                    errorCode = McpErrorCodes.PATH_OUT_OF_SANDBOX,
                    message = "Путь '$singlePath' выходит за пределы репозитория.",
                    recoverableByAgent = false
                )
            if (!file.exists()) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.FILE_NOT_FOUND,
                    message = "Файл '$singlePath' не найден.",
                    recoverableByAgent = true
                )
            }
            scanFile(file, singlePath)
        } else {
            walkProjectFiles(resolved.dir, skipIgnoredDirs = true) { file, rel ->
                scanFile(file, rel)
                filesWithMarkers.length() < 100
            }
        }

        val relevantUnmerged = if (singlePath != null) {
            unmergedInIndex.filter { it == singlePath }
        } else {
            unmergedInIndex
        }
        val hasMarkers = filesWithMarkers.length() > 0
        val hasConflicts = hasMarkers || relevantUnmerged.isNotEmpty()
        val hint = when {
            hasMarkers -> "Удали конфликтные блоки <<<<<<< / ======= / >>>>>>> через fs_edit_file, затем выполни git add и git commit."
            relevantUnmerged.isNotEmpty() -> "Маркеров в файлах уже нет, но файлы всё ещё помечены как conflicted в индексе Git. Выполни git add для: ${relevantUnmerged.joinToString(", ")}"
            else -> "Конфликтов не обнаружено."
        }

        return McpToolResult.success {
            put("hasConflicts", hasConflicts)
            put("hasConflictMarkers", hasMarkers)
            put("resolvedReadyToCommit", !hasConflicts)
            put("verifyResolvedMode", verifyResolved)
            put("totalHunks", totalHunks)
            put("unmergedInIndex", JSONArray(relevantUnmerged))
            put("filesWithMarkers", filesWithMarkers)
            put("hint", hint)
        }
    }

    private fun parseConflictHunks(content: String): JSONArray {
        val lines = content.split('\n').map { it.removeSuffix("\r") }
        val hunks = JSONArray()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("<<<<<<<")) {
                val startLine = i + 1
                val oursLabel = line.removePrefix("<<<<<<<").trim()
                val oursLines = mutableListOf<String>()
                val theirsLines = mutableListOf<String>()
                var sepLine = -1
                var endLine = -1
                var theirsLabel = ""
                var inTheirs = false
                var j = i + 1
                while (j < lines.size) {
                    val cur = lines[j]
                    if (!inTheirs && cur.startsWith("=======")) {
                        sepLine = j + 1
                        inTheirs = true
                    } else if (cur.startsWith(">>>>>>>")) {
                        endLine = j + 1
                        theirsLabel = cur.removePrefix(">>>>>>>").trim()
                        break
                    } else {
                        if (inTheirs) theirsLines.add(cur) else oursLines.add(cur)
                    }
                    j++
                }
                hunks.put(
                    JSONObject().apply {
                        put("startLine", startLine)
                        put("separatorLine", if (sepLine > 0) sepLine else JSONObject.NULL)
                        put("endLine", if (endLine > 0) endLine else JSONObject.NULL)
                        put("oursLabel", oursLabel)
                        put("theirsLabel", theirsLabel)
                        put("ours", oursLines.joinToString("\n").take(2000))
                        put("theirs", theirsLines.joinToString("\n").take(2000))
                        put("malformed", sepLine < 0 || endLine < 0)
                    }
                )
                i = if (endLine > 0) endLine else lines.size
            } else {
                i++
            }
        }
        return hunks
    }
}

// endregion

// region check_file_syntax

data class SyntaxValidationOutcome(
    val engine: String,
    val issues: List<SyntaxIssue>
)

class FileSyntaxValidator(
    private val syntaxCheckEngine: SyntaxCheckEngine = NoOpSyntaxCheckEngine
) {

    suspend fun validate(
        relPath: String,
        content: String,
        language: CodeLanguage = CodeSyntaxHighlighter.detectLanguage(relPath)
    ): SyntaxValidationOutcome {
        val issues = mutableListOf<SyntaxIssue>()

        // 1. Always detect leftover Git merge conflict markers
        val lines = content.split('\n').map { it.removeSuffix("\r") }
        for (i in lines.indices) {
            val l = lines[i]
            if (l.startsWith("<<<<<<<") || l.startsWith(">>>>>>>")) {
                issues.add(SyntaxIssue(i + 1, 1, "error", "Обнаружен неразрешённый маркер Git-конфликта: ${l.take(24)}"))
            }
        }

        val ext = relPath.substringAfterLast('.', "").lowercase()
        // 2. Fast native strict validators for JSON and XML/SVG
        when {
            language == CodeLanguage.JSON || ext == "json" -> {
                validateJson(content, issues)
                return SyntaxValidationOutcome("kotlin-json", issues)
            }
            ext in setOf("xml", "svg", "xhtml", "plist", "csproj", "pom", "xsd", "xsl", "xaml") -> {
                validateXml(content, issues)
                return SyntaxValidationOutcome("kotlin-xml", issues)
            }
        }

        // 3. Real offline AST syntax check via V8/WASM sandbox (PHP, JS/TS, Python, C/C++/C#, Java, Kotlin, Go, Rust, Bash, SQL, HTML, CSS, YAML, TOML)
        val astResult = runCatching { syntaxCheckEngine.check(relPath, content) }.getOrNull()
        if (astResult != null && astResult.handled) {
            issues.addAll(astResult.issues)
            return SyntaxValidationOutcome(astResult.engine ?: "js-ast", issues)
        }

        // 4. Lightweight lexical fallback when AST engine is unavailable or file format is unsupported
        val fallbackEngine = when {
            language == CodeLanguage.YAML || ext in setOf("yml", "yaml") -> {
                validateYamlBasic(lines, issues)
                "basic-yaml"
            }
            language == CodeLanguage.TOML_INI || ext in setOf("toml", "ini", "cfg", "properties") -> {
                validateTomlIni(lines, ext == "properties", issues)
                "basic-toml-ini"
            }
            language != CodeLanguage.PLAIN_TEXT &&
                language != CodeLanguage.MARKDOWN &&
                language != CodeLanguage.HTML_XML &&
                language != CodeLanguage.DOCKERFILE -> {
                validateCodeLexicalBalance(content, language, issues)
                "lexical-fallback"
            }
            else -> "conflict-markers-only"
        }

        return SyntaxValidationOutcome(fallbackEngine, issues)
    }

    fun buildLineSnippet(lines: List<String>, lineNumber: Int, column: Int): String? {
        val idx = lineNumber - 1
        if (idx !in lines.indices) return null
        val rawLine = lines[idx]
        if (rawLine.isEmpty()) return null
        val trimmed = if (rawLine.length > 140) rawLine.take(140) + "…" else rawLine
        val caretPos = (column - 1).coerceIn(0, trimmed.length)
        val pointer = " ".repeat(caretPos) + "^"
        return "$trimmed\n$pointer"
    }

    private fun validateJson(content: String, issues: MutableList<SyntaxIssue>) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            issues.add(SyntaxIssue(1, 1, "error", "JSON-файл пуст."))
            return
        }
        // Strict RFC 8259 check: reject trailing commas outside strings (org.json accepts them leniently)
        findJsonTrailingComma(content)?.let { (line, col) ->
            issues.add(SyntaxIssue(line, col, "error", "Лишняя завершающая запятая (trailing comma) перед закрывающей скобкой в JSON."))
        }
        try {
            val tokener = JSONTokener(trimmed)
            val root = tokener.nextValue()
            if (root == null) {
                issues.add(SyntaxIssue(1, 1, "error", "Некорректное корневое JSON-значение."))
                return
            }
            val trailing = tokener.nextClean()
            if (trailing.code != 0) {
                issues.add(SyntaxIssue(1, 1, "error", "Лишние символы после окончания JSON-документа (начиная с '$trailing')."))
            }
        } catch (ex: Throwable) {
            issues.add(SyntaxIssue(1, 1, "error", "Ошибка парсинга JSON: ${ex.message ?: ex.javaClass.simpleName}"))
        }
    }

    private fun findJsonTrailingComma(content: String): Pair<Int, Int>? {
        var inString = false
        var line = 1
        var col = 1
        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c == '\n') {
                line++; col = 1; i++
                continue
            }
            if (inString) {
                if (c == '\\' && i + 1 < content.length) {
                    i += 2; col += 2
                    continue
                }
                if (c == '"') inString = false
                i++; col++
                continue
            }
            if (c == '"') {
                inString = true
                i++; col++
                continue
            }
            if (c == ',') {
                var j = i + 1
                while (j < content.length && content[j].isWhitespace()) j++
                if (j < content.length && (content[j] == '}' || content[j] == ']')) {
                    return line to col
                }
            }
            i++; col++
        }
        return null
    }

    private fun validateXml(content: String, issues: MutableList<SyntaxIssue>) {
        if (content.isBlank()) {
            issues.add(SyntaxIssue(1, 1, "error", "XML-файл пуст."))
            return
        }
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                isExpandEntityReferences = false
            }
            val builder = factory.newDocumentBuilder()
            builder.setErrorHandler(null)
            builder.parse(InputSource(StringReader(content)))
        } catch (ex: org.xml.sax.SAXParseException) {
            issues.add(
                SyntaxIssue(
                    line = ex.lineNumber.coerceAtLeast(1),
                    column = ex.columnNumber.coerceAtLeast(1),
                    severity = "error",
                    message = "Ошибка XML: ${ex.message ?: "некорректная разметка"}"
                )
            )
        } catch (ex: Throwable) {
            issues.add(SyntaxIssue(1, 1, "error", "Ошибка парсинга XML: ${ex.message ?: ex.javaClass.simpleName}"))
        }
    }

    private fun validateYamlBasic(lines: List<String>, issues: MutableList<SyntaxIssue>) {
        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val leading = line.takeWhile { it == ' ' || it == '\t' }
            if ('\t' in leading) {
                issues.add(SyntaxIssue(i + 1, leading.indexOf('\t') + 1, "error", "В YAML запрещены символы табуляции (\\t) в отступах; используй пробелы."))
            }
            // Check simple unclosed single-line quotes when not a block scalar
            val valuePart = if (':' in trimmed && !trimmed.startsWith("- ")) trimmed.substringAfter(':').trim() else trimmed
            if (valuePart.startsWith("\"") && valuePart.length > 1 && !hasClosingQuote(valuePart, '"')) {
                issues.add(SyntaxIssue(i + 1, 1, "error", "Незакрытая двойная кавычка в YAML-значении."))
            } else if (valuePart.startsWith("'") && valuePart.length > 1 && !valuePart.drop(1).contains('\'')) {
                issues.add(SyntaxIssue(i + 1, 1, "error", "Незакрытая одинарная кавычка в YAML-значении."))
            }
        }
    }

    private fun validateTomlIni(lines: List<String>, isProperties: Boolean, issues: MutableList<SyntaxIssue>) {
        var inMultilineDouble = false
        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()
            if (inMultilineDouble) {
                if ("\"\"\"" in trimmed) inMultilineDouble = false
                continue
            }
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";") || (isProperties && trimmed.startsWith("!"))) continue
            if (!isProperties && trimmed.startsWith("[")) {
                val noComment = stripInlineHashComment(trimmed).trim()
                if (!noComment.endsWith("]")) {
                    issues.add(SyntaxIssue(i + 1, 1, "error", "Незакрытый заголовок секции: $trimmed"))
                }
                continue
            }
            if ("\"\"\"" in trimmed) {
                val count = trimmed.windowed(3).count { it == "\"\"\"" }
                if (count % 2 == 1) inMultilineDouble = true
                continue
            }
            if (!isProperties && !trimmed.contains('=') && !trimmed.endsWith(",") && !trimmed.startsWith("]") && !trimmed.startsWith("}")) {
                // Skip array continuation lines inside multi-line arrays
                if (!trimmed.startsWith("\"") && !trimmed.startsWith("'") && !trimmed.startsWith("{")) {
                    issues.add(SyntaxIssue(i + 1, 1, "warning", "Строка без знака '=': ${trimmed.take(50)}"))
                }
            }
        }
        if (inMultilineDouble) {
            issues.add(SyntaxIssue(lines.size, 1, "error", "Незакрытая многострочная строка \"\"\" в файле."))
        }
    }

    private fun validateCodeLexicalBalance(
        content: String,
        language: CodeLanguage,
        issues: MutableList<SyntaxIssue>
    ) {
        val supportsHashComments = language in setOf(CodeLanguage.PYTHON, CodeLanguage.SHELL, CodeLanguage.YAML)
        val supportsSlashComments = language !in setOf(CodeLanguage.PYTHON, CodeLanguage.SHELL, CodeLanguage.SQL)
        val supportsSqlComments = language == CodeLanguage.SQL
        val supportsBacktickString = language in setOf(CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT, CodeLanguage.GO, CodeLanguage.SHELL, CodeLanguage.KOTLIN)
        val supportsTripleQuotes = language in setOf(CodeLanguage.KOTLIN, CodeLanguage.PYTHON, CodeLanguage.JAVA, CodeLanguage.SWIFT, CodeLanguage.DART)
        val singleQuoteOnlyCharOrLifetime = language in setOf(CodeLanguage.RUST, CodeLanguage.KOTLIN, CodeLanguage.JAVA, CodeLanguage.C_CPP, CodeLanguage.CSHARP, CodeLanguage.GO)

        data class OpenToken(val ch: Char, val line: Int, val col: Int)
        val stack = ArrayDeque<OpenToken>()

        var line = 1
        var col = 1
        var i = 0
        val len = content.length

        while (i < len) {
            val c = content[i]
            val next = if (i + 1 < len) content[i + 1] else '\u0000'
            val next2 = if (i + 2 < len) content[i + 2] else '\u0000'

            if (c == '\n') {
                line++
                col = 1
                i++
                continue
            }

            // Line comments
            if ((supportsSlashComments && c == '/' && next == '/') ||
                (supportsHashComments && c == '#') ||
                (supportsSqlComments && c == '-' && next == '-')
            ) {
                while (i < len && content[i] != '\n') { i++; col++ }
                continue
            }

            // Block comments /* ... */
            if ((supportsSlashComments || supportsSqlComments) && c == '/' && next == '*') {
                val startLine = line
                val startCol = col
                i += 2; col += 2
                var closed = false
                while (i < len) {
                    if (content[i] == '*' && i + 1 < len && content[i + 1] == '/') {
                        i += 2; col += 2
                        closed = true
                        break
                    }
                    if (content[i] == '\n') { line++; col = 1 } else col++
                    i++
                }
                if (!closed) {
                    issues.add(SyntaxIssue(startLine, startCol, "error", "Незакрытый блочный комментарий /* ... */."))
                }
                continue
            }

            // Triple-quoted strings """ ... """ or ''' ... '''
            if (supportsTripleQuotes && ((c == '"' && next == '"' && next2 == '"') ||
                    (language == CodeLanguage.PYTHON && c == '\'' && next == '\'' && next2 == '\''))
            ) {
                val q = c
                val startLine = line
                val startCol = col
                i += 3; col += 3
                var closed = false
                while (i < len) {
                    if (content[i] == q && i + 2 < len && content[i + 1] == q && content[i + 2] == q) {
                        i += 3; col += 3
                        closed = true
                        break
                    }
                    if (content[i] == '\n') { line++; col = 1 } else col++
                    i++
                }
                if (!closed) {
                    issues.add(SyntaxIssue(startLine, startCol, "error", "Незакрытая многострочная строка ${q}${q}${q}."))
                }
                continue
            }

            // Double-quoted strings
            if (c == '"') {
                val startLine = line
                val startCol = col
                i++; col++
                var closed = false
                while (i < len && content[i] != '\n') {
                    if (content[i] == '\\' && i + 1 < len) {
                        i += 2; col += 2
                        continue
                    }
                    if (content[i] == '"') {
                        i++; col++
                        closed = true
                        break
                    }
                    i++; col++
                }
                if (!closed) {
                    issues.add(SyntaxIssue(startLine, startCol, "error", "Незакрытый строковый литерал \"."))
                }
                continue
            }

            // Single-quoted strings / char literals (skip Rust lifetimes like 'a / 'static)
            if (c == '\'') {
                if (language == CodeLanguage.RUST && next.isLetter() && (i + 2 >= len || content[i + 2] != '\'')) {
                    i++; col++
                    continue
                }
                val startLine = line
                val startCol = col
                i++; col++
                var closed = false
                while (i < len && content[i] != '\n') {
                    if (content[i] == '\\' && i + 1 < len) {
                        i += 2; col += 2
                        continue
                    }
                    if (content[i] == '\'') {
                        i++; col++
                        closed = true
                        break
                    }
                    i++; col++
                }
                if (!closed && !singleQuoteOnlyCharOrLifetime) {
                    issues.add(SyntaxIssue(startLine, startCol, "error", "Незакрытый строковый литерал '."))
                } else if (!closed && singleQuoteOnlyCharOrLifetime && language != CodeLanguage.RUST) {
                    issues.add(SyntaxIssue(startLine, startCol, "error", "Незакрытый символьный литерал '."))
                }
                continue
            }

            // Backtick strings / identifiers
            if (c == '`' && supportsBacktickString) {
                val startLine = line
                val startCol = col
                i++; col++
                var closed = false
                while (i < len) {
                    if (content[i] == '\\' && i + 1 < len && language != CodeLanguage.GO) {
                        i += 2; col += 2
                        continue
                    }
                    if (content[i] == '`') {
                        i++; col++
                        closed = true
                        break
                    }
                    if (content[i] == '\n') { line++; col = 1 } else col++
                    i++
                }
                if (!closed) {
                    issues.add(SyntaxIssue(startLine, startCol, "error", "Незакрытый шаблон/идентификатор в обратных кавычках `."))
                }
                continue
            }

            // Brackets / braces / parens
            when (c) {
                '(', '{', '[' -> stack.addLast(OpenToken(c, line, col))
                ')', '}', ']' -> {
                    val expected = when (c) {
                        ')' -> '('
                        '}' -> '{'
                        else -> '['
                    }
                    if (stack.isEmpty()) {
                        issues.add(SyntaxIssue(line, col, "error", "Лишняя закрывающая скобка '$c' без соответствующей '$expected'."))
                    } else {
                        val top = stack.removeLast()
                        if (top.ch != expected) {
                            issues.add(
                                SyntaxIssue(
                                    line,
                                    col,
                                    "error",
                                    "Несоответствие скобок: встречена '$c', но последней была открыта '${top.ch}' (строка ${top.line}, колонка ${top.col})."
                                )
                            )
                        }
                    }
                }
            }

            i++
            col++
        }

        while (stack.isNotEmpty()) {
            val unclosed = stack.removeLast()
            issues.add(
                SyntaxIssue(
                    unclosed.line,
                    unclosed.col,
                    "error",
                    "Незакрытая скобка '${unclosed.ch}'."
                )
            )
        }
    }

    private fun hasClosingQuote(s: String, quote: Char): Boolean {
        var i = 1
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                i += 2
                continue
            }
            if (s[i] == quote) return true
            i++
        }
        return false
    }

    private fun stripInlineHashComment(s: String): String {
        val idx = s.indexOf('#')
        return if (idx >= 0) s.substring(0, idx) else s
    }
}

private class CheckFileSyntaxTool(
    private val fileManager: ProjectFileManager,
    private val gitEngine: GitEngine,
    private val resolver: McpWorkspaceResolver,
    syntaxCheckEngine: SyntaxCheckEngine = NoOpSyntaxCheckEngine
) : McpTool {

    private val validator = FileSyntaxValidator(syntaxCheckEngine)

    override val definition = McpToolDefinition(
        name = "check_file_syntax",
        description = "Оффлайн AST-проверка синтаксиса файлов прямо на устройстве: полноценный парсинг PHP 8.x, JS/JSX/TS/TSX, Python, C/C++/C#, Java, Kotlin, Go, Rust, Bash, SQL, HTML, CSS, YAML, TOML, JSON и XML/SVG, плюс проверка отсутствия маркеров Git-конфликтов (с fallback на баланс скобок/кавычек для прочих форматов).",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("path", McpSchemaBuilder.stringProp("Путь к одному файлу для проверки"))
                put(
                    "paths",
                    McpSchemaBuilder.arrayProp(
                        "Список путей для пакетной проверки (если ни path, ни paths не заданы — проверяются все изменённые файлы в Git status)",
                        McpSchemaBuilder.stringProp("Относительный путь к файлу")
                    )
                )
            }
        ),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!

        val targetPaths = linkedSetOf<String>()
        arguments.optNonBlankString("path")?.let { targetPaths.add(it.trimStart('/')) }
        arguments.optJSONArray("paths")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optString(i, "").trim().trimStart('/')
                if (p.isNotEmpty()) targetPaths.add(p)
            }
        }
        if (targetPaths.isEmpty()) {
            val status = gitEngine.getStatus(resolved.dir)
            (status.stagedChanges + status.unstagedChanges)
                .filter { it.changeType.name != "DELETED" }
                .take(30)
                .forEach { targetPaths.add(it.path) }
        }

        val reports = JSONArray()
        var totalErrors = 0
        var totalWarnings = 0

        for (relPath in targetPaths) {
            val file = runCatching { fileManager.resolveSafe(resolved.dir, relPath) }.getOrNull()
            if (file == null) {
                reports.put(
                    JSONObject().apply {
                        put("path", relPath)
                        put("valid", false)
                        put("error", "Путь выходит за пределы репозитория")
                    }
                )
                totalErrors++
                continue
            }
            if (!file.exists() || !file.isFile) {
                reports.put(
                    JSONObject().apply {
                        put("path", relPath)
                        put("valid", false)
                        put("error", "Файл не найден")
                    }
                )
                totalErrors++
                continue
            }

            val read = fileManager.readTextFile(resolved.dir, relPath, maxBytes = 2 * 1024 * 1024)
            when (read) {
                is FileReadResult.Binary -> reports.put(
                    JSONObject().apply {
                        put("path", relPath)
                        put("valid", true)
                        put("skipped", true)
                        put("reason", "Бинарный файл")
                    }
                )
                is FileReadResult.TooLarge -> reports.put(
                    JSONObject().apply {
                        put("path", relPath)
                        put("valid", true)
                        put("skipped", true)
                        put("reason", "Файл превышает лимит 2 МБ")
                    }
                )
                is FileReadResult.Text -> {
                    val lang = CodeSyntaxHighlighter.detectLanguage(relPath)
                    val outcome = validator.validate(relPath, read.content, lang)
                    val issues = outcome.issues
                    val lines = read.content.split('\n').map { it.removeSuffix("\r") }
                    val errCount = issues.count { it.severity == "error" }
                    val warnCount = issues.count { it.severity == "warning" }
                    totalErrors += errCount
                    totalWarnings += warnCount

                    val issuesArr = JSONArray()
                    issues.take(50).forEach { iss ->
                        issuesArr.put(
                            JSONObject().apply {
                                put("line", iss.line)
                                put("column", iss.column)
                                iss.endLine?.let { put("endLine", it) }
                                iss.endColumn?.let { put("endColumn", it) }
                                put("severity", iss.severity)
                                iss.code?.let { put("code", it) }
                                put("message", iss.message)
                                validator.buildLineSnippet(lines, iss.line, iss.column)?.let { put("snippet", it) }
                            }
                        )
                    }
                    reports.put(
                        JSONObject().apply {
                            put("path", relPath)
                            put("language", lang.name.lowercase())
                            put("engine", outcome.engine)
                            put("valid", errCount == 0)
                            put("errorCount", errCount)
                            put("warningCount", warnCount)
                            put("issues", issuesArr)
                        }
                    )
                }
            }
        }

        return McpToolResult.success {
            put("allValid", totalErrors == 0)
            put("checkedCount", reports.length())
            put("totalErrors", totalErrors)
            put("totalWarnings", totalWarnings)
            put("files", reports)
        }
    }
}

// endregion
