package io.github.kshatriyaabhay.wizlights.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.kshatriyaabhay.wizlights.R
import io.github.kshatriyaabhay.wizlights.WizApp
import io.github.kshatriyaabhay.wizlights.data.LightUi
import io.github.kshatriyaabhay.wizlights.net.Gate
import io.github.kshatriyaabhay.wizlights.theme.PaletteSpec
import io.github.kshatriyaabhay.wizlights.ui.Ic
import io.github.kshatriyaabhay.wizlights.ui.Themes
import io.github.kshatriyaabhay.wizlights.ui.MainActivity
import kotlinx.coroutines.withTimeoutOrNull

/** The widget colours, from the selected theme's palette. */
private class WColors(p: PaletteSpec) {
    private fun c(v: Int) = ColorProvider(Color(0xFF000000.toInt() or v))
    val bg = c(p.bg)
    val tile = c(p.tile)
    val offTile = c(p.offTile)
    val line = c(p.line)
    val text = c(p.text)
    val sub = c(p.sub)
    val dim = c(p.dim)
    val accent = c(p.accent)
    val onAccent = c(p.onAccent)
    val accentTile = c(p.accentTile)
}

class LightsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = WizApp.of(context).repository
        repo.ensureLoaded()
        runCatching { repo.checkGate() }
        // A new widget session (periodic update, first draw, or after the last one went idle)
        // refreshes bulb state when the app isn't open; the app polls on its own.
        if (!WizApp.of(context).isForeground) withTimeoutOrNull(5000) { repo.refresh() }
        provideContent {
            val lights by repo.lights.collectAsState()
            val scanning by repo.scanning.collectAsState()
            val allBusy by repo.allBusy.collectAsState()
            val gate by repo.gate.collectAsState()
            val settings by repo.settings.collectAsState()
            Content(lights, scanning, allBusy, gate, WColors(Themes.palette(context, settings.themeName)))
        }
    }
}

class LightsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LightsWidget()
}

// ───────────── Actions ─────────────

internal val MacKey = ActionParameters.Key<String>("mac")
internal val OnKey = ActionParameters.Key<Boolean>("on")

class AllPowerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WizApp.of(context).repository.setAllPower(parameters[OnKey] ?: true)
    }
}

class RescanAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WizApp.of(context).repository.scan()
    }
}

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = WizApp.of(context).repository
        repo.ensureLoaded()
        val mac = parameters[MacKey] ?: return
        val l = repo.lights.value.firstOrNull { it.mac == mac } ?: return
        io.github.kshatriyaabhay.wizlights.net.DebugLog.d("widget toggle ${mac.takeLast(4)} reachable=${l.state.reachable} on=${l.state.on} loaded=${repo.loaded.value}")
        if (!l.state.reachable) repo.refresh() else repo.setPower(mac, !l.state.on)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = WizApp.of(context).repository
        repo.checkGate(force = true)
        repo.refresh()
    }
}

private fun openApp(context: Context, settings: Boolean = false): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (settings) putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_SETTINGS) },
    )

// ───────────── Content ─────────────

