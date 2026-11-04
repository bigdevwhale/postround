package app.postround.ui.art

import app.postround.domain.Sky
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The moment a scene shows: time of day, weather, month, and the day (for stable randomness). */
data class Moment(val minute: Int, val sky: Sky, val month: Int, val day: Long) {
    /** 0 by day, up to .66 deep at night. */
    val dark: Float
        get() = when {
            minute < 6 * 60 -> .62f
            minute < 7 * 60 + 30 -> .25f
            minute < 17 * 60 + 30 -> 0f
            minute < 19 * 60 + 30 -> (minute - 17 * 60 - 30) / 120f * .3f
            else -> min(.66f, .3f + (minute - 19 * 60 - 30) / 90f * .36f)
        }
    val dusk get() = minute in (17 * 60 + 30) until 20 * 60
    val winter get() = month in listOf(12, 1, 2)
    val cloudy get() = sky.rain || sky.snow || sky.fog
}

/** How a person is drawn: built from the `look` column of people.tsv. */
data class Look(val skin: IntArray, val hair: IntArray, val cloth: IntArray, val extras: Set<String>, val hat: IntArray? = null) {
    companion object {
        private val CLOTH = mapOf("cream" to R.cream, "green" to R.green, "jam" to R.jam, "roofB" to R.roofB, "navy" to G.navy,
            "apple" to R.apple, "lilac" to R.lilac, "oak" to G.oak, "honey" to R.honey, "enamel" to R.enamel, "autumn" to R.autumn, "red" to R.red)
        private val HAIR = mapOf("wood" to R.wood, "grey" to R.woodG, "dark" to R.iron, "ginger" to R.ginger, "honey" to R.honey)
        fun of(m: Map<String, String>): Look {
            val skin = when (m["skin"]) { "3" -> R.ginger; else -> R.skin }
            val hair = HAIR[m["hair"]] ?: R.wood
            val cloth = CLOTH[m["cloth"]] ?: R.coat
            val hat = m["kerchief"]?.let { CLOTH[it] } ?: if ("cap" in m) CLOTH[m["cloth"]] else null
            return Look(skin, hair, cloth, m.keys + m.filterValues { it != "1" }.map { "${it.key}=${it.value}" }, hat)
        }
    }
}

internal fun Buf.rr(x: Int, y: Int, w: Int, h: Int, r: IntArray, v: (Int, Int) -> Double) {
    for (j in 0 until h) for (i in 0 until w) p(x + i, y + j, rp(r, v(i, j), x + i, y + j))
}

/** A sky band coloured by the hour and the weather, with drifting clouds and stars at night. */
fun Buf.skyBand(x0: Int, y0: Int, w: Int, h: Int, m: Moment, t: Int) {
    val r = when {
        m.minute >= 19 * 60 + 30 || m.minute < 6 * 60 -> R.night
        m.dusk -> if (m.cloudy) R.overcast else R.dusk
        m.minute < 8 * 60 -> R.morning
        m.cloudy -> R.overcast
        else -> R.day
    }
    for (y in 0 until h) for (x in 0 until w) {
        var v = when (r) { R.night -> .1 + y.toDouble() / h * .35; R.dusk -> .3 + y.toDouble() / h * .6; else -> .35 + y.toDouble() / h * .55 }
        if (fbm((x + t * .2) / 16, (y + 5) / 5.0) > .58) v += if (r === R.night) .05 else .12
        var c = rp(r, v, x0 + x, y0 + y)
        if (r === R.night && !m.cloudy && hash(x, y) < .012) c = R.cream[5]
        p(x0 + x, y0 + y, c)
    }
}

