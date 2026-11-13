package io.github.kshatriyaabhay.wizlights.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kshatriyaabhay.wizlights.data.LightUi
import io.github.kshatriyaabhay.wizlights.data.WizRepository
import io.github.kshatriyaabhay.wizlights.wiz.Limits
import io.github.kshatriyaabhay.wizlights.wiz.Mode
import io.github.kshatriyaabhay.wizlights.wiz.Preset
import io.github.kshatriyaabhay.wizlights.wiz.Presets
import io.github.kshatriyaabhay.wizlights.wiz.Scenes
import io.github.kshatriyaabhay.wizlights.wiz.WizColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The meta line: "module · ip · -61 dBm", or "offline · ip". */
fun metaText(l: LightUi): String {
    if (!l.state.reachable) return "offline · ${l.light.ip}"
    val parts = mutableListOf<String>()
    if (l.light.moduleName.isNotEmpty()) parts += l.light.moduleName
    if (l.light.ip.isNotEmpty()) parts += l.light.ip
    l.state.rssi?.takeIf { it != 0 }?.let { parts += "$it dBm" }
    return parts.joinToString(" · ")
}

/** Per-tile UI state that lives only for this session, as in the plugin's uiState. */
private class TileSession {
    var hue by mutableDoubleStateOf(35.0)
    var sat by mutableDoubleStateOf(1.0)
    var temp by mutableIntStateOf(2700)
    var tweaked by mutableStateOf(false)
    var saved by mutableStateOf(false)
    /** The dot colour of the preset last applied or saved this session. */
    var lastApplied by mutableStateOf<String?>(null)
    /** The mode the user last changed by hand, so Save knows what to store. */
    var tweakedMode by mutableStateOf<String?>(null)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LightTile(
    ui: LightUi,
    expanded: Boolean,
    enabled: Boolean,
    onToggleExpand: () -> Unit,
    repo: WizRepository,
) {
    val scope = rememberCoroutineScope()
    val s = ui.state
    val session = remember(ui.mac) { TileSession() }
    val reduce = LocalReduceMotion.current

    // Seed the colour and temperature controls from the bulb when it reports them.
    LaunchedEffect(ui.mac, s.rgb) {
        s.rgb?.let { (r, g, b) ->
            val cur = WizColor.hsvToRgb(session.hue, session.sat, 1.0)
            if (cur.r != r || cur.g != g || cur.b != b) {
                val hsv = WizColor.rgbToHsv(r, g, b)
                session.hue = hsv.h
                session.sat = hsv.s
            }
        }
    }
    LaunchedEffect(ui.mac, s.temp) { s.temp?.let { session.temp = it.coerceIn(Limits.TEMP_MIN, Limits.TEMP_MAX) } }

    val on = s.reachable && s.on
    val bulbTint = when {
        !on -> Tn.dim
        s.mode == Mode.Color -> s.rgb!!.let { (r, g, b) -> androidx.compose.ui.graphics.Color(r, g, b) }
        s.mode == Mode.Temp -> hexColor(WizColor.kelvinToHex(s.temp ?: 2700))
        else -> Tn.accent
    }

    var renaming by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Tile(
        modifier = Modifier.fillMaxWidth().alpha(if (s.reachable) 1f else DimAlpha),
        container = if (on) Tn.tile else Tn.offTile,
        border = BorderStroke(1.dp, if (expanded) Tn.lineHi else Tn.line),
        enabled = enabled,
        padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(enabled = enabled, role = Role.Button, onClick = {
                    if (s.reachable) onToggleExpand() else scope.launch { repo.refresh() }
                })
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(Ic.bulb, null, tint = bulbTint, size = 26.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(ui.light.name, size = 15, weight = FontWeight.SemiBold, maxLines = 1)
                T(metaText(ui), size = 12, color = Tn.sub, family = Mono, maxLines = 1)
            }
            if (expanded) {
                IconButton(onClick = { renaming = true }) { Sym(Ic.edit, "Rename ${ui.light.name}", tint = Tn.sub, size = 20.dp) }
                Box {
                    IconButton(onClick = { menu = true }) { Sym(Ic.more, "More for ${ui.light.name}", tint = Tn.sub, size = 20.dp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Tn.tileHi) {
                        DropdownMenuItem(
                            text = { T("Forget light", color = Tn.red) },
                            leadingIcon = { Sym(Ic.delete, null, tint = Tn.red, size = 20.dp) },
                            onClick = { menu = false; forgetting = true },
                        )
                    }
                }
            }
            Switch(
                checked = on,
                onCheckedChange = { want -> scope.launch { repo.setPower(ui.mac, want) } },
                enabled = enabled && s.reachable && !ui.pending,
                modifier = Modifier.semantics { contentDescription = "${ui.light.name}, ${if (on) "on" else "off"}" },
                thumbContent = if (ui.pending) {
                    { Spinner(Modifier.size(14.dp), color = Tn.accent) }
                } else null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Tn.onAccent, checkedTrackColor = Tn.accent, checkedBorderColor = Tn.accent,
                    uncheckedThumbColor = Tn.sub, uncheckedTrackColor = Tn.line, uncheckedBorderColor = Tn.dim,
                    disabledCheckedTrackColor = Tn.accent.copy(alpha = 0.5f), disabledCheckedThumbColor = Tn.onAccent,
                    disabledUncheckedTrackColor = Tn.line, disabledUncheckedThumbColor = Tn.dim, disabledUncheckedBorderColor = Tn.line,
                ),
            )
        }

        AnimatedVisibility(
            visible = expanded && s.reachable,
            enter = if (reduce) androidx.compose.animation.EnterTransition.None else expandVertically(tween(MOTION_MS)),
            exit = if (reduce) androidx.compose.animation.ExitTransition.None else shrinkVertically(tween(MOTION_MS)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ExpandedControls(ui, session, enabled, repo)
            }
        }
    }

