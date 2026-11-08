package app.postround.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.postround.AppContainer
import app.postround.BuildConfig
import app.postround.container
import app.postround.domain.Block
import app.postround.domain.Geo
import app.postround.domain.House
import app.postround.domain.Terrain
import app.postround.domain.metres
import app.postround.ui.art.Buf
import app.postround.ui.art.G
import app.postround.ui.art.Icons
import app.postround.ui.art.MailIcons
import app.postround.ui.art.MapPainter
import app.postround.ui.art.R
import app.postround.ui.art.clampCam
import app.postround.ui.art.postman
import app.postround.ui.art.sign
import app.postround.ui.art.streetLife
import app.postround.ui.components.BufImage
import app.postround.ui.components.Kind
import app.postround.ui.components.Panel
import app.postround.ui.components.PixelButton
import app.postround.ui.components.pixelBox
import app.postround.ui.theme.C
import app.postround.ui.theme.T
import app.postround.work.WalkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** View widths in map pixels, from close up to half the district; the height follows the box. */
private val ZOOMS = intArrayOf(80, 112, 160, 224, 320)

/** The painted district, kept per month: painting takes a second, so it is done once and off the main thread. */
object MapCache {
    private const val ART = 1
    var month = -1
    var buf: Buf? = null
    var windows: List<Int> = emptyList()

    fun load(ctx: android.content.Context, t: Terrain, month: Int, px: Int = t.w / 2, py: Int = t.h / 2, quick: () -> Unit = {}) {
        val d = t.d
        val f = java.io.File(ctx.cacheDir, "map_${ART}_${month}_${"%.4f_%.4f".format(java.util.Locale.US, d.south, d.west)}.bin")
        runCatching {
            java.io.DataInputStream(java.util.zip.GZIPInputStream(f.inputStream().buffered())).use { inp ->
                val w = inp.readInt(); val h = inp.readInt()
                if (w != t.w || h != t.h) error("size")
                val b = Buf(w, h)
                for (i in b.d.indices) b.d[i] = inp.readInt()
                windows = List(inp.readInt()) { inp.readInt() }
                buf = b; this.month = month
                return
            }
        }
        val near = MapPainter(t, month, px - 170, py - 210, px + 170, py + 210)
        buf = near.paint(); windows = near.windows; this.month = month
        quick()
        val p = MapPainter(t, month)
        val b = p.paint()
        buf = b; windows = p.windows; this.month = month
        runCatching {
            ctx.cacheDir.listFiles()?.filter { it.name.startsWith("map_") }?.forEach { it.delete() }
            java.io.DataOutputStream(java.util.zip.GZIPOutputStream(f.outputStream().buffered())).use { out ->
                out.writeInt(b.w); out.writeInt(b.h)
                for (c in b.d) out.writeInt(c)
                out.writeInt(p.windows.size); p.windows.forEach { out.writeInt(it) }
            }
        }
    }
}

/** The outline of a raised building (roof and front wall) as map pixels, for the glow around a house with mail. */
private object Outlines {
    private val cache = HashMap<Int, IntArray>()
    fun of(t: Terrain, b: Block): IntArray = cache.getOrPut(b.index) {
        val set = HashSet<Int>()
        for (i in b.pts) { val x = i % t.w; val y = i / t.w; for (k in 0..b.h) set += (y - k) * t.w + x }
        val out = ArrayList<Int>()
        for (i in set) {
            val x = i % t.w; val y = i / t.w
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) { val j = (y + dy) * t.w + x + dx; if (j !in set) { out += x + dx; out += y + dy } }
        }
        out.toIntArray()
    }
}