/** Rain streaks or snowflakes over a whole scene. */
fun Buf.precipitation(m: Moment, t: Int) {
    if (m.sky.rain) for (i in 0 until 60) {
        val x = ((hash(i, 1) * w + t * 2) % w).toInt(); val y = ((hash(i, 2) * h + t * 7) % h).toInt()
        p(x, y, R.glass[5], .4f); p(x - 1, y + 1, R.glass[5], .3f)
    }
    if (m.sky.snow) for (i in 0 until 50) {
        val x = ((hash(i, 3) * w + sin(t / 6.0 + i) * 3 + w) % w).toInt(); val y = ((hash(i, 4) * h + t * 1.2) % h).toInt()
        p(x, y, R.snow[5])
    }
}

/** Night falls over everything except the lit pixels in [keep]. */
fun Buf.night(m: Moment, keep: BooleanArray? = null) {
    val a = m.dark
    if (a <= 0f) return
    for (i in d.indices) if (keep == null || !keep[i]) {
        var c = d[i]
        if (m.dusk) c = mixMul(c, rgb(0xf2a26a), .2f)
        d[i] = mixMul(c, rgb(0x2a3a78), a * .95f)
    }
}

private fun mixMul(o: Int, c: Int, a: Float): Int {
    fun one(sh: Int): Int { val s = Buf.ch(c, sh) / 255f; val v = Buf.ch(o, sh) / 255f; return ((v + (v * s - v) * a) * 255).toInt().coerceIn(0, 255) }
    return (o and 0xFF000000.toInt()) or (one(16) shl 16) or (one(8) shl 8) or one(0)
}

/** A warm round light, dithered. */
fun Buf.lamplight(cx: Int, cy: Int, r: Int, a: Float) {
    for (j in -r..r) for (i in -r..r) { val d = hypot(i.toDouble(), j.toDouble()) / r; if (d < 1) scr(cx + i, cy + j, G.WARM, ((1 - d) * (1 - d) * a).toFloat()) }
}

/** A standing person, 13 pixels wide and 34 tall, feet at (cx, by). */
fun Buf.person(cx: Int, by: Int, l: Look) {
    val coat = l.cloth
    rr(cx - 3, by - 8, 2, 8, R.iron) { _, _ -> .3 }; rr(cx + 1, by - 8, 2, 8, R.iron) { _, _ -> .2 }
    r(cx - 4, by - 1, 3, 1, R.INK); r(cx + 1, by - 1, 3, 1, R.INK)
    vol(cx - 5, by - 20, 11, 13, { u, v -> kotlin.math.abs(u - 5) <= 4 - (if (v < 2) 1 else 0) + (if (v > 9) 1 else 0) }, { u, v -> .75 - u * .05 - v * .012 }, coat, R.INK)
    if ("apron" in l.extras) rr(cx - 3, by - 16, 7, 9, R.cream) { i, _ -> .85 - i * .04 }
    rr(cx - 6, by - 18, 2, 8, coat) { _, j -> .7 - j * .02 }; rr(cx + 5, by - 18, 2, 8, coat) { _, _ -> .4 }
    p(cx - 6, by - 10, l.skin[4]); p(cx + 5, by - 10, l.skin[3])
    ball(cx, by - 24, 3, l.skin, R.INK)
    val hr = l.hair
    rr(cx - 3, by - 28, 7, 2, hr) { i, _ -> .7 - i * .06 }; p(cx - 3, by - 26, hr[3]); p(cx + 3, by - 26, hr[2])
    if ("long" in l.extras) { rr(cx - 4, by - 27, 2, 7, hr) { _, _ -> .5 }; rr(cx + 3, by - 27, 2, 7, hr) { _, _ -> .35 } }
    p(cx - 1, by - 24, R.INK); p(cx + 1, by - 24, R.INK); p(cx - 2, by - 23, R.jam[5]); p(cx + 2, by - 23, R.jam[5]); p(cx, by - 22, R.red[2])
    if ("beard" in l.extras) { rr(cx - 3, by - 23, 7, 3, hr) { _, _ -> .5 }; p(cx, by - 22, R.red[1]) }
    if ("moustache" in l.extras) r(cx - 1, by - 22, 3, 1, hr[2])
    if ("glasses" in l.extras) { r(cx - 2, by - 25, 2, 1, R.INK); r(cx + 1, by - 25, 2, 1, R.INK); p(cx, by - 24, R.INK) }
    if (l.extras.any { it.startsWith("hat=toque") }) { rr(cx - 3, by - 33, 7, 5, R.cream) { i, _ -> .95 - i * .06 }; r(cx - 4, by - 28, 9, 1, R.cream[2]) }
    if (l.extras.any { it.startsWith("kerchief") } && l.hat != null) {
        rr(cx - 4, by - 29, 9, 3, l.hat) { i, _ -> .7 - i * .05 }; r(cx - 4, by - 26, 1, 4, l.hat[3]); r(cx + 4, by - 26, 1, 4, l.hat[2])
        p(cx - 2, by - 28, R.cream[5]); p(cx + 2, by - 29, R.cream[5])
    } else if ("cap" in l.extras || l.extras.any { it == "hat=cap" }) {
        val c = l.hat ?: G.navy
        rr(cx - 3, by - 30, 7, 3, c) { i, _ -> .7 - i * .07 }; r(cx - 4, by - 27, 9, 1, R.INK)
    }
    if ("bun" in l.extras) ball(cx, by - 30, 2, hr, R.INK)
    if ("curls" in l.extras) for (k in -4..4 step 2) ball(cx + k, by - 28, 1, hr, null)
    if ("pigtails" in l.extras) { ball(cx - 5, by - 25, 1, hr, R.INK); ball(cx + 5, by - 25, 1, hr, R.INK) }
    if ("earrings" in l.extras) { p(cx - 4, by - 23, R.gold[4]); p(cx + 4, by - 23, R.gold[4]) }
}

