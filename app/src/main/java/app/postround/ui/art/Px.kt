package app.postround.ui.art

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A small ARGB pixel buffer every scene is painted into, one art pixel per cell. Screens scale it up
 * with nearest-neighbour filtering. Colours are 0xAARRGGBB ints; drawing past the edges is ignored.
 */
class Buf(val w: Int, val h: Int) {
    val d = IntArray(w * h)

    fun copyFrom(o: Buf) = System.arraycopy(o.d, 0, d, 0, d.size)

    fun get(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) 0 else d[y * w + x]

    /** Paints a pixel, blending by [a] (0..1) over what is there. */
    fun p(x: Int, y: Int, c: Int, a: Float = 1f) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0f) return
        val i = y * w + x
        if (a >= 1f || d[i] ushr 24 == 0) {
            d[i] = if (a >= 1f) c or 0xFF000000.toInt() else (c and 0xFFFFFF) or ((a * 255).toInt() shl 24)
            return
        }
        val o = d[i]
        val r = ch(o, 16) + ((ch(c, 16) - ch(o, 16)) * a).toInt()
        val g = ch(o, 8) + ((ch(c, 8) - ch(o, 8)) * a).toInt()
        val b = ch(o, 0) + ((ch(c, 0) - ch(o, 0)) * a).toInt()
        val al = max(o ushr 24, (a * 255).toInt())
        d[i] = (al shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun p(x: Double, y: Double, c: Int, a: Float = 1f) = p(floor(x).toInt(), floor(y).toInt(), c, a)

    /** Screen blend: lightens, for light falling on things. */
    fun scr(x: Int, y: Int, c: Int, a: Float) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0f) return
        val i = y * w + x
        val o = d[i]
        fun one(sh: Int): Int {
            val s = ch(c, sh) / 255f
            val v = ch(o, sh) / 255f
            val out = v + ((1 - (1 - s) * (1 - v)) - v) * a
            return (out * 255).roundToInt().coerceIn(0, 255)
        }
        d[i] = (o and 0xFF000000.toInt()) or (one(16) shl 16) or (one(8) shl 8) or one(0)
    }

    /** Multiply blend: darkens and tints, for night and shadow. */
    fun mul(x: Int, y: Int, c: Int, a: Float) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0f) return
        val i = y * w + x
        val o = d[i]
        fun one(sh: Int): Int {
            val s = ch(c, sh) / 255f
            val v = ch(o, sh) / 255f
            return ((v + (v * s - v) * a) * 255).roundToInt().coerceIn(0, 255)
        }
        d[i] = (o and 0xFF000000.toInt()) or (one(16) shl 16) or (one(8) shl 8) or one(0)
    }

    fun r(x: Int, y: Int, w: Int, h: Int, c: Int, a: Float = 1f) {
        for (j in 0 until h) for (i in 0 until w) p(x + i, y + j, c, a)
    }

    fun clear() = d.fill(0)

    companion object {
        fun ch(c: Int, sh: Int) = (c shr sh) and 0xFF
    }
}

fun rgb(hex: Long): Int = (hex or 0xFF000000).toInt()

/** Mixes two colours. */
fun mix(a: Int, b: Int, t: Float): Int {
    fun one(sh: Int) = (Buf.ch(a, sh) + (Buf.ch(b, sh) - Buf.ch(a, sh)) * t).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (one(16) shl 16) or (one(8) shl 8) or one(0)
}

private val B4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/** The 4×4 ordered-dither threshold at a pixel, 0..15. */
fun bay(x: Int, y: Int): Int = B4[((y % 4 + 4) % 4) * 4 + ((x % 4 + 4) % 4)]

/** A stable pseudo-random number in 0..1 for a pair of ints. */
fun hash(i: Int, j: Int): Double {
    var h = i * 374761393 + j * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)).toLong() and 0xFFFFFFFFL) / 4294967296.0
}

fun rnd(i: Int): Double = hash(i, i * 7 + 3)

fun vn(x: Double, y: Double): Double {
    val i = floor(x).toInt()
    val j = floor(y).toInt()
    val fx = x - i
    val fy = y - j
    val a = hash(i, j)
    val b = hash(i + 1, j)
    val c = hash(i, j + 1)
    val d = hash(i + 1, j + 1)
    val ux = fx * fx * (3 - 2 * fx)
    val uy = fy * fy * (3 - 2 * fy)
    return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy
}

fun fbm(x: Double, y: Double): Double = vn(x, y) * .5 + vn(x * 2.03, y * 2.03) * .3 + vn(x * 4.1, y * 4.1) * .2

