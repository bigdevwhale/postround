package app.postround.ui.art

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/** A 3×5 font for house-number plates: digits, the slash and the letters house numbers use. */
private val PLATE = mapOf(
    '0' to "111101101101111", '1' to "010110010010111", '2' to "111001111100111", '3' to "111001111001111", '4' to "101101111001001",
    '5' to "111100111001111", '6' to "111100111101111", '7' to "111001001001001", '8' to "111101111101111", '9' to "111101111001111",
    '/' to "001001010100100", '-' to "000000111000000", 'А' to "010101111101101", 'Б' to "111100111101111", 'В' to "110101110101110",
    'Г' to "111100100100100", 'Д' to "01100110011011111001", 'Е' to "111100110100111", 'К' to "101110100110101", 'Л' to "011101101101101",
    'П' to "111101101101101", 'Р' to "111101111100100", 'С' to "111100100100111", 'Т' to "111010010010010",
    'A' to "010101111101101", 'B' to "110101110101110", 'C' to "111100100100111", 'D' to "110101101101110", 'E' to "111100110100111",
    'K' to "101110100110101", 'L' to "100100100100111", 'P' to "111101111100100", 'T' to "111010010010010",
)

fun plateWidth(s: String) = s.uppercase().sumOf { PLATE[it]?.let { g -> g.length / 5 + 1 } ?: 2 } - 1

fun Buf.plate(s: String, x: Int, y: Int, c: Int) {
    var xx = x
    for (ch in s.uppercase()) {
        val g = PLATE[ch]
        if (g == null) { xx += 2; continue }
        // most glyphs are 3 wide; Д needs 4 so it doesn't read as А
        val gw = g.length / 5
        for (j in 0 until 5) for (i in 0 until gw) if (g[j * gw + i] == '1') p(xx + i, y + j, c)
        xx += gw + 1
    }
}

/**
 * The house seen from the pavement, at the door the postman stands by: a city facade in the house's colour,
 * the number plate, the stairwell door with its intercom (or a shop window, the school's porch, the post office),
 * the resident in the doorway, the weather and the hour.
 */
class DoorScene(val kind: String, val fac: IntArray, val number: String, val look: Look?, val m: Moment, val seed: Int) : Scene(128, 100) {
    override val key get() = listOf(kind, number, seed, m.minute / 10, m.sky, m.month, m.day, look)
    private val keep = BooleanArray(w * h)

