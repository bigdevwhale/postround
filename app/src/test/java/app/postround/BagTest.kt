package app.postround

import app.postround.data.Content
import app.postround.domain.Game
import app.postround.domain.GameState
import app.postround.domain.Item
import app.postround.domain.Now
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** The bag fills by its weight and by the clock (#24); the letter with no address is shown in the end (#20); a telegram can be turned down (#12). */
class BagTest {
    private val content = Content.read { p -> File("src/main/assets/content", p).takeIf { it.exists() }?.inputStream() }
    private val g = Game(content)
    private val d0 = LocalDate.of(2026, 11, 20)

    @Test fun aStoryTelegramWaitsForTheDayShift() {
        val s = GameState().apply { this.day = d0.toEpochDay() }
        s.queue += Item(1, "telegram", "h1", arc = "a", st = 1, arrive = d0.toEpochDay())
        assertTrue(g.topUp(s, Now(d0, hour = 2)).isEmpty())
        assertEquals(1, s.queue.size)
        assertEquals(1, g.topUp(s, Now(d0, hour = 9)).size)
        assertTrue(s.queue.isEmpty())
    }

    @Test fun aFullBagLeavesTheRestInTheQueue() {
        val s = GameState().apply { this.day = d0.toEpochDay() }
        repeat(g.cap(s)) { s.bag += Item(100L + it, "letter", "h") }
        s.queue += Item(1, "letter", "h1", arc = "a", st = 1, arrive = d0.toEpochDay())
        assertTrue(g.topUp(s, Now(d0, hour = 9)).isEmpty())
        s.bag.removeAt(0)
        assertEquals(1, g.topUp(s, Now(d0, hour = 9)).size)
    }

    @Test fun theUnaddressedHouseIsShownAfterTwoDaysOrFiveDoors() {
        val s = GameState()
        val rd = Item(1, "riddle", "h1", arrive = d0.toEpochDay())
        assertFalse(g.revealed(s, rd, Now(d0.plusDays(1))))
        assertTrue(g.revealed(s, rd, Now(d0.plusDays(2))))
        s.riddleTries = 5
        assertTrue(g.revealed(s, rd, Now(d0)))
    }

    @Test fun aWalkTelegramCanBeTurnedDownButNotAStoryOne() {
        val s = GameState()
        s.bag += Item(1, "telegram", "h1", due = 1L)
        s.bag += Item(2, "telegram", "h2", arc = "a", st = 0)
        assertTrue(g.refuse(s, 1))
        assertFalse(g.refuse(s, 2))
        assertEquals(listOf(2L), s.bag.map { it.id })
    }
}
