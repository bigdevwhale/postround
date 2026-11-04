package app.postround.ui.art

import app.postround.domain.Block
import app.postround.domain.K
import app.postround.domain.Prop
import app.postround.domain.Terrain
import app.postround.domain.Tree
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Ramps the city needs on top of Lipovka's. */
object G {
    private fun r(vararg c: Long) = IntArray(c.size) { rgb(c[it]) }
    val granite = r(0x5a4e48, 0x786a62, 0x96887e, 0xb2a498, 0xcbbfb2, 0xe2d8cc, 0xf2ebe2)
    val asph = r(0x544a44, 0x665a52, 0x786b62, 0x8a7d72, 0x9c8f83, 0xaea193)
    val dirt = r(0x5a3416, 0x7a4a20, 0x9c642c, 0xbd8040, 0xd49e58, 0xe6b874, 0xf2d096)
    val sand = r(0x8a6430, 0xb08440, 0xd0a558, 0xe6c278, 0xf2da9a, 0xfaecc0)
    val lime = r(0x5a4a10, 0x8a7212, 0xbf9e18, 0xe6c42a, 0xf5dd52, 0xfcef88)
    val oak = r(0x2a2410, 0x463a18, 0x645424, 0x80703a, 0xa08c4e, 0xbcaa6a)
    val spruce = r(0x0c2a20, 0x123e2c, 0x1a5638, 0x247046, 0x348a56, 0x4ca468)
    val teal = r(0x0e3a44, 0x16586a, 0x1f7a8c, 0x34a0ae, 0x6cc8cc, 0xbfe8e4)
    val ochre = r(0x6a4210, 0x9a6418, 0xc88a24, 0xe6ac3c, 0xf5ca62, 0xfde49a)
    val salmon = r(0x6a2a20, 0x9a3e30, 0xc85a44, 0xe67c5e, 0xf5a282, 0xfcc8ac)
    val mint = r(0x1e5a48, 0x2a7a60, 0x3c9a78, 0x5cba94, 0x88d6b4, 0xbaeed6)
    val sky2 = r(0x22406e, 0x305c96, 0x4478ba, 0x6496d4, 0x8eb6e6, 0xbcd6f4)
    val plum = r(0x2a0c18, 0x4a1428, 0x6a2038, 0x8a3048, 0xa84a60)
    val bottle = r(0x081a12, 0x10301e, 0x1a462c, 0x265e3c, 0x367a4e)
    val navy = r(0x080e1e, 0x101a34, 0x1a284a, 0x263862, 0x344a7a)
    val fort = r(0x3a2418, 0x5a3624, 0x7a4c34, 0x9a6446, 0xb88058, 0xd4a074, 0xe8c094)
    val grassLate = r(0x3d3a28, 0x564f33, 0x6f6640, 0x8a7d4e, 0xa3955f, 0xbcae74, 0xd2c68e)
    val tin = r(0x1a2a5a, 0x243e80, 0x3256a6, 0x4672c4, 0x6290d8, 0x88b0e8, 0xb4d0f4)
    val rust = r(0x5a1410, 0x861f16, 0xb02e20, 0xd0442c, 0xe8623e, 0xf58a5a, 0xfbb082)
    val roofW = r(0x3a1e0e, 0x5a3018, 0x7a4422, 0x9a5a2e, 0xb8763e, 0xd09456, 0xe4b47a)
    val roofGl = r(0x14401e, 0x1e5c2a, 0x2a7a36, 0x3c9a44, 0x58b858, 0x82d07a, 0xb0e6a4)
    val water = r(0x123e78, 0x1a5aa0, 0x2475c4, 0x3b92dc, 0x5cb0ec, 0x8acdf5, 0xc2e8fb)
    val iron = r(0x100c0c, 0x1e1818, 0x2e2626, 0x423836, 0x5a4e4a, 0x766a62, 0x968a80)
    val SHADOW = rgb(0x2a3c6a)
    val WARM = rgb(0xf8cf7a)
    val WARM2 = rgb(0xffe6a8)
    val FAC = listOf(ochre, salmon, mint, sky2, R.cream, ochre, salmon, R.lilac, R.cream)
    val ROOFS = listOf(rust, tin, rust, roofGl, roofW, rust, tin, roofW)
}

