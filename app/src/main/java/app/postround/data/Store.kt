package app.postround.data

import android.content.Context
import app.postround.domain.GameState
import app.postround.domain.Sky
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Saves the game as JSON in the app's files, atomically and durably: write aside, force it to the disk, then
 * rename, keeping the previous save as a spare. A sudden power-off then leaves either the old save or the new one,
 * never an empty file that would start the game over. Also keeps the raw
 * district map and a short log of the skies, so missed nights are replayed with the real weather.
 */
class Store(context: Context) {
    private val dir = context.filesDir
    private val stateFile = File(dir, "state.json")
    private val districtFile = File(dir, "district.json")
    private val tilesFile = File(dir, "district.bin")
    private val skiesFile = File(dir, "skies.json")

    /** The save; if it is missing mid-write, the synced new copy beside it; else the previous save. */
    fun load(): GameState? = sequenceOf(stateFile, File(dir, "state.json.tmp"), spare(stateFile))
        .firstNotNullOfOrNull { f -> runCatching { GameState.fromJson(JSONObject(f.readText())) }.getOrNull() }

    @Synchronized
    fun save(s: GameState) = write(stateFile, s.toJson().toString())

    fun district(): String? = districtFile.takeIf { it.exists() }?.readText()
    fun saveDistrict(json: String) = write(districtFile, json)

    /** The district as vector tiles (the normal case); the JSON above is only the demo's bundled Overpass dump. */
    fun tiles(): ByteArray? = tilesFile.takeIf { it.exists() }?.readBytes()
    fun saveTiles(b: ByteArray) = write(tilesFile, b)
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

    private fun write(f: File, text: String) = write(f, text.toByteArray())

    private fun write(f: File, bytes: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        FileOutputStream(tmp).use { out -> out.write(bytes); out.flush(); out.fd.sync() }
        if (f.exists()) f.renameTo(spare(f))
        tmp.renameTo(f)   // if this fails, load() still finds the spare or the synced .tmp
    }

    private fun spare(f: File) = File(f.parentFile, f.name + ".bak")
}
