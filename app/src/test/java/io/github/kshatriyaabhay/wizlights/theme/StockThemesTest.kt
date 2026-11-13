package io.github.kshatriyaabhay.wizlights.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contrast contract of PaletteSpec, adapted from Flux's ThemePaletteTest, over every stock theme. */
class StockThemesTest {
    private fun hex(c: Int) = OmarchyTheme.hex(c)
    private val failures = mutableListOf<String>()

    private fun check(what: String, a: Int, b: Int, least: Double) {
        val r = contrast(a, b)
        if (r < least) failures += "$what: ${hex(a)} on ${hex(b)} is ${"%.2f".format(r)}:1, needs $least:1"
    }

    private fun readable(p: PaletteSpec) {
        for (s in p.containers) {
            check("${p.name} text", p.text, s, TEXT_CONTRAST)
            check("${p.name} sub", p.sub, s, TEXT_CONTRAST)
        }
        check("${p.name} text against sub", p.text, p.sub, TEXT_STEP)
        for (s in listOf(p.bg, p.tile, p.tileHi)) check("${p.name} dim", p.dim, s, NON_TEXT_CONTRAST)
        val fills = listOf("accent", "cyan", "green", "magenta", "orange", "red", "yellow").zip(p.fills)
        for ((name, c) in fills) {
            for (s in p.surfaces + p.accentTile) check("${p.name} $name as text", c, s, TEXT_CONTRAST)
            check("${p.name} $name as an icon on line", c, p.line, NON_TEXT_CONTRAST)
            check("${p.name} ink on $name", p.onAccent, c, TEXT_CONTRAST)
        }
    }

    @Test
    fun everyStockThemeMeetsTheContract() {
        assertEquals(23, StockThemes.all.size)
        assertEquals(StockThemes.all.size, StockThemes.all.map { it.name }.toSet().size)
        for (t in StockThemes.all) readable(StockThemes.palette(t.name))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun darkFlagFollowsTheTheme() {
        assertEquals(false, StockThemes.palette("catppuccin-latte").dark)
        assertEquals(true, StockThemes.palette("nord").dark)
        assertEquals("tokyo-night", StockThemes.byName("nope").name)
        assertEquals("Catppuccin Latte", StockThemes.displayName("catppuccin-latte"))
    }
}
