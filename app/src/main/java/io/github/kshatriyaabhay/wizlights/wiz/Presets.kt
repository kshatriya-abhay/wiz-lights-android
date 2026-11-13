package io.github.kshatriyaabhay.wizlights.wiz

import kotlin.math.abs
import kotlin.math.roundToInt

/** Preset rules ported from wizctl.py and Panel.qml. Pure. */
object Presets {
    val defaults: List<Preset> = listOf(
        Preset(mode = Preset.TEMP, name = "Warm White", color = "#FFA757", hex = "#FFA757", temp = 2700),
        Preset(mode = Preset.TEMP, name = "Daylight", color = "#FFE4CE", hex = "#FFE4CE", temp = 5000),
        Preset(mode = Preset.TEMP, name = "Cool White", color = "#BCD4FF", hex = "#BCD4FF", temp = 6500),
        Preset(mode = Preset.TEMP, name = "Night Light", color = "#FF9227", hex = "#FF9227", temp = 2200),
    )

    /** Port of clean_preset(). Returns null for a preset that cannot be used. */
    fun clean(p: Preset): Preset? {
        var mode = p.mode
        if (mode !in setOf(Preset.TEMP, Preset.COLOR, Preset.SCENE)) {
            mode = when {
                p.temp != null -> Preset.TEMP
                p.r != null || p.hex != null || p.color != null -> Preset.COLOR
                p.sceneId != null -> Preset.SCENE
                else -> return null
            }
        }
        var color = p.color?.takeIf { it.length <= 32 }
        var hex = p.hex?.takeIf { it.length <= 32 }
        if (color == null && hex != null) color = hex
        if (hex == null && color != null) hex = color
        val name = p.name?.capped()
        return when (mode) {
            Preset.TEMP -> {
                val temp = (p.temp ?: 2700).coerceIn(Limits.TEMP_MIN, Limits.TEMP_MAX)
                Preset(mode, name, color ?: "#FFA757", hex ?: "#FFA757", temp = temp)
            }
            Preset.COLOR -> Preset(
                mode, name, color ?: "#FFFFFF", hex ?: "#FFFFFF",
                r = p.r?.coerceIn(0, 255), g = p.g?.coerceIn(0, 255), b = p.b?.coerceIn(0, 255),
                hue = p.hue, sat = p.sat,
            )
            else -> {
                val id = p.sceneId ?: 1
                if (!Scenes.isValid(id)) return null
                Preset(mode, name, color, hex, sceneId = id)
            }
        }
    }

    /** get_bulb_presets(): a bulb with an entry uses exactly that list (even empty); else the defaults. */
    fun forBulb(all: Map<String, List<Preset>>, mac: String): List<Preset> {
        val custom = all[mac] ?: return defaults
        return custom.take(Limits.MAX_PRESETS_PER_BULB).mapNotNull(::clean)
    }

    /** Builds the preset for the current white temperature. */
    fun fromTemp(kelvin: Int): Preset {
        val k = WizColor.clampTemp(kelvin)
        val dot = WizColor.kelvinToHex(k)
        return Preset(mode = Preset.TEMP, name = "${k}K", color = dot, hex = dot, temp = k)
    }

    /** Builds the preset for the current colour; [sat] is 0..1. */
    fun fromColor(hue: Double, sat: Double): Preset {
        val rgb = WizColor.hsvToRgb(hue, sat, 1.0)
        val hex = WizColor.rgbToHex(rgb)
        return Preset(
            mode = Preset.COLOR, name = hex, color = hex, hex = hex,
            r = rgb.r, g = rgb.g, b = rgb.b, hue = hue.roundToInt(), sat = (sat * 100).roundToInt(),
        )
    }

    /** save-preset: drops a preset with the same mode and temp/colour, puts the new one first, keeps 12. */
    fun save(all: Map<String, List<Preset>>, mac: String, preset: Preset): Map<String, List<Preset>> {
        val p = clean(preset) ?: return all
        val current = forBulb(all, mac).filterNot {
            it.mode == p.mode && if (p.mode == Preset.TEMP) it.temp == p.temp else it.color.equals(p.color, ignoreCase = true)
        }
        return all + (mac to (listOf(p) + current).take(Limits.MAX_PRESETS_PER_BULB))
    }

    /** delete-preset: a bulb without an entry gets a copy of the defaults first. */
    fun delete(all: Map<String, List<Preset>>, mac: String, index: Int): Map<String, List<Preset>> {
        val current = (all[mac] ?: defaults).toMutableList()
        if (index !in current.indices) return if (mac in all) all else all + (mac to current)
        current.removeAt(index)
        return all + (mac to current)
    }

    /**
     * Whether [preset] shows as active. [lastApplied] is the dot colour of the
     * preset last applied or saved this session, if any.
     */
    fun isActive(
        preset: Preset,
        on: Boolean,
        sceneActive: Boolean,
        lastApplied: String?,
        mode: Mode,
        currentTemp: Int,
        currentHex: String,
    ): Boolean {
        if (!on || sceneActive) return false
        if (lastApplied != null) return preset.dot.equals(lastApplied, ignoreCase = true)
        return when (preset.mode) {
            Preset.TEMP -> mode == Mode.Temp && preset.temp != null && abs(currentTemp - preset.temp) < 150
            Preset.COLOR -> mode == Mode.Color && preset.dot.equals(currentHex, ignoreCase = true)
            else -> false
        }
    }
}
