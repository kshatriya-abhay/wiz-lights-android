package io.github.kshatriyaabhay.wizlights.data

import io.github.kshatriyaabhay.wizlights.net.Gate
import io.github.kshatriyaabhay.wizlights.net.GateRules
import io.github.kshatriyaabhay.wizlights.net.LocalNetwork
import io.github.kshatriyaabhay.wizlights.net.SsidRead
import io.github.kshatriyaabhay.wizlights.net.SsidReader
import io.github.kshatriyaabhay.wizlights.net.SsidProblem
import io.github.kshatriyaabhay.wizlights.net.Transport
import io.github.kshatriyaabhay.wizlights.net.AddressGuard
import io.github.kshatriyaabhay.wizlights.net.DebugLog
import io.github.kshatriyaabhay.wizlights.wiz.Capabilities
import io.github.kshatriyaabhay.wizlights.wiz.Command
import io.github.kshatriyaabhay.wizlights.wiz.Light
import io.github.kshatriyaabhay.wizlights.wiz.LightBook
import io.github.kshatriyaabhay.wizlights.wiz.LightState
import io.github.kshatriyaabhay.wizlights.wiz.Preset
import io.github.kshatriyaabhay.wizlights.wiz.Presets
import io.github.kshatriyaabhay.wizlights.wiz.SendResult
import io.github.kshatriyaabhay.wizlights.wiz.WizClient
import io.github.kshatriyaabhay.wizlights.wiz.WizColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/** A light with everything the UI needs. */
data class LightUi(
    val light: Light,
    val state: LightState,
    val presets: List<Preset>,
    val pending: Boolean,
) {
    val mac: String get() = light.mac
    val caps: Capabilities get() = Capabilities.of(light.moduleName)
}

/** The outcome of a user action, for the snackbar or the widget. */
sealed interface ActionResult {
    data object Ok : ActionResult
    data class Failed(val message: String) : ActionResult
}

/**
 * The single entry point for the app and the widget. A process-wide
 * singleton (see WizApp), so both share state and never write the same file
 * at the same time. Behaviour ports Service.qml and Panel.qml.
 */
