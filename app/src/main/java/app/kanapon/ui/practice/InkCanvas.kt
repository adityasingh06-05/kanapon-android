package app.kanapon.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.kanapon.engine.InkPipeline
import app.kanapon.ui.theme.LocalPalette
import java.util.IdentityHashMap

/** Finished strokes as filled paths, rebuilt only when the cell, pen or pressure setting changes. */
private class PathCache {
    private class Entry(val key: List<Any>, val path: Path)

    private val map = IdentityHashMap<InkStroke, Entry>()

    fun get(stroke: InkStroke, key: List<Any>, build: () -> Path): Path {
        map[stroke]?.let { if (it.key == key) return it.path }
        return build().also { map[stroke] = Entry(key, it) }
    }

    fun retain(live: Collection<InkStroke>) {
        map.keys.retainAll(live.toSet())
    }
}

private fun outlinePath(stroke: InkStroke, cellDp: Float, width: Double, pressure: Boolean, done: Boolean): Path {
    val pts = stroke.points.map { InkPipeline.InkPoint(it.x.toDouble() * cellDp, it.y.toDouble() * cellDp, it.p.toDouble()) }
    val path = Path()
    InkPipeline.trace(
        InkPipeline.outline(pts, width, pressure, done),
        object : InkPipeline.PathSink {
            override fun moveTo(x: Double, y: Double) = path.moveTo(x.toFloat(), y.toFloat())

            override fun quadTo(x1: Double, y1: Double, x2: Double, y2: Double) =
                path.quadraticTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())

            override fun close() = path.close()
        }
    )
    return path
}

/**
 * A writing surface: one pointer at a time, every sample the hardware reports (historical points
 * included), pressure from a stylus, palm rejection once a stylus has been seen. Ink is stored as
 * fractions of the cell so a resize or a theme change only redraws it; it is drawn through the same
 * pipeline as the web app's, in dp, the counterpart of its CSS pixels.
 */
@Composable
fun InkCanvas(practice: PracticeState, id: Int, pen: Float, pressure: Boolean, modifier: Modifier = Modifier) {
    val ink = practice.cells[id]
    val color = LocalPalette.current.ink
    val cache = remember { PathCache() }
    Canvas(
        modifier
            .semantics { contentDescription = "Writing area" }
            .pointerInput(practice, id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    if (!practice.admit(stylus, down.type == PointerType.Touch)) return@awaitEachGesture
                    down.consume()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    fun pt(pos: Offset, p: Float, type: PointerType): NPoint {
                        val pr = if (type == PointerType.Mouse || p <= 0f) 0.5f else p
                        return NPoint(pos.x / w, pos.y / h, pr)
                    }
                    practice.strokeStart(id)
                    val stroke = InkStroke()
                    stroke.points.add(pt(down.position, down.pressure, down.type))
                    ink.strokes.add(stroke)
                    ink.active = stroke
                    ink.touch()
                    practice.pressure.floatValue = stroke.points.last().p
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val p = change.pressure
                        change.historical.forEach { stroke.points.add(pt(it.position, p, change.type)) }
                        stroke.points.add(pt(change.position, p, change.type))
                        change.consume()
                        ink.touch()
                        practice.pressure.floatValue = stroke.points.last().p
                    }
                    ink.active = null
                    ink.touch()
                    practice.pressure.floatValue = 0f
                    practice.strokeEnd(id)
                }
            }
    ) {
        ink.version // read, so any change to the ink redraws
        val cellDp = size.width / density
        if (cellDp <= 0f) return@Canvas
        val width = InkPipeline.penWidth(pen.toDouble(), cellDp.toDouble())
        val key = listOf(cellDp, width, pressure)
        val active = ink.active
        scale(density, density, pivot = Offset.Zero) {
            for (s in ink.strokes) {
                if (s === active) continue
                drawPath(cache.get(s, key) { outlinePath(s, cellDp, width, pressure, true) }, color)
            }
            if (active != null) drawPath(outlinePath(active, cellDp, width, pressure, false), color)
        }
        cache.retain(ink.strokes)
    }
}
