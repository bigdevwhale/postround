package app.postround.data

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * The district from vector tiles (OpenFreeMap: OpenStreetMap cut into z14 squares, served from a CDN for apps,
 * no key). A district is 4–9 tiles of 30–300 KB that download in seconds, unlike Overpass, which is meant for
 * people at a browser and turns automated requests away when busy.
 */
object Tiles {
    /** Landuse a postman doesn't walk into, from the OpenMapTiles schema (#4). */
    private val SHUT = setOf("industrial", "military", "railway", "garages", "quarry")

    const val Z = 14
    private const val AGENT = "Postround/1.0 (github.com/bigdevwhale/postround)"
    private const val TILEJSON = "https://tiles.openfreemap.org/planet"

    fun tileX(lon: Double) = floor((lon + 180) / 360 * (1 shl Z)).toInt()
    fun tileY(lat: Double): Int { val r = Math.toRadians(lat); return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * (1 shl Z)).toInt() }
    fun lon(tx: Double) = tx / (1 shl Z) * 360 - 180
    fun lat(ty: Double) = Math.toDegrees(atan(sinh(PI * (1 - 2 * ty / (1 shl Z)))))

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", AGENT)
        c.setRequestProperty("Accept-Encoding", "gzip")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode} at ${URL(url).host}")
            val raw = c.inputStream.use { it.readBytes() }
            return if (c.contentEncoding == "gzip" || (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte())) GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() } else raw
        } finally { c.disconnect() }
    }

    /** Downloads every tile covering the box (s, w, n, e) and packs them into one blob for the store. */
    fun download(box: DoubleArray): ByteArray {
        val template = JSONObject(String(get(TILEJSON))).getJSONArray("tiles").getString(0)
        val x0 = tileX(box[1]); val x1 = tileX(box[3]); val y0 = tileY(box[2]); val y1 = tileY(box[0])
        val keys = (x0..x1).flatMap { x -> (y0..y1).map { y -> x to y } }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = keys.map { (x, y) ->
                pool.submit<ByteArray> {
                    val url = template.replace("{z}", "$Z").replace("{x}", "$x").replace("{y}", "$y")
                    var last: Exception? = null
                    repeat(3) { attempt ->
                        try { return@submit get(url) } catch (e: Exception) { last = e; Thread.sleep(800L * (attempt + 1)) }
                    }
                    throw last!!
                }
            }
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { o ->
                o.writeInt(0x47535431) // "GST1"
                o.writeInt(keys.size)
                keys.forEachIndexed { i, (x, y) -> val b = jobs[i].get(); o.writeInt(x); o.writeInt(y); o.writeInt(b.size); o.write(b) }
            }
            return out.toByteArray()
        } finally { pool.shutdown() }
    }

    fun isTiles(b: ByteArray) = b.size > 4 && b[0] == 0x47.toByte() && b[1] == 0x53.toByte() && b[2] == 0x54.toByte() && b[3] == 0x31.toByte()

    /** Turns the packed tiles into a district of the box. */
    fun parse(blob: ByteArray, box: DoubleArray, mpp: Double = 1.6): District {
        val empty = District(box[0], box[1], box[2], box[3], mpp, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val buildings = ArrayList<Building>(); val roads = ArrayList<Road>(); val water = ArrayList<Poly>()
        val greens = ArrayList<Green>(); val places = ArrayList<Spot>(); val addrs = ArrayList<Spot>(); val shut = ArrayList<Poly>()
        DataInputStream(ByteArrayInputStream(blob)).use { inp ->
            inp.readInt()
            val n = inp.readInt()
            repeat(n) {
                val tx = inp.readInt(); val ty = inp.readInt(); val len = inp.readInt()
                val bytes = ByteArray(len); inp.readFully(bytes)
                for (layer in Mvt.layers(bytes)) {
                    val ext = layer.extent.toDouble()
                    fun px(gx: Int, gy: Int): Pair<Float, Float> = empty.x(lon(tx + gx / ext)) to empty.y(lat(ty + gy / ext))
                    for (f in layer.features) {
                        val tags = f.tags
                        val parts = f.parts.map { part -> FloatArray(part.size) { i -> if (i % 2 == 0) px(part[i], part[i + 1]).first else px(part[i - 1], part[i]).second } }
                        when (layer.name) {
                            "building" -> polys(parts).forEach { p ->
                                val hgt = (tags["render_height"] as? Number)?.toFloat() ?: 0f
                                buildings += Building(p, if (hgt > 0) (hgt / 3.2f).coerceIn(1f, 30f) else 3f, "yes", "")
                            }
                            "water" -> water += polys(parts)
                            "waterway" -> if (tags["class"] in setOf("stream", "ditch", "drain")) parts.forEach { roads += Road("stream", it, false, false, "", "") }
                            "park" -> polys(parts).forEach { greens += Green("park", it) }
                            "landcover" -> {
                                val k = when (tags["class"]) { "grass", "farmland", "wetland" -> "grass"; "wood" -> "wood"; "sand" -> "sand"; else -> null }
                                if (k != null) polys(parts).forEach { greens += Green(k, it) }
                            }
                            "landuse" -> {
                                val k = when (tags["class"]) { "pitch", "stadium" -> "pitch"; "playground" -> "play"; "zoo", "theme_park" -> "park"; else -> null }
                                if (k != null) polys(parts).forEach { greens += Green(k, it) }
                                if (tags["class"] in SHUT) polys(parts).forEach { shut += it }
                            }
                            "transportation" -> if (f.type == 2) {
                                val type = roadType(tags) ?: continue
                                val bridge = tags["brunnel"] == "bridge"; val tunnel = tags["brunnel"] == "tunnel"
                                if (tunnel && type in setOf("primary", "secondary", "tertiary", "residential", "service")) continue
                                parts.forEach { roads += Road(type, it, bridge, tunnel, if (tags["surface"] == "unpaved") "unpaved" else "", "") }
                            }
                            "transportation_name" -> if (f.type == 2) {
                                val nm = (tags["name"] as? String).orEmpty()
                                if (nm.isNotBlank()) parts.forEach { roads += Road("name", it, false, false, "", nm) }
                            }
                            "housenumber" -> if (f.type == 1) parts.forEach { p ->
                                val n = (tags["housenumber"] as? String).orEmpty()
                                if (n.isNotBlank()) addrs += Spot(p[0], p[1], "addr", "|$n")
                            }
                            "poi" -> if (f.type == 1) parts.forEach { p ->
                                val kind = poiKind(tags) ?: return@forEach
                                places += Spot(p[0], p[1], kind, (tags["name"] as? String).orEmpty())
                            }
                        }
                    }
                }
            }
        }
        val order = mapOf("park" to 0, "grass" to 1, "wood" to 2, "scrub" to 2, "sand" to 3, "play" to 4, "pitch" to 4)
        greens.sortBy { order[it.kind] ?: 0 }
        // the tiles reach past the district: keep only places inside it, so nobody settles beyond the edge of the map
        places.removeAll { it.x < 0 || it.y < 0 || it.x > empty.w || it.y > empty.h }
        addrs.removeAll { it.x < 0 || it.y < 0 || it.x > empty.w || it.y > empty.h }
        return District(box[0], box[1], box[2], box[3], mpp, buildings, roads, water, greens, emptyList(), emptyList(), places, addrs, shut)
    }

    /** MVT polygons: a ring with positive area starts a polygon, negative ones are its holes. */
    private fun polys(parts: List<FloatArray>): List<Poly> {
        val out = ArrayList<Poly>()
        var cur: MutableList<FloatArray>? = null
        for (r in parts) {
            if (r.size < 6) continue
            var a = 0f; val n = r.size / 2
            for (i in 0 until n) { val j = (i + 1) % n; a += r[2 * i] * r[2 * j + 1] - r[2 * j] * r[2 * i + 1] }
            if (a > 0 || cur == null) { cur?.let { out += Poly(it) }; cur = mutableListOf(r) } else cur.add(r)
        }
        cur?.let { out += Poly(it) }
        return out
    }

    private fun roadType(t: Map<String, Any>): String? {
        val cls = t["class"] as? String ?: return null
        val sub = t["subclass"] as? String
        return when (cls) {
            "motorway", "trunk", "primary" -> "primary"
            "secondary" -> "secondary"
            "tertiary" -> "tertiary"
            "minor" -> "residential"
            "service" -> "service"
            "track" -> "track"
            "path" -> when (sub) { "steps" -> "steps"; "pedestrian" -> "pedestrian"; "cycleway" -> "cycleway"; "path", "bridleway" -> "path"; else -> "footway" }
            "rail", "transit" -> if (sub == "tram") "tram" else null
            else -> null
        }
    }

    private val SHOPS = setOf("bakery", "florist", "garden_centre", "hardware", "doityourself", "books", "greengrocer", "farm", "hairdresser",
        "beauty", "tailor", "laundry", "dry_cleaning", "pet", "car_repair", "tyres", "trade", "convenience", "supermarket", "butcher",
        "clothes", "shoes", "gift", "stationery", "kiosk", "deli", "confectionery", "alcohol", "mobile_phone", "optician")

    private fun poiKind(t: Map<String, Any>): String? {
        val cls = t["class"] as? String ?: return null
        val sub = (t["subclass"] as? String) ?: cls
        return when {
            sub in SHOPS -> "shop=$sub"
            cls == "railway" || sub in setOf("station", "halt", "subway", "subway_entrance") -> "railway=station"
            sub == "bus_station" -> "amenity=bus_station"
            sub in setOf("museum") -> "tourism=museum"
            sub in setOf("art_gallery", "gallery") -> "tourism=gallery"
            sub in setOf("sports_centre", "stadium", "fitness_centre", "pitch") -> "leisure=sports_centre"
            sub in setOf("park", "garden") -> "leisure=park"
            sub in setOf("pier") -> "man_made=pier"
            cls == "shop" || cls == "grocery" || cls == "clothing_store" -> "shop=$sub"
            sub in setOf("parking", "bench", "bicycle_parking", "toilets", "waste_basket", "recycling", "vending_machine", "gate", "lift_gate",
                "bollard", "bus_stop", "artwork", "atm", "playground", "entrance", "information", "drinking_water", "fountain", "shelter") -> null
            else -> "amenity=$sub"
        }
    }
}

