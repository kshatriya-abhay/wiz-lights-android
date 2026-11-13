package io.github.kshatriyaabhay.wizlights.wiz

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class Rgb(val r: Int, val g: Int, val b: Int)

data class Hsv(val h: Double, val s: Double, val v: Double)

/** Colour helpers ported line by line from the plugin's Model.js. */
object WizColor {
    /** JavaScript's Math.round: halves round up, also for negatives. */
    private fun jsRound(x: Double): Int = kotlin.math.floor(x + 0.5).toInt()

    private fun finite(x: Double, fallback: Double = 0.0) = if (x.isFinite()) x else fallback

    fun hsvToRgb(h: Double, s: Double, v: Double): Rgb {
        val hue = ((finite(h) % 360) + 360) % 360
        val sat = max(0.0, min(1.0, finite(s)))
        val value = max(0.0, min(1.0, finite(v)))
        val c = value * sat
        val x = c * (1 - abs((hue / 60) % 2 - 1))
        val m = value - c
        var rp = 0.0
        var gp = 0.0
        var bp = 0.0
        when {
            hue < 60 -> { rp = c; gp = x }
            hue < 120 -> { rp = x; gp = c }
            hue < 180 -> { gp = c; bp = x }
            hue < 240 -> { gp = x; bp = c }
            hue < 300 -> { rp = x; bp = c }
            else -> { rp = c; bp = x }
        }
        return Rgb(jsRound((rp + m) * 255), jsRound((gp + m) * 255), jsRound((bp + m) * 255))
    }

    fun rgbToHsv(r: Int, g: Int, b: Int): Hsv {
        val rn = r / 255.0
        val gn = g / 255.0
        val bn = b / 255.0
        val mx = max(rn, max(gn, bn))
        val mn = min(rn, min(gn, bn))
        val d = mx - mn
        var h = 0.0
        if (d > 0) {
            h = when (mx) {
                rn -> 60 * (((gn - bn) / d) % 6)
                gn -> 60 * ((bn - rn) / d + 2)
                else -> 60 * ((rn - gn) / d + 4)
            }
        }
        if (h < 0) h += 360
        return Hsv(h, if (mx == 0.0) 0.0 else d / mx, mx)
    }

    fun rgbToHex(r: Number, g: Number, b: Number): String {
        fun byte(v: Number): String {
            val n = max(0, min(255, jsRound(finite(v.toDouble()))))
            return n.toString(16).padStart(2, '0')
        }
        return ("#" + byte(r) + byte(g) + byte(b)).uppercase()
    }

    fun rgbToHex(c: Rgb): String = rgbToHex(c.r, c.g, c.b)

    fun hexToRgb(hex: String?): Rgb? {
        if (hex == null) return null
        var clean = hex.removePrefix("#")
        if (clean.length == 3) clean = clean.map { "$it$it" }.joinToString("")
        if (clean.length != 6) return null
        val num = clean.toIntOrNull(16) ?: return null
        return Rgb((num shr 16) and 255, (num shr 8) and 255, num and 255)
    }

    fun hueToHex(h: Double): String = rgbToHex(hsvToRgb(h, 1.0, 1.0))

    fun kelvinToHex(k: Int): String {
        val temp = (if (k == 0) 2700 else k) / 100.0
        val r: Double
        val g: Double
        val b: Double
        if (temp <= 66) {
            r = 255.0
        } else {
            r = (329.698727446 * (temp - 60).pow(-0.1332047592)).coerceIn(0.0, 255.0)
        }
        g = if (temp <= 66) {
            (99.4708025861 * ln(temp) - 161.1195681661).coerceIn(0.0, 255.0)
        } else {
            (288.1221695283 * (temp - 60).pow(-0.0755148492)).coerceIn(0.0, 255.0)
        }
        b = when {
            temp >= 66 -> 255.0
            temp <= 19 -> 0.0
            else -> (138.5177312231 * ln(temp - 10) - 305.0447927307).coerceIn(0.0, 255.0)
        }
        return rgbToHex(r, g, b)
    }

    /** "#RRGGBB" to 0xRRGGBB, or null. */
    fun hexToInt(hex: String?): Int? = hexToRgb(hex)?.let { (it.r shl 16) or (it.g shl 8) or it.b }

    /** Rounds Kelvin to the nearest 100 and clamps to the bulb range. */
    fun clampTemp(k: Int): Int = ((k / 100.0).roundToInt() * 100).coerceIn(Limits.TEMP_MIN, Limits.TEMP_MAX)
}
