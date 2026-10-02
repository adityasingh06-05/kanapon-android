package app.kanapon.ui.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kanapon.data.Gojuon
import app.kanapon.data.Kana
import app.kanapon.data.KanaSet
import app.kanapon.engine.Feedback
import app.kanapon.engine.Pt
import app.kanapon.ui.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The four writing cells: two to trace, two from memory. */
enum class CellSpec(val label: String, val guide: Float) {
    Trace("trace", 1f),
    Fainter("trace, fainter", 0.5f),
    Memory1("from memory", 0f),
    Memory2("from memory", 0f)
}

/** A sample, as a fraction of the cell (0..1 both ways) and pressure 0..1. */
class NPoint(val x: Float, val y: Float, val p: Float)

class InkStroke {
    val points = ArrayList<NPoint>()
}

/** One cell's ink. [version] changes whenever the ink does, so the canvas knows to redraw. */
class CellInk {
    val strokes = ArrayList<InkStroke>()
    var active: InkStroke? = null
    var version by mutableIntStateOf(0)
        private set

    fun touch() {
        version++
    }

    val isEmpty: Boolean get() = strokes.isEmpty()
}

/** What the last scoring of a cell said. */
data class CellResult(val score: Int, val drawn: Int, val expected: Int, val note: String, val notCounted: String?)

private class Pending(val char: String, val score: Int, val drawn: Int, val notCounted: String?)

/**
 * The practice page's state and the attempt lifecycle, ported from the web app's Practice.jsx: a
 * cell is scored as each stroke lifts, and the attempt is written to history only when it is
 * settled: three seconds with the pen up, a stroke in another cell, a new character, leaving the
 * tab, or the app going to the background.
 */
class PracticeState(private val app: AppState, private val scope: CoroutineScope) {
    var set by mutableStateOf(KanaSet.Hiragana)
        private set
    var index by mutableIntStateOf(0)
        private set
    val kana: Kana get() = Gojuon.listFor(set).getOrElse(index) { Gojuon.listFor(set)[0] }

    /** The cell shown on a phone, which shows one at a time. */
    var cell by mutableIntStateOf(0)
    var recall by mutableStateOf(false)
        private set

    /** Stroke order shown anyway while in recall mode. Resets with the character. */
    var revealed by mutableStateOf(false)
    var touchBlocked by mutableStateOf(false)
        private set

    /** The live pen pressure, read only by the readout so drawing does not recompose the page. */
    val pressure = mutableFloatStateOf(0f)

    val cells = List(CellSpec.entries.size) { CellInk() }
    val results = mutableStateMapOf<Int, CellResult>()

    private val pending = HashMap<Int, Pending>()
    private val committed = IntArray(cells.size)
    private var idle: Job? = null
    private var lastCell: Int? = null

    /* Palm rejection, app-wide like the web's: once a stylus has been seen, a finger is a palm. */
    var penSeen = false
        private set
    private var palmGuard = true

    fun guideFor(spec: CellSpec): Float = if (recall) 0f else app.settings.guide * spec.guide

    fun toggleRecall() {
        recall = !recall
        revealed = false
    }

    fun allowTouch() {
        palmGuard = false
        touchBlocked = false
    }

    /** Called on every pointer down; false means the touch is a palm and must be ignored. */
    fun admit(isStylus: Boolean, isTouch: Boolean): Boolean {
        if (isStylus) {
            penSeen = true
        } else if (isTouch && penSeen && palmGuard) {
            touchBlocked = true
            return false
        }
        return true
    }

    /* ---------- drawing ---------- */

    fun strokeStart(id: Int) {
        idle?.cancel()
        lastCell = id
        pending.keys.filter { it != id }.forEach { finalise(it) }
    }

    /** Scores the cell; with [record] false it only refreshes the display (an undo into recorded ink). */
    fun strokeEnd(id: Int, record: Boolean = true) {
        val ink = cells[id]
        if (ink.isEmpty) return
        val k = kana
        val strokes = ink.strokes.map { s -> s.points.map { Pt(it.x.toDouble(), it.y.toDouble()) } }
        val r = app.matcher.match(strokes, k.char, 1.0, 1.0) ?: return
        val score = Feedback.toScore(r.raw)
        val drawn = ink.strokes.size
        val notCounted = Feedback.whyNotCounted(drawn, k.strokes, r.order)
        val note = Feedback.describeAttempt(score, app.bestOf(k.char), drawn, k.strokes, r.diagnostics, r.order, r.strokes)
        results[id] = CellResult(score, drawn, k.strokes, note, notCounted)
        if (!record) return
        pending[id] = Pending(k.char, score, drawn, notCounted)
        idle?.cancel()
        idle = scope.launch {
            delay(IDLE_FINALISE_MS)
            finalise(id)
        }
    }

    private fun finalise(id: Int) {
        val p = pending.remove(id) ?: return
        val already = committed[id]
        val prevBest = app.record(p.char, p.score, maxOf(0, p.drawn - already), p.notCounted == null)
        committed[id] = p.drawn
        // The best is only known once recorded; an attempt that could not count keeps the note that says why.
        if (p.notCounted == null && prevBest > 0 && p.score > prevBest) {
            results[id]?.let { results[id] = it.copy(note = "your best yet") }
        }
    }

    fun finaliseAll() {
        pending.keys.toList().forEach { finalise(it) }
    }

    /** Throws away attempts not yet written, for a reset of the history. */
    fun discardPending() {
        pending.clear()
        idle?.cancel()
    }

    private fun wipe() {
        idle?.cancel()
        lastCell = null
        committed.fill(0)
        cells.forEach {
            it.strokes.clear()
            it.active = null
            it.touch()
        }
        results.clear()
    }

    /* ---------- changing character ---------- */

    fun goTo(toSet: KanaSet, toIndex: Int) {
        finaliseAll()
        wipe()
        set = toSet
        index = toIndex.coerceIn(0, Gojuon.listFor(toSet).size - 1)
        cell = 0
        revealed = false
    }

    fun step(delta: Int) {
        val n = Gojuon.listFor(set).size
        goTo(set, (index + delta + n) % n)
    }

    fun pick(char: String) {
        val s = Gojuon.setOf(char) ?: return
        goTo(s, Gojuon.indexIn(s, char))
    }

    fun switchSet(to: KanaSet) = goTo(to, 0)

    fun clearAll() {
        pending.clear()
        wipe()
    }

    fun undo() {
        fun hasInk(i: Int?) = i != null && !cells[i].isEmpty
        val id = lastCell.takeIf { hasInk(it) } ?: cells.indices.reversed().firstOrNull { hasInk(it) } ?: return
        val ink = cells[id]
        ink.strokes.removeAt(ink.strokes.size - 1)
        ink.touch()
        val already = minOf(committed[id], ink.strokes.size)
        committed[id] = already
        pending.remove(id)
        if (ink.isEmpty) results.remove(id) else strokeEnd(id, record = ink.strokes.size > already)
    }

    companion object {
        const val IDLE_FINALISE_MS = 3000L
    }
}
