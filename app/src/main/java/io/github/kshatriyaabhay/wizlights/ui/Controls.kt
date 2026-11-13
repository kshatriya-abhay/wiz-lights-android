package io.github.kshatriyaabhay.wizlights.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.kshatriyaabhay.wizlights.wiz.WizColor
import kotlin.math.roundToInt

fun hexColor(hex: String?): Color = WizColor.hexToInt(hex)?.let { Color(0xFF000000.toInt() or it) } ?: Color.White

/**
 * A slider with a 8 dp track. With [track] it draws that gradient over the
 * whole track (white temperature); without it, the filled part is the accent.
 */
@Composable
fun ValueSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    description: String,
    valueText: String,
    onChange: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    track: Brush? = null,
    enabled: Boolean = true,
) {
    val change by rememberUpdatedState(onChange)
    val release by rememberUpdatedState(onRelease)
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val accent = Tn.accent
    val line = Tn.line
    val thumb = Tn.text
    val ring = Tn.bg
    fun snap(v: Float): Float {
        val s = ((v - range.start) / step).roundToInt() * step + range.start
        return s.coerceIn(range.start, range.endInclusive)
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                contentDescription = description
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, ((range.endInclusive - range.start) / step).toInt() - 1)
                setProgress { v ->
                    change(snap(v))
                    release()
                    true
                }
            }
            .pointerInput(enabled, range, step) {
                if (!enabled) return@pointerInput
                val thumbPx = 22.dp.toPx()
                fun at(x: Float): Float {
                    val usable = (size.width - thumbPx).coerceAtLeast(1f)
                    val f = ((x - thumbPx / 2) / usable).coerceIn(0f, 1f)
                    return snap(range.start + f * (range.endInclusive - range.start))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    change(at(down.position.x))
                    drag(down.id) { c ->
                        change(at(c.position.x))
                        c.consume()
                    }
                    release()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbSize = 22.dp
        val density = LocalDensity.current
        val usable = maxWidth - thumbSize
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).drawBehind {
                if (track != null) {
                    drawRect(track)
                } else {
                    drawRect(line)
                    drawRect(accent, size = size.copy(width = thumbSize.toPx() / 2 + usable.toPx() * fraction))
                }
            },
        )
        Box(
            Modifier
                .offset(x = with(density) { (usable * fraction) })
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumb)
                .border(3.dp, ring, CircleShape),
        )
    }
}

private val HueStops = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
    Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/**
 * The hue × saturation plane from the plugin's HueSatPlane.qml.
 * Horizontal: hue 0..360. Vertical: saturation, 0 at the top (white) to 1 at the bottom.
 */
@Composable
fun HueSatPlane(
    hue: Double,
    sat: Double,
    onChange: (Double, Double) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val change by rememberUpdatedState(onChange)
    val release by rememberUpdatedState(onRelease)
    val shape = RoundedCornerShape(12.dp)
    val marker = WizColor.hsvToRgb(hue, sat, 1.0)
    val markerColor = Color(marker.r, marker.g, marker.b)
    val border = Tn.text.copy(alpha = 0.45f)
    val pct = (sat * 100).roundToInt()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(shape)
            .drawBehind {
                drawRect(Brush.horizontalGradient(HueStops))
                drawRect(Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
            }
            .border(1.dp, border, shape)
            .semantics {
                contentDescription = "Colour picker"
                stateDescription = "Hue ${hue.roundToInt()} degrees, saturation $pct percent"
                customActions = listOf(
                    CustomAccessibilityAction("Hue up") { change((hue + 15) % 360, sat); release(); true },
                    CustomAccessibilityAction("Hue down") { change((hue + 345) % 360, sat); release(); true },
                    CustomAccessibilityAction("More saturated") { change(hue, (sat + 0.1).coerceAtMost(1.0)); release(); true },
                    CustomAccessibilityAction("Less saturated") { change(hue, (sat - 0.1).coerceAtLeast(0.0)); release(); true },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun at(x: Float, y: Float) {
                    val h = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) * 360.0
                    val s = (y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f).toDouble()
                    change(h, s)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    at(down.position.x, down.position.y)
                    drag(down.id) { c ->
                        at(c.position.x, c.position.y)
                        c.consume()
                    }
                    release()
                }
            },
    ) {
        val m = 28.dp
        val inset = m / 2 * 0.65f
        val cx = (maxWidth * (hue / 360.0).toFloat()).coerceIn(inset, maxWidth - inset)
        val cy = (maxHeight * sat.toFloat()).coerceIn(inset, maxHeight - inset)
        Box(
            Modifier
                .offset(x = cx - m / 2, y = cy - m / 2)
                .size(m)
                .clip(CircleShape)
                .background(markerColor)
                .border(3.dp, Color.Black.copy(alpha = 0.55f), CircleShape),
        )
    }
}
