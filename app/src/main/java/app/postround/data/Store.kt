package app.postround.data

import android.content.Context
import app.postround.domain.GameState
import app.postround.domain.Sky
import org.json.JSONObject
import java.io.File

/**
 * Saves the game as JSON in the app's files, atomically (write aside, then rename). Also keeps the raw
 * district map and a short log of the skies, so missed nights are replayed with the real weather.
 */
class Store(context: Context) {
    private val dir = context.filesDir
    private val stateFile = File(dir, "state.json")
    private val districtFile = File(dir, "district.json")
    private val tilesFile = File(dir, "district.bin")
    private val skiesFile = File(dir, "skies.json")

    fun load(): GameState? = runCatching { GameState.fromJson(JSONObject(stateFile.readText())) }.getOrNull()

    @Synchronized
    fun save(s: GameState) = write(stateFile, s.toJson().toString())

    fun district(): String? = districtFile.takeIf { it.exists() }?.readText()
    fun saveDistrict(json: String) = write(districtFile, json)

    /** The district as vector tiles (the normal case); the JSON above is only the demo's bundled Overpass dump. */
    fun tiles(): ByteArray? = tilesFile.takeIf { it.exists() }?.readBytes()
    fun saveTiles(b: ByteArray) { val tmp = File(dir, "district.bin.tmp"); tmp.writeBytes(b); if (!tmp.renameTo(tilesFile)) { tilesFile.delete(); tmp.renameTo(tilesFile) } }
    fun hasDistrict() = tilesFile.exists() || districtFile.exists()

    /** Remembers the sky of a day (epoch day → sky), keeping the last 60 days. */
    @Synchronized
    fun remember(day: Long, sky: Sky) {
        val o = runCatching { JSONObject(skiesFile.readText()) }.getOrDefault(JSONObject())
        o.put(day.toString(), JSONObject().put("r", sky.rain).put("s", sky.snow).put("f", sky.frost).put("g", sky.fog).put("u", sky.sun).put("h", sky.heat).put("t", sky.temp))
        o.keys().asSequence().toList().filter { (it.toLongOrNull() ?: 0) < day - 60 }.forEach { o.remove(it) }
        write(skiesFile, o.toString())
    }

    fun sky(day: Long): Sky? {
        val o = runCatching { JSONObject(skiesFile.readText()) }.getOrNull()?.optJSONObject(day.toString()) ?: return null
        return Sky(o.optBoolean("r"), o.optBoolean("s"), o.optBoolean("f"), o.optBoolean("g"), o.optBoolean("u"), o.optBoolean("h"), o.optDouble("t", 10.0))
    }

    private fun write(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