    override fun base(b: Buf) {
        b.skyBand(0, 0, w, 12, m, 0)
        b.rr(0, 8, w, 66, fac) { x, y -> val yy = y + 8; var v = .55 + (fbm(x * .22, yy * .22) - .5) * .16; if (yy >= 52 && (yy - 52) % 5 == 0) v -= .18; if (yy >= 52 && (x + ((yy - 52) / 5) * 6) % 12 == 0) v -= .12; v }
        b.rr(0, 8, w, 4, fac) { _, y -> .8 - if (y == 3) .3 else 0.0 }
        for (x in 0 until w step 3) b.p(x, 12, fac[1])
        b.rr(0, 47, w, 4, fac) { _, y -> .78 - if (y == 3) .35 else 0.0 }
        val lit = m.dark > .15f
        fun window(wx: Int, wy: Int, ww: Int, hh: Int) {
            b.rr(wx - 1, wy - 3, ww + 2, 2, fac) { _, y -> .85 - y * .3 }
            b.r(wx - 1, wy - 1, ww + 2, hh + 2, R.cream[4]); b.r(wx, wy, ww, hh, R.cream[2])
            val on = lit && hash(wx + seed, m.day.toInt()) < .6
            b.rr(wx + 1, wy + 1, ww - 2, hh - 2, if (on) R.honey else R.glass) { x, y -> if (on) .55 + .3 * (1 - y / hh.toDouble()) else .25 + (if (x + y < 10) .35 else 0.0) + if ((x - y + 40) % 14 < 2) .2 else 0.0 }
            if (on) for (j in wy + 1 until wy + hh - 1) for (i in wx + 1 until wx + ww - 1) keep[j * w + i] = true
            b.r(wx + ww / 2 - 1, wy + 1, 2, hh - 2, R.cream[3]); b.r(wx + 1, wy + (hh * .38).toInt(), ww - 2, 1, R.cream[3])
            b.rr(wx - 2, wy + hh, ww + 4, 2, R.stone) { _, y -> .75 - y * .35 }
            for (i in 0 until ww + 4) b.mul(wx - 2 + i, wy + hh + 2, G.SHADOW, .4f)
            if (m.month in 5..9 && hash(wx, seed) < .4) for (i in 0 until ww / 3 - 1) { b.p(wx + 2 + i * 3, wy + hh - 2, R.green[5]); b.p(wx + 2 + i * 3, wy + hh - 3, listOf(R.apple[4], R.honey[4], R.cream[5])[i % 3]) }
        }
        for (wx in listOf(6, 30, 84, 108)) window(wx, 17, 14, 22)
        if (kind == "shop" || kind == "post") {
            val board = if (kind == "post") R.blue else listOf(G.plum, G.bottle, G.navy, R.wood)[(hash(seed, 2) * 4).toInt()]
            val awn = if (kind == "post") R.blue else listOf(R.red, R.enamel, R.honey)[(hash(seed, 3) * 3).toInt()]
            b.rr(30, 36, 68, 10, board) { _, y -> .6 - y * .04 }
            b.r(30, 36, 68, 1, R.gold[4]); b.r(30, 45, 68, 1, R.gold[1])
            val icon = if (kind == "post") Icons.post else listOf(Icons.bakery, Icons.cafe, Icons.market, Icons.florist)[(hash(seed, 4) * 4).toInt()]
            for (j in 0 until 5) for (i in 0 until 5) if (icon[j * 5 + i] != 0) b.rr(58 + i * 2, 37 + j * 2, 2, 2, R.gold) { _, _ -> .85 - j * .08 }
            fun win(x: Int, y: Int, ww: Int, hh: Int) {
                b.r(x - 2, y - 2, ww + 4, hh + 4, R.wood[1]); b.rr(x - 1, y - 1, ww + 2, hh + 2, R.wood) { _, _ -> .55 }
                b.rr(x, y, ww, hh, R.honey) { i, j -> (if (lit) .55 else .28) + (1 - j.toDouble() / hh) * .15 + if (i + j < 12) .12 else 0.0 }
                if (kind == "post") { for (k in 0 until ww / 9) { b.r(x + 3 + k * 9, y + 4, 7, 5, R.INK); b.rr(x + 4 + k * 9, y + 5, 5, 3, R.cream) { _, _ -> .85 } }; b.r(x, y + hh - 4, ww, 1, R.wood[4]) }
                else b.goods(listOf("bakery", "cafe", "market", "florist")[(hash(seed, 4) * 4).toInt()], x, y, ww, hh)
                if (lit) for (j in y until y + hh) for (i in x until x + ww) keep[j * w + i] = true
                b.rr(x - 3, y + hh + 2, ww + 6, 2, R.stone) { _, _ -> .7 }
            }
            win(10, 54, 42, 16); win(84, 54, 34, 16)
            b.r(58, 51, 20, 23, R.wood[1]); b.rr(60, 53, 16, 21, R.honey) { _, j -> if (lit) .5 - j * .01 else .25 - j * .008 }
            if (lit) for (j in 53 until 74) for (i in 60 until 76) keep[j * w + i] = true
            for (x in 8 until 55) { val st = ((x - 8) shr 2) and 1; for (y in 47 until 53) b.p(x, y, rp(if (st == 1) R.cream else awn, .7 - (y - 47) * .06, x, y)); b.mul(x, 54, G.SHADOW, .4f) }
            if (kind == "post") {
                // the blue post box on its pole
                b.rr(100, 32, 10, 12, R.blue) { i, _ -> .75 - i * .04 }; b.r(101, 35, 8, 1, R.INK); b.r(100, 44, 10, 1, R.INK); b.rr(104, 45, 2, 29, R.iron) { _, _ -> .4 }
                for (j in 0 until 5) for (i in 0 until 5) if (Icons.post[j * 5 + i] != 0) b.p(102 + i, 38 + j, R.gold[4])
            }
        } else {
            for (wx in listOf(10, 98)) window(wx, 55, 18, 13)
            val door = if (kind == "inst") R.wood else listOf(G.bottle, R.wood, G.navy, G.plum)[(hash(seed, 6) * 4).toInt()]
            b.rr(50, 49, 28, 25, R.stone) { i, j -> .35 + (if (i == 0 || i == 27) .25 else 0.0) + if (j == 0) .3 else 0.0 }
            b.rr(53, 53, 22, 21, door) { i, j -> .55 - i * .012 + if (j % 7 == 0) -.12 else 0.0 }
            b.r(64, 53, 1, 21, R.INK); b.p(62, 64, R.brass[4]); b.p(66, 64, R.brass[4])
            b.rr(55, 54, 7, 8, R.glass) { _, _ -> .4 }; b.rr(66, 54, 7, 8, R.glass) { _, _ -> .35 }
            b.rr(47, 45, 34, 3, R.iron) { i, _ -> .6 - i * .01 }; b.r(48, 48, 1, 3, R.iron[1]); b.r(79, 48, 1, 3, R.iron[1])
            // intercom
            b.r(80, 58, 6, 9, R.INK); b.rr(81, 59, 4, 7, R.iron) { _, _ -> .6 }
            for (j in 0 until 3) for (i in 0 until 2) b.p(82 + i, 60 + j * 2, R.cream[4])
            b.p(83, 65, rgb(0x50e080))
            if (kind == "inst") { b.rr(52, 41, 24, 4, R.blue) { _, y -> .6 - y * .1 }; for (i in 0 until 10) b.p(55 + i * 2, 43, R.cream[5]) }
            else if (hash(seed, 7) < .5) { b.rr(38, 64, 8, 10, R.wood) { i, _ -> .6 - i * .03 }; for (k in 0 until 4) b.p(39 + k * 2, 63 - k % 2, if (m.month in 4..9) R.green[5] else G.oak[3]) }
            b.r(64, 41, 1, 4, R.iron[2]); b.rr(62, 38, 5, 3, R.glass) { _, _ -> .85 }
        }
        // the house number plate: blue enamel, white digits
        val pw = plateWidth(number) + 4
        val px = 86 - pw / 2
        b.r(px, 36, pw, 9, R.INK); b.rr(px + 1, 37, pw - 2, 7, R.blue) { _, _ -> .7 }
        b.plate(number, px + 2, 38, R.cream[5])
        // pavement
        b.rr(0, 74, w, 20, G.granite) { x, y -> var v = .58 + (fbm(x * .2, y * .3) - .5) * .14; if (y % 6 == 0 || (x + (y / 6) * 7) % 14 == 0) v -= .14; v }
        b.rr(0, 94, w, 2, G.granite) { _, _ -> .8 }; b.rr(0, 96, w, 4, G.asph) { _, _ -> .45 }
        for (x in 0 until w) b.mul(x, 74, G.SHADOW, .45f)
        if (m.winter) for (x in 0 until w) for (y in 74 until 94) if (fbm(x * .1, y * .2) > .45) b.p(x, y, rp(R.snow, .5 + fbm(x * .3, y * .3) * .4, x, y))
        if (m.sky.rain) for ((px2, pw2) in listOf(18 to 18, 90 to 24)) for (i in 0 until pw2) for (j in 0 until 3) if (abs(i - pw2 / 2.0) / (pw2 / 2.0) + j / 3.0 < 1) b.p(px2 + i, 84 + j, R.glass[4], .45f)
        if (m.month in 9..10) for (i in 0 until 14) b.p((hash(i, m.day.toInt() + seed) * w).toInt(), 76 + (hash(m.day.toInt(), i + seed) * 16).toInt(), rp(R.autumn, .3 + hash(i, 2) * .6, i, 0))
        if (hash(m.day.toInt(), seed) < .35) { val cx = 104; val cy = 74; b.rr(cx, cy - 3, 6, 3, R.ginger) { _, j -> .75 - j * .12 }; b.p(cx, cy - 4, R.ginger[4]); b.p(cx + 2, cy - 4, R.ginger[4]); b.p(cx + 1, cy - 3, R.INK); b.p(cx + 6, cy - 1, R.ginger[2]); b.p(cx + 7, cy - 2, R.ginger[3]) }
        b.r(4, 40, 1, 6, R.iron[2]); b.r(2, 39, 5, 1, R.iron[3]); b.rr(3, 40, 3, 3, R.glass) { _, _ -> .8 }
        if (look != null) b.person(if (kind == "shop" || kind == "post") 68 else 64, 77, look)
    }