/** Picks a colour from a ramp for a value 0..1, dithering between neighbours. */
fun rp(ramp: IntArray, v: Double, x: Int, y: Int): Int {
    val n = ramp.size - 1
    val vv = v.coerceIn(0.0, .9999)
    val f = vv * n
    val i = floor(f).toInt()
    val fr = f - i
    return if (i < n && fr * 16 > bay(x, y) + .5) ramp[i + 1] else ramp[i]
}

/** The light direction: from the upper left, towards the viewer. */
private val LV = doubleArrayOf(-.48, -.66, .58).let { v -> val l = sqrt(v.sumOf { it * it }); DoubleArray(3) { v[it] / l } }

/**
 * Paints a shaded volume: every cell inside [mask] gets a colour from [ramp] by its [light] (0..1),
 * then an outline of [ol] goes round it. Coordinates passed to the lambdas are local to (x, y).
 */
inline fun Buf.vol(
    x: Int, y: Int, w: Int, h: Int,
    mask: (Int, Int) -> Boolean,
    light: (Int, Int) -> Double,
    ramp: IntArray,
    ol: Int? = null,
) {
    val m = BooleanArray((w + 2) * (h + 2))
    for (v in 0 until h) for (u in 0 until w) if (mask(u, v)) {
        m[(v + 1) * (w + 2) + u + 1] = true
        p(x + u, y + v, rp(ramp, light(u, v), x + u, y + v))
    }
    if (ol != null) outline(m, x, y, w, h, ol)
}

fun Buf.outline(m: BooleanArray, x: Int, y: Int, w: Int, h: Int, ol: Int) {
    fun at(u: Int, v: Int) = u >= 0 && v >= 0 && u < w && v < h && m[(v + 1) * (w + 2) + u + 1]
    for (v in -1..h) for (u in -1..w) {
        if (at(u, v)) continue
        if (at(u - 1, v) || at(u + 1, v) || at(u, v - 1) || at(u, v + 1)) p(x + u, y + v, ol)
    }
}

/** Sphere-like shading for an ellipse centred at (cx, cy) with radii rx, ry. */
fun sph(cx: Double, cy: Double, rx: Double, ry: Double, amb: Double = .16, spec: Double = .25): (Int, Int) -> Double = { u, v ->
    val nx = (u + .5 - cx) / rx
    val ny = (v + .5 - cy) / ry
    val nz = sqrt(max(0.0, 1 - nx * nx - ny * ny))
    val d = nx * LV[0] + ny * LV[1] + nz * LV[2]
    amb + (1 - amb) * max(0.0, d) * .9 + if (d > .94) spec else 0.0
}

fun ell(cx: Double, cy: Double, rx: Double, ry: Double): (Int, Int) -> Boolean = { u, v ->
    val a = (u + .5 - cx) / rx
    val b = (v + .5 - cy) / ry
    a * a + b * b <= 1
}

fun Buf.disc(cx: Int, cy: Int, r: Int, c: Int, a: Float = 1f) {
    for (j in -r..r) for (i in -r..r) if (i * i + j * j <= r * r + r * .8) p(cx + i, cy + j, c, a)
}

fun Buf.ball(cx: Int, cy: Int, r: Int, ramp: IntArray, ol: Int? = null) {
    val rr = r + .5
    vol(cx - r, cy - r, 2 * r + 1, 2 * r + 1, ell(rr, rr, rr, rr), sph(rr, rr, rr, rr), ramp, ol)
}

fun Buf.line(x0: Double, y0: Double, x1: Double, y1: Double, c: Int, a: Float = 1f) {
    var x = x0.roundToInt()
    var y = y0.roundToInt()
    val xe = x1.roundToInt()
    val ye = y1.roundToInt()
    val dx = abs(xe - x)
    val dy = -abs(ye - y)
    val sx = if (x < xe) 1 else -1
    val sy = if (y < ye) 1 else -1
    var e = dx + dy
    while (true) {
        p(x, y, c, a)
        if (x == xe && y == ye) break
        val e2 = 2 * e
        if (e2 >= dy) { e += dy; x += sx }
        if (e2 <= dx) { e += dx; y += sy }
    }
}

fun Buf.line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int, a: Float = 1f) = line(x0.toDouble(), y0.toDouble(), x1.toDouble(), y1.toDouble(), c, a)

/** A vertical gradient through a ramp, dithered. */
fun Buf.vgrad(x: Int, y: Int, w: Int, h: Int, ramp: IntArray) {
    for (j in 0 until h) for (i in 0 until w) p(x + i, y + j, rp(ramp, j / max(1.0, h - 1.0), x + i, y + j))
}

