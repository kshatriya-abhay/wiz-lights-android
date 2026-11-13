package io.github.kshatriyaabhay.wizlights.wiz

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList

/** A WiZ bulb on 127.0.0.1 that answers getSystemConfig, getPilot and setPilot. */
class FakeBulb(
    val mac: String = "a8bb50aabbcc",
    val module: String = "ESP03_SHRGB1C_31",
    var reply: Boolean = true,
    var accept: Boolean = true,
) : Closeable {
    val socket = DatagramSocket(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    val port: Int get() = socket.localPort
    val received = CopyOnWriteArrayList<JsonObject>()
    @Volatile var state = mutableMapOf<String, Any>("state" to true, "dimming" to 80, "temp" to 2700, "sceneId" to 0, "rssi" to -61)

    private val thread = Thread {
        val buf = ByteArray(4096)
        while (!socket.isClosed) {
            val p = DatagramPacket(buf, buf.size)
            try {
                socket.receive(p)
            } catch (_: SocketException) {
                break
            }
            val msg = Json.parseToJsonElement(String(buf, 0, p.length)).jsonObject
            received += msg
            if (!reply) continue
            val out = when (msg["method"]?.jsonPrimitive?.content) {
                "getSystemConfig" -> """{"method":"getSystemConfig","env":"pro","result":{"mac":"$mac","moduleName":"$module","fwVersion":"1.30.0"}}"""
                "getPilot" -> {
                    val body = state.entries.joinToString(",") { (k, v) -> "\"$k\":" + (if (v is String) "\"$v\"" else v.toString()) }
                    """{"method":"getPilot","env":"pro","result":{"mac":"$mac",$body}}"""
                }
                "setPilot" -> if (accept) """{"method":"setPilot","env":"pro","result":{"success":true}}""" else """{"method":"setPilot","error":{"code":-32600}}"""
                else -> continue
            }.encodeToByteArray()
            socket.send(DatagramPacket(out, out.size, p.socketAddress))
        }
    }.apply { isDaemon = true; start() }

    override fun close() {
        socket.close()
        thread.join(1000)
    }
}
