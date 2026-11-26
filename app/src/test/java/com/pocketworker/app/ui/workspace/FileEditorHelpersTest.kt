package com.pocketworker.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class FileEditorHelpersTest {

    @Test
    fun `applyEditorZoom scales within bounds and ignores invalid factors`() {
        assertEquals(1.5f, applyEditorZoom(1.0f, 1.5f), 0.0001f)
        assertEquals(MAX_EDITOR_ZOOM, applyEditorZoom(2.0f, 3.0f), 0.0001f)
        assertEquals(MIN_EDITOR_ZOOM, applyEditorZoom(0.8f, 0.2f), 0.0001f)
        assertEquals(1.2f, applyEditorZoom(1.2f, Float.NaN), 0.0001f)
        assertEquals(1.2f, applyEditorZoom(1.2f, -1f), 0.0001f)
    }

    @Test
    fun `formatEditorZoomPercent formats clamped integer percentage`() {
        assertEquals("100%", formatEditorZoomPercent(1.0f))
        assertEquals("135%", formatEditorZoomPercent(1.352f))
        assertEquals("65%", formatEditorZoomPercent(0.1f))
        assertEquals("240%", formatEditorZoomPercent(5.0f))
    }

    @Test
    fun `buildLineNumbersText produces exact sequential newline-separated lines`() {
        assertEquals("1", buildLineNumbersText(0))
        assertEquals("1", buildLineNumbersText(1))
        assertEquals("1\n2\n3\n4", buildLineNumbersText(4))
    }
}
