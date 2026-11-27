package com.pocketworker.app.core.mcp

import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.GitOAuthManager
import com.pocketworker.app.core.git.ProjectFileManager
import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.storage.CredentialStore
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.api.errors.RefNotFoundException
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.ignore.IgnoreNode
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilterGroup
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ParsedGitCommand(
    val rawCommand: String,
    val subcommand: String,
    val args: List<String>
)

internal sealed class GitTokenizeResult {
    data class Ok(val parsed: ParsedGitCommand) : GitTokenizeResult()
    data class Error(val errorResult: McpToolResult) : GitTokenizeResult()
}

internal object VirtualGitTokenizer {

    private val SHELL_OPERATORS = listOf("&&", "||", "$(", ";", "|", ">", "<", "`")

    fun parse(rawCommand: String): GitTokenizeResult {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) {
            return GitTokenizeResult.Error(
                McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Команда Git не может быть пустой.",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf("Передай команду, например: \"status --short\" или \"log -n 10 --oneline\"")
                )
            )
        }

        // First scan outside quotes for shell operators & tokenize.
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var hasCurrentToken = false
        var quoteChar: Char? = null
        var i = 0

        while (i < trimmed.length) {
            val c = trimmed[i]
            if (quoteChar != null) {
                when {
                    c == quoteChar -> {
                        quoteChar = null
                    }
                    c == '\\' && quoteChar == '"' && i + 1 < trimmed.length -> {
                        val next = trimmed[i + 1]
                        when (next) {
                            '"', '\\' -> current.append(next)
                            'n' -> current.append('\n')
                            't' -> current.append('\t')
                            else -> {
                                current.append('\\').append(next)
                            }
                        }
                        i++
                    }
                    else -> current.append(c)
                }
                hasCurrentToken = true
                i++
                continue
            }

            // Outside quotes: check for unsupported shell syntax
            val remaining = trimmed.substring(i)
            val matchedShellOp = SHELL_OPERATORS.firstOrNull { remaining.startsWith(it) }
            if (matchedShellOp != null) {
                return GitTokenizeResult.Error(
                    McpToolResult.error(
                        errorCode = McpErrorCodes.SHELL_SYNTAX_NOT_SUPPORTED,
                        message = "Синтаксис оболочки '$matchedShellOp' не поддерживается в виртуальном Git.",
                        recoverableByAgent = true,
                        reason = "Виртуальный Git не является полноценным bash/shell и выполняет строго одну Git-команду за один вызов.",
                        suggestedAlternatives = listOf(
                            "Разбей цепочку команд на отдельные последовательные вызовы git_exec",
                            "Не используй пайпы (|), перенаправления (>, <) и подстановки ($(...))"
                        )
                    )
                )
            }

            when {
                c == '\'' || c == '"' -> {
                    quoteChar = c
                    hasCurrentToken = true
                }
                c == '\\' && i + 1 < trimmed.length -> {
                    current.append(trimmed[i + 1])
                    hasCurrentToken = true
                    i++
                }
                c.isWhitespace() -> {
                    if (hasCurrentToken) {
                        tokens.add(current.toString())
                        current.clear()
                        hasCurrentToken = false
                    }
                }
                else -> {
                    current.append(c)
                    hasCurrentToken = true
                }
            }
            i++
        }

        if (quoteChar != null) {
            return GitTokenizeResult.Error(
                McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Незакрытая кавычка ($quoteChar) в команде: $trimmed",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf("Проверь парность кавычек в аргументах команды")
                )
            )
        }

        if (hasCurrentToken) {
            tokens.add(current.toString())
        }

        if (tokens.isEmpty()) {
            return GitTokenizeResult.Error(
                McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Не удалось распознать команду Git.",
                    recoverableByAgent = true
                )
            )
        }

        val normalized = if (tokens.first().equals("git", ignoreCase = true)) {
            tokens.drop(1)
        } else {
            tokens
        }

        if (normalized.isEmpty()) {
            return GitTokenizeResult.Error(
                McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Не указана подкоманда Git (например, status, diff, commit).",
                    recoverableByAgent = true
                )
            )
        }

        val first = normalized.first()
        if (first.startsWith("-")) {
            return GitTokenizeResult.Error(
                McpToolResult.error(
                    errorCode = McpErrorCodes.UNSUPPORTED_GIT_FLAG,
                    message = "Глобальный флаг Git '$first' перед подкомандой не поддерживается.",
                    recoverableByAgent = true,
                    reason = "Целевой репозиторий уже задан параметрами projectId и repoId.",
                    suggestedAlternatives = listOf("Укажи сразу подкоманду, например: status, log, commit -m \"...\"")
                )
            )
        }

        return GitTokenizeResult.Ok(
            ParsedGitCommand(
                rawCommand = trimmed,
                subcommand = first.lowercase(Locale.ROOT),
                args = normalized.drop(1)
            )
        )
    }
}

