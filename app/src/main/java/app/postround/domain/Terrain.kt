package app.postround.domain

import app.postround.data.District
import app.postround.data.Poly
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What a map pixel is. */
object K {
    const val GROUND = 0; const val LAWN = 1; const val WOOD = 2; const val SAND = 3; const val PITCH = 4
    const val WATER = 5; const val ROAD = 6; const val WALK = 7; const val DIRT = 8; const val BRIDGE = 9
    const val STEPS = 10; const val BLD = 11
}

/** One building as pixels, with what the art needs to raise it. */
class Block(
    val index: Int,
    val pts: IntArray,
    val x0: Int, val y0: Int, val x1: Int, val y1: Int,
    /** Wall height in pixels. */
    val h: Int,
    val kind: String,
    val name: String,
    val cx: Float, val cy: Float,
    /** Storeys as tagged in OSM (or guessed from the footprint). */
    val levels: Float = 3f,
    /** Has a courtyard inside (a polygon with holes). */
    val courtyard: Boolean = false,
) { val area get() = pts.size }

class Tree(val x: Int, val y: Int, val r: Int, val kind: String, val seed: Double)
class Prop(val kind: String, val x: Int, val y: Int, val arg: String = "")

/**
 * The district rasterised into classes, one per map pixel (1.6 m): lawns, pavements, roads, water,
 * buildings. Built once per district; the art paints from it and the game asks it where a bed may go.
 */
class Terrain(val d: District) {
    val w = d.w
    val h = d.h
    val n = w * h
    val cls = ByteArray(n)
    val rail = BooleanArray(n)
    val lane = BooleanArray(n)
    val pass = BooleanArray(n)
    val bid = IntArray(n) { -1 }
    val blocks = ArrayList<Block>()
    val trees = ArrayList<Tree>()
    val lamps = ArrayList<IntArray>()
    val props = ArrayList<Prop>()

    fun at(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) K.GROUND else cls[y * w + x].toInt()

    init { build() }

    private fun fill(p: Poly, f: (Int, Int) -> Unit) {
        var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (r in p.rings) for (i in 1 until r.size step 2) { y0 = min(y0, r[i]); y1 = max(y1, r[i]) }
        val xs = FloatArray(256)
        for (y in max(0, floor(y0).toInt())..min(h - 1, ceil(y1).toInt())) {
            val yc = y + .5f
            var k = 0
            var big: MutableList<Float>? = null
            for (r in p.rings) {
                val m = r.size
                var i = 0
                while (i < m) {
                    val ax = r[i]; val ay = r[i + 1]; val j = (i + 2) % m; val bx = r[j]; val by = r[j + 1]
                    if ((ay <= yc) != (by <= yc)) {
                        val x = ax + (yc - ay) / (by - ay) * (bx - ax)
                        if (k < xs.size) xs[k++] = x else (big ?: ArrayList<Float>().also { big = it }).add(x)
                    }
                    i += 2
                }
            }
            val all = if (big == null) xs.copyOf(k).also { it.sort() } else (xs.copyOf(k).toList() + big!!).sorted().toFloatArray()
            var q = 0
            while (q + 1 < all.size) {
                val a = max(0, ceil(all[q] - .5f).toInt()); val b = min(w - 1, floor(all[q + 1] - .5f).toInt())
                for (x in a..b) f(x, y)
                q += 2
            }
        }
    }

    private fun stroke(p: FloatArray, width: Float, f: (Int, Int, Float) -> Unit) {
        val r = width / 2
        var i = 0
        while (i + 3 < p.size) {
            val ax = p[i]; val ay = p[i + 1]; val bx = p[i + 2]; val by = p[i + 3]
            val dx = bx - ax; val dy = by - ay; val l = (dx * dx + dy * dy).takeIf { it > 0 } ?: 1f
            val x0 = max(0, floor(min(ax, bx) - r).toInt()); val x1 = min(w - 1, ceil(max(ax, bx) + r).toInt())
            val y0 = max(0, floor(min(ay, by) - r).toInt()); val y1 = min(h - 1, ceil(max(ay, by) + r).toInt())
            for (y in y0..y1) for (x in x0..x1) {
                val px = x + .5f - ax; val py = y + .5f - ay
                val t = ((px * dx + py * dy) / l).coerceIn(0f, 1f)
                val ex = px - t * dx; val ey = py - t * dy
                val dd = sqrt(ex * ex + ey * ey)
                if (dd <= r) f(x, y, dd)
            }
            i += 2
        }
    }

