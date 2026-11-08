package app.postround.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.postround.AppContainer
import app.postround.container
import app.postround.domain.Asked
import app.postround.domain.GameState
import app.postround.domain.Handed
import app.postround.domain.House
import app.postround.domain.Mine
import app.postround.domain.Result
import app.postround.domain.Shown
import app.postround.ui.art.DoorScene
import app.postround.ui.art.Look
import app.postround.ui.art.MailIcons
import app.postround.ui.art.PixelView
import app.postround.ui.art.chessBoard
import app.postround.ui.art.facadeOf
import app.postround.ui.art.stampOf
import app.postround.ui.components.BufImage
import app.postround.ui.components.Eyebrow
import app.postround.ui.components.Kind
import app.postround.ui.components.PixelButton
import app.postround.ui.components.pixelBox
import app.postround.ui.theme.C
import app.postround.ui.theme.T

/** The house as seen at its door, with whoever lives there. */
@Composable
fun DoorPicture(c: AppContainer, h: House, withPerson: Boolean = true) {
    val s = c.state ?: return
    val r = c.round ?: return
    val res = c.game.resident(s, h)
    val m = moment(c)
    PixelView(remember(h.id, m.minute / 10, withPerson) { DoorScene(h.kind, facadeOf(r.facade(h)), h.number, if (withPerson) Look.of(res.look) else null, m, h.block.index) },
        Modifier.fillMaxWidth().pixelBox(Color.Transparent).padding(2.dp))
}

/** The post office front with Galina at the door. */
@Composable
fun PostPicture(c: AppContainer) {
    val m = moment(c)
    val galina = mapOf("hair" to "wood", "cloth" to "roofB", "bun" to "1", "glasses" to "1")
    PixelView(remember(m.minute / 10) { DoorScene("post", app.postround.ui.art.G.sky2, w(true, "", ""), Look.of(galina), m, 7) }, Modifier.fillMaxWidth().pixelBox(Color.Transparent).padding(2.dp))
}

@Composable
private fun ShownBlock(sh: Shown, ru: Boolean, c: AppContainer) {
    val it = sh.item
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BufImage(MailIcons.of(it.kind), Modifier.padding(end = 8.dp), 2.dp)
            Text(kindName(it.kind, ru) + " · " + w(ru, "от: ", "from: ") + sh.from(ru), style = T.Eyebrow)
        }
        if (!sh.text.blank) {
            val tg = it.kind == "telegram"
            Text(sh.text(ru), style = if (tg) T.Body.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp)) else T.Letter,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).pixelBox(if (tg) C.telegram else C.paper, bevel = null).padding(horizontal = 12.dp, vertical = 10.dp))
        }
        if (it.kind == "chess") {
            val moves = c.game.c.arc[it.arc]?.steps?.take(it.st + 1)?.filter { st -> st.kind == "chess" }?.map { st -> st.extra }.orEmpty()
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BufImage(chessBoard(moves), scale = 2.dp)
                Text(w(ru, "Ход ${moves.size}. Доска — как у них на столе.", "Move ${moves.size}. The board as it stands on their table."), style = T.Small, modifier = Modifier.padding(start = 10.dp))
            }
        }
        if (!sh.say.blank) Text(said(sh.say(ru)), style = T.Body, modifier = Modifier.padding(top = 8.dp))
        if (!sh.gift.blank) Text(w(ru, "Вам в карман: ", "Into your pocket: ") + sh.gift(ru) + ".", style = T.Body.copy(color = C.ochreDeep), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun StampRow(c: AppContainer, street: String, ru: Boolean) {
    val r = c.round ?: return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).pixelBox(C.paper, bevel = null).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BufImage(stampOf(street, r.motif[street] ?: "houses", true, c.now().month, 0xFFF2E2BB.toInt()), scale = 2.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(w(ru, "Новая марка", "A new stamp"), style = T.Heading)
            Text(street + w(ru, ". Первое вручение на этой улице.", ". The first delivery on this street."), style = T.Small)
        }
    }
}

