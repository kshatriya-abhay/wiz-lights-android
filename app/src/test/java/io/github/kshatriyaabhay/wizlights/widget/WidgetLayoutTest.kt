package io.github.kshatriyaabhay.wizlights.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sizes measured on a Pixel 11 launcher (dp): 2x1 = 140 x 95, 5x1 = 373 x 95, 5x2 = 373 x 202. */
class WidgetLayoutTest {
    @Test
    fun twoByOneIsJustOnAndOff() {
        val p = WidgetLayout.layoutFor(140f, 95f, 3)
        assertFalse(p.showRescan); assertFalse(p.grid); assertEquals(0, p.inlineToggles)
        assertTrue("2x1 uses ON / OFF", p.shortLabels)
    }

    @Test
    fun threeByOneAddsRescan() {
        val p = WidgetLayout.layoutFor(220f, 95f, 3)
        assertTrue(p.showRescan); assertEquals(0, p.inlineToggles); assertFalse(p.grid)
    }

    @Test
    fun fourByOneHasFullLabelsWhenThereIsRoom() {
        // Below the inline threshold the two buttons share the row.
        val p = WidgetLayout.layoutFor(290f, 95f, 3)
        assertTrue(p.showRescan); assertFalse(p.shortLabels); assertEquals(0, p.inlineToggles)
    }

    @Test
    fun fiveByOneFillsTheRowWithToggles() {
        // 373 - 16 - 2 * 56 - 8 - 48 = 189 dp -> 2 toggles of at least 80 + 8.
        val p = WidgetLayout.layoutFor(373f, 95f, 3)
        assertEquals(2, p.inlineToggles)
        assertTrue(p.shortLabels)
        assertEquals(3, WidgetLayout.layoutFor(600f, 95f, 3).inlineToggles)
        assertEquals(WidgetLayout.MAX_INLINE, WidgetLayout.layoutFor(1400f, 95f, 30).inlineToggles)
    }

    @Test
    fun fiveByTwoSharesTheHeightWhenEverythingFits() {
        val p = WidgetLayout.layoutFor(373f, 202f, 3)
        assertTrue(p.grid)
        assertEquals(3, p.gridColumns)
        assertEquals(1, p.fillRows)
        assertFalse(p.shortLabels)
    }

    @Test
    fun columnsNeverExceedTheLightCount() {
        assertEquals(2, WidgetLayout.layoutFor(600f, 202f, 2).gridColumns)
        assertEquals(1, WidgetLayout.layoutFor(140f, 202f, 3).gridColumns)
    }

    @Test
    fun tooManyLightsScroll() {
        // 2x2 with 10 lights: 10 rows of 44 dp cannot fit in ~130 dp.
        val p = WidgetLayout.layoutFor(140f, 202f, 10)
        assertTrue(p.grid); assertEquals(0, p.fillRows)
        assertEquals(5, WidgetLayout.layoutFor(1200f, 600f, 30).gridColumns)
    }

    @Test
    fun noLightsNoGrid() {
        assertFalse(WidgetLayout.layoutFor(300f, 300f, 0).grid)
        assertEquals(0, WidgetLayout.layoutFor(600f, 95f, 0).inlineToggles)
    }
}
