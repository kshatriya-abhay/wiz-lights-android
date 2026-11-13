package io.github.kshatriyaabhay.wizlights.data

import io.github.kshatriyaabhay.wizlights.wiz.Light
import io.github.kshatriyaabhay.wizlights.wiz.LightBook
import io.github.kshatriyaabhay.wizlights.wiz.LightIcon
import io.github.kshatriyaabhay.wizlights.wiz.LightState
import io.github.kshatriyaabhay.wizlights.wiz.Limits
import io.github.kshatriyaabhay.wizlights.wiz.Preset
import io.github.kshatriyaabhay.wizlights.wiz.Presets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal val StoreJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    explicitNulls = false
    encodeDefaults = true
}

/** A JSON file in app storage: size-capped reads, atomic writes (temp file + rename), one writer at a time. */
internal class JsonFile(private val file: File) {
    private val lock = Mutex()

    suspend fun read(): JsonElement? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!file.isFile || file.length() > Limits.MAX_FILE_BYTES) return@withLock null
            runCatching { StoreJson.parseToJsonElement(file.readText(Charsets.UTF_8)) }.getOrNull()
        }
    }

    suspend fun write(value: JsonElement) = withContext(Dispatchers.IO) {
        lock.withLock {
            file.parentFile?.mkdirs()
            val tmp = File.createTempFile(file.name + ".", ".tmp", file.parentFile)
            try {
                tmp.writeText(StoreJson.encodeToString(JsonElement.serializer(), value) + "\n", Charsets.UTF_8)
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                tmp.delete()
            }
        }
    }
}

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()

/** lights.json, same format as the plugin: {"lights":[{"mac","ip","name","moduleName"}]}. */
class LightStore(dir: File) {
    private val f = JsonFile(File(dir, "lights.json"))

    suspend fun load(): List<Light> = decode(f.read())

    suspend fun save(lights: List<Light>) = f.write(encode(lights))

    companion object {
        /** load_state(): skips bad entries, caps counts and lengths. */
        fun decode(root: JsonElement?): List<Light> {
            val arr = ((root as? JsonObject)?.get("lights") as? JsonArray) ?: return emptyList()
            return arr.take(Limits.MAX_LIGHTS).mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                LightBook.clean(Light(o.str("mac"), o.str("ip"), o.str("name"), o.str("moduleName"), LightIcon.clean(o.str("icon"))))
            }
        }

        fun encode(lights: List<Light>): JsonElement = buildJsonObject {
            put("lights", buildJsonArray {
                lights.take(Limits.MAX_LIGHTS).mapNotNull(LightBook::clean).forEach { l ->
                    add(buildJsonObject {
                        put("mac", l.mac); put("ip", l.ip); put("name", l.name); put("moduleName", l.moduleName)
                        if (l.icon != LightIcon.BULB) put("icon", l.icon)
                    })
                }
            })
        }
    }
}

/** presets.json, same format as the plugin: {"<mac>":[Preset, ...]}. */
class PresetStore(dir: File) {
    private val f = JsonFile(File(dir, "presets.json"))

    suspend fun load(): Map<String, List<Preset>> = decode(f.read())

    suspend fun save(all: Map<String, List<Preset>>) = f.write(encode(all))

    companion object {
        fun decode(root: JsonElement?): Map<String, List<Preset>> {
            val o = root as? JsonObject ?: return emptyMap()
            val out = LinkedHashMap<String, List<Preset>>()
            for ((mac, list) in o.entries.take(Limits.MAX_PRESET_MACS)) {
                if (mac.isEmpty()) continue
                val arr = list as? JsonArray ?: continue
                out[mac.take(Limits.MAX_FIELD_LEN)] = arr.take(Limits.MAX_PRESETS_PER_BULB).mapNotNull { e ->
                    val obj = e as? JsonObject ?: return@mapNotNull null
                    val withMode = if ("mode" in obj) obj else JsonObject(obj + ("mode" to JsonPrimitive("")))
                    runCatching { StoreJson.decodeFromJsonElement<Preset>(withMode) }.getOrNull()?.let(Presets::clean)
                }
            }
            return out
        }

        fun encode(all: Map<String, List<Preset>>): JsonElement = buildJsonObject {
            for ((mac, list) in all.entries.take(Limits.MAX_PRESET_MACS)) {
                put(mac.take(Limits.MAX_FIELD_LEN), StoreJson.encodeToJsonElement(list.take(Limits.MAX_PRESETS_PER_BULB).mapNotNull(Presets::clean)))
            }
        }
    }
}

@Serializable
data class Snapshot(val at: Long = 0, val states: Map<String, LightState> = emptyMap())

/** snapshot.json: the last known state, so the widget and a cold start draw at once. */
class SnapshotStore(dir: File) {
    private val f = JsonFile(File(dir, "snapshot.json"))

    suspend fun load(): Snapshot = f.read()?.let { runCatching { StoreJson.decodeFromJsonElement<Snapshot>(it) }.getOrNull() } ?: Snapshot()

    suspend fun save(s: Snapshot) = f.write(StoreJson.encodeToJsonElement(s))
}
