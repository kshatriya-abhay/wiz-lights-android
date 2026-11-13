package io.github.kshatriyaabhay.wizlights.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.kshatriyaabhay.wizlights.net.GateRules
import io.github.kshatriyaabhay.wizlights.theme.StockThemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class Settings(
    val themeName: String = StockThemes.DEFAULT,
    val pollIntervalSec: Int = 15,
    val restrictWifi: Boolean = false,
    val allowedSsids: Set<String> = emptySet(),
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("themeName")
        val poll = intPreferencesKey("pollIntervalSec")
        val restrict = booleanPreferencesKey("restrictWifi")
        val ssids = stringSetPreferencesKey("allowedSsids")
    }

    private val flow = context.dataStore.data.map { p ->
        Settings(
            themeName = p[K.theme] ?: StockThemes.DEFAULT,
            pollIntervalSec = (p[K.poll] ?: 15).coerceIn(5, 600),
            restrictWifi = p[K.restrict] ?: false,
            allowedSsids = p[K.ssids].orEmpty(),
        )
    }

    val settings: StateFlow<Settings> = flow.stateIn(scope, SharingStarted.Eagerly, Settings())

    /** The settings once DataStore has read them; null before that, so callers can wait instead of drawing the defaults. */
    val ready: StateFlow<Settings?> = flow.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun current(): Settings = flow.first()

    suspend fun setTheme(name: String) = context.dataStore.edit { it[K.theme] = name }

    suspend fun setPollInterval(sec: Int) = context.dataStore.edit { it[K.poll] = sec.coerceIn(5, 600) }

    suspend fun setRestrict(on: Boolean) = context.dataStore.edit { it[K.restrict] = on }

    suspend fun addSsid(ssid: String) = context.dataStore.edit {
        val cur = it[K.ssids].orEmpty()
        if (cur.size < GateRules.MAX_SSIDS) it[K.ssids] = cur + ssid.take(64)
    }

    suspend fun removeSsid(ssid: String) = context.dataStore.edit { it[K.ssids] = it[K.ssids].orEmpty() - ssid }
}
