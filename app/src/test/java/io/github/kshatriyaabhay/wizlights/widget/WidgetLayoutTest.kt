package io.github.kshatriyaabhay.wizlights.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Typical launcher cell sizes: about 73 dp wide per column after the first, 57 dp tall per row on a phone. */
class WidgetLayoutTest {
    private fun cells(c: Int) = 57f + 73f * (c - 1) + 16f

    @Test
    fun twoByOneIsJustTheTwoButtons() {
        val p = WidgetLayout.layoutFor(130f, 60f, 5)
        assertFalse(p.showRescan); assertFalse(p.grid); assertEquals(0, p.inlineToggles)
        assertTrue(p.iconOnly)
    }

    @Test
    fun threeByOneAddsRescan() {
        val p = WidgetLayout.layoutFor(cells(3), 60f, 5)
        assertTrue(p.showRescan); assertEquals(0, p.inlineToggles); assertFalse(p.grid)
    }

    @Test
    fun fourByOneHasLabels() {
        val p = WidgetLayout.layoutFor(cells(4), 60f, 5)
        assertTrue(p.showRescan); assertFalse(p.iconOnly)
    }

    @Test
    fun wideSingleRowGetsInlineToggles() {
        val five = WidgetLayout.layoutFor(cells(5), 60f, 5)
        assertEquals(1, five.inlineToggles)
        assertEquals(72f, five.buttonWidth)
        // 600 - 16 padding - 2 * 72 - 8 - 48 rescan = 384 dp = 4 toggles of 88 + 8.
        assertEquals(4, WidgetLayout.layoutFor(600f, 60f, 5).inlineToggles)
        assertEquals(2, WidgetLayout.layoutFor(600f, 60f, 2).inlineToggles)
    }

    @Test
    fun tallWidgetsGetAGrid() {
        val p2x2 = WidgetLayout.layoutFor(130f, 130f, 5)
        assertTrue(p2x2.grid); assertEquals(1, p2x2.gridColumns); assertFalse(p2x2.showRescan)
        val p4x2 = WidgetLayout.layoutFor(cells(4), 130f, 5)
        assertTrue(p4x2.grid); assertEquals(2, p4x2.gridColumns); assertTrue(p4x2.showRescan); assertEquals(0, p4x2.inlineToggles)
        val p4x4 = WidgetLayout.layoutFor(cells(4), 300f, 30)
        assertTrue(p4x4.grid)
        assertEquals(5, WidgetLayout.layoutFor(1200f, 300f, 30).gridColumns)
    }

    @Test
    fun noLightsNoGrid() {
        assertFalse(WidgetLayout.layoutFor(300f, 300f, 0).grid)
        assertEquals(0, WidgetLayout.layoutFor(600f, 60f, 0).inlineToggles)
    }
}
