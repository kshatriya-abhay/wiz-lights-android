// Adapted from Flux (https://github.com/bjarneo/flux, android/, commit f6ed4ac): ui/TiledKit.kt,
// ui/Components.kt, ui/ShellKit.kt, ui/Icons.kt. MIT License, Copyright (c) 2026 Bjarne Oeverli.
// See NOTICE. Flux-specific components are left out; the theme comes from the app's setting.
package io.github.kshatriyaabhay.wizlights.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.kshatriyaabhay.wizlights.R
import io.github.kshatriyaabhay.wizlights.theme.PaletteSpec

// ───────────────────────── Colours ─────────────────────────

/** The theme colours as Compose colours. See the contrast contract in PaletteSpec. */
@Immutable
class TiledColors(
    val dark: Boolean,
    val bg: Color,
    val tile: Color,
    val tileHi: Color,
    val offTile: Color,
    val line: Color,
    val lineHi: Color,
    val text: Color,
    val sub: Color,
    val dim: Color,
    val onAccent: Color,
    val accent: Color,
    val cyan: Color,
    val green: Color,
    val magenta: Color,
    val orange: Color,
    val red: Color,
    val yellow: Color,
    val accentTile: Color,
)

private fun rgb(c: Int) = Color(0xFF000000.toInt() or c)

fun PaletteSpec.toTiledColors() = TiledColors(
    dark = dark,
    bg = rgb(bg), tile = rgb(tile), tileHi = rgb(tileHi), offTile = rgb(offTile),
    line = rgb(line), lineHi = rgb(lineHi),
    text = rgb(text), sub = rgb(sub), dim = rgb(dim), onAccent = rgb(onAccent),
    accent = rgb(accent), cyan = rgb(cyan), green = rgb(green), magenta = rgb(magenta),
    orange = rgb(orange), red = rgb(red), yellow = rgb(yellow),
    accentTile = rgb(accentTile),
)

private val LocalTiledColors = staticCompositionLocalOf { io.github.kshatriyaabhay.wizlights.theme.TokyoNight.toTiledColors() }

/** The colours of the current theme. */
val Tn: TiledColors
    @Composable @ReadOnlyComposable get() = LocalTiledColors.current

val TileShape = RoundedCornerShape(12.dp)
val TileGap = 8.dp
val Gutter = 16.dp
const val DimAlpha = 0.55f
val Mono = FontFamily.Monospace

// ───────────────────────── Motion ─────────────────────────

const val MOTION_MS = 200

val LocalReduceMotion = compositionLocalOf { false }

private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var off by remember { mutableStateOf(animationsOff(context)) }
    LifecycleResumeEffect(context) {
        off = animationsOff(context)
        onPauseOrDispose { }
    }
    return off
}

// ───────────────────────── Theme ─────────────────────────

@Composable
fun WizTheme(spec: PaletteSpec, content: @Composable () -> Unit) {
    val colors = remember(spec) { spec.toTiledColors() }
    val scheme = remember(colors) { colors.scheme() }
    val view = LocalView.current
    DisposableEffect(view, colors) {
        view.context.findActivity()?.let { activity ->
            val window = activity.window
            WindowCompat.getInsetsController(window, view).run {
                isAppearanceLightStatusBars = !colors.dark
                isAppearanceLightNavigationBars = !colors.dark
            }
            window.setBackgroundDrawable(colors.bg.toArgb().toDrawable())
        }
        onDispose { }
    }
    val reduce = rememberReduceMotion()
    CompositionLocalProvider(LocalTiledColors provides colors, LocalReduceMotion provides reduce) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides colors.text, content = content)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun TiledColors.scheme(): ColorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = accent, onPrimary = onAccent,
    primaryContainer = accentTile, onPrimaryContainer = text,
    secondary = cyan, onSecondary = onAccent,
    secondaryContainer = line, onSecondaryContainer = text,
    tertiary = magenta, onTertiary = onAccent,
    tertiaryContainer = line, onTertiaryContainer = text,
    background = bg, onBackground = text,
    surface = bg, onSurface = text,
    surfaceVariant = tile, onSurfaceVariant = sub,
    surfaceTint = accent,
    surfaceContainerLowest = bg, surfaceContainerLow = offTile,
    surfaceContainer = tile, surfaceContainerHigh = tileHi, surfaceContainerHighest = line,
    inverseSurface = tileHi, inverseOnSurface = text, inversePrimary = accent,
    outline = dim, outlineVariant = line,
    error = red, onError = onAccent,
    errorContainer = red, onErrorContainer = onAccent,
)

// ───────────────────────── Text and icons ─────────────────────────

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    color: Color = Tn.text,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = FontFamily.Default,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: Float = 0f,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFamily = family,
            textAlign = align ?: TextAlign.Unspecified,
            lineHeight = if (lineHeight > 0) (size * lineHeight).sp else TextUnit.Unspecified,
        ),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

