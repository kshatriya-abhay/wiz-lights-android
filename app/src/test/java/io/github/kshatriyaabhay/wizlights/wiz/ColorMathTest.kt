package io.github.kshatriyaabhay.wizlights.wiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expected values computed by running the plugin's Model.js under Node. */
class ColorMathTest {
    @Test
    fun kelvinToHexMatchesModelJs() {
        val expected = mapOf(2200 to "#FF9227", 2700 to "#FFA757", 5000 to "#FFE4CE", 6500 to "#FFFEFA", 3000 to "#FFB16E", 4000 to "#FFCEA6")
        for ((k, hex) in expected) assertEquals("$k K", hex, WizColor.kelvinToHex(k))
    }

    @Test
    fun hsvToRgbMatchesModelJs() {
        val cases = listOf(
            Triple(0.0, 1.0, Rgb(255, 0, 0)), Triple(35.0, 1.0, Rgb(255, 149, 0)), Triple(120.0, 0.5, Rgb(128, 255, 128)),
            Triple(200.0, 0.25, Rgb(191, 234, 255)), Triple(300.0, 0.8, Rgb(255, 51, 255)), Triple(359.9, 1.0, Rgb(255, 0, 0)),
            Triple(90.0, 0.0, Rgb(255, 255, 255)),
        )
        for ((h, s, rgb) in cases) assertEquals("h=$h s=$s", rgb, WizColor.hsvToRgb(h, s, 1.0))
        assertEquals("#BFEAFF", WizColor.rgbToHex(Rgb(191, 234, 255)))
        assertEquals("#00AAFF", WizColor.hueToHex(200.0))
    }

    @Test
    fun rgbToHsvMatchesModelJs() {
        val cases = listOf(
            Rgb(255, 167, 87) to Hsv(28.57142857142857, 0.6588235294117647, 1.0),
            Rgb(10, 200, 30) to Hsv(126.3157894736842, 0.95, 0.7843137254901961),
            Rgb(0, 0, 255) to Hsv(240.0, 1.0, 1.0),
            Rgb(128, 128, 128) to Hsv(0.0, 0.0, 0.5019607843137255),
            Rgb(255, 0, 128) to Hsv(329.88235294117646, 1.0, 1.0),
        )
        for ((rgb, hsv) in cases) {
            val got = WizColor.rgbToHsv(rgb.r, rgb.g, rgb.b)
            assertEquals(hsv.h, got.h, 1e-9); assertEquals(hsv.s, got.s, 1e-9); assertEquals(hsv.v, got.v, 1e-9)
        }
    }

    @Test
    fun hexParsing() {
        assertEquals(Rgb(170, 187, 204), WizColor.hexToRgb("#abc"))
        assertNull(WizColor.hexToRgb("zzzzzz"))
        assertNull(WizColor.hexToRgb("#12345"))
    }

    @Test
    fun roundTripsThroughHsv() {
        for (h in 0 until 360 step 7) for (s in listOf(0.2, 0.5, 1.0)) {
            val rgb = WizColor.hsvToRgb(h.toDouble(), s, 1.0)
            val back = WizColor.rgbToHsv(rgb.r, rgb.g, rgb.b)
            assertEquals(rgb, WizColor.hsvToRgb(back.h, back.s, 1.0))
        }
    }
}