    override fun anim(b: Buf, t: Int) {
        b.precipitation(m, t)
        b.night(m, keep)
        if (m.dark > .15f) b.lamplight(4, 42, 12, .6f)
    }
}

/** The postman on the map: navy cap with a brass badge, a blue coat, a red scarf, the leather bag. Feet at (x, y). */
fun Buf.postman(x: Int, y: Int, step: Int, left: Boolean, rain: Boolean = false) {
    for (i in -3..3) mul(x + i, y, G.SHADOW, .35f)
    val rows = listOf("..kkkk..", ".krrrrk.", "krrRrrrk", "kkffffkk", ".kfefek.", ".kffffk.", ".ksssskb", "kccsscbb", "kcccccbk", "kcccccbk", ".kcccck.",
        if (step == 1) ".kpk.kpk" else ".kpkkpk.", if (step == 1) "..kk.kk." else ".kk..kk.")
    val pal = mapOf('k' to R.INK, 'r' to G.navy[3], 'R' to R.gold[4], 'f' to R.skin[4], 'e' to R.INK, 's' to R.red[4], 'c' to R.blue[3], 'b' to R.leather[4], 'p' to R.iron[2])
    rows.forEachIndexed { j, row ->
        for (i in 0 until 8) {
            val ch = row[if (left) 7 - i else i]
            if (ch == '.') continue
            p(x - 4 + i, y - 13 + j, when { ch == 'c' && i < 3 -> R.blue[4]; ch == 'b' && j == 6 -> R.leather[2]; else -> pal.getValue(ch) })
        }
    }
    if (rain) { for (i in -5..5) p(x + i, y - 14 + if (abs(i) > 3) 1 else 0, G.bottle[3]); p(x, y - 15, G.bottle[4]) }
}

