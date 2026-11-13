package io.github.kshatriyaabhay.wizlights.wiz

import kotlinx.serialization.Serializable

/** Limits ported from wizctl.py. Every value read from a bulb or a file is capped by these. */
object Limits {
    const val PORT = 38899
    const val MAX_LIGHTS = 64
    const val MAX_FIELD_LEN = 128
    const val MAX_PRESET_MACS = 64
    const val MAX_PRESETS_PER_BULB = 12
    const val MAX_FILE_BYTES = 1 shl 20
    const val RECEIVE_BUFFER = 4096

    const val TEMP_MIN = 2200
    const val TEMP_MAX = 6500
    const val DIM_MIN = 10
    const val DIM_MAX = 100

    const val PILOT_TIMEOUT_MS = 1000
    const val PILOT_RETRIES = 1
    const val SET_TIMEOUT_MS = 1500
    const val SET_ALL_TIMEOUT_MS = 1200
    const val PARALLELISM = 8
    const val DISCOVERY_ROUNDS = 3
    const val DISCOVERY_LISTEN_MS = 1500L
}

/** A saved bulb, as stored in lights.json (same format as the plugin). */
@Serializable
data class Light(
    val mac: String,
    val ip: String,
    val name: String = "",
    val moduleName: String = "",
)

/** A bulb that answered a discovery broadcast. */
data class Discovered(val mac: String, val ip: String, val moduleName: String)

enum class Mode { Scene, Color, Temp, None }

/** The state of a bulb from getPilot. An unreachable bulb has [reachable] false and [on] false. */
@Serializable
data class LightState(
    val reachable: Boolean = false,
    val on: Boolean = false,
    val dimming: Int? = null,
    val temp: Int? = null,
    val rgb: List<Int>? = null,
    val sceneId: Int = 0,
    val rssi: Int? = null,
) {
    val mode: Mode
        get() = when {
            sceneId > 0 -> Mode.Scene
            rgb != null -> Mode.Color
            temp != null && temp > 0 -> Mode.Temp
            else -> Mode.None
        }

    companion object {
        val Unreachable = LightState()
    }
}

/** What a bulb can do, guessed from its module name. */
data class Capabilities(val color: Boolean, val temp: Boolean) {
    companion object {
        val All = Capabilities(color = true, temp = true)

        fun of(moduleName: String): Capabilities {
            val m = moduleName.uppercase()
            return when {
                m.isBlank() -> All
                "RGB" in m -> All
                "TW" in m -> Capabilities(color = false, temp = true)
                else -> Capabilities(color = false, temp = false)
            }
        }
    }
}

/**
 * A preset stores a colour or a white temperature, never brightness. The
 * field set matches presets.json from the plugin.
 */
@Serializable
data class Preset(
    val mode: String,
    val name: String? = null,
    val color: String? = null,
    val hex: String? = null,
    val temp: Int? = null,
    val r: Int? = null,
    val g: Int? = null,
    val b: Int? = null,
    val hue: Int? = null,
    val sat: Int? = null,
    val sceneId: Int? = null,
) {
    /** The colour of the preset's dot. */
    val dot: String get() = color ?: hex ?: "#FFFFFF"

    companion object {
        const val TEMP = "temp"
        const val COLOR = "color"
        const val SCENE = "scene"
    }
}

/** Truncates to the field limit of wizctl.py. */
internal fun String.capped(): String = take(Limits.MAX_FIELD_LEN)