@Composable
fun DoorSheet(r: Result, close: () -> Unit) {
    val c = LocalContext.current.container
    val ru = ru()
    val s = c.state ?: return
    val res = c.game.resident(s, r.house)
    DoorPicture(c, r.house)
    Text(res.name(ru), style = T.Title, modifier = Modifier.padding(top = 10.dp))
    Text(r.house.addr(ru) + " · " + res.role(ru), style = T.Small)
    r.shown.forEach { ShownBlock(it, ru, c) }
    r.notes.forEach { Text(it(ru), style = T.Small.copy(color = C.blue), modifier = Modifier.padding(top = 8.dp)) }
    r.stamp?.let { StampRow(c, it, ru) }
    r.rankUp?.let { Text(w(ru, "Новый класс: ", "New grade: ") + it(ru) + "!", style = T.Heading.copy(color = C.green), modifier = Modifier.padding(top = 10.dp)) }
    Spacer(Modifier.height(12.dp))
    PixelButton(w(ru, "Дальше", "Go on"), close, Modifier.fillMaxWidth(), Kind.LAMP)
}

@Composable
fun AskSheet(a: Asked, close: () -> Unit) {
    val c = LocalContext.current.container
    val ru = ru()
    if (a.found) {
        DoorPicture(c, a.house)
        Text(w(ru, "Нашли! ", "Found it! ") + a.house.addr(ru), style = T.Title, modifier = Modifier.padding(top = 10.dp))
        Text(a.memory(ru), style = T.Letter, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).pixelBox(C.paper, bevel = null).padding(12.dp))
        Text(said(a.say(ru)), style = T.Body, modifier = Modifier.padding(top = 8.dp))
        Text(if (a.last) w(ru, "Это было третье письмо без адреса. Завтра в окошке «До востребования» будет ответ.", "That was the third letter with no address. Tomorrow there'll be an answer in the poste restante window.")
             else w(ru, "Следующее такое письмо придёт через несколько дней.", "The next letter like this will come in a few days."), style = T.Small.copy(color = C.blue), modifier = Modifier.padding(top = 8.dp))
        a.stamp?.let { StampRow(c, it, ru) }
    } else {
        DoorPicture(c, a.house)
        Text(a.house.addr(ru), style = T.Title, modifier = Modifier.padding(top = 10.dp))
        Text(a.hint(ru), style = T.Body, modifier = Modifier.padding(top = 8.dp))
    }
    Spacer(Modifier.height(12.dp))
    PixelButton(w(ru, "Дальше", "Go on"), close, Modifier.fillMaxWidth(), Kind.LAMP)
}

@Composable
fun PostSheet(sent: List<Shown>, close: () -> Unit) {
    val c = LocalContext.current.container
    val ru = ru()
    val s = c.state ?: return
    PostPicture(c)
    Text(w(ru, "Отделение связи", "The post office"), style = T.Title, modifier = Modifier.padding(top = 10.dp))
    if (c.round?.realPost != true) Text(w(ru, "Настоящего отделения в районе нет — посылки уходят из ящика у вашего дома.", "There's no real post office in the district — parcels go from the box by your home."), style = T.Small)
    sent.forEach { Text(w(ru, "Отправлено: ", "Sent: ") + it.text(ru), style = T.Body, modifier = Modifier.padding(top = 8.dp)) }
    val left = s.bag.size
    Text("— " + if (left == 0) w(ru, "Всё разнесли? Ну вы даёте. Чаю?", "All delivered? Well I never. Tea?")
         else w(ru, "В сумке ещё ${countItems(left, true)}. Не успеете сегодня — завтра донесёте.", "Still ${countItems(left, false)} in the bag. If not today, then tomorrow."),
        style = T.Body, modifier = Modifier.padding(top = 10.dp))
    Spacer(Modifier.height(12.dp))
    PixelButton(w(ru, "Дальше", "Go on"), close, Modifier.fillMaxWidth(), Kind.LAMP)
}