/** The order to walk the houses in: the telegram first, then always the nearest next. */
fun routeOf(c: AppContainer, fromX: Float, fromY: Float): List<House> {
    val s = c.state ?: return emptyList(); val r = c.round ?: return emptyList()
    val left = s.bag.filter { it.kind != "riddle" && it.to != "post" }.mapNotNull { r.byId[it.to] }.distinct().toMutableList()
    val out = ArrayList<House>()
    var x = fromX; var y = fromY
    telegramOf(s.bag)?.let { tg -> r.byId[tg.to]?.let { h -> out += h; left.remove(h); x = h.doorX.toFloat(); y = h.doorY.toFloat() } }
    while (left.isNotEmpty() && out.size < 16) {
        val n = left.minBy { hypot((it.doorX - x).toDouble(), (it.doorY - y).toDouble()) }
        out += n; left.remove(n); x = n.doorX.toFloat(); y = n.doorY.toFloat()
    }
    return out
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen(nav: Nav) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ver by c.version.collectAsState()
    val here by c.here.collectAsState()
    val walking by WalkService.walking.collectAsState()
    val riding by c.riding.collectAsState()
    val sel by c.selected.collectAsState()
    val ru = ru()
    ver.let { }
    val t = c.terrain; val d = c.district; val s = c.state; val r = c.round
    if (t == null || d == null || s == null || r == null) { Loading(); return }
    val now = c.now()
    var ready by remember { mutableIntStateOf(if (MapCache.month == now.month) 1 else 0) }
    val me = here ?: s.home ?: return
    val mx = d.x(me.lon).roundToInt(); val my = d.y(me.lat).roundToInt()
    LaunchedEffect(now.month) {
        if (MapCache.month != now.month) { withContext(Dispatchers.Default) { MapCache.load(ctx, t, now.month, mx, my) { ready++ } }; ready++ }
    }
    var pan by remember { mutableStateOf(IntOffset.Zero) }
    var zoom by remember { mutableIntStateOf(2) }
    val focus by c.focus.collectAsState()
    LaunchedEffect(focus) { val f = focus ?: return@LaunchedEffect; pan = IntOffset((d.x(f.lon) - mx).roundToInt(), (d.y(f.lat) - my).roundToInt()); c.focus.value = null }
    // a house picked elsewhere (the bag, a banner): show it
    LaunchedEffect(sel) { val h = sel?.let { r.byId[it] } ?: return@LaunchedEffect; if (abs(h.doorX - mx - pan.x) > 50 || abs(h.doorY - my - pan.y) > 60) pan = IntOffset(h.doorX - mx, h.doorY - my) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { var last = 0L; while (true) withFrameMillis { ms -> if (ms - last >= 110) { last = ms; tick++ } } }
    val lt = LocalDateTime.now()
    val moment = app.postround.ui.art.Moment(lt.hour * 60 + lt.minute, now.sky, now.month, now.day)
    val withMail = s.bag.filter { it.kind != "riddle" }.groupBy { it.to }
    val route = remember(ver, mx / 8, my / 8) { routeOf(c, mx.toFloat(), my.toFloat()) }
    val postXY = c.postXY()
    // the block under the map can be folded away to see more of the map; remembered between launches
    val prefs = remember { ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var folded by remember { mutableStateOf(prefs.getBoolean("panel_folded", false)) }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            val boxW = maxWidth.value; val boxH = maxHeight.value
            val vw = ZOOMS[zoom]
            val vh = (vw * boxH / boxW).roundToInt().coerceIn(vw / 2, vw * 3).coerceAtMost(t.h)
            val view = remember(vw, vh) { Buf(vw, vh) }
            val under = remember(vw, vh) { IntArray(vw * vh) }
            val front = remember(vw, vh) { BooleanArray(vw * vh) }
            val bmp = remember(vw, vh) { Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888) }
            val img = remember(bmp) { bmp.asImageBitmap() }
            val cx = clampCam(mx - vw / 2 + pan.x, t.w, vw); val cy = clampCam(my - vh * 11 / 20 + pan.y, t.h, vh)
            val k = boxW / vw    // dp per map pixel
            if (MapCache.month != now.month && ready == 0) Loading(Modifier.align(Alignment.Center))
            Canvas(Modifier.fillMaxSize()
                .pointerInput(vw, vh) {
                    var acc = 1f
                    detectTransformGestures { _, drag, z, _ ->
                        val kk = size.width / vw.toFloat()
                        pan = IntOffset(pan.x - (drag.x / kk).roundToInt(), pan.y - (drag.y / kk).roundToInt())
                        acc *= z
                        if (acc > 1.3f && zoom > 0) { zoom--; acc = 1f }
                        if (acc < .77f && zoom < ZOOMS.size - 1) { zoom++; acc = 1f }
                    }
                }
                .pointerInput(ver, vw, vh, cx, cy) {
                    detectTapGestures(
                        onTap = { o ->
                            val kk = size.width / vw.toFloat()
                            val ax = cx + o.x / kk; val ay = cy + o.y / kk
                            val xi = ax.toInt(); val yi = ay.toInt()
                            val bi = if (xi in 0 until t.w && yi in 0 until t.h) t.bid[yi * t.w + xi] else -1
                            val h = if (bi >= 0) r.byBlock[t.blocks[bi].index] else null
                            c.selected.value = h?.id ?: withMail.keys.mapNotNull { r.byId[it] }.minByOrNull { hypot((it.block.cx - ax).toDouble(), (it.block.y0 - it.block.h - ay).toDouble()) }
                                ?.takeIf { hypot((it.block.cx - ax).toDouble(), (it.block.y0 - it.block.h - ay).toDouble()) < 10 }?.id
                        },
                        onLongPress = { o ->
                            if (!BuildConfig.DEMO) return@detectTapGestures
                            val kk = size.width / vw.toFloat()
                            val ax = cx + o.x / kk; val ay = cy + o.y / kk
                            pan = IntOffset.Zero
                            c.fix(Geo(d.lat(ay), d.lon(ax)), 5f)
                        },
                    )
                }) {
                val frame = tick + ready
                val base = MapCache.buf ?: return@Canvas
                frame.let { }
                for (y in 0 until vh) System.arraycopy(base.d, (cy + y) * base.w + cx, view.d, y * vw, vw)
                System.arraycopy(view.d, 0, under, 0, under.size)
                // the postman and the post office sign stand among the houses
                val objs = ArrayList<Pair<Int, () -> Unit>>()
                postXY?.let { (px, py) -> objs += py.toInt() to { view.sign(px.toInt() - cx, py.toInt() - cy, Icons.post, true) } }
                objs += my - cy to { view.postman(mx - cx, my - cy, if (walking) (tick / 2) % 2 else 0, false, now.sky.rain) }
                objs.sortedBy { it.first }.forEach { it.second() }
                for (i in under.indices) front[i] = view.d[i] != under[i]
                view.streetLife(t, cx, cy, moment, tick, MapCache.windows, mx - cx, my - cy, front)
                // after the night falls: the glow of houses with mail, the threads, the route — they must read in the dark
                val on = (tick / 4) % 2 == 0
                for ((id, items) in withMail) {
                    val h = r.byId[id] ?: continue
                    val b = h.block
                    if (b.x1 + 4 < cx || b.x0 - 4 > cx + vw || b.y1 + 4 < cy || b.y0 - b.h - 4 > cy + vh) continue
                    val tg = items.any { it.kind == "telegram" }
                    val col = if (tg) (if (on) R.red[5] else R.red[3]) else if (id == sel) (if (on) R.cream[5] else G.WARM2) else (if (on) G.WARM2 else R.gold[3])
                    val o = Outlines.of(t, b)
                    var i = 0
                    while (i < o.size) { view.p(o[i] - cx, o[i + 1] - cy, col); i += 2 }
                }
                sel?.let { id -> if (id !in withMail) r.byId[id]?.let { h -> val o = Outlines.of(t, h.block); var i = 0; while (i < o.size) { view.p(o[i] - cx, o[i + 1] - cy, R.cream[5], .8f); i += 2 } } }
                for ((a, b2) in s.threads) {
                    val A = r.byId[a] ?: continue; val B = r.byId[b2] ?: continue
                    val ax = A.doorX.toFloat(); val ay = A.doorY.toFloat(); val bx = B.doorX.toFloat(); val by = B.doorY.toFloat()
                    val len = hypot((bx - ax).toDouble(), (by - ay).toDouble())
                    var q = 0.0
                    while (q < len) {
                        val f = q / len
                        val x = (ax + (bx - ax) * f).roundToInt() - cx; val y = (ay + (by - ay) * f - Math.sin(f * Math.PI) * len * .08).roundToInt() - cy
                        if (((q + tick * .6) % 7) < 4) { view.p(x, y, R.red[4], .9f); view.p(x, y + 1, R.red[2], .6f) }
                        q += 1.0
                    }
                }
                // today's route: dots from the postman through the houses in order
                var px = mx.toFloat(); var py = my.toFloat()
                for ((n, h) in route.withIndex()) {
                    val len = hypot((h.doorX - px).toDouble(), (h.doorY - py).toDouble())
                    var q = 3.0
                    while (q < len - 2) {
                        val f = q / len
                        if (n == 0 || ((q / 4).toInt() % 2 == 0)) view.p((px + (h.doorX - px) * f).roundToInt() - cx, (py + (h.doorY - py) * f).roundToInt() - cy, if (n == 0) R.honey[5] else R.cream[4], if (n == 0) .95f else .55f)
                        q += if (n == 0) 2.0 else 1.0
                    }
                    px = h.doorX.toFloat(); py = h.doorY.toFloat()
                }
                // the reach of a GPS handing-over, a dotted ring of 30 m
                if (!walking) {
                    val rr = (app.postround.domain.Game.REACH / d.mpp)
                    for (a in 0 until 72 step 2) { val an = a / 72.0 * Math.PI * 2 + tick / 40.0; view.p((mx - cx + Math.cos(an) * rr).roundToInt(), (my - cy + Math.sin(an) * rr * .8).roundToInt(), R.honey[5], .55f) }
                }
                bmp.setPixels(view.d, 0, vw, 0, 0, vw, vh)
                drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(vw, vh), dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), (vh * size.width / vw).roundToInt()), filterQuality = FilterQuality.None)
            }
            // tags over the houses with mail; arrows at the edge for the ones out of sight
            val off = ArrayList<Triple<House, String, Int>>()
            val bob = if ((tick / 5) % 2 == 0) 0f else 2f
            for ((id, items) in withMail) {
                val h = if (id == "post") null else r.byId[id]
                val kind = items.maxBy { pri(it.kind) }.kind
                val (hx, hy) = if (h != null) h.block.cx to (h.block.y0 - h.block.h - 2).toFloat() else postXY?.let { it.first to it.second - 10 } ?: continue
                val sx = (hx - cx) * k; val sy = (hy - cy) * k
                if (sx < 6 || sy < 30 || sx > boxW - 6 || sy > boxH + 6) { if (h != null) off += Triple(h, kind, items.size); continue }
                Box(Modifier.offset(x = (sx - 20).dp, y = (sy - 36 - bob).dp)
                    .pixelBox(when { kind == "telegram" -> C.telegram; id == "post" -> C.paper2; id == sel -> C.light; else -> C.light }, bevel = null)
                    .clickable { if (h != null) c.selected.value = h.id else nav.open(Sheet.Post(c.post())) }
                    .padding(horizontal = 5.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BufImage(MailIcons.of(kind), scale = 2.dp)
                        if (items.size > 1) Text("${items.size}", style = T.Small.copy(color = C.light), modifier = Modifier.padding(start = 3.dp).background(C.red).padding(horizontal = 3.dp))
                    }
                }
            }
            // arrows at the edge: put each where its house lies, then line up those on the same edge so none overlap
            class Chip(val h: House, val kind: String, val dx: Float, val dy: Float, var x: Float, var y: Float, val side: Int)
            val chips = off.sortedBy { hypot((it.first.doorX - mx).toDouble(), (it.first.doorY - my).toDouble()) - if (it.second == "telegram") 1e6 else 0.0 }.take(6).map { (h, kind, _) ->
                val dx = (h.block.cx - cx) * k - boxW / 2; val dy = (h.block.y0 - cy) * k - boxH / 2
                val m = max(abs(dx) / (boxW / 2 - 50), abs(dy) / (boxH / 2 - 20))
                val x = boxW / 2 + dx / m; val y = boxH / 2 + dy / m
                val side = when { abs(dx) / (boxW / 2 - 50) >= abs(dy) / (boxH / 2 - 20) -> if (dx > 0) 1 else 3; else -> if (dy > 0) 2 else 0 }
                Chip(h, kind, dx, dy, x, y, side)
            }
            for (side in 0..3) {
                val vertical = side == 1 || side == 3
                val group = chips.filter { it.side == side }.sortedBy { if (vertical) it.y else it.x }
                val step = if (vertical) 36f else 104f
                val lo = if (vertical) 20f else 52f; val hi = if (vertical) boxH - 20f else boxW - 52f
                var prev = -1e9f
                for (ch in group) { val v = maxOf(if (vertical) ch.y else ch.x, prev + step, lo); if (vertical) ch.y = v else ch.x = v; prev = v }
                // pushed past the end: slide the whole row back
                val over = prev - hi
                if (over > 0) for (ch in group) if (vertical) ch.y = maxOf(lo, ch.y - over) else ch.x = maxOf(lo, ch.x - over)
            }
            for (ch in chips) {
                val metres = r.metresDoor(ch.h, mx.toFloat(), my.toFloat())
                Row(Modifier.offset(x = (ch.x - 46).dp, y = (ch.y - 15).dp).pixelBox(if (ch.kind == "telegram") C.telegram else C.light, bevel = null)
                    .clickable { c.selected.value = ch.h.id }.padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    DirArrow(Math.toDegrees(atan2(-ch.dy.toDouble(), ch.dx.toDouble())).toFloat(), color = C.red)
                    BufImage(MailIcons.of(ch.kind), Modifier.padding(horizontal = 3.dp), 1.5.dp)
                    Text(dist(metres, ru), style = T.Small.copy(color = C.ink))
                }
            }
            sel?.let { id -> r.byId[id] }?.let { h ->
                val sx = (h.block.cx - cx) * k; val sy = (h.block.y0 - h.block.h - cy) * k
                if (sx in 0f..boxW && sy in 40f..boxH) {
                    val label = h.addr(ru)
                    val lw = label.length * 8.6f + 12
                    Text(label, style = T.Small.copy(color = C.ink), maxLines = 1, modifier = Modifier.offset(x = (sx - lw / 2).coerceIn(2f, boxW - lw - 2).dp, y = (sy - (if (h.id in withMail) 66 else 26)).dp)
                        .pixelBox(C.lamp, bevel = null).padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PixelButton("+", { if (zoom > 0) zoom-- }, kind = Kind.GHOST, enabled = zoom > 0)
                PixelButton("−", { if (zoom < ZOOMS.size - 1) zoom++ }, kind = Kind.GHOST, enabled = zoom < ZOOMS.size - 1)
            }
            if (pan != IntOffset.Zero) PixelButton(w(ru, "К себе", "To me"), { pan = IntOffset.Zero }, Modifier.align(Alignment.TopEnd).padding(8.dp), Kind.GHOST)
            PixelButton("?", { nav.open(Sheet.Legend) }, Modifier.align(Alignment.BottomStart).padding(8.dp), Kind.GHOST)
            PixelButton(if (folded) w(ru, "Развернуть", "Show panel") else w(ru, "Свернуть", "Hide panel"),
                { folded = !folded; prefs.edit().putBoolean("panel_folded", folded).apply() }, Modifier.align(Alignment.TopStart).padding(8.dp), Kind.GHOST)
        }
        ActionPanel(c, nav, me, route, walking, riding, sel, folded)
    }
}

