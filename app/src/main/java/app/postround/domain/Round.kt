package app.postround.domain

import app.postround.data.District
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A house with a real address. [id] = "street|number" stays the same across loads of the same map. */
class House(
    val id: String,
    val street: String,
    val number: String,
    val block: Block,
    /** home · shop · inst */
    val kind: String,
    /** Where the postman stands at the door: a pavement pixel in front of the house. */
    val doorX: Int, val doorY: Int,
) {
    fun addr(ru: Boolean) = Round.short(street, ru) + ", " + number
}

/** What a house looks like and what is near it: for letters without an address. */
class Signs(val colour: Int, val storeys: Int, val near: List<String>)

/**
 * The postman's round: every building of the district that has an address, the post office, the streets.
 * Built once per district from the map, with no randomness, so house ids survive restarts.
 */
class Round(val d: District, val t: Terrain) {
    val houses: List<House>
    val byId: Map<String, House>
    val byBlock: Map<Int, House>
    /** The post office: a real one from the map if there is one in the district, else null (home stands in). */
    val postX: Int; val postY: Int; val realPost: Boolean
    /** Streets with at least one house, most houses first. */
    val streets: List<String>
    /** What a street's stamp shows: water · tram · park · houses · roofs · lamp · arch. */
    val motif: Map<String, String>

    init {
        val named = d.roads.filter { it.name.isNotBlank() }
        fun nearestStreet(x: Float, y: Float): String? {
            var best: String? = null; var bd = 45.0
            for (r in named) {
                var i = 0
                while (i + 3 < r.pts.size) {
                    val dd = segDist(x, y, r.pts[i], r.pts[i + 1], r.pts[i + 2], r.pts[i + 3])
                    if (dd < bd) { bd = dd; best = r.name }
                    i += 2
                }
            }
            return best
        }
        val shopAt = HashSet<Int>()
        for (p in d.places) if (p.kind.startsWith("shop=") || p.kind in setOf("amenity=cafe", "amenity=pharmacy", "amenity=restaurant", "amenity=bakery")) blockAt(p.x, p.y)?.let { shopAt += it.index }
        val instAt = HashSet<Int>()
        for (p in d.places) if (p.kind in setOf("amenity=school", "amenity=kindergarten", "amenity=library", "amenity=college", "amenity=university")) blockAt(p.x, p.y)?.let { instAt += it.index }
        val out = LinkedHashMap<String, House>()
        val taken = HashSet<Int>()
        for (a in d.addrs) {
            val parts = a.name.split('|')
            // «10 литГ», «10 лит. Г» → «10Г»: the letter of a building in a block, as written on the plate
            val num = parts.getOrElse(1) { "" }.trim().replace(Regex("""\s*лит\.?\s*"""), "").replace(Regex("""\s*корп\.?\s*"""), "к")
            if (num.isEmpty() || num.length > 12) continue
            val b = blockAt(a.x, a.y) ?: continue
            if (b.index in taken || b.area < 12) continue
            // inside works, a railway yard or military land: no door the postman can reach (#4)
            if (d.shut.any { inside(it, b.cx, b.cy) }) continue
            val street = parts[0].ifBlank { nearestStreet(b.cx, b.cy) ?: continue }
            val id = "$street|$num"
            if (id in out) continue
            val kind = when {
                b.kind in SHOPS || b.index in shopAt -> "shop"
                b.kind in INST || b.index in instAt -> "inst"
                b.kind in SKIP -> continue
                else -> "home"
            }
            val (dx, dy) = door(b)
            out[id] = House(id, street, num, b, kind, dx, dy)
            taken += b.index
        }
        houses = out.values.toList()
        byId = out
        byBlock = houses.associateBy { it.block.index }
        val post = d.places.filter { it.kind == "amenity=post_office" }
        val cx = d.w / 2f; val cy = d.h / 2f
        val p = post.minByOrNull { hypot((it.x - cx).toDouble(), (it.y - cy).toDouble()) }
        if (p != null) {
            val b = blockAt(p.x, p.y)
            val (x, y) = if (b != null) door(b) else p.x.roundToInt() to p.y.roundToInt()
            postX = x; postY = y; realPost = true
        } else { postX = -1; postY = -1; realPost = false }
        val per = houses.groupBy { it.street }
        streets = per.keys.sortedByDescending { per.getValue(it).size }
        motif = streets.associateWith { s ->
            val hs = per.getValue(s)
            val opts = ArrayList<String>()
            if (hs.count { t.nearWater(it.doorX, it.doorY, 12) } >= 1) opts += "water"
            if (hs.count { railNear(it.doorX, it.doorY, 6) } >= 1) opts += "tram"
            if (hs.count { green(it.doorX, it.doorY, 10) > 40 } * 2 >= hs.size) opts += "park"
            opts += listOf("houses", "roofs", "lamp", "arch")[(strHash(s) % 4).toInt()]
            opts[(strHash(s) % min(opts.size, 2)).toInt()]
        }
    }

    fun blockAt(x: Float, y: Float): Block? {
        val xi = x.roundToInt(); val yi = y.roundToInt()
        if (xi in 0 until t.w && yi in 0 until t.h) { val b = t.bid[yi * t.w + xi]; if (b >= 0) return t.blocks[b] }
        // an address node just outside the outline: the nearest building within ~10 m
        var best: Block? = null; var bd = 7
        for (j in -6..6) for (i in -6..6) {
            val xx = xi + i; val yy = yi + j
            if (xx !in 0 until t.w || yy !in 0 until t.h) continue
            val b = t.bid[yy * t.w + xx]
            if (b >= 0 && max(kotlin.math.abs(i), kotlin.math.abs(j)) < bd) { bd = max(kotlin.math.abs(i), kotlin.math.abs(j)); best = t.blocks[b] }
        }
        return best
    }