    private fun build() {
        val gk = mapOf("park" to K.LAWN, "grass" to K.LAWN, "wood" to K.WOOD, "scrub" to K.WOOD, "sand" to K.SAND, "play" to K.SAND, "pitch" to K.PITCH)
        for (g in d.greens) { val k = gk[g.kind] ?: continue; fill(g.poly) { x, y -> cls[y * w + x] = k.toByte() } }
        for (p in d.water) fill(p) { x, y -> cls[y * w + x] = K.WATER.toByte() }
        val roads = d.roads
        for (r in roads) if (r.type == "stream") stroke(r.pts, 2f) { x, y, _ -> cls[y * w + x] = K.WATER.toByte() }
        for (r in roads) if (!r.bridge && r.type in SIDE) stroke(r.pts, (RW[r.type] ?: 5f) + 4f) { x, y, _ -> val i = y * w + x; if (cls[i].toInt() != K.WATER) cls[i] = K.WALK.toByte() }
        for (r in roads) if (!r.bridge && r.type !in CARS && RW[r.type] != null) stroke(r.pts, RW.getValue(r.type)) { x, y, _ ->
            val i = y * w + x; val c = cls[i].toInt()
            if (c != K.WATER) cls[i] = when {
                r.type == "steps" -> K.STEPS
                r.surface in UNPAVED || c == K.LAWN || c == K.WOOD || c == K.SAND || c == K.DIRT || r.type == "path" || r.type == "track" -> K.DIRT
                else -> K.WALK
            }.toByte()
        }
        for (r in roads) if (!r.bridge && r.type in CARS) stroke(r.pts, RW[r.type] ?: 4f) { x, y, _ -> val i = y * w + x; if (cls[i].toInt() != K.WATER) cls[i] = K.ROAD.toByte() }
        for (r in roads) if (!r.bridge && (r.type == "primary" || r.type == "secondary")) dashes(r.pts)
        for (r in roads) if (r.bridge) stroke(r.pts, (RW[r.type] ?: 3f) + if (r.type in SIDE) 4f else 1f) { x, y, _ -> cls[y * w + x] = K.BRIDGE.toByte() }
        for (r in roads) if (r.type == "tram") stroke(r.pts, 3.6f) { x, y, dd ->
            val i = y * w + x
            if (dd > .8f && dd < 1.7f) { rail[i] = true; val c = cls[i].toInt(); if (c == K.GROUND || c == K.WALK) cls[i] = K.ROAD.toByte() }
        }
        d.buildings.forEachIndexed { bi, b ->
            val pts = ArrayList<Int>()
            var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = -1; var y1 = -1
            fill(b.poly) { x, y ->
                val i = y * w + x
                if (cls[i].toInt() == K.WATER && b.kind != "ship") return@fill
                cls[i] = K.BLD.toByte(); bid[i] = blocks.size; pts += i
                x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
            }
            if (pts.isEmpty()) return@forEachIndexed
            val castle = b.kind == "castle"
            val small = b.kind in setOf("kiosk", "garage", "garages", "pavilion", "shed", "hut")
            val hh = if (castle) 3 else if (small) 2 else (b.levels * 1.5f).roundToInt().coerceIn(2, 16)
            var cx = 0f; var cy = 0f
            for (i in pts) { cx += i % w; cy += i / w }
            blocks += Block(bi, pts.toIntArray(), x0, y0, x1, y1, hh, b.kind, b.name, cx / pts.size, cy / pts.size, b.levels, b.poly.rings.size > 1)
        }
        for (r in roads) if (r.tunnel) stroke(r.pts, RW[r.type] ?: 2f) { x, y, _ -> pass[y * w + x] = true }
        plant()
    }

