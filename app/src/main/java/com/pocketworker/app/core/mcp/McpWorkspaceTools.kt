package com.pocketworker.app.core.mcp

import com.pocketworker.app.core.git.GitEngine
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.git.RepoGroupDiscoveryService
import com.pocketworker.app.core.git.WorkspaceProject
import com.pocketworker.app.core.storage.CredentialStore
import com.pocketworker.app.core.storage.ProjectRegistry
import com.pocketworker.app.ui.ai.AiLocationContext
import com.pocketworker.app.ui.common.formatGitTransportError
import com.pocketworker.app.ui.projectdetail.ProjectDetailViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class ResolvedRepo(
    val project: WorkspaceProject,
    val repo: RepoEntry,
    val dir: File
)

internal class McpWorkspaceResolver(
    private val registry: ProjectRegistry
) {
    fun resolveProject(projectId: String): Pair<WorkspaceProject?, McpToolResult?> {
        val project = registry.findProjectById(projectId)
            ?: return null to McpToolResult.error(
                errorCode = McpErrorCodes.PROJECT_NOT_FOUND,
                message = "Проект с id '$projectId' не найден.",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Вызови workspace_list_projects, чтобы получить актуальные projectId")
            )
        return project to null
    }

    fun resolveRepo(projectId: String, repoId: String): Pair<ResolvedRepo?, McpToolResult?> {
        val (project, projectErr) = resolveProject(projectId)
        if (projectErr != null || project == null) return null to projectErr

        val repo = registry.findRepository(projectId, repoId)
            ?: return null to McpToolResult.error(
                errorCode = McpErrorCodes.REPO_NOT_FOUND,
                message = "Репозиторий с id '$repoId' не найден в проекте '${project.name}'.",
                recoverableByAgent = true,
                suggestedAlternatives = listOf("Вызови workspace_list_repos(projectId='$projectId')")
            )
        val dir = File(repo.directoryPath)
        if (!dir.exists() || !dir.isDirectory) {
            return null to McpToolResult.error(
                errorCode = McpErrorCodes.REPO_NOT_FOUND,
                message = "Папка репозитория '${repo.name}' отсутствует на диске.",
                recoverableByAgent = false,
                userMessageHint = "Папка репозитория '${repo.name}' была удалена с устройства."
            )
        }
        return ResolvedRepo(project, repo, dir) to null
    }
}

internal fun isSshGitUrl(url: String): Boolean {
    val trimmed = url.trim().lowercase()
    return trimmed.startsWith("ssh://") ||
        trimmed.startsWith("git@") ||
        Regex("^[a-z0-9._-]+@[a-z0-9._-]+:.+").matches(trimmed)
}

internal fun createWorkspaceTools(
    registry: ProjectRegistry,
    gitEngine: GitEngine,
    credentialStore: CredentialStore,
    groupDiscoveryService: RepoGroupDiscoveryService,
    resolver: McpWorkspaceResolver
): List<McpTool> = listOf(
    WorkspaceGetContextTool(registry, gitEngine),
    WorkspaceListProjectsTool(registry, gitEngine),
    WorkspaceCreateProjectTool(registry),
    WorkspaceListReposTool(registry, gitEngine, resolver),
    WorkspaceCreateRepoTool(registry, gitEngine, resolver),
    WorkspaceCloneRepoTool(registry, gitEngine, credentialStore, resolver),
    WorkspaceDiscoverGroupReposTool(credentialStore, groupDiscoveryService)
)

private class WorkspaceGetContextTool(
    private val registry: ProjectRegistry,
    private val gitEngine: GitEngine
) : McpTool {
    override val definition = McpToolDefinition(
        name = "workspace_get_context",
        description = "Возвращает текущее расположение пользователя в приложении: все проекты, открытый проект, репозиторий (с веткой и статусом) или открытый файл.",
        inputSchema = McpSchemaBuilder.objectSchema(),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val loc = context.currentLocationProvider()
        return McpToolResult.success {
            when (loc) {
                is AiLocationContext.AllProjects -> {
                    put("scope", "all_projects")
                    put("projectCount", registry.listProjects().size)
                }
                is AiLocationContext.ProjectFolder -> {
                    put("scope", "project")
                    put("projectId", loc.projectId)
                    put("projectName", loc.projectName)
                    put("repoCount", registry.listRepositories(loc.projectId).size)
                }
                is AiLocationContext.Repository -> {
                    put("scope", "repository")
                    put("projectId", loc.projectId)
                    put("projectName", loc.projectName)
                    put("repoId", loc.repoId)
                    put("repoName", loc.repoName)
                    put("currentDir", loc.currentDir)
                    attachRepoBrief(loc.projectId, loc.repoId)
                }
                is AiLocationContext.File -> {
                    put("scope", "file")
                    put("projectId", loc.projectId)
                    put("projectName", loc.projectName)
                    put("repoId", loc.repoId)
                    put("repoName", loc.repoName)
                    put("filePath", loc.filePath)
                    attachRepoBrief(loc.projectId, loc.repoId)
                }
            }
        }
    }

    private fun JSONObject.attachRepoBrief(projectId: String, repoId: String) {
        val repo = registry.findRepository(projectId, repoId) ?: return
        val dir = File(repo.directoryPath)
        if (!dir.exists()) return
        runCatching { gitEngine.getStatus(dir) }.onSuccess { status ->
            put("isGitRepo", status.isGitRepo)
            put("currentBranch", status.currentBranch ?: JSONObject.NULL)
            put("hasUncommittedChanges", status.hasChanges)
            put("aheadCount", status.aheadCount)
            put("behindCount", status.behindCount)
        }
    }
}