internal class VirtualGitCommandTool(
    private val fileManager: ProjectFileManager,
    private val gitEngine: GitEngine,
    private val credentialStore: CredentialStore,
    private val resolver: McpWorkspaceResolver,
    private val oauthManager: GitOAuthManager? = null
) : McpTool {

    override val definition = McpToolDefinition(
        name = "git_exec",
        description = "Виртуальный Git CLI поверх JGit для работы внутри выбранного репозитория. Принимает одну команду за вызов (без shell-операторов &&, |, ;). Автоматически подставляет сохранённые PAT-учётные данные и возвращает структурированные ошибки при неподдерживаемых командах/флагах.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "repoId", "command"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("repoId", McpSchemaBuilder.stringProp("ID репозитория"))
                put("command", McpSchemaBuilder.stringProp("Git-команда без shell-цепочек, например: 'status --short', 'diff --staged', 'commit -m \"feat: ...\"', 'push origin main'"))
            }
        ),
        defaultTier = McpPermissionTier.CONTROLLED_WRITE
    )

    override fun classifyTier(arguments: JSONObject): McpPermissionTier {
        val raw = arguments.optNonBlankString("command") ?: return McpPermissionTier.AUTO
        val parsed = when (val res = VirtualGitTokenizer.parse(raw)) {
            is GitTokenizeResult.Ok -> res.parsed
            is GitTokenizeResult.Error -> return McpPermissionTier.AUTO // fail fast with structured error
        }

        if (precheckUnsupported(parsed) != null) {
            return McpPermissionTier.AUTO // immediately surface clear error without prompting user
        }

        val sub = parsed.subcommand
        val args = parsed.args

        return when (sub) {
            "status", "diff", "log", "show", "blame", "rev-parse", "ls-files", "check-ignore", "fetch" ->
                McpPermissionTier.AUTO

            "push" -> McpPermissionTier.ALWAYS_CONFIRM

            "reset" -> if (args.any { it == "--hard" }) McpPermissionTier.ALWAYS_CONFIRM else McpPermissionTier.CONTROLLED_WRITE

            "clean" -> {
                val isDryRun = args.any { it == "-n" || it == "--dry-run" || (it.startsWith("-") && !it.startsWith("--") && 'n' in it) }
                if (isDryRun) McpPermissionTier.AUTO else McpPermissionTier.ALWAYS_CONFIRM
            }

            "restore" -> {
                val hasStaged = args.any { it == "--staged" || it == "-S" }
                val hasWorktree = args.any { it == "--worktree" || it == "-W" }
                if (hasStaged && !hasWorktree) McpPermissionTier.CONTROLLED_WRITE else McpPermissionTier.ALWAYS_CONFIRM
            }

            "checkout" -> {
                val isPathDiscard = args.contains("--") && !args.any { it == "-b" || it == "-B" }
                if (isPathDiscard) McpPermissionTier.ALWAYS_CONFIRM else McpPermissionTier.CONTROLLED_WRITE
            }

            "branch" -> when {
                args.any { it == "-d" || it == "-D" || it == "--delete" } -> McpPermissionTier.ALWAYS_CONFIRM
                isBranchListCommand(args) -> McpPermissionTier.AUTO
                else -> McpPermissionTier.CONTROLLED_WRITE
            }

            "tag" -> when {
                args.any { it == "-d" || it == "--delete" } -> McpPermissionTier.ALWAYS_CONFIRM
                args.isEmpty() || args.all { it == "-l" || it == "--list" } || (args.firstOrNull() in setOf("-l", "--list")) ->
                    McpPermissionTier.AUTO
                else -> McpPermissionTier.CONTROLLED_WRITE
            }

            "stash" -> when (args.firstOrNull()?.lowercase(Locale.ROOT)) {
                "list", "show" -> McpPermissionTier.AUTO
                "drop", "clear" -> McpPermissionTier.ALWAYS_CONFIRM
                else -> McpPermissionTier.CONTROLLED_WRITE
            }

            "remote" -> when (args.firstOrNull()?.lowercase(Locale.ROOT)) {
                null, "-v", "--verbose", "show", "get-url" -> McpPermissionTier.AUTO
                "remove", "rm" -> McpPermissionTier.ALWAYS_CONFIRM
                else -> McpPermissionTier.CONTROLLED_WRITE
            }

            "init", "add", "rm", "mv", "commit", "switch", "merge", "rebase", "cherry-pick", "revert", "pull" ->
                McpPermissionTier.CONTROLLED_WRITE

            else -> McpPermissionTier.AUTO
        }
    }

    override fun buildConfirmationSummary(arguments: JSONObject): String {
        val s = AppLocale.current
        val raw = arguments.optNonBlankString("command") ?: ""
        val display = if (raw.lowercase(Locale.ROOT).startsWith("git ")) raw else "git $raw"
        val parsed = (VirtualGitTokenizer.parse(raw) as? GitTokenizeResult.Ok)?.parsed
        val sub = parsed?.subcommand
        val args = parsed?.args.orEmpty()
        return when {
            sub == "push" && args.any { it == "-f" || it == "--force" || it == "--force-with-lease" } ->
                s.mcpConfirmGitForcePush(display)
            sub == "push" ->
                s.mcpConfirmGitPush(display)
            sub == "reset" && args.any { it == "--hard" } ->
                s.mcpConfirmGitResetHard(display)
            sub == "clean" ->
                s.mcpConfirmGitClean(display)
            sub == "restore" || (sub == "checkout" && args.contains("--")) ->
                s.mcpConfirmGitRestore(display)
            sub == "branch" && args.any { it == "-d" || it == "-D" || it == "--delete" } ->
                s.mcpConfirmGitDeleteBranch(display)
            sub == "stash" && args.firstOrNull() in setOf("drop", "clear") ->
                s.mcpConfirmGitStashDelete(display)
            else ->
                s.mcpConfirmGitCommand(display)
        }
    }

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val repoId = arguments.requireNonBlankString("repoId")
        val rawCommand = arguments.requireNonBlankString("command")

        val parsed = when (val res = VirtualGitTokenizer.parse(rawCommand)) {
            is GitTokenizeResult.Ok -> res.parsed
            is GitTokenizeResult.Error -> return res.errorResult
        }

        precheckUnsupported(parsed)?.let { return it }

        val (resolved, err) = resolver.resolveRepo(projectId, repoId)
        if (err != null || resolved == null) return err!!

        if (parsed.subcommand != "init" && !gitEngine.isGitRepository(resolved.dir)) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.NOT_A_GIT_REPO,
                message = "Папка '${resolved.repo.name}' не является Git-репозиторием.",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Инициализируй репозиторий командой git_exec(..., command=\"init -b main\")")
            )
        }

        val remoteUrlForAuth = if (parsed.subcommand in setOf("fetch", "pull", "push")) {
            val remotes = runCatching { gitEngine.getRemotes(resolved.dir) }.getOrDefault(emptyList())
            val positionalRemote = parsed.args.firstOrNull { !it.startsWith("-") }
            val matched = positionalRemote?.let { name -> remotes.firstOrNull { it.name == name } }
                ?: remotes.firstOrNull { it.name == "origin" }
                ?: remotes.firstOrNull()
            matched?.pushUrl ?: matched?.fetchUrl ?: resolved.repo.remoteUrl
        } else {
            resolved.repo.remoteUrl
        }
        val creds = if (parsed.subcommand in setOf("fetch", "pull", "push")) {
            oauthManager?.resolveFreshCredentials(projectId, repoId, remoteUrlForAuth)
                ?: credentialStore.resolveForRepo(projectId, repoId, remoteUrlForAuth)
        } else {
            credentialStore.resolveForRepo(projectId, repoId, remoteUrlForAuth)
        }
        return try {
            when (parsed.subcommand) {
                "init" -> execInit(resolved.dir, parsed)
                "status" -> execStatus(resolved.dir, parsed)
                "diff" -> execDiff(resolved.dir, parsed)
                "log" -> execLog(resolved.dir, parsed)
                "show" -> execShow(resolved.dir, parsed)
                "blame" -> execBlame(resolved.dir, parsed)
                "rev-parse" -> execRevParse(resolved.dir, parsed)
                "ls-files" -> execLsFiles(resolved.dir, parsed)
                "check-ignore" -> execCheckIgnore(resolved.dir, parsed)
                "add" -> execAdd(resolved.dir, parsed)
                "reset" -> execReset(resolved.dir, parsed)
                "restore" -> execRestore(resolved.dir, parsed)
                "rm" -> execRm(resolved.dir, parsed)
                "mv" -> execMv(resolved.dir, parsed)
                "clean" -> execClean(resolved.dir, parsed)
                "commit" -> execCommit(resolved.dir, resolved.project, parsed)
                "branch" -> execBranch(resolved.dir, parsed)
                "checkout" -> execCheckout(resolved.dir, parsed)
                "switch" -> execSwitch(resolved.dir, parsed)
                "merge" -> execMerge(resolved.dir, parsed)
                "rebase" -> execRebase(resolved.dir, parsed)
                "cherry-pick" -> execCherryPick(resolved.dir, parsed)
                "revert" -> execRevert(resolved.dir, parsed)
                "stash" -> execStash(resolved.dir, parsed)
                "tag" -> execTag(resolved.dir, resolved.project, parsed)
                "remote" -> execRemote(resolved.dir, parsed)
                "fetch" -> execFetch(resolved.dir, parsed, creds)
                "pull" -> execPull(resolved.dir, parsed, creds)
                "push" -> execPush(resolved.dir, parsed, creds)
                else -> unsupportedCommandError(parsed.subcommand)
            }
        } catch (ex: CheckoutConflictException) {
            McpToolResult.error(
                errorCode = McpErrorCodes.DIRTY_WORKING_TREE,
                message = "Операция остановлена: локальные изменения в файлах будут перезаписаны (${ex.conflictingPaths.joinToString(", ")}).",
                recoverableByAgent = true,
                suggestedAlternatives = listOf(
                    "Сохрани изменения через git stash push -m \"wip\"",
                    "Или закоммить изменения перед переключением"
                ),
                details = JSONObject().put("conflictingPaths", JSONArray(ex.conflictingPaths))
            )
        } catch (ex: EmptyCommitException) {
            McpToolResult.error(
                errorCode = McpErrorCodes.NOTHING_TO_COMMIT,
                message = "Нет проиндексированных изменений для создания коммита.",
                recoverableByAgent = true,
                suggestedAlternatives = listOf(
                    "Выполни git status, чтобы увидеть изменённые файлы",
                    "Добавь файлы через git add <path> или git add -A",
                    "Либо передай --allow-empty, если действительно нужен пустой коммит"
                )
            )
        } catch (ex: RefNotFoundException) {
            McpToolResult.error(
                errorCode = McpErrorCodes.REF_NOT_FOUND,
                message = ex.message ?: "Указанная ветка или ссылка не найдена.",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Проверь список веток через git branch -a или тегов через git tag")
            )
        } catch (ex: RefAlreadyExistsException) {
            McpToolResult.error(
                errorCode = McpErrorCodes.ALREADY_EXISTS,
                message = ex.message ?: "Ветка или тег с таким именем уже существует.",
                recoverableByAgent = true
            )
        } catch (ex: Throwable) {
            mapGitRuntimeFailure(ex)
        }
    }

    // region Prechecks for unsupported / interactive commands

    private fun precheckUnsupported(parsed: ParsedGitCommand): McpToolResult? {
        val sub = parsed.subcommand
        val args = parsed.args

        if (sub == "grep") {
            return McpToolResult.error(
                errorCode = McpErrorCodes.UNSUPPORTED_COMMAND_USE_TOOL,
                message = "Команда 'git grep' не используется в виртуальном Git — для поиска есть отдельный MCP-инструмент.",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Используй инструмент fs_grep(projectId, repoId, query)")
            )
        }

        if (sub == "hook" || sub == "hooks") {
            return McpToolResult.error(
                errorCode = McpErrorCodes.HOOKS_NOT_SUPPORTED,
                message = "Git hooks не выполняются на Android-устройстве (JGit не запускает shell-хуки).",
                recoverableByAgent = false,
                reason = "В мобильном окружении PocketWorker нет POSIX shell для pre-commit/pre-push скриптов.",
                userMessageHint = AppLocale.current.mcpHintGitHooksUnavailable
            )
        }

        if (sub in UNSUPPORTED_SUBCOMMANDS) {
            return unsupportedCommandError(sub)
        }

        if (sub !in SUPPORTED_SUBCOMMANDS) {
            return unsupportedCommandError(sub)
        }

        // Interactive mode checks
        if (sub == "rebase" && args.any { it == "-i" || it == "--interactive" }) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INTERACTIVE_MODE_UNSUPPORTED,
                message = "Интерактивный режим 'git rebase -i' не поддерживается в виртуальном Git.",
                recoverableByAgent = true,
                reason = "Виртуальный Git не предоставляет интерактивный терминал/редактор.",
                suggestedAlternatives = listOf(
                    "Используй обычный неинтерактивный git rebase <branch>",
                    "Для переноса отдельных коммитов используй git cherry-pick <commit>",
                    "Для объединения истории используй git merge --squash <branch>"
                )
            )
        }

        if (sub in setOf("add", "checkout", "reset", "restore", "stash") &&
            args.any { it == "-p" || it == "--patch" || it == "-i" || it == "--interactive" }
        ) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INTERACTIVE_MODE_UNSUPPORTED,
                message = "Интерактивный выбор фрагментов (-p/--patch/-i) в 'git $sub' не поддерживается.",
                recoverableByAgent = true,
                reason = "Виртуальный Git не поддерживает интерактивный TTY-диалог.",
                suggestedAlternatives = listOf(
                    "Укажи пути к файлам явно (например, git add path/to/file.kt)",
                    "Для точечных правок файла используй fs_edit_file"
                )
            )
        }

        if (sub in setOf("merge", "cherry-pick", "revert") && args.any { it == "--edit" || it == "-e" }) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INTERACTIVE_MODE_UNSUPPORTED,
                message = "Интерактивное редактирование сообщения коммита (--edit) не поддерживается.",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Убери флаг --edit и при необходимости передай -m \"сообщение\"")
            )
        }

        return null
    }

    private fun unsupportedCommandError(sub: String): McpToolResult {
        val hint = when (sub) {
            "lfs" -> "Git LFS не поддерживается на Android-устройстве. Крупные бинарные объекты LFS нужно скачивать/отправлять с ПК."
            "submodule" -> "Git submodules не поддерживаются в мобильном клиенте PocketWorker. Управляйте подмодулями с ПК или клонируйте нужный репозиторий отдельно."
            "worktree" -> "Git worktree не поддерживается в JGit на Android. Используйте переключение веток или отдельный клон."
            "bisect" -> "Git bisect (требующий автозапуска сборки/тестов) недоступен на телефоне. Анализируйте коммиты через git log / git show."
            else -> "Команда 'git $sub' не поддерживается встроенным движком JGit на Android. Выполните это действие на компьютере."
        }
        return McpToolResult.error(
            errorCode = McpErrorCodes.UNSUPPORTED_GIT_COMMAND,
            message = "Команда 'git $sub' не поддерживается в виртуальном Git (JGit на Android).",
            recoverableByAgent = false,
            reason = "Встроенный движок JGit не реализует команду '$sub' или она требует нативный shell/плагин.",
            suggestedAlternatives = listOf(
                "Сообщи пользователю, что эта операция недоступна на телефоне и требует ПК",
                "Если задача решается через базовые команды (status, diff, log, branch, commit, merge, cherry-pick, stash, pull, push) — используй их"
            ),
            userMessageHint = hint
        )
    }

    private fun unsupportedFlagError(
        subcommand: String,
        flag: String,
        supportedFlags: List<String>,
        alternatives: List<String> = emptyList()
    ): McpToolResult = McpToolResult.error(
        errorCode = McpErrorCodes.UNSUPPORTED_GIT_FLAG,
        message = "Флаг '$flag' не поддерживается для команды 'git $subcommand'.",
        recoverableByAgent = true,
        supportedFlags = supportedFlags,
        suggestedAlternatives = alternatives.ifEmpty {
            listOf("Повтори команду 'git $subcommand' с поддерживаемыми флагами: ${supportedFlags.joinToString(", ")}")
        }
    )

    // endregion

    // region Subcommand implementations

    private fun execInit(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-b <branch>", "--initial-branch=<branch>")
        var branch = "main"
        var i = 0
        while (i < parsed.args.size) {
            val a = parsed.args[i]
            when {
                a == "-b" || a == "--initial-branch" -> {
                    branch = parsed.args.getOrNull(i + 1)
                        ?: return missingFlagValue("init", a)
                    i += 2
                }
                a.startsWith("--initial-branch=") -> {
                    branch = a.substringAfter('=').ifBlank { "main" }
                    i++
                }
                a.startsWith("-") -> return unsupportedFlagError("init", a, supported)
                else -> return McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Лишний аргумент '$a' для git init. Репозиторий инициализируется в текущей папке.",
                    recoverableByAgent = true
                )
            }
        }
        val already = gitEngine.isGitRepository(repoDir)
        gitEngine.initGitInExistingProject(repoDir, branch)
        val out = if (already) {
            "Репозиторий уже инициализирован."
        } else {
            "Initialized empty Git repository (branch: $branch)"
        }
        return gitSuccess(parsed, out)
    }

    private fun execStatus(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-s", "--short", "-b", "--branch", "--porcelain", "-u", "--untracked-files", "--ignored")
        var shortFormat = false
        var showBranch = false
        var porcelain = false
        var showIgnored = false
        var showUntracked = true

        for (a in parsed.args) {
            when {
                a == "-s" || a == "--short" -> shortFormat = true
                a == "-b" || a == "--branch" -> showBranch = true
                a == "--porcelain" -> {
                    shortFormat = true
                    porcelain = true
                }
                a == "--ignored" -> showIgnored = true
                a == "-u" || a == "-uall" || a == "--untracked-files" || a == "--untracked-files=all" || a == "--untracked-files=normal" ->
                    showUntracked = true
                a == "-uno" || a == "--untracked-files=no" -> showUntracked = false
                a == "-sb" || a == "-bs" -> {
                    shortFormat = true
                    showBranch = true
                }
                a.startsWith("-") -> return unsupportedFlagError("status", a, supported)
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            val status = git.status().call()
            val summary = gitEngine.getStatus(repoDir)
            val state = repo.repositoryState
            val branchLine = formatBranchHeader(summary.currentBranch, summary.isDetachedHead, summary.isUnborn, summary.aheadCount, summary.behindCount)

            val out = if (shortFormat) {
                buildString {
                    if (showBranch && !porcelain) {
                        appendLine("## $branchLine")
                    }
                    val allPaths = (status.added + status.changed + status.removed +
                        status.modified + status.missing + status.conflicting +
                        if (showUntracked) status.untracked else emptySet()).toSortedSet()

                    for (path in allPaths) {
                        val code = when {
                            path in status.conflicting -> "UU"
                            path in status.untracked -> "??"
                            else -> {
                                val x = when {
                                    path in status.added -> 'A'
                                    path in status.changed -> 'M'
                                    path in status.removed -> 'D'
                                    else -> ' '
                                }
                                val y = when {
                                    path in status.modified -> 'M'
                                    path in status.missing -> 'D'
                                    else -> ' '
                                }
                                "$x$y"
                            }
                        }
                        appendLine("$code $path")
                    }
                    if (showIgnored) {
                        status.ignoredNotInIndex.sorted().forEach { appendLine("!! $it") }
                    }
                }.trimEnd()
            } else {
                buildString {
                    appendLine("On branch ${summary.currentBranch ?: "HEAD"}${if (summary.isDetachedHead) " (detached HEAD)" else ""}${if (summary.isUnborn) " (no commits yet)" else ""}")
                    if (state != RepositoryState.SAFE) {
                        appendLine("Repository state: ${state.description}")
                    }
                    if (summary.aheadCount > 0 || summary.behindCount > 0) {
                        appendLine("Tracking: ahead ${summary.aheadCount}, behind ${summary.behindCount}")
                    }
                    if (status.conflicting.isNotEmpty()) {
                        appendLine("\nUnmerged paths (conflicts):")
                        status.conflicting.sorted().forEach { appendLine("  both modified: $it") }
                    }
                    if (summary.stagedChanges.isNotEmpty()) {
                        appendLine("\nChanges to be committed:")
                        summary.stagedChanges.forEach { appendLine("  ${it.changeType.name.lowercase()}: ${it.path}") }
                    }
                    val unstagedTracked = summary.unstagedChanges.filter { it.changeType.name != "UNTRACKED" && it.changeType.name != "CONFLICTED" }
                    if (unstagedTracked.isNotEmpty()) {
                        appendLine("\nChanges not staged for commit:")
                        unstagedTracked.forEach { appendLine("  ${it.changeType.name.lowercase()}: ${it.path}") }
                    }
                    if (showUntracked && status.untracked.isNotEmpty()) {
                        appendLine("\nUntracked files:")
                        status.untracked.sorted().forEach { appendLine("  $it") }
                    }
                    if (showIgnored && status.ignoredNotInIndex.isNotEmpty()) {
                        appendLine("\nIgnored files:")
                        status.ignoredNotInIndex.sorted().forEach { appendLine("  $it") }
                    }
                    if (status.isClean && status.untracked.isEmpty()) {
                        appendLine("nothing to commit, working tree clean")
                    }
                }.trimEnd()
            }

            return gitSuccess(parsed, out) {
                put("branch", summary.currentBranch ?: JSONObject.NULL)
                put("isDetachedHead", summary.isDetachedHead)
                put("isUnborn", summary.isUnborn)
                put("repositoryState", state.name)
                put("aheadCount", summary.aheadCount)
                put("behindCount", summary.behindCount)
                put("isClean", status.isClean && status.untracked.isEmpty())
                put("conflictingCount", status.conflicting.size)
            }
        }
    }

    private fun execDiff(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("--staged", "--cached", "--stat", "--name-only", "--name-status", "<ref>", "<ref1>..<ref2>", "-- <paths>")
        var staged = false
        var statOnly = false
        var nameOnly = false
        var nameStatus = false
        val positional = mutableListOf<String>()
        val paths = mutableListOf<String>()
        var afterDoubleDash = false

        for (a in parsed.args) {
            if (afterDoubleDash) {
                paths.add(a.trimStart('/'))
                continue
            }
            when {
                a == "--" -> afterDoubleDash = true
                a == "--staged" || a == "--cached" -> staged = true
                a == "--stat" -> statOnly = true
                a == "--name-only" -> nameOnly = true
                a == "--name-status" -> nameStatus = true
                a.startsWith("-") -> return unsupportedFlagError("diff", a, supported)
                else -> positional.add(a)
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            // Disambiguate positional args: refs vs file paths
            val refs = mutableListOf<String>()
            for (p in positional) {
                if (p.contains("..") && refs.isEmpty()) {
                    val parts = p.split("..", limit = 2)
                    refs.add(parts[0].ifBlank { Constants.HEAD })
                    refs.add(parts[1].ifBlank { Constants.HEAD })
                } else if (refs.size < 2 && repo.resolve(p) != null && !File(repoDir, p).exists()) {
                    refs.add(p)
                } else if (refs.size < 2 && repo.resolve(p) != null && paths.isEmpty()) {
                    refs.add(p)
                } else {
                    paths.add(p.trimStart('/'))
                }
            }

            val out = ByteArrayOutputStream()
            DiffFormatter(out).use { df ->
                df.setRepository(repo)
                df.isDetectRenames = true
                if (paths.isNotEmpty()) {
                    df.pathFilter = PathFilterGroup.createFromStrings(paths)
                }

                val entries: List<DiffEntry> = repo.newObjectReader().use { reader ->
                    when {
                        refs.size == 2 -> {
                            val oldTree = resolveTreeParser(repo, reader, refs[0])
                                ?: return refNotFound(refs[0])
                            val newTree = resolveTreeParser(repo, reader, refs[1])
                                ?: return refNotFound(refs[1])
                            formatDiff(df, oldTree, newTree, nameOnly, nameStatus, statOnly)
                        }
                        refs.size == 1 -> {
                            val oldTree = resolveTreeParser(repo, reader, refs[0])
                                ?: return refNotFound(refs[0])
                            val newTree = if (staged) DirCacheIterator(repo.readDirCache()) else FileTreeIterator(repo)
                            formatDiff(df, oldTree, newTree, nameOnly, nameStatus, statOnly)
                        }
                        staged -> {
                            val headTreeId = repo.resolve("${Constants.HEAD}^{tree}")
                            val oldTree = if (headTreeId != null) {
                                CanonicalTreeParser().apply { reset(reader, headTreeId) }
                            } else {
                                EmptyTreeIterator()
                            }
                            val indexTree = DirCacheIterator(repo.readDirCache())
                            formatDiff(df, oldTree, indexTree, nameOnly, nameStatus, statOnly)
                        }
                        else -> {
                            val indexTree = DirCacheIterator(repo.readDirCache())
                            val workTree = FileTreeIterator(repo)
                            formatDiff(df, indexTree, workTree, nameOnly, nameStatus, statOnly)
                        }
                    }
                }

                val text = when {
                    nameOnly -> entries.joinToString("\n") { diffEntryPath(it) }
                    nameStatus -> entries.joinToString("\n") { "${shortChangeType(it.changeType)}\t${diffEntryPath(it)}" }
                    statOnly -> entries.joinToString("\n") {
                        val header = runCatching { df.toFileHeader(it) }.getOrNull()
                        var adds = 0
                        var dels = 0
                        header?.toEditList()?.forEach { edit ->
                            dels += edit.endA - edit.beginA
                            adds += edit.endB - edit.beginB
                        }
                        " ${diffEntryPath(it)} | +$adds -$dels"
                    }
                    else -> {
                        df.flush()
                        out.toString(StandardCharsets.UTF_8.name())
                    }
                }

                val maxChars = 48_000
                val truncated = text.length > maxChars
                val finalOut = if (truncated) {
                    text.take(maxChars) + "\n... (diff обрезан на $maxChars символах; используй --stat или укажи конкретный путь)"
                } else text.trimEnd()

                return gitSuccess(parsed, finalOut) {
                    put("filesChanged", entries.size)
                    put("truncated", truncated)
                }
            }
        }
    }

    private fun execLog(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-n <N>", "--max-count=<N>", "--oneline", "--stat", "--name-only", "--name-status", "--author=<str>", "--grep=<str>", "--all", "<ref>", "-- <path>")
        var maxCount = 20
        var oneline = false
        var stat = false
        var nameOnly = false
        var nameStatus = false
        var authorFilter: String? = null
        var grepFilter: String? = null
        var allRefs = false
        var refArg: String? = null
        val pathFilters = mutableListOf<String>()
        var afterDoubleDash = false

        var i = 0
        while (i < parsed.args.size) {
            val a = parsed.args[i]
            if (afterDoubleDash) {
                pathFilters.add(a.trimStart('/'))
                i++
                continue
            }
            when {
                a == "--" -> { afterDoubleDash = true; i++ }
                a == "--oneline" -> { oneline = true; i++ }
                a == "--stat" -> { stat = true; i++ }
                a == "--name-only" -> { nameOnly = true; i++ }
                a == "--name-status" -> { nameStatus = true; i++ }
                a == "--all" -> { allRefs = true; i++ }
                a == "-n" || a == "--max-count" -> {
                    val v = parsed.args.getOrNull(i + 1)?.toIntOrNull()
                        ?: return missingFlagValue("log", a)
                    maxCount = v.coerceIn(1, 200)
                    i += 2
                }
                a.startsWith("-n") && a.length > 2 && a.substring(2).all { it.isDigit() } -> {
                    maxCount = a.substring(2).toInt().coerceIn(1, 200)
                    i++
                }
                a.startsWith("--max-count=") -> {
                    maxCount = a.substringAfter('=').toIntOrNull()?.coerceIn(1, 200)
                        ?: return missingFlagValue("log", "--max-count")
                    i++
                }
                Regex("^-[0-9]+$").matches(a) -> {
                    maxCount = a.substring(1).toInt().coerceIn(1, 200)
                    i++
                }
                a == "--author" -> {
                    authorFilter = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("log", a)
                    i += 2
                }
                a.startsWith("--author=") -> {
                    authorFilter = a.substringAfter('=')
                    i++
                }
                a == "--grep" -> {
                    grepFilter = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("log", a)
                    i += 2
                }
                a.startsWith("--grep=") -> {
                    grepFilter = a.substringAfter('=')
                    i++
                }
                a.startsWith("-") -> {
                    val alts = if (a == "--graph") {
                        listOf("Выполни git log --oneline -n 20 без --graph")
                    } else emptyList()
                    return unsupportedFlagError("log", a, supported, alts)
                }
                else -> {
                    if (refArg == null) refArg = a else pathFilters.add(a.trimStart('/'))
                    i++
                }
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            val logCmd = git.log()
            if (allRefs) {
                logCmd.all()
            } else {
                val targetRef = refArg ?: Constants.HEAD
                val resolvedId = repo.resolve(targetRef)
                if (resolvedId == null) {
                    if (refArg == null) {
                        return gitSuccess(parsed, "(в репозитории ещё нет коммитов)") { put("commitCount", 0) }
                    }
                    // Maybe user passed a file path without '--'
                    val maybeFile = File(repoDir, targetRef)
                    val headId = repo.resolve(Constants.HEAD)
                    if (maybeFile.exists() && headId != null) {
                        pathFilters.add(targetRef.trimStart('/'))
                        logCmd.add(headId)
                    } else {
                        return refNotFound(targetRef)
                    }
                } else {
                    logCmd.add(resolvedId)
                }
            }
            pathFilters.forEach { logCmd.addPath(it) }

            val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val commits = mutableListOf<RevCommit>()
            for (c in logCmd.call()) {
                if (authorFilter != null) {
                    val hay = "${c.authorIdent?.name.orEmpty()} <${c.authorIdent?.emailAddress.orEmpty()}>"
                    if (!hay.contains(authorFilter, ignoreCase = true)) continue
                }
                if (grepFilter != null && !c.fullMessage.contains(grepFilter, ignoreCase = true)) {
                    continue
                }
                commits.add(c)
                if (commits.size >= maxCount) break
            }

            val sb = StringBuilder()
            for (c in commits) {
                if (oneline) {
                    sb.appendLine("${c.id.abbreviate(7).name()} ${c.shortMessage}")
                } else {
                    sb.appendLine("commit ${c.id.name}")
                    sb.appendLine("Author: ${c.authorIdent?.name ?: "Unknown"} <${c.authorIdent?.emailAddress.orEmpty()}>")
                    sb.appendLine("Date:   ${dateFmt.format(Date(c.commitTime.toLong() * 1000L))}")
                    sb.appendLine()
                    c.fullMessage.trimEnd().lines().forEach { sb.appendLine("    $it") }
                    sb.appendLine()
                }
                if (stat || nameOnly || nameStatus) {
                    val fileLines = formatCommitChangedFiles(repo, c, nameOnly = nameOnly, nameStatus = nameStatus)
                    if (fileLines.isNotEmpty()) {
                        sb.appendLine(fileLines)
                        sb.appendLine()
                    }
                }
            }

            return gitSuccess(parsed, sb.toString().trimEnd()) {
                put("commitCount", commits.size)
            }
        }
    }

    private fun execShow(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("--stat", "--name-only", "--name-status", "<ref>", "<ref>:<path>")
        var statOnly = false
        var nameOnly = false
        var nameStatus = false
        var target: String? = null

        for (a in parsed.args) {
            when {
                a == "--stat" -> statOnly = true
                a == "--name-only" -> nameOnly = true
                a == "--name-status" -> nameStatus = true
                a.startsWith("-") -> return unsupportedFlagError("show", a, supported)
                target == null -> target = a
                else -> return McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Слишком много аргументов для git show: '$a'.",
                    recoverableByAgent = true
                )
            }
        }

        val spec = target ?: Constants.HEAD
        Git.open(repoDir).use { git ->
            val repo = git.repository
            // Support <ref>:<path> to read file snapshot from a commit/branch
            val colonIdx = spec.indexOf(':')
            if (colonIdx > 0) {
                val refPart = spec.substring(0, colonIdx)
                val pathPart = spec.substring(colonIdx + 1).trimStart('/')
                val commitId = repo.resolve(refPart) ?: return refNotFound(refPart)
                RevWalk(repo).use { rw ->
                    val commit = rw.parseCommit(commitId)
                    TreeWalk.forPath(repo, pathPart, commit.tree)?.use { tw ->
                        val loader = repo.open(tw.getObjectId(0))
                        val bytes = loader.bytes
                        if (bytes.take(4096).any { it.toInt() == 0 }) {
                            return McpToolResult.error(
                                errorCode = McpErrorCodes.BINARY_FILE,
                                message = "Объект '$spec' является бинарным файлом (${bytes.size} байт).",
                                recoverableByAgent = false
                            )
                        }
                        val text = String(bytes, StandardCharsets.UTF_8)
                        val truncated = text.length > 48_000
                        val out = if (truncated) text.take(48_000) + "\n... (содержимое обрезано)" else text
                        return gitSuccess(parsed, out) {
                            put("ref", refPart)
                            put("path", pathPart)
                            put("truncated", truncated)
                        }
                    } ?: return McpToolResult.error(
                        errorCode = McpErrorCodes.FILE_NOT_FOUND,
                        message = "Файл '$pathPart' не найден в коммите '$refPart'.",
                        recoverableByAgent = true
                    )
                }
            }

            val objId = repo.resolve(spec) ?: return refNotFound(spec)
            RevWalk(repo).use { rw ->
                val commit = rw.parseCommit(objId)
                val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                val header = buildString {
                    appendLine("commit ${commit.id.name}")
                    appendLine("Author: ${commit.authorIdent?.name ?: "Unknown"} <${commit.authorIdent?.emailAddress.orEmpty()}>")
                    appendLine("Date:   ${dateFmt.format(Date(commit.commitTime.toLong() * 1000L))}")
                    appendLine()
                    commit.fullMessage.trimEnd().lines().forEach { appendLine("    $it") }
                }
                val body = when {
                    nameOnly -> formatCommitChangedFiles(repo, commit, nameOnly = true, nameStatus = false)
                    nameStatus -> formatCommitChangedFiles(repo, commit, nameOnly = false, nameStatus = true)
                    statOnly -> formatCommitChangedFiles(repo, commit, nameOnly = false, nameStatus = false)
                    else -> formatCommitFullDiff(repo, commit)
                }
                val combined = (header + "\n" + body).trimEnd()
                val truncated = combined.length > 48_000
                val out = if (truncated) combined.take(48_000) + "\n... (вывод обрезан; используй git show --stat $spec)" else combined
                return gitSuccess(parsed, out) { put("truncated", truncated) }
            }
        }
    }

    private fun execBlame(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-L <start>,<end>", "<file>")
        var startLine = 1
        var endLine = Int.MAX_VALUE
        var filePath: String? = null
        var i = 0
        while (i < parsed.args.size) {
            val a = parsed.args[i]
            when {
                a == "-L" -> {
                    val range = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("blame", a)
                    val (s, e) = parseLineRange(range) ?: return McpToolResult.error(
                        errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                        message = "Некорректный диапазон строк '$range'. Ожидается формат start,end (например, 10,40).",
                        recoverableByAgent = true
                    )
                    startLine = s
                    endLine = e
                    i += 2
                }
                a.startsWith("-L") && a.length > 2 -> {
                    val (s, e) = parseLineRange(a.substring(2)) ?: return McpToolResult.error(
                        errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                        message = "Некорректный диапазон строк '${a.substring(2)}'.",
                        recoverableByAgent = true
                    )
                    startLine = s
                    endLine = e
                    i++
                }
                a.startsWith("-") -> return unsupportedFlagError("blame", a, supported)
                filePath == null -> {
                    filePath = a.trimStart('/')
                    i++
                }
                else -> i++
            }
        }

        val targetPath = filePath ?: return McpToolResult.error(
            errorCode = McpErrorCodes.INVALID_ARGUMENTS,
            message = "Укажи путь к файлу для git blame.",
            recoverableByAgent = true
        )

        Git.open(repoDir).use { git ->
            val result = git.blame().setFilePath(targetPath).call()
                ?: return McpToolResult.error(
                    errorCode = McpErrorCodes.FILE_NOT_FOUND,
                    message = "Файл '$targetPath' не найден в истории Git.",
                    recoverableByAgent = true
                )
            val totalLines = result.resultContents.size()
            val from = startLine.coerceIn(1, (totalLines).coerceAtLeast(1))
            val to = endLine.coerceAtMost(minOf(totalLines, from + 199))
            val sb = StringBuilder()
            for (lineNum in from..to) {
                val idx = lineNum - 1
                val commit = result.getSourceCommit(idx)
                val shortSha = commit?.id?.abbreviate(7)?.name() ?: "0000000"
                val author = (result.getSourceAuthor(idx)?.name ?: "Unknown").take(18).padEnd(18)
                val content = result.resultContents.getString(idx)
                sb.appendLine("$shortSha ($author ${lineNum.toString().padStart(4)}) $content")
            }
            return gitSuccess(parsed, sb.toString().trimEnd()) {
                put("path", targetPath)
                put("totalLines", totalLines)
                put("startLine", from)
                put("endLine", to)
            }
        }
    }

    private fun execRevParse(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("--abbrev-ref", "--short", "--show-toplevel", "--is-inside-work-tree", "<ref>")
        var abbrevRef = false
        var shortSha = false
        var showToplevel = false
        var isInsideWorkTree = false
        val refs = mutableListOf<String>()

        for (a in parsed.args) {
            when {
                a == "--abbrev-ref" -> abbrevRef = true
                a == "--short" || a.startsWith("--short=") -> shortSha = true
                a == "--show-toplevel" -> showToplevel = true
                a == "--is-inside-work-tree" -> isInsideWorkTree = true
                a.startsWith("-") -> return unsupportedFlagError("rev-parse", a, supported)
                else -> refs.add(a)
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            val lines = mutableListOf<String>()
            if (isInsideWorkTree) lines.add("true")
            if (showToplevel) lines.add(repoDir.name) // never expose full Android internal filesystem path unnecessarily

            for (r in refs) {
                if (abbrevRef && r == Constants.HEAD) {
                    val headRef = repo.exactRef(Constants.HEAD)
                    if (headRef != null && headRef.isSymbolic) {
                        lines.add(Repository.shortenRefName(headRef.target.name))
                    } else {
                        lines.add("HEAD")
                    }
                } else {
                    val objId = repo.resolve(r) ?: return refNotFound(r)
                    lines.add(if (shortSha) objId.abbreviate(7).name() else objId.name)
                }
            }
            return gitSuccess(parsed, lines.joinToString("\n"))
        }
    }

    private fun execLsFiles(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("--cached", "--others", "--ignored", "--exclude-standard", "--stage")
        var others = false
        var ignored = false
        var stage = false
        var cached = false
        for (a in parsed.args) {
            when (a) {
                "--cached", "-c" -> cached = true
                "--others", "-o" -> others = true
                "--ignored", "-i" -> ignored = true
                "--exclude-standard" -> {} // handled via git status
                "--stage", "-s" -> stage = true
                else -> if (a.startsWith("-")) return unsupportedFlagError("ls-files", a, supported)
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            val lines = mutableListOf<String>()
            if (others || ignored) {
                val status = git.status().call()
                if (ignored) {
                    lines.addAll(status.ignoredNotInIndex.sorted())
                } else {
                    lines.addAll(status.untracked.sorted())
                }
            }
            if (cached || (!others && !ignored)) {
                val cache = repo.readDirCache()
                for (i in 0 until cache.entryCount) {
                    val entry = cache.getEntry(i)
                    if (stage) {
                        lines.add("${entry.fileMode} ${entry.objectId.name} ${entry.stage}\t${entry.pathString}")
                    } else {
                        lines.add(entry.pathString)
                    }
                }
            }
            val limit = 500
            val truncated = lines.size > limit
            val shown = if (truncated) lines.take(limit) else lines
            return gitSuccess(parsed, shown.joinToString("\n")) {
                put("count", lines.size)
                put("truncated", truncated)
            }
        }
    }

    private fun execCheckIgnore(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-v", "--verbose", "<paths...>")
        var verbose = false
        val paths = mutableListOf<String>()
        for (a in parsed.args) {
            when {
                a == "-v" || a == "--verbose" -> verbose = true
                a.startsWith("-") -> return unsupportedFlagError("check-ignore", a, supported)
                else -> paths.add(a.trimStart('/'))
            }
        }
        if (paths.isEmpty()) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Укажи хотя бы один путь для git check-ignore.",
                recoverableByAgent = true
            )
        }

        val ignoreRules = collectIgnoreNodes(repoDir)
        val matched = mutableListOf<String>()
        val resultsJson = JSONArray()

        for (rawPath in paths) {
            val norm = rawPath.replace('\\', '/').trimStart('/')
            val isDir = File(repoDir, norm).isDirectory
            val ignoredBy = checkPathIgnored(norm, isDir, ignoreRules)
            resultsJson.put(
                JSONObject().apply {
                    put("path", norm)
                    put("ignored", ignoredBy != null)
                    put("source", ignoredBy ?: JSONObject.NULL)
                }
            )
            if (ignoredBy != null) {
                matched.add(if (verbose) "$ignoredBy\t$norm" else norm)
            }
        }

        return gitSuccess(parsed, matched.joinToString("\n")) {
            put("results", resultsJson)
        }
    }

    private fun execAdd(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-A", "--all", "-u", "--update", ".", "<paths...>")
        var all = false
        var updateOnly = false
        val paths = mutableListOf<String>()
        for (a in parsed.args) {
            when {
                a == "-A" || a == "--all" -> all = true
                a == "-u" || a == "--update" -> updateOnly = true
                a == "--" -> {}
                a.startsWith("-") -> return unsupportedFlagError("add", a, supported)
                else -> paths.add(a.trimStart('/'))
            }
        }

        if (!all && !updateOnly && paths.isEmpty()) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Укажи файлы для добавления или флаг -A / .",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("git add -A", "git add path/to/file")
            )
        }

        if (all || (paths.size == 1 && paths[0] == ".")) {
            gitEngine.stageAll(repoDir)
        } else {
            Git.open(repoDir).use { git ->
                if (updateOnly) {
                    val cmd = git.add().setUpdate(true)
                    if (paths.isEmpty()) cmd.addFilepattern(".")
                    else paths.forEach { cmd.addFilepattern(it) }
                    cmd.call()
                } else {
                    for (p in paths) {
                        if (isProtectedGitPath(p)) {
                            return McpToolResult.error(
                                errorCode = McpErrorCodes.PROTECTED_GIT_PATH,
                                message = "Нельзя добавлять служебную папку .git в индекс.",
                                recoverableByAgent = true
                            )
                        }
                        val target = File(repoDir, p)
                        if (target.exists()) {
                            git.add().addFilepattern(p).call()
                        } else {
                            git.add().setUpdate(true).addFilepattern(p).call()
                        }
                    }
                }
            }
        }

        val status = gitEngine.getStatus(repoDir)
        return gitSuccess(parsed, "В индексе файлов: ${status.stagedChanges.size}") {
            put("stagedCount", status.stagedChanges.size)
        }
    }

    private fun execReset(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("--soft", "--mixed", "--hard", "HEAD", "<ref>", "-- <paths...>")
        var mode: ResetCommand.ResetType? = null
        val positional = mutableListOf<String>()
        val explicitPaths = mutableListOf<String>()
        var afterDoubleDash = false

        for (a in parsed.args) {
            if (afterDoubleDash) {
                explicitPaths.add(a.trimStart('/'))
                continue
            }
            when {
                a == "--" -> afterDoubleDash = true
                a == "--soft" -> mode = ResetCommand.ResetType.SOFT
                a == "--mixed" -> mode = ResetCommand.ResetType.MIXED
                a == "--hard" -> mode = ResetCommand.ResetType.HARD
                a.startsWith("-") -> return unsupportedFlagError("reset", a, supported)
                else -> positional.add(a)
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            val hasHead = repo.resolve(Constants.HEAD) != null

            // Unstage specific paths if provided
            if (explicitPaths.isNotEmpty() || (mode == null && positional.isNotEmpty() && repo.resolve(positional.first()) == null)) {
                val targetPaths = if (explicitPaths.isNotEmpty()) explicitPaths else positional.map { it.trimStart('/') }
                val ref = if (explicitPaths.isNotEmpty()) positional.firstOrNull() else null
                if (!hasHead) {
                    val rm = git.rm().setCached(true)
                    targetPaths.forEach { rm.addFilepattern(it) }
                    rm.call()
                } else {
                    val cmd = git.reset()
                    if (ref != null) cmd.setRef(ref)
                    targetPaths.forEach { cmd.addPath(it) }
                    cmd.call()
                }
                return gitSuccess(parsed, "Сброшено из индекса: ${targetPaths.joinToString(", ")}")
            }

            // Full reset
            if (!hasHead) {
                gitEngine.unstageAll(repoDir)
                return gitSuccess(parsed, "Индекс очищен (в репозитории ещё нет коммитов).")
            }

            val ref = positional.firstOrNull() ?: Constants.HEAD
            if (repo.resolve(ref) == null) return refNotFound(ref)

            val effectiveMode = mode ?: ResetCommand.ResetType.MIXED
            git.reset()
                .setMode(effectiveMode)
                .setRef(ref)
                .call()
            val newHead = repo.resolve(Constants.HEAD)?.abbreviate(7)?.name() ?: ref
            return gitSuccess(parsed, "HEAD is now at $newHead (mode=${effectiveMode.name.lowercase()})")
        }
    }

    private fun execRestore(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("--staged", "-S", "--worktree", "-W", "--source=<ref>", "-s <ref>", "<paths...>")
        var staged = false
        var worktree = false
        var sourceRef: String? = null
        val paths = mutableListOf<String>()
        var afterDoubleDash = false
        var i = 0
        while (i < parsed.args.size) {
            val a = parsed.args[i]
            if (afterDoubleDash) {
                paths.add(a.trimStart('/'))
                i++
                continue
            }
            when {
                a == "--" -> { afterDoubleDash = true; i++ }
                a == "--staged" || a == "-S" -> { staged = true; i++ }
                a == "--worktree" || a == "-W" -> { worktree = true; i++ }
                a == "-s" || a == "--source" -> {
                    sourceRef = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("restore", a)
                    i += 2
                }
                a.startsWith("--source=") -> {
                    sourceRef = a.substringAfter('=')
                    i++
                }
                a.startsWith("-") -> return unsupportedFlagError("restore", a, supported)
                else -> { paths.add(a.trimStart('/')); i++ }
            }
        }
        if (paths.isEmpty()) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Укажи пути к файлам для git restore.",
                recoverableByAgent = true
            )
        }
        if (!staged && !worktree) worktree = true

        Git.open(repoDir).use { git ->
            val repo = git.repository
            if (staged) {
                if (repo.resolve(Constants.HEAD) == null) {
                    val rm = git.rm().setCached(true)
                    paths.forEach { rm.addFilepattern(it) }
                    rm.call()
                } else {
                    val reset = git.reset()
                    if (sourceRef != null) reset.setRef(sourceRef)
                    paths.forEach { reset.addPath(it) }
                    reset.call()
                }
            }
            if (worktree) {
                val co = git.checkout()
                if (sourceRef != null) {
                    if (repo.resolve(sourceRef) == null) return refNotFound(sourceRef)
                    co.setStartPoint(sourceRef)
                }
                paths.forEach { co.addPath(it) }
                co.call()
            }
        }

        return gitSuccess(parsed, "Восстановлены файлы: ${paths.joinToString(", ")}")
    }

    private fun execRm(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("--cached", "-r", "-f", "--force", "<paths...>")
        var cached = false
        val paths = mutableListOf<String>()
        for (a in parsed.args) {
            when {
                a == "--cached" -> cached = true
                a == "-r" || a == "-f" || a == "--force" || a == "-rf" || a == "-fr" -> {}
                a == "--" -> {}
                a.startsWith("-") -> return unsupportedFlagError("rm", a, supported)
                else -> paths.add(a.trimStart('/'))
            }
        }
        if (paths.isEmpty()) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Укажи пути для git rm.",
                recoverableByAgent = true
            )
        }
        for (p in paths) {
            if (isProtectedGitPath(p)) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.PROTECTED_GIT_PATH,
                    message = "Нельзя удалять служебную папку .git.",
                    recoverableByAgent = true
                )
            }
        }

        Git.open(repoDir).use { git ->
            val cmd = git.rm().setCached(cached)
            paths.forEach { cmd.addFilepattern(it) }
            cmd.call()
        }
        return gitSuccess(parsed, "Удалено из ${if (cached) "индекса" else "репозитория"}: ${paths.joinToString(", ")}")
    }

    private fun execMv(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-f", "--force", "<source> <dest>")
        var force = false
        val positional = mutableListOf<String>()
        for (a in parsed.args) {
            when {
                a == "-f" || a == "--force" -> force = true
                a.startsWith("-") -> return unsupportedFlagError("mv", a, supported)
                else -> positional.add(a.trimStart('/'))
            }
        }
        if (positional.size != 2) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Ожидается 2 аргумента: git mv <source> <dest>.",
                recoverableByAgent = true
            )
        }
        val (srcRel, dstRel) = positional[0] to positional[1]
        val (srcFile, srcErr) = resolveWritableSafe(fileManager, repoDir, srcRel)
        if (srcErr != null || srcFile == null) return srcErr!!
        val (dstFileRaw, dstErr) = resolveWritableSafe(fileManager, repoDir, dstRel)
        if (dstErr != null || dstFileRaw == null) return dstErr!!
        if (!srcFile.exists()) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.FILE_NOT_FOUND,
                message = "Источник не найден: $srcRel",
                recoverableByAgent = true
            )
        }
        val dstFile = if (dstFileRaw.isDirectory) File(dstFileRaw, srcFile.name) else dstFileRaw
        if (dstFile.exists() && !force) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.ALREADY_EXISTS,
                message = "Целевой путь уже существует: ${dstFile.relativeTo(repoDir).invariantSeparatorsPath}",
                recoverableByAgent = true
            )
        }
        dstFile.parentFile?.mkdirs()
        if (dstFile.exists()) dstFile.deleteRecursively()
        val moved = srcFile.renameTo(dstFile) || run {
            srcFile.copyRecursively(dstFile, overwrite = true) && srcFile.deleteRecursively()
        }
        if (!moved) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INTERNAL_ERROR,
                message = "Не удалось переместить '$srcRel' в '$dstRel'.",
                recoverableByAgent = false
            )
        }
        val finalDstRel = dstFile.canonicalFile.relativeTo(repoDir.canonicalFile).invariantSeparatorsPath
        Git.open(repoDir).use { git ->
            git.rm().setCached(true).addFilepattern(srcRel).call()
            git.add().addFilepattern(finalDstRel).call()
        }
        return gitSuccess(parsed, "Перемещено: $srcRel -> $finalDstRel")
    }

    private fun execClean(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-f", "--force", "-d", "-x", "-n", "--dry-run", "-fd", "-fdx")
        var force = false
        var dirs = false
        var includeIgnored = false
        var dryRun = false
        for (a in parsed.args) {
            when {
                a == "--force" -> force = true
                a == "--dry-run" -> dryRun = true
                a.startsWith("-") && !a.startsWith("--") && a.length > 1 -> {
                    for (ch in a.substring(1)) {
                        when (ch) {
                            'f' -> force = true
                            'd' -> dirs = true
                            'x' -> includeIgnored = true
                            'n' -> dryRun = true
                            else -> return unsupportedFlagError("clean", "-$ch", supported)
                        }
                    }
                }
                a.startsWith("-") -> return unsupportedFlagError("clean", a, supported)
            }
        }
        if (!force && !dryRun) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Для git clean требуется флаг -f (или -n для предпросмотра).",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Используй git clean -n -d для предпросмотра или git clean -fd для удаления")
            )
        }

        Git.open(repoDir).use { git ->
            val cleaned = git.clean()
                .setForce(true)
                .setCleanDirectories(dirs)
                .setIgnore(!includeIgnored)
                .setDryRun(dryRun)
                .call()
                .sorted()
            val prefix = if (dryRun) "Would remove " else "Removing "
            val out = if (cleaned.isEmpty()) {
                "Нет неотслеживаемых файлов для удаления."
            } else {
                cleaned.joinToString("\n") { "$prefix$it" }
            }
            return gitSuccess(parsed, out) {
                put("dryRun", dryRun)
                put("count", cleaned.size)
                put("files", JSONArray(cleaned))
            }
        }
    }

    private fun execCommit(
        repoDir: File,
        project: com.pocketworker.app.core.git.WorkspaceProject,
        parsed: ParsedGitCommand
    ): McpToolResult {
        val supported = listOf("-m <msg>", "--message=<msg>", "-a", "--all", "-am <msg>", "--amend", "--no-edit", "--allow-empty")
        val messages = mutableListOf<String>()
        var stageTracked = false
        var amend = false
        var noEdit = false
        var allowEmpty = false

        var i = 0
        while (i < parsed.args.size) {
            val a = parsed.args[i]
            when {
                a == "-m" || a == "--message" -> {
                    val m = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("commit", a)
                    messages.add(m)
                    i += 2
                }
                a.startsWith("--message=") -> {
                    messages.add(a.substringAfter('='))
                    i++
                }
                a == "-am" || a == "-ma" -> {
                    stageTracked = true
                    val m = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("commit", a)
                    messages.add(m)
                    i += 2
                }
                a == "-a" || a == "--all" -> { stageTracked = true; i++ }
                a == "--amend" -> { amend = true; i++ }
                a == "--no-edit" -> { noEdit = true; i++ }
                a == "--allow-empty" -> { allowEmpty = true; i++ }
                a.startsWith("-") -> return unsupportedFlagError("commit", a, supported)
                else -> return McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Неожиданный аргумент '$a' для git commit.",
                    recoverableByAgent = true
                )
            }
        }

        if (messages.isEmpty() && !noEdit) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INTERACTIVE_MODE_UNSUPPORTED,
                message = "Для git commit необходимо указать сообщение через -m \"...\" (либо --amend --no-edit).",
                recoverableByAgent = true,
                reason = "Интерактивный текстовый редактор коммита недоступен в виртуальном Git.",
                suggestedAlternatives = listOf("Передай сообщение явно: git commit -m \"описание изменений\"")
            )
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            val status = git.status().call()
            if (status.conflicting.isNotEmpty()) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.MERGE_CONFLICT,
                    message = "Нельзя сделать коммит: остались неразрешённые конфликты (${status.conflicting.size}).",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf("Вызови check_merge_conflicts, исправь файлы и выполни git add")
                )
            }

            if (stageTracked) {
                git.add().setUpdate(true).addFilepattern(".").call()
            }

            val finalMessage = if (messages.isNotEmpty()) {
                messages.joinToString("\n\n").trim()
            } else {
                // --no-edit: reuse HEAD message on amend or MERGE_MSG
                val mergeMsg = runCatching { repo.readMergeCommitMsg() }.getOrNull()?.trim()
                if (!mergeMsg.isNullOrBlank()) {
                    mergeMsg
                } else if (amend) {
                    val headId = repo.resolve(Constants.HEAD)
                        ?: return McpToolResult.error(
                            errorCode = McpErrorCodes.NOTHING_TO_COMMIT,
                            message = "Нельзя выполнить --amend в репозитории без коммитов.",
                            recoverableByAgent = true
                        )
                    RevWalk(repo).use { it.parseCommit(headId).fullMessage.trim() }
                } else {
                    return McpToolResult.error(
                        errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                        message = "Нет сообщения коммита для --no-edit.",
                        recoverableByAgent = true
                    )
                }
            }

            if (finalMessage.isBlank()) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Сообщение коммита не может быть пустым.",
                    recoverableByAgent = true
                )
            }

            val postStatus = git.status().call()
            val hasStaged = postStatus.added.isNotEmpty() || postStatus.changed.isNotEmpty() || postStatus.removed.isNotEmpty()
            if (!hasStaged && !allowEmpty && !amend && repo.repositoryState == RepositoryState.SAFE) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.NOTHING_TO_COMMIT,
                    message = "Нет проиндексированных изменений для коммита.",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf("Выполни git add -A или git add <file> перед git commit")
                )
            }

            val (authorName, authorEmail) = GitEngine.resolveEffectiveAuthor(gitEngine, repoDir, project)
            val ident = PersonIdent(authorName, authorEmail)
            val commit = git.commit()
                .setAuthor(ident)
                .setCommitter(ident)
                .setMessage(finalMessage)
                .setAmend(amend)
                .setAllowEmpty(allowEmpty)
                .call()

            return gitSuccess(
                parsed,
                "[${repo.branch} ${commit.id.abbreviate(7).name()}] ${commit.shortMessage}"
            ) {
                put("commitId", commit.id.name)
                put("shortId", commit.id.abbreviate(7).name())
                put("message", commit.shortMessage)
            }
        }
    }

    private fun execBranch(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-a", "--all", "-r", "--remotes", "-v", "-vv", "--show-current", "-m <old> <new>", "-M", "-d <name>", "-D <name>", "<new-branch> [start]")
        var listAll = false
        var listRemotes = false
        var verbose = false
        var showCurrent = false
        var rename = false
        var delete = false
        var force = false
        val positional = mutableListOf<String>()

        for (a in parsed.args) {
            when (a) {
                "-a", "--all" -> listAll = true
                "-r", "--remotes" -> listRemotes = true
                "-v", "-vv", "--verbose" -> verbose = true
                "--list" -> {}
                "--show-current" -> showCurrent = true
                "-m" -> rename = true
                "-M" -> { rename = true; force = true }
                "-d", "--delete" -> delete = true
                "-D" -> { delete = true; force = true }
                "-f", "--force" -> force = true
                else -> if (a.startsWith("-")) return unsupportedFlagError("branch", a, supported) else positional.add(a)
            }
        }

        if (showCurrent) {
            val st = gitEngine.getStatus(repoDir)
            val out = if (st.isDetachedHead) "" else st.currentBranch.orEmpty()
            return gitSuccess(parsed, out)
        }

        if (delete) {
            if (positional.isEmpty()) return missingFlagValue("branch", "-d")
            val deleted = mutableListOf<String>()
            Git.open(repoDir).use { git ->
                for (name in positional) {
                    val res = git.branchDelete().setBranchNames(name).setForce(force).call()
                    if (res.isEmpty()) {
                        return McpToolResult.error(
                            errorCode = McpErrorCodes.BRANCH_NOT_FOUND,
                            message = "Ветка '$name' не найдена.",
                            recoverableByAgent = true
                        )
                    }
                    deleted.addAll(res)
                }
            }
            return gitSuccess(parsed, "Удалены ветки: ${deleted.joinToString(", ")}")
        }

        if (rename) {
            val st = gitEngine.getStatus(repoDir)
            val (oldName, newName) = when (positional.size) {
                1 -> (st.currentBranch ?: return McpToolResult.error(
                    errorCode = McpErrorCodes.DETACHED_HEAD,
                    message = "Нельзя переименовать текущую ветку в состоянии detached HEAD.",
                    recoverableByAgent = true
                )) to positional[0]
                2 -> positional[0] to positional[1]
                else -> return McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Используй: git branch -m <newName> или git branch -m <oldName> <newName>.",
                    recoverableByAgent = true
                )
            }
            gitEngine.renameBranch(repoDir, oldName, newName)
            return gitSuccess(parsed, "Ветка '$oldName' переименована в '$newName'.")
        }

        if (positional.isNotEmpty()) {
            val newBranch = positional[0]
            val startPoint = positional.getOrNull(1)
            gitEngine.createBranch(repoDir, newBranch, startPoint, checkout = false)
            return gitSuccess(parsed, "Создана ветка '$newBranch'.")
        }

        // List branches
        val branches = gitEngine.getBranches(repoDir).filter { b ->
            when {
                listAll -> true
                listRemotes -> b.isRemote
                else -> !b.isRemote
            }
        }
        val out = branches.joinToString("\n") { b ->
            val mark = if (b.isCurrent) "* " else "  "
            val name = if (b.isRemote) "remotes/${b.name}" else b.name
            if (verbose) {
                val sha = b.targetCommitId?.take(7) ?: "-------"
                val track = b.upstreamName?.let { " [$it]" }.orEmpty()
                "$mark$name $sha$track"
            } else {
                "$mark$name"
            }
        }
        return gitSuccess(parsed, out) { put("count", branches.size) }
    }

    private fun execCheckout(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-b <branch> [start]", "-B <branch> [start]", "-f", "--force", "<branch-or-ref>", "-- <paths...>")
        var create = false
        var force = false
        var newBranchName: String? = null
        val positional = mutableListOf<String>()
        val explicitPaths = mutableListOf<String>()
        var afterDoubleDash = false

        var i = 0
        while (i < parsed.args.size) {
            val a = parsed.args[i]
            if (afterDoubleDash) {
                explicitPaths.add(a.trimStart('/'))
                i++
                continue
            }
            when {
                a == "--" -> { afterDoubleDash = true; i++ }
                a == "-b" -> {
                    create = true
                    newBranchName = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("checkout", a)
                    i += 2
                }
                a == "-B" -> {
                    create = true
                    force = true
                    newBranchName = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("checkout", a)
                    i += 2
                }
                a == "-f" || a == "--force" -> { force = true; i++ }
                a.startsWith("-") -> return unsupportedFlagError("checkout", a, supported)
                else -> { positional.add(a); i++ }
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            if (explicitPaths.isNotEmpty()) {
                val co = git.checkout().setForced(force)
                val startRef = positional.firstOrNull()
                if (startRef != null) {
                    if (repo.resolve(startRef) == null) return refNotFound(startRef)
                    co.setStartPoint(startRef)
                }
                explicitPaths.forEach { co.addPath(it) }
                co.call()
                return gitSuccess(parsed, "Восстановлены файлы: ${explicitPaths.joinToString(", ")}")
            }

            if (create && newBranchName != null) {
                val startPoint = positional.firstOrNull()
                if (startPoint != null && repo.resolve(startPoint) == null) return refNotFound(startPoint)
                val cmd = git.checkout()
                    .setCreateBranch(true)
                    .setForceRefUpdate(force)
                    .setForced(force)
                    .setName(newBranchName)
                if (startPoint != null) cmd.setStartPoint(startPoint)
                cmd.call()
                return gitSuccess(parsed, "Switched to a new branch '$newBranchName'")
            }

            val target = positional.firstOrNull() ?: return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Укажи имя ветки, коммит или пути после '--'.",
                recoverableByAgent = true
            )

            // Check if remote tracking branch like origin/dev
            val remoteRef = repo.findRef(Constants.R_REMOTES + target)
            val localRef = repo.findRef(Constants.R_HEADS + target)
            if (localRef == null && remoteRef != null) {
                val localName = target.substringAfter('/')
                git.checkout()
                    .setCreateBranch(true)
                    .setName(localName)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .setStartPoint(remoteRef.name)
                    .setForced(force)
                    .call()
                return gitSuccess(parsed, "Switched to new branch '$localName' tracking '$target'")
            }

            if (localRef == null && repo.resolve(target) == null) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.BRANCH_NOT_FOUND,
                    message = "Ветка или коммит '$target' не найдены.",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf("Проверь список веток через git branch -a")
                )
            }

            git.checkout()
                .setName(target)
                .setForced(force)
                .call()
            return gitSuccess(parsed, "Switched to '$target'")
        }
    }

    private fun execSwitch(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-c <branch> [start]", "-C <branch> [start]", "--detach <ref>", "<branch>")
        var create = false
        var force = false
        var detach = false
        var newBranch: String? = null
        val positional = mutableListOf<String>()

        var i = 0
        while (i < parsed.args.size) {
            val a = parsed.args[i]
            when {
                a == "-c" || a == "--create" -> {
                    create = true
                    newBranch = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("switch", a)
                    i += 2
                }
                a == "-C" || a == "--force-create" -> {
                    create = true
                    force = true
                    newBranch = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("switch", a)
                    i += 2
                }
                a == "--detach" || a == "-d" -> { detach = true; i++ }
                a.startsWith("-") -> return unsupportedFlagError("switch", a, supported)
                else -> { positional.add(a); i++ }
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            if (create && newBranch != null) {
                val start = positional.firstOrNull()
                if (start != null && repo.resolve(start) == null) return refNotFound(start)
                val cmd = git.checkout()
                    .setCreateBranch(true)
                    .setForceRefUpdate(force)
                    .setName(newBranch)
                if (start != null) cmd.setStartPoint(start)
                cmd.call()
                return gitSuccess(parsed, "Switched to a new branch '$newBranch'")
            }

            val target = positional.firstOrNull() ?: return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Укажи имя ветки для git switch.",
                recoverableByAgent = true
            )

            if (detach) {
                val obj = repo.resolve(target) ?: return refNotFound(target)
                git.checkout().setName(obj.name).call()
                return gitSuccess(parsed, "HEAD is now detached at ${obj.abbreviate(7).name()}")
            }

            val localRef = repo.findRef(Constants.R_HEADS + target)
            if (localRef == null) {
                // Auto-track single matching remote branch if exists
                val remoteMatches = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
                    .filter { Repository.shortenRefName(it.name).substringAfter('/') == target }
                if (remoteMatches.size == 1) {
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(target)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .setStartPoint(remoteMatches.first().name)
                        .call()
                    return gitSuccess(parsed, "Switched to a new branch '$target' tracking '${Repository.shortenRefName(remoteMatches.first().name)}'")
                }
                return McpToolResult.error(
                    errorCode = McpErrorCodes.BRANCH_NOT_FOUND,
                    message = "Ветка '$target' не найдена.",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf("Проверь список веток через git branch -a или создай новую через git switch -c $target")
                )
            }

            git.checkout().setName(target).call()
            return gitSuccess(parsed, "Switched to branch '$target'")
        }
    }

    private fun execMerge(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("--no-ff", "--ff-only", "--squash", "--abort", "-m <msg>", "<branch-or-ref>")
        var ffMode = MergeCommand.FastForwardMode.FF
        var squash = false
        var abort = false
        var message: String? = null
        var target: String? = null

        var i = 0
        while (i < parsed.args.size) {
            val a = parsed.args[i]
            when {
                a == "--no-ff" -> { ffMode = MergeCommand.FastForwardMode.NO_FF; i++ }
                a == "--ff-only" -> { ffMode = MergeCommand.FastForwardMode.FF_ONLY; i++ }
                a == "--ff" -> { ffMode = MergeCommand.FastForwardMode.FF; i++ }
                a == "--squash" -> { squash = true; i++ }
                a == "--abort" -> { abort = true; i++ }
                a == "-m" -> {
                    message = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("merge", a)
                    i += 2
                }
                a.startsWith("-") -> return unsupportedFlagError("merge", a, supported)
                target == null -> { target = a; i++ }
                else -> i++
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            if (abort) {
                if (repo.repositoryState != RepositoryState.MERGING &&
                    repo.repositoryState != RepositoryState.MERGING_RESOLVED
                ) {
                    return McpToolResult.error(
                        errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                        message = "Нет активного незавершённого merge для отмены.",
                        recoverableByAgent = true
                    )
                }
                repo.writeMergeHeads(null)
                repo.writeMergeCommitMsg(null)
                git.reset().setMode(ResetCommand.ResetType.HARD).call()
                return gitSuccess(parsed, "Слияние отменено (merge --abort), рабочее дерево восстановлено.")
            }

            val refName = target ?: return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Укажи ветку или коммит для слияния: git merge <branch>.",
                recoverableByAgent = true
            )
            val objId = repo.resolve(refName) ?: return refNotFound(refName)
            val cmd = git.merge()
                .include(refName, objId)
                .setFastForward(ffMode)
                .setSquash(squash)
            if (!message.isNullOrBlank()) cmd.setMessage(message)

            val result = cmd.call()
            val conflicts = result.conflicts?.keys?.sorted().orEmpty()
            if (conflicts.isNotEmpty() || result.mergeStatus.name.contains("CONFLICT")) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.MERGE_CONFLICT,
                    message = "Слияние с '$refName' завершилось конфликтами в ${conflicts.size} файлах: ${conflicts.joinToString(", ")}.",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf(
                        "Вызови check_merge_conflicts, чтобы увидеть точные конфликтные блоки",
                        "Исправь файлы через fs_edit_file, затем git add и git commit -m \"Merge $refName\"",
                        "Или отмени слияние через git merge --abort"
                    ),
                    details = JSONObject().apply {
                        put("status", result.mergeStatus.toString())
                        put("conflicts", JSONArray(conflicts))
                    }
                )
            }
            if (!result.mergeStatus.isSuccessful) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.INTERNAL_ERROR,
                    message = "Слияние не выполнено: ${result.mergeStatus}.",
                    recoverableByAgent = true
                )
            }
            return gitSuccess(parsed, "Merge ($refName): ${result.mergeStatus}") {
                put("status", result.mergeStatus.toString())
            }
        }
    }

    private fun execRebase(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("<upstream>", "--abort", "--continue", "--skip")
        var op = RebaseCommand.Operation.BEGIN
        var upstream: String? = null

        for (a in parsed.args) {
            when {
                a == "--abort" -> op = RebaseCommand.Operation.ABORT
                a == "--continue" -> op = RebaseCommand.Operation.CONTINUE
                a == "--skip" -> op = RebaseCommand.Operation.SKIP
                a.startsWith("-") -> return unsupportedFlagError("rebase", a, supported)
                upstream == null -> upstream = a
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            val cmd = git.rebase().setOperation(op)
            if (op == RebaseCommand.Operation.BEGIN) {
                val up = upstream ?: return McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Укажи базовую ветку для git rebase <upstream>.",
                    recoverableByAgent = true
                )
                if (repo.resolve(up) == null) return refNotFound(up)
                cmd.setUpstream(up)
            }
            val res = cmd.call()
            if (res.status == org.eclipse.jgit.api.RebaseResult.Status.STOPPED ||
                res.status == org.eclipse.jgit.api.RebaseResult.Status.CONFLICTS
            ) {
                val conflicts = res.conflicts.orEmpty()
                return McpToolResult.error(
                    errorCode = McpErrorCodes.MERGE_CONFLICT,
                    message = "Rebase остановлен из-за конфликтов: ${conflicts.joinToString(", ")}.",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf(
                        "Вызови check_merge_conflicts, исправь файлы, выполни git add и git rebase --continue",
                        "Или откати rebase через git rebase --abort"
                    ),
                    details = JSONObject().put("conflicts", JSONArray(conflicts))
                )
            }
            if (!res.status.isSuccessful) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.INTERNAL_ERROR,
                    message = "Rebase завершился со статусом: ${res.status}.",
                    recoverableByAgent = true
                )
            }
            return gitSuccess(parsed, "Rebase: ${res.status}") {
                put("status", res.status.name)
            }
        }
    }

    private fun execCherryPick(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("--no-commit", "-n", "--abort", "--continue", "<commit>")
        var noCommit = false
        var abort = false
        val commits = mutableListOf<String>()

        for (a in parsed.args) {
            when {
                a == "-n" || a == "--no-commit" -> noCommit = true
                a == "--abort" -> abort = true
                a == "--continue" -> {
                    return McpToolResult.error(
                        errorCode = McpErrorCodes.UNSUPPORTED_GIT_FLAG,
                        message = "Для завершения cherry-pick после разрешения конфликтов выполни git add и git commit -m \"...\".",
                        recoverableByAgent = true,
                        suggestedAlternatives = listOf("git add <files>", "git commit --no-edit")
                    )
                }
                a.startsWith("-") -> return unsupportedFlagError("cherry-pick", a, supported)
                else -> commits.add(a)
            }
        }

        Git.open(repoDir).use { git ->
            val repo = git.repository
            if (abort) {
                repo.writeCherryPickHead(null)
                repo.writeMergeCommitMsg(null)
                git.reset().setMode(ResetCommand.ResetType.HARD).call()
                return gitSuccess(parsed, "Cherry-pick отменён.")
            }

            if (commits.isEmpty()) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                    message = "Укажи SHA коммита для git cherry-pick <commit>.",
                    recoverableByAgent = true
                )
            }

            val cmd = git.cherryPick().setNoCommit(noCommit)
            for (c in commits) {
                val id = repo.resolve(c) ?: return refNotFound(c)
                cmd.include(id)
            }
            val result = cmd.call()
            if (result.status != org.eclipse.jgit.api.CherryPickResult.CherryPickStatus.OK) {
                val conflicting = git.status().call().conflicting.sorted()
                return McpToolResult.error(
                    errorCode = McpErrorCodes.MERGE_CONFLICT,
                    message = "Cherry-pick остановлен (${result.status}). Конфликты: ${conflicting.joinToString(", ")}.",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf(
                        "Вызови check_merge_conflicts, разреши конфликты и сделай коммит",
                        "Или выполни git cherry-pick --abort"
                    ),
                    details = JSONObject().put("conflicts", JSONArray(conflicting))
                )
            }
            val newId = result.newHead?.id?.abbreviate(7)?.name() ?: "working-tree"
            return gitSuccess(parsed, "Cherry-pick выполнен ($newId)")
        }
    }

    private fun execRevert(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("--no-edit", "<commit>")
        val commits = mutableListOf<String>()
        for (a in parsed.args) {
            when {
                a == "--no-edit" -> {} // JGit never opens an interactive editor
                a == "-n" || a == "--no-commit" -> return unsupportedFlagError(
                    "revert",
                    a,
                    supported,
                    listOf("Выполни git revert <commit>, а если нужно убрать коммит в staging — выполни git reset --soft HEAD~1")
                )
                a.startsWith("-") -> return unsupportedFlagError("revert", a, supported)
                else -> commits.add(a)
            }
        }
        if (commits.isEmpty()) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                message = "Укажи SHA коммита для git revert <commit>.",
                recoverableByAgent = true
            )
        }
        Git.open(repoDir).use { git ->
            val repo = git.repository
            val cmd = git.revert()
            for (c in commits) {
                val id = repo.resolve(c) ?: return refNotFound(c)
                cmd.include(id)
            }
            val newCommit = cmd.call()
            if (newCommit == null) {
                val unmerged = cmd.unmergedPaths.orEmpty()
                return McpToolResult.error(
                    errorCode = McpErrorCodes.MERGE_CONFLICT,
                    message = "Revert остановился из-за конфликтов: ${unmerged.joinToString(", ")}.",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf("Вызови check_merge_conflicts и разреши конфликты")
                )
            }
            return gitSuccess(parsed, "Создан revert-коммит ${newCommit.id.abbreviate(7).name()}: ${newCommit.shortMessage}") {
                put("commitId", newCommit.id.name)
            }
        }
    }

    private fun execStash(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("list", "push [-m <msg>] [-u]", "save [msg]", "apply [stash@{n}]", "pop [stash@{n}]", "drop [stash@{n}]", "clear", "show [stash@{n}]")
        val sub = parsed.args.firstOrNull()?.lowercase(Locale.ROOT)
        val rest = if (sub != null && sub in setOf("list", "push", "save", "apply", "pop", "drop", "clear", "show")) {
            parsed.args.drop(1)
        } else {
            parsed.args
        }
        val effective = if (sub in setOf("list", "push", "save", "apply", "pop", "drop", "clear", "show")) sub!! else "push"

        return when (effective) {
            "list" -> {
                val list = gitEngine.getStashes(repoDir)
                val out = list.joinToString("\n") { "stash@{${it.index}}: ${it.message}" }
                gitSuccess(parsed, out) { put("count", list.size) }
            }
            "push", "save" -> {
                var msg: String? = null
                var includeUntracked = false
                var i = 0
                while (i < rest.size) {
                    val a = rest[i]
                    when {
                        a == "-m" || a == "--message" -> {
                            msg = rest.getOrNull(i + 1) ?: return missingFlagValue("stash", a)
                            i += 2
                        }
                        a == "-u" || a == "--include-untracked" -> { includeUntracked = true; i++ }
                        a.startsWith("-") -> return unsupportedFlagError("stash", a, supported)
                        effective == "save" && msg == null -> { msg = a; i++ }
                        else -> i++
                    }
                }
                val entry = gitEngine.createStash(repoDir, msg, includeUntracked)
                if (entry == null) {
                    gitSuccess(parsed, "Нет локальных изменений для сохранения в stash.")
                } else {
                    gitSuccess(parsed, "Saved working directory as stash@{0}: ${entry.message}")
                }
            }
            "apply" -> {
                val idx = parseStashIndex(rest.firstOrNull())
                gitEngine.applyStash(repoDir, idx)
                gitSuccess(parsed, "Применён stash@{$idx}.")
            }
            "pop" -> {
                val idx = parseStashIndex(rest.firstOrNull())
                gitEngine.popStash(repoDir, idx)
                gitSuccess(parsed, "Применён и удалён stash@{$idx}.")
            }
            "drop" -> {
                val idx = parseStashIndex(rest.firstOrNull())
                gitEngine.dropStash(repoDir, idx)
                gitSuccess(parsed, "Удалён stash@{$idx}.")
            }
            "clear" -> {
                gitEngine.clearStashes(repoDir)
                gitSuccess(parsed, "Все записи stash очищены.")
            }
            "show" -> {
                val idx = parseStashIndex(rest.firstOrNull())
                val stashes = gitEngine.getStashes(repoDir)
                val target = stashes.getOrNull(idx) ?: return refNotFound("stash@{$idx}")
                val detail = gitEngine.getCommitDetail(repoDir, target.commitId)
                val out = buildString {
                    appendLine("stash@{$idx}: ${target.message}")
                    detail.changedFiles.forEach { appendLine("  ${it.changeType.name.lowercase()}: ${it.path}") }
                }.trimEnd()
                gitSuccess(parsed, out)
            }
            else -> unsupportedFlagError("stash", effective, supported)
        }
    }

    private fun execTag(
        repoDir: File,
        project: com.pocketworker.app.core.git.WorkspaceProject,
        parsed: ParsedGitCommand
    ): McpToolResult {
        val supported = listOf("-l", "--list", "-a <name> -m <msg>", "-d <name>", "<name>")
        var list = false
        var annotated = false
        var delete = false
        var message: String? = null
        val positional = mutableListOf<String>()

        var i = 0
        while (i < parsed.args.size) {
            val a = parsed.args[i]
            when {
                a == "-l" || a == "--list" -> { list = true; i++ }
                a == "-a" || a == "--annotate" -> { annotated = true; i++ }
                a == "-d" || a == "--delete" -> { delete = true; i++ }
                a == "-m" -> {
                    message = parsed.args.getOrNull(i + 1) ?: return missingFlagValue("tag", a)
                    i += 2
                }
                a.startsWith("-") -> return unsupportedFlagError("tag", a, supported)
                else -> { positional.add(a); i++ }
            }
        }

        if (delete) {
            val name = positional.firstOrNull() ?: return missingFlagValue("tag", "-d")
            gitEngine.deleteTag(repoDir, name)
            return gitSuccess(parsed, "Тег '$name' удалён.")
        }

        if (list || positional.isEmpty()) {
            val pattern = positional.firstOrNull()
            val tags = gitEngine.getTags(repoDir).filter { pattern == null || matchesFileFilter(it.name, pattern) }
            val out = tags.joinToString("\n") { t ->
                if (t.message != null) "${t.name} - ${t.message}" else t.name
            }
            return gitSuccess(parsed, out) { put("count", tags.size) }
        }

        val tagName = positional[0]
        if (annotated && message.isNullOrBlank()) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.INTERACTIVE_MODE_UNSUPPORTED,
                message = "Для аннотированного тега (-a) укажи сообщение через -m \"...\".",
                recoverableByAgent = true
            )
        }
        val (authorName, authorEmail) = GitEngine.resolveEffectiveAuthor(gitEngine, repoDir, project)
        gitEngine.createTag(repoDir, tagName, message, authorName, authorEmail)
        return gitSuccess(parsed, "Создан тег '$tagName'.")
    }

    private fun execRemote(repoDir: File, parsed: ParsedGitCommand): McpToolResult {
        val supported = listOf("-v", "show [name]", "get-url <name>", "add <name> <url>", "set-url <name> <url>", "remove <name>")
        val args = parsed.args
        val first = args.firstOrNull()
        return when {
            first == null -> {
                val remotes = gitEngine.getRemotes(repoDir)
                gitSuccess(parsed, remotes.joinToString("\n") { it.name })
            }
            first == "-v" || first == "--verbose" -> {
                val remotes = gitEngine.getRemotes(repoDir)
                val out = remotes.joinToString("\n") { r ->
                    "${r.name}\t${r.fetchUrl} (fetch)\n${r.name}\t${r.pushUrl} (push)"
                }
                gitSuccess(parsed, out)
            }
            first == "get-url" -> {
                val name = args.getOrNull(1) ?: return missingFlagValue("remote", "get-url")
                val r = gitEngine.getRemotes(repoDir).firstOrNull { it.name == name }
                    ?: return remoteNotFound(name)
                gitSuccess(parsed, r.fetchUrl)
            }
            first == "show" -> {
                val name = args.getOrNull(1) ?: "origin"
                val r = gitEngine.getRemotes(repoDir).firstOrNull { it.name == name }
                    ?: return remoteNotFound(name)
                gitSuccess(parsed, "* remote ${r.name}\n  Fetch URL: ${r.fetchUrl}\n  Push  URL: ${r.pushUrl}")
            }
            first == "add" -> {
                val name = args.getOrNull(1) ?: return missingFlagValue("remote", "add")
                val url = args.getOrNull(2) ?: return missingFlagValue("remote", "add <name> <url>")
                if (isSshGitUrl(url)) return sshUnsupportedError(url)
                gitEngine.addRemote(repoDir, name, url)
                gitSuccess(parsed, "Добавлен remote '$name' -> $url")
            }
            first == "set-url" -> {
                val name = args.getOrNull(1) ?: return missingFlagValue("remote", "set-url")
                val url = args.getOrNull(2) ?: return missingFlagValue("remote", "set-url <name> <url>")
                if (isSshGitUrl(url)) return sshUnsupportedError(url)
                gitEngine.updateRemoteUrl(repoDir, name, url)
                gitSuccess(parsed, "URL remote '$name' обновлён -> $url")
            }
            first == "remove" || first == "rm" -> {
                val name = args.getOrNull(1) ?: return missingFlagValue("remote", first)
                gitEngine.removeRemote(repoDir, name)
                gitSuccess(parsed, "Удалён remote '$name'.")
            }
            else -> unsupportedFlagError("remote", first, supported)
        }
    }

    private fun execFetch(repoDir: File, parsed: ParsedGitCommand, creds: GitCredentials?): McpToolResult {
        val supported = listOf("--all", "--prune", "-p", "--tags", "[remote]")
        var all = false
        var prune = false
        var tags = false
        var remoteName: String? = null

        for (a in parsed.args) {
            when {
                a == "--all" -> all = true
                a == "--prune" || a == "-p" -> prune = true
                a == "--tags" -> tags = true
                a.startsWith("-") -> return unsupportedFlagError("fetch", a, supported)
                remoteName == null -> remoteName = a
            }
        }

        val remotes = gitEngine.getRemotes(repoDir)
        val targets = if (all) {
            if (remotes.isEmpty()) return remoteNotFound("origin")
            remotes
        } else {
            val name = remoteName ?: "origin"
            val found = remotes.firstOrNull { it.name == name } ?: return remoteNotFound(name)
            listOf(found)
        }

        for (r in targets) {
            if (isSshGitUrl(r.fetchUrl)) return sshUnsupportedError(r.fetchUrl)
        }

        val lines = mutableListOf<String>()
        Git.open(repoDir).use { git ->
            for (r in targets) {
                val cmd = git.fetch()
                    .setRemote(r.name)
                    .setRemoveDeletedRefs(prune)
                if (tags) cmd.setTagOpt(org.eclipse.jgit.transport.TagOpt.FETCH_TAGS)
                buildCredentialsProvider(creds)?.let { cmd.setCredentialsProvider(it) }
                val res = cmd.call()
                val updates = res.trackingRefUpdates.size
                lines.add("Fetch (${r.name}): обновлено ссылок — $updates")
            }
        }
        return gitSuccess(parsed, lines.joinToString("\n"))
    }

    private fun execPull(repoDir: File, parsed: ParsedGitCommand, creds: GitCredentials?): McpToolResult {
        val supported = listOf("--rebase", "-r", "--ff-only", "[remote] [branch]")
        var rebase = false
        var ffOnly = false
        val positional = mutableListOf<String>()

        for (a in parsed.args) {
            when {
                a == "--rebase" || a == "-r" -> rebase = true
                a == "--ff-only" -> ffOnly = true
                a.startsWith("-") -> return unsupportedFlagError("pull", a, supported)
                else -> positional.add(a)
            }
        }

        val remoteName = positional.getOrNull(0) ?: "origin"
        val remoteBranch = positional.getOrNull(1)
        val remote = gitEngine.getRemotes(repoDir).firstOrNull { it.name == remoteName }
            ?: return remoteNotFound(remoteName)
        if (isSshGitUrl(remote.fetchUrl)) return sshUnsupportedError(remote.fetchUrl)

        Git.open(repoDir).use { git ->
            val repo = git.repository
            val currentBranch = repo.branch
            val cmd = git.pull()
                .setRemote(remoteName)
                .setRebase(rebase)
            if (ffOnly) {
                cmd.setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
            }
            if (!remoteBranch.isNullOrBlank()) {
                cmd.setRemoteBranchName(remoteBranch)
            } else if (currentBranch != null && BranchConfig(repo.config, currentBranch).trackingBranch == null) {
                cmd.setRemoteBranchName(currentBranch)
            }
            buildCredentialsProvider(creds)?.let { cmd.setCredentialsProvider(it) }
            val result = cmd.call()

            val mergeConflicts = result.mergeResult?.conflicts?.keys?.sorted().orEmpty()
            val rebaseConflicts = result.rebaseResult?.conflicts.orEmpty()
            val allConflicts = (mergeConflicts + rebaseConflicts).distinct()
            if (allConflicts.isNotEmpty() || !result.isSuccessful) {
                if (allConflicts.isNotEmpty()) {
                    return McpToolResult.error(
                        errorCode = McpErrorCodes.MERGE_CONFLICT,
                        message = "Pull завершился конфликтами в файлах: ${allConflicts.joinToString(", ")}.",
                        recoverableByAgent = true,
                        suggestedAlternatives = listOf(
                            "Вызови check_merge_conflicts для анализа конфликтных блоков",
                            "Исправь файлы через fs_edit_file, затем git add и git commit"
                        ),
                        details = JSONObject().put("conflicts", JSONArray(allConflicts))
                    )
                }
                val detailMsg = result.mergeResult?.mergeStatus?.toString()
                    ?: result.rebaseResult?.status?.toString()
                    ?: "Pull не выполнен"
                return McpToolResult.error(
                    errorCode = McpErrorCodes.INTERNAL_ERROR,
                    message = "Pull ($remoteName) завершился ошибкой: $detailMsg.",
                    recoverableByAgent = true
                )
            }

            val summary = result.mergeResult?.mergeStatus?.toString()
                ?: result.rebaseResult?.status?.toString()
                ?: "OK"
            return gitSuccess(parsed, "Pull ($remoteName): $summary")
        }
    }

    private fun execPush(repoDir: File, parsed: ParsedGitCommand, creds: GitCredentials?): McpToolResult {
        val supported = listOf("-u", "--set-upstream", "--tags", "-f", "--force", "--force-with-lease", "--delete", "[remote] [branch]")
        var setUpstream = false
        var pushTags = false
        var force = false
        var deleteRemoteBranch = false
        val positional = mutableListOf<String>()

        for (a in parsed.args) {
            when {
                a == "-u" || a == "--set-upstream" -> setUpstream = true
                a == "--tags" -> pushTags = true
                a == "-f" || a == "--force" || a == "--force-with-lease" -> force = true
                a == "--delete" || a == "-d" -> deleteRemoteBranch = true
                a.startsWith("-") -> return unsupportedFlagError("push", a, supported)
                else -> positional.add(a)
            }
        }

        val remoteName = positional.getOrNull(0) ?: "origin"
        val explicitBranch = positional.getOrNull(1)
        val remote = gitEngine.getRemotes(repoDir).firstOrNull { it.name == remoteName }
            ?: return remoteNotFound(remoteName)
        if (isSshGitUrl(remote.pushUrl)) return sshUnsupportedError(remote.pushUrl)

        Git.open(repoDir).use { git ->
            val repo = git.repository
            val headRef = repo.exactRef(Constants.HEAD)
            val isDetached = headRef != null && !headRef.isSymbolic
            val hasHead = repo.resolve(Constants.HEAD) != null

            if (!hasHead && !pushTags && !deleteRemoteBranch) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.NOTHING_TO_COMMIT,
                    message = "Нечего отправлять: в репозитории нет коммитов.",
                    recoverableByAgent = true
                )
            }
            if (isDetached && explicitBranch == null && !pushTags && !deleteRemoteBranch) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.DETACHED_HEAD,
                    message = "Нельзя выполнить git push в состоянии detached HEAD без явного указания ветки.",
                    recoverableByAgent = true,
                    suggestedAlternatives = listOf("Создай ветку через git checkout -b <name> и затем сделай push")
                )
            }

            val targetBranch = explicitBranch ?: repo.branch
            val cmd = git.push()
                .setRemote(remoteName)
                .setForce(force)

            when {
                pushTags -> cmd.setPushTags()
                deleteRemoteBranch -> {
                    if (targetBranch.isNullOrBlank()) {
                        return McpToolResult.error(
                            errorCode = McpErrorCodes.INVALID_ARGUMENTS,
                            message = "Укажи имя удалённой ветки для --delete.",
                            recoverableByAgent = true
                        )
                    }
                    cmd.setRefSpecs(RefSpec(":${Constants.R_HEADS}$targetBranch"))
                }
                else -> {
                    if (targetBranch.isNullOrBlank()) {
                        return McpToolResult.error(
                            errorCode = McpErrorCodes.DETACHED_HEAD,
                            message = "Не удалось определить текущую ветку для push.",
                            recoverableByAgent = true
                        )
                    }
                    cmd.setRefSpecs(RefSpec("${Constants.R_HEADS}$targetBranch:${Constants.R_HEADS}$targetBranch"))
                }
            }

            buildCredentialsProvider(creds)?.let { cmd.setCredentialsProvider(it) }
            val results = cmd.call()

            val okMsgs = mutableListOf<String>()
            val nonFastForwardRefs = mutableListOf<String>()
            val otherErrors = mutableListOf<String>()

            for (pushRes in results) {
                for (u in pushRes.remoteUpdates) {
                    when (u.status) {
                        RemoteRefUpdate.Status.OK,
                        RemoteRefUpdate.Status.UP_TO_DATE -> {
                            okMsgs.add("${Repository.shortenRefName(u.remoteName)}: ${u.status}")
                        }
                        RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD -> {
                            nonFastForwardRefs.add(Repository.shortenRefName(u.remoteName))
                        }
                        else -> {
                            otherErrors.add(
                                "${Repository.shortenRefName(u.remoteName)}: ${u.status}" +
                                    (u.message?.let { " ($it)" } ?: "")
                            )
                        }
                    }
                }
            }

            if (nonFastForwardRefs.isNotEmpty()) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.NON_FAST_FORWARD,
                    message = "Push отклонён (non-fast-forward) для веток: ${nonFastForwardRefs.joinToString(", ")}. На сервере есть новые коммиты.",
                    recoverableByAgent = true,
                    reason = "Локальная ветка отстаёт от удалённой.",
                    suggestedAlternatives = listOf(
                        "Выполни git pull $remoteName ${targetBranch ?: ""} (или git pull --rebase)",
                        "Проверь конфликты через check_merge_conflicts и повтори git push"
                    )
                )
            }

            if (otherErrors.isNotEmpty()) {
                return McpToolResult.error(
                    errorCode = McpErrorCodes.INTERNAL_ERROR,
                    message = "Push отклонён сервером: ${otherErrors.joinToString("; ")}",
                    recoverableByAgent = false,
                    userMessageHint = AppLocale.current.mcpHintPushRejected(otherErrors.joinToString("; "))
                )
            }

            // Ensure upstream tracking is set on successful branch push
            if (!pushTags && !deleteRemoteBranch && !targetBranch.isNullOrBlank()) {
                val cfg = repo.config
                if (setUpstream || cfg.getString("branch", targetBranch, "remote") == null) {
                    cfg.setString("branch", targetBranch, "remote", remoteName)
                    cfg.setString("branch", targetBranch, "merge", Constants.R_HEADS + targetBranch)
                    cfg.save()
                }
            }

            val msg = if (okMsgs.isEmpty()) "Push в $remoteName выполнен." else "Push в $remoteName: ${okMsgs.joinToString(", ")}"
            return gitSuccess(parsed, msg)
        }
    }

    // endregion

    // region Helpers

    private fun gitSuccess(
        parsed: ParsedGitCommand,
        stdout: String,
        extra: JSONObject.() -> Unit = {}
    ): McpToolResult = McpToolResult.success {
        put("command", parsed.rawCommand)
        put("subcommand", parsed.subcommand)
        put("exitCode", 0)
        put("stdout", stdout)
        put("stderr", "")
        extra()
    }

    private fun missingFlagValue(sub: String, flag: String): McpToolResult = McpToolResult.error(
        errorCode = McpErrorCodes.INVALID_ARGUMENTS,
        message = "Не указано значение после '$flag' в команде 'git $sub'.",
        recoverableByAgent = true
    )

    private fun refNotFound(ref: String): McpToolResult = McpToolResult.error(
        errorCode = McpErrorCodes.REF_NOT_FOUND,
        message = "Ссылка, ветка или коммит '$ref' не найдены.",
        recoverableByAgent = true,
        suggestedAlternatives = listOf("Проверь существующие ветки через git branch -a и историю через git log --oneline")
    )

    private fun remoteNotFound(name: String): McpToolResult = McpToolResult.error(
        errorCode = McpErrorCodes.REMOTE_NOT_FOUND,
        message = "Удалённый репозиторий (remote) '$name' не найден.",
        recoverableByAgent = true,
        suggestedAlternatives = listOf("Выполни git remote -v, чтобы увидеть список настроенных remote")
    )

    private fun sshUnsupportedError(url: String): McpToolResult = McpToolResult.error(
        errorCode = McpErrorCodes.SSH_TRANSPORT_UNSUPPORTED,
        message = "SSH-адрес '$url' не поддерживается на устройстве.",
        recoverableByAgent = false,
        reason = "PocketWorker поддерживает только HTTPS-транспорт с Personal Access Token (PAT).",
        suggestedAlternatives = listOf("Попроси пользователя переключить remote на HTTPS URL или добавить PAT в настройках"),
        userMessageHint = AppLocale.current.mcpHintSshUnsupported
    )

    private fun mapGitRuntimeFailure(ex: Throwable): McpToolResult {
        val raw = ex.localizedMessage ?: ex.message ?: ex.javaClass.simpleName
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            "401" in lower || "not authorized" in lower || "authentication is required" in lower ||
                "403" in lower || "forbidden" in lower || "unknownhost" in lower ||
                "connect" in lower || "timed out" in lower || "network" in lower -> mapTransportFailure(ex)
            "ssh" in lower && "transport" in lower -> sshUnsupportedError(raw)
            "nothing to commit" in lower -> McpToolResult.error(
                errorCode = McpErrorCodes.NOTHING_TO_COMMIT,
                message = raw,
                recoverableByAgent = true
            )
            else -> McpToolResult.error(
                errorCode = McpErrorCodes.INTERNAL_ERROR,
                message = "Ошибка Git: $raw",
                recoverableByAgent = false,
                userMessageHint = raw
            )
        }
    }

    private fun isBranchListCommand(args: List<String>): Boolean {
        if (args.isEmpty()) return true
        val listFlags = setOf("-a", "--all", "-r", "--remotes", "-v", "-vv", "--verbose", "--list", "--show-current")
        return args.all { it in listFlags }
    }

    private fun formatBranchHeader(
        branch: String?,
        detached: Boolean,
        unborn: Boolean,
        ahead: Int,
        behind: Int
    ): String = buildString {
        if (unborn) append("No commits yet on ")
        append(if (detached) "HEAD (detached at ${branch ?: "?"})" else (branch ?: "HEAD"))
        if (ahead > 0 || behind > 0) {
            append(" [")
            val parts = mutableListOf<String>()
            if (ahead > 0) parts.add("ahead $ahead")
            if (behind > 0) parts.add("behind $behind")
            append(parts.joinToString(", "))
            append("]")
        }
    }

    private fun resolveTreeParser(
        repo: Repository,
        reader: org.eclipse.jgit.lib.ObjectReader,
        ref: String
    ): CanonicalTreeParser? {
        val treeId: ObjectId = repo.resolve("$ref^{tree}") ?: return null
        return CanonicalTreeParser().apply { reset(reader, treeId) }
    }

    private fun formatDiff(
        df: DiffFormatter,
        oldTree: org.eclipse.jgit.treewalk.AbstractTreeIterator,
        newTree: org.eclipse.jgit.treewalk.AbstractTreeIterator,
        nameOnly: Boolean,
        nameStatus: Boolean,
        statOnly: Boolean
    ): List<DiffEntry> {
        val entries = df.scan(oldTree, newTree)
        if (!nameOnly && !nameStatus && !statOnly) {
            df.format(entries)
        }
        return entries
    }

    private fun diffEntryPath(e: DiffEntry): String = when (e.changeType) {
        DiffEntry.ChangeType.DELETE -> e.oldPath
        DiffEntry.ChangeType.RENAME -> "${e.oldPath} -> ${e.newPath}"
        else -> e.newPath
    }

    private fun shortChangeType(type: DiffEntry.ChangeType): String = when (type) {
        DiffEntry.ChangeType.ADD -> "A"
        DiffEntry.ChangeType.MODIFY -> "M"
        DiffEntry.ChangeType.DELETE -> "D"
        DiffEntry.ChangeType.RENAME -> "R"
        DiffEntry.ChangeType.COPY -> "C"
    }

    private fun formatCommitChangedFiles(
        repo: Repository,
        commit: RevCommit,
        nameOnly: Boolean,
        nameStatus: Boolean
    ): String {
        val parent = if (commit.parentCount > 0) {
            RevWalk(repo).use { it.parseCommit(commit.getParent(0).id) }
        } else null
        return ByteArrayOutputStream().use { out ->
            DiffFormatter(out).use { df ->
                df.setRepository(repo)
                df.isDetectRenames = true
                val entries = repo.newObjectReader().use { reader ->
                    val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }
                    val oldTree = if (parent != null) {
                        CanonicalTreeParser().apply { reset(reader, parent.tree) }
                    } else EmptyTreeIterator()
                    df.scan(oldTree, newTree)
                }
                entries.joinToString("\n") { e ->
                    when {
                        nameOnly -> diffEntryPath(e)
                        nameStatus -> "${shortChangeType(e.changeType)}\t${diffEntryPath(e)}"
                        else -> {
                            val header = runCatching { df.toFileHeader(e) }.getOrNull()
                            var adds = 0
                            var dels = 0
                            header?.toEditList()?.forEach { edit ->
                                dels += edit.endA - edit.beginA
                                adds += edit.endB - edit.beginB
                            }
                            " ${diffEntryPath(e)} | +$adds -$dels"
                        }
                    }
                }
            }
        }
    }

    private fun formatCommitFullDiff(repo: Repository, commit: RevCommit): String {
        val parent = if (commit.parentCount > 0) {
            RevWalk(repo).use { it.parseCommit(commit.getParent(0).id) }
        } else null
        return ByteArrayOutputStream().use { out ->
            DiffFormatter(out).use { df ->
                df.setRepository(repo)
                df.isDetectRenames = true
                repo.newObjectReader().use { reader ->
                    val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }
                    val oldTree = if (parent != null) {
                        CanonicalTreeParser().apply { reset(reader, parent.tree) }
                    } else EmptyTreeIterator()
                    df.format(oldTree, newTree)
                }
                df.flush()
                out.toString(StandardCharsets.UTF_8.name())
            }
        }
    }

    private fun parseLineRange(spec: String): Pair<Int, Int>? {
        val parts = spec.split(',', limit = 2)
        if (parts.size != 2) return null
        val start = parts[0].trim().toIntOrNull() ?: return null
        val end = parts[1].trim().toIntOrNull() ?: return null
        if (start < 1 || end < start) return null
        return start to end
    }

    private fun parseStashIndex(arg: String?): Int {
        if (arg.isNullOrBlank()) return 0
        val m = Regex("""stash@\{(\d+)\}""").matchEntire(arg.trim())
        if (m != null) return m.groupValues[1].toIntOrNull() ?: 0
        return arg.trim().toIntOrNull() ?: 0
    }

    private fun collectIgnoreNodes(repoDir: File): List<Pair<String, IgnoreNode>> {
        val nodes = mutableListOf<Pair<String, IgnoreNode>>()
        val excludeFile = File(repoDir, ".git/info/exclude")
        if (excludeFile.isFile) {
            val node = IgnoreNode()
            excludeFile.inputStream().use { node.parse(it) }
            nodes.add(".git/info/exclude" to node)
        }
        walkProjectFiles(repoDir, skipIgnoredDirs = false) { file, rel ->
            if (file.name == ".gitignore") {
                val node = IgnoreNode()
                runCatching { file.inputStream().use { node.parse(it) } }
                nodes.add(rel to node)
            }
            true
        }
        return nodes
    }

    private fun checkPathIgnored(
        relPath: String,
        isDir: Boolean,
        ignoreNodes: List<Pair<String, IgnoreNode>>
    ): String? {
        var matchedSource: String? = null
        // Check from shallowest (.git/info/exclude, root .gitignore) to deepest subdirectory .gitignore
        val sorted = ignoreNodes.sortedBy { (src, _) ->
            if (src.startsWith(".git/")) -1 else src.count { it == '/' }
        }
        for ((src, node) in sorted) {
            val baseDir = if (src.startsWith(".git/") || !src.contains('/')) "" else src.substringBeforeLast('/') + "/"
            if (baseDir.isNotEmpty() && !relPath.startsWith(baseDir)) continue
            val localRel = if (baseDir.isEmpty()) relPath else relPath.removePrefix(baseDir)

            // Check parent directories as well (ignoring a directory ignores everything inside)
            val segments = localRel.split('/')
            var prefix = ""
            var decided = false
            for (idx in segments.indices) {
                prefix = if (idx == 0) segments[idx] else "$prefix/${segments[idx]}"
                val segIsDir = if (idx < segments.lastIndex) true else isDir
                when (node.isIgnored(prefix, segIsDir)) {
                    IgnoreNode.MatchResult.IGNORED -> {
                        matchedSource = src
                        decided = true
                        break
                    }
                    IgnoreNode.MatchResult.NOT_IGNORED -> {
                        matchedSource = null
                        decided = true
                        break
                    }
                    else -> {}
                }
            }
            if (decided) continue
        }
        return matchedSource
    }

    private fun buildCredentialsProvider(credentials: GitCredentials?): CredentialsProvider? {
        if (credentials == null || credentials.tokenOrPassword.isBlank()) return null
        val user = credentials.username.trim().ifEmpty { "x-access-token" }
        return UsernamePasswordCredentialsProvider(user, credentials.tokenOrPassword.trim())
    }

    companion object {
        private val SUPPORTED_SUBCOMMANDS = setOf(
            "init", "status", "diff", "log", "show", "blame", "rev-parse", "ls-files", "check-ignore",
            "add", "reset", "restore", "rm", "mv", "clean", "commit",
            "branch", "checkout", "switch", "merge", "rebase", "cherry-pick", "revert",
            "stash", "tag", "remote", "fetch", "pull", "push"
        )

        private val UNSUPPORTED_SUBCOMMANDS = setOf(
            "lfs", "worktree", "bisect", "submodule", "filter-branch", "gc", "notes",
            "svn", "archive", "bundle", "daemon", "rerere", "instaweb", "send-email",
            "credential", "fsck", "prune", "reflog", "format-patch", "am", "apply",
            "replace", "sparse-checkout", "maintenance", "whatchanged", "shortlog"
        )
    }

    // endregion
}
