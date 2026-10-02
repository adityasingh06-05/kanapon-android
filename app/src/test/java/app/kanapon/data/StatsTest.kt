package app.kanapon.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private val today = LocalDate.of(2026, 10, 1)

    private fun record(s: Stats, char: String, score: Int, strokes: Int, counts: Boolean = true, day: LocalDate = today) =
        StatsMath.recordAttempt(s, char, score, strokes, counts, day)

    @Test
    fun recordsAttemptsBestStrokesAndDays() {
        val (a, prev0) = record(Stats(), "あ", 60, 3)
        assertEquals(0, prev0)
        val (b, prev1) = record(a, "あ", 80, 3)
        assertEquals(60, prev1)
        assertEquals(CharStat(2, 80), b.chars["あ"])
        assertEquals(6, b.strokes)
        assertEquals(2, b.days["2026-10-01"])
    }

    @Test
    fun anAttemptThatDoesNotCountNeverRaisesTheBest() {
        val (a, _) = record(Stats(), "あ", 60, 3)
        val (b, _) = record(a, "あ", 100, 3, counts = false)
        assertEquals(CharStat(2, 60), b.chars["あ"])
        assertEquals(6, b.strokes)
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val days = mapOf("2026-09-30" to 2, "2026-09-29" to 1, "2026-09-27" to 4)
        assertEquals(2, StatsMath.streak(days, today))
        assertEquals(3, StatsMath.streak(days + ("2026-10-01" to 1), today))
        assertEquals(0, StatsMath.streak(mapOf("2026-09-28" to 1), today))
        assertEquals(0, StatsMath.streak(emptyMap(), today))
    }

    @Test
    fun streakCrossesDaylightSavingAndMonthEnds() {
        // Calendar dates only, so a 23- or 25-hour day can neither skip nor repeat a date.
        val d = LocalDate.of(2026, 3, 30)
        val days = (0L until 5L).associate { StatsMath.dateKey(d.minusDays(it)) to 1 }
        assertEquals(5, StatsMath.streak(days, d))
    }

    @Test
    fun heatmapIsAlwaysTheFullGridEndingToday() {
        val cols = StatsMath.heatmap(mapOf("2026-10-01" to 3), today)
        assertEquals(26, cols.size)
        assertTrue(cols.all { it.size == 7 })
        assertEquals("2026-04-03", cols.first().first().key)
        assertEquals(HeatDay("2026-10-01", 3), cols.last().last())
    }

    @Test
    fun bucketsAgainstTheBusiestDay() {
        assertEquals(0, StatsMath.bucket(0, 10))
        assertEquals(4, StatsMath.bucket(1, 1))
        assertEquals(1, StatsMath.bucket(1, 10))
        assertEquals(2, StatsMath.bucket(5, 10))
        assertEquals(4, StatsMath.bucket(10, 10))
    }

    @Test
    fun weakestIsLowestBestThenMostAttempts() {
        val s = Stats(
            chars = mapOf(
                "か" to CharStat(2, 40),
                "あ" to CharStat(5, 40),
                "ア" to CharStat(1, 10),
                "ん" to CharStat(9, 90)
            )
        )
        assertEquals(listOf("ア", "あ", "か", "ん"), StatsMath.weakest(s).map { it.char })
        assertEquals(listOf("ア", "あ"), StatsMath.weakest(s, 2).map { it.char })
    }

    @Test
    fun parseKeepsOnlyWhatMakesSense() {
        assertEquals(Stats(), StatsMath.parse("not json"))
        assertEquals(Stats(), StatsMath.parse(null))
        val s = StatsMath.parse(
            """{"chars":{"あ":{"attempts":2,"best":70},"い":{"attempts":0,"best":10},"う":"x"},"days":{"2026-10-01":3,"2026-09-01":0},"strokes":"12"}"""
        )
        assertEquals(mapOf("あ" to CharStat(2, 70)), s.chars)
        assertEquals(mapOf("2026-10-01" to 3), s.days)
        assertEquals(12, s.strokes) // Number("12") is 12 on the web too
        assertEquals(s, StatsMath.parse(StatsMath.encode(s)))
    }
}