@Composable
fun MorningSheet(nav: Nav, close: () -> Unit) {
    val c = LocalContext.current.container
    val ru = ru()
    val s = c.state ?: return
    val m = c.morning
    PostPicture(c)
    val d = c.now().date
    val months = if (ru) listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    else listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    Text(w(ru, "Утро, ${d.dayOfMonth} ${months[d.monthValue - 1]}", "Morning, ${months[d.monthValue - 1]} ${d.dayOfMonth}"), style = T.Title.copy(color = C.green), modifier = Modifier.padding(top = 10.dp))
    m?.yesterday?.takeIf { it.handed > 0 || it.walked > .2 }?.let { y ->
        Text(w(ru, "Вчера: вручено ${y.handed}, пройдено ${km(y.walked, true)}", "Yesterday: ${y.handed} delivered, ${km(y.walked, false)} walked") +
            (if (y.steps > 0) w(ru, " (%,d шагов)", " (%,d steps)").format(y.steps).replace(',', ' ') else "") +
            if (y.streets > 0) w(ru, ", новых марок: ${y.streets}.", ", ${y.streets} new stamps.") else ".", style = T.Small, modifier = Modifier.padding(top = 4.dp))
    }
    val counts = s.bag.groupingBy { kindName(it.kind, ru) }.eachCount()
    Text(w(ru, "Галина передала сумку: ", "Galina has handed you the bag: ") + counts.entries.joinToString(", ") { "${it.key} ×${it.value}" } + ".", style = T.Body, modifier = Modifier.padding(top = 8.dp))
    Text(w(ru, "Вес ${c.game.load(s)} из ${c.game.cap(s)}.", "Weight ${c.game.load(s)} of ${c.game.cap(s)}."), style = T.Small)
    if (s.bag.any { it.kind == "riddle" }) Text(w(ru, "Среди писем одно без номера и без улицы. Галина пожимает плечами: «Опять она».", "One letter has no number and no street. Galina shrugs: \"Her again.\""),
        style = T.Small.copy(color = C.blue), modifier = Modifier.padding(top = 6.dp))
    val unread = s.mine.count { !it.read && it.day <= s.day }
    if (unread > 0) Text(w(ru, "В окошке «До востребования» — письмо для вас.", "There's a letter for you in the poste restante window."), style = T.Small.copy(color = C.red), modifier = Modifier.padding(top = 6.dp))
    val sky = c.now().sky
    Text("— " + when {
        sky.rain -> w(ru, "Дождь. Газеты под накидку, а то размокнут.", "Rain. Papers under the cape or they'll go soggy.")
        sky.snow -> w(ru, "Скользко. Не бегайте с посылками.", "It's slippery. Don't run with the parcels.")
        sky.fog -> w(ru, "Туман. Номера домов на ощупь, да?", "Fog. Feeling for the house numbers, are we?")
        else -> w(ru, "Погода — гуляй не хочу. Ну, идите.", "Lovely day for it. Off you go.")
    }, style = T.Body, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(12.dp))
    if (s.delivered.any { it.day == s.day - 1 }) PixelButton(w(ru, "Что вручили вчера", "What was delivered yesterday"), { close(); nav.open(Sheet.Delivered(s.day - 1)) }, Modifier.fillMaxWidth().padding(bottom = 8.dp), Kind.GHOST)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelButton(w(ru, "На участок", "Out on the round"), close, Modifier.weight(1f), Kind.LAMP)
        if (unread > 0) PixelButton(w(ru, "Окошко", "The window"), { close(); nav.tab(3) }, kind = Kind.GHOST)
    }
}

@Composable
fun PocketSheet(list: List<Handed>, close: () -> Unit) {
    val c = LocalContext.current.container
    val ru = ru()
    val s = c.state ?: return
    val r = c.round ?: return
    var i by remember { mutableIntStateOf(0) }
    val h = list.getOrNull(i) ?: run { close(); return }
    val house = r.byId[h.house]
    Eyebrow(w(ru, "вручено по дороге · ${i + 1} из ${list.size}", "delivered on the way · ${i + 1} of ${list.size}"))
    if (house != null) {
        DoorPicture(c, house)
        val res = c.game.resident(s, house)
        Text(res.name(ru), style = T.Title, modifier = Modifier.padding(top = 10.dp))
        Text(house.addr(ru), style = T.Small)
    }
    h.items.forEach { ShownBlock(c.game.show(s, it), ru, c) }
    Spacer(Modifier.height(12.dp))
    PixelButton(if (i + 1 < list.size) w(ru, "Следующий дом", "Next house") else w(ru, "Готово", "Done"), {
        h.seen = true; c.changed()
        if (i + 1 < list.size) i++ else close()
    }, Modifier.fillMaxWidth(), Kind.LAMP)
}