/** The 12×10 icons of the kinds of mail. */
object MailIcons {
    private val cache = HashMap<String, Buf>()
    fun of(kind: String): Buf = cache.getOrPut(kind) {
        val b = Buf(12, 10)
        val x = 6; val y = 5
        when (kind) {
            "telegram" -> { b.r(x - 5, y - 3, 11, 7, R.INK); b.r(x - 4, y - 2, 9, 5, R.honey[4]); b.r(x - 4, y, 9, 1, R.red[4]); b.r(x - 4, y - 2, 9, 1, R.honey[5]) }
            "parcel", "out", "pickup" -> {
                b.r(x - 5, y - 4, 11, 9, R.INK); b.rr(x - 4, y - 3, 9, 7, R.wood) { _, j -> .75 - j * .06 }
                b.r(x, y - 3, 1, 7, R.cream[4]); b.r(x - 4, y, 9, 1, R.cream[4])
                if (kind == "out") { b.r(x + 2, y - 3, 3, 2, R.blue[4]) }
                if (kind == "pickup") { b.p(x + 3, y - 3, R.red[4]); b.p(x + 2, y - 3, R.red[4]) }
            }
            "paper" -> { b.r(x - 5, y - 3, 11, 7, R.INK); b.rr(x - 4, y - 2, 9, 5, R.cream) { _, j -> .78 - j * .1 }; b.r(x - 1, y - 2, 1, 5, R.red[3]); b.r(x - 3, y - 1, 2, 1, R.iron[3]) }
            "mag" -> { b.r(x - 4, y - 4, 9, 9, R.INK); b.rr(x - 3, y - 3, 7, 7, R.roofB) { _, j -> .7 - j * .05 }; b.r(x - 2, y - 2, 5, 2, R.cream[5]); b.r(x - 2, y + 1, 5, 1, R.cream[3]) }
            "riddle" -> { envelope(b, x, y, R.cream); b.r(x - 2, y - 1, 1, 1, R.iron[2]); for (k in 0 until 3) b.p(x + 1 + k, y + 2, R.iron[2]); b.p(x + 3, y - 2, R.red[4]); b.p(x + 3, y - 1, R.red[4]); b.p(x + 3, y + 1, R.red[4]) }
            "chess" -> { envelope(b, x, y, R.cream); for (j in 0 until 2) for (i in 0 until 2) b.p(x + 1 + i, y - 2 + j, if ((i + j) % 2 == 0) R.INK else R.cream[5]) }
            "card" -> { b.r(x - 5, y - 4, 11, 8, R.INK); b.rr(x - 4, y - 3, 9, 6, R.cream) { _, _ -> .9 }; b.r(x + 1, y - 3, 3, 3, R.red[4]); b.p(x + 2, y - 2, R.cream[5]); for (k in 0 until 3) b.r(x - 3, y - 1 + k, 4, 1, R.iron[3 + k % 2]) }
            "bill" -> { envelope(b, x, y, R.stone); b.r(x + 2, y - 2, 2, 2, R.blue[4]) }
            else -> { envelope(b, x, y, R.cream); b.r(x + 2, y - 2, 2, 2, R.red[4]) }
        }
        b
    }

