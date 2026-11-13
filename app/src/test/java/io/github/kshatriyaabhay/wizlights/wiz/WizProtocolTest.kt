package io.github.kshatriyaabhay.wizlights.wiz

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WizProtocolTest {
    private fun params(cmd: Command, combined: Boolean = true) =
        Json.parseToJsonElement(WizProtocol.setPilot(cmd, combined).decodeToString()).jsonObject.let {
            assertEquals("\"setPilot\"", it["method"].toString())
            it["params"]!!.jsonObject.toString()
        }

    @Test
    fun encodesCommands() {
        assertEquals("""{"state":false}""", params(Command.Power(false)))
        assertEquals("""{"state":true,"dimming":72}""", params(Command.Brightness(72)))
        assertEquals("""{"state":true,"dimming":10}""", params(Command.Brightness(0)))
        assertEquals("""{"state":true,"temp":2700}""", params(Command.Temp(2690)))
        assertEquals("""{"state":true,"temp":6500}""", params(Command.Temp(9000)))
        assertEquals("""{"state":true,"r":255,"g":0,"b":12}""", params(Command.Color(300, -5, 12)))
        assertEquals("""{"state":true,"sceneId":29}""", params(Command.SceneCmd(29)))
        assertEquals("""{"temp":2200}""", params(Command.Temp(2200), combined = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidScene() {
        WizProtocol.setPilot(Command.SceneCmd(37))
    }

    @Test
    fun decodesPilotInColourMode() {
        val s = WizProtocol.pilot("""{"method":"getPilot","result":{"mac":"x","rssi":-55,"state":true,"sceneId":0,"r":255,"g":10,"b":300,"dimming":64}}""".encodeToByteArray())!!
        assertEquals(listOf(255, 10, 255), s.rgb)
        assertEquals(Mode.Color, s.mode)
        assertEquals(64, s.dimming)
        assertNull(s.temp)
    }

    @Test
    fun ignoresWrongTypes() {
        val s = WizProtocol.pilot("""{"result":{"state":"true","dimming":"50","temp":null,"sceneId":4}}""".encodeToByteArray())!!
        assertFalse(s.on)
        assertNull(s.dimming)
        assertEquals(Mode.Scene, s.mode)
    }

    @Test
    fun rejectsMalformedReplies() {
        for (bad in listOf("", "not json", "[]", """{"result":[]}""", """{"error":{"code":1}}""", "ÿþ")) {
            assertNull(bad, WizProtocol.pilot(bad.encodeToByteArray()))
        }
        assertNull(WizProtocol.result(ByteArray(5000) { 'a'.code.toByte() }))
        assertNull(WizProtocol.discovered("""{"result":{"moduleName":"x"}}""".encodeToByteArray(), 31, "1.2.3.4"))
        assertNull(WizProtocol.discovered("""{"result":{"mac":""}}""".encodeToByteArray(), 21, "1.2.3.4"))
    }

    @Test
    fun successNeedsTrue() {
        assertTrue(WizProtocol.isSuccess("""{"result":{"success":true}}""".encodeToByteArray()))
        assertFalse(WizProtocol.isSuccess("""{"result":{"success":"true"}}""".encodeToByteArray()))
        assertFalse(WizProtocol.isSuccess("""{"result":{}}""".encodeToByteArray()))
    }

    @Test
    fun capsLongStrings() {
        val long = "m".repeat(500)
        val bytes = """{"result":{"mac":"$long","moduleName":"$long"}}""".encodeToByteArray()
        val d = WizProtocol.discovered(bytes, bytes.size, "1.2.3.4")!!
        assertEquals(128, d.mac.length)
        assertEquals(128, d.moduleName.length)
    }

    @Test
    fun scenesValidity() {
        assertTrue(Scenes.isValid(1)); assertTrue(Scenes.isValid(36)); assertTrue(Scenes.isValid(1000)); assertTrue(Scenes.isValid(260))
        assertFalse(Scenes.isValid(0)); assertFalse(Scenes.isValid(37)); assertFalse(Scenes.isValid(266))
        assertEquals(12, Scenes.all.size)
        assertTrue(Scenes.all.all { Scenes.isValid(it.id) })
        assertEquals(listOf(10, 5, 6, 29, 31, 32), Scenes.forCapabilities(Capabilities(color = false, temp = true)).map { it.id })
    }

    @Test
    fun capabilitiesFromModule() {
        assertEquals(Capabilities.All, Capabilities.of("ESP03_SHRGB1C_31"))
        assertEquals(Capabilities(false, true), Capabilities.of("ESP06_SHTW1C_01"))
        assertEquals(Capabilities(false, false), Capabilities.of("ESP06_SHDW1_01"))
        assertEquals(Capabilities.All, Capabilities.of(""))
    }

    @Test
    fun macsAreCompared() {
        assertEquals("a8bb50aabbcc", WizProtocol.normalMac("A8:BB:50:AA:BB:CC"))
        assertEquals("a8bb50aabbcc", WizProtocol.pilotMac("""{"result":{"mac":"A8BB50AABBCC","state":true}}""".encodeToByteArray()))
        assertNull(WizProtocol.pilotMac("""{"result":{"state":true}}""".encodeToByteArray()))
        assertNull(WizProtocol.pilotMac("""{"result":{"mac":7}}""".encodeToByteArray()))
    }
}