/** "8 ноября" / "November 8". */
fun dayName(day: Long, ru: Boolean): String {
    val d = java.time.LocalDate.ofEpochDay(day)
    val months = if (ru) listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    else listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    return if (ru) "${d.dayOfMonth} ${months[d.monthValue - 1]}" else "${months[d.monthValue - 1]} ${d.dayOfMonth}"
}

/** The days the journal holds, oldest first; today is always there. */
private fun journalDays(s: GameState): List<Long> = (s.delivered.map { it.day } + s.day).filter { it > s.day - GameState.JOURNAL_DAYS }.distinct().sorted()

/**
 * What was handed over in a day, house by house, with what each piece said and what was said at the door.
 * With [from] > 0 it is the summary of the round that began then: only what was handed since.
 */
@Composable
fun DeliveredSheet(day0: Long, from: Long, nav: Nav, close: () -> Unit) {
    val c = LocalContext.current.container
    val ru = ru()
    val s = c.state ?: return
    val r = c.round ?: return
    var day by remember { mutableLongStateOf(day0) }
    var open by remember { mutableStateOf<Int?>(null) }
    val round = from > 0
    val list = s.delivered.filter { if (round) it.at >= from else it.day == day }
    val items = list.flatMap { it.items }
    if (round) {
        Eyebrow(w(ru, "обход окончен", "the round is over"))
        Text(w(ru, "Что вы разнесли", "What you delivered"), style = T.Title, modifier = Modifier.padding(top = 2.dp))
    } else {
        Eyebrow(w(ru, "журнал вручений", "the delivery log"))
        val days = journalDays(s)
        val i = days.indexOf(day)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            PixelButton("<", { if (i > 0) { day = days[i - 1]; open = null } }, kind = Kind.GHOST, enabled = i > 0)
            Text(when (s.day - day) { 0L -> w(ru, "Сегодня", "Today"); 1L -> w(ru, "Вчера", "Yesterday"); else -> dayName(day, ru) },
                style = T.Title, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            PixelButton(">", { if (i in 0 until days.size - 1) { day = days[i + 1]; open = null } }, kind = Kind.GHOST, enabled = i in 0 until days.size - 1)
        }
    }
    // the summary: how much, how far, what kinds
    val n = items.count { it.kind != "pickup" }
    val houses = list.map { it.house }.filter { it != "post" }.distinct().size
    val walked = if (round) (s.kmToday - c.roundKm).takeIf { s.day == day0 }?.coerceAtLeast(0.0) else s.log.firstOrNull { it.day == day }?.walked
    val stamps = if (round) list.mapNotNull { h -> r.byId[h.house]?.street }.distinct().count { st -> s.streets[st] == day0 && s.delivered.none { o -> o.at < from && r.byId[o.house]?.street == st } }
                 else s.log.firstOrNull { it.day == day }?.streets ?: 0
    if (list.isEmpty()) {
        Text(if (round) w(ru, "За этот обход ничего не вручено.", "Nothing was delivered on this round.") else w(ru, "В этот день ничего не вручали.", "Nothing was delivered that day."),
            style = T.Body, modifier = Modifier.padding(top = 10.dp))
        walked?.takeIf { it > .05 }?.let { Text(w(ru, "Пройдено ", "Walked ") + km(it, ru) + ".", style = T.Small) }
    } else {
        Text(w(ru, "Вручено ${countItems(n, true)}", "${countItems(n, false).replaceFirstChar { it.uppercase() }} delivered") +
            (if (houses > 0) w(ru, " в $houses ${plural(houses, "дом", "дома", "домов")}", " to $houses house" + if (houses == 1) "" else "s") else "") +
            (walked?.takeIf { it > .05 }?.let { w(ru, ", пройдено ", ", ") + km(it, ru) + w(ru, "", " walked") } ?: "") +
            (if (stamps > 0) w(ru, ", новых марок: $stamps", ", $stamps new stamp" + if (stamps == 1) "" else "s") else "") + ".",
            style = T.Body, modifier = Modifier.padding(top = 8.dp))
        val kinds = items.groupingBy { kindName(it.kind, ru) }.eachCount()
        Text(kinds.entries.joinToString(", ") { "${it.key} ×${it.value}" }, style = T.Small)
        val replies = items.count { it.arc.isNotEmpty() && it.kind != "out" }
        if (replies > 0) Text(w(ru, "Из них в переписках: $replies.", "Of them, in correspondences: $replies."), style = T.Small)
        Text(w(ru, "Нажмите на дом — покажу, что было в письмах и что сказали.", "Tap a house to see what the mail said and what was said at the door."),
            style = T.Small.copy(color = C.muted), modifier = Modifier.padding(top = 6.dp))
    }
    list.forEachIndexed { k, h ->
        val house = r.byId[h.house]
        val post = h.house == "post"
        val expanded = open == k
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).pixelBox(if (expanded) C.paper else C.light, bevel = null).clickable { open = if (expanded) null else k }.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val title = when { post -> w(ru, "Почта", "The post office"); house != null -> house.addr(ru); else -> "—" }
                    Text((if (h.at > 0) hhmm(h.at) + " · " else "") + title, style = T.Body, maxLines = 1)
                    val who = when { post -> w(ru, "Галина, отправка посылок", "Galina, parcels out"); house != null -> c.game.resident(s, house).let { it.name(ru) + " · " + it.role(ru) }; else -> "" }
                    if (who.isNotEmpty()) Text(who, style = T.Small, maxLines = 1)
                }
                for (it in h.items.distinctBy { it.kind }) BufImage(MailIcons.of(it.kind), Modifier.padding(start = 4.dp), 1.5.dp)
            }
            if (expanded) {
                h.items.forEach { ShownBlock(c.game.show(s, it, h.late(it)), ru, c) }
                if (house != null) PixelButton(w(ru, "На карте", "On the map"), { close(); nav.show(house.id) }, Modifier.padding(top = 8.dp), Kind.GHOST)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    val pocket = s.handed.filter { !it.seen }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pocket.isNotEmpty()) PixelButton(w(ru, "По порядку · ${pocket.size}", "One by one · ${pocket.size}"), { nav.open(Sheet.Pocket(pocket)) }, Modifier.weight(1f), Kind.GHOST)
        PixelButton(w(ru, "Закрыть", "Close"), close, Modifier.weight(1f), Kind.LAMP)
    }
}

