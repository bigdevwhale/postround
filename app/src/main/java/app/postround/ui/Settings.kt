package app.postround.ui

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.postround.container
import kotlinx.coroutines.launch
import androidx.core.os.LocaleListCompat
import app.postround.ui.art.Buf
import app.postround.ui.components.Kind
import app.postround.ui.components.Panel
import app.postround.ui.components.PixelButton
import app.postround.ui.theme.C
import app.postround.ui.theme.T

/** The StillCraft bot: deep links file a bug or an idea for this app, or follow its releases. */
object Community {
    private const val BOT = "https://t.me/ArcaneWhisperBot?start="
    const val SLUG = "postround"
    const val BUG = "${BOT}bug_$SLUG"
    const val IDEA = "${BOT}wish_$SLUG"
    const val FOLLOW = "${BOT}f_$SLUG"
    const val CHAT = "https://t.me/+Q4gFEDk8O842OGVi"
}

/** A small brass gear for the top line. */
val gearIcon: Buf by lazy {
    val b = Buf(11, 11)
    val ink = 0xFF2B1A14.toInt(); val brass = 0xFFF0C24A.toInt(); val lit = 0xFFFCE9A0.toInt()
    for (y in 0 until 11) for (x in 0 until 11) {
        val dx = x - 5.0; val dy = y - 5.0; val r = Math.hypot(dx, dy)
        val a = Math.atan2(dy, dx)
        val tooth = Math.cos(a * 8) > .25
        if (r <= 2.6 && r > 1.2 || r <= 3.6 && r > 1.2 || (tooth && r <= 5.2 && r > 1.2)) b.p(x, y, if (dx + dy < -1) lit else brass)
    }
    for (y in 0 until 11) for (x in 0 until 11) if (b.get(x, y) == 0) {
        val n = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any { (i, j) -> i in 0..10 && j in 0..10 && b.get(i, j).let { it == brass || it == lit } }
        if (n) b.p(x, y, ink)
    }
    b
}

@Composable
fun SettingsView() {
    val ru = ru()
    val ctx = LocalContext.current
    fun open(url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(w(ru, "Настройки", "Settings"), style = T.Title)

        Text(w(ru, "язык", "language"), style = T.Eyebrow)
        val cur = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').substringBefore(',')
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((tag, label) in listOf("" to w(ru, "Как в системе", "System"), "ru" to "Русский", "en" to "English")) {
                PixelButton(label, {
                    AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
                }, Modifier.weight(1f), kind = if (cur == tag) Kind.PRIMARY else Kind.GHOST)
            }
        }

        HealthSection()

        Text(w(ru, "сообщество", "community"), style = T.Eyebrow, modifier = Modifier.padding(top = 6.dp))
        Text(w(ru, "Игра делается вместе с теми, кто в неё играет. Всё идёт через бота StillCraft в Telegram.",
            "The game is made together with the people who play it. It all goes through the StillCraft bot on Telegram."), style = T.Small)
        for ((icon, label, url) in listOf(
            Triple("🐞", w(ru, "Сообщить об ошибке", "Report a bug"), Community.BUG),
            Triple("💡", w(ru, "Предложить идею", "Suggest an idea"), Community.IDEA),
            Triple("🔔", w(ru, "Подписаться на обновления", "Follow updates"), Community.FOLLOW),
            Triple("💬", w(ru, "Чат StillCraft", "StillCraft chat"), Community.CHAT),
        )) {
            Panel(Modifier.fillMaxWidth(), fill = C.paper, onClick = { open(url) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, style = T.Heading, modifier = Modifier.width(34.dp))
                    Text(label, style = T.Body, modifier = Modifier.weight(1f))
                    Text("›", style = T.Heading.copy(color = C.green))
                }
            }
        }

        Text(w(ru, "об игре", "about"), style = T.Eyebrow, modifier = Modifier.padding(top = 6.dp))
        Text(w(ru,
            "Карта района — © участники OpenStreetMap, тайлы OpenFreeMap. Погода — MET Norway (yr.no).\nРисунки, письма и жильцы сделаны для «До востребования».",
            "District map © OpenStreetMap contributors, tiles by OpenFreeMap. Weather by MET Norway (yr.no).\nPictures, letters and residents were made for Poste Restante."), style = T.Small)
    }
}