/** What a shop window shows, by the neighbour's place. */
internal fun Buf.goods(kind: String, x: Int, y: Int, w: Int, h: Int) {
    fun shelf(yy: Int) { r(x, yy, w, 1, R.wood[4]); r(x, yy + 1, w, 1, R.wood[1]) }
    when (kind) {
        "bakery" -> { shelf(y + 8); shelf(y + h - 3); for (i in 0 until w / 8) ball(x + 4 + i * 7, y + 5, 2, R.honey, R.INK); for (i in 0 until w / 9) vol(x + 3 + i * 8, y + h - 8, 7, 4, ell(3.5, 2.0, 3.5, 1.8), sph(3.5, 2.0, 3.5, 1.8), R.ginger, R.INK) }
        "pharmacy" -> { shelf(y + 8); shelf(y + h - 3); for (i in 0 until w / 5 - 1) { rr(x + 3 + i * 5, y + 3, 3, 5, if (i % 2 == 1) R.glass else G.bottle) { _, _ -> .6 }; r(x + 3 + i * 5, y + 2, 3, 1, R.cream[4]) } }
        "cafe" -> { shelf(y + h - 3); for (i in 0 until w / 9) { rr(x + 5 + i * 9, y + h - 8, 4, 4, R.cream) { a, _ -> .85 - a * .1 }; p(x + 9 + i * 9, y + h - 7, R.cream[2]) }; r(x + w / 2, y, 1, 5, R.INK); ball(x + w / 2, y + 6, 2, R.honey, R.INK) }
        "school" -> { rr(x + 3, y + 3, w - 6, h - 8, G.bottle) { i, j -> .4 + hash(i, j) * .08 }; for (k in 0 until 4) line(x + 6, y + 6 + k * 3, x + 14 + k * 5, y + 6 + k * 3, R.cream[5], .7f); ball(x + w - 6, y + h - 6, 2, R.roofB, R.INK) }
        "market" -> { shelf(y + h - 3); for (i in 0 until w / 6) ball(x + 4 + i * 6, y + h - 6, 2, listOf(R.apple, R.honey, R.green, R.autumn)[i % 4], R.INK) }
        "florist" -> { shelf(y + h - 3); for (i in 0 until w / 6) { r(x + 4 + i * 6, y + h - 9, 1, 6, R.green[4]); ball(x + 4 + i * 6, y + h - 10, 1, listOf(R.jam, R.honey, R.lilac, R.cream)[i % 4], null) } }
        "library" -> { for (row in 0..1) { shelf(y + 7 + row * 7); for (i in 0 until w / 3) rr(x + 1 + i * 3, y + 2 + row * 7, 2, 5, listOf(R.red, G.navy, R.green, R.honey)[(i + row) % 4]) { _, _ -> .5 } } }
        else -> { shelf(y + h - 3); for (i in 0 until w / 8) rr(x + 3 + i * 8, y + h - 9, 5, 6, listOf(R.iron, R.wood, R.cream)[i % 3]) { _, _ -> .55 } }
    }
}

