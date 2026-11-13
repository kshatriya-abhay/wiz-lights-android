package io.github.kshatriyaabhay.wizlights.wiz

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class WizClientTest {
    private val bulbs = mutableListOf<FakeBulb>()
    private fun bulb(mac: String = "a8bb50aabbcc", reply: Boolean = true, accept: Boolean = true) =
        FakeBulb(mac, reply = reply, accept = accept).also { bulbs += it }

    @After
    fun close() = bulbs.forEach { it.close() }

    private val loopbackSockets = UdpSockets { port, broadcast ->
        DatagramSocket(null as java.net.SocketAddress?).apply {
            this.broadcast = broadcast
            bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
        }
    }

    private fun client(port: Int, filter: TargetFilter = TargetFilter { it.isLoopbackAddress }) =
        WizClient(loopbackSockets, filter, port)

    @Test
    fun pilotReadsState() = runBlocking {
        val b = bulb()
        val s = client(b.port).pilot("127.0.0.1")
        assertTrue(s.reachable)
        assertTrue(s.on)
        assertEquals(80, s.dimming)
        assertEquals(2700, s.temp)
        assertEquals(-61, s.rssi)
        assertEquals(Mode.Temp, s.mode)
    }

    @Test
    fun pilotRetriesOnceThenGivesUp() = runBlocking {
        val b = bulb(reply = false)
        val t0 = System.currentTimeMillis()
        val s = client(b.port).pilot("127.0.0.1")
        val took = System.currentTimeMillis() - t0
        assertFalse(s.reachable)
        assertEquals(2, b.received.size)
        assertTrue("took $took", took in 1900..3500)
    }

    @Test
    fun sendCombinesStateWithValue() = runBlocking {
        val b = bulb()
        assertEquals(SendResult.Ok, client(b.port).send("127.0.0.1", Command.Brightness(5)))
        val params = b.received.single()["params"]!!.jsonObject
        assertEquals("true", params["state"]!!.jsonPrimitive.content)
        assertEquals("10", params["dimming"]!!.jsonPrimitive.content)
    }

    @Test
    fun sendReportsRejectAndTimeout() = runBlocking {
        val rejecting = bulb(accept = false)
        assertEquals(SendResult.Rejected, client(rejecting.port).send("127.0.0.1", Command.Power(true)))
        val silent = bulb(mac = "b", reply = false)
        assertEquals(SendResult.NoReply, client(silent.port).send("127.0.0.1", Command.Power(true), timeoutMs = 300))
    }

    @Test
    fun filterBlocksNonLocalTargets() = runBlocking {
        val b = bulb()
        val c = client(b.port, TargetFilter { false })
        assertEquals(SendResult.NotLocal, c.send("127.0.0.1", Command.Power(true)))
        assertFalse(c.pilot("127.0.0.1").reachable)
        assertTrue(b.received.isEmpty())
    }

    @Test
    fun hostnamesAreNeverResolved() = runBlocking {
        val b = bulb()
        assertEquals(SendResult.NoReply, client(b.port).send("localhost", Command.Power(true)))
        assertTrue(b.received.isEmpty())
    }

    @Test
    fun discoveryDeduplicatesByMac() = runBlocking {
        val b = bulb()
        val found = client(b.port).discover(listOf(InetAddress.getLoopbackAddress()), rounds = 3, listenMs = 300)
        assertEquals(1, found.size)
        assertEquals("a8bb50aabbcc", found[0].mac)
        assertEquals("127.0.0.1", found[0].ip)
        assertEquals("ESP03_SHRGB1C_31", found[0].moduleName)
        assertEquals(3, b.received.size)
    }

    @Test
    fun discoveryDropsRepliesTheFilterRejects() = runBlocking {
        val b = bulb()
        // The target passes, but no reply source does.
        var calls = 0
        val c = client(b.port, TargetFilter { calls++ == 0 })
        assertTrue(c.discover(listOf(InetAddress.getLoopbackAddress()), rounds = 1, listenMs = 300).isEmpty())
    }

    @Test
    fun setAllReportsFailures() = runBlocking {
        val ok = bulb(mac = "ok")
        val c = client(ok.port)
        val lights = listOf(Light("ok", "127.0.0.1"), Light("bad", "10.255.255.1"))
        val failed = WizClient(loopbackSockets, TargetFilter { it.isLoopbackAddress }, ok.port).setAll(lights, true)
        assertEquals(listOf("bad"), failed)
        assertEquals(1, c.snapshot(lights.take(1)).size)
    }
}