object Ic {
    val add = R.drawable.ic_add
    val back = R.drawable.ic_arrow_back
    val more = R.drawable.ic_more_vert
    val close = R.drawable.ic_close
    val check = R.drawable.ic_check
    val delete = R.drawable.ic_delete
    val edit = R.drawable.ic_edit
    val refresh = R.drawable.ic_refresh
    val scan = R.drawable.ic_wifi_find
    val settings = R.drawable.ic_settings
    val power = R.drawable.ic_power_settings_new
    val wifi = R.drawable.ic_wifi
    val wifiOff = R.drawable.ic_wifi_off
    val info = R.drawable.ic_info
    val warning = R.drawable.ic_warning
    val bulb = R.drawable.ic_bulb
    val lamp = R.drawable.ic_lamp
    val chevron = R.drawable.ic_chevron_right

    /** The drawable for a light's chosen icon. */
    fun forLight(icon: String): Int = if (icon == io.github.kshatriyaabhay.wizlights.wiz.LightIcon.LAMP) lamp else bulb
}

@Composable
fun Sym(
    @DrawableRes id: Int,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(painterResource(id), contentDescription, modifier.size(size), tint)
}

// ───────────────────────── Components ─────────────────────────

@Composable
fun Tile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    container: Color = Tn.tile,
    border: BorderStroke? = BorderStroke(1.dp, Tn.line),
    enabled: Boolean = true,
    padding: PaddingValues = PaddingValues(14.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    shape: Shape = TileShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val stroke = if (pressed && onClick != null) BorderStroke(1.dp, Tn.accent) else border
    var m = modifier.alpha(if (enabled) 1f else DimAlpha).clip(shape).background(container)
    if (stroke != null) m = m.border(stroke, shape)
    if (onClick != null) {
        m = m.combinedClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }
    Column(m.padding(padding), verticalArrangement = verticalArrangement, content = content)
}

@Composable
@ReadOnlyComposable
fun choiceFill(selected: Boolean): Color = if (selected) Tn.accentTile else Tn.tile

@Composable
@ReadOnlyComposable
fun choiceBorder(selected: Boolean): BorderStroke = if (selected) BorderStroke(2.dp, Tn.accent) else BorderStroke(1.dp, Tn.line)

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mono: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.minimumInteractiveComponentSize().heightIn(min = 40.dp).alpha(if (enabled) 1f else DimAlpha)
            .clip(shape).background(choiceFill(selected)).border(choiceBorder(selected), shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        T(
            label, size = 13, color = if (selected) Tn.text else Tn.sub, weight = FontWeight.SemiBold,
            family = if (mono) Mono else FontFamily.Default, align = TextAlign.Center,
        )
    }
}

enum class ButtonKind { Filled, Tonal, Outlined, Destructive, Text }

@Composable
fun WizButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Filled,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val on = enabled && !busy
    val (fill, ink) = when (kind) {
        ButtonKind.Filled -> Tn.accent to Tn.onAccent
        ButtonKind.Tonal -> Tn.line to Tn.text
        ButtonKind.Outlined, ButtonKind.Text -> Color.Transparent to Tn.accent
        ButtonKind.Destructive -> Color.Transparent to Tn.red
    }
    val flat = kind == ButtonKind.Outlined || kind == ButtonKind.Text || kind == ButtonKind.Destructive
    val colors = ButtonDefaults.buttonColors(
        containerColor = fill, contentColor = ink,
        disabledContainerColor = if (busy) fill else if (flat) Color.Transparent else Tn.tile,
        disabledContentColor = if (busy) ink else Tn.dim,
    )
    val border = when (kind) {
        ButtonKind.Outlined -> BorderStroke(1.dp, if (on || busy) Tn.dim else Tn.line)
        ButtonKind.Destructive -> BorderStroke(1.dp, if (on || busy) Tn.red else Tn.line)
        else -> null
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = on,
        shape = TileShape,
        colors = colors,
        border = border,
        contentPadding = PaddingValues(horizontal = if (kind == ButtonKind.Text) 12.dp else 18.dp, vertical = 10.dp),
    ) {
        if (busy) {
            Spinner(Modifier.size(18.dp), color = LocalContentColor.current)
            Spacer(Modifier.size(8.dp))
        } else if (icon != null) {
            Sym(icon, size = 18.dp)
            Spacer(Modifier.size(8.dp))
        }
        Text(label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center)
    }
}

/** A 40 dp square icon button for top bars, with a 48 dp touch target. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SquareButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    // The description doubles as a tooltip, shown on long-press or mouse hover.
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip(containerColor = Tn.tileHi, contentColor = Tn.text) { Text(description) } },
        state = rememberTooltipState(),
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Tn.tile, contentColor = Tn.text, disabledContainerColor = Tn.tile, disabledContentColor = Tn.dim,
            ),
        ) { Sym(icon, description, size = 20.dp) }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier, color: Color = Tn.accent, strokeWidth: Dp = 2.dp) {
    if (LocalReduceMotion.current) {
        CircularProgressIndicator(progress = { 0.75f }, modifier = modifier, color = color, strokeWidth = strokeWidth, trackColor = Color.Transparent)
    } else {
        CircularProgressIndicator(modifier, color = color, strokeWidth = strokeWidth)
    }
}

@Composable
fun TileLabel(text: String, modifier: Modifier = Modifier, color: Color = Tn.sub) {
    T(text, modifier, size = 12, color = color, weight = FontWeight.Medium, lineHeight = 1.35f)
}

@Composable
fun SectionLabel(text: String) {
    T(
        text,
        Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
        size = 14, weight = FontWeight.Medium,
    )
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
