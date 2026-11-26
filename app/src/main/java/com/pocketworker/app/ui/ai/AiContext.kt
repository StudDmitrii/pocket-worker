package com.pocketworker.app.ui.ai

/**
 * Describes where the user currently is in the workspace when opening the AI assistant chat.
 */
sealed interface AiLocationContext {
    data object AllProjects : AiLocationContext

    data class ProjectFolder(
        val projectId: String = "",
        val projectName: String = ""
    ) : AiLocationContext

    data class Repository(
        val projectId: String = "",
        val projectName: String = "",
        val repoId: String = "",
        val repoName: String = "",
        val currentDir: String = ""
    ) : AiLocationContext

    data class File(
        val projectId: String = "",
        val projectName: String = "",
        val repoId: String = "",
        val repoName: String = "",
        val filePath: String
    ) : AiLocationContext
}

/**
 * Represents an AI chat session entry shown in the chat history drawer.
 */
data class AiChatSession(
    val id: String,
    val title: String = DEFAULT_TITLE,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val preview: String = "",
    val lastContextKey: String = ""
) {
    companion object {
        const val DEFAULT_TITLE = "Новый чат"
    }
}

/**
 * Returns a stable identity key for [AiLocationContext] so the chat can detect
 * when the user navigated to a different project, repository, directory, or file.
 */
fun AiLocationContext.stableContextKey(): String = when (this) {
    is AiLocationContext.AllProjects -> "all_projects"
    is AiLocationContext.ProjectFolder -> "project:${projectId.trim()}"
    is AiLocationContext.Repository -> "repo:${projectId.trim()}:${repoId.trim()}:${currentDir.trim('/')}"
    is AiLocationContext.File -> "file:${projectId.trim()}:${repoId.trim()}:${filePath.trim('/')}"
}

/**
 * Builds the context-aware welcome message shown by the AI assistant upon entering chat.
 */
fun buildAiGreetingMessage(context: AiLocationContext): String {
    return when (context) {
        is AiLocationContext.AllProjects ->
            "Привет! Чем могу помочь? Сейчас ты находишься в папке со всеми проектами, я их вижу."

        is AiLocationContext.ProjectFolder -> {
            if (context.projectName.isNotBlank()) {
                "Привет! Чем могу помочь? Сейчас ты находишься в папке проекта «${context.projectName}», я вижу его репозитории."
            } else {
                "Привет! Чем могу помочь? Сейчас ты находишься в папке проекта, я вижу его репозитории."
            }
        }

        is AiLocationContext.Repository -> {
            val cleanDir = context.currentDir.trim('/')
            val cleanRepo = context.repoName.trim()
            if (cleanDir.isNotEmpty()) {
                if (cleanRepo.isNotEmpty()) {
                    "Привет! Чем могу помочь? Сейчас ты находишься в папке «$cleanDir» репозитория «$cleanRepo», я её вижу."
                } else {
                    "Привет! Чем могу помочь? Сейчас ты находишься в папке «$cleanDir» репозитория, я её вижу."
                }
            } else {
                if (cleanRepo.isNotEmpty()) {
                    "Привет! Чем могу помочь? Сейчас ты находишься в папке с репозиторием «$cleanRepo», я его вижу."
                } else {
                    "Привет! Чем могу помочь? Сейчас ты находишься в папке с репозиторием, я его вижу."
                }
            }
        }

        is AiLocationContext.File -> {
            val cleanFile = context.filePath.trim()
            val cleanRepo = context.repoName.trim()
            if (cleanRepo.isNotEmpty()) {
                "Привет! Чем могу помочь? Сейчас ты находишься в файле «$cleanFile» репозитория «$cleanRepo», я его вижу."
            } else {
                "Привет! Чем могу помочь? Сейчас ты находишься в файле «$cleanFile», я его вижу."
            }
        }
    }
}

/**
 * Builds a concise path/location subtitle for the AI chat top bar.
 */
fun formatAiContextSubtitle(context: AiLocationContext): String {
    return when (context) {
        is AiLocationContext.AllProjects -> "Все проекты"
        is AiLocationContext.ProjectFolder ->
            context.projectName.takeIf { it.isNotBlank() }?.let { "Проект: $it" } ?: "Папка проекта"
        is AiLocationContext.Repository -> {
            val dir = context.currentDir.trim('/')
            val repo = context.repoName.trim()
            when {
                repo.isNotEmpty() && dir.isNotEmpty() -> "$repo/$dir"
                repo.isNotEmpty() -> "Репозиторий: $repo"
                dir.isNotEmpty() -> dir
                else -> "Репозиторий"
            }
        }
        is AiLocationContext.File -> {
            val cleanPath = context.filePath.trim('/')
            val repo = context.repoName.trim()
            when {
                repo.isNotEmpty() && cleanPath.isNotEmpty() -> "$repo/$cleanPath"
                cleanPath.isNotEmpty() -> cleanPath
                else -> "Файл"
            }
        }
    }
}
