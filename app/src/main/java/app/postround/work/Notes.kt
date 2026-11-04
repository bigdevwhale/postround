package app.postround.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.postround.AppContainer
import app.postround.MainActivity
import app.postround.R
import app.postround.domain.Item
import app.postround.domain.Result
import java.time.Instant
import java.time.ZoneId

/** Quiet notifications from the round: a telegram has come, mail was handed over in a pocket. */
object Notes {
    private const val CHANNEL = "mail"

    private fun builder(c: AppContainer): NotificationCompat.Builder? {
        val app = c.app
        val m = app.getSystemService(NotificationManager::class.java) ?: return null
        if (m.getNotificationChannel(CHANNEL) == null) m.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.channel_mail), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(app, 2, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground).setContentIntent(open).setAutoCancel(true)
    }

    private fun ru(c: AppContainer) = c.app.resources.configuration.locales[0].language == "ru"

    fun telegram(c: AppContainer, tg: Item): Pair<Int, Notification>? {
        val b = builder(c) ?: return null
        val ru = ru(c)
        val h = c.round?.byId?.get(tg.to) ?: return null
        val t = Instant.ofEpochMilli(tg.due).atZone(ZoneId.systemDefault()).toLocalTime()
        val by = "%02d:%02d".format(t.hour, t.minute)
        val text = if (ru) "Вручить до $by · ${h.addr(true)}" else "Deliver by $by · ${h.addr(false)}"
        return 301 to b.setContentTitle(if (ru) "Телеграмма!" else "A telegram!").setContentText(text).setPriority(NotificationCompat.PRIORITY_HIGH).build()
    }

    fun handed(c: AppContainer, r: Result): Pair<Int, Notification>? {
        val b = builder(c) ?: return null
        val ru = ru(c)
        val s = c.state ?: return null
        val who = c.game.resident(s, r.house)
        val n = r.shown.size
        val title = if (ru) "Вручено: ${r.house.addr(true)}" else "Delivered: ${r.house.addr(false)}"
        val text = (if (ru) "${who.name.ru} — ${plural(n)}. " else "${who.name.en} — $n item${if (n == 1) "" else "s"}. ") +
            (if (ru) "Что сказали — в сумке, когда откроете игру." else "What they said is waiting in the bag.")
        return (400 + (r.house.id.hashCode() and 63)) to b.setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setSilent(false).build()
    }

    private fun plural(n: Int): String {
        val m10 = n % 10; val m100 = n % 100
        return "$n " + when { m10 == 1 && m100 != 11 -> "отправление"; m10 in 2..4 && m100 !in 12..14 -> "отправления"; else -> "отправлений" }
    }
}