@Composable
private fun Content(lights: List<LightUi>, scanning: Boolean, allBusy: Boolean, gate: Gate, c: WColors) {
    val context = androidx.glance.LocalContext.current
    val size = LocalSize.current
    val tall = size.height.value >= WidgetLayout.TALL
    val rounded = GlanceModifier.fillMaxSize().appWidgetBackground().background(c.bg)
    val root = (if (Build.VERSION.SDK_INT >= 31) rounded.cornerRadius(android.R.dimen.system_app_widget_background_radius) else rounded.cornerRadius(16.dp))
        .padding(WidgetLayout.PAD.dp)
        .clickable(openApp(context))

    if (gate is Gate.SsidUnavailable) {
        Column(root, verticalAlignment = Alignment.CenterVertically) {
            WideButton("Open app to allow Wi-Fi check", c.line, c.text, openApp(context, settings = true), tall)
        }
        return
    }
    if (lights.isEmpty()) {
        Column(root, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            if (tall) {
                Text("No lights yet", style = TextStyle(color = c.sub, fontSize = 12.sp))
                Spacer(GlanceModifier.height(WidgetLayout.GAP.dp))
            }
            if (scanning) {
                Box(GlanceModifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(GlanceModifier.size(20.dp), color = c.accent)
                }
            } else {
                WideButton("Scan for lights", c.accent, c.onAccent, actionRunCallback<RescanAction>(), tall)
            }
        }
        return
    }

    val blocked = gate.blocked
    val plan = WidgetLayout.layoutFor(size.width.value, size.height.value, lights.size)
    val onAction = if (blocked) actionRunCallback<RefreshAction>() else actionRunCallback<AllPowerAction>(actionParametersOf(OnKey to true))
    val offAction = if (blocked) actionRunCallback<RefreshAction>() else actionRunCallback<AllPowerAction>(actionParametersOf(OnKey to false))
    val gap = WidgetLayout.GAP.dp

    /** Row 1: All on, All off, optional inline toggles, optional Rescan. */
    @Composable
    fun Header(modifier: GlanceModifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            val inline = plan.inlineToggles > 0
            val bw = if (inline) GlanceModifier.width(WidgetLayout.COMPACT_BUTTON.dp) else GlanceModifier.defaultWeight()
            PowerButton(true, plan.shortLabels, allBusy, blocked, c, onAction, bw.fillMaxHeight())
            PowerButton(false, plan.shortLabels, allBusy, blocked, c, offAction, bw.fillMaxHeight().padding(start = gap))
            if (inline) {
                for (l in lights.take(plan.inlineToggles)) {
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = gap)) {
                        LightToggle(l, c, blocked, GlanceModifier.fillMaxSize())
                    }
                }
            }
            if (plan.showRescan) {
                Box(GlanceModifier.width(WidgetLayout.RESCAN.dp + gap).fillMaxHeight().padding(start = gap)) {
                    Box(
                        GlanceModifier.fillMaxSize().background(c.tile).cornerRadius(12.dp).clickable(actionRunCallback<RescanAction>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(GlanceModifier.size(18.dp), color = c.accent)
                        } else {
                            Image(ImageProvider(R.drawable.ic_refresh), "Rescan", GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(c.sub))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun BlockedLine() {
        if (blocked) {
            Text(
                if (gate is Gate.WrongWifi) "Not on your lights' Wi-Fi" else "Connect to Wi-Fi",
                GlanceModifier.padding(top = 4.dp),
                style = TextStyle(color = c.sub, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }

    when {
        !plan.grid -> Column(root) { Header(GlanceModifier.fillMaxWidth().fillMaxHeight()) }
        plan.fillRows > 0 -> Column(root) {
            // Everything fits: row 1 and each toggle row share the height evenly.
            Header(GlanceModifier.fillMaxWidth().defaultWeight())
            BlockedLine()
            for (row in lights.chunked(plan.gridColumns).take(plan.fillRows)) {
                Row(GlanceModifier.fillMaxWidth().defaultWeight().padding(top = gap)) {
                    for (i in 0 until plan.gridColumns) {
                        val l = row.getOrNull(i)
                        Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = if (i == 0) 0.dp else gap)) {
                            if (l != null) LightToggle(l, c, blocked, GlanceModifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        else -> Column(root) {
            Header(GlanceModifier.fillMaxWidth().height(WidgetLayout.HEADER.dp))
            BlockedLine()
            Spacer(GlanceModifier.height(gap))
            LazyVerticalGrid(GridCells.Fixed(plan.gridColumns), GlanceModifier.fillMaxSize()) {
                items(lights, itemId = { it.mac.hashCode().toLong() }) { l ->
                    Box(GlanceModifier.padding(bottom = 6.dp, end = 6.dp)) {
                        LightToggle(l, c, blocked, GlanceModifier.fillMaxWidth().height(WidgetLayout.GRID_CELL_HEIGHT.dp))
                    }
                }
            }
        }
    }
}

/** All on / All off use fixed green and red, whatever the theme, so they are never confused. */
private val OnGreen = ColorProvider(Color(0xFF2E7D32))
private val OffRed = ColorProvider(Color(0xFFC62828))
private val White = ColorProvider(Color.White)

@Composable
private fun PowerButton(
    on: Boolean, short: Boolean, busy: Boolean, blocked: Boolean,
    c: WColors, action: Action, modifier: GlanceModifier,
) {
    val fill = if (blocked) c.line else if (on) OnGreen else OffRed
    val ink = if (blocked) c.sub else White
    val label = when {
        short -> if (on) "ON" else "OFF"
        else -> if (on) "All on" else "All off"
    }
    Box(modifier) {
        Box(
            GlanceModifier.fillMaxSize().background(fill).cornerRadius(12.dp).clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(GlanceModifier.size(16.dp), color = ink)
            } else {
                Text(label, style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

/** On: the accent fill, like the app's filled button. Off: a plain tile with dimmed text. */
@Composable
private fun LightToggle(l: LightUi, c: WColors, blocked: Boolean, modifier: GlanceModifier) {
    val reachable = l.state.reachable && !blocked
    val on = reachable && l.state.on
    // On: accent fill. Off: a tile. Unreachable: sunk into the background with a dimmed name.
    val fill = when {
        on -> c.accent
        reachable -> c.tile
        else -> c.offTile
    }
    val ink = when {
        on -> c.onAccent
        reachable -> c.sub
        else -> c.dim
    }
    val action = if (blocked) actionRunCallback<RefreshAction>() else actionRunCallback<ToggleAction>(actionParametersOf(MacKey to l.mac))
    Row(
        modifier.background(fill).cornerRadius(10.dp).padding(horizontal = 8.dp).clickable(action),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (l.pending) {
            CircularProgressIndicator(GlanceModifier.size(16.dp), color = ink)
        } else {
            Image(ImageProvider(Ic.forLight(l.light.icon)), null, GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(if (on) c.onAccent else c.dim))
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            l.light.name,
            style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun WideButton(label: String, fill: ColorProvider, ink: ColorProvider, action: Action, tall: Boolean) {
    val h: Dp = if (tall) 48.dp else 40.dp
    Box(
        GlanceModifier.fillMaxWidth().height(h).background(fill).cornerRadius(12.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 2)
    }
}
