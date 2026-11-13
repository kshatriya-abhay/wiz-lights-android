package io.github.kshatriyaabhay.wizlights

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.kshatriyaabhay.wizlights.data.LightStore
import io.github.kshatriyaabhay.wizlights.data.PresetStore
import io.github.kshatriyaabhay.wizlights.data.SettingsStore
import io.github.kshatriyaabhay.wizlights.data.SnapshotStore
import io.github.kshatriyaabhay.wizlights.data.WizRepository
import io.github.kshatriyaabhay.wizlights.net.DebugLog
import io.github.kshatriyaabhay.wizlights.net.LocalNetwork
import io.github.kshatriyaabhay.wizlights.net.SsidReader
import io.github.kshatriyaabhay.wizlights.widget.LightsWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Manual DI: one repository per process, shared by the activity and the widget. */
class WizApp : Application() {
    /** Background work never crashes the process; failures are logged in debug builds. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> DebugLog.w("background job failed", e) },
    )
    private val foreground = AtomicBoolean(false)

    val repository: WizRepository by lazy {
        WizRepository(
            scope = scope,
            lightStore = LightStore(filesDir),
            presetStore = PresetStore(filesDir),
            snapshotStore = SnapshotStore(filesDir),
            settingsStore = SettingsStore(this, scope),
            network = LocalNetwork(this),
            ssid = SsidReader(this),
            isForeground = { foreground.get() },
        )
    }

    private var poller: Job? = null

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground.set(true)
                startPolling()
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground.set(false)
                poller?.cancel()
                poller = null
            }
        })

        val repo = repository
        // The widget redraws when anything it shows changes: names, on/off, reachability, busy flags, theme.
        scope.launch {
            combine(repo.lights, repo.scanning, repo.allBusy, repo.gate, repo.settings) { lights, scanning, busy, gate, settings ->
                listOf(lights.map { listOf(it.mac, it.light.name, it.state.on, it.state.reachable, it.pending) }, scanning, busy, gate, settings.themeName)
            }.distinctUntilChanged().drop(1).debounce(250).collect {
                runCatching { LightsWidget().updateAll(this@WizApp) }
            }
        }
        // The snapshot keeps the last known state for a cold start and the widget.
        scope.launch {
            repo.lights.map { l -> l.map { it.mac to it.state } }.distinctUntilChanged().drop(1).debounce(1000).collect {
                runCatching { repo.saveSnapshot() }
            }
        }
    }

    private fun startPolling() {
        poller?.cancel()
        poller = scope.launch {
            val repo = repository
            repo.ensureLoaded()
            runCatching { repo.refresh() }
            while (isActive) {
                delay(repo.settings.value.pollIntervalSec * 1000L)
                if (repo.canPoll()) runCatching { repo.refresh() }
            }
        }
    }

    companion object {
        fun of(context: Context): WizApp = context.applicationContext as WizApp
    }
}
