package com.pocketworker.app.ui.ai

import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.i18n.AppStrings
import com.pocketworker.app.core.i18n.StringsEn
import com.pocketworker.app.core.i18n.StringsRu

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
    fun isUntitledDefault(): Boolean =
        title == DEFAULT_TITLE || title == StringsRu.aiNewChat || title == StringsEn.aiNewChat

    fun localizedTitle(s: AppStrings = AppLocale.current): String =
        if (isUntitledDefault()) s.aiNewChat else title

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
fun buildAiGreetingMessage(
    context: AiLocationContext,
    s: AppStrings = AppLocale.current
): String {
    return when (context) {
        is AiLocationContext.AllProjects -> s.aiGreetingAllProjects

        is AiLocationContext.ProjectFolder -> {
            if (context.projectName.isNotBlank()) {
                s.aiGreetingProjectNamed(context.projectName)
            } else {
                s.aiGreetingProjectUnnamed
            }
        }

        is AiLocationContext.Repository -> {
            val cleanDir = context.currentDir.trim('/')
            val cleanRepo = context.repoName.trim()
            if (cleanDir.isNotEmpty()) {
                if (cleanRepo.isNotEmpty()) {
                    s.aiGreetingRepoSubdirNamed(cleanDir, cleanRepo)
                } else {
                    s.aiGreetingRepoSubdirUnnamed(cleanDir)
                }
            } else {
                if (cleanRepo.isNotEmpty()) {
                    s.aiGreetingRepoRootNamed(cleanRepo)
                } else {
                    s.aiGreetingRepoRootUnnamed
                }
            }
        }

        is AiLocationContext.File -> {
            val cleanFile = context.filePath.trim()
            val cleanRepo = context.repoName.trim()
            if (cleanRepo.isNotEmpty()) {
                s.aiGreetingFileWithRepo(cleanFile, cleanRepo)
            } else {
                s.aiGreetingFileWithoutRepo(cleanFile)
            }
        }
    }
}

/**
 * Builds the centered welcome prompt shown in an empty/new AI chat above the input field.
 */
fun buildAiEmptyStatePrompt(
    context: AiLocationContext,
    s: AppStrings = AppLocale.current
): String {
    return when (context) {
        is AiLocationContext.AllProjects -> s.aiEmptyPromptAllProjects

        is AiLocationContext.ProjectFolder -> {
            val cleanProject = context.projectName.trim()
            if (cleanProject.isNotEmpty()) {
                s.aiEmptyPromptProjectNamed(cleanProject)
            } else {
                s.aiEmptyPromptProjectUnnamed
            }
        }

        is AiLocationContext.Repository -> {
            val cleanDir = context.currentDir.trim('/')
            val cleanRepo = context.repoName.trim()
            when {
                cleanDir.isNotEmpty() && cleanRepo.isNotEmpty() ->
                    s.aiEmptyPromptRepoSubdirNamed(cleanDir, cleanRepo)
                cleanRepo.isNotEmpty() ->
                    s.aiEmptyPromptRepoNamed(cleanRepo)
                cleanDir.isNotEmpty() ->
                    s.aiEmptyPromptDirOnly(cleanDir)
                else ->
                    s.aiEmptyPromptRepoUnnamed
            }
        }

        is AiLocationContext.File -> {
            val cleanPath = context.filePath.trim('/')
            val fileName = cleanPath.substringAfterLast('/')
            if (fileName.isNotEmpty()) {
                s.aiEmptyPromptFileNamed(fileName)
            } else {
                s.aiEmptyPromptFileUnnamed
            }
        }
    }
}

/**
 * Builds a concise path/location subtitle for the AI chat top bar.
 */
fun formatAiContextSubtitle(
    context: AiLocationContext,
    s: AppStrings = AppLocale.current
): String {
    return when (context) {
        is AiLocationContext.AllProjects -> s.aiSubtitleAllProjects
        is AiLocationContext.ProjectFolder ->
            context.projectName.takeIf { it.isNotBlank() }?.let { s.aiSubtitleProject(it) } ?: s.aiSubtitleProjectFolder
        is AiLocationContext.Repository -> {
            val dir = context.currentDir.trim('/')
            val repo = context.repoName.trim()
            when {
                repo.isNotEmpty() && dir.isNotEmpty() -> "$repo/$dir"
                repo.isNotEmpty() -> s.aiSubtitleRepo(repo)
                dir.isNotEmpty() -> dir
                else -> s.aiSubtitleRepoUnnamed
            }
        }
        is AiLocationContext.File -> {
            val cleanPath = context.filePath.trim('/')
            val repo = context.repoName.trim()
            when {
                repo.isNotEmpty() && cleanPath.isNotEmpty() -> "$repo/$cleanPath"
                cleanPath.isNotEmpty() -> cleanPath
                else -> s.aiSubtitleFile
            }
        }
    }
}
