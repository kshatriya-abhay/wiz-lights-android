package io.github.kshatriyaabhay.wizlights.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class SsidProblem { Permission, LocationOff, Background, Unknown }

sealed interface SsidRead {
    data class Known(val ssid: String) : SsidRead
    data class Unavailable(val problem: SsidProblem) : SsidRead
}

sealed interface Gate {
    data object Allowed : Gate
    data object NoLocalNetwork : Gate
    data class WrongWifi(val ssid: String) : Gate
    data class SsidUnavailable(val problem: SsidProblem) : Gate

    val blocked: Boolean get() = this != Allowed
}

/** The pure decision of section 4.4 of the design. */
object GateRules {
    const val MAX_SSIDS = 5

    fun decide(restrict: Boolean, transport: Transport?, ssid: () -> SsidRead, allowed: Set<String>): Gate {
        if (transport == null) return Gate.NoLocalNetwork
        if (!restrict || transport == Transport.Ethernet) return Gate.Allowed
        return when (val s = ssid()) {
            is SsidRead.Known -> if (s.ssid in allowed) Gate.Allowed else Gate.WrongWifi(s.ssid)
            is SsidRead.Unavailable -> Gate.SsidUnavailable(s.problem)
        }
    }

    /** Strips the quotes Android puts around an SSID; "<unknown ssid>" and blanks are null. */
    fun cleanSsid(raw: String?): String? {
        val s = raw?.trim()?.removeSurrounding("\"") ?: return null
        if (s.isEmpty() || s == "<unknown ssid>") return null
        return s
    }
}

/** Reads the connected Wi-Fi's SSID, which Android only reveals to apps with location access. */
class SsidReader(private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    val hasLocation: Boolean get() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val hasBackground: Boolean get() = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    val locationOn: Boolean
        get() = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true

    suspend fun read(foreground: Boolean): SsidRead {
        if (!hasLocation) return SsidRead.Unavailable(SsidProblem.Permission)
        if (!foreground && !hasBackground) return SsidRead.Unavailable(SsidProblem.Background)
        if (!locationOn) return SsidRead.Unavailable(SsidProblem.LocationOff)
        val raw = if (Build.VERSION.SDK_INT >= 31) readWithCallback() else readLegacy()
        return GateRules.cleanSsid(raw)?.let { SsidRead.Known(it) } ?: SsidRead.Unavailable(SsidProblem.Unknown)
    }

    @Suppress("DEPRECATION")
    private fun readLegacy(): String? =
        context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid

    @RequiresApi(31)
    private suspend fun readWithCallback(): String? {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        var cb: ConnectivityManager.NetworkCallback? = null
        return try {
            withTimeoutOrNull(1000) {
                suspendCancellableCoroutine { cont ->
                    val c = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                            val ssid = (caps.transportInfo as? WifiInfo)?.ssid
                            if (GateRules.cleanSsid(ssid) != null && cont.isActive) cont.resume(ssid)
                        }
                    }
                    cb = c
                    try {
                        cm.registerNetworkCallback(req, c)
                    } catch (_: Exception) {
                        cb = null
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } finally {
            cb?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        }
    }
}
