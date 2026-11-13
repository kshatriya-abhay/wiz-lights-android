package io.github.kshatriyaabhay.wizlights.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.kshatriyaabhay.wizlights.theme.OmarchyTheme
import io.github.kshatriyaabhay.wizlights.theme.PaletteSpec
import io.github.kshatriyaabhay.wizlights.theme.StockThemes
import io.github.kshatriyaabhay.wizlights.theme.paletteOf

/** Resolves a theme name to a palette, including Material You, which needs a Context. */
object Themes {
    val materialYouAvailable: Boolean get() = Build.VERSION.SDK_INT >= 31

    /** Theme names in the picker's order: Material You first when the phone supports it. */
    val names: List<String>
        get() = (if (materialYouAvailable) listOf(StockThemes.MATERIAL_YOU) else emptyList()) + StockThemes.all.map { it.name }

    private var cacheKey: Any? = null
    private var cached: PaletteSpec? = null

    fun palette(context: Context, name: String?): PaletteSpec {
        if (name == StockThemes.MATERIAL_YOU && materialYouAvailable) return materialYou(context)
        return StockThemes.palette(name)
    }

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * Builds an Omarchy-style theme from the system's wallpaper colours and
     * runs it through Flux's contrast guard. It follows the system's dark
     * mode. Red, green, yellow and orange are not part of the dynamic
     * palette, so they come from Tokyo Night through paletteOf's fallbacks.
     */
    @RequiresApi(31)
    @Synchronized
    fun materialYou(context: Context): PaletteSpec {
        val dark = isDark(context)
        fun c(id: Int) = context.getColor(id) and 0xFFFFFF
        val colors = if (dark) {
            mapOf(
                "background" to c(android.R.color.system_neutral1_900),
                "lighter_background" to c(android.R.color.system_neutral2_800),
                "foreground" to c(android.R.color.system_neutral1_100),
                "muted" to c(android.R.color.system_neutral2_400),
                "accent" to c(android.R.color.system_accent1_200),
                "blue" to c(android.R.color.system_accent1_200),
                "cyan" to c(android.R.color.system_accent3_200),
                "magenta" to c(android.R.color.system_accent2_200),
            )
        } else {
            mapOf(
                "background" to c(android.R.color.system_neutral1_10),
                "dark_background" to c(android.R.color.system_neutral2_50),
                "darker_background" to c(android.R.color.system_neutral2_100),
                "foreground" to c(android.R.color.system_neutral1_900),
                "muted" to c(android.R.color.system_neutral2_500),
                "accent" to c(android.R.color.system_accent1_600),
                "blue" to c(android.R.color.system_accent1_600),
                "cyan" to c(android.R.color.system_accent3_600),
                "magenta" to c(android.R.color.system_accent2_600),
            )
        }
        val key = dark to colors
        if (key == cacheKey) cached?.let { return it }
        val p = paletteOf(OmarchyTheme(StockThemes.MATERIAL_YOU, dark, colors))
        cacheKey = key
        cached = p
        return p
    }
}
