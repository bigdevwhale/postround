package app.postround

import app.postround.data.Content
import app.postround.data.DemoSeed
import app.postround.data.District
import app.postround.domain.Game
import app.postround.domain.Now
import app.postround.domain.Round
import app.postround.domain.Terrain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** How long the written content lasts for a postman who walks every day: keen (hands over everything) and easy (about 60 %). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PacingTest {
    private val content = Content.read { p -> File("src/main/assets/content", p).takeIf { it.exists() }?.inputStream() }

    @Test fun pacing() {
        val json = javaClass.classLoader!!.getResource("petrogradka.osm.json")!!.readText()
        val d = District.parse(json, DemoSeed.BOX)
        val r = Round(d, Terrain(d))
        for ((name, share) in listOf("keen" to 1.0, "easy" to .6)) {
            val g = Game(content)
            val start = LocalDate.of(2026, 11, 1)
            val s = g.newGame(DemoSeed.HOME, Now(start))
            val rnd = Random(7)
            val finished = LinkedHashMap<String, Int>()
            val fillersSeen = HashSet<Int>(); var fillersAll = 0; var fillerRepeatDay = -1
            val tgSeen = HashSet<Int>(); var tgRepeatDay = -1
            val ridSeen = HashSet<Int>(); var ridRepeatDay = -1
            var lastStoryDay = -1
            for (day in 0..365) {
                val date = start.plusDays(day.toLong())
                val now = Now(date, 13, minute = 13 * 60, ms = date.toEpochDay() * 86_400_000L + 13 * 3_600_000L)
                g.morning(s, r, now)
                g.walked(s, r, now, 3000.0, r.d.w / 2f, r.d.h / 2f)
                for (it in s.bag) {
                    if (it.arc.isEmpty() && it.kind in setOf("card", "bill", "mag", "parcel")) { fillersAll++; if (!fillersSeen.add(it.v) && fillerRepeatDay < 0) fillerRepeatDay = day }
                    if (it.kind == "telegram" && it.arc.isEmpty() && !tgSeen.add(it.v) && tgRepeatDay < 0) tgRepeatDay = day
                    if (it.kind == "riddle" && !ridSeen.add(it.v) && ridRepeatDay < 0) ridRepeatDay = day
                }
                repeat(3) {
                    for (id in s.bag.map { it.to }.distinct()) if (id != "post" && s.bag.none { it.to == id && it.kind == "riddle" } && rnd.nextDouble() < share) g.deliver(s, r, now, id)
                    if (rnd.nextDouble() < share) g.post(s, r, now)
                }
                s.bag.firstOrNull { it.kind == "riddle" }?.let { if (rnd.nextDouble() < share * .5) g.ask(s, r, now, it.to) }
                for (a in s.arcDone) if (a !in finished) { finished[a] = day; lastStoryDay = day }
            }
            println("== $name: stories finished by day: $finished")
            println("   stories finished per month: " + finished.values.groupingBy { start.plusDays(it.toLong()).monthValue }.eachCount().toSortedMap())
            println("   year letter: ${s.mine.any { it.arc == "year" }}, letters to the postman ${s.mine.size}, stamps ${s.streets.size}/${r.streets.size}, threads ${s.threads.size}")
            println("   all ${content.arcs.size}? done ${finished.size}, last on day $lastStoryDay; not finished: ${content.arcs.map { it.id } - finished.keys}")
            println("   fillers: ${fillersSeen.size}/${content.fillers.size} distinct seen, first repeat on day $fillerRepeatDay, total filler items $fillersAll")
            println("   telegrams first repeat day $tgRepeatDay; riddles solved ${s.riddles}, first repeat day $ridRepeatDay; arist line ${s.paperN}/${content.arist.size}")
        }
    }
}
