package io.github.kshatriyaabhay.wizlights.wiz

/** [color] is one static 0xRRGGBB that stands for the scene while its colours change. */
data class Scene(val id: Int, val name: String, val whiteOk: Boolean, val color: Int)

/** The scenes the UI offers, in the plugin's order. */
object Scenes {
    val all = listOf(
        Scene(10, "Bedtime", whiteOk = true, color = 0xFFA757),
        Scene(1, "Ocean", whiteOk = false, color = 0x1E88E5),
        Scene(2, "Romance", whiteOk = false, color = 0xE91E63),
        Scene(3, "Sunset", whiteOk = false, color = 0xFF7043),
        Scene(4, "Party", whiteOk = false, color = 0xAB47BC),
        Scene(5, "Fireplace", whiteOk = true, color = 0xFF6D00),
        Scene(6, "Cozy", whiteOk = true, color = 0xFFB74D),
        Scene(7, "Forest", whiteOk = false, color = 0x43A047),
        Scene(29, "Candlelight", whiteOk = true, color = 0xFFB347),
        Scene(31, "Pulse", whiteOk = true, color = 0xFFE4CE),
        Scene(32, "Steampunk", whiteOk = true, color = 0xC77B30),
        Scene(33, "Diwali", whiteOk = false, color = 0xFF8F00),
    )

    /** The scene ids WiZ bulbs accept: 1..36, 256..265 and 1000. */
    fun isValid(id: Int): Boolean = id in 1..36 || id in 256..265 || id == 1000

    fun forCapabilities(caps: Capabilities): List<Scene> = if (caps.color) all else all.filter { it.whiteOk }

    fun byId(id: Int): Scene? = all.firstOrNull { it.id == id }

    /** A warm white for scenes the UI doesn't list (WiZ has more than these 12). */
    const val OTHER_SCENE_COLOR = 0xFFC27A
}
