package io.github.kshatriyaabhay.wizlights.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.kshatriyaabhay.wizlights.BuildConfig
import io.github.kshatriyaabhay.wizlights.data.WizRepository
import io.github.kshatriyaabhay.wizlights.net.GateRules
import io.github.kshatriyaabhay.wizlights.net.SsidReader
import io.github.kshatriyaabhay.wizlights.net.SsidRead
import io.github.kshatriyaabhay.wizlights.theme.PaletteSpec
import io.github.kshatriyaabhay.wizlights.theme.StockThemes
import kotlinx.coroutines.launch

fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private val PollChoices = listOf(5 to "5 s", 15 to "15 s", 30 to "30 s", 60 to "60 s", 300 to "5 min")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(repo: WizRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState()
    val store = repo.settingsStore
    val reader = remember { SsidReader(context) }

    // Permission and SSID state, re-read whenever the screen resumes (the user may come back from system settings).
    var hasLocation by remember { mutableStateOf(reader.hasLocation) }
    var hasBackground by remember { mutableStateOf(reader.hasBackground) }
    var locationOn by remember { mutableStateOf(reader.locationOn) }
    var currentSsid by remember { mutableStateOf<String?>(null) }
    var showRevokeHint by remember { mutableStateOf(false) }

    fun reread() {
        hasLocation = reader.hasLocation
        hasBackground = reader.hasBackground
        locationOn = reader.locationOn
        scope.launch {
            currentSsid = (repo.currentSsid() as? SsidRead.Known)?.ssid
            repo.checkGate(force = true)
        }
    }
    LifecycleResumeEffect(Unit) {
        reread()
        onPauseOrDispose { }
    }

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reread() }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            scope.launch {
                store.setRestrict(true)
                reread()
            }
        } else {
            reread()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Tn.bg).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = Gutter).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SquareButton(Ic.back, "Back", onBack)
                Spacer(Modifier.width(TileGap))
                T("Settings", Modifier.semantics { heading() }, size = 18, weight = FontWeight.SemiBold)
            }

            // Theme
            SectionLabel("Theme")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cols = if (maxWidth >= 480.dp) 3 else 2
                val cardWidth = (maxWidth - TileGap * (cols - 1)) / cols
                FlowRow(horizontalArrangement = Arrangement.spacedBy(TileGap), verticalArrangement = Arrangement.spacedBy(TileGap)) {
                    for (t in StockThemes.all) {
                        ThemeCard(
                            name = t.name,
                            palette = StockThemes.palette(t.name),
                            selected = settings.themeName == t.name,
                            modifier = Modifier.width(cardWidth),
                        ) { scope.launch { store.setTheme(t.name) } }
                    }
                }
            }

            // Refresh
            SectionLabel("Refresh")
            Tile(Modifier.fillMaxWidth()) {
                T("Check lights every", weight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((sec, label) in PollChoices) {
                        ChoiceChip(label, selected = settings.pollIntervalSec == sec, onClick = { scope.launch { store.setPollInterval(sec) } })
                    }
                }
                TileLabel("Only while the app is open.")
            }

            // Network
            SectionLabel("Network")
            Tile(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Only on chosen Wi-Fi networks", Modifier.weight(1f), weight = FontWeight.Medium)
                    Switch(
                        checked = settings.restrictWifi,
                        onCheckedChange = { want ->
                            if (want) {
                                showRevokeHint = false
                                if (reader.hasLocation) {
                                    scope.launch { store.setRestrict(true); reread() }
                                } else {
                                    fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
                            } else {
                                showRevokeHint = hasLocation
                                scope.launch { store.setRestrict(false); reread() }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Tn.onAccent, checkedTrackColor = Tn.accent, checkedBorderColor = Tn.accent,
                            uncheckedThumbColor = Tn.sub, uncheckedTrackColor = Tn.line, uncheckedBorderColor = Tn.dim,
                        ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                T(
                    "Scanning and control stop on other networks. Android needs Location permission to read the Wi-Fi name. " +
                        "Location is not used for anything else and never leaves the phone.",
                    size = 13, color = Tn.sub, lineHeight = 1.3f,
                )
                if (settings.restrictWifi) {
                    Spacer(Modifier.height(12.dp))
                    if (settings.allowedSsids.isEmpty()) {
                        T("No networks yet. Control is blocked on every Wi-Fi until you add one.", size = 13, color = Tn.yellow)
                    }
                    for (ssid in settings.allowedSsids.sorted()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Sym(Ic.wifi, null, tint = Tn.sub, size = 18.dp)
                            Spacer(Modifier.width(10.dp))
                            T(ssid, Modifier.weight(1f), family = Mono)
                            IconButton(onClick = { scope.launch { store.removeSsid(ssid); repo.checkGate(force = true) } }) {
                                Sym(Ic.close, "Remove $ssid", tint = Tn.sub, size = 20.dp)
                            }
                        }
                    }
                    val cur = currentSsid
                    if (cur != null && cur !in settings.allowedSsids && settings.allowedSsids.size < GateRules.MAX_SSIDS) {
                        Spacer(Modifier.height(4.dp))
                        WizButton("Add current Wi-Fi ($cur)", {
                            scope.launch { store.addSsid(cur); repo.checkGate(force = true) }
                        }, kind = ButtonKind.Tonal, icon = Ic.add)
                    }
                    Spacer(Modifier.height(12.dp))
                    PermissionLine("Location permission", hasLocation, if (!hasLocation) "Allow" else null) {
                        fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                    PermissionLine("Location services", locationOn, if (!locationOn) "Turn on" else null) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    }
                    PermissionLine("Background location, so the home-screen widget can check the Wi-Fi name", hasBackground,
                        if (hasLocation && !hasBackground) "Allow" else null) {
                        bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
                if (showRevokeHint && !settings.restrictWifi) {
                    Spacer(Modifier.height(10.dp))
                    T("The location permission is no longer needed. You can remove it in system settings.", size = 13, color = Tn.sub)
                    WizButton("App settings", { openAppSettings(context) }, kind = ButtonKind.Text)
                }
            }

            // About
            SectionLabel("About")
            Tile(Modifier.fillMaxWidth()) {
                T("WiZ Lights ${BuildConfig.VERSION_NAME}", weight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                T("This app talks only to devices on your local network. It has no internet features.", size = 13, color = Tn.sub)
                Spacer(Modifier.height(10.dp))
                for (line in listOf(
                    "License: GPL-3.0",
                    "WiZ protocol reference: kek's WiZ Light Controller (GPL-3.0)",
                    "Theme engine: Flux by Bjarne Oeverli (MIT)",
                    "Themes: Omarchy",
                    "Icons: Material Symbols (Apache-2.0), Lucide (ISC)",
                )) T(line, size = 12, color = Tn.sub, lineHeight = 1.4f)
                Spacer(Modifier.height(10.dp))
                T("WiZ is a trademark of Signify. This app is not affiliated with or endorsed by Signify.", size = 12, color = Tn.sub)
            }
        }
    }
}

@Composable
private fun PermissionLine(label: String, ok: Boolean, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(if (ok) Ic.check else Ic.warning, null, tint = if (ok) Tn.green else Tn.yellow, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        T(label, Modifier.weight(1f), size = 13, color = Tn.text)
        if (action != null) WizButton(action, onAction, kind = ButtonKind.Text)
    }
}

/** A swatch card drawn in its own palette. */
@Composable
private fun ThemeCard(name: String, palette: PaletteSpec, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    fun c(v: Int) = Color(0xFF000000.toInt() or v)
    Tile(
        modifier,
        onClick = onClick,
        container = c(palette.bg),
        border = if (selected) BorderStroke(2.dp, Tn.accent) else BorderStroke(1.dp, c(palette.line)),
        padding = PaddingValues(12.dp),
    ) {
        T(StockThemes.displayName(name), size = 13, color = c(palette.text), weight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (v in listOf(palette.accent, palette.green, palette.yellow, palette.red)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(c(v)))
            }
        }
    }
}