@Composable
fun LetterSheet(m: Mine, close: () -> Unit) {
    val c = LocalContext.current.container
    val ru = ru()
    val (from, text, gift) = c.game.mineText(m)
    if (!m.read) { m.read = true; c.changed() }
    Eyebrow(w(ru, "до востребования · почтальону", "poste restante · to the postman"))
    Text(text(ru), style = T.Letter, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).pixelBox(C.paper, bevel = null).padding(14.dp))
    Text("— " + from(ru), style = T.Hand, modifier = Modifier.padding(top = 6.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    if (!gift.blank) Text(w(ru, "Приложено: ", "Enclosed: ") + gift(ru), style = T.Body.copy(color = C.ochreDeep), modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(12.dp))
    PixelButton(w(ru, "Сложить в шкатулку", "Put it away"), close, Modifier.fillMaxWidth(), Kind.LAMP)
}

@Composable
fun StampSheet(street: String, nav: Nav, close: () -> Unit) {
    val c = LocalContext.current.container
    val ru = ru()
    val s = c.state ?: return
    val r = c.round ?: return
    val got = street in s.streets
    Row(verticalAlignment = Alignment.CenterVertically) {
        BufImage(stampOf(street, r.motif[street] ?: "houses", got, c.now().month, 0xFFFBF3DC.toInt()), scale = 4.dp)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(if (got) street else w(ru, "Марка ещё не ваша", "Not yours yet"), style = T.Heading)
            val houses = r.houses.filter { it.street == street }
            val been = houses.count { it.id in s.visited }
            Text(w(ru, "Домов с адресом: ${houses.size}\nВы были у $been", "Houses with numbers: ${houses.size}\nYou've called at $been"), style = T.Small, modifier = Modifier.padding(top = 4.dp))
            s.streets[street]?.let { d -> Text(w(ru, "Первое вручение: ", "First delivery: ") + java.time.LocalDate.ofEpochDay(d).toString(), style = T.Small) }
        }
    }
    if (!got) Text(w(ru, "Марка приходит с первым вручением на этой улице. Улица — «$street».", "The stamp comes with your first delivery on this street — $street."), style = T.Body, modifier = Modifier.padding(top = 10.dp))
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val first = r.houses.firstOrNull { it.street == street }
        if (first != null) PixelButton(w(ru, "На карте", "On the map"), { close(); nav.show(first.id) }, Modifier.weight(1f), Kind.GHOST)
        PixelButton(w(ru, "Закрыть", "Close"), close, Modifier.weight(1f), Kind.LAMP)
    }
}