private class WorkspaceListProjectsTool(
    private val registry: ProjectRegistry,
    private val gitEngine: GitEngine
) : McpTool {
    override val definition = McpToolDefinition(
        name = "workspace_list_projects",
        description = "Возвращает список всех проектов-папок в песочнице приложения с количеством репозиториев и числом изменённых репозиториев.",
        inputSchema = McpSchemaBuilder.objectSchema(),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projects = registry.listProjects()
        val arr = JSONArray()
        for (project in projects) {
            val repos = registry.listRepositories(project.id)
            val dirtyCount = repos.count { repo ->
                val summary = gitEngine.getRepoSummary(repo)
                summary.changedFilesCount + summary.untrackedCount > 0
            }
            arr.put(
                JSONObject().apply {
                    put("projectId", project.id)
                    put("name", project.name)
                    put("description", project.description)
                    put("repoCount", repos.size)
                    put("dirtyRepoCount", dirtyCount)
                    put("repoNames", JSONArray(repos.map { it.name }))
                }
            )
        }
        return McpToolResult.success {
            put("count", projects.size)
            put("projects", arr)
        }
    }
}

private class WorkspaceCreateProjectTool(
    private val registry: ProjectRegistry
) : McpTool {
    override val definition = McpToolDefinition(
        name = "workspace_create_project",
        description = "Создаёт новый проект-папку для группировки репозиториев.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("name"),
            properties = {
                put("name", McpSchemaBuilder.stringProp("Название нового проекта"))
                put("description", McpSchemaBuilder.stringProp("Краткое описание проекта (необязательно)"))
            }
        ),
        defaultTier = McpPermissionTier.CONTROLLED_WRITE
    )

    override fun buildConfirmationSummary(arguments: JSONObject): String =
        "Создать новый проект «${arguments.optString("name")}»"

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val name = arguments.requireNonBlankString("name")
        val description = arguments.optString("description", "")
        val created = registry.createProject(name, description)
        return McpToolResult.success {
            put("projectId", created.id)
            put("name", created.name)
            put("description", created.description)
        }
    }
}

private class WorkspaceListReposTool(
    private val registry: ProjectRegistry,
    private val gitEngine: GitEngine,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "workspace_list_repos",
        description = "Возвращает список репозиториев внутри указанного проекта с краткой Git-сводкой (ветка, изменения, ahead/behind, remoteUrl).",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
            }
        ),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val (project, err) = resolver.resolveProject(projectId)
        if (err != null || project == null) return err!!

        val repos = registry.listRepositories(projectId)
        val arr = JSONArray()
        for (repo in repos) {
            val card = gitEngine.getRepoSummary(repo)
            arr.put(
                JSONObject().apply {
                    put("repoId", repo.id)
                    put("name", repo.name)
                    put("isGitRepo", card.isGitRepo)
                    put("currentBranch", card.currentBranch ?: JSONObject.NULL)
                    put("isUnborn", card.isUnborn)
                    put("changedFilesCount", card.changedFilesCount)
                    put("untrackedCount", card.untrackedCount)
                    put("aheadCount", card.aheadCount)
                    put("behindCount", card.behindCount)
                    put("remoteUrl", card.remoteUrl ?: JSONObject.NULL)
                    put("lastCommitMessage", card.lastCommitMessage ?: JSONObject.NULL)
                }
            )
        }
        return McpToolResult.success {
            put("projectId", project.id)
            put("projectName", project.name)
            put("count", repos.size)
            put("repositories", arr)
        }
    }
}