/** The grass of the month: late-summer green, autumn olive, dull late autumn and winter. */
fun grassOf(month: Int): IntArray = when (month) {
    in 4..8 -> R.grassSummer
    9, 10 -> R.grass
    else -> G.grassLate
}

/**
 * Paints the whole district once: textured ground, embankments, shadows, raised buildings with hipped roofs,
 * trees by season, lamps and the small things. Windows that light up at night are collected in [windows].
 */
class MapPainter(
    val t: Terrain, val month: Int,
    /** The part of the map to paint (default: all of it). A window around the player paints in a fraction of a second. */
    private val rx0: Int = 0, private val ry0: Int = 0, private val rx1: Int = t.w, private val ry1: Int = t.h,
    val b: Buf = Buf(t.w, t.h),
) {
    val windows = ArrayList<Int>()
    private val grass = grassOf(month)
    private val winter = month in listOf(12, 1, 2)

    fun paint(): Buf {
        ground()
        shadows()
        val objs = ArrayList<Pair<Int, () -> Unit>>()
        val m = 24
        fun inside(x: Int, y: Int) = x >= rx0 - m && x < rx1 + m && y >= ry0 - m && y < ry1 + m
        for (k in t.blocks) if (k.x1 >= rx0 - m && k.x0 < rx1 + m && k.y1 >= ry0 - m && k.y0 - k.h < ry1 + m) objs += k.y1 to { building(k) }
        for (tr in t.trees) if (inside(tr.x, tr.y)) objs += tr.y to { tree(tr) }
        for (l in t.lamps) if (inside(l[0], l[1])) objs += l[1] to { lamp(l[0], l[1]) }
        for (p in t.props) if (inside(p.x, p.y)) objs += p.y to { prop(p) }
        objs.sortBy { it.first }
        objs.forEach { it.second() }
        return b
    }

    private fun at(x: Int, y: Int) = t.at(x, y)

    private fun ground() {
        val w = t.w; val h = t.h
        val edge = { q: Int -> q == K.WALK || q == K.ROAD || q == K.DIRT || q == K.STEPS }
        for (y in maxOf(0, ry0) until minOf(h, ry1)) for (x in maxOf(0, rx0) until minOf(w, rx1)) {
            val i = y * w + x
            val c = t.cls[i].toInt()
            val col: Int = when (c) {
                K.LAWN, K.PITCH -> {
                    if (edge(at(x, y - 1)) || edge(at(x, y + 1)) || edge(at(x - 1, y)) || edge(at(x + 1, y)))
                        rp(G.granite, .62 + if (edge(at(x, y - 1))) .15 else 0.0, x, y)
                    else {
                        // smooth meadow with little tufts: a light blade over a dark root
                        var v = .46 + (fbm(x * .07, y * .07) - .5) * .3
                        val tuft = hash(x, y)
                        if (tuft < .035) v += .2 else if (hash(x, y - 1) < .035) v -= .14
                        if (c == K.PITCH) v = .5 + ((y shr 2) and 1) * .08
                        var cc = rp(grass, v, x, y)
                        val hv = hash(x * 7, y * 3)
                        if (month in 5..8 && hv < .006) cc = listOf(R.cream[4], R.honey[4], R.jam[5], R.lilac[4])[(hash(x, y * 11) * 4).toInt()]
                        if (month in 9..10 && hv < .02) cc = rp(R.autumn, .3 + hash(y, x * 3) * .7, x, y)
                        if ((month == 11 || month == 3) && hv < .03) cc = rp(G.oak, .2 + hash(y, x * 3) * .5, x, y)
                        if (winter) cc = if (fbm(x * .09, y * .09) > .3 || hv < .5) rp(R.snow, .45 + fbm(x * .2, y * .2) * .45, x, y) else rp(grass, .2, x, y)
                        cc
                    }
                }
                K.WOOD -> {
                    var cc = rp(grass, .18 + fbm(x * .15, y * .15) * .35, x, y)
                    if (month in 9..11 && hash(x * 5, y * 9) < .18) cc = rp(if (month < 11) R.autumn else G.oak, .15 + hash(x, y * 7) * .6, x, y)
                    if (winter) cc = rp(R.snow, .3 + fbm(x * .2, y * .2) * .4, x, y)
                    cc
                }
                K.SAND -> rp(G.sand, .5 + fbm(x * .2, y * .2) * .3 + if (hash(x, y) < .06) -.2 else 0.0, x, y)
                K.WATER -> water(x, y)
                K.ROAD -> when {
                    t.rail[i] -> rp(G.iron, .75 + ((x + y) and 1) * .15, x, y)
                    t.lane[i] -> rgb(0xd8d0b4)
                    at(x, y - 1) == K.WALK || at(x, y + 1) == K.WALK || at(x - 1, y) == K.WALK || at(x + 1, y) == K.WALK -> rp(G.granite, .5, x, y)
                    else -> rp(G.asph, .42 + fbm(x * .3, y * .3) * .25 + if (hash(x, y) < .04) .15 else 0.0, x, y)
                }
                K.WALK -> {
                    val slab = (((x shr 2) + ((y shr 1) and 1) * 2) % 2)
                    var v = .6 + fbm(x * .25, y * .25) * .12 + slab * .05
                    if ((x and 3) == 0 || y % 3 == 0) v -= .1
                    if (winter && fbm(x * .11, y * .11) > .55) rp(R.snow, .35 + fbm(x * .3, y * .3) * .3, x, y) else rp(R.stone, v, x, y)
                }
                K.DIRT -> {
                    var cc = rp(G.dirt, .5 + fbm(x * .2, y * .2) * .25 + (if (hash(x, y) < .07) .18 else 0.0) - (if (hash(y * 3, x) < .05) .18 else 0.0), x, y)
                    if (month in 9..10 && hash(x * 13, y * 7) < .06) cc = rp(R.autumn, .3 + hash(x, y) * .6, x, y)
                    cc
                }
                K.BRIDGE -> {
                    val o = listOf(at(x, y - 1), at(x, y + 1), at(x - 1, y), at(x + 1, y))
                    if (t.rail[i]) rp(G.iron, .8, x, y)
                    else if (o.any { it != K.BRIDGE }) rp(G.iron, if (o[0] != K.BRIDGE) .75 else .35, x, y)
                    else rp(G.asph, .45 + fbm(x * .3, y * .3) * .2, x, y)
                }
                K.STEPS -> rp(G.granite, if (y and 1 == 1) .75 else .45, x, y)
                K.BLD -> rp(R.stone, .4, x, y)
                else -> {
                    if (at(x, y - 1) == K.WATER || at(x, y + 1) == K.WATER || at(x - 1, y) == K.WATER || at(x + 1, y) == K.WATER) rp(G.granite, .82, x, y)
                    else {
                        var v = .5 + fbm(x * .2, y * .2) * .18
                        if (((x + ((y shr 1) and 1)) and 1) == 0 && (y and 1) == 0) v += .07
                        var cc = rp(R.stone, v, x, y)
                        if (month in 9..10 && hash(x * 11, y * 3) < .012) cc = rp(R.autumn, .5, x, y)
                        if (winter && fbm(x * .07, y * .07) > .42) cc = rp(R.snow, .4 + fbm(x * .3, y * .3) * .4, x, y)
                        cc
                    }
                }
            }
            b.d[i] = col
        }
        // the granite parapet along the water
        for (y in maxOf(1, ry0) until minOf(h - 2, ry1)) for (x in maxOf(1, rx0) until minOf(w - 1, rx1)) {
            val i = y * w + x
            val c = t.cls[i].toInt()
            if (c == K.WATER || c == K.BRIDGE || c == K.BLD) continue
            if (t.cls[i + w].toInt() == K.WATER || t.cls[i + 2 * w].toInt() == K.WATER) b.d[i] = rp(G.granite, if (t.cls[i + w].toInt() == K.WATER) .55 else .8, x, y)
        }
    }

    private fun water(x: Int, y: Int): Int {
        val up1 = at(x, y - 1); val up2 = at(x, y - 2)
        if (up1 != K.WATER && up1 != K.BRIDGE) return rp(G.granite, .28 + hash(x, y) * .1, x, y)
        if (up2 != K.WATER && up2 != K.BRIDGE) return rp(G.granite, .16, x, y)
        val n = fbm(x * .06, y * .18)
        var v = .3 + n * .28
        if (at(x, y - 1) == K.BRIDGE || at(x, y - 2) == K.BRIDGE || at(x, y - 3) == K.BRIDGE) v -= .22
        if ((x + (n * 14).toInt()) % 11 == 0 && y % 3 == 0) v += .22
        if (at(x, y - 3) != K.WATER && at(x, y - 3) != K.BRIDGE) v -= .1
        if (month in listOf(12, 1, 2)) return rp(R.ice, .45 + n * .3 + if ((x + y * 3) % 17 == 0) .2 else 0.0, x, y)
        return rp(G.water, v, x, y)
    }

    private fun shadows() {
        val sh = BooleanArray(t.n)
        for (k in t.blocks) {
            if (k.x1 < rx0 - 30 || k.x0 > rx1 || k.y1 < ry0 - 30 || k.y0 > ry1) continue
            val sl = (k.h * .9).roundToInt() + 1
            for (i in k.pts) {
                val x = i % t.w; val y = i / t.w
                for (s in 1..sl) {
                    val tx = x + s; val ty = y + (s * .35).roundToInt()
                    if (tx >= t.w || ty >= t.h) break
                    val q = ty * t.w + tx
                    if (t.cls[q].toInt() != K.BLD) sh[q] = true
                }
            }
        }
        for (y in maxOf(0, ry0) until minOf(t.h, ry1)) for (x in maxOf(0, rx0) until minOf(t.w, rx1)) if (sh[y * t.w + x]) b.mul(x, y, G.SHADOW, .32f)
    }

    private fun building(k: Block) {
        val h = k.h; val x0 = k.x0; val y0 = k.y0 - h; val bw = k.x1 - k.x0 + 1; val bh = k.y1 - k.y0 + 1 + h
        val n = bw * bh
        val f = BooleanArray(n); val rf = BooleanArray(n); val wl = BooleanArray(n)
        for (i in k.pts) { val x = i % t.w; val y = i / t.w; f[(x - x0) + (y - y0) * bw] = true }
        for (v in h until bh) for (u in 0 until bw) if (f[u + v * bw]) rf[u + (v - h) * bw] = true
        for (u in 0 until bw) for (v in 0 until bh) if (f[u + v * bw]) for (s in 0..h) { if (v - s < 0) break; val j = u + (v - s) * bw; if (!rf[j]) wl[j] = true }
        val seed = k.index * 7
        val castle = k.kind == "castle"
        val roof = G.ROOFS[(hash(k.index, 5) * G.ROOFS.size).toInt()]
        val fac = if (castle) G.fort else if (k.kind == "ship") R.cream else G.FAC[(hash(k.index, 3) * G.FAC.size).toInt()]
        val lit = k.kind !in setOf("castle", "garage", "garages", "industrial", "service", "shed")
        fun r(u: Int, v: Int) = u in 0 until bw && v in 0 until bh && rf[u + v * bw]
        fun wa(u: Int, v: Int) = u in 0 until bw && v in 0 until bh && wl[u + v * bw]
        for (v in 0 until bh) for (u in 0 until bw) {
            val j = u + v * bw; val x = u + x0; val y = v + y0
            if (rf[j]) {
                if (castle) { b.p(x, y, rp(grass, .45 + fbm(x * .2, y * .2) * .3 + if (!r(u, v - 1)) .25 else 0.0, x, y)); continue }
                var dT = 0; var dB = 0; var dL = 0; var dR = 0
                while (dT < 8 && r(u, v - dT - 1)) dT++
                while (dB < 8 && r(u, v + dB + 1)) dB++
                while (dL < 8 && r(u - dL - 1, v)) dL++
                while (dR < 8 && r(u + dR + 1, v)) dR++
                val mn = minOf(dT, dB, dL, dR)
                var s = if (mn >= 8) .56 else if (mn == dT) .74 else if (mn == dL) .62 else if (mn == dR) .44 else .36
                if (mn < 8 && (if (mn == dT || mn == dB) (x + seed) % 3 == 0 else (y + seed) % 3 == 0)) s -= .07
                if ((dT == dB || dL == dR) && mn in 1..7) s += .1
                if (mn == 0 && dT == 0) s += .1
                s += (fbm(x * .15, y * .15) - .5) * .08
                b.p(x, y, if (winter && mn > 0 && fbm(x * .2, y * .2) > .28) rp(R.snow, s + .1, x, y) else rp(roof, s, x, y))
            } else if (wl[j]) {
                var dn = 0; while (wa(u, v + dn + 1)) dn++
                var up = 0; while (wa(u, v - up - 1)) up++
                val run = dn + up + 1
                var s = .48 + (fbm(x * .3, y * .3) - .5) * .12 + up.toDouble() / run * .12
                if (dn == 0) s -= .24
                if (up == 0) s += .22
                if (!(wa(u - 1, v) || r(u - 1, v))) s += .1
                if (!(wa(u + 1, v) || r(u + 1, v))) s -= .14
                var col = rp(fac, s, x, y)
                if (!castle && run >= 3 && dn > 0 && up > 0 && dn % 2 == 1 && (x + seed) % 3 == 1) {
                    col = rp(R.glass, .18 + hash(x, y) * .25, x, y)
                    if (lit) windows += y * t.w + x
                }
                b.p(x, y, col)
            }
        }
        for (v in -1..bh) for (u in -1..bw) {
            if (r(u, v) || wa(u, v)) continue
            if (r(u - 1, v) || r(u + 1, v) || r(u, v - 1) || r(u, v + 1) || wa(u - 1, v) || wa(u + 1, v) || wa(u, v - 1) || wa(u, v + 1)) b.p(u + x0, v + y0, R.INK, .55f)
        }
        if (!castle && k.area > 120) {
            val cnt = min(7, k.area / 260 + 1)
            var tries = 0; var made = 0
            while (made < cnt && tries++ < 80) {
                val u = (hash(seed, tries) * bw).toInt(); val v = (hash(tries, seed) * bh).toInt()
                if (!r(u, v) || !r(u + 2, v) || !r(u, v + 3) || v < 2) continue
                val x = u + x0; val y = v + y0
                if (hash(x, y) < .75) {
                    for (jj in 0 until 4) for (ii in 0 until 2) b.p(x + ii, y - 2 + jj, rp(R.brick, .65 - ii * .3 - jj * .05, x + ii, y + jj))
                    b.p(x, y - 3, R.brick[0]); b.p(x + 1, y - 3, R.brick[1]); b.mul(x + 2, y, G.SHADOW, .4f); b.mul(x + 2, y + 1, G.SHADOW, .4f)
                } else { b.r(x, y, 2, 1, R.iron[1]); b.p(x, y - 1, R.iron[4]) }
                made++
            }
        }
    }

    private fun treeRamp(tr: Tree): IntArray? {
        val h = tr.seed
        if (tr.kind == "spruce") return G.spruce
        return when (month) {
            5, 6, 7, 8 -> if (month == 8 && h < .12) G.lime else R.green
            4 -> if (h < .5) R.green else null
            9, 10 -> when (tr.kind) { "maple" -> if (h < .5) R.autumn else R.apple; "oak" -> G.oak; "elm" -> if (h < .4) R.green else G.lime; else -> G.lime }
            11 -> if (tr.kind == "oak" && h < .6) G.oak else null
            else -> null
        }
    }

    private fun tree(tr: Tree) {
        val x = tr.x; val y = tr.y; val r = tr.r
        val ramp = treeRamp(tr)
        for (j in -2..2) for (i in -r..r + 3) { val a = (i - 1.5) / (r + 2); val c = j / 2.2; if (a * a + c * c <= 1) b.mul(x + i + 1, y + j, G.SHADOW, .36f) }
        if (tr.kind == "spruce") {
            val hh = r * 3 + 3
            for (j in 0 until hh) {
                val w = ((j + 1).toDouble() / hh * (r + .5)).roundToInt() - if (j % 3 == 0 && j > 2) 1 else 0
                for (i in -w..w) b.p(x + i, y - 3 - hh + j, rp(G.spruce, .3 + (-i.toDouble() / (w + 1)) * .3 + (if (j % 3 == 1) .15 else 0.0) + hash(x + i, j) * .1, x + i, y + j))
                b.p(x - w - 1, y - 3 - hh + j, R.INK, .5f); b.p(x + w + 1, y - 3 - hh + j, R.INK, .5f)
                if (month in listOf(12, 1, 2) && j % 3 == 0) for (i in -w..w) if (hash(i, j + x) < .5) b.p(x + i, y - 3 - hh + j, R.snow[5])
            }
            b.r(x, y - 3, 1, 3, R.wood[1]); return
        }
        val birch = tr.kind == "birch"
        for (k in 0 until 4) {
            b.p(x, y - k, if (birch) (if (k % 2 == 1) R.cream[4] else R.INK) else rp(R.wood, .3, x, y - k))
            b.p(x + 1, y - k, if (birch) R.cream[2] else R.wood[1])
        }
        val rx = if (birch) r - 1 else r; val ry = if (birch) r + 1 else r
        val cy = y - 3 - ry
        if (ramp == null) {
            val br = R.woodG
            for (k in 0 until 5) { val a = -PI / 2 + (k - 2) * .45 + hash(x, k) * .2; val l = ry * 1.6; b.line(x.toDouble(), y - 3.0, x + cos(a) * l * .9, y - 3 + sin(a) * l, br[2]) }
            for (k in 0 until 9) { val a = -PI / 2 + (hash(x, k * 7) - .5) * 2.4; val l = ry * (1.2 + hash(k, x) * .8); b.p(x + cos(a) * l, y - 3 + sin(a) * l, br[3]) }
            if (month in listOf(12, 1, 2)) for (k in 0 until 5) b.p(x + (hash(x, k + 40) * rx * 2 - rx).toInt(), cy - ry / 2 + (hash(k, x) * ry).toInt(), R.snow[5])
            return
        }
        val sp = sph(rx + .5, ry + .5, rx + .5, ry + .5, .18, .2)
        b.vol(x - rx, cy - ry, rx * 2 + 1, ry * 2 + 1,
            { u, v -> val a = (u - rx) / (rx + .5); val c = (v - ry) / (ry + .5); a * a + c * c <= 1 - (fbm((x + u) * .55, (y + v) * .55) - .5) * .55 },
            { u, v -> sp(u, v) + (fbm((x + u) * .7, (y + v) * .7) - .5) * .5 }, ramp, mix(ramp[0], R.INK, .5f))
    }

    private fun lamp(x: Int, y: Int) {
        b.mul(x + 1, y, G.SHADOW, .4f); b.mul(x + 2, y, G.SHADOW, .3f)
        for (k in 0 until 7) b.p(x, y - k, if (k == 0) G.iron[1] else rp(G.iron, .35 + if (k == 6) .3 else 0.0, x, y - k))
        b.p(x - 1, y - 7, G.iron[2]); b.p(x + 1, y - 7, G.iron[2]); b.p(x - 1, y - 8, G.iron[1]); b.p(x, y - 8, G.iron[3]); b.p(x + 1, y - 8, G.iron[1])
        b.p(x, y - 7, R.cream[3])
    }

    private fun prop(p: Prop) {
        val x = p.x; val y = p.y
        when (p.kind) {
            "bench" -> {
                b.r(x - 2, y, 5, 1, R.wood[5]); b.r(x - 2, y - 1, 5, 1, R.wood[3]); b.p(x - 2, y + 1, R.iron[1]); b.p(x + 2, y + 1, R.iron[1])
                for (i in -2..3) b.mul(x + i, y + 2, G.SHADOW, .4f)
                b.p(x - 3, y - 2, R.INK, .4f); b.r(x - 2, y - 2, 5, 1, R.wood[2])
            }
            "bed" -> {
                for (j in -3..3) for (i in -4..4) {
                    val a = i / 4.5; val c = j / 3.2; val d = a * a + c * c
                    if (d > 1) continue
                    var col = if (d > .7) rp(G.granite, .6, x + i, y + j) else rp(G.dirt, .2 + hash(x + i, y + j) * .2, x + i, y + j)
                    if (d <= .7 && month in 5..9 && hash(x * 3 + i, y * 7 + j) < (if (month in 6..8) .6 else .25))
                        col = listOf(R.apple[4], R.honey[4], R.cream[5], R.lilac[4], R.jam[5])[(hash(i + x, j + y) * 5).toInt()]
                    b.p(x + i, y + j, col)
                }
                for (i in -4..4) b.mul(x + i, y + 4, G.SHADOW, .4f)
            }
        }
    }
}

/** Distance helper for glows. */
fun dist(ax: Int, ay: Int, bx: Int, by: Int) = hypot((ax - bx).toDouble(), (ay - by).toDouble())

/** Keeps a value in a range; tiny shorthand used by the scenes. */
fun clampD(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))
