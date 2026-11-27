package com.pocketworker.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopNotificationStateTest {

    @Test
    fun `show trims message, assigns monotonic ids, clamps duration, and ignores blank messages`() {
        val state = TopNotificationState()
        assertNull(state.current)

        state.show("   ")
        assertNull(state.current)

        state.show("  Saved successfully  ", isError = false, durationMillis = 100L)
        val first = state.current
        assertNotNull(first)
        assertEquals("Saved successfully", first!!.message)
        assertFalse(first.isError)
        assertEquals(800L, first.durationMillis)

        state.show("Push failed", isError = true, durationMillis = 4_200L)
        val second = state.current
        assertNotNull(second)
        assertTrue(second!!.id > first.id)
        assertEquals("Push failed", second.message)
        assertTrue(second.isError)
        assertEquals(4_200L, second.durationMillis)
    }

    @Test
    fun `dismiss ignores stale ids and clears matching or wildcard dismissal`() {
        val state = TopNotificationState()
        state.show("First")
        val firstId = state.current!!.id

        state.show("Second")
        val secondId = state.current!!.id

        // Stale dismiss from an old banner must not hide the active banner
        state.dismiss(firstId)
        assertEquals(secondId, state.current?.id)

        // Matching id dismisses active banner
        state.dismiss(secondId)
        assertNull(state.current)

        // Wildcard dismiss clears whatever is active
        state.show("Third")
        assertNotNull(state.current)
        state.dismiss()
        assertNull(state.current)
    }
}
