package io.github.kshatriyaabhay.wizlights.widget

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What the widget draws at a given size. Pure, so it is unit-tested. */
data class WidgetPlan(
    val showRescan: Boolean,
    /** Light toggles in row 1 (one-row widgets only). */
    val inlineToggles: Int,
    /** True when a scrolling grid of toggles goes below row 1. */
    val grid: Boolean,
    val gridColumns: Int,
    /** Width of each of All on / All off, in dp. */
    val buttonWidth: Float,
    /** True when All on / All off show only their icons. */
    val iconOnly: Boolean,
)

object WidgetLayout {
    const val PAD = 8f
    const val GAP = 8f
    const val RESCAN = 40f
    const val COMPACT_BUTTON = 72f
    const val INLINE_TOGGLE = 88f
    const val GRID_CELL = 110f
    const val RESCAN_MIN_WIDTH = 180f
    const val INLINE_MIN_WIDTH = 300f
    const val TALL = 100f
    /** Glance's LazyVerticalGrid takes 1..5 fixed columns. */
    const val MAX_GRID_COLUMNS = 5

    fun layoutFor(w: Float, h: Float, lightCount: Int): WidgetPlan {
        val inner = w - 2 * PAD
        val rescan = w >= RESCAN_MIN_WIDTH
        val rescanSpace = if (rescan) RESCAN + GAP else 0f
        val tall = h >= TALL
        if (!tall && w >= INLINE_MIN_WIDTH && lightCount > 0) {
            val free = inner - 2 * COMPACT_BUTTON - GAP - rescanSpace
            val n = min(lightCount, max(0, floor(free / (INLINE_TOGGLE + GAP)).toInt()))
            if (n > 0) {
                return WidgetPlan(rescan, n, grid = false, gridColumns = 0, buttonWidth = COMPACT_BUTTON, iconOnly = false)
            }
        }
        val buttonWidth = max(0f, (inner - GAP - rescanSpace) / 2)
        val columns = (floor(inner / GRID_CELL).toInt()).coerceIn(1, MAX_GRID_COLUMNS)
        return WidgetPlan(
            showRescan = rescan,
            inlineToggles = 0,
            grid = tall && lightCount > 0,
            gridColumns = columns,
            buttonWidth = buttonWidth,
            iconOnly = buttonWidth < COMPACT_BUTTON,
        )
    }
}