/** A neighbour's place seen from the pavement: facade, signboard, window, the neighbour in the door. */
class FacadeScene(val kind: String, val icon: IntArray, val fac: IntArray, val board: IntArray, val awning: IntArray, val look: Look, val m: Moment, val cat: Boolean) : Scene(128, 100) {
    override val key get() = listOf(kind, m.minute / 10, m.sky, m.month, m.day)
    private val keep = BooleanArray(w * h)

    override fun base(b: Buf) {
        b.skyBand(0, 0, w, 12, m, 0)
        b.rr(0, 8, w, 66, fac) { x, y -> val yy = y + 8; var v = .55 + (fbm(x * .22, yy * .22) - .5) * .16; if (yy >= 52 && (yy - 52) % 5 == 0) v -= .18; if (yy >= 52 && (x + ((yy - 52) / 5) * 6) % 12 == 0) v -= .12; v }
        b.rr(0, 8, w, 4, fac) { _, y -> .8 - if (y == 3) .3 else 0.0 }
        for (x in 0 until w step 3) b.p(x, 12, fac[1])
        b.rr(0, 47, w, 4, fac) { _, y -> .78 - if (y == 3) .35 else 0.0 }
        val lit = m.dark > .15f
        for (wx in listOf(6, 30, 84, 108)) {
            val wy = 17
            b.rr(wx - 1, wy - 3, 16, 2, fac) { _, y -> .85 - y * .3 }
            b.r(wx - 1, wy - 1, 16, 24, R.cream[4]); b.r(wx, wy, 14, 22, R.cream[2])
            val on = lit && hash(wx, m.day.toInt()) < .6
            b.rr(wx + 1, wy + 1, 12, 20, if (on) R.honey else R.glass) { x, y -> if (on) .55 + .3 * (1 - y / 20.0) else .25 + (if (x + y < 10) .35 else 0.0) + if ((x - y + 40) % 14 < 2) .2 else 0.0 }
            if (on) for (j in wy + 1 until wy + 21) for (i in wx + 1 until wx + 13) keep[j * w + i] = true
            b.r(wx + 6, wy + 1, 2, 20, R.cream[3]); b.r(wx + 1, wy + 8, 12, 1, R.cream[3])
            b.rr(wx + 1, wy + 1, 2, 20, R.cream) { _, _ -> .85 }; b.rr(wx + 11, wy + 1, 2, 20, R.cream) { _, _ -> .8 }
            b.rr(wx - 2, wy + 22, 18, 2, R.stone) { _, y -> .75 - y * .35 }
            for (i in 0 until 18) b.mul(wx - 2 + i, wy + 24, G.SHADOW, .4f)
            if (m.month in 5..9 && hash(wx, 5) < .6) for (i in 0 until 4) { b.p(wx + 2 + i * 3, wy + 20, R.green[5]); b.p(wx + 2 + i * 3, wy + 19, listOf(R.apple[4], R.honey[4], R.cream[5])[i % 3]) }
        }
        b.rr(30, 36, 68, 10, board) { _, y -> .6 - y * .04 }
        b.r(30, 36, 68, 1, R.gold[4]); b.r(30, 45, 68, 1, R.gold[1]); b.r(30, 36, 1, 10, R.gold[3]); b.r(97, 36, 1, 10, R.gold[2])
        for (j in 0 until 5) for (i in 0 until 5) if (icon[j * 5 + i] != 0) b.rr(58 + i * 2, 37 + j * 2, 2, 2, R.gold) { _, _ -> .85 - j * .08 }
        for (sx in listOf(34, 92)) { b.r(sx, 33, 1, 3, R.iron[2]); b.p(sx, 32, R.iron[4]) }
        fun win(x: Int, y: Int, ww: Int, hh: Int) {
            b.r(x - 2, y - 2, ww + 4, hh + 4, R.wood[1]); b.rr(x - 1, y - 1, ww + 2, hh + 2, R.wood) { _, _ -> .55 }
            b.rr(x, y, ww, hh, R.honey) { i, j -> (if (lit) .55 else .28) + (1 - j.toDouble() / hh) * .15 + if (i + j < 12) .12 else 0.0 }
            b.goods(kind, x, y, ww, hh)
            if (lit) for (j in y until y + hh) for (i in x until x + ww) keep[j * w + i] = true
            for (k in 0 until ww step 2) b.p(x + k, y + (k * 3) % hh, R.glass[5], .15f)
            b.rr(x - 3, y + hh + 2, ww + 6, 2, R.stone) { _, _ -> .7 }
        }
        win(10, 54, 42, 16); win(84, 54, 34, 16)
        b.r(58, 51, 20, 23, R.wood[1]); b.rr(60, 53, 16, 21, R.honey) { _, j -> if (lit) .5 - j * .01 else .25 - j * .008 }
        if (lit) for (j in 53 until 74) for (i in 60 until 76) keep[j * w + i] = true
        b.rr(76, 53, 3, 21, R.wood) { _, _ -> .6 }; b.p(77, 64, R.brass[4])
        for (x in 8 until 55) {
            val st = ((x - 8) shr 2) and 1
            for (y in 47 until 53) b.p(x, y, rp(if (st == 1) R.cream else awning, .7 - (y - 47) * .06, x, y))
            if ((x - 8) % 4 < 2) b.p(x, 53, if (st == 1) R.cream[3] else awning[2])
            b.mul(x, 54, G.SHADOW, .4f)
        }
        b.rr(0, 74, w, 20, G.granite) { x, y -> var v = .58 + (fbm(x * .2, y * .3) - .5) * .14; if (y % 6 == 0 || (x + (y / 6) * 7) % 14 == 0) v -= .14; v }
        b.rr(0, 94, w, 2, G.granite) { _, _ -> .8 }; b.rr(0, 96, w, 4, G.asph) { _, _ -> .45 }
        for (x in 0 until w) b.mul(x, 74, G.SHADOW, .45f)
        if (m.winter) for (x in 0 until w) for (y in 74 until 94) if (fbm(x * .1, y * .2) > .45) b.p(x, y, rp(R.snow, .5 + fbm(x * .3, y * .3) * .4, x, y))
        if (m.sky.rain) for ((px, pw) in listOf(18 to 18, 90 to 24)) for (i in 0 until pw) for (j in 0 until 3) if (kotlin.math.abs(i - pw / 2.0) / (pw / 2.0) + j / 3.0 < 1) b.p(px + i, 84 + j, R.glass[4], .45f)
        if (m.month in 9..10) for (i in 0 until 14) b.p((hash(i, m.day.toInt()) * w).toInt(), 76 + (hash(m.day.toInt(), i) * 16).toInt(), rp(R.autumn, .3 + hash(i, 2) * .6, i, 0))
        b.rr(52, 68, 5, 6, R.brick) { i, _ -> .7 - i * .12 }
        for (k in 0 until 5) b.p(52 + k, 66 - k % 2, if (m.month in 4..9) R.green[5] else G.oak[3])
        if (cat) { val cx = 104; val cy = 72; b.rr(cx, cy - 3, 6, 3, R.ginger) { _, j -> .75 - j * .12 }; b.p(cx, cy - 4, R.ginger[4]); b.p(cx + 2, cy - 4, R.ginger[4]); b.p(cx + 1, cy - 3, R.INK); b.p(cx + 6, cy - 1, R.ginger[2]); b.p(cx + 7, cy - 2, R.ginger[3]) }
        b.r(4, 40, 1, 6, R.iron[2]); b.r(2, 39, 5, 1, R.iron[3]); b.rr(3, 40, 3, 3, R.glass) { _, _ -> .8 }
        b.person(68, 74, look)
    }

