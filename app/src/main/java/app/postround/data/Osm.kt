package app.postround.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos

/** A polygon: the first ring is the outline, the rest are holes. Points are in map pixels, x then y. */
class Poly(val rings: List<FloatArray>)

class Building(val poly: Poly, val levels: Float, val kind: String, val name: String)
class Road(val type: String, val pts: FloatArray, val bridge: Boolean, val tunnel: Boolean, val surface: String, val name: String)
class Green(val kind: String, val poly: Poly)
class Spot(val x: Float, val y: Float, val kind: String, val name: String)

/**
 * The district as geometry, projected to map pixels of [mpp] metres around a box: (0,0) is the north-west
 * corner. Everything the art and the game need from OpenStreetMap.
 */
class District(
    val south: Double, val west: Double, val north: Double, val east: Double,
    val mpp: Double,
    val buildings: List<Building>,
    val roads: List<Road>,
    val water: List<Poly>,
    val greens: List<Green>,
    val trees: List<Spot>,
    val lamps: List<Spot>,
    /** Shops and amenities with their main tag, e.g. kind = "shop=bakery". */
    val places: List<Spot>,
    /** House numbers: kind "addr", name "street|number" (the street may be empty: tiles give the number only). */
    val addrs: List<Spot> = emptyList(),
) {
    private val kx = cos(Math.toRadians((south + north) / 2)) * 111_320.0 / mpp
    private val ky = 110_574.0 / mpp
    val w: Int = ((east - west) * kx).toInt()
    val h: Int = ((north - south) * ky).toInt()

    fun x(lon: Double) = ((lon - west) * kx).toFloat()
    fun y(lat: Double) = ((north - lat) * ky).toFloat()
    fun lon(x: Float) = west + x / kx
    fun lat(y: Float) = north - y / ky

    companion object {
        /** A square box of [half] metres around a point. */
        fun box(lat: Double, lon: Double, half: Double): DoubleArray {
            val dLat = half / 110_574.0
            val dLon = half / (111_320.0 * cos(Math.toRadians(lat)))
            return doubleArrayOf(lat - dLat, lon - dLon, lat + dLat, lon + dLon)
        }

        fun query(s: Double, w: Double, n: Double, e: Double): String {
            val b = "%.5f,%.5f,%.5f,%.5f".format(Locale.US, s, w, n, e)
            // geometry comes inline ("out geom"), so no second pass over thousands of bare nodes;
            // only the amenities the game binds neighbours to, not every bench and parking lot
            return """
                [out:json][timeout:60][bbox:$b];
                (
                 way["building"]; relation["building"]["type"="multipolygon"];
                 way["highway"]; way["railway"="tram"];
                 way["natural"="water"]; relation["natural"="water"]; way["waterway"~"^(riverbank|stream|ditch|drain)$"];
                 way["leisure"~"^(park|garden|playground|pitch)$"]; relation["leisure"~"^(park|garden)$"];
                 way["landuse"~"^(grass|forest|recreation_ground|meadow|village_green)$"];
                 way["natural"~"^(wood|scrub|sand|beach)$"];
                 node["natural"="tree"]; node["highway"="street_lamp"];
                 nwr["shop"];
                 nwr["amenity"~"^(pharmacy|cafe|school|kindergarten|library|marketplace|post_office|theatre|arts_centre|veterinary|fuel|bus_station|restaurant|bakery)$"];
                 nwr["leisure"~"^(sports_centre|stadium|fitness_centre)$"]; nwr["tourism"~"^(museum|gallery)$"];
                 nwr["railway"~"^(station|halt|subway_entrance|tram_stop)$"]; nwr["craft"="tailor"]; nwr["man_made"="pier"];
                 node["addr:housenumber"];
                );
                out body geom qt;
            """.trimIndent()
        }

        private const val AGENT = "Postround/1.0 (github.com/bigdevwhale/postround)"
        private val SERVERS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )

        /** Downloads the raw Overpass JSON for a box (s, w, n, e). Only the box is sent. */
        fun download(box: DoubleArray): String {
            val body = "data=" + URLEncoder.encode(query(box[0], box[1], box[2], box[3]), "UTF-8")
            var last: Throwable? = null
            for (srv in SERVERS) {
                val r = runCatching {
                    val c = URL(srv).openConnection() as HttpURLConnection
                    c.connectTimeout = 15_000; c.readTimeout = 90_000
                    c.setRequestProperty("User-Agent", AGENT)
                    c.requestMethod = "POST"; c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                    c.outputStream.use { it.write(body.toByteArray()) }
                    try {
                        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode} at ${URL(srv).host}")
                        c.inputStream.bufferedReader().use { it.readText() }.also { if (!it.trimStart().startsWith("{")) error("not JSON at ${URL(srv).host}") }
                    } finally { c.disconnect() }
                }
                if (r.isSuccess) return r.getOrThrow()
                last = r.exceptionOrNull()
            }
            throw last ?: IllegalStateException("no server")
        }

        /** Parses an Overpass dump into a district of the given box. */
        fun parse(json: String, box: DoubleArray, mpp: Double = 1.6): District {
            val els = JSONObject(json).getJSONArray("elements")
            val nodes = HashMap<Long, DoubleArray>()
            val ways = HashMap<Long, JSONObject>()
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                when (e.getString("type")) {
                    "node" -> nodes[e.getLong("id")] = doubleArrayOf(e.getDouble("lat"), e.getDouble("lon"))
                    "way" -> ways[e.getLong("id")] = e
                }
            }
            val empty = District(box[0], box[1], box[2], box[3], mpp, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            fun geom(g: org.json.JSONArray): FloatArray? {
                val out = FloatArray(g.length() * 2); var k = 0
                for (i in 0 until g.length()) { val o = g.optJSONObject(i) ?: continue; out[k++] = empty.x(o.getDouble("lon")); out[k++] = empty.y(o.getDouble("lat")) }
                return if (k < 4) null else out.copyOf(k)
            }
            fun pts(w: JSONObject): FloatArray? {
                w.optJSONArray("geometry")?.let { return geom(it) }
                val ns = w.optJSONArray("nodes") ?: return null
                val out = FloatArray(ns.length() * 2)
                var k = 0
                for (i in 0 until ns.length()) {
                    val p = nodes[ns.getLong(i)] ?: continue
                    out[k++] = empty.x(p[1]); out[k++] = empty.y(p[0])
                }
                return if (k < 4) null else out.copyOf(k)
            }
            fun closed(w: JSONObject): Boolean {
                val ns = w.optJSONArray("nodes") ?: return false
                return ns.length() >= 4 && ns.getLong(0) == ns.getLong(ns.length() - 1)
            }
            fun wayPoly(w: JSONObject): Poly? = if (closed(w)) pts(w)?.let { Poly(listOf(it)) } else null
            fun relPoly(r: JSONObject): Poly? {
                val mem = r.optJSONArray("members") ?: return null
                val outer = ArrayList<FloatArray>(); val inner = ArrayList<FloatArray>()
                for (i in 0 until mem.length()) {
                    val m = mem.getJSONObject(i)
                    if (m.getString("type") != "way") continue
                    val p = m.optJSONArray("geometry")?.let { geom(it) } ?: ways[m.getLong("ref")]?.let { pts(it) } ?: continue
                    if (m.optString("role") == "inner") inner += p else outer += p
                }
                val rings = stitch(outer) + stitch(inner)
                return if (rings.isEmpty()) null else Poly(rings)
            }
            fun tagsOf(e: JSONObject): Map<String, String> {
                val t = e.optJSONObject("tags") ?: return emptyMap()
                return t.keys().asSequence().associateWith { t.optString(it) }
            }
            fun centre(e: JSONObject): Pair<Float, Float>? {
                if (e.has("lat")) return empty.x(e.getDouble("lon")) to empty.y(e.getDouble("lat"))
                val p = when (e.getString("type")) { "way" -> pts(e); else -> null } ?: return null
                var sx = 0f; var sy = 0f; val n = p.size / 2
                for (i in 0 until n) { sx += p[2 * i]; sy += p[2 * i + 1] }
                return sx / n to sy / n
            }
            val buildings = ArrayList<Building>(); val roads = ArrayList<Road>(); val water = ArrayList<Poly>()
            val greens = ArrayList<Green>(); val trees = ArrayList<Spot>(); val lamps = ArrayList<Spot>(); val places = ArrayList<Spot>(); val addrs = ArrayList<Spot>()
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                val t = tagsOf(e)
                if (t.isEmpty()) continue
                val type = e.getString("type")
                if (type == "node") {
                    val x = empty.x(e.getDouble("lon")); val y = empty.y(e.getDouble("lat"))
                    when {
                        t["natural"] == "tree" -> trees += Spot(x, y, "tree", "")
                        t["highway"] == "street_lamp" -> lamps += Spot(x, y, "lamp", "")
                    }
                }
                place(t)?.let { k -> centre(e)?.let { (x, y) -> places += Spot(x, y, k, t["name"].orEmpty()) } }
                t["addr:housenumber"]?.let { n -> centre(e)?.let { (x, y) -> addrs += Spot(x, y, "addr", t["addr:street"].orEmpty() + "|" + n) } }
                val poly by lazy { if (type == "relation") relPoly(e) else wayPoly(e) }
                when {
                    "building" in t -> poly?.let {
                        val lv = t["building:levels"]?.toFloatOrNull() ?: t["height"]?.toFloatOrNull()?.div(3.2f)
                        buildings += Building(it, lv ?: if (area(it.rings[0]) > 2400f / (mpp * mpp) * 0.64f * 1.5f) 5f else 2f, t.getValue("building"), t["name"].orEmpty())
                    }
                    type == "way" && "highway" in t -> {
                        val hw = t.getValue("highway")
                        if (hw !in setOf("corridor", "construction", "proposed", "elevator", "platform")) pts(e)?.let {
                            roads += Road(hw, it, t["bridge"].let { b -> b != null && b != "no" }, t["tunnel"] in setOf("building_passage", "yes"), t["surface"].orEmpty(), t["name"].orEmpty())
                        }
                    }
                    type == "way" && t["railway"] == "tram" -> pts(e)?.let { roads += Road("tram", it, t["bridge"] != null && t["bridge"] != "no", false, "", "") }
                    t["natural"] == "water" || t["waterway"] == "riverbank" -> poly?.let { water += it }
                    type == "way" && t["waterway"] in setOf("stream", "ditch", "drain", "canal", "river") && !closed(e) ->
                        if (t["waterway"] in setOf("stream", "ditch", "drain")) pts(e)?.let { roads += Road("stream", it, false, false, "", "") }
                    else -> greenKind(t)?.let { k -> poly?.let { greens += Green(k, it) } }
                }
            }
            val order = mapOf("park" to 0, "grass" to 1, "wood" to 2, "scrub" to 2, "sand" to 3, "play" to 4, "pitch" to 4)
            greens.sortBy { order[it.kind] ?: 0 }
            return District(box[0], box[1], box[2], box[3], mpp, buildings, roads, water, greens, trees, lamps, places, addrs)
        }

        private fun greenKind(t: Map<String, String>): String? = when {
            t["leisure"] in setOf("park", "garden") -> "park"
            t["landuse"] in setOf("grass", "recreation_ground", "meadow") -> "grass"
            t["landuse"] == "forest" || t["natural"] == "wood" -> "wood"
            t["natural"] == "scrub" -> "scrub"
            t["natural"] in setOf("beach", "sand") -> "sand"
            t["leisure"] == "playground" -> "play"
            t["leisure"] == "pitch" -> "pitch"
            else -> null
        }

        /** The tag a neighbour can be bound to, e.g. "shop=bakery". */
        private fun place(t: Map<String, String>): String? {
            for (k in listOf("shop", "amenity", "railway", "leisure", "tourism", "craft", "man_made")) {
                val v = t[k] ?: continue
                if (k == "amenity" && v in setOf("parking", "bench", "waste_basket", "bicycle_parking", "vending_machine", "recycling", "toilets", "parking_entrance", "shelter")) continue
                return "$k=$v"
            }
            return null
        }

        fun area(r: FloatArray): Float {
            var a = 0f; val n = r.size / 2
            for (i in 0 until n) { val j = (i + 1) % n; a += r[2 * i] * r[2 * j + 1] - r[2 * j] * r[2 * i + 1] }
            return kotlin.math.abs(a) / 2
        }

        /** Joins way pieces into closed rings by matching ends. */
        fun stitch(parts: List<FloatArray>): List<FloatArray> {
            val left = parts.toMutableList(); val out = ArrayList<FloatArray>()
            fun same(a: Float, b: Float, c: Float, d: Float) = kotlin.math.abs(a - c) < 0.01f && kotlin.math.abs(b - d) < 0.01f
            while (left.isNotEmpty()) {
                var ring = left.removeAt(0)
                var grew = true
                while (grew && !same(ring[0], ring[1], ring[ring.size - 2], ring[ring.size - 1])) {
                    grew = false
                    for (i in left.indices) {
                        val p = left[i]
                        val ex = ring[ring.size - 2]; val ey = ring[ring.size - 1]
                        val next = when {
                            same(ex, ey, p[0], p[1]) -> p
                            same(ex, ey, p[p.size - 2], p[p.size - 1]) -> reverse(p)
                            else -> null
                        } ?: continue
                        ring = ring + next.copyOfRange(2, next.size)
                        left.removeAt(i); grew = true; break
                    }
                }
                if (ring.size >= 6) out += ring
            }
            return out
        }

        private fun reverse(p: FloatArray): FloatArray {
            val n = p.size / 2; val o = FloatArray(p.size)
            for (i in 0 until n) { o[2 * i] = p[2 * (n - 1 - i)]; o[2 * i + 1] = p[2 * (n - 1 - i) + 1] }
            return o
        }
    }
}