    if (renaming) RenameDialog(ui.light.name, onDismiss = { renaming = false }) { name ->
        renaming = false
        scope.launch { repo.rename(ui.mac, name) }
    }
    if (forgetting) {
        AlertDialog(
            onDismissRequest = { forgetting = false },
            containerColor = Tn.tileHi,
            title = { T("Forget ${ui.light.name}?", size = 18, weight = FontWeight.SemiBold) },
            text = { T("You can find it again with a scan.", color = Tn.sub) },
            confirmButton = {
                WizButton("Forget", { forgetting = false; scope.launch { repo.forget(ui.mac) } }, kind = ButtonKind.Destructive)
            },
            dismissButton = { WizButton("Cancel", { forgetting = false }, kind = ButtonKind.Text) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ExpandedControls(ui: LightUi, session: TileSession, enabled: Boolean, repo: WizRepository) {
    val scope = rememberCoroutineScope()
    val s = ui.state
    val caps = ui.caps
    val on = s.on
    val sceneActive = s.sceneId > 0
    val currentHex = WizColor.rgbToHex(WizColor.hsvToRgb(session.hue, session.sat, 1.0))

    fun markTweaked(mode: String) {
        session.tweaked = true
        session.saved = false
        session.tweakedMode = mode
        session.lastApplied = null
    }

    // Presets
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TileLabel("Presets")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ui.presets.forEachIndexed { i, p ->
                if (p.mode == Preset.COLOR && !caps.color) return@forEachIndexed
                var menu by remember(p, i) { mutableStateOf(false) }
                val active = Presets.isActive(p, on, sceneActive, session.lastApplied, s.mode, session.temp, currentHex)
                Box {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .border(if (active) 2.dp else 1.dp, if (active) Tn.accent else Tn.line, CircleShape)
                            .padding(4.dp).clip(CircleShape)
                            .background(hexColor(p.dot))
                            .combinedClickable(
                                enabled = enabled,
                                role = Role.Button,
                                onClickLabel = "Apply",
                                onLongClickLabel = "Remove",
                                onLongClick = { menu = true },
                                onClick = {
                                    session.lastApplied = p.dot
                                    session.tweaked = false
                                    session.saved = false
                                    if (p.mode == Preset.TEMP && p.temp != null) {
                                        session.temp = WizColor.clampTemp(p.temp)
                                    } else if (p.hue != null && p.sat != null) {
                                        session.hue = p.hue.toDouble(); session.sat = p.sat / 100.0
                                    } else {
                                        WizColor.hexToRgb(p.dot)?.let { val h = WizColor.rgbToHsv(it.r, it.g, it.b); session.hue = h.h; session.sat = h.s }
                                    }
                                    scope.launch { repo.applyPreset(ui.mac, p) }
                                },
                            )
                            .semantics { contentDescription = "Preset ${p.name ?: p.dot}" },
                    )
                    if (!on) {
                        // Off: draw a desaturated veil like the plugin's grayscale filter.
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Tn.offTile.copy(alpha = 0.55f)))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Tn.tileHi) {
                        DropdownMenuItem(
                            text = { T("Remove", color = Tn.red) },
                            leadingIcon = { Sym(Ic.delete, null, tint = Tn.red, size = 20.dp) },
                            onClick = {
                                menu = false
                                if (session.lastApplied.equals(p.dot, ignoreCase = true)) session.lastApplied = null
                                scope.launch { repo.deletePreset(ui.mac, i) }
                            },
                        )
                    }
                }
            }
            if (session.tweaked || session.saved) {
                ChoiceChip(
                    label = if (session.saved) "Saved" else "Save",
                    selected = session.saved,
                    enabled = enabled && !session.saved,
                    leading = { Sym(if (session.saved) Ic.check else Ic.add, null, tint = if (session.saved) Tn.accent else Tn.sub, size = 16.dp) },
                    onClick = {
                        val mode = session.tweakedMode ?: if (s.mode == Mode.Color) Preset.COLOR else Preset.TEMP
                        val preset = if (mode == Preset.COLOR) Presets.fromColor(session.hue, session.sat) else Presets.fromTemp(session.temp)
                        session.saved = true
                        session.lastApplied = preset.dot
                        scope.launch {
                            repo.savePreset(ui.mac, preset)
                            delay(1000)
                            session.saved = false
                            session.tweaked = false
                        }
                    },
                )
            }
        }
    }

    // Scenes
    val scenes = Scenes.forCapabilities(caps)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TileLabel("Scenes")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (sc in scenes) {
                ChoiceChip(sc.name, selected = on && s.sceneId == sc.id, enabled = enabled, onClick = {
                    session.tweaked = false
                    session.lastApplied = null
                    scope.launch { repo.setScene(ui.mac, sc.id) }
                })
            }
        }
    }

    // Brightness
    val dim = (s.dimming ?: 100).coerceIn(Limits.DIM_MIN, Limits.DIM_MAX)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileLabel("Brightness", Modifier.weight(1f))
            T("$dim %", size = 12, color = Tn.sub, family = Mono)
        }
        ValueSlider(
            value = dim.toFloat(), range = 10f..100f, step = 1f,
            description = "Brightness", valueText = "$dim percent", enabled = enabled,
            onChange = { repo.setBrightness(ui.mac, it.roundToInt()) }, onRelease = {},
        )
    }

    // White temperature
    if (caps.temp) {
        val warm = hexColor(WizColor.kelvinToHex(Limits.TEMP_MIN))
        val cool = hexColor(WizColor.kelvinToHex(Limits.TEMP_MAX))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TileLabel("White", Modifier.weight(1f))
                T("${session.temp} K", size = 12, color = Tn.sub, family = Mono)
            }
            ValueSlider(
                value = session.temp.toFloat(), range = Limits.TEMP_MIN.toFloat()..Limits.TEMP_MAX.toFloat(), step = 100f,
                description = "White temperature", valueText = "${session.temp} kelvin", enabled = enabled,
                track = Brush.horizontalGradient(listOf(warm, cool)),
                onChange = {
                    val k = it.roundToInt()
                    if (k != session.temp || s.mode != Mode.Temp) {
                        session.temp = k
                        markTweaked(Preset.TEMP)
                        repo.setTemp(ui.mac, k)
                    }
                },
                onRelease = {},
            )
        }
    }

    // Colour
    if (caps.color) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TileLabel("Colour", Modifier.weight(1f))
                T(currentHex, size = 12, color = Tn.sub, family = Mono)
            }
            HueSatPlane(
                hue = session.hue, sat = session.sat, enabled = enabled,
                onChange = { h, sat ->
                    session.hue = h
                    session.sat = sat
                    markTweaked(Preset.COLOR)
                    repo.setColor(ui.mac, h, sat)
                },
                onRelease = {},
            )
        }
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tn.tileHi,
        title = { T("Rename light", size = 18, weight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(Limits.MAX_FIELD_LEN) },
                singleLine = true,
                label = { Text("Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Tn.accent, unfocusedBorderColor = Tn.dim,
                    focusedLabelColor = Tn.accent, unfocusedLabelColor = Tn.sub,
                    focusedTextColor = Tn.text, unfocusedTextColor = Tn.text, cursorColor = Tn.accent,
                ),
            )
        },
        confirmButton = { WizButton("Save", { onSave(text.trim()) }, enabled = text.isNotBlank(), kind = ButtonKind.Text) },
        dismissButton = { WizButton("Cancel", onDismiss, kind = ButtonKind.Text) },
    )
}