    /** A pavement pixel in front of the building, searched outwards from the middle of its south side. */
    private fun door(b: Block): Pair<Int, Int> {
        val x0 = b.cx.roundToInt(); val y0 = b.y1 + 2
        for (r in 0..14) for (j in -r..r) for (i in -r..r) {
            if (max(kotlin.math.abs(i), kotlin.math.abs(j)) != r) continue
            val c = t.at(x0 + i, y0 + j)
            if (c == K.WALK || c == K.DIRT || c == K.GROUND || c == K.STEPS) return (x0 + i) to (y0 + j)
        }
        return x0 to y0
    }

    private fun railNear(x: Int, y: Int, r: Int): Boolean {
        for (j in -r..r) for (i in -r..r) { val xx = x + i; val yy = y + j; if (xx in 0 until t.w && yy in 0 until t.h && t.rail[yy * t.w + xx]) return true }
        return false
    }

    private fun green(x: Int, y: Int, r: Int): Int {
        var n = 0
        for (j in -r..r) for (i in -r..r) { val c = t.at(x + i, y + j); if (c == K.LAWN || c == K.WOOD) n++ }
        return n
    }

    /** Distance in metres from a map point to the building (its bounding box). */
    fun metresTo(h: House, x: Float, y: Float): Double {
        val b = h.block
        val dx = max(0f, max(b.x0 - x, x - b.x1)); val dy = max(0f, max(b.y0 - h.block.h - y, y - b.y1))
        return hypot(dx.toDouble(), dy.toDouble()) * d.mpp
    }

    fun metresDoor(h: House, x: Float, y: Float) = hypot((h.doorX - x).toDouble(), (h.doorY - y).toDouble()) * d.mpp

    /** The colour index of a facade (into the art's palette), the same the map paints. */
    fun facade(h: House): Int = (Terrain.hash(h.block.index, 3) * 9).toInt()

    /** What can be said about a house without its number: storeys, colour, what's next to it. */
    fun signs(h: House): Signs {
        val near = ArrayList<String>()
        val hx = h.doorX; val hy = h.doorY
        val r = (35 / d.mpp)
        for ((k, tag) in listOf("amenity=pharmacy" to "pharmacy", "amenity=cafe" to "cafe", "shop=bakery" to "bakery", "amenity=school" to "school",
            "amenity=library" to "library", "shop=greengrocer" to "greengrocer", "railway=station" to "station", "shop=florist" to "florist", "amenity=kindergarten" to "kindergarten")) {
            if (d.places.any { it.kind == k && hypot((it.x - hx).toDouble(), (it.y - hy).toDouble()) < r }) { near += tag; break }
        }
        if (t.nearWater(hx, hy, (25 / d.mpp).toInt())) near += "water"
        if (railNear(hx, hy, 5)) near += "tram" else if (green(hx, hy, 8) > 40) near += "park"
        return Signs(facade(h), h.block.levels.roundToInt(), near)
    }

    companion object {
        val SHOPS = setOf("retail", "commercial", "kiosk", "supermarket", "shop")
        val INST = setOf("school", "kindergarten", "university", "college", "office", "public", "civic", "government", "hospital")
        val SKIP = setOf("garage", "garages", "shed", "hut", "roof", "carport", "service", "industrial", "warehouse", "construction", "ruins", "transformer_tower", "bunker", "ship")

        /** Whether (x, y) lies inside the outer ring of [p]. */
        fun inside(p: app.postround.data.Poly, x: Float, y: Float): Boolean {
            val r = p.rings.firstOrNull() ?: return false
            var inside = false
            var j = r.size - 2
            var i = 0
            while (i + 1 < r.size) {
                val xi = r[i]; val yi = r[i + 1]; val xj = r[j]; val yj = r[j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i; i += 2
            }
            return inside
        }

        fun strHash(s: String): Long { var h = 7L; for (c in s) h = (h * 31 + c.code) and 0xffffffffL; return h }

        private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Double {
            val dx = bx - ax; val dy = by - ay; val l = dx * dx + dy * dy
            val tt = if (l == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / l).coerceIn(0f, 1f)
            return hypot((px - ax - tt * dx).toDouble(), (py - ay - tt * dy).toDouble())
        }

        private val RU = listOf("улица" to "ул.", "проспект" to "пр.", "переулок" to "пер.", "набережная" to "наб.", "площадь" to "пл.", "бульвар" to "бул.", "шоссе" to "ш.", "проезд" to "пр-д")
        private val EN = listOf("Street" to "St", "Avenue" to "Ave", "Road" to "Rd", "Lane" to "Ln", "Square" to "Sq", "Boulevard" to "Blvd", "Embankment" to "Emb.")

        /** "улица Воскова" → "ул. Воскова", "Введенская улица" → "Введенская ул.". Streets keep their local language. */
        fun short(street: String, ru: Boolean): String {
            var s = street
            for ((a, b) in RU + EN) s = s.replace(Regex("(^|\\s)$a(\\s|$)")) { m -> m.groupValues[1] + b + m.groupValues[2] }
            return s
        }
    }
}
