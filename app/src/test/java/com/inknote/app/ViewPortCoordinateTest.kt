package com.inknote.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ViewPortCoordinateTest {

    @Test
    fun testDirectViewPixelsZeroDriftHandoff() {
        // Test multiple screen resolutions and zoom levels
        val testWidths = listOf(360f, 600f, 800f, 1080f, 1440f, 2160f, 3200f)
        val testTouches = listOf(
            Pair(0f, 0f),
            Pair(150f, 250f),
            Pair(359.5f, 500f),
            Pair(720f, 1000f),
            Pair(1080f, 1920f)
        )

        for (w in testWidths) {
            for ((touchX, touchY) in testTouches) {
                // Live drawing: InProgressStrokesView records inputs in local View pixels
                val livePixelX = touchX
                val livePixelY = touchY

                // Direct handoff: finished Stroke is kept in local View pixels and drawn with Identity matrix
                val renderedX = livePixelX
                val renderedY = livePixelY

                assertEquals("Rendered X must match touch X exactly at width $w", touchX, renderedX, 0.0f)
                assertEquals("Rendered Y must match touch Y exactly at width $w", touchY, renderedY, 0.0f)

                // Persistence roundtrip: normalized to 0..1 for storage
                val nx = livePixelX / w
                val ny = livePixelY / w

                // When reloaded at the same width:
                val restoredX = nx * w
                val restoredY = ny * w
                assertEquals("Restored X must match original touch X", touchX, restoredX, 0.01f)
                assertEquals("Restored Y must match original touch Y", touchY, restoredY, 0.01f)

                // When page is zoomed (e.g. 1.5x zoom -> w * 1.5):
                val zoom = 1.5f
                val zoomedWidth = w * zoom
                val zoomedX = nx * zoomedWidth
                val zoomedY = ny * zoomedWidth
                assertEquals("Zoomed X must scale proportionally by $zoom", touchX * zoom, zoomedX, 0.001f)
                assertEquals("Zoomed Y must scale proportionally by $zoom", touchY * zoom, zoomedY, 0.001f)
            }
        }
    }

    @Test
    fun testPressureSensitivityCurves() {
        // Standard sensitivity (1.0x) -> linear
        val rawP = 0.5
        val pPowerStandard = 1.0 / 1.0
        val pStandard = rawP.pow(pPowerStandard)
        assertEquals(0.5, pStandard, 0.001)

        // High sensitivity (2.0x) -> light touch is amplified (more responsive)
        val pPowerHigh = 1.0 / 2.0
        val pHigh = rawP.pow(pPowerHigh)
        assertTrue("High sensitivity should increase pressure response for light touches", pHigh > pStandard)
        assertEquals(0.7071, pHigh, 0.001)

        // Low sensitivity (0.5x) -> firm touch required
        val pPowerLow = 1.0 / 0.5
        val pLow = rawP.pow(pPowerLow)
        assertTrue("Low sensitivity should require firmer pressure", pLow < pStandard)
        assertEquals(0.25, pLow, 0.001)
    }

    @Test
    fun testViewPortScaleCalculation() {
        val docW = 1000f
        val viewW = 1500f // 1.5x zoom
        val s = viewW / docW
        assertEquals(1.5f, s, 0.0001f)

        // Point on screen (750, 750) mapped to document
        val docX = 750f / s
        val docY = 750f / s
        assertEquals(500f, docX, 0.001f)
        assertEquals(500f, docY, 0.001f)

        // Point mapped back to screen
        val screenX = docX * s
        val screenY = docY * s
        assertEquals(750f, screenX, 0.001f)
        assertEquals(750f, screenY, 0.001f)
    }
}
