package io.github.kshatriyaabhay.wizlights.wiz

/** Saved-light rules ported from wizctl.py. Pure. */
object LightBook {
    /** "Color Light XXXX" for RGB modules, else "WiZ Light XXXX", XXXX = last 4 of the MAC. */
    fun defaultName(moduleName: String, mac: String): String {
        val suffix = if (mac.length >= 4) mac.takeLast(4).uppercase() else ""
        val base = if ("RGB" in moduleName.uppercase()) "Color Light" else "WiZ Light"
        return "$base $suffix".trim()
    }

    /** Cleans one stored entry like clean_entry(): needs a mac and an ip; caps lengths. */
    fun clean(entry: Light): Light? {
        if (entry.mac.isEmpty() || entry.ip.isEmpty()) return null
        return Light(entry.mac.capped(), entry.ip.capped(), entry.name.capped(), entry.moduleName.capped(), LightIcon.clean(entry.icon))
    }

    /**
     * merge_discovered(): matches by MAC, updates ip and module, keeps the
     * user's name, names new bulbs, keeps unseen bulbs. Sorted by MAC, capped.
     */
    fun merge(saved: List<Light>, found: List<Discovered>): List<Light> {
        val byMac = LinkedHashMap<String, Light>()
        for (e in saved.take(Limits.MAX_LIGHTS)) byMac[e.mac] = e
        for (d in found.take(Limits.MAX_LIGHTS)) {
            val existing = byMac[d.mac]
            byMac[d.mac] = Light(
                mac = d.mac,
                ip = d.ip,
                name = existing?.name?.takeIf { it.isNotEmpty() } ?: defaultName(d.moduleName, d.mac),
                moduleName = d.moduleName.ifEmpty { existing?.moduleName.orEmpty() },
                icon = existing?.icon ?: LightIcon.BULB,
            )
        }
        return byMac.keys.sorted().map { byMac.getValue(it) }.take(Limits.MAX_LIGHTS)
    }

    fun rename(lights: List<Light>, mac: String, name: String): List<Light> {
        val n = name.trim().capped()
        require(n.isNotEmpty()) { "empty name" }
        return lights.map { if (it.mac == mac) it.copy(name = n) else it }
    }

    fun setIcon(lights: List<Light>, mac: String, icon: String): List<Light> =
        lights.map { if (it.mac == mac) it.copy(icon = LightIcon.clean(icon)) else it }

    fun forget(lights: List<Light>, mac: String): List<Light> = lights.filter { it.mac != mac }

    /** Display order: by name, case-insensitive, then MAC. */
    fun displayOrder(lights: List<Light>): List<Light> =
        lights.sortedWith(compareBy<Light> { it.name.lowercase() }.thenBy { it.mac })
}
