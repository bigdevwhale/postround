package app.postround.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.postround.AppContainer
import app.postround.container
import app.postround.domain.Asked
import app.postround.domain.Handed
import app.postround.domain.Mine
import app.postround.domain.Result
import app.postround.domain.Shown
import app.postround.ui.art.Buf
import app.postround.ui.art.R
import app.postround.ui.components.BufImage
import app.postround.ui.components.Panel
import app.postround.ui.components.pixelBox
import app.postround.ui.theme.C
import app.postround.ui.theme.T
import app.postround.work.Tracker
import app.postround.work.WalkService
import kotlinx.coroutines.delay

/** What opens over the tabs. */
sealed interface Sheet {
    data class Door(val r: Result) : Sheet
    data class Ask(val a: Asked) : Sheet
    data class Post(val sent: List<Shown>) : Sheet
    data object Morning : Sheet
    data class Pocket(val list: List<Handed>) : Sheet
    data class Letter(val m: Mine) : Sheet
    data class Stamp(val street: String) : Sheet
    data object Settings : Sheet
    data object Legend : Sheet
    /** The delivery log of a day; with [from] > 0, the summary of the round that began then. */
    data class Delivered(val day: Long, val from: Long = 0L) : Sheet
}

/** Tabs and the actions shared by the screens. */
class Nav(val tab: (Int) -> Unit, val open: (Sheet) -> Unit, val show: (String) -> Unit)

@Composable
fun Root() {
    val ctx = LocalContext.current
    val c = ctx.container
    val ver by c.version.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    LaunchedEffect(Unit) { c.wake(); c.refreshWeather() }
    LaunchedEffect(ver) { if (sheet == null && c.morning != null) sheet = Sheet.Morning }
    // the round has just ended (by the button or from the notification): what was delivered on it
    val walking by WalkService.walking.collectAsState()
    var wasWalking by remember { mutableStateOf(walking) }
    LaunchedEffect(walking) {
        val s = c.state
        if (wasWalking && !walking && sheet == null && s != null && c.roundFrom > 0 && s.delivered.any { it.at >= c.roundFrom }) sheet = Sheet.Delivered(s.day, c.roundFrom)
        wasWalking = walking
    }
    DisposableEffect(Unit) { val tr = Tracker.of(ctx); tr.start(); onDispose { tr.stop() } }
    ver.let { }
    if (c.state?.home == null || c.terrain == null && !c.store.hasDistrict()) { Onboarding(); return }
    val nav = Nav({ tab = it }, { sheet = it }) { id -> c.selected.value = id; tab = 0 }
    Column(Modifier.fillMaxSize().background(C.paper)) {
        TopLine(c) { sheet = Sheet.Settings }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> MapScreen(nav)
                1 -> BagScreen(nav)
                2 -> AlbumScreen(nav)
                else -> WindowScreen(nav)
            }
        }
        Tabs(tab, c) { tab = it }
    }
    sheet?.let { sh -> SheetView(sh, nav) { sheet = null; if (c.morning != null && sh is Sheet.Morning) c.morning = null } }
    TelegramBanner(c, nav)
}

@Composable
private fun TelegramBanner(c: AppContainer, nav: Nav) {
    val tg by c.telegram.collectAsState()
    val ru = ru()
    val it = tg ?: return
    LaunchedEffect(it) { delay(9000); if (c.telegram.value === it) c.telegram.value = null }
    val h = c.round?.byId?.get(it.to) ?: return
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp), contentAlignment = Alignment.TopCenter) {
        Panel(Modifier.fillMaxWidth(), fill = C.telegram, onClick = { c.telegram.value = null; nav.show(h.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BufImage(app.postround.ui.art.MailIcons.of("telegram"), Modifier.padding(end = 10.dp), 3.dp)
                Column(Modifier.weight(1f)) {
                    Text(w(ru, "телеграмма! вручить до ", "a telegram! deliver by ") + hhmm(it.due), style = T.Eyebrow.copy(color = C.redDeep))
                    Text(h.addr(ru), style = T.Heading)
                    Text(w(ru, "Нажмите — покажу на карте.", "Tap to see it on the map."), style = T.Small)
                }
            }
        }
    }
}

@Composable
private fun TopLine(c: AppContainer, settings: () -> Unit) {
    val ru = ru()
    c.version.collectAsState().value
    val s = c.state ?: return
    val n = c.now()
    val d = n.date
    val months = if (ru) listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    else listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val sky = n.sky
    val wx = when { sky.snow -> w(ru, "снег", "snow"); sky.rain -> w(ru, "дождь", "rain"); sky.fog -> w(ru, "туман", "fog"); sky.sun -> w(ru, "ясно", "clear"); else -> w(ru, "облачно", "cloudy") }
    Row(Modifier.fillMaxWidth().background(C.greenDeep).statusBarsPadding().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (ru) "${d.dayOfMonth} ${months[d.monthValue - 1]}" else "${months[d.monthValue - 1]} ${d.dayOfMonth}", style = T.Heading.copy(color = C.light))
            Text("$wx, %+.0f°".format(sky.temp) + " · " + c.game.rankName(s)(ru).lowercase(), style = T.Small.copy(color = C.paper2), maxLines = 1)
        }
        val load = c.game.load(s); val cap = c.game.cap(s)
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 6.dp)) {
            Text(w(ru, "сумка", "bag"), style = T.Small.copy(color = C.paper2))
            Text("$load/$cap", style = T.Heading.copy(color = if (load >= cap) C.telegram else C.lamp))
        }
        Box(Modifier.size(44.dp).clickable(onClick = settings), contentAlignment = Alignment.Center) { BufImage(gearIcon, scale = 2.4.dp) }
    }
}

