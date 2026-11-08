package app.postround.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.postround.container
import app.postround.domain.Game
import app.postround.ui.art.MailIcons
import app.postround.ui.art.chessBoard
import app.postround.ui.art.stampOf
import app.postround.ui.components.BufImage
import app.postround.ui.components.Eyebrow
import app.postround.ui.components.Kind
import app.postround.ui.components.Panel
import app.postround.ui.components.PixelButton
import app.postround.ui.components.Segments
import app.postround.ui.components.Stat
import app.postround.ui.components.pixelBox
import app.postround.ui.theme.C
import app.postround.ui.theme.T

/** The bag, sorted the way postmen sort it: by street, nearest street first. */
@Composable
fun BagScreen(nav: Nav) {
    val c = LocalContext.current.container
    val ru = ru()
    c.version.collectAsState().value
    val here by c.here.collectAsState()
    val s = c.state ?: return; val r = c.round ?: return; val d = c.district ?: return
    val me = here ?: s.home ?: return
    val mx = d.x(me.lon); val my = d.y(me.lat)
    val load = c.game.load(s); val cap = c.game.cap(s)
    val byStreet = s.bag.filter { it.kind != "riddle" && it.to != "post" }.groupBy { r.byId[it.to]?.street ?: "" }
        .entries.sortedBy { (_, items) -> items.minOf { it2 -> r.byId[it2.to]?.let { h -> r.metresDoor(h, mx, my) } ?: 1e9 } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(w(ru, "Сумка", "The bag"), style = T.Title)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(w(ru, "вес $load из $cap", "weight $load of $cap"), style = T.Small, modifier = Modifier.width(110.dp))
                Segments(load / cap.toFloat(), Modifier.weight(1f), cap)
            }
            if (s.belt) Text(w(ru, "Ремень Сомовой: плечо не режет.", "Mrs Somers' strap: easy on the shoulder."), style = T.Small)
        }
        val pocket = s.handed.filter { !it.seen }
        if (pocket.isNotEmpty()) item {
            Panel(Modifier.fillMaxWidth(), fill = C.lamp, onClick = { nav.open(Sheet.Pocket(pocket)) }) {
                Text(w(ru, "вручено по дороге", "delivered on the way"), style = T.Eyebrow.copy(color = C.ink))
                Text(w(ru, "${pocket.size} ${plural(pocket.size, "дом", "дома", "домов")} — что сказали жильцы", "${pocket.size} houses — what people said"), style = T.Body)
            }
        }
        s.bag.firstOrNull { it.kind == "riddle" }?.let { rd ->
            item {
                Panel(Modifier.fillMaxWidth(), fill = C.paper2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BufImage(MailIcons.of("riddle"), Modifier.padding(end = 10.dp), 3.dp)
                        Column(Modifier.weight(1f)) {
                            Text(w(ru, "письмо без адреса", "a letter with no address"), style = T.Eyebrow)
                            Text("«" + c.game.clue(r, rd)(ru) + "»", style = T.Hand)
                        }
                    }
                    Text(w(ru, "Номера нет, улицы тоже. Найдите дом по приметам, подойдите и спросите — жильцы подскажут, куда идти.",
                        "No number, no street. Find the house by the signs, walk up and ask — the people there will point the way."), style = T.Small, modifier = Modifier.padding(top = 4.dp))
                    if (s.riddleTries > 0) Text(w(ru, "Спрашивали уже ${s.riddleTries} раз.", "Asked ${s.riddleTries} times so far."), style = T.Small)
                }
            }
        }
        s.bag.filter { it.to == "post" }.takeIf { it.isNotEmpty() }?.let { out ->
            item {
                Panel(Modifier.fillMaxWidth(), fill = C.light, onClick = { c.postXY()?.let { (x, y) -> c.focus.value = app.postround.domain.Geo(d.lat(y), d.lon(x)); nav.tab(0) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BufImage(MailIcons.of("out"), Modifier.padding(end = 10.dp), 3.dp)
                        Column(Modifier.weight(1f)) {
                            Text(w(ru, "на почту", "to the post office"), style = T.Eyebrow)
                            Text(w(ru, "Посылка на отправку", "A parcel to send") + if (out.size > 1) " ×${out.size}" else "", style = T.Body)
                        }
                        c.postXY()?.let { (x, y) -> Text(dist(Math.hypot((x - mx).toDouble(), (y - my).toDouble()) * d.mpp, ru), style = T.Small) }
                    }
                }
            }
        }
        for ((street, items) in byStreet) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(app.postround.domain.Round.short(street, ru), style = T.Heading, modifier = Modifier.weight(1f))
                    if (street !in s.streets) Text(w(ru, "новая марка", "new stamp"), style = T.Small.copy(color = C.red))
                }
            }
            items(items.sortedBy { r.byId[it.to]?.number?.filter(Char::isDigit)?.toIntOrNull() ?: 0 }, key = { it.id }) { it ->
                val h = r.byId[it.to] ?: return@items
                val who = c.game.resident(s, h)
                val m = r.metresDoor(h, mx, my)
                val tg = it.kind == "telegram"
                Panel(Modifier.fillMaxWidth(), fill = if (tg) C.telegram else C.light, onClick = { nav.show(h.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BufImage(MailIcons.of(it.kind), Modifier.padding(end = 10.dp).clickable { nav.open(Sheet.Legend) }, 3.dp)
                        Column(Modifier.weight(1f)) {
                            Text(who.dat(ru).replaceFirstChar { it.uppercase() }, style = T.Body, maxLines = 1)
                            Text("${h.number} · " + kindName(it.kind, ru) + " · " + w(ru, "от: ", "from: ") + c.game.show(s, it).from(ru) +
                                if (tg) w(ru, " · до ", " · by ") + hhmm(it.due) else "", style = T.Small, maxLines = 2)
                        }
                        Text(dist(m, ru), style = T.Small.copy(color = if (m <= Game.REACH) C.green else C.muted))
                    }
                }
            }
        }
        if (s.bag.isEmpty()) item { Text(w(ru, "Пусто. Всё вручено — новая сумка будет утром.", "Empty. Everything's delivered — a new bag comes in the morning."), style = T.Body, modifier = Modifier.padding(top = 20.dp)) }
    }
}