    override fun anim(b: Buf, t: Int) {
        b.precipitation(m, t)
        b.night(m, keep)
        if (m.dark > .15f) b.lamplight(4, 42, 12, .6f)
    }
}

/** The embankment with a rod, the far bank, the float. [state]: idle, wait, bite, done. */
class FishScene(val m: Moment, val big: Boolean, var state: String = "idle") : Scene(160, 84) {
    override val key get() = listOf(m.minute / 10, m.sky, m.month, big)

    override fun base(b: Buf) {
        b.skyBand(0, 0, w, 32, m, 0)
        if (big) {
            for (x in 0 until w) { val top = 25 + Math.round(sin(x / 9.0)).toInt(); for (y in top until 34) b.p(x, y, rp(G.fort, .45 + (if (y == top) .3 else 0.0) - (if (x % 12 < 1) .1 else 0.0) - if (y > 31) .1 else 0.0, x, y)) }
            for (x in 60 until 100) for (y in 19 until 26) b.p(x, y, rp(G.salmon, .6 + (if (y == 19) .2 else 0.0) - if (x % 5 == 0) .2 else 0.0, x, y))
            for (x in 58 until 102) b.p(x, 18, G.rust[3])
        } else for (x in 0 until w) {
            val top = 20 + (fbm(x * .15, 1.0) * 8).toInt()
            val ramp = if (m.month in 9..10) R.autumn else if (m.month in 4..8) R.green else G.oak
            for (y in top until 34) b.p(x, y, rp(ramp, .3 + (y - top) * -.02 + fbm(x * .3, y * .3) * .4, x, y))
        }
        b.rr(0, 70, w, 14, G.granite) { x, y -> var v = .55 + (fbm(x * .2, y * .2) - .5) * .15; if (y == 0) v += .25; if (x % 32 == 0) v -= .2; v }
        for (x in 0 until w) b.mul(x, 69, G.SHADOW, .5f)
        for (i in 0..24) b.p(8 + i * 1.6, 82 - i * 1.7, if (i < 6) R.wood[1] else R.wood[4])
    }

