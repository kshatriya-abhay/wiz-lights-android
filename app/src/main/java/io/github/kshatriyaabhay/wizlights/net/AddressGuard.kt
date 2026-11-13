package io.github.kshatriyaabhay.wizlights.net

import java.net.Inet4Address
import java.net.InetAddress

/** An IPv4 subnet: the phone's own address on it and the prefix length. Pure. */
data class Subnet(val address: Inet4Address, val prefix: Int) {
    init {
        require(prefix in 0..32) { "prefix $prefix" }
    }

    private val mask: Int = if (prefix == 0) 0 else (-1 shl (32 - prefix))
    private val self: Int = address.toInt()

    val network: Int get() = self and mask
    val broadcastInt: Int get() = network or mask.inv()

    val broadcast: Inet4Address get() = broadcastInt.toInet4()

    fun contains(a: Inet4Address): Boolean = (a.toInt() and mask) == network

    override fun toString(): String = "${network.toInet4().hostAddress}/$prefix"
}

/** The one place that decides whether a packet may be sent to (or accepted from) an address. */
object AddressGuard {
    val LIMITED_BROADCAST: Inet4Address = InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1)) as Inet4Address

    /**
     * True only for an IPv4 address inside [subnet] (this includes its
     * directed broadcast) or 255.255.255.255. A /31 or /32 subnet has no
     * room for bulbs, but the rule stays the same. A null subnet allows nothing.
     */
    fun allows(target: InetAddress, subnet: Subnet?): Boolean {
        if (subnet == null) return false
        val v4 = target as? Inet4Address ?: return false
        if (v4 == LIMITED_BROADCAST) return true
        // A subnet of /0 would mean "the internet"; never accept a prefix shorter than /8.
        if (subnet.prefix < 8) return false
        return subnet.contains(v4)
    }
}

internal fun Inet4Address.toInt(): Int {
    val b = address
    return ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
        ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
}

internal fun Int.toInet4(): Inet4Address = InetAddress.getByAddress(
    byteArrayOf((this ushr 24).toByte(), (this ushr 16).toByte(), (this ushr 8).toByte(), this.toByte()),
) as Inet4Address
