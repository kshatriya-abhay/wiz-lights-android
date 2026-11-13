package io.github.kshatriyaabhay.wizlights.wiz

import org.junit.Assert.assertEquals
import org.junit.Test

class LightBookTest {
    @Test
    fun defaultNames() {
        assertEquals("Color Light BBCC", LightBook.defaultName("ESP03_SHRGB1C_31", "a8bb50aabbcc"))
        assertEquals("WiZ Light BBCC", LightBook.defaultName("ESP06_SHTW1C_01", "a8bb50aabbcc"))
        assertEquals("WiZ Light", LightBook.defaultName("", "abc"))
    }

    @Test
    fun mergeKeepsNamesUpdatesIpsAndSorts() {
        val saved = listOf(Light("bb", "10.0.0.2", "Desk", "ESP03_SHRGB1C_31"), Light("zz", "10.0.0.9", "Gone", "X"))
        val found = listOf(Discovered("bb", "10.0.0.7", ""), Discovered("aa11", "10.0.0.3", "ESP_RGB"))
        val m = LightBook.merge(saved, found)
        assertEquals(listOf("aa11", "bb", "zz"), m.map { it.mac })
        assertEquals(Light("aa11", "10.0.0.3", "Color Light AA11", "ESP_RGB"), m[0])
        assertEquals(Light("bb", "10.0.0.7", "Desk", "ESP03_SHRGB1C_31"), m[1])
        assertEquals("Gone", m[2].name)
    }

    @Test
    fun mergeCaps() {
        val found = (0 until 100).map { Discovered("m%03d".format(it), "10.0.0.$it", "") }
        assertEquals(64, LightBook.merge(emptyList(), found).size)
    }

    @Test
    fun renameAndForget() {
        val l = listOf(Light("a", "1"), Light("b", "2"))
        assertEquals("Lamp", LightBook.rename(l, "a", "  Lamp  ")[0].name)
        assertEquals(128, LightBook.rename(l, "a", "x".repeat(300))[0].name.length)
        assertEquals(listOf("b"), LightBook.forget(l, "a").map { it.mac })
    }

    @Test(expected = IllegalArgumentException::class)
    fun renameRejectsBlank() {
        LightBook.rename(listOf(Light("a", "1")), "a", "   ")
    }

    @Test
    fun displayOrderIsByNameThenMac() {
        val l = listOf(Light("c", "", "bedroom"), Light("b", "", "Attic"), Light("a", "", "Bedroom"))
        assertEquals(listOf("b", "a", "c"), LightBook.displayOrder(l).map { it.mac })
    }
}