/** The stamps of the streets: one per street of the district, coloured once you've handed something over on it. */
@Composable
fun AlbumScreen(nav: Nav) {
    val c = LocalContext.current.container
    val ru = ru()
    c.version.collectAsState().value
    val s = c.state ?: return; val r = c.round ?: return
    val got = r.streets.count { it in s.streets }
    val month = c.now().month
    LazyVerticalGrid(GridCells.Adaptive(82.dp), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(w(ru, "Марки улиц", "Street stamps"), style = T.Title)
                Text(w(ru, "У каждой улицы района своя марка. Она приходит с первым вручением на этой улице. Собрано $got из ${r.streets.size}.",
                    "Every street of the district has its own stamp. It comes with your first delivery there. $got of ${r.streets.size} collected."), style = T.Small)
                Segments(got / r.streets.size.coerceAtLeast(1).toFloat(), Modifier.padding(top = 6.dp), 20)
            }
        }
        val order = r.streets.sortedByDescending { s.streets[it] != null }
        items(order, key = { it }) { st ->
            val have = st in s.streets
            Column(Modifier.clickable { nav.open(Sheet.Stamp(st)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                BufImage(stampOf(st, r.motif[st] ?: "houses", have, month), scale = 2.dp)
                Text(if (have) app.postround.domain.Round.short(st, ru) else "?", style = T.Small.copy(color = if (have) C.ink else C.faint), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** The «До востребования» window: letters for the postman, the threads, the chess game, the rank and what's in the pockets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WindowScreen(nav: Nav) {
    val c = LocalContext.current.container
    val ru = ru()
    c.version.collectAsState().value
    val s = c.state ?: return; val r = c.round ?: return
    val g = c.game
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(w(ru, "До востребования", "Poste restante"), style = T.Title)
            Text(w(ru, "Окошко в отделении: здесь лежит то, что пишут вам.", "The window at the post office: what people write to you waits here."), style = T.Small)
        }
        item {
            val rk = g.rank(s); val nx = Game.RANKS.getOrNull(rk + 1)
            Panel(Modifier.fillMaxWidth()) {
                Text(g.rankName(s)(ru), style = T.Heading)
                if (nx != null) {
                    Text(w(ru, "до «${nx.second.ru}» — ещё ${nx.first - s.done}", "to \"${nx.second.en}\": ${nx.first - s.done} more"), style = T.Small)
                    Segments((s.done - Game.RANKS[rk].first) / (nx.first - Game.RANKS[rk].first).toFloat(), Modifier.padding(top = 4.dp))
                }
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("${s.done}", w(ru, "вручено", "delivered"))
                    Stat(km(s.km, ru), w(ru, "пройдено", "walked"))
                    Stat("${s.streets.size}/${r.streets.size}", w(ru, "марок", "stamps"))
                    Stat("${s.tgOk}/${s.tgAll}", w(ru, "телеграмм вовремя", "telegrams on time"))
                    Stat("${s.riddles}", w(ru, "без адреса", "no address"))
                    Stat("${s.threads.size}", w(ru, "нитей", "threads"))
                    if (s.healthFrom >= 0) s.log.filter { it.day > s.day - 7 && it.steps > 0 }.sumOf { it.steps }.takeIf { it > 0 }?.let { n ->
                        Stat("%,d".format(n).replace(',', ' '), w(ru, "шагов за неделю", "steps this week"))
                    }
                }
            }
        }
        item {
            val list = s.delivered.filter { it.day == s.day }
            val n = list.sumOf { h -> h.items.count { it.kind != "pickup" } }
            Panel(Modifier.fillMaxWidth(), fill = C.light, onClick = { nav.open(Sheet.Delivered(s.day)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BufImage(MailIcons.of("parcel"), Modifier.padding(end = 10.dp), 3.dp)
                    Column(Modifier.weight(1f)) {
                        Text(w(ru, "Журнал вручений", "The delivery log"), style = T.Body)
                        Text(if (n > 0) w(ru, "Сегодня вручено ${countItems(n, true)} — кому и что", "Today: ${countItems(n, false)} — to whom and what")
                             else w(ru, "Кому и что вы вручили за последние дни", "Who you delivered what to, these last days"), style = T.Small)
                    }
                }
            }
        }
        val letters = s.mine.filter { it.day <= s.day }.reversed()
        item { Eyebrow(w(ru, "письма почтальону", "letters to the postman"), Modifier.padding(top = 4.dp)) }
        if (letters.isEmpty()) item { Text(w(ru, "Пока пусто. Жильцы пишут почтальону, когда их переписка доходит до конца.", "Nothing yet. People write to the postman when their correspondence comes to an end."), style = T.Small) }
        items(letters, key = { it.arc + it.day }) { m ->
            val (from, text, _) = g.mineText(m)
            Panel(Modifier.fillMaxWidth(), fill = if (m.read) C.light else C.lamp, onClick = { nav.open(Sheet.Letter(m)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BufImage(MailIcons.of("letter"), Modifier.padding(end = 10.dp), 3.dp)
                    Column(Modifier.weight(1f)) {
                        Text(from(ru) + if (!m.read) w(ru, " · новое", " · new") else "", style = T.Body)
                        Text(text(ru), style = T.Small, maxLines = 2)
                    }
                }
            }
        }
        item { Eyebrow(w(ru, "нити", "threads"), Modifier.padding(top = 4.dp)) }
        val started = g.c.arcs.filter { it.id in s.arcs }
        if (started.isEmpty()) item { Text(w(ru, "Когда вы носите письма между двумя домами, на карте между ними натягивается красная нить.", "When you carry letters between two houses, a red thread is drawn between them on the map."), style = T.Small) }
        items(started, key = { it.id }) { a ->
            val n = s.arcs[a.id] ?: 0
            val done = a.id in s.arcDone
            Panel(Modifier.fillMaxWidth(), fill = C.light, onClick = {
                a.cast.firstNotNullOfOrNull { id -> s.cast[id] }?.let { nav.show(it) }
            }) {
                Text(a.title(ru), style = T.Body)
                Text(if (done) w(ru, "дописано", "finished") else w(ru, "писем: $n из ${a.steps.size}", "letters: $n of ${a.steps.size}"), style = T.Small)
                Segments(n / a.steps.size.toFloat(), Modifier.padding(top = 4.dp), a.steps.size)
                val who = a.cast.mapNotNull { id -> val h = s.cast[id]?.let { r.byId[it] } ?: return@mapNotNull null; g.c.cast[id]?.name?.invoke(ru) + " — " + h.addr(ru) }
                if (who.isNotEmpty()) Text(who.joinToString("\n"), style = T.Small)
                if (g.chessArc() == a.id && n > 0) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BufImage(chessBoard(g.chessMoves(s)), scale = 2.dp)
                    Text(w(ru, "Ход ${g.chessMoves(s).size}", "Move ${g.chessMoves(s).size}"), style = T.Small, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
        if (s.gifts.isNotEmpty()) {
            item { Eyebrow(w(ru, "карманы", "pockets"), Modifier.padding(top = 4.dp)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.gifts.distinct().forEach { Text(giftText(it, ru), style = T.Small.copy(color = C.ink), modifier = Modifier.pixelBox(C.paper2, bevel = null).padding(horizontal = 7.dp, vertical = 3.dp)) }
                }
            }
        }
        item { PixelButton(w(ru, "Настройки", "Settings"), { nav.open(Sheet.Settings) }, Modifier.fillMaxWidth().padding(top = 8.dp), Kind.GHOST) }
    }
}
