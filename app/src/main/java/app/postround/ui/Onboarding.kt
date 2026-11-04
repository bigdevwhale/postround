package app.postround.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.postround.container
import app.postround.domain.Sky
import app.postround.ui.art.Buf
import app.postround.ui.art.DoorScene
import app.postround.ui.art.G
import app.postround.ui.art.Look
import app.postround.ui.art.MailIcons
import app.postround.ui.art.Moment
import app.postround.ui.art.PixelView
import app.postround.ui.art.R
import app.postround.ui.art.Scene
import app.postround.ui.art.fbm
import app.postround.ui.art.hash
import app.postround.ui.art.postman
import app.postround.ui.art.rp
import app.postround.ui.components.BufImage
import app.postround.ui.components.Kind
import app.postround.ui.components.PixelButton
import app.postround.ui.components.pixelBox
import app.postround.ui.theme.C
import app.postround.ui.theme.T
import app.postround.work.Tracker
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** A strip of pavement: the postman walks past three doors; at the one with a letter the ring of 30 m lights up. */
private class PavementScene : Scene(128, 56) {
    override val key get() = "pavement"
    override fun base(b: Buf) {
        for (y in 0 until h) for (x in 0 until w) {
            val c = when {
                y < 26 -> rp(listOf(G.ochre, G.salmon, G.mint)[(x / 43).coerceAtMost(2)], .55 + (fbm(x * .2, y * .2) - .5) * .15, x, y)
                y < 28 -> rp(G.granite, .85, x, y)
                else -> rp(G.granite, .62 + (fbm(x * .3, y * .3) - .5) * .12 - if (x % 7 == 0 || y % 5 == 0) .1 else 0.0, x, y)
            }
            b.p(x, y, c)
        }
        for (k in 0 until 3) { val dx = 14 + k * 43; b.r(dx, 8, 14, 18, R.INK); b.r(dx + 1, 9, 12, 17, listOf(G.bottle, R.wood, G.navy)[k][3]); b.p(dx + 10, 18, R.brass[4]) }
        for (k in 0 until 3) { val px = 36 + k * 43; b.r(px, 6, 3, 4, R.blue[4]) }
    }

    override fun anim(b: Buf, t: Int) {
        val x = (t * 2) % (w + 20) - 10
        val door = 64 + 7
        val near = kotlin.math.abs(x - door) < 22
        if (near) for (a in 0 until 48 step 2) { val an = a / 48.0 * Math.PI * 2; b.p((door + Math.cos(an) * 20).toInt(), (42 + Math.sin(an) * 9).toInt(), R.honey[5]) }
        val env = MailIcons.of("letter")
        for (j in 0 until env.h) for (i in 0 until env.w) { val c = env.d[j * env.w + i]; if (c != 0) b.p(door - 6 + i, 1 + j - if ((t / 4) % 2 == 0) 0 else 1, c) }
        b.postman(x, 50, (t / 2) % 2, false)
        if (near) b.p(x + 5, 40, R.cream[5])
    }
}

