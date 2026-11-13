package io.github.kshatriyaabhay.wizlights.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GateRulesTest {
    private val home = setOf("Home", "Home-5G")
    private fun known(s: String) = { SsidRead.Known(s) }
    private val fail: () -> SsidRead = { throw AssertionError("SSID must not be read") }

    @Test
    fun decisionTable() {
        assertEquals(Gate.NoLocalNetwork, GateRules.decide(false, null, fail, home))
        assertEquals(Gate.NoLocalNetwork, GateRules.decide(true, null, fail, home))
        assertEquals(Gate.Allowed, GateRules.decide(false, Transport.Wifi, fail, home))
        assertEquals(Gate.Allowed, GateRules.decide(true, Transport.Ethernet, fail, home))
        assertEquals(Gate.Allowed, GateRules.decide(true, Transport.Wifi, known("Home-5G"), home))
        assertEquals(Gate.WrongWifi("Cafe"), GateRules.decide(true, Transport.Wifi, known("Cafe"), home))
        assertEquals(Gate.WrongWifi("Cafe"), GateRules.decide(true, Transport.Wifi, known("Cafe"), emptySet()))
        assertEquals(
            Gate.SsidUnavailable(SsidProblem.Background),
            GateRules.decide(true, Transport.Wifi, { SsidRead.Unavailable(SsidProblem.Background) }, home),
        )
    }

    @Test
    fun cleansSsids() {
        assertEquals("Home", GateRules.cleanSsid("\"Home\""))
        assertEquals("Home", GateRules.cleanSsid("Home"))
        assertNull(GateRules.cleanSsid("<unknown ssid>"))
        assertNull(GateRules.cleanSsid("\"\""))
        assertNull(GateRules.cleanSsid(null))
    }
}