    private fun envelope(b: Buf, x: Int, y: Int, ramp: IntArray) {
        b.r(x - 5, y - 4, 11, 8, R.INK); b.r(x - 4, y - 3, 9, 6, ramp[ramp.size - 2])
        for (i in 0 until 5) { b.p(x - 4 + i, y - 3 + i, ramp[1]); b.p(x + 4 - i, y - 3 + i, ramp[1]) }
    }
}

/** Facade colours by index: the map's G.FAC. */
fun facadeOf(i: Int): IntArray = G.FAC[i.coerceIn(0, G.FAC.size - 1)]

private val FRAMES by lazy { listOf(R.red, R.blue, R.enamel, G.plum, G.ochre, G.bottle) }

/**
 * A street's stamp, 30×38: perforated paper, a coloured frame, a little picture of what the street has
 * (water and a bridge, a tram, a park, houses, roofs with a cat, a lamp in the rain, an arch to a courtyard)
 * and a value. Not yet collected: greyed out.
 */
fun stampOf(street: String, motif: String, got: Boolean, month: Int, bg: Int = rgb(0xF2E2BB)): Buf {
    val w = 30; val h = 38
    val b = Buf(w, h)
    var hs = 7L; for (ch in street) hs = (hs * 31 + ch.code) and 0xffffffffL
    val fr = FRAMES[(hs % FRAMES.size).toInt()]
    b.r(0, 0, w, h, bg); b.r(1, 1, w - 2, h - 2, R.cream[5])
    for (x in 2 until w - 1 step 3) { b.p(x, 1, bg); b.p(x, h - 2, bg) }
    for (y in 2 until h - 1 step 3) { b.p(1, y, bg); b.p(w - 2, y, bg) }
    b.r(3, 3, w - 6, h - 6, fr[3])
    val x0 = 4; val y0 = 4; val aw = w - 8; val ah = h - 16
    val dusk = hs % 3 == 0L
    for (y in 0 until ah) for (x in 0 until aw) b.p(x0 + x, y0 + y, rp(if (dusk) R.dusk else R.day, (if (dusk) .35 else .3) + y.toDouble() / ah * .6, x0 + x, y0 + y))
    if (dusk) b.ball(x0 + aw - 6, y0 + 5, 2, R.gold) else for (i in 0 until 6) b.p(x0 + 3 + i, y0 + 4 + i % 2, R.cream[5])
    fun clip(x: Int, y: Int, c: Int) { if (x in x0 until x0 + aw && y in y0 until y0 + ah) b.p(x, y, c) }
    when (motif) {
        "water" -> {
            for (y in ah - 9 until ah) for (x in 0 until aw) b.p(x0 + x, y0 + y, rp(G.water, .3 + (if ((x + y * 2) % 5 == 0) .35 else 0.0) - (y - ah + 9) * .02, x0 + x, y0 + y))
            b.rr(x0, y0 + ah - 11, aw, 2, G.granite) { _, _ -> .7 }
            for (x in 0 until aw) { val a = abs(x - aw / 2.0) / (aw / 2.0); if (a < .8) clip(x0 + x, y0 + ah - 15 + (a * a * 4).toInt(), R.iron[2]) }
            b.rr(x0 + 4, y0 + ah - 6, 7, 2, R.cream) { _, _ -> .8 }; b.r(x0 + 6, y0 + ah - 8, 2, 2, R.red[3])
        }
        "tram" -> {
            b.rr(x0, y0 + ah - 5, aw, 5, G.asph) { _, _ -> .45 }; b.r(x0, y0 + ah - 3, aw, 1, R.iron[4]); b.line(x0, y0 + 5, x0 + aw - 1, y0 + 3, R.INK, .6f)
            b.rr(x0 + 3, y0 + ah - 14, 16, 9, R.red) { _, j -> .7 - j * .05 }; b.r(x0 + 3, y0 + ah - 14, 16, 2, R.cream[4])
            for (k in 0 until 4) b.rr(x0 + 5 + k * 3, y0 + ah - 11, 2, 3, R.glass) { _, _ -> .75 }
            b.r(x0 + 10, y0 + ah - 17, 1, 3, R.INK)
        }
        "park" -> {
            b.rr(x0, y0 + ah - 7, aw, 7, if (month in 9..11) R.grass else R.grassSummer) { _, j -> .4 + j * .05 }
            for ((tx, r, rm) in listOf(Triple(5, 4, R.autumn), Triple(13, 5, R.green), Triple(19, 3, G.lime))) { b.r(x0 + tx, y0 + ah - 7, 1, 3, R.wood[1]); b.ball(x0 + tx, y0 + ah - 10 - r + 2, r, rm) }
            b.r(x0 + 8, y0 + ah - 3, 7, 1, R.wood[4])
        }
        "roofs" -> {
            for (k in 0 until 3) {
                val rx = x0 - 2 + k * 9; val ry = y0 + ah - 14 + (k % 2) * 3; val rm = if (k == 1) G.rust else G.tin
                for (j in 0 until 16) for (i in 0 until 11) { val top = abs(i - 5) * .7; if (j < top) continue; clip(rx + i, ry + j, rp(rm, .65 - (if (i > 5) .25 else 0.0) - (if (j % 3 == 0) .08 else 0.0), rx + i, ry + j)) }
                if (rx + 8 < x0 + aw) b.rr(rx + 7, ry - 1, 2, 4, R.brick) { _, _ -> .6 }
            }
            b.rr(x0 + 12, y0 + ah - 17, 4, 3, R.ginger) { _, j -> .75 - j * .15 }; b.p(x0 + 12, y0 + ah - 18, R.ginger[4]); b.p(x0 + 14, y0 + ah - 18, R.ginger[4]); b.p(x0 + 16, y0 + ah - 15, R.ginger[2])
        }
        "lamp" -> {
            for (y in 0 until ah) for (x in 0 until aw) b.p(x0 + x, y0 + y, rp(R.night, .15 + y.toDouble() / ah * .3, x0 + x, y0 + y))
            b.rr(x0, y0 + ah - 5, aw, 5, R.stone) { i, j -> .35 + ((i + j) and 1) * .08 }
            val lx = x0 + aw / 2
            b.r(lx, y0 + 7, 1, ah - 11, R.iron[3]); b.r(lx - 1, y0 + ah - 6, 3, 1, R.iron[2]); b.r(lx - 2, y0 + 5, 5, 1, R.iron[3]); b.rr(lx - 1, y0 + 6, 3, 3, R.honey) { _, _ -> .9 }
            for (j in -7..7) for (i in -7..7) { val d = hypot(i.toDouble(), j.toDouble()) / 7; if (d < 1) b.scr(lx + i, y0 + 7 + j, G.WARM, ((1 - d) * (1 - d) * .7).toFloat()) }
            for (i in 0 until 10) b.p(x0 + (hash(i, hs.toInt()) * aw).toInt(), y0 + (hash(hs.toInt(), i) * (ah - 6)).toInt(), R.glass[5], .6f)
        }
        "arch" -> {
            b.rr(x0, y0 + 3, aw, ah - 3, G.FAC[(hs % G.FAC.size).toInt()]) { i, j -> .55 + (fbm(i * .3, j * .3) - .5) * .2 }
            val ax = x0 + 5; val aww = aw - 10
            for (j in 0 until ah - 7) for (i in 0 until aww) {
                val top = j < 4 && abs(i - aww / 2.0 + .5) > sqrt(maxOf(0.0, (aww / 2.0) * (aww / 2.0) - (4 - j) * (4 - j) * 2.2))
                if (!top) b.p(ax + i, y0 + 7 + j, rp(R.stone, .18 + j.toDouble() / ah * .2, ax + i, y0 + 7 + j))
            }
            b.rr(ax + 3, y0 + ah - 6, aww - 6, 6, R.stone) { _, _ -> .55 }; b.ball(ax + aww / 2, y0 + ah - 9, 3, if (month in 9..11) R.autumn else R.green); b.r(ax + aww / 2, y0 + ah - 6, 1, 3, R.wood[1])
        }
        else -> {
            var x = 0; var k = 0
            while (x < aw) {
                val bw = min(aw - x, 6 + (hash(hs.toInt(), k) * 4).toInt()); val bh = 10 + (hash(k, hs.toInt()) * 9).toInt(); val fa = G.FAC[((hs + k) % G.FAC.size).toInt()]
                b.rr(x0 + x, y0 + ah - bh, bw, bh, fa) { i, _ -> .6 - i * .03 }; b.r(x0 + x, y0 + ah - bh, bw, 1, G.tin[2])
                var j = y0 + ah - bh + 3
                while (j < y0 + ah - 2) { var i = x0 + x + 1; while (i < x0 + x + bw - 1) { b.p(i, j, R.glass[1]); i += 2 }; j += 3 }
                x += bw; k++
            }
        }
    }
    b.r(x0, y0 + ah, aw, 1, fr[1]); b.rr(x0, y0 + ah + 1, aw, h - 8 - ah - y0 + 2, fr) { _, _ -> .25 }
    b.digits("5", x0 + 1, y0 + ah + 3, R.cream[5])
    for (i in 0 until 3) b.r(x0 + 7 + i * 5, y0 + ah + 5, 4, 1, R.cream[3])
    if (!got) for (i in b.d.indices) { val c = b.d[i]; val l = ((c shr 16 and 255) * .3 + (c shr 8 and 255) * .55 + (c and 255) * .15).toInt(); b.d[i] = mix(rgb(l.toLong() * 0x010101L), bg, .55f) }
    return b
}

