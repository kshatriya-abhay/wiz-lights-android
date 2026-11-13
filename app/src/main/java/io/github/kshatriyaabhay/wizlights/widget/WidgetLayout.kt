package io.github.kshatriyaabhay.wizlights.widget

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What the widget draws at a given size. Pure, so it is unit-tested. */
data class WidgetPlan(
    val showRescan: Boolean,
    /** Light toggles in row 1 (one-row widgets only). They stretch to fill the row. */
    val inlineToggles: Int,
    /** True when light toggles go below row 1. */
    val grid: Boolean,
    val gridColumns: Int,
    /**
     * Rows of the toggle grid when every light fits: then row 1 and the toggle
     * rows share the height evenly, with no empty space. 0 means the grid scrolls.
     */
    val fillRows: Int,
    /** Width of each of All on / All off, in dp; 0 means they share the row by weight. */
    val buttonWidth: Float,
    /** True when All on / All off show the short labels "ON" / "OFF". */
    val shortLabels: Boolean,
)

object WidgetLayout {
    const val PAD = 8f
    const val GAP = 8f
    const val RESCAN = 40f
    /** All on / All off next to inline toggles: just wide enough for "ON" / "OFF". */
    const val COMPACT_BUTTON = 56f
    /** Below this width each of All on / All off shows "ON" / "OFF". */
    const val FULL_LABEL_MIN = 88f
    const val INLINE_TOGGLE_MIN = 80f
    const val GRID_CELL = 110f
    const val GRID_CELL_HEIGHT = 44f
    const val HEADER = 48f
    const val RESCAN_MIN_WIDTH = 180f
    const val INLINE_MIN_WIDTH = 300f
    const val TALL = 100f
    /** Glance's LazyVerticalGrid takes 1..5 fixed columns. */
    const val MAX_GRID_COLUMNS = 5
    /** A Glance Row or Column holds at most 10 children; keep well under it. */
    const val MAX_INLINE = 6
    const val MAX_FILL_ROWS = 8

    fun layoutFor(w: Float, h: Float, lightCount: Int): WidgetPlan {
        val inner = w - 2 * PAD
        val rescan = w >= RESCAN_MIN_WIDTH
        val rescanSpace = if (rescan) RESCAN + GAP else 0f
        val tall = h >= TALL

        if (!tall && w >= INLINE_MIN_WIDTH && lightCount > 0) {
            // Two compact buttons, then as many toggles as fit at their minimum width.
            val free = inner - 2 * COMPACT_BUTTON - GAP - rescanSpace
            val n = minOf(lightCount, MAX_INLINE, max(0, floor(free / (INLINE_TOGGLE_MIN + GAP)).toInt()))
            if (n > 0) {
                return WidgetPlan(rescan, n, grid = false, gridColumns = 0, fillRows = 0, buttonWidth = COMPACT_BUTTON, shortLabels = true)
            }
        }

        val buttonWidth = max(0f, (inner - GAP - rescanSpace) / 2)
        val shortLabels = buttonWidth < FULL_LABEL_MIN
        if (!tall || lightCount == 0) {
            return WidgetPlan(rescan, 0, grid = false, gridColumns = 0, fillRows = 0, buttonWidth = 0f, shortLabels = shortLabels)
        }

        val columns = min(lightCount, floor(inner / GRID_CELL).toInt().coerceIn(1, MAX_GRID_COLUMNS))
        val rows = ceil(lightCount / columns.toFloat()).toInt()
        val gridHeight = h - 2 * PAD - HEADER - GAP
        val fits = rows <= MAX_FILL_ROWS && rows * GRID_CELL_HEIGHT + (rows - 1) * GAP <= gridHeight
        return WidgetPlan(
            showRescan = rescan,
            inlineToggles = 0,
            grid = true,
            gridColumns = columns,
            fillRows = if (fits) rows else 0,
            buttonWidth = 0f,
            shortLabels = shortLabels,
        )
    }
}
