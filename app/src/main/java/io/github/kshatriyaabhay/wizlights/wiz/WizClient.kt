package io.github.kshatriyaabhay.wizlights.wiz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Opens UDP sockets. The Android implementation binds each socket to the Wi-Fi/Ethernet network. */
fun interface UdpSockets {
    /** Opens a socket bound to [localPort], or to an ephemeral port when [localPort] is 0. */
    @Throws(IOException::class)
    fun open(localPort: Int, broadcast: Boolean): DatagramSocket
}

/** Decides whether an address may be talked to. The Android implementation allows only the local subnet. */
fun interface TargetFilter {
    fun allows(address: InetAddress): Boolean
}

class NotLocalException(address: InetAddress) : IOException("not a local address: ${address.hostAddress}")

enum class SendResult { Ok, Rejected, NoReply, NotLocal }

/**
 * The WiZ UDP client. Pure JVM: sockets and the address filter are
 * injected, so the tests run it against a fake bulb on loopback.
 */
class WizClient(
    private val sockets: UdpSockets,
    private val filter: TargetFilter,
    private val port: Int = Limits.PORT,
    /** When true, value commands also carry `"state": true`. See WizProtocol.setPilot. */
    @Volatile var combinedState: Boolean = true,
) {
    /** Parses a literal IPv4 address. Never resolves a hostname. */
    private fun literal(ip: String): Inet4Address? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for (i in 0..3) {
            val n = parts[i].toIntOrNull() ?: return null
            if (n !in 0..255 || parts[i].length > 3) return null
            bytes[i] = n.toByte()
        }
        return InetAddress.getByAddress(bytes) as Inet4Address
    }

    /**
     * Sends [payload] to [ip] and waits up to [timeoutMs] for one reply from
     * that address. Returns the reply bytes, or null on timeout or error.
     */
    private suspend fun call(ip: String, payload: ByteArray, timeoutMs: Int): ByteArray? = withContext(Dispatchers.IO) {
        val target = literal(ip) ?: return@withContext null
        if (!filter.allows(target)) throw NotLocalException(target)
        try {
            sockets.open(0, broadcast = false).use { s ->
                s.soTimeout = timeoutMs
                s.send(DatagramPacket(payload, payload.size, target, port))
                val buf = ByteArray(Limits.RECEIVE_BUFFER)
                val deadline = System.nanoTime() + timeoutMs * 1_000_000L
                while (true) {
                    val left = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
                    if (left <= 0) return@withContext null
                    s.soTimeout = left
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    if (p.address == target) return@withContext buf.copyOf(p.length)
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        } catch (_: SocketTimeoutException) {
            null
        } catch (e: NotLocalException) {
            throw e
        } catch (_: IOException) {
            null
        }
    }

    /** getPilot with one retry. An unreachable bulb returns [LightState.Unreachable]. */
    suspend fun pilot(ip: String): LightState {
        repeat(Limits.PILOT_RETRIES + 1) {
            val reply = try {
                call(ip, WizProtocol.GET_PILOT, Limits.PILOT_TIMEOUT_MS)
            } catch (_: NotLocalException) {
                return LightState.Unreachable
            }
            if (reply != null) WizProtocol.pilot(reply)?.let { return it }
        }
        return LightState.Unreachable
    }

    suspend fun send(ip: String, cmd: Command, timeoutMs: Int = Limits.SET_TIMEOUT_MS): SendResult {
        val payload = WizProtocol.setPilot(cmd, combinedState)
        val reply = try {
            call(ip, payload, timeoutMs)
        } catch (_: NotLocalException) {
            return SendResult.NotLocal
        } ?: return SendResult.NoReply
        return if (WizProtocol.isSuccess(reply)) SendResult.Ok else SendResult.Rejected
    }

    /** getPilot for each light, [Limits.PARALLELISM] at a time. Keyed by MAC. */
    suspend fun snapshot(lights: List<Light>): Map<String, LightState> = parallel(lights.take(Limits.MAX_LIGHTS)) { it.mac to pilot(it.ip) }.toMap()

    /** Powers every light on or off in parallel. Returns the MACs that failed. */
    suspend fun setAll(lights: List<Light>, on: Boolean): List<String> =
        parallel(lights.take(Limits.MAX_LIGHTS)) { it.mac to send(it.ip, Command.Power(on), Limits.SET_ALL_TIMEOUT_MS) }
            .filter { it.second != SendResult.Ok }
            .map { it.first }

    private suspend fun <T, R> parallel(items: List<T>, block: suspend (T) -> R): List<R> = coroutineScope {
        val gate = Semaphore(Limits.PARALLELISM)
        items.map { async { gate.withPermit { block(it) } } }.awaitAll()
    }

    /**
     * Broadcast discovery, port of wizctl.py discover(): binds port 38899 when
     * it can (some firmwares only answer that source port), sends
     * getSystemConfig to each of [targets] per round, and collects replies by
     * MAC, first reply wins. Replies from addresses the filter rejects are dropped.
     */
    suspend fun discover(
        targets: List<InetAddress>,
        rounds: Int = Limits.DISCOVERY_ROUNDS,
        listenMs: Long = Limits.DISCOVERY_LISTEN_MS,
    ): List<Discovered> = withContext(Dispatchers.IO) {
        val allowed = targets.filter { filter.allows(it) }
        if (allowed.isEmpty()) return@withContext emptyList()
        val socket = try {
            sockets.open(port, broadcast = true)
        } catch (_: IOException) {
            sockets.open(0, broadcast = true)
        }
        val found = LinkedHashMap<String, Discovered>()
        socket.use { s ->
            val payload = WizProtocol.GET_SYSTEM_CONFIG
            val buf = ByteArray(Limits.RECEIVE_BUFFER)
            repeat(rounds) {
                for (t in allowed) {
                    try {
                        s.send(DatagramPacket(payload, payload.size, t, port))
                    } catch (_: IOException) {
                    }
                }
                val deadline = System.nanoTime() + listenMs * 1_000_000L
                while (true) {
                    val left = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
                    if (left <= 0) break
                    s.soTimeout = left
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(p)
                    } catch (_: SocketTimeoutException) {
                        break
                    } catch (_: IOException) {
                        continue
                    }
                    val from = p.address as? Inet4Address ?: continue
                    if (!filter.allows(from)) continue
                    val d = WizProtocol.discovered(buf, p.length, from.hostAddress ?: continue) ?: continue
                    if (d.mac !in found && found.size < Limits.MAX_LIGHTS) found[d.mac] = d
                }
            }
        }
        found.values.toList()
    }
}