    private fun dashes(p: FloatArray) {
        var acc = 0f
        var i = 0
        while (i + 3 < p.size) {
            val ax = p[i]; val ay = p[i + 1]; val bx = p[i + 2]; val by = p[i + 3]
            val l = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
            var s = 0f
            while (s < l) {
                if ((acc + s) % 9f < 4.5f) {
                    val x = (ax + (bx - ax) * s / l).toInt(); val y = (ay + (by - ay) * s / l).toInt()
                    if (x in 0 until w && y in 0 until h && cls[y * w + x].toInt() == K.ROAD) lane[y * w + x] = true
                }
                s += .7f
            }
            acc += l; i += 2
        }
    }

    /** Trees from OSM plus parkland filled in by hand, lamps, benches and flowerbeds. */
    private fun plant() {
        val occ = BooleanArray(n)
        fun clear(x: Int, y: Int, r: Int): Boolean {
            for (j in -r..r) for (i in -r..r) {
                val c = at(x + i, y + j)
                if (c != K.LAWN && c != K.WOOD && c != K.GROUND && c != K.SAND) return false
                val q = (y + j) * w + x + i
                if (q in 0 until n && occ[q]) return false
            }
            return true
        }
        fun add(xf: Float, yf: Float, force: Boolean) {
            val x = xf.roundToInt(); val y = yf.roundToInt()
            if (x < 2 || y < 2 || x >= w - 2 || y >= h - 2) return
            if (!force && !clear(x, y, 2)) return
            val c = at(x, y)
            val hv = hash(x * 3, y * 5)
            val kind = when {
                c == K.WOOD && hv < .18 -> "spruce"; hv < .14 -> "birch"; hv < .42 -> "lime"; hv < .62 -> "maple"; hv < .78 -> "oak"; else -> "elm"
            }
            trees += Tree(x, y, 3 + (hash(x, y) * 3).toInt() + if (c == K.WOOD) 1 else 0, kind, hv)
            for (j in -3..3) for (i in -3..3) { val q = (y + j) * w + x + i; if (q in 0 until n) occ[q] = true }
        }
        for (t in d.trees) { val c = at(t.x.roundToInt(), t.y.roundToInt()); if (c != K.BLD && c != K.WATER && c != K.ROAD) add(t.x, t.y, true) }
        var gy = 3
        while (gy < h - 3) {
            var gx = 3
            while (gx < w - 3) {
                val x = gx + (hash(gx, gy) * 4).toInt(); val y = gy + (hash(gy, gx) * 4).toInt()
                val c = at(x, y)
                val p = if (c == K.WOOD) .95 else if (c == K.LAWN) .5 else 0.0
                if (hash(x, y * 3) < p) add(x.toFloat(), y.toFloat(), false)
                gx += 5
            }
            gy += 5
        }
        for (l in d.lamps) { val x = l.x.roundToInt(); val y = l.y.roundToInt(); val c = at(x, y); if (c != K.BLD && c != K.WATER) lamps += intArrayOf(x, y) }
        // tiles carry no street lamps: put one every ~40 m along the pavements of proper streets
        if (d.lamps.isEmpty()) for (r in d.roads) {
            if (r.type !in SIDE || r.bridge) continue
            var acc = 0f; var i = 0
            while (i + 3 < r.pts.size) {
                val ax = r.pts[i]; val ay = r.pts[i + 1]; val bx = r.pts[i + 2]; val by = r.pts[i + 3]
                val l = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
                var s = (25f - acc % 25f)
                while (s < l) {
                    val off = (RW[r.type] ?: 5f) / 2 + 1.5f
                    val nx = -(by - ay) / l; val ny = (bx - ax) / l
                    val x = (ax + (bx - ax) * s / l + nx * off).roundToInt(); val y = (ay + (by - ay) * s / l + ny * off).roundToInt()
                    if (at(x, y) == K.WALK || at(x, y) == K.LAWN || at(x, y) == K.GROUND) lamps += intArrayOf(x, y)
                    s += 25f
                }
                acc += l; i += 2
            }
        }
        var y = 4
        while (y < h - 4) {
            var x = 4
            while (x < w - 4) {
                if (at(x, y) == K.DIRT && at(x, y - 2) == K.LAWN && at(x, y - 3) == K.LAWN && hash(x, y) < .22 && !occ[(y - 2) * w + x]) props += Prop("bench", x, y - 1)
                else if (at(x, y) == K.LAWN && hash(y, x) < .05 && clear(x, y, 4)) props += Prop("bed", x, y)
                x += 7
            }
            y += 7
        }
    }