/** Where the steps come from, as in Lipovka: the phone's own step counter, or Health Connect (health apps, watches, bands). */
@Composable
private fun HealthSection() {
    val ru = ru()
    val ctx = LocalContext.current
    val c = ctx.container
    val s = c.state ?: return
    var health by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(s.healthFrom >= 0) }
    var allowed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    var sensorOk by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(c.sensor.allowed) }
    var note by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { allowed = c.health.allowed() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun usePhone() {
        s.healthFrom = -1; s.counter = -1; health = false; note = null; c.changed()
        scope.launch { c.readSensor() }
    }
    fun useHealth() {
        s.healthFrom = java.time.LocalDate.now().minusDays(7).toEpochDay(); health = true; note = null; c.changed()
        scope.launch { c.syncHealth() }
    }
    val askSensor = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        sensorOk = ok
        if (ok) usePhone() else note = w(ru, "Без разрешения «Физическая активность» шагомер телефона не читается.", "Without the Physical activity permission the phone's step counter can't be read.")
    }
    val askHealth = androidx.activity.compose.rememberLauncherForActivityResult(app.postround.work.HealthSteps.requestContract()) { got ->
        allowed = app.postround.work.HealthSteps.READ in got
        if (allowed == true) useHealth() else note = w(ru, "Health Connect не дал доступ к шагам.", "Health Connect didn't allow the steps.")
    }
    fun pickPhone() {
        when {
            !c.sensor.present -> note = w(ru, "В этом телефоне нет шагомера. Подойдёт Health Connect.", "This phone has no step counter. Health Connect will do.")
            !c.sensor.allowed && android.os.Build.VERSION.SDK_INT >= 29 -> askSensor.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
            else -> usePhone()
        }
    }
    fun pickHealth() {
        when {
            c.health.available -> askHealth.launch(setOf(app.postround.work.HealthSteps.READ))
            c.health.installable -> { note = w(ru, "Сначала установите Health Connect.", "Install Health Connect first."); runCatching { ctx.startActivity(c.health.storeIntent()) } }
            else -> note = w(ru, "Health Connect на этом телефоне недоступен.", "Health Connect isn't available on this phone.")
        }
    }
    Text(w(ru, "шаги", "steps"), style = T.Eyebrow, modifier = Modifier.padding(top = 6.dp))
    Text(w(ru,
        "Утренняя сумка — по тому, сколько вы на самом деле ходите, даже в дни без обхода. Шаги считает шагомер телефона или Health Connect (Mi Fitness, Samsung Health, Google Fit, часы, браслеты). Письма всё равно вручаются только у двери, по GPS.",
        "The morning bag follows how much you really walk, even on days without the round. Steps come from the phone's own counter or from Health Connect (Mi Fitness, Samsung Health, Google Fit, watches, bands). Mail is still handed over only at the door, by GPS."), style = T.Small)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelButton(w(ru, "Шагомер телефона", "Phone's counter"), { pickPhone() }, Modifier.weight(1f), kind = if (!health) Kind.PRIMARY else Kind.GHOST)
        PixelButton("Health Connect", { pickHealth() }, Modifier.weight(1f), kind = if (health) Kind.PRIMARY else Kind.GHOST)
    }
    val today = s.log.firstOrNull { it.day == java.time.LocalDate.now().toEpochDay() }?.steps ?: -1
    val bad = note != null || (health && allowed == false) || (!health && (!sensorOk || !c.sensor.present))
    val status = note ?: when {
        health && allowed == false -> w(ru, "Доступ к шагам закрыт в Health Connect. Нажмите «Health Connect», чтобы открыть.", "Access to steps is closed in Health Connect. Tap Health Connect to open it.")
        health -> w(ru, "Шаги берутся из Health Connect.", "Steps come from Health Connect.")
        !c.sensor.present -> w(ru, "В этом телефоне нет шагомера. Подойдёт Health Connect.", "This phone has no step counter. Health Connect will do.")
        !sensorOk -> w(ru, "Шагомеру нужно разрешение «Физическая активность». Нажмите «Шагомер телефона».", "The counter needs the Physical activity permission. Tap Phone's counter.")
        else -> w(ru, "Шаги считает сам телефон, даже когда игра закрыта.", "The phone counts the steps itself, even with the game closed.")
    }
    Text(status, style = T.Small.copy(color = if (bad) C.red else C.green))
    if (today >= 0) Text(w(ru, "Сегодня: %,d шагов", "Today: %,d steps").format(today).replace(',', ' '), style = T.Body.copy(color = C.green))
}