/** A minimal Mapbox Vector Tile reader: layers, features, tags and geometry commands. */
object Mvt {
    class Feature(val type: Int, val tags: Map<String, Any>, val parts: List<IntArray>)
    class Layer(val name: String, val extent: Int, val features: List<Feature>)

    private class Pb(val b: ByteArray, var p: Int = 0, val end: Int = b.size) {
        fun more() = p < end
        fun varint(): Long { var r = 0L; var s = 0; while (true) { val x = b[p++].toInt(); r = r or ((x and 0x7f).toLong() shl s); if (x and 0x80 == 0) return r; s += 7 } }
        fun sub(): Pb { val n = varint().toInt(); val q = Pb(b, p, p + n); p += n; return q }
        fun skip(wire: Int) { when (wire) { 0 -> varint(); 1 -> p += 8; 2 -> p += varint().toInt(); 5 -> p += 4; else -> error("wire $wire") } }
        fun string(): String { val n = varint().toInt(); val s = String(b, p, n, Charsets.UTF_8); p += n; return s }
        fun packed(): IntArray { val q = sub(); val out = ArrayList<Int>(); while (q.more()) out += q.varint().toInt(); return out.toIntArray() }
        fun fixed64(): Long { var r = 0L; for (i in 0 until 8) r = r or ((b[p + i].toLong() and 0xff) shl (8 * i)); p += 8; return r }
        fun fixed32(): Int { var r = 0; for (i in 0 until 4) r = r or ((b[p + i].toInt() and 0xff) shl (8 * i)); p += 4; return r }
    }

