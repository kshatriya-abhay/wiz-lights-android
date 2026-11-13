package io.github.kshatriyaabhay.wizlights.wiz

import io.github.kshatriyaabhay.wizlights.data.PresetStore
import io.github.kshatriyaabhay.wizlights.data.LightStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {
    @Test
    fun defaultsUntilTheBulbHasAnEntry() {
        assertEquals(4, Presets.forBulb(emptyMap(), "a").size)
        assertEquals(0, Presets.forBulb(mapOf("a" to emptyList()), "a").size)
    }

    @Test
    fun saveDedupesAndPutsFirst() {
        var all = Presets.save(emptyMap(), "a", Presets.fromTemp(2700))
        // 2700K matches the default Warm White by temp, so it replaces it.
        assertEquals(listOf(2700, 5000, 6500, 2200), all["a"]!!.map { it.temp })
        assertEquals("2700K", all["a"]!![0].name)
        all = Presets.save(all, "a", Presets.fromColor(0.0, 1.0))
        assertEquals("#FF0000", all["a"]!![0].dot)
        all = Presets.save(all, "a", Presets.fromColor(0.0, 1.0))
        assertEquals(5, all["a"]!!.size)
        repeat(20) { all = Presets.save(all, "a", Presets.fromColor(it * 10.0, 1.0)) }
        assertEquals(12, all["a"]!!.size)
    }

    @Test
    fun deleteCopiesDefaultsFirst() {
        val all = Presets.delete(emptyMap(), "a", 0)
        assertEquals(listOf("Daylight", "Cool White", "Night Light"), all["a"]!!.map { it.name })
        assertEquals(all, Presets.delete(all, "a", 99))
    }

    @Test
    fun cleanRules() {
        assertEquals(6500, Presets.clean(Preset(mode = "temp", temp = 9000))!!.temp)
        assertEquals("#FFA757", Presets.clean(Preset(mode = "temp"))!!.color)
        assertEquals(Preset.COLOR, Presets.clean(Preset(mode = "", hex = "#123456"))!!.mode)
        assertEquals("#123456", Presets.clean(Preset(mode = "", hex = "#123456"))!!.color)
        assertEquals(255, Presets.clean(Preset(mode = "color", r = 999))!!.r)
        assertNull(Presets.clean(Preset(mode = "scene", sceneId = 99)))
        assertNull(Presets.clean(Preset(mode = "bogus")))
    }

    @Test
    fun activeRules() {
        val warm = Presets.defaults[0]
        assertTrue(Presets.isActive(warm, true, false, null, Mode.Temp, 2800, "#000000"))
        assertFalse(Presets.isActive(warm, true, false, null, Mode.Temp, 2850, "#000000"))
        assertFalse(Presets.isActive(warm, false, false, null, Mode.Temp, 2700, "#000000"))
        assertFalse(Presets.isActive(warm, true, true, null, Mode.Temp, 2700, "#000000"))
        assertTrue(Presets.isActive(warm, true, false, "#ffa757", Mode.Color, 0, "#000000"))
        val red = Presets.fromColor(0.0, 1.0)
        assertTrue(Presets.isActive(red, true, false, null, Mode.Color, 0, "#FF0000"))
    }

    @Test
    fun readsThePluginsFiles() {
        val presets = PresetStore.decode(Json.parseToJsonElement(
            """{"a8bb":[{"name":"Warm White","color":"#FFA757","mode":"temp","temp":2700,"hex":"#FFA757"},
                {"color":"#00FF00","r":0,"g":255,"b":0},{"mode":"temp","temp":"oops"},7],"": [], "x": "no"}""",
        ))
        assertEquals(setOf("a8bb"), presets.keys)
        assertEquals(listOf("temp", "color"), presets["a8bb"]!!.map { it.mode })
        val lights = LightStore.decode(Json.parseToJsonElement(
            """{"lights":[{"mac":"a","ip":"1.2.3.4","name":"Desk","moduleName":"M"},{"mac":"","ip":"x"},{"mac":"b","ip":"5","name":7},3]}""",
        ))
        assertEquals(listOf(Light("a", "1.2.3.4", "Desk", "M"), Light("b", "5", "", "")), lights)
        assertEquals(lights, LightStore.decode(LightStore.encode(lights)))
        assertEquals(presets, PresetStore.decode(PresetStore.encode(presets)))
    }
}
