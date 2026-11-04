package app.postround.data

import app.postround.AppContainer
import app.postround.domain.Game
import app.postround.domain.GameState
import app.postround.domain.Geo
import app.postround.domain.Now
import app.postround.domain.Round
import java.time.LocalDate

/**
 * The demo build (-Pdemo): home on Petrogradka, St Petersburg, the district bundled in the APK,
 * and ten days of the round already walked by the real rules — stamps, threads, a story or two under way.
 */
object DemoSeed {
    val HOME = Geo(59.9572, 30.3135)
    /** The box of the bundled Overpass dump. */
    val BOX = doubleArrayOf(59.9455, 30.3040, 59.9585, 30.3260)

    fun seedIfEmpty(c: AppContainer) {
        if (c.state != null) return
        val json = runCatching { c.app.assets.open("demo/petrogradka.osm.json").bufferedReader().use { it.readText() } }.getOrNull() ?: return
        seed(c, json)
    }

    /** Seeds the demo from an Overpass dump of Petrogradka (the APK's asset, or a test resource). */
    fun seed(c: AppContainer, json: String) {
        c.store.saveDistrict(json)
        val today = LocalDate.now()
        val s = c.game.newGame(HOME, Now(today.minusDays(10)))
        c.adopt(s, BOX)
        val r = c.round ?: return
        play(c.game, s, r, today, 10)
        c.game.morning(s, r, c.now())
        c.here.value = HOME
        c.changed()
    }

    /** Plays [days] days before [today] the way a keen postman would: nearly everything handed over. */
    fun play(g: Game, s: GameState, r: Round, today: LocalDate, days: Int) {
        for (k in days downTo 1) {
            val date = today.minusDays(k.toLong())
            val now = Now(date, 13, minute = 13 * 60, ms = date.toEpochDay() * 86_400_000L + 13 * 3_600_000L)
            g.morning(s, r, now)
            g.walked(s, r, now, 2200.0 + k * 90, r.d.w / 2f, r.d.h / 2f)
            repeat(4) {
                for (id in s.bag.map { it.to }.distinct()) if (id != "post" && s.bag.none { it.to == id && it.kind == "riddle" }) g.deliver(s, r, now, id)
                g.post(s, r, now)
            }
            s.bag.firstOrNull { it.kind == "riddle" }?.let { if (k % 3 == 0) g.ask(s, r, now, it.to) }
        }
    }
}
