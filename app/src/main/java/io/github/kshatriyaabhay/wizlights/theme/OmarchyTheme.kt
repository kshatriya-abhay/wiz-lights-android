// Adapted from Flux (https://github.com/bjarneo/flux, android/, commit f6ed4ac), MIT License,
// Copyright (c) 2026 Bjarne Oeverli. See NOTICE. The packet parsing is removed; this app
// bundles its themes instead of receiving them from a computer.
package io.github.kshatriyaabhay.wizlights.theme

/**
 * An Omarchy theme: its colors.toml colours by key, as 0xRRGGBB.
 * [dark] is the theme's mode, or null when unknown.
 */
data class OmarchyTheme(
    val name: String,
    val dark: Boolean?,
    val colors: Map<String, Int>,
    val border: List<Int> = emptyList(),
    val borderAngle: Float? = null,
) {
    operator fun get(key: String): Int? = colors[key]

    companion object {
        private val HEX = Regex("^#?([0-9a-fA-F]{6})([0-9a-fA-F]{2})?$")

        /** Reads "#rrggbb" or "#rrggbbaa" as 0xRRGGBB. It drops the alpha. */
        fun parseColor(text: String?): Int? {
            val m = HEX.matchEntire(text?.trim() ?: return null) ?: return null
            return m.groupValues[1].toInt(16)
        }

        /** Writes 0xRRGGBB as "#rrggbb". */
        fun hex(rgb: Int): String = "#" + Integer.toHexString(opaque(rgb)).padStart(6, '0')
    }
}