    fun layers(bytes: ByteArray): List<Layer> {
        val out = ArrayList<Layer>()
        val t = Pb(bytes)
        while (t.more()) {
            val key = t.varint().toInt()
            if (key shr 3 == 3 && key and 7 == 2) out += layer(t.sub()) else t.skip(key and 7)
        }
        return out
    }

    private fun layer(l: Pb): Layer {
        var name = ""; var extent = 4096
        val keys = ArrayList<String>(); val values = ArrayList<Any>()
        val raw = ArrayList<Triple<Int, IntArray, IntArray>>()
        while (l.more()) {
            val key = l.varint().toInt()
            when (key shr 3) {
                1 -> name = l.string()
                2 -> {
                    val f = l.sub(); var type = 0; var tags = IntArray(0); var geom = IntArray(0)
                    while (f.more()) {
                        val k = f.varint().toInt()
                        when (k shr 3) { 2 -> tags = f.packed(); 3 -> type = f.varint().toInt(); 4 -> geom = f.packed(); else -> f.skip(k and 7) }
                    }
                    raw += Triple(type, tags, geom)
                }
                3 -> keys += l.string()
                4 -> {
                    val v = l.sub(); var value: Any = ""
                    while (v.more()) {
                        val k = v.varint().toInt()
                        value = when (k shr 3) {
                            1 -> v.string(); 2 -> java.lang.Float.intBitsToFloat(v.fixed32()); 3 -> java.lang.Double.longBitsToDouble(v.fixed64())
                            4, 5 -> v.varint(); 6 -> v.varint().let { (it ushr 1) xor -(it and 1) }; 7 -> v.varint() != 0L
                            else -> { v.skip(k and 7); value }
                        }
                    }
                    values += value
                }
                5 -> extent = l.varint().toInt()
                else -> l.skip(key and 7)
            }
        }
        val features = raw.map { (type, tags, geom) ->
            val m = HashMap<String, Any>()
            var i = 0
            while (i + 1 < tags.size) { keys.getOrNull(tags[i])?.let { k -> values.getOrNull(tags[i + 1])?.let { m[k] = it } }; i += 2 }
            Feature(type, m, geometry(geom))
        }
        return Layer(name, extent, features)
    }

    /** Decodes MoveTo / LineTo / ClosePath into parts (each part a flat x, y array). */
    private fun geometry(g: IntArray): List<IntArray> {
        val parts = ArrayList<IntArray>()
        var cur = ArrayList<Int>()
        var x = 0; var y = 0; var i = 0
        fun zz(v: Int) = (v ushr 1) xor -(v and 1)
        while (i < g.size) {
            val cmd = g[i] and 7; val count = g[i] ushr 3; i++
            when (cmd) {
                1 -> repeat(count) { if (cur.size >= 2) parts += cur.toIntArray(); cur = ArrayList(); x += zz(g[i]); y += zz(g[i + 1]); i += 2; cur += x; cur += y }
                2 -> repeat(count) { x += zz(g[i]); y += zz(g[i + 1]); i += 2; cur += x; cur += y }
                7 -> {}
            }
        }
        if (cur.size >= 2) parts += cur.toIntArray()
        return parts
    }
}