/** A chess board after [moves] (coordinates like "e2e4"), the last move lit. 60×60. */
fun chessBoard(moves: List<String>): Buf {
    val b = Buf(60, 60)
    val board = "rnbqkbnrpppppppp................................PPPPPPPPRNBQKBNR".toCharArray()
    fun sq(s: String) = (8 - (s[1] - '0')) * 8 + (s[0] - 'a')
    for (m in moves) if (m.length >= 4) { board[sq(m.substring(2, 4))] = board[sq(m.substring(0, 2))]; board[sq(m.substring(0, 2))] = '.' }
    val last = moves.lastOrNull()?.takeIf { it.length >= 4 }?.let { setOf(sq(it.substring(0, 2)), sq(it.substring(2, 4))) } ?: emptySet()
    val pcs = mapOf('p' to listOf(".....", "..#..", ".###.", "..#..", ".###."), 'r' to listOf("#.#.#", "#####", ".###.", ".###.", "#####"),
        'n' to listOf(".##..", "####.", "..##.", ".###.", "#####"), 'b' to listOf("..#..", ".#.#.", ".###.", "..#..", ".###."),
        'q' to listOf("#.#.#", ".###.", "..#..", ".###.", "#####"), 'k' to listOf("..#..", ".###.", "..#..", ".###.", "#####"))
    b.r(0, 0, 60, 60, R.INK)
    for (i in 0 until 64) {
        val cx = 2 + (i % 8) * 7; val cy = 2 + (i / 8) * 7
        var c = if ((i % 8 + i / 8) % 2 == 1) R.wood[4] else R.wood[7]
        if (i in last) c = mix(c, R.honey[5], .6f)
        b.r(cx, cy, 7, 7, c)
        val p = board[i]; if (p == '.') continue
        val white = p.isUpperCase()
        pcs.getValue(p.lowercaseChar()).forEachIndexed { j, row -> for (k in 0 until 5) if (row[k] == '#') b.p(cx + 1 + k, cy + 1 + j, if (white) (if (j == 4) R.cream[2] else R.cream[5]) else (if (j == 0) R.iron[4] else R.iron[1])) }
    }
    return b
}