private class WorkspaceCreateRepoTool(
    private val registry: ProjectRegistry,
    private val gitEngine: GitEngine,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "workspace_create_repo",
        description = "Создаёт новый локальный репозиторий в проекте (с опциональным git init, начальной веткой, README и remoteUrl).",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "name"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("name", McpSchemaBuilder.stringProp("Имя нового репозитория (папки)"))
                put("initGit", McpSchemaBuilder.boolProp("Инициализировать Git (по умолчанию true)"))
                put("initialBranch", McpSchemaBuilder.stringProp("Начальная ветка (по умолчанию main)"))
                put("createReadme", McpSchemaBuilder.boolProp("Создать README.md и первый коммит (по умолчанию true)"))
                put("remoteUrl", McpSchemaBuilder.stringProp("HTTPS URL удалённого репозитория (необязательно)"))
            }
        ),
        defaultTier = McpPermissionTier.CONTROLLED_WRITE
    )

    override fun buildConfirmationSummary(arguments: JSONObject): String =
        "Создать репозиторий «${arguments.optString("name")}»"

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val (_, err) = resolver.resolveProject(projectId)
        if (err != null) return err

        val name = arguments.requireNonBlankString("name")
        val initGit = arguments.optBoolean("initGit", true)
        val initialBranch = arguments.optNonBlankString("initialBranch") ?: "main"
        val createReadme = arguments.optBoolean("createReadme", true)
        val remoteUrl = arguments.optNonBlankString("remoteUrl")

        if (remoteUrl != null && isSshGitUrl(remoteUrl)) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.SSH_TRANSPORT_UNSUPPORTED,
                message = "SSH-ссылка '$remoteUrl' не поддерживается на Android.",
                recoverableByAgent = false,
                reason = "Мобильный JGit-клиент работает только по HTTPS с Personal Access Token (PAT).",
                userMessageHint = "Укажите HTTPS-ссылку репозитория (https://...) вместо SSH."
            )
        }

        val (repoId, targetDir) = registry.allocateRepositoryDir(projectId, name)
        gitEngine.initProject(
            targetDir = targetDir,
            initialBranch = initialBranch,
            initGit = initGit,
            createReadmeAndCommit = createReadme,
            projectName = name,
            remoteUrl = remoteUrl
        )
        val entry = RepoEntry(
            id = repoId,
            projectId = projectId,
            name = name,
            directoryPath = targetDir.absolutePath,
            createdAt = System.currentTimeMillis(),
            remoteUrl = remoteUrl
        )
        registry.registerRepository(entry)

        return McpToolResult.success {
            put("repoId", entry.id)
            put("projectId", projectId)
            put("name", entry.name)
            put("initGit", initGit)
            put("initialBranch", initialBranch)
            put("remoteUrl", remoteUrl ?: JSONObject.NULL)
        }
    }
}

private class WorkspaceCloneRepoTool(
    private val registry: ProjectRegistry,
    private val gitEngine: GitEngine,
    private val credentialStore: CredentialStore,
    private val resolver: McpWorkspaceResolver
) : McpTool {
    override val definition = McpToolDefinition(
        name = "workspace_clone_repo",
        description = "Клонирует удалённый Git-репозиторий по HTTPS в выбранный проект. Сохранённый PAT подставляется автоматически без передачи токена модели.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("projectId", "url"),
            properties = {
                put("projectId", McpSchemaBuilder.stringProp("ID проекта"))
                put("url", McpSchemaBuilder.stringProp("HTTPS URL Git-репозитория"))
                put("customName", McpSchemaBuilder.stringProp("Имя папки репозитория (необязательно)"))
                put("branch", McpSchemaBuilder.stringProp("Конкретная ветка для клонирования (необязательно)"))
                put("useStoredAuth", McpSchemaBuilder.boolProp("Использовать сохранённый токен проекта/по умолчанию (по умолчанию true)"))
            }
        ),
        defaultTier = McpPermissionTier.ALWAYS_CONFIRM
    )

    override fun buildConfirmationSummary(arguments: JSONObject): String =
        "Клонировать репозиторий ${arguments.optString("url")}"

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val projectId = arguments.requireNonBlankString("projectId")
        val (_, err) = resolver.resolveProject(projectId)
        if (err != null) return err

        val url = arguments.requireNonBlankString("url")
        if (isSshGitUrl(url)) {
            return McpToolResult.error(
                errorCode = McpErrorCodes.SSH_TRANSPORT_UNSUPPORTED,
                message = "Клонирование по SSH ('$url') не поддерживается в мобильном клиенте.",
                recoverableByAgent = false,
                reason = "PocketWorker на Android поддерживает только HTTPS-транспорт с Personal Access Token.",
                userMessageHint = "Для клонирования используйте HTTPS-ссылку (https://...) и при необходимости добавьте PAT в настройках."
            )
        }

        val repoName = arguments.optNonBlankString("customName")
            ?: ProjectDetailViewModel.deriveRepoNameFromUrl(url)
        val branch = arguments.optNonBlankString("branch")
        val useStoredAuth = arguments.optBoolean("useStoredAuth", true)
        val creds = if (useStoredAuth) credentialStore.resolveForRepo(projectId, null) else null

        val (repoId, targetDir) = registry.allocateRepositoryDir(projectId, repoName)
        return runCatching {
            val status = gitEngine.cloneRepository(
                url = url,
                targetDir = targetDir,
                branch = branch,
                credentials = creds
            )
            val entry = RepoEntry(
                id = repoId,
                projectId = projectId,
                name = repoName,
                directoryPath = targetDir.absolutePath,
                createdAt = System.currentTimeMillis(),
                remoteUrl = url
            )
            registry.registerRepository(entry)
            McpToolResult.success {
                put("repoId", entry.id)
                put("projectId", projectId)
                put("name", entry.name)
                put("currentBranch", status.currentBranch ?: JSONObject.NULL)
                put("remoteUrl", url)
            }
        }.getOrElse { ex ->
            targetDir.deleteRecursively()
            mapTransportFailure(ex)
        }
    }
}