    override fun anim(b: Buf, t: Int) {
        val ice = m.winter
        for (y in 34 until 70) for (x in 0 until w) {
            val n = fbm(x * .05 + t * .03, y * .25)
            val v = .32 + n * .25 - (y - 34) / 36.0 * .1 + if ((x + (n * 20).toInt()) % 13 == 0 && y % 3 == 0) .15 else 0.0
            b.p(x, y, if (ice) rp(R.ice, .5 + n * .3, x, y) else rp(G.water, v, x, y))
        }
        if (ice) { b.disc(90, 52, 4, G.water[2]); b.disc(90, 52, 3, G.water[1]) }
        val bite = state == "bite"
        val by = 50 + if (bite) 3 else Math.round(sin(t / 3.0)).toInt()
        if (state != "idle") {
            for (i in 0..40) { val k = i / 40.0; b.p(46 + k * 44, 41 + (by - 3 - 41) * k + sin(k * PI) * 5, R.cream[5], .8f) }
            if (bite) { for (i in -6..6) b.p(90 + i, by + 2, G.water[6], .7f); for (i in -9..9) b.p(90 + i, by + 4, G.water[5], .5f) }
            b.r(89, by - 3, 3, 2, R.cream[5]); b.r(89, by - 1, 3, 2, R.red[3]); b.r(88, by + 1, 5, 1, G.water[5])
        }
        b.precipitation(m, t)
        b.night(m)
    }
}

