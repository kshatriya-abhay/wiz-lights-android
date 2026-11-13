package io.github.kshatriyaabhay.wizlights.wiz

data class Scene(val id: Int, val name: String, val whiteOk: Boolean)

/** The scenes the UI offers, in the plugin's order. */
object Scenes {
    val all = listOf(
        Scene(10, "Bedtime", whiteOk = true),
        Scene(1, "Ocean", whiteOk = false),
        Scene(2, "Romance", whiteOk = false),
        Scene(3, "Sunset", whiteOk = false),
        Scene(4, "Party", whiteOk = false),
        Scene(5, "Fireplace", whiteOk = true),
        Scene(6, "Cozy", whiteOk = true),
        Scene(7, "Forest", whiteOk = false),
        Scene(29, "Candlelight", whiteOk = true),
        Scene(31, "Pulse", whiteOk = true),
        Scene(32, "Steampunk", whiteOk = true),
        Scene(33, "Diwali", whiteOk = false),
    )

    /** The scene ids WiZ bulbs accept: 1..36, 256..265 and 1000. */
    fun isValid(id: Int): Boolean = id in 1..36 || id in 256..265 || id == 1000

    fun forCapabilities(caps: Capabilities): List<Scene> = if (caps.color) all else all.filter { it.whiteOk }

    fun byId(id: Int): Scene? = all.firstOrNull { it.id == id }
}