private fun pri(kind: String) = when (kind) { "telegram" -> 9; "pickup" -> 8; "out" -> 7; "parcel" -> 6; "chess" -> 5; "letter" -> 5; "card" -> 4; "riddle" -> 0; else -> 2 }

/** What can be done right here, in a compact block under the map. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionPanel(c: AppContainer, nav: Nav, me: Geo, route: List<House>, walking: Boolean, riding: Boolean, sel: String?, folded: Boolean) {
    val ctx = LocalContext.current
    val ru = ru()
    val s = c.state ?: return; val r = c.round ?: return; val d = c.district ?: return
    val mx = d.x(me.lon); val my = d.y(me.lat)
    val reach = c.reachable(me)
    Column(Modifier.fillMaxWidth().heightIn(max = 330.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = if (folded) 4.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        // folded: only what has to be acted on here and now — the hand-over buttons, a telegram, a house just tapped
        if (!folded) Row(verticalAlignment = Alignment.Bottom) {
            Text(placeName(c, me, ru), style = T.Heading, modifier = Modifier.weight(1f), maxLines = 1)
            val steps = s.log.firstOrNull { it.day == s.day }?.steps ?: -1
            Text(w(ru, "сегодня ", "today ") + km(maxOf(s.kmToday, if (steps > 0) steps * 0.00072 else 0.0), ru) + if (steps >= 0) " · " + "%,d".format(steps).replace(',', ' ') + w(ru, " шагов", " steps") else "", style = T.Small)
        }
        telegramOf(s.bag)?.let { tg ->
            val h = r.byId[tg.to]
            val left = ((tg.due - System.currentTimeMillis()) / 60_000).toInt()
            Panel(Modifier.fillMaxWidth(), fill = C.telegram, onClick = { c.selected.value = tg.to }) {
                Text(w(ru, "телеграмма · вручить до ", "telegram · deliver by ") + hhmm(tg.due), style = T.Eyebrow.copy(color = C.redDeep))
                Text((h?.addr(ru) ?: "") + " · " + if (left > 0) w(ru, "осталось $left мин", "$left min left") else w(ru, "опаздывает", "running late"), style = T.Body)
            }
        }
        if (riding) Text(w(ru, "Вы едете — из транспорта письма не вручают. Выйдите и подойдите к дому.", "You're riding — mail isn't handed over from a vehicle. Get off and walk up to the house."), style = T.Small.copy(color = C.red))
        if (reach.isNotEmpty() || (c.nearPost(me) && s.bag.any { it.to == "post" }) || s.bag.any { it.kind == "riddle" }) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (id in reach) {
                if (id == "post") continue
                val h = r.byId[id] ?: continue
                val items = s.bag.filter { it.to == id && it.kind != "riddle" }
                if (items.isEmpty()) continue
                val who = c.game.resident(s, h)
                val pick = items.any { it.kind == "pickup" }
                PixelButton((if (pick) w(ru, "Зайти: ", "Call on ") else w(ru, "Вручить: ", "Hand to ")) + who.dat(ru) + if (items.size > 1) " · ${items.size}" else "",
                    { c.deliver(id)?.let { res -> c.selected.value = null; nav.open(Sheet.Door(res)) } }, kind = Kind.LAMP)
            }
            if (c.nearPost(me) && s.bag.any { it.to == "post" }) PixelButton(w(ru, "Сдать посылку на почту", "Hand the parcel in"), { nav.open(Sheet.Post(c.post())) }, kind = Kind.PRIMARY)
            if (s.bag.any { it.kind == "riddle" }) {
                val near = r.houses.filter { c.game.inReach(r, it, mx, my) }.minByOrNull { r.metresTo(it, mx, my) }
                if (near != null) PixelButton(w(ru, "Спросить здесь про письмо без адреса", "Ask here about the unaddressed letter"), { c.ask(near.id)?.let { nav.open(Sheet.Ask(it)) } }, kind = Kind.GHOST)
            }
        }
        // the picked house, or the next stop of the route
        val pick = sel?.let { r.byId[it] }
        val target = pick ?: route.firstOrNull()?.takeIf { !folded }
        if (target != null) {
            val items = s.bag.filter { it.to == target.id && it.kind != "riddle" }
            val m = r.metresDoor(target, mx, my)
            val ang = Math.toDegrees(atan2(-(target.doorY - my).toDouble(), (target.doorX - mx).toDouble())).toFloat()
            val who = c.game.resident(s, target)
            Panel(Modifier.fillMaxWidth(), fill = if (pick != null) C.light else C.paper, onClick = { c.focus.value = Geo(d.lat(target.doorY.toFloat()), d.lon(target.doorX.toFloat())) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (pick != null) w(ru, "дом", "house") else w(ru, "дальше по маршруту", "next on the route"), style = T.Eyebrow)
                        Text(target.addr(ru), style = T.Heading, maxLines = 1)
                        Text(who.name(ru) + " · " + who.role(ru), style = T.Small, maxLines = 1)
                        // what's for this house, each with its icon and a word on what it is
                        for (it in items.distinctBy { it.kind }) Row(Modifier.padding(top = 3.dp).clickable { nav.open(Sheet.Legend) }, verticalAlignment = Alignment.CenterVertically) {
                            BufImage(MailIcons.of(it.kind), Modifier.padding(end = 6.dp), 1.5.dp)
                            val n = items.count { o -> o.kind == it.kind }
                            Text(kindName(it.kind, ru) + (if (n > 1) " ×$n" else "") + " — " + kindHint(it.kind, ru), style = T.Small.copy(color = C.ink), maxLines = 2)
                        }
                        if (items.isEmpty() && pick != null) Text(w(ru, "Сегодня для этого дома ничего нет.", "Nothing for this house today."), style = T.Small)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DirArrow(ang, size = 24.dp, color = C.green)
                        Text(dist(m, ru), style = T.Body)
                    }
                }
            }
        } else if (s.bag.isEmpty() && !folded) {
            Text(w(ru, "Сумка пуста. Завтра утром будет новая.", "The bag is empty. A new one comes in the morning."), style = T.Body)
        }
        if (folded) return@Column
        if (route.size > 1) {
            var len = 0.0; var px = mx; var py = my
            route.forEach { h -> len += hypot((h.doorX - px).toDouble(), (h.doorY - py).toDouble()) * d.mpp; px = h.doorX.toFloat(); py = h.doorY.toFloat() }
            Text(w(ru, "Маршрут на сегодня: ${route.size} ${plural(route.size, "адрес", "адреса", "адресов")} · около ${dist(len, true)}", "Today's route: ${route.size} addresses · about ${dist(len, false)}"), style = T.Small)
        }
        val pocket = s.handed.filter { !it.seen }
        val today = s.delivered.filter { it.day == s.day }.sumOf { h -> h.items.count { it.kind != "pickup" } }
        if (pocket.isNotEmpty() || today > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pocket.isNotEmpty()) PixelButton(w(ru, "Что сказали · ${pocket.size}", "What they said · ${pocket.size}"), { nav.open(Sheet.Pocket(pocket)) }, Modifier.weight(1f), Kind.GHOST)
            if (today > 0) PixelButton(w(ru, "Разнесено · $today", "Delivered · $today"), { nav.open(Sheet.Delivered(s.day)) }, Modifier.weight(1f), Kind.GHOST)
        }
        PixelButton(if (walking) w(ru, "Закончить обход", "End the round") else w(ru, "Выйти на обход", "Start the round"),
            { if (walking) WalkService.stop(ctx) else WalkService.start(ctx) }, Modifier.fillMaxWidth(), if (walking) Kind.GHOST else Kind.PRIMARY)
        Text(if (walking) w(ru, "Телефон можно убрать в карман: у дверей письма вручатся сами, а что сказали жильцы — прочтёте потом.", "You can pocket the phone: mail is handed over at the doors, and you can read what people said later.")
             else w(ru, "Письмо вручается в 30 метрах от дома. На обходе — даже с телефоном в кармане.", "Mail is handed over within 30 metres of the house. On the round, even with the phone in your pocket."), style = T.Small)
        if (BuildConfig.DEMO) Text(w(ru, "Демо: долгое нажатие на карту переносит почтальона.", "Demo: a long press on the map moves the postman."), style = T.Small.copy(color = C.blue))
    }
}

/** The street name nearest to the player, or a general word. */
fun placeName(c: AppContainer, at: Geo, ru: Boolean): String {
    val d = c.district ?: return ""
    if (c.nearPost(at)) return w(ru, "У почты", "At the post office")
    val home = c.state?.home
    if (home != null && home.metres(at) < 30) return w(ru, "У дома", "At home")
    val x = d.x(at.lon); val y = d.y(at.lat)
    var best: String? = null; var bd = 14.0
    for (r in d.roads) {
        if (r.name.isBlank()) continue
        var i = 0
        while (i + 1 < r.pts.size) { val dd = Math.hypot((r.pts[i] - x).toDouble(), (r.pts[i + 1] - y).toDouble()); if (dd < bd) { bd = dd; best = r.name }; i += 2 }
    }
    return best?.let { app.postround.domain.Round.short(it, ru) } ?: w(ru, "На участке", "On the round")
}