/** Meeting a neighbour out for a walk: a park path between trees, houses beyond, a lamp, the season on the grass. */
class StrollScene(val look: Look, val m: Moment, val seed: Int) : Scene(128, 100) {
    override val key get() = listOf(seed, m.minute / 10, m.sky, m.month, m.day)
    private val keep = BooleanArray(w * h)

    override fun base(b: Buf) {
        b.skyBand(0, 0, w, 46, m, 0)
        // houses beyond the park
        var x = -4
        var k = 0
        while (x < w) {
            val bw = 18 + (hash(seed + k, 3) * 16).toInt(); val bh = 14 + (hash(seed + k, 7) * 18).toInt()
            val fac = G.FAC[(hash(seed + k, 11) * G.FAC.size).toInt()]
            b.rr(x, 46 - bh, bw, bh, fac) { _, j -> .5 - j * .004 }
            for (wy in 46 - bh + 3 until 44 step 5) for (wx in x + 2 until x + bw - 2 step 4) b.p(wx, wy, if (m.dark > .15f && hash(wx, wy + seed) < .5) G.WARM else fac[1])
            x += bw + 1; k++
        }
        // the lawn, the path, the season
        val winter = m.winter
        b.rr(0, 46, w, 54, if (winter) R.cream else R.green) { i, j -> (if (winter) .8 else .5) + (fbm(i * .2, j * .3) - .5) * .2 + j * .003 }
        for (j in 0 until 54) {
            val half = 10 + j * 3 / 4; val cx = 64 + ((j - 30) * (j - 30)) / 60
            for (i in cx - half..cx + half) b.p(i, 46 + j, rp(G.dirt, .55 + (fbm(i * .3, j * .3) - .5) * .2 + if (i == cx - half || i == cx + half) -.2 else 0.0, i, j))
        }
        if (m.month in 9..11) for (i in 0 until 40) b.p((hash(i, seed) * w).toInt(), 50 + (hash(seed, i) * 48).toInt(), rp(R.autumn, .3 + hash(i, 2) * .6, i, 0))
        // trees along the path
        for ((tx, ty) in listOf(14 to 60, 112 to 62, 30 to 50, 98 to 50)) {
            b.r(tx, ty - 4, 2, 12, R.wood[2]); b.r(tx + 2, ty - 4, 1, 12, R.wood[1])
            val crown = if (winter || m.month in listOf(11, 3)) null else if (m.month in 9..10) R.autumn else R.green
            if (crown != null) b.ball(tx + 1, ty - 12, 9, crown, R.INK)
            else for (k2 in 0 until 6) b.line(tx + 1, ty - 4, tx - 6 + k2 * 3, ty - 16 - (k2 % 2) * 3, R.wood[2])
        }
        // a lamp post and a bench
        b.r(90, 54, 1, 34, R.iron[1]); b.r(88, 52, 5, 3, R.iron[2]); b.p(90, 55, if (m.dark > .15f) R.honey[5] else R.glass[3])
        b.r(16, 80, 18, 2, R.wood[4]); b.r(16, 76, 18, 2, R.wood[3]); b.r(17, 82, 1, 4, R.iron[1]); b.r(32, 82, 1, 4, R.iron[1])
        b.person(64, 96, look)
        b.night(m, keep)
        if (m.dark > .15f) b.lamplight(90, 56, 16, .6f)
    }

    override fun anim(b: Buf, t: Int) { b.precipitation(m, t) }
}