class WizRepository(
    private val scope: CoroutineScope,
    private val lightStore: LightStore,
    private val presetStore: PresetStore,
    private val snapshotStore: SnapshotStore,
    val settingsStore: SettingsStore,
    private val network: LocalNetwork,
    private val ssid: SsidReader,
    private val isForeground: () -> Boolean,
) {
    private val client = WizClient(network.sockets, network.filter)

    private val _lights = MutableStateFlow<List<Light>>(emptyList())
    private val _states = MutableStateFlow<Map<String, LightState>>(emptyMap())
    private val _presets = MutableStateFlow<Map<String, List<Preset>>>(emptyMap())
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    private val _scanning = MutableStateFlow(false)
    private val _allBusy = MutableStateFlow(false)
    private val _gate = MutableStateFlow<Gate>(Gate.Allowed)
    private val _error = MutableStateFlow<String?>(null)
    private val _loaded = MutableStateFlow(false)
    /** True after a scan that found no bulb at all, until the next scan finds one. */
    private val _scanFoundNothing = MutableStateFlow(false)

    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    val allBusy: StateFlow<Boolean> = _allBusy.asStateFlow()
    val gate: StateFlow<Gate> = _gate.asStateFlow()
    val lastError: StateFlow<String?> = _error.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    val scanFoundNothing: StateFlow<Boolean> = _scanFoundNothing.asStateFlow()
    val settings get() = settingsStore.settings

    val lights: StateFlow<List<LightUi>> = combine(_lights, _states, _presets, _pending) { lights, states, presets, pending ->
        LightBook.displayOrder(lights).map {
            LightUi(it, states[it.mac] ?: LightState.Unreachable, Presets.forBulb(presets, it.mac), it.mac in pending)
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val loadLock = Mutex()
    private val inFlight = AtomicInteger(0)
    @Volatile private var holdUntil = 0L

    fun clearError() {
        _error.value = null
    }

    private fun fail(msg: String): ActionResult.Failed {
        _error.value = msg
        return ActionResult.Failed(msg)
    }

    /** Loads the files once. Safe to call from anywhere, any number of times. */
    suspend fun ensureLoaded() {
        if (_loaded.value) return
        loadLock.withLock {
            if (_loaded.value) return
            _lights.value = lightStore.load()
            _presets.value = presetStore.load()
            _states.value = snapshotStore.load().states.filterKeys { mac -> _lights.value.any { it.mac == mac } }
            _loaded.value = true
        }
    }

    // ───────────── Gate ─────────────

    @Volatile private var gateAt = 0L

    /** Re-checks the gate at most every 3 s unless [force]d (for example after a settings change). */
    suspend fun checkGate(force: Boolean = false): Gate {
        val now = System.currentTimeMillis()
        if (!force && now - gateAt < 3000) return _gate.value
        val s = settingsStore.current()
        val net = network.current.value
        val read = if (s.restrictWifi && net?.transport == Transport.Wifi) ssid.read(isForeground()) else SsidRead.Unavailable(SsidProblem.Unknown)
        val g = GateRules.decide(s.restrictWifi, net?.transport, { read }, s.allowedSsids)
        _gate.value = g
        gateAt = now
        DebugLog.d("gate=$g restrict=${s.restrictWifi} transport=${net?.transport}")
        return g
    }

    suspend fun currentSsid(): SsidRead = ssid.read(foreground = true)

    private fun gateMessage(g: Gate): String = when (g) {
        Gate.Allowed -> ""
        Gate.NoLocalNetwork -> "Connect to Wi-Fi to control your lights."
        is Gate.WrongWifi -> "Not on your lights' Wi-Fi (connected to ${g.ssid})."
        is Gate.SsidUnavailable -> "Can't check the Wi-Fi name. Location permission is needed."
    }

    /** Runs [block] only when the gate allows it. */
    private suspend inline fun gated(block: () -> ActionResult): ActionResult {
        return try {
            ensureLoaded()
            val g = checkGate()
            if (g.blocked) return fail(gateMessage(g))
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never let a network or file error take the app down; show it instead.
            DebugLog.w("action failed", e)
            fail("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ───────────── Polling helpers ─────────────

    fun hold(ms: Long) {
        holdUntil = max(holdUntil, System.currentTimeMillis() + ms)
    }

    /** True when a poll tick may run now. */
    fun canPoll(): Boolean = !_scanning.value && inFlight.get() == 0 && System.currentTimeMillis() >= holdUntil

    private inline fun <T> tracked(block: () -> T): T {
        inFlight.incrementAndGet()
        try {
            return block()
        } finally {
            inFlight.decrementAndGet()
        }
    }

    // ───────────── Read ─────────────

    suspend fun refresh() {
        try {
            refreshInner()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("refresh failed", e)
        }
    }

    private suspend fun refreshInner() {
        ensureLoaded()
        if (checkGate().blocked) return
        val lights = _lights.value
        if (lights.isEmpty()) return
        val snap = client.snapshot(lights)
        // A command sent while this poll ran may already have changed a value; the hold protects sliders.
        if (System.currentTimeMillis() < holdUntil) {
            _states.update { cur -> snap.mapValues { (mac, s) -> if (s.reachable) cur[mac]?.takeIf { it.reachable } ?: s else s } }
        } else {
            _states.value = snap
        }
    }

    suspend fun scan(): ActionResult = gated {
        if (_scanning.value) return ActionResult.Ok
        _scanning.value = true
        val guard = scope.launch {
            delay(8000)
            _scanning.value = false
        }
        try {
            val net = network.current.value ?: return fail("Connect to Wi-Fi to control your lights.")
            val targets = listOf(net.subnet.broadcast, AddressGuard.LIMITED_BROADCAST).distinct()
            DebugLog.d("discover targets=${targets.map { it.hostAddress }}")
            val found = client.discover(targets)
            DebugLog.d("discover found=${found.size} ${found.map { "${it.mac}@${it.ip}" }}")
            val merged = LightBook.merge(_lights.value, found)
            _lights.value = merged
            lightStore.save(merged)
            _scanFoundNothing.value = merged.isEmpty()
        } finally {
            guard.cancel()
            _scanning.value = false
        }
        refresh()
        ActionResult.Ok
    }

    // ───────────── Power ─────────────

    private fun light(mac: String): Light? = _lights.value.firstOrNull { it.mac == mac }

    private fun patch(mac: String, f: (LightState) -> LightState) {
        _states.update { it + (mac to f(it[mac] ?: LightState.Unreachable)) }
    }

    private fun setPending(mac: String, on: Boolean) {
        _pending.update { if (on) it + mac else it - mac }
    }

    private fun describe(r: SendResult, name: String): String = when (r) {
        SendResult.Rejected -> "$name rejected the command"
        else -> "$name didn't respond"
    }

    suspend fun setPower(mac: String, on: Boolean): ActionResult = gated {
        val l = light(mac) ?: return ActionResult.Ok
        val before = _states.value[mac]
        patch(mac) { it.copy(on = on) }
        setPending(mac, true)
        val r = tracked { client.send(l.ip, Command.Power(on)) }
        setPending(mac, false)
        val result = if (r == SendResult.Ok) ActionResult.Ok else {
            before?.let { b -> patch(mac) { b } }
            fail(describe(r, l.name))
        }
        refresh()
        result
    }

    suspend fun setAllPower(on: Boolean): ActionResult = gated {
        val lights = _lights.value
        if (lights.isEmpty()) return ActionResult.Ok
        _allBusy.value = true
        val macs = lights.map { it.mac }.toSet()
        _states.update { cur -> cur.mapValues { (mac, s) -> if (mac in macs && s.reachable) s.copy(on = on) else s } }
        _pending.update { it + macs }
        val failed = try {
            tracked { client.setAll(lights, on) }
        } finally {
            _pending.update { it - macs }
            _allBusy.value = false
        }
        refresh()
        if (failed.isEmpty()) ActionResult.Ok else fail("${failed.size} of ${lights.size} bulbs failed to respond")
    }

    // ───────────── Throttled value commands ─────────────

    private class Throttle {
        var next: Command? = null
        var job: Job? = null
    }

    private val throttles = HashMap<String, Throttle>()
    private val throttleLock = Any()

    /**
     * Sends at most one command every 200 ms per light and control, always the
     * latest value. The last value submitted is always sent.
     */
    private fun submit(mac: String, control: String, cmd: Command) {
        val key = "$mac:$control"
        synchronized(throttleLock) {
            val t = throttles.getOrPut(key) { Throttle() }
            t.next = cmd
            if (t.job != null) return
            t.job = scope.launch {
                while (true) {
                    val c = synchronized(throttleLock) {
                        val n = t.next
                        t.next = null
                        if (n == null) t.job = null
                        n
                    } ?: break
                    val l = light(mac) ?: continue
                    if (checkGate().blocked) continue
                    hold(2000)
                    val start = System.currentTimeMillis()
                    val r = tracked { client.send(l.ip, c) }
                    if (r != SendResult.Ok) fail(describe(r, l.name))
                    hold(2000)
                    delay(max(0L, 200L - (System.currentTimeMillis() - start)))
                }
            }
        }
    }

    fun setBrightness(mac: String, percent: Int) {
        val p = percent.coerceIn(10, 100)
        patch(mac) { it.copy(on = true, dimming = p) }
        submit(mac, "dim", Command.Brightness(p))
    }

    fun setTemp(mac: String, kelvin: Int) {
        val k = WizColor.clampTemp(kelvin)
        patch(mac) { it.copy(on = true, temp = k, rgb = null, sceneId = 0) }
        submit(mac, "color", Command.Temp(k))
    }

    /** [sat] is 0..1. The bulb always gets full-value colour; brightness is separate. */
    fun setColor(mac: String, hue: Double, sat: Double) {
        val rgb = WizColor.hsvToRgb(hue, sat, 1.0)
        patch(mac) { it.copy(on = true, rgb = listOf(rgb.r, rgb.g, rgb.b), temp = null, sceneId = 0) }
        submit(mac, "color", Command.Color(rgb.r, rgb.g, rgb.b))
    }

    suspend fun setScene(mac: String, sceneId: Int): ActionResult = gated {
        val l = light(mac) ?: return ActionResult.Ok
        hold(2000)
        patch(mac) { it.copy(on = true, sceneId = sceneId) }
        val r = tracked { client.send(l.ip, Command.SceneCmd(sceneId)) }
        hold(2000)
        if (r == SendResult.Ok) ActionResult.Ok else fail(describe(r, l.name))
    }

    // ───────────── Presets ─────────────

    suspend fun applyPreset(mac: String, preset: Preset): ActionResult = gated {
        val l = light(mac) ?: return ActionResult.Ok
        hold(3000)
        val cmd: Command = if (preset.mode == Preset.TEMP && preset.temp != null) {
            val k = WizColor.clampTemp(preset.temp)
            patch(mac) { it.copy(on = true, temp = k, rgb = null, sceneId = 0) }
            Command.Temp(k)
        } else {
            val rgb = WizColor.hexToRgb(preset.dot) ?: return ActionResult.Ok
            patch(mac) { it.copy(on = true, rgb = listOf(rgb.r, rgb.g, rgb.b), temp = null, sceneId = 0) }
            Command.Color(rgb.r, rgb.g, rgb.b)
        }
        val r = tracked { client.send(l.ip, cmd) }
        hold(3000)
        if (r == SendResult.Ok) ActionResult.Ok else fail(describe(r, l.name))
    }

    suspend fun savePreset(mac: String, preset: Preset): ActionResult {
        ensureLoaded()
        val next = Presets.save(_presets.value, mac, preset)
        _presets.value = next
        presetStore.save(next)
        return ActionResult.Ok
    }

    suspend fun deletePreset(mac: String, index: Int): ActionResult {
        ensureLoaded()
        val next = Presets.delete(_presets.value, mac, index)
        _presets.value = next
        presetStore.save(next)
        return ActionResult.Ok
    }

    // ───────────── Book keeping ─────────────

    suspend fun rename(mac: String, name: String): ActionResult {
        ensureLoaded()
        if (name.isBlank()) return fail("empty name")
        val next = LightBook.rename(_lights.value, mac, name)
        _lights.value = next
        lightStore.save(next)
        return ActionResult.Ok
    }

    /** The edit dialog: a new name and icon in one save. */
    suspend fun editLight(mac: String, name: String, icon: String): ActionResult {
        ensureLoaded()
        if (name.isBlank()) return fail("empty name")
        val next = LightBook.setIcon(LightBook.rename(_lights.value, mac, name), mac, icon)
        _lights.value = next
        lightStore.save(next)
        return ActionResult.Ok
    }

    suspend fun forget(mac: String): ActionResult {
        ensureLoaded()
        val next = LightBook.forget(_lights.value, mac)
        _lights.value = next
        lightStore.save(next)
        if (mac in _presets.value) {
            _presets.value = _presets.value - mac
            presetStore.save(_presets.value)
        }
        _states.update { it - mac }
        return ActionResult.Ok
    }

    suspend fun saveSnapshot() {
        snapshotStore.save(Snapshot(System.currentTimeMillis(), _states.value))
    }
}