    /** What kind of home stands nearest to a map point: an old city house, a block of flats, or a house of one's own. */
    fun homeKind(x: Float, y: Float): String {
        val home = blocks.minByOrNull { Math.hypot((it.cx - x).toDouble(), (it.cy - y).toDouble()) } ?: return "house"
        // how built-up the neighbourhood is: building pixels within ~150 m
        var built = 0; var all = 0
        val r = (150 / d.mpp).toInt()
        for (j in -r..r step 3) for (i in -r..r step 3) { all++; if (at(x.toInt() + i, y.toInt() + j) == K.BLD) built++ }
        val density = built.toDouble() / all
        return when {
            home.kind in setOf("house", "detached", "semidetached_house", "farm", "bungalow", "cabin", "hut", "terrace") -> "house"
            home.levels <= 2.5f && density < .18 -> "house"
            home.levels >= 8.5f -> "block"
            home.kind == "apartments" && home.levels >= 7f && !home.courtyard -> "block"
            else -> "well"
        }
    }

    /** May a bed go here: a lawn pixel within a few metres of a pavement, a park path or a road. */
    fun verge(x: Int, y: Int): Boolean {
        if (at(x, y) != K.LAWN) return false
        var near = false
        for (j in -3..3) for (i in -3..3) { val c = at(x + i, y + j); if (c == K.WALK || c == K.DIRT || c == K.ROAD) near = true; if (c == K.BLD || c == K.WATER) return false }
        return near
    }

    /** The nearest verge pixel within [r] pixels, or null. */
    fun nearestVerge(x: Int, y: Int, r: Int): IntArray? {
        var best: IntArray? = null; var bd = Int.MAX_VALUE
        for (j in -r..r) for (i in -r..r) {
            val d2 = i * i + j * j
            if (d2 < bd && verge(x + i, y + j)) { bd = d2; best = intArrayOf(x + i, y + j) }
        }
        return best
    }

    fun nearWater(x: Int, y: Int, r: Int): Boolean {
        for (j in -r..r) for (i in -r..r) if (at(x + i, y + j) == K.WATER) return true
        return false
    }

    companion object {
        val RW = mapOf("primary" to 8f, "primary_link" to 5f, "secondary" to 7f, "secondary_link" to 5f, "tertiary" to 6f, "tertiary_link" to 5f,
            "residential" to 5f, "unclassified" to 5f, "living_street" to 4f, "service" to 3f, "pedestrian" to 4f, "footway" to 2f, "path" to 2f,
            "cycleway" to 2f, "track" to 2f, "bridleway" to 2f, "steps" to 2f)
        val SIDE = setOf("primary", "secondary", "tertiary", "residential", "unclassified")
        val CARS = setOf("primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link", "residential", "unclassified", "living_street", "service")
        val UNPAVED = setOf("unpaved", "ground", "dirt", "gravel", "fine_gravel", "compacted", "sand", "grass", "earth", "pebblestone")

        fun hash(i: Int, j: Int): Double {
            var hh = i * 374761393 + j * 668265263
            hh = (hh xor (hh ushr 13)) * 1274126177
            return ((hh xor (hh ushr 16)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }
}