@Suppress("unused") private val keepWidth = Modifier.width(1.dp)

/** A line said at the door: a dash before speech, none before a stage direction or a quote. */
fun said(t: String): String = if (t.startsWith("—") || t.startsWith("«") || t.startsWith("\"") || "«" in t || "\"" in t) t else "— $t"

/** What the icons and marks on the map mean. */
@Composable
fun LegendSheet(close: () -> Unit) {
    val ru = ru()
    Text(w(ru, "Что означают значки", "What the icons mean"), style = T.Title)
    for (k in listOf("letter", "card", "bill", "mag", "parcel", "paper", "telegram", "chess", "riddle", "pickup", "out")) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BufImage(MailIcons.of(k), Modifier.padding(end = 12.dp), 3.dp)
            Column(Modifier.weight(1f)) {
                Text(when (k) { "bill" -> w(ru, "Казённое письмо", "Official letter"); "chess" -> w(ru, "Шахматный ход", "A chess move"); else -> kindName(k, ru).replaceFirstChar { it.uppercase() } }, style = T.Body)
                Text(kindHint(k, ru), style = T.Small)
            }
        }
    }
    Text(w(ru, "на карте", "on the map"), style = T.Eyebrow, modifier = Modifier.padding(top = 14.dp))
    for ((mark, text) in listOf(
        w(ru, "Бирка над домом", "A tag over a house") to w(ru, "для этого дома есть почта; цифра — сколько отправлений. Нажмите — покажу, что именно.", "there's mail for this house; the number is how many items. Tap it to see what."),
        w(ru, "Золотой контур", "A golden outline") to w(ru, "дом с почтой; красный контур — телеграмма.", "a house with mail; a red outline means a telegram."),
        w(ru, "Красная нить", "A red thread") to w(ru, "эти два дома переписываются через вас.", "these two houses write to each other through you."),
        w(ru, "Точки от почтальона", "Dots from the postman") to w(ru, "маршрут на сегодня, ближайший дом — яркими точками.", "today's route; the nearest house in bright dots."),
        w(ru, "Пунктирный круг", "A dotted ring") to w(ru, "30 метров: в этих пределах письмо можно вручить.", "30 metres: within it, mail can be handed over."),
        w(ru, "Стрелка у края", "An arrow at the edge") to w(ru, "почта за пределами экрана: куда идти и сколько метров.", "mail beyond the screen: which way and how far."),
    )) {
        Text(mark, style = T.Body, modifier = Modifier.padding(top = 8.dp))
        Text(text, style = T.Small)
    }
    Spacer(Modifier.height(12.dp))
    PixelButton(w(ru, "Понятно", "Got it"), close, Modifier.fillMaxWidth(), Kind.LAMP)
}