/** The four tabs, with pixel icons and little counters. */
@Composable
private fun Tabs(tab: Int, c: AppContainer, set: (Int) -> Unit) {
    val ru = ru()
    c.version.collectAsState().value
    val s = c.state
    val bag = s?.bag?.size ?: 0
    val unread = (s?.mine?.count { !it.read && it.day <= (s.day) } ?: 0) + (s?.handed?.count { !it.seen } ?: 0)
    val names = listOf(w(ru, "Карта", "Map"), w(ru, "Сумка", "Bag"), w(ru, "Марки", "Stamps"), w(ru, "Окошко", "Window"))
    Row(Modifier.fillMaxWidth().background(Color(0xFF241812)).navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        names.forEachIndexed { i, label ->
            val on = i == tab
            Box(Modifier.weight(1f).then(if (on) Modifier.pixelBox(Color(0xFF4A3324), bevel = null, lip = Color(0xFF6E4426)) else Modifier).clickable { set(i) }.padding(vertical = 5.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BufImage(TabIcons.of(i), scale = 2.dp)
                    Spacer(Modifier.height(2.dp))
                    Text(label, style = T.Small.copy(color = if (on) C.lamp else Color(0xFFCBB68C)))
                }
                val badge = when (i) { 1 -> bag; 3 -> unread; else -> 0 }
                if (badge > 0) Text("$badge", style = T.Small.copy(color = C.light), modifier = Modifier.align(Alignment.TopEnd).offset(x = (-6).dp)
                    .background(if (i == 3) C.red else C.blue).padding(horizontal = 4.dp))
            }
        }
    }
}

/** The tab icons, 14×14. */
object TabIcons {
    private val cache = HashMap<Int, Buf>()
    fun of(i: Int): Buf = cache.getOrPut(i) {
        val b = Buf(14, 14)
        when (i) {
            0 -> { b.r(1, 2, 12, 10, R.paper[2]); b.r(2, 3, 10, 8, R.stone[4]); b.r(2, 7, 10, 1, R.stone[6]); b.r(6, 3, 1, 8, R.stone[6]); b.r(9, 8, 3, 3, R.water[4]); b.p(4, 5, R.red[4]); b.p(4, 4, R.red[3]) }
            1 -> { b.r(3, 1, 8, 1, R.leather[2]); b.r(2, 2, 1, 4, R.leather[2]); b.r(11, 2, 1, 4, R.leather[2]); b.r(1, 5, 12, 8, R.leather[3]); b.r(1, 5, 12, 3, R.leather[4]); b.r(6, 7, 2, 2, R.gold[4]); b.r(1, 12, 12, 1, R.leather[1]) }
            2 -> { b.r(2, 1, 10, 12, R.cream[5]); for (x in 2 until 12 step 2) { b.p(x, 1, 0); b.p(x, 12, 0) }; b.r(4, 3, 6, 6, R.red[3]); b.r(4, 3, 6, 2, R.day[3]); b.r(4, 10, 6, 1, R.red[2]) }
            else -> { b.r(1, 3, 12, 9, R.INK); b.r(2, 4, 10, 7, R.cream[4]); for (k in 0 until 5) { b.p(2 + k, 4 + k, R.cream[1]); b.p(11 - k, 4 + k, R.cream[1]) }; b.r(9, 4, 2, 2, R.red[4]) }
        }
        b
    }
}

@Composable
private fun SheetView(sh: Sheet, nav: Nav, close: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = close).statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).navigationBarsPadding().padding(bottom = 10.dp)
                .pixelBox(C.light).clickable(enabled = false) {}.verticalScroll(rememberScrollState()).padding(14.dp)) {
                when (sh) {
                    is Sheet.Door -> DoorSheet(sh.r, close)
                    is Sheet.Ask -> AskSheet(sh.a, close)
                    is Sheet.Post -> PostSheet(sh.sent, close)
                    is Sheet.Morning -> MorningSheet(nav, close)
                    is Sheet.Pocket -> PocketSheet(sh.list, close)
                    is Sheet.Letter -> LetterSheet(sh.m, close)
                    is Sheet.Stamp -> StampSheet(sh.street, nav, close)
                    is Sheet.Settings -> SettingsView()
                    is Sheet.Legend -> LegendSheet(close)
                    is Sheet.Delivered -> DeliveredSheet(sh.day, sh.from, nav, close)
                }
            }
        }
    }
}
