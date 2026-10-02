package app.kanapon.ui

import app.kanapon.TestData
import app.kanapon.data.KanaSet
import app.kanapon.data.MemorySettingsStore
import app.kanapon.data.MemoryStatsStore
import app.kanapon.engine.PathParser
import app.kanapon.ui.practice.CellSpec
import app.kanapon.ui.practice.InkStroke
import app.kanapon.ui.practice.NPoint
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The attempt lifecycle of the web app's Practice.jsx: when an attempt is written, and what it says. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeStateTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun main() = Dispatchers.setMain(dispatcher)

    @After fun reset() = Dispatchers.resetMain()

    private fun TestScope.app(): AppState = AppState(
        scope = this,
        statsStore = MemoryStatsStore(),
        settingsStore = MemorySettingsStore(),
        strokeData = TestData.strokeData,
        today = { LocalDate.of(2026, 10, 1) },
        io = dispatcher
    )

    /** The reference strokes of [char] as ink: a perfect trace, as fractions of the cell. */
    private fun trace(char: String): List<InkStroke> = TestData.strokeData.reference.getValue(char).map { d ->
        InkStroke().apply { PathParser.parse(d).forEach { points.add(NPoint((it.x / 109).toFloat(), (it.y / 109).toFloat(), 0.5f)) } }
    }

    private fun AppState.draw(cell: Int, strokes: List<InkStroke>) {
        strokes.forEach {
            practice.strokeStart(cell)
            practice.cells[cell].strokes.add(it)
            practice.strokeEnd(cell)
        }
    }

    @Test
    fun anAttemptIsWrittenOnlyOnceSettled() = runTest(dispatcher) {
        val app = app()
        app.draw(0, trace("あ"))
        val r = app.practice.results.getValue(0)
        assertEquals(3, r.drawn)
        assertTrue("score ${r.score}", r.score >= 95)
        assertNull(r.notCounted)
        assertTrue(app.stats.chars.isEmpty())
        advanceTimeBy(3001)
        runCurrent()
        assertEquals(1, app.stats.chars.getValue("あ").attempts)
        assertEquals(3, app.stats.strokes)
        assertEquals(1, app.stats.days["2026-10-01"])
    }

    @Test
    fun aStrokeInAnotherCellSettlesTheFirst() = runTest(dispatcher) {
        val app = app()
        app.draw(0, trace("あ"))
        app.practice.strokeStart(1)
        assertEquals(1, app.stats.chars.getValue("あ").attempts)
    }

    @Test
    fun pausingThenAddingAStrokeCountsOnlyTheNewOne() = runTest(dispatcher) {
        val app = app()
        val strokes = trace("あ")
        app.draw(0, strokes.take(2))
        advanceTimeBy(3001)
        runCurrent()
        app.draw(0, strokes.drop(2))
        advanceTimeBy(3001)
        runCurrent()
        assertEquals(2, app.stats.chars.getValue("あ").attempts)
        assertEquals(3, app.stats.strokes)
    }

    @Test
    fun wrongOrderIsPracticeButNeverABest() = runTest(dispatcher) {
        val app = app()
        val s = trace("あ")
        app.draw(0, listOf(s[1], s[0], s[2]))
        assertEquals("stroke order", app.practice.results.getValue(0).notCounted)
        app.practice.finaliseAll()
        assertEquals(0, app.stats.chars.getValue("あ").best)
        assertEquals(1, app.stats.chars.getValue("あ").attempts)
    }

    @Test
    fun aBetterAttemptIsCalledYourBestYet() = runTest(dispatcher) {
        val app = app()
        val s = trace("あ")
        // A first, wobblier attempt sets a lower best.
        val wobbly = s.map { st ->
            InkStroke().apply {
                st.points.forEachIndexed { i, p ->
                    points.add(
                        NPoint(
                            p.x + if (i % 2 == 0) 0.02f else -0.02f,
                            p.y,
                            p.p
                        )
                    )
                }
            }
        }
        app.draw(0, wobbly)
        app.practice.finaliseAll()
        val first = app.stats.chars.getValue("あ").best
        app.draw(1, s)
        assertEquals("your best yet", app.practice.results.getValue(1).note)
        app.practice.finaliseAll()
        assertTrue(app.stats.chars.getValue("あ").best > first)
    }

    @Test
    fun clearDiscardsWhatIsNotYetWritten() = runTest(dispatcher) {
        val app = app()
        app.draw(0, trace("あ"))
        app.practice.clearAll()
        advanceTimeBy(5000)
        runCurrent()
        assertTrue(app.stats.chars.isEmpty())
        assertTrue(app.practice.results.isEmpty())
        assertTrue(app.practice.cells.all { it.isEmpty })
    }

    @Test
    fun undoIntoRecordedInkIsNotANewAttempt() = runTest(dispatcher) {
        val app = app()
        app.draw(0, trace("あ"))
        app.practice.finaliseAll()
        app.practice.undo()
        assertEquals(2, app.practice.results.getValue(0).drawn)
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(1, app.stats.chars.getValue("あ").attempts)
        app.practice.undo()
        app.practice.undo()
        assertTrue(app.practice.results.isEmpty())
    }

    @Test
    fun changingCharacterSettlesAndWipes() = runTest(dispatcher) {
        val app = app()
        app.draw(2, trace("あ"))
        app.practice.cell = 2
        app.practice.step(1)
        assertEquals(1, app.stats.chars.getValue("あ").attempts)
        assertEquals("い", app.practice.kana.char)
        assertEquals(0, app.practice.cell)
        assertTrue(app.practice.cells.all { it.isEmpty })
        app.practice.step(-2)
        assertEquals("ん", app.practice.kana.char)
        app.practice.switchSet(KanaSet.Katakana)
        assertEquals("ア", app.practice.kana.char)
    }

    @Test
    fun aPalmIsTurnedAwayOnceAStylusHasBeenSeen() = runTest(dispatcher) {
        val p = app().practice
        assertTrue(p.admit(isStylus = false, isTouch = true))
        assertTrue(p.admit(isStylus = true, isTouch = false))
        assertEquals(false, p.admit(isStylus = false, isTouch = true))
        assertTrue(p.touchBlocked)
        p.allowTouch()
        assertTrue(p.admit(isStylus = false, isTouch = true))
    }

    @Test
    fun recallHidesTheGuideAndResetNeedsTwoTaps() = runTest(dispatcher) {
        val app = app()
        app.practice.toggleRecall()
        assertEquals(0f, app.practice.guideFor(CellSpec.Trace))
        app.draw(0, trace("あ"))
        app.practice.finaliseAll()
        app.tapReset()
        assertTrue(app.confirmingReset)
        assertEquals(1, app.stats.attempted)
        app.tapReset()
        assertEquals(0, app.stats.attempted)
    }
}