@Composable
fun Onboarding() {
    val ctx = LocalContext.current
    val c = ctx.container
    val ru = ru()
    var page by remember { mutableIntStateOf(0) }
    val here by c.here.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf("") }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r[Manifest.permission.ACCESS_FINE_LOCATION] == true || r[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) Tracker.of(ctx).start()
    }
    LaunchedEffect(granted) { if (granted) Tracker.of(ctx).start() }
    val now = LocalDateTime.now()
    val m = Moment(now.hour * 60 + now.minute, Sky(sun = true), now.monthValue, now.toLocalDate().toEpochDay())
    val pages = 4
    if (busy) { Box(Modifier.fillMaxSize().background(C.paper).statusBarsPadding(), contentAlignment = Alignment.Center) { Loading() }; return }

    Column(Modifier.fillMaxSize().background(C.paper).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            when (page) {
                0 -> {
                    Text(w(ru, "До востребования", "Poste Restante"), style = T.Display.copy(color = C.green))
                    Text(w(ru, "почтальон своего района", "the postman of your own neighbourhood"), style = T.Eyebrow)
                    Spacer(Modifier.height(10.dp))
                    PixelView(remember { DoorScene("post", G.sky2, "", Look.of(mapOf("hair" to "wood", "cloth" to "roofB", "bun" to "1", "glasses" to "1")), m.copy(minute = 9 * 60), 7) },
                        Modifier.fillMaxWidth().pixelBox(Color.Transparent).padding(2.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(w(ru,
                        "Ваш район — ваш почтовый участок. Улицы, дома и номера на них — настоящие, с карты OpenStreetMap. А живут в домах выдуманные люди.\n\nКаждое утро в отделении вас ждёт сумка: письма, открытки, газеты, посылки. Разносите их, когда гуляете, идёте в магазин или на работу.",
                        "Your neighbourhood is your round. The streets, the houses and their numbers are real, from the OpenStreetMap map. The people who live in them are made up.\n\nEvery morning a bag waits for you at the post office: letters, postcards, papers, parcels. Deliver them as you walk, go to the shops or to work."), style = T.Body)
                }
                1 -> {
                    Text(w(ru, "Как вручают письма", "How mail is delivered"), style = T.Title)
                    Spacer(Modifier.height(10.dp))
                    PixelView(remember { PavementScene() }, Modifier.fillMaxWidth().pixelBox(Color.Transparent).padding(2.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(w(ru,
                        "· Письмо вручается, только когда вы стоите в 30 метрах от дома. Так решает GPS.\n· Нажмите «Выйти на обход» — и телефон можно убрать в карман: у дверей почта вручится сама, а что сказали жильцы, прочтёте потом.\n· Телеграммы приходят по дороге и ждут недолго.\n· Из трамвая или машины не вручить: почтальон ходит пешком.",
                        "· Mail is handed over only when you stand within 30 metres of the house. The GPS decides.\n· Tap 'Start the round' and you can pocket the phone: mail is handed over at the doors, and you read what people said later.\n· Telegrams come while you walk and don't wait long.\n· Nothing is delivered from a tram or a car: a postman walks."), style = T.Body)
                }
                2 -> {
                    Text(w(ru, "Письма между домами", "Letters between houses"), style = T.Title)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { for (k in listOf("letter", "card", "telegram", "parcel", "chess", "riddle")) BufImage(MailIcons.of(k), scale = 3.dp) }
                    Spacer(Modifier.height(12.dp))
                    Text(w(ru,
                        "Жильцы пишут друг другу: двое стариков, познакомившихся на катке в пятьдесят втором, шахматная партия по переписке, бабушка и внучка. Вы носите письма туда и обратно — и между домами на карте натягивается красная нить.\n\nА ещё приходят письма без адреса: «Жильцам жёлтого дома в пять этажей, у аптеки». Найдите его.\n\nУ каждой улицы — своя марка. В окошке «До востребования» — письма вам.",
                        "People write to each other: two old people who met on a skating rink in 1952, a game of chess by post, a grandmother and her granddaughter. You carry the letters back and forth, and a red thread is drawn between their houses on the map.\n\nAnd there are letters with no address: 'To the people of the yellow house, five storeys high, by the chemist's.' Find it.\n\nEvery street has its stamp. In the poste restante window, letters for you."), style = T.Body)
                    Spacer(Modifier.height(14.dp))
                    Text(w(ru, "Что нужно игре", "What the game needs"), style = T.Heading)
                    Text(w(ru,
                        "Геолокация — чтобы знать, у какого дома вы стоите. Физическая активность — шагомер телефона: по шагам собирается утренняя сумка. Уведомления — для обхода и телеграмм. Координаты никуда не уходят, кроме запросов карты (OpenStreetMap) и погоды (MET Norway) для вашего района.",
                        "Location — to know which house you're standing at. Physical activity — the phone's step counter: the morning bag follows your steps. Notifications — for the round and the telegrams. Your location goes nowhere except the map (OpenStreetMap) and weather (MET Norway) requests for your area."), style = T.Small)
                    Spacer(Modifier.height(10.dp))
                    if (granted) Text(w(ru, "Геолокация разрешена.", "Location is allowed."), style = T.Body.copy(color = C.green))
                    else PixelButton(w(ru, "Разрешить", "Allow"), {
                        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                        if (Build.VERSION.SDK_INT >= 29) perms += Manifest.permission.ACTIVITY_RECOGNITION
                        ask.launch(perms.toTypedArray())
                    }, Modifier.fillMaxWidth(), Kind.LAMP)
                }
                else -> {
                    Text(w(ru, "Где ваш дом?", "Where is home?"), style = T.Title)
                    Spacer(Modifier.height(12.dp))
                    Text(w(ru,
                        "Встаньте у своей парадной или калитки и нажмите кнопку. Вокруг нарисуется участок — примерно полтора на полтора километра, с номерами домов. Это займёт меньше минуты.\n\nСначала участок небольшой, метров триста вокруг дома. Чем больше писем вы разнесёте, тем дальше он раздвинется.",
                        "Stand by your front door or gate and tap the button. The round will be drawn around it — about a mile across, with the house numbers. It takes less than a minute.\n\nAt first the round is small, three hundred metres around home. The more you deliver, the further it grows."), style = T.Body)
                    Spacer(Modifier.height(16.dp))
                    when {
                        !granted -> Text(w(ru, "Сначала разрешите геолокацию — на прошлой странице.", "Allow location first — on the previous page."), style = T.Body.copy(color = C.red))
                        here == null -> Text(w(ru, "Жду, пока телефон найдёт, где вы… На улице это быстрее.", "Waiting for the phone to find where you are… It's quicker outdoors."), style = T.Body)
                        else -> PixelButton(w(ru, "Здесь мой дом", "My home is here"), {
                            busy = true; error = false
                            scope.launch { runCatching { c.setHome(here!!) }.onFailure { error = true; why = it.message ?: it.javaClass.simpleName }; busy = false }
                        }, Modifier.fillMaxWidth(), Kind.LAMP)
                    }
                    if (error) {
                        Text(w(ru, "Сервер карты не ответил — он бесплатный и иногда занят. Нажмите ещё раз.", "The map server didn't answer — it's free and sometimes busy. Tap again."), style = T.Small.copy(color = C.red), modifier = Modifier.padding(top = 8.dp))
                        if (why.isNotBlank()) Text(why, style = T.Small, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(w(ru, "Карта © участники OpenStreetMap. Погода — MET Norway.", "Map © OpenStreetMap contributors. Weather by MET Norway."), style = T.Small)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page > 0) PixelButton(w(ru, "Назад", "Back"), { page-- }, kind = Kind.GHOST) else Spacer(Modifier.size(1.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                for (i in 0 until pages) Box(Modifier.padding(3.dp).size(8.dp).background(if (i == page) C.green else C.paper2))
            }
            if (page < pages - 1) PixelButton(w(ru, "Дальше", "Next"), { page++ }) else Spacer(Modifier.size(1.dp))
        }
    }
}

@Suppress("unused") private fun unusedHash() = hash(1, 2)