private class WorkspaceDiscoverGroupReposTool(
    private val credentialStore: CredentialStore,
    private val groupDiscoveryService: RepoGroupDiscoveryService
) : McpTool {
    override val definition = McpToolDefinition(
        name = "workspace_discover_group_repos",
        description = "Возвращает список доступных репозиториев по ссылке на группу GitLab или организацию/профиль GitHub.",
        inputSchema = McpSchemaBuilder.objectSchema(
            required = listOf("url"),
            properties = {
                put("url", McpSchemaBuilder.stringProp("HTTPS URL группы GitLab или организации/профиля GitHub"))
                put("projectId", McpSchemaBuilder.stringProp("ID проекта для подбора сохранённого токена (необязательно)"))
                put("useStoredAuth", McpSchemaBuilder.boolProp("Использовать сохранённый токен (по умолчанию true)"))
            }
        ),
        defaultTier = McpPermissionTier.AUTO
    )

    override suspend fun execute(arguments: JSONObject, context: McpExecutionContext): McpToolResult {
        val url = arguments.requireNonBlankString("url")
        val projectId = arguments.optNonBlankString("projectId")
        val useStoredAuth = arguments.optBoolean("useStoredAuth", true)
        val creds = if (useStoredAuth) {
            if (projectId != null) credentialStore.resolveForRepo(projectId, null)
            else credentialStore.getDefaultCredentials()
        } else null

        return runCatching {
            val repos = groupDiscoveryService.discoverRepositories(url, creds)
            val arr = JSONArray()
            for (r in repos) {
                arr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("name", r.name)
                        put("fullName", r.fullName)
                        put("cloneUrl", r.cloneUrl)
                        put("defaultBranch", r.defaultBranch ?: JSONObject.NULL)
                        put("description", r.description)
                        put("isPrivate", r.isPrivate)
                    }
                )
            }
            McpToolResult.success {
                put("count", repos.size)
                put("repositories", arr)
            }
        }.getOrElse { ex -> mapTransportFailure(ex) }
    }
}

internal fun mapTransportFailure(err: Throwable): McpToolResult {
    val raw = err.localizedMessage ?: err.message ?: err.javaClass.simpleName
    val lower = raw.lowercase()
    return when {
        "401" in lower || "not authorized" in lower || "authentication is required" in lower -> McpToolResult.error(
            errorCode = McpErrorCodes.AUTH_REQUIRED,
            message = formatGitTransportError(err),
            recoverableByAgent = false,
            reason = "Сервер требует авторизацию (PAT).",
            userMessageHint = "Добавьте или обновите Personal Access Token в «Настройки → Общие» для репозитория, проекта или по умолчанию."
        )
        "403" in lower || "forbidden" in lower -> McpToolResult.error(
            errorCode = McpErrorCodes.AUTH_FORBIDDEN,
            message = formatGitTransportError(err),
            recoverableByAgent = false,
            reason = "Недостаточно прав у текущего токена (403).",
            userMessageHint = "Проверьте права доступа (scopes) вашего Personal Access Token к этому репозиторию."
        )
        "unknownhost" in lower || "connect" in lower || "timed out" in lower || "network" in lower -> McpToolResult.error(
            errorCode = McpErrorCodes.NETWORK_UNREACHABLE,
            message = formatGitTransportError(err),
            recoverableByAgent = false,
            reason = "Не удалось установить сетевое соединение с Git-сервером.",
            userMessageHint = "Проверьте подключение к интернету и доступность сервера."
        )
        else -> McpToolResult.error(
            errorCode = McpErrorCodes.INTERNAL_ERROR,
            message = formatGitTransportError(err),
            recoverableByAgent = false,
            userMessageHint = formatGitTransportError(err)
        )
    }
}
