package app.postround.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.postround.MainActivity
import app.postround.R
import app.postround.container
import app.postround.domain.Geo
import kotlinx.coroutines.flow.MutableStateFlow

/** Listens to the GPS while the app is open or the round is on, and feeds every fix to the game. */
class Tracker(private val context: Context) : LocationListener {
    private val lm = context.getSystemService(LocationManager::class.java)
    private var users = 0

    val allowed get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start() {
        if (!allowed) return
        if (users++ > 0) return
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) {
                runCatching { lm.requestLocationUpdates(p, 4000L, 4f, this, Looper.getMainLooper()) }
                runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { onLocationChanged(it) }
            }
        }
    }

    @Synchronized
    fun stop() {
        if (users == 0) return
        if (--users == 0) runCatching { lm.removeUpdates(this) }
    }

    override fun onLocationChanged(l: Location) {
        // the demo lives on Petrogradka: the real position would put the postman off its map (long-press moves instead)
        if (app.postround.BuildConfig.DEMO) return
        context.container.fix(Geo(l.latitude, l.longitude), if (l.hasAccuracy()) l.accuracy else 30f)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    companion object {
        @Volatile private var one: Tracker? = null
        fun of(c: Context) = one ?: synchronized(this) { one ?: Tracker(c.applicationContext).also { one = it } }
    }
}

/**
 * The round: the GPS keeps handing mail over with the phone in a pocket. Android lets an app hear the location
 * in the background only as a foreground service, so the round shows a quiet notification until it ends.
 */
class WalkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        val ok = runCatching { ServiceCompat.startForeground(this, ID, note(), type) }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        if (!running) { running = true; walking.value = true; Tracker.of(this).start() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running) { running = false; walking.value = false; Tracker.of(this).stop() }
        super.onDestroy()
    }

    private fun note(): Notification {
        val m = getSystemService(NotificationManager::class.java)
        if (m.getNotificationChannel(CHANNEL) == null) m.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_walk), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, WalkService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.walk_title))
            .setContentText(getString(R.string.walk_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.walk_stop), stop)
            .setOngoing(true).setSilent(true)
            .build()
    }

    companion object {
        const val CHANNEL = "walk"
        const val ID = 7
        const val STOP = "stop"
        @Volatile var running = false
        val walking = MutableStateFlow(false)
        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, WalkService::class.java))
        fun stop(c: Context) = c.startService(Intent(c, WalkService::class.java).setAction(STOP))
    }
}
