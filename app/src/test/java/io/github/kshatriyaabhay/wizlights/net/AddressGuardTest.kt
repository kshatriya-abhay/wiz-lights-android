package io.github.kshatriyaabhay.wizlights.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class AddressGuardTest {
    private fun a(s: String) = InetAddress.getByName(s)
    private fun subnet(ip: String, p: Int) = Subnet(a(ip) as Inet4Address, p)

    @Test
    fun slash24() {
        val s = subnet("192.168.1.42", 24)
        assertEquals("192.168.1.255", s.broadcast.hostAddress)
        assertTrue(AddressGuard.allows(a("192.168.1.7"), s))
        assertTrue(AddressGuard.allows(a("192.168.1.255"), s))
        assertTrue(AddressGuard.allows(a("255.255.255.255"), s))
        assertFalse(AddressGuard.allows(a("192.168.2.7"), s))
        assertFalse(AddressGuard.allows(a("10.0.0.1"), s))
        assertFalse(AddressGuard.allows(a("8.8.8.8"), s))
        assertFalse(AddressGuard.allows(a("::1"), s))
        assertFalse(AddressGuard.allows(a("fe80::1"), s))
    }

    @Test
    fun slash22() {
        val s = subnet("10.20.5.9", 22)
        assertEquals("10.20.7.255", s.broadcast.hostAddress)
        assertTrue(AddressGuard.allows(a("10.20.4.1"), s))
        assertTrue(AddressGuard.allows(a("10.20.7.254"), s))
        assertFalse(AddressGuard.allows(a("10.20.8.1"), s))
        assertFalse(AddressGuard.allows(a("10.20.3.255"), s))
    }

    @Test
    fun noSubnetOrHugeSubnetAllowsNothing() {
        assertFalse(AddressGuard.allows(a("192.168.1.1"), null))
        assertFalse(AddressGuard.allows(a("8.8.8.8"), subnet("8.8.4.4", 0)))
        assertFalse(AddressGuard.allows(a("8.8.8.8"), subnet("8.8.4.4", 7)))
    }
}