/** Light falling in stepped rings, the pixel-art way, with dithered edges. */
fun Buf.glow(cx: Int, cy: Int, r: Int, c: Int, maxA: Float, steps: Int = 5, screen: Boolean = true) {
    for (j in -r..r) for (i in -r..r) {
        val d = hypot(i.toDouble(), j.toDouble())
        if (d >= r) continue
        val t = (1 - d / r).let { it * it }
        val lv = floor(t * steps + (bay(cx + i, cy + j) / 16.0 - .5) * .8) / steps
        if (lv <= 0) continue
        if (screen) scr(cx + i, cy + j, c, (maxA * lv).toFloat()) else p(cx + i, cy + j, c, (maxA * lv).toFloat())
    }
}

/** Darkens the edges in stepped rings. */
fun Buf.vignette(c: Int, maxA: Float, cx: Double = w / 2.0, cy: Double = h / 2.0, rx: Double = w * .62, ry: Double = h * .62) {
    for (y in 0 until h) for (x in 0 until w) {
        val d = hypot((x - cx) / rx, (y - cy) / ry)
        if (d < .6) continue
        val lv = floor(min(1.0, (d - .6) / .6) * 4 + (bay(x, y) / 16.0 - .5) * .8) / 4
        if (lv > 0) mul(x, y, c, (maxA * lv).toFloat())
    }
}

/** Draws an ASCII sprite: each char picks a colour from [pal]; '.' and unknown chars are clear. */
fun Buf.spr(rows: List<String>, x: Int, y: Int, pal: Map<Char, Int>, flip: Boolean = false) {
    rows.forEachIndexed { j, row ->
        for (i in row.indices) {
            val c = pal[row[i]] ?: continue
            p(if (flip) x + row.length - 1 - i else x + i, y + j, c)
        }
    }
}

private val DIG = mapOf(
    '0' to "111101101101111", '1' to "010110010010111", '2' to "111001111100111", '3' to "111001111001111",
    '4' to "101101111001001", '5' to "111100111001111", '6' to "111100111101111", '7' to "111001010010010",
    '8' to "111101111101111", '9' to "111101111001111", ':' to "000010000010000",
)

/** Tiny 3×5 digits, for clocks, calendars and stamp values. */
fun Buf.digits(s: String, x: Int, y: Int, c: Int) {
    s.forEachIndexed { k, ch ->
        val g = DIG[ch] ?: return@forEachIndexed
        for (j in 0 until 5) for (i in 0 until 3) if (g[j * 3 + i] == '1') p(x + k * 4 + i, y + j, c)
    }
}

fun bez(p: DoubleArray, t: Double): Pair<Double, Double> {
    val u = 1 - t
    return Pair(
        u * u * u * p[0] + 3 * u * u * t * p[2] + 3 * u * t * t * p[4] + t * t * t * p[6],
        u * u * u * p[1] + 3 * u * u * t * p[3] + 3 * u * t * t * p[5] + t * t * t * p[7],
    )
}

fun quad(ax: Double, ay: Double, cx: Double, cy: Double, ex: Double, ey: Double, t: Double): Pair<Double, Double> {
    val u = 1 - t
    return Pair(u * u * ax + 2 * u * t * cx + t * t * ex, u * u * ay + 2 * u * t * cy + t * t * ey)
}

/**
 * Which pixels of a scene still show what lies behind it, like the sky past a roof or through a window:
 * [open] an area once it is painted, then paint whatever may stand in front of it inside [Buf.cover].
 * Clouds, stars and rain then go only where the mask is still open.
 */
class Mask(val w: Int, val h: Int) {
    private val m = BooleanArray(w * h)

    fun open(x0: Int, y0: Int, ww: Int, hh: Int) {
        for (y in max(0, y0) until min(h, y0 + hh)) for (x in max(0, x0) until min(w, x0 + ww)) m[y * w + x] = true
    }

    operator fun invoke(x: Int, y: Int) = x in 0 until w && y in 0 until h && m[y * w + x]

    internal fun shut(i: Int) { m[i] = false }
}

/** Paints with [draw], then shuts the [masks] wherever it changed a pixel. */
fun Buf.cover(vararg masks: Mask, draw: () -> Unit) {
    val before = d.copyOf()
    draw()
    for (i in d.indices) if (d[i] != before[i]) for (m in masks) m.shut(i)
}

fun cosD(a: Double) = cos(a)
fun sinD(a: Double) = sin(a)
