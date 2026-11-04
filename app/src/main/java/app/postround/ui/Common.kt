package app.postround.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.postround.AppContainer
import app.postround.domain.Item
import app.postround.domain.Tx
import app.postround.ui.art.Moment
import app.postround.ui.theme.C
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun ru(): Boolean = LocalConfiguration.current.locales[0].language == "ru"
fun w(ru: Boolean, a: String, b: String) = if (ru) a else b

/** Russian plural: 1 письмо, 2 письма, 5 писем. */
fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10; val m100 = n % 100
    return when { m10 == 1 && m100 != 11 -> one; m10 in 2..4 && m100 !in 12..14 -> few; else -> many }
}

/** "3 письма" / "3 items". */
fun countItems(n: Int, ru: Boolean) = if (ru) "$n " + plural(n, "отправление", "отправления", "отправлений") else "$n item" + if (n == 1) "" else "s"

/** A word on what each kind of mail is and what to do with it. */
fun kindHint(kind: String, ru: Boolean): String = when (kind) {
    "letter" -> w(ru, "личное, часто часть переписки: ответ понесёте следом", "personal, often part of a correspondence: you'll carry the reply")
    "chess" -> w(ru, "ход в шахматной партии по почте", "a move in a game of chess by post")
    "card" -> w(ru, "открытая, её текст видно; прочтут при вас", "open, the text shows; they'll read it in front of you")
    "bill" -> w(ru, "казённое: счёт, извещение, приглашение", "official: a bill, a notice, an invitation")
    "mag" -> w(ru, "по подписке, раз в месяц", "by subscription, once a month")
    "parcel" -> w(ru, "тяжёлая, занимает 3 места в сумке", "heavy, takes 3 places in the bag")
    "paper" -> w(ru, "подписчику; к утру устаревает", "for a subscriber; stale by morning")
    "telegram" -> w(ru, "срочно! вручить до указанного времени", "urgent! deliver by the time on it")
    "riddle" -> w(ru, "без номера и улицы: дом ищут по приметам и расспросам", "no number, no street: find the house by its signs and by asking")
    "pickup" -> w(ru, "зайти к жильцу: он передаст посылку на отправку", "call on them: they'll give you a parcel to send")
    "out" -> w(ru, "отнесите на почту", "take it to the post office")
    else -> ""
}

fun kindName(kind: String, ru: Boolean): String = when (kind) {
    "letter", "chess", "bill" -> w(ru, "письмо", "letter")
    "card" -> w(ru, "открытка", "postcard")
    "mag" -> w(ru, "журнал", "magazine")
    "parcel" -> w(ru, "посылка", "parcel")
    "paper" -> w(ru, "газета", "newspaper")
    "telegram" -> w(ru, "телеграмма", "telegram")
    "riddle" -> w(ru, "письмо без адреса", "letter with no address")
    "pickup" -> w(ru, "забрать посылку", "parcel to collect")
    "out" -> w(ru, "на отправку", "to send")
    else -> kind
}

/** Metres as "240 м" or "1,2 км". */
fun dist(m: Double, ru: Boolean): String = if (m < 1000) "${(m / 10).toInt() * 10} " + w(ru, "м", "m") else "%.1f ".format(m / 1000).let { if (ru) it.replace('.', ',') else it } + w(ru, "км", "km")

fun km(v: Double, ru: Boolean) = "%.1f".format(v).let { if (ru) it.replace('.', ',') else it } + w(ru, " км", " km")

fun hhmm(ms: Long): String { val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime(); return "%02d:%02d".format(t.hour, t.minute) }

fun moment(c: AppContainer): Moment {
    val n = c.now(); val t = LocalDateTime.now()
    return Moment(t.hour * 60 + t.minute, n.sky, n.month, n.day)
}

fun Tx.of(ru: Boolean) = this(ru)

/** A gift kept as "ru|en". */
fun giftText(g: String, ru: Boolean) = g.split("|").let { p -> if (ru) p[0] else p.getOrElse(1) { p[0] } }

/** Which way a point lies: an arrow drawn in pixels, rotated to the bearing (0 = east, counter-clockwise, screen y down). */
@Composable
fun DirArrow(angleDeg: Float, modifier: Modifier = Modifier, size: Dp = 18.dp, color: Color = C.ink) {
    Canvas(modifier.size(size)) {
        val p = this.size.width / 9
        rotate(-angleDeg) {
            // a solid arrow pointing right: a short shaft and a broad head, so it reads at any angle
            drawRect(color, Offset(1 * p, 3.6f * p), Size(4.4f * p, 1.8f * p))
            val head = androidx.compose.ui.graphics.Path().apply { moveTo(4.6f * p, 1f * p); lineTo(8.4f * p, 4.5f * p); lineTo(4.6f * p, 8f * p); close() }
            drawPath(head, color)
        }
    }
}

/** The telegram that's still in the bag, if any. */
fun telegramOf(items: List<Item>): Item? = items.firstOrNull { it.kind == "telegram" && it.arc.isEmpty() }
