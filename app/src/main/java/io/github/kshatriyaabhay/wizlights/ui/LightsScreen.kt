package io.github.kshatriyaabhay.wizlights.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.kshatriyaabhay.wizlights.data.LightUi
import io.github.kshatriyaabhay.wizlights.data.WizRepository
import io.github.kshatriyaabhay.wizlights.net.Gate
import io.github.kshatriyaabhay.wizlights.net.SsidProblem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ScanPhrases = listOf("Scanning network", "Discovering bulbs", "Searching for lumens")
private val ActivePhrases = listOf(
    "Casting lumens", "Bending spectra", "Photons in flight", "Illuminating spaces",
    "Tuning kelvins", "Warming atmosphere", "Chasing shadows", "Painting with light",
)

/** The rotating line under the title, from the plugin's Panel.qml. */
@Composable
private fun heroLine(lights: List<LightUi>, scanning: Boolean): String {
    val reduce = LocalReduceMotion.current
    val on = lights.count { it.state.reachable && it.state.on }
    val count = "$on of ${lights.size} on"
    var tick by remember { mutableIntStateOf(0) }
    val rotating = !reduce && (scanning || on > 0)
    LaunchedEffect(rotating, scanning) {
        tick = 0
        while (rotating) {
            delay(2800)
            tick++
        }
    }
    return when {
        scanning && !reduce -> ScanPhrases[tick % ScanPhrases.size]
        scanning -> ScanPhrases[0]
        rotating -> (listOf(count) + ActivePhrases)[tick % (ActivePhrases.size + 1)]
        else -> count
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightsScreen(repo: WizRepository, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lights by repo.lights.collectAsState()
    val scanning by repo.scanning.collectAsState()
    val allBusy by repo.allBusy.collectAsState()
    val userRefreshing by repo.refreshing.collectAsState()
    val gate by repo.gate.collectAsState()
    val error by repo.lastError.collectAsState()
    val loaded by repo.loaded.collectAsState()
    val foundNothing by repo.scanFoundNothing.collectAsState()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        scope.launch {
            repo.ensureLoaded()
            repo.checkGate(force = true)
        }
        onPauseOrDispose { }
    }
    LaunchedEffect(error) {
        val e = error ?: return@LaunchedEffect
        snackbar.showSnackbar(e)
        repo.clearError()
    }

    val blocked = gate.blocked
    Scaffold(
        containerColor = Tn.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            SnackbarHost(snackbar) { Snackbar(it, containerColor = Tn.tileHi, contentColor = Tn.text, actionColor = Tn.accent) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = Gutter)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("WiZ Lights", Modifier.semantics { heading() }, size = 18, weight = FontWeight.SemiBold, lineHeight = 1.2f)
                        T(
                            heroLine(lights, scanning),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            size = 13, color = Tn.sub,
                        )
                    }
                    if (userRefreshing) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Spinner(Modifier.size(18.dp)) }
                    } else {
                        SquareButton(Ic.refresh, "Refresh lights", { scope.launch { repo.refreshNow() } }, enabled = !blocked && !scanning)
                    }
                    if (scanning) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Spinner(Modifier.size(18.dp)) }
                    } else {
                        SquareButton(Ic.scan, "Scan network for new lights", { scope.launch { repo.scan() } }, enabled = !blocked)
                    }
                    SquareButton(Ic.settings, "Settings", onSettings)
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        repo.checkGate(force = true)
                        repo.refresh()
                        refreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    val itemMod = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = Gutter)
                    if (blocked) item("gate") {
                        GateBanner(gate, itemMod, onSettings = onSettings, onWifi = {
                            runCatching { context.startActivity(Intent(Settings.Panel.ACTION_WIFI)) }
                        }, onLocation = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                        })
                    }
                    if (loaded && lights.isEmpty()) {
                        item("empty") {
                            EmptyTile(itemMod, scanning, foundNothing, enabled = !blocked) { scope.launch { repo.scan() } }
                        }
                    } else if (lights.isNotEmpty()) {
                        item("master") {
                            Tile(itemMod, enabled = !blocked) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    T("All lights", Modifier.weight(1f), size = 15, weight = FontWeight.SemiBold)
                                    WizButton("All on", {
                                        scope.launch { repo.setAllPower(true) }
                                    }, enabled = !blocked && allBusy == null, busy = allBusy == true)
                                    Spacer(Modifier.size(TileGap))
                                    WizButton("All off", {
                                        expanded = null
                                        scope.launch { repo.setAllPower(false) }
                                    }, kind = ButtonKind.Tonal, enabled = !blocked && allBusy == null, busy = allBusy == false)
                                }
                            }
                        }
                        items(lights, key = { it.mac }) { l ->
                            Box(itemMod) {
                                LightTile(
                                    ui = l,
                                    expanded = expanded == l.mac,
                                    enabled = !blocked,
                                    onToggleExpand = { expanded = if (expanded == l.mac) null else l.mac },
                                    repo = repo,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTile(modifier: Modifier, scanning: Boolean, foundNothing: Boolean, enabled: Boolean, onScan: () -> Unit) {
    Tile(modifier.padding(top = 24.dp), padding = PaddingValues(20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Sym(Ic.bulb, null, tint = Tn.accent, size = 40.dp)
            T("No lights yet", size = 22, weight = FontWeight.SemiBold)
            T("Scan your Wi-Fi for WiZ bulbs.", color = Tn.sub, align = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            WizButton("Scan", onScan, icon = Ic.refresh, enabled = enabled, busy = scanning)
            if (foundNothing && !scanning) {
                Spacer(Modifier.height(8.dp))
                T("No bulbs answered. Check that:", color = Tn.text, weight = FontWeight.Medium)
                for (hint in listOf(
                    "the phone and the bulbs are on the same network",
                    "the bulbs are on 2.4 GHz Wi-Fi",
                    "\"AP isolation\" / \"client isolation\" is off on the router",
                    "\"Allow local communication\" is on in the WiZ app",
                )) T("•  $hint", Modifier.fillMaxWidth(), size = 13, color = Tn.sub)
            }
        }
    }
}

@Composable
private fun GateBanner(gate: Gate, modifier: Modifier, onSettings: () -> Unit, onWifi: () -> Unit, onLocation: () -> Unit) {
    val (text, button, action) = when (gate) {
        Gate.NoLocalNetwork -> Triple("Connect to Wi-Fi to control your lights.", "Wi-Fi settings", onWifi)
        is Gate.WrongWifi -> Triple("Not on your lights' Wi-Fi (connected to ${gate.ssid}).", "Wi-Fi settings", onWifi)
        is Gate.SsidUnavailable -> when (gate.problem) {
            SsidProblem.LocationOff -> Triple("Can't check the Wi-Fi name. Location is turned off.", "Location settings", onLocation)
            else -> Triple("Can't check the Wi-Fi name. Location permission is needed.", "Grant permission", onSettings)
        }
        Gate.Allowed -> return
    }
    Tile(modifier, container = Tn.tileHi) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sym(if (gate == Gate.NoLocalNetwork) Ic.wifiOff else Ic.warning, null, tint = Tn.yellow, size = 22.dp)
            Spacer(Modifier.size(12.dp))
            T(text, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        WizButton(button, action, kind = ButtonKind.Tonal)
    }
}
