package com.pocketworker.app.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiContextTest {

    @Test
    fun `buildAiGreetingMessage returns expected greeting for AllProjects`() {
        val greeting = buildAiGreetingMessage(AiLocationContext.AllProjects)
        assertEquals(
            "Привет! Чем могу помочь? Сейчас ты находишься в папке со всеми проектами, я их вижу.",
            greeting
        )
    }

    @Test
    fun `buildAiGreetingMessage handles ProjectFolder with and without name`() {
        val named = buildAiGreetingMessage(
            AiLocationContext.ProjectFolder(projectId = "p1", projectName = "Shop Platform")
        )
        assertEquals(
            "Привет! Чем могу помочь? Сейчас ты находишься в папке проекта «Shop Platform», я вижу его репозитории.",
            named
        )

        val unnamed = buildAiGreetingMessage(
            AiLocationContext.ProjectFolder(projectId = "p1", projectName = "")
        )
        assertEquals(
            "Привет! Чем могу помочь? Сейчас ты находишься в папке проекта, я вижу его репозитории.",
            unnamed
        )
    }

    @Test
    fun `buildAiGreetingMessage handles Repository root and subdirectory`() {
        val rootGreeting = buildAiGreetingMessage(
            AiLocationContext.Repository(
                projectId = "p1",
                projectName = "Shop",
                repoId = "r1",
                repoName = "backend-api",
                currentDir = ""
            )
        )
        assertEquals(
            "Привет! Чем могу помочь? Сейчас ты находишься в папке с репозиторием «backend-api», я его вижу.",
            rootGreeting
        )

        val subDirGreeting = buildAiGreetingMessage(
            AiLocationContext.Repository(
                projectId = "p1",
                projectName = "Shop",
                repoId = "r1",
                repoName = "backend-api",
                currentDir = "src/main/kotlin"
            )
        )
        assertEquals(
            "Привет! Чем могу помочь? Сейчас ты находишься в папке «src/main/kotlin» репозитория «backend-api», я её вижу.",
            subDirGreeting
        )
    }

    @Test
    fun `buildAiGreetingMessage handles File with and without repo name`() {
        val withRepo = buildAiGreetingMessage(
            AiLocationContext.File(
                projectId = "p1",
                projectName = "Shop",
                repoId = "r1",
                repoName = "client",
                filePath = "app/src/MainActivity.kt"
            )
        )
        assertEquals(
            "Привет! Чем могу помочь? Сейчас ты находишься в файле «app/src/MainActivity.kt» репозитория «client», я его вижу.",
            withRepo
        )

        val withoutRepo = buildAiGreetingMessage(
            AiLocationContext.File(
                filePath = "README.md"
            )
        )
        assertEquals(
            "Привет! Чем могу помочь? Сейчас ты находишься в файле «README.md», я его вижу.",
            withoutRepo
        )
    }

    @Test
    fun `formatAiContextSubtitle uses slash paths for repository directories and files`() {
        assertEquals("Все проекты", formatAiContextSubtitle(AiLocationContext.AllProjects))
        assertEquals(
            "Проект: Shop",
            formatAiContextSubtitle(AiLocationContext.ProjectFolder(projectName = "Shop"))
        )
        assertEquals(
            "Репозиторий: client",
            formatAiContextSubtitle(AiLocationContext.Repository(repoName = "client"))
        )
        assertEquals(
            "client/src/ui",
            formatAiContextSubtitle(
                AiLocationContext.Repository(repoName = "client", currentDir = "src/ui")
            )
        )
        assertEquals(
            "client/src/App.kt",
            formatAiContextSubtitle(
                AiLocationContext.File(repoName = "client", filePath = "src/App.kt")
            )
        )
        assertEquals(
            "README.md",
            formatAiContextSubtitle(
                AiLocationContext.File(repoName = "", filePath = "/README.md")
            )
        )
    }

    @Test
    fun `default chat session title is Новый чат`() {
        val session = AiChatSession(id = "1")
        assertEquals("Новый чат", session.title)
    }

    @Test
    fun `buildAiEmptyStatePrompt formats current location cleanly`() {
        assertEquals(
            "Над чем поработаем в проектах?",
            buildAiEmptyStatePrompt(AiLocationContext.AllProjects)
        )
        assertEquals(
            "Над чем поработаем в проекте «Shop Platform»?",
            buildAiEmptyStatePrompt(AiLocationContext.ProjectFolder(projectId = "p1", projectName = "Shop Platform"))
        )
        assertEquals(
            "Над чем поработаем в проекте?",
            buildAiEmptyStatePrompt(AiLocationContext.ProjectFolder(projectId = "p1", projectName = "  "))
        )
        assertEquals(
            "Над чем поработаем в репозитории «backend-api»?",
            buildAiEmptyStatePrompt(AiLocationContext.Repository(repoName = "backend-api", currentDir = ""))
        )
        assertEquals(
            "Над чем поработаем в папке «src/ui» репозитория «client»?",
            buildAiEmptyStatePrompt(AiLocationContext.Repository(repoName = "client", currentDir = "src/ui"))
        )
        assertEquals(
            "Над чем поработаем в файле «MainActivity.kt»?",
            buildAiEmptyStatePrompt(AiLocationContext.File(repoName = "client", filePath = "app/src/MainActivity.kt"))
        )
        assertEquals(
            "Над чем поработаем в файле?",
            buildAiEmptyStatePrompt(AiLocationContext.File(filePath = ""))
        )
    }
}
