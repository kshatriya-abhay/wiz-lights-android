package io.github.kshatriyaabhay.wizlights.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.github.kshatriyaabhay.wizlights.wiz.TargetFilter
import io.github.kshatriyaabhay.wizlights.wiz.UdpSockets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

enum class Transport { Wifi, Ethernet }

/** The Wi-Fi or Ethernet network the app talks on. */
data class LocalNet(val network: Network, val transport: Transport, val subnet: Subnet)

/**
 * Tracks connected Wi-Fi and Ethernet networks (never cellular, never the
 * "default" network, which can be a VPN) and exposes the one to use:
 * Ethernet first, then Wi-Fi. IPv4 only.
 */
class LocalNetwork(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val known = ConcurrentHashMap<Network, Pair<NetworkCapabilities?, LinkProperties?>>()
    private val _current = MutableStateFlow<LocalNet?>(null)
    val current: StateFlow<LocalNet?> = _current.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            known[network] = caps to (known[network]?.second ?: cm.getLinkProperties(network))
            recompute()
        }

        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            known[network] = (known[network]?.first ?: cm.getNetworkCapabilities(network)) to lp
            recompute()
        }

        override fun onLost(network: Network) {
            known.remove(network)
            recompute()
        }
    }

    init {
        for (t in listOf(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_ETHERNET)) {
            val req = NetworkRequest.Builder()
                .addTransportType(t)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            runCatching { cm.registerNetworkCallback(req, callback) }
        }
        recompute()
    }

    private fun transportOf(caps: NetworkCapabilities?): Transport? = when {
        caps == null -> null
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> null
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.Ethernet
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.Wifi
        else -> null
    }

    private fun recompute() {
        val candidates = known.mapNotNull { (network, pair) ->
            val transport = transportOf(pair.first) ?: return@mapNotNull null
            val lp = pair.second ?: return@mapNotNull null
            val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return@mapNotNull null
            LocalNet(network, transport, Subnet(la.address as Inet4Address, la.prefixLength))
        }
        _current.value = candidates.minByOrNull { if (it.transport == Transport.Ethernet) 0 else 1 }
    }

    /** Sockets bound to the current local network, so packets never leave via cellular or a VPN. */
    val sockets = UdpSockets { localPort, broadcast ->
        val net = _current.value ?: throw IOException("no local network")
        val s = DatagramSocket(null as java.net.SocketAddress?)
        try {
            s.reuseAddress = true
            s.broadcast = broadcast
            s.bind(InetSocketAddress(localPort))
            net.network.bindSocket(s)
            s
        } catch (e: Exception) {
            s.close()
            throw (e as? IOException ?: IOException(e))
        }
    }

    /** Allows only the current subnet and 255.255.255.255. */
    val filter = TargetFilter { AddressGuard.allows(it, _current.value?.subnet) }
}
