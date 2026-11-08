package app.postround

import android.app.Application
import android.content.Context
import app.postround.data.Content
import app.postround.data.District
import app.postround.data.Store
import app.postround.data.Tiles
import app.postround.domain.Asked
import app.postround.domain.Forecast
import app.postround.domain.Game
import app.postround.domain.GameState
import app.postround.domain.Geo
import app.postround.domain.Handed
import app.postround.domain.Item
import app.postround.domain.Morning
import app.postround.domain.Now
import app.postround.domain.Result
import app.postround.domain.Round
import app.postround.domain.Shown
import app.postround.domain.Terrain
import app.postround.domain.Weather
import app.postround.domain.metres
import app.postround.work.Notes
import app.postround.work.HealthSteps
import app.postround.work.StepSensor
import app.postround.work.StepWorker
import app.postround.domain.DayLog
import app.postround.work.WalkService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/** Half the side of the district square around home, metres. */
const val DISTRICT_HALF = 800.0

/** Holds the game for the whole app: content, the player's state, the district and the weather. */
class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val content: Content = Content.read { path -> runCatching { app.assets.open("content/$path") }.getOrNull() }
    val game = Game(content)
    val store = Store(app)
    val health = HealthSteps(app)
    val sensor = StepSensor(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var state: GameState? = store.load()
        private set
    /** Bumped after every change so the screens redraw. */
    val version = MutableStateFlow(0)
    var district: District? = null
        private set
    var terrain: Terrain? = null
        private set
    var round: Round? = null
        private set
    var forecast: Forecast? = null
        private set
    /** The player's last known position. */
    val here = MutableStateFlow<Geo?>(null)
    /** A place the map should pan to. */
    val focus = MutableStateFlow<Geo?>(null)
    /** The house picked on the map or in the bag (its card shows under the map). */
    val selected = MutableStateFlow<String?>(null)
    /** What the district loading is doing now: download, parse, terrain, paint, or empty. */
    val stage = MutableStateFlow("")
    var morning: Morning? = null
    /** A telegram that has just arrived, for a banner. */
    val telegram = MutableStateFlow<Item?>(null)
    /** True while the phone moves faster than a walk: no handing over from a tram. */
    val riding = MutableStateFlow(false)
    /** True while the app is on screen: handing-over then waits for a tap; in a pocket it happens by itself. */
    @Volatile var visible = false
    /** When the current round began (wall-clock ms) and the day's distance at that moment, for the summary at its end. */
    @Volatile var roundFrom = 0L
    @Volatile var roundKm = 0.0

    private var lastFix: Geo? = null
    private var lastFixAt = 0L

    fun changed() {
        state?.let { store.save(it) }
        version.value++
    }

    fun now(at: LocalDateTime = LocalDateTime.now()): Now {
        val f = forecast
        val today = at.toLocalDate()
        val sky = f?.today ?: store.sky(today.toEpochDay()) ?: Weather.simulated(today, today.monthValue)
        val tomorrow = f?.tomorrow ?: Weather.simulated(today.plusDays(1), today.plusDays(1).monthValue)
        return Now(today, at.hour, sky, tomorrow, at.hour * 60 + at.minute, System.currentTimeMillis())
    }

    /** Loads the stored district. Heavy: call off the main thread. */
    fun loadDistrict() {
        val s = state ?: return
        val home = s.home ?: return
        val tiles = store.tiles()
        val json = if (tiles == null) store.district() ?: return else null
        val box = s.box ?: District.box(home.lat, home.lon, DISTRICT_HALF)
        stage.value = "parse"
        val d = if (tiles != null) Tiles.parse(tiles, box) else District.parse(json!!, box)
        stage.value = "terrain"
        val t = Terrain(d)
        district = d; terrain = t
        round = Round(d, t)
        android.util.Log.i("Postround", "district ${d.w}x${d.h}, ${d.addrs.size} addresses, ${round?.houses?.size} houses")
    }

    /** Takes a ready state (the demo) and the district box it was saved for. */
    fun adopt(s: GameState, box: DoubleArray) {
        state = s; s.box = box
        loadDistrict()
        store.save(s)
    }

    /** Sets home where the player stands and downloads the district around it. */
    suspend fun setHome(at: Geo) {
        val box = District.box(at.lat, at.lon, DISTRICT_HALF)
        stage.value = "download"
        val tiles = try { withContext(Dispatchers.IO) { Tiles.download(box) } } catch (e: Exception) { stage.value = ""; throw e }
        store.saveTiles(tiles)
        val s = state ?: game.newGame(at, now()).also { state = it }
        s.home = at; s.box = box
        loadDistrict()
        wake()
        changed()
    }

    /** Steps per day from Health Connect into the day log (the last two weeks), when it's connected. */
    suspend fun syncHealth() {
        val s = state ?: return
        if (s.healthFrom < 0 || !health.allowed()) return
        val today = LocalDate.now()
        val from = maxOf(LocalDate.ofEpochDay(s.healthFrom), today.minusDays(14))
        val days = health.days(from, today) ?: return
        synchronized(this) {
            for ((day, n) in days) {
                val log = s.log.firstOrNull { it.day == day } ?: DayLog(day).also { s.log += it; s.log.sortBy { l -> l.day }; while (s.log.size > 40) s.log.removeAt(0) }
                log.steps = n
            }
        }
        changed()
    }

    /**
     * The phone's step counter into today's steps, unless Health Connect is the source. The counter only grows
     * (from the last reboot), so what it gained since the last reading is today's walking.
     */
    suspend fun readSensor() {
        val s = state ?: return
        if (s.healthFrom >= 0) return
        val v = sensor.read(4_000) ?: return
        synchronized(this) {
            val today = LocalDate.now().toEpochDay()
            val log = s.log.firstOrNull { it.day == today } ?: DayLog(today).also { s.log += it; s.log.sortBy { l -> l.day }; while (s.log.size > 40) s.log.removeAt(0) }
            // smaller than before: the phone was restarted and the counter began again
            val gained = when { s.counter < 0 -> 0L; v >= s.counter -> v - s.counter; else -> v }
            // a gap over a missed midnight goes to today: it can't be split, and a day can only be under-counted
            if (gained in 0..60_000) log.steps = maxOf(0, log.steps) + gained
            s.counter = v
        }
        changed()
    }

    /** A new day: the morning bag. */
    @Synchronized
    fun wake() {
        val s = state ?: return
        val r = round ?: return
        val now = now()
        store.remember(now.day, now.sky)
        game.morning(s, r, now)?.let { m -> if (!m.fresh || s.bag.isNotEmpty()) morning = m }
        changed()
    }

    fun refreshWeather() {
        val h = state?.home ?: return
        if (forecast != null && System.currentTimeMillis() - forecast!!.at < 3 * 3600_000L) return
        scope.launch(Dispatchers.IO) {
            runCatching { Weather.fetch(h.lat, h.lon) }.getOrNull()?.let { f ->
                forecast = f; store.remember(LocalDate.now().toEpochDay(), f.today); version.value++
            }
        }
    }

    /** Where the player is on the map, in map pixels. */
    fun xy(at: Geo? = here.value): Pair<Float, Float>? { val d = district ?: return null; val g = at ?: return null; return d.x(g.lon) to d.y(g.lat) }

    /** Houses with mail within reach of the player. */
    fun reachable(at: Geo? = here.value): List<String> {
        val s = state ?: return emptyList(); val r = round ?: return emptyList()
        val (x, y) = xy(at) ?: return emptyList()
        if (riding.value) return emptyList()
        return s.bag.map { it.to }.distinct().filter { id -> r.byId[id]?.let { h -> game.inReach(r, h, x, y) } == true }
    }

    fun nearPost(at: Geo? = here.value): Boolean {
        val r = round ?: return false
        val (x, y) = xy(at) ?: return false
        val (px, py) = postXY() ?: return false
        return Math.hypot((x - px).toDouble(), (y - py).toDouble()) * r.d.mpp <= 35
    }

    /** The post office on the map: the real one, or home when the district has none. */
    fun postXY(): Pair<Float, Float>? {
        val r = round ?: return null
        if (r.realPost) return r.postX.toFloat() to r.postY.toFloat()
        val h = state?.home ?: return null
        return r.d.x(h.lon) to r.d.y(h.lat)
    }

    @Synchronized
    fun deliver(houseId: String): Result? {
        val s = state ?: return null; val r = round ?: return null
        val res = game.deliver(s, r, now(), houseId)
        changed()
        return res
    }

    @Synchronized
    fun ask(houseId: String): Asked? {
        val s = state ?: return null; val r = round ?: return null
        val a = game.ask(s, r, now(), houseId)
        changed()
        return a
    }

    @Synchronized
    fun post(): List<Shown> {
        val s = state ?: return emptyList(); val r = round ?: return emptyList()
        val out = game.post(s, r, now())
        changed()
        return out
    }

    /** A new position from the GPS: distance walked, a telegram maybe, mail handed over in a pocket. */
    @Synchronized
    fun fix(at: Geo, accuracy: Float) {
        here.value = at
        val s = state ?: return
        val r = round ?: return
        if (accuracy > 40f) return
        val now = now()
        if (s.day != now.day) { game.morning(s, r, now)?.let { morning = it } }
        val t = System.currentTimeMillis()
        lastFix?.let { prev ->
            val m = prev.metres(at)
            val secs = (t - lastFixAt) / 1000.0
            riding.value = secs > 0 && m / secs > 4.5 && m > 25
            if (m in 3.0..300.0 && !riding.value) {
                val (x, y) = xy(at) ?: return@let
                game.walked(s, r, now, m, x, y)?.let { tg -> telegram.value = tg; notify(Notes.telegram(this, tg)) }
            }
        }
        lastFix = at; lastFixAt = t
        if (s.healthFrom < 0) scope.launch { readSensor() }
        // in a pocket on the round: hand over by itself and keep the story for later
        if (!visible && WalkService.running && !riding.value) {
            for (id in reachable(at)) {
                val res = game.deliver(s, r, now, id) ?: continue
                s.handed += Handed(now.day, id, res.shown.map { it.item }, at = now.ms)
                while (s.handed.size > 60) s.handed.removeAt(0)
                notify(Notes.handed(this, res))
            }
        }
        changed()
    }

    private fun notify(n: Pair<Int, android.app.Notification>?) {
        n ?: return
        val m = app.getSystemService(android.app.NotificationManager::class.java) ?: return
        runCatching { m.notify(n.first, n.second) }
    }
}

class PostApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            runCatching {
                if (BuildConfig.DEMO) app.postround.data.DemoSeed.seedIfEmpty(container) else container.loadDistrict()
                if (container.terrain == null) container.loadDistrict()
                container.wake()
            }.onFailure { android.util.Log.e("Postround", "district", it) }
            // the demo has no real GPS: the postman stands at home until moved by a long press
            if (BuildConfig.DEMO && container.here.value == null) container.here.value = container.state?.home
            container.version.value++
        }
        runCatching { StepWorker.schedule(this) }   // unit tests run without WorkManager
    }
}

val Context.container: AppContainer get() = (applicationContext as PostApp).container
