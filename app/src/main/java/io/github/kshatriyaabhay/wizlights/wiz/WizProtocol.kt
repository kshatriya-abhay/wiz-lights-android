package io.github.kshatriyaabhay.wizlights.wiz

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** A setPilot command. Every command but [Power] also sends `"state": true`, so a tweak turns the bulb on. */
sealed interface Command {
    data class Power(val on: Boolean) : Command
    data class Brightness(val percent: Int) : Command
    data class Temp(val kelvin: Int) : Command
    data class Color(val r: Int, val g: Int, val b: Int) : Command
    data class SceneCmd(val sceneId: Int) : Command
}

/** JSON encoding and decoding of the WiZ local UDP API. Pure; no I/O. */
object WizProtocol {
    private val json = Json { ignoreUnknownKeys = true }

    val GET_SYSTEM_CONFIG: ByteArray = """{"method":"getSystemConfig","params":{}}""".encodeToByteArray()
    val GET_PILOT: ByteArray = """{"method":"getPilot","params":{}}""".encodeToByteArray()

    /**
     * Encodes a setPilot command. With [combined] true (the default), value
     * commands carry `"state": true`; with false they send only the value key,
     * like the plugin does.
     */
    fun setPilot(cmd: Command, combined: Boolean = true): ByteArray {
        val params = buildJsonObject {
            when (cmd) {
                is Command.Power -> put("state", cmd.on)
                is Command.Brightness -> {
                    if (combined) put("state", true)
                    put("dimming", cmd.percent.coerceIn(Limits.DIM_MIN, Limits.DIM_MAX))
                }
                is Command.Temp -> {
                    if (combined) put("state", true)
                    put("temp", WizColor.clampTemp(cmd.kelvin))
                }
                is Command.Color -> {
                    if (combined) put("state", true)
                    put("r", cmd.r.coerceIn(0, 255))
                    put("g", cmd.g.coerceIn(0, 255))
                    put("b", cmd.b.coerceIn(0, 255))
                }
                is Command.SceneCmd -> {
                    require(Scenes.isValid(cmd.sceneId)) { "invalid sceneId: ${cmd.sceneId}" }
                    if (combined) put("state", true)
                    put("sceneId", cmd.sceneId)
                }
            }
        }
        val msg = buildJsonObject {
            put("method", "setPilot")
            put("params", params)
        }
        return msg.toString().encodeToByteArray()
    }

    /** The `result` object of a reply, or null when the reply is not JSON or has no object result. */
    fun result(bytes: ByteArray, length: Int = bytes.size): JsonObject? {
        if (length <= 0 || length > Limits.RECEIVE_BUFFER) return null
        val text = runCatching { bytes.decodeToString(0, length, throwOnInvalidSequence = true) }.getOrNull() ?: return null
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        return root["result"] as? JsonObject
    }

    /** A setPilot reply is a success when `result.success` is true. */
    fun isSuccess(bytes: ByteArray, length: Int = bytes.size): Boolean =
        result(bytes, length)?.bool("success") == true

    /** Decodes a getSystemConfig reply. [ip] is the datagram's source address. */
    fun discovered(bytes: ByteArray, length: Int, ip: String): Discovered? {
        val r = result(bytes, length) ?: return null
        val mac = r.string("mac")?.takeIf { it.isNotEmpty() }?.capped() ?: return null
        return Discovered(mac = mac, ip = ip.capped(), moduleName = r.string("moduleName")?.capped().orEmpty())
    }

    /** The `mac` a getPilot reply names, normalised, or null when the reply has none. */
    fun pilotMac(bytes: ByteArray, length: Int = bytes.size): String? =
        result(bytes, length)?.string("mac")?.let(::normalMac)?.takeIf { it.isNotEmpty() }

    /** Lower case without separators, so "A8:BB:50:AA:BB:CC" matches "a8bb50aabbcc". */
    fun normalMac(mac: String): String = mac.lowercase().filter { it.isLetterOrDigit() }

    /** Decodes a getPilot reply into a state. Numbers are clamped; wrong types are ignored. */
    fun pilot(bytes: ByteArray, length: Int = bytes.size): LightState? {
        val r = result(bytes, length) ?: return null
        val rr = r.int("r")
        val gg = r.int("g")
        val bb = r.int("b")
        val rgb = if (rr != null && gg != null && bb != null) listOf(rr, gg, bb).map { it.coerceIn(0, 255) } else null
        return LightState(
            reachable = true,
            on = r.bool("state") ?: false,
            dimming = r.int("dimming")?.coerceIn(0, 100),
            temp = r.int("temp")?.takeIf { it > 0 }?.coerceIn(1000, 10000),
            rgb = rgb,
            sceneId = r.int("sceneId")?.coerceIn(0, 100000) ?: 0,
            rssi = r.int("rssi")?.coerceIn(-200, 0),
        )
    }

    private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    /** A real JSON boolean only; the string "true" does not count. */
    private fun JsonObject.bool(key: String): Boolean? = prim(key)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.string(key: String): String? = prim(key)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.int(key: String): Int? {
        val p = prim(key) ?: return null
        if (p.isString) return null
        val d = p.doubleOrNull ?: return null
        if (!d.isFinite()) return null
        return d.coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
    }
}
