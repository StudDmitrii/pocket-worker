package com.pocketworker.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAgentBackgroundCoordinatorTest {

    private class FakeNotificationPresenter : AiAgentNotificationPresenter {
        val ongoingHistory = mutableListOf<Triple<String, String, String>>()
        var hideOngoingCount = 0
        val finishedHistory = mutableListOf<FinishedNotificationRecord>()
        val cancelledFinishedChatIds = mutableListOf<String>()

        data class FinishedNotificationRecord(
            val chatId: String,
            val chatTitle: String,
            val title: String,
            val contentText: String
        )

        override fun showOrUpdateOngoing(chatId: String, chatTitle: String, statusText: String) {
            ongoingHistory.add(Triple(chatId, chatTitle, statusText))
        }

        override fun hideOngoing() {
            hideOngoingCount++
        }

        override fun showFinished(chatId: String, chatTitle: String, title: String, contentText: String) {
            finishedHistory.add(FinishedNotificationRecord(chatId, chatTitle, title, contentText))
        }

        override fun cancelFinished(chatId: String) {
            cancelledFinishedChatIds.add(chatId)
        }
    }

    @Test
    fun `does not show ongoing or finished notification while app stays in foreground`() {
        val presenter = FakeNotificationPresenter()
        val coordinator = AiAgentBackgroundCoordinator(presenter)

        coordinator.onAgentStarted("chat-1", "Новый чат", "Думает...")
        coordinator.onAgentStatusChanged("chat-1", "Новый чат", "Читаю файл...")
        coordinator.onAgentStatusChanged("chat-1", "Новый чат", "Отвечает...")
        coordinator.onAgentFinished("chat-1", "Новый чат", wasCancelled = false, hadError = false, previewText = "Готово!")

        assertTrue(presenter.ongoingHistory.isEmpty())
        assertTrue(presenter.finishedHistory.isEmpty())
    }

    @Test
    fun `shows live loader status while backgrounded, hides on foreground return, and notifies on background completion`() {
        val presenter = FakeNotificationPresenter()
        val coordinator = AiAgentBackgroundCoordinator(presenter)

        // Agent starts in foreground -> no notification yet
        coordinator.onAgentStarted("chat-42", "Фикс авторизации", "Думает...")
        assertTrue(presenter.ongoingHistory.isEmpty())

        // User minimizes app -> ongoing notification immediately appears with current loader status
        coordinator.onAppMovedToBackground()
        assertEquals(listOf(Triple("chat-42", "Фикс авторизации", "Думает...")), presenter.ongoingHistory)

        // Status transitions update notification live; duplicate statuses are deduplicated
        coordinator.onAgentStatusChanged("chat-42", "Фикс авторизации", "Читаю файл...")
        coordinator.onAgentStatusChanged("chat-42", "Фикс авторизации", "Отвечает...")
        coordinator.onAgentStatusChanged("chat-42", "Фикс авторизации", "Отвечает...") // duplicate ignored
        assertEquals(3, presenter.ongoingHistory.size)
        assertEquals("Читаю файл...", presenter.ongoingHistory[1].third)
        assertEquals("Отвечает...", presenter.ongoingHistory[2].third)

        // Agent finishes while app is STILL backgrounded -> hides ongoing & shows "Агент закончил задачу"
        coordinator.onAgentFinished(
            chatId = "chat-42",
            chatTitle = "Фикс авторизации",
            wasCancelled = false,
            hadError = false,
            previewText = "Исправил токен."
        )
        assertTrue(presenter.hideOngoingCount >= 1)
        assertEquals(1, presenter.finishedHistory.size)
        val finished = presenter.finishedHistory.single()
        assertEquals("chat-42", finished.chatId)
        assertEquals("Агент закончил задачу", finished.title)
        assertEquals("Фикс авторизации: Исправил токен.", finished.contentText)
    }

    @Test
    fun `hides ongoing notification when returning to foreground while agent is still running`() {
        val presenter = FakeNotificationPresenter()
        val coordinator = AiAgentBackgroundCoordinator(presenter)

        coordinator.onAgentStarted("chat-7", "Тест", "Ищу в коде...")
        coordinator.onAppMovedToBackground()
        assertEquals(1, presenter.ongoingHistory.size)

        coordinator.onAppMovedToForeground()
        assertEquals(1, presenter.hideOngoingCount)

        // Further updates in foreground do NOT re-post ongoing notification
        coordinator.onAgentStatusChanged("chat-7", "Тест", "Отвечает...")
        assertEquals(1, presenter.ongoingHistory.size)
    }

    @Test
    fun `cancels active agent cleanly and suppresses finished notification when app is swiped from memory`() {
        val presenter = FakeNotificationPresenter()
        val coordinator = AiAgentBackgroundCoordinator(presenter)
        var cancelledCount = 0
        coordinator.registerCancelCallback { cancelledCount++ }

        coordinator.onAgentStarted("chat-9", "Сборка", "Создаю проект...")
        coordinator.onAppMovedToBackground()
        assertEquals(1, presenter.ongoingHistory.size)

        // User swipes app away from Recents
        coordinator.onAppTaskRemoved()
        assertEquals(1, cancelledCount)
        assertNull(coordinator.currentRun())
        assertTrue(presenter.hideOngoingCount >= 1)

        // Cancellation completion does NOT emit "Агент закончил задачу"
        coordinator.onAgentFinished("chat-9", "Сборка", wasCancelled = true, hadError = false)
        assertTrue(presenter.finishedHistory.isEmpty())
    }
}
