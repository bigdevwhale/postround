package app.postround

import app.postround.data.Content
import app.postround.data.DemoSeed
import app.postround.data.District
import app.postround.domain.Game
import app.postround.domain.GameState
import app.postround.domain.Now
import app.postround.domain.Round
import app.postround.domain.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoundTest {
    private val content = Content.read { p -> File("src/main/assets/content", p).takeIf { it.exists() }?.inputStream() }

    private fun round(): Round {
        val json = javaClass.classLoader!!.getResource("petrogradka.osm.json")!!.readText()
        val d = District.parse(json, DemoSeed.BOX)
        return Round(d, Terrain(d))
    }

    @Test
    fun content() {
        assertTrue(content.arcs.size >= 3)
        for (a in content.arcs) {
            assertTrue("${a.id} has steps", a.steps.isNotEmpty())
            for (st in a.steps) {
                for (id in listOf(st.from, st.to)) if (id.isNotBlank() && !id.startsWith("ext:") && id != "post") assertTrue("${a.id}: cast $id", id in content.cast)
                if (st.kind == "pickup") assertTrue("${a.id}: pickup goes to the post", st.to == "post")
            }
        }
        println("content: ${content.arcs.size} stories, ${content.cast.size} cast, ${content.fillers.size} fillers, ${content.telegrams.size} telegrams, ${content.riddles.size} riddles, ${content.names.size} names")
    }

    @Test
    fun houses() {
        val r = round()
        println("houses ${r.houses.size} on ${r.streets.size} streets, post office: ${r.realPost}; kinds ${r.houses.groupingBy { it.kind }.eachCount()}")
        println("motifs ${r.motif.values.groupingBy { it }.eachCount()}")
        assertTrue(r.houses.size > 100)
        assertEquals(r.houses.size, r.byId.size)
    }

    /** Ninety days of a keen postman: every story should end, the old postwoman should write. */
    @Test
    fun ninetyDays() {
        val r = round()
        val g = Game(content)
        val today = LocalDate.of(2026, 10, 1)
        val s = g.newGame(DemoSeed.HOME, Now(today))
        DemoSeed.play(g, s, r, today.plusDays(90), 90)
        val again = GameState.fromJson(s.toJson())
        assertEquals(s.toJson().toString(), again.toJson().toString())
        println("after 90 days: done ${s.done}, km %.1f, stamps ${s.streets.size}/${r.streets.size}, threads ${s.threads.size}, riddles ${s.riddles}, mine ${s.mine.size}, rank ${g.rankName(s).ru}".format(s.km))
        println("stories done ${s.arcDone.size}/${content.arcs.size}: ${s.arcDone}; running ${s.arcs.keys - s.arcDone}")
        println("cast: " + s.cast.entries.joinToString { "${it.key}=${it.value}" })
        assertTrue(s.riddles >= 3)
        assertTrue(s.belt)
        assertTrue("stories finish", s.arcDone.size >= 15)
        assertTrue(s.bag.none { it.to.isBlank() })
        // the delivery log keeps the last week, every day's handing-over in it
        val days = s.delivered.map { it.day }.distinct()
        assertTrue("log keeps a week: $days", days.size in 5..GameState.JOURNAL_DAYS && days.all { it > s.day - GameState.JOURNAL_DAYS })
        val last = s.log.first { it.day == s.day - 1 }
        assertEquals(last.handed, s.delivered.filter { it.day == last.day }.sumOf { h -> h.items.count { it.kind != "pickup" } })
    }
}
