package app.kanapon.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How a stroke's samples become the ink that is drawn, ported from the web app's src/lib/ink.js
 * (strokePath and the helpers above it). Only the drawing is trimmed, densified and smoothed; the
 * stored points, and the score, are untouched.
 *
 * Coordinates are the cell's own density-independent pixels, the counterpart of the web's CSS
 * pixels, so every distance constant below means what it means there.
 */
object InkPipeline {
    /** A sample: position in dp and pressure 0..1. */
    class InkPoint(val x: Double, val y: Double, val p: Double)

    /** The practice cell size the pen widths were tuned on, in CSS pixels. */
    const val REFERENCE_CELL = 193.2

    private const val PRESSURE_SIZE = 1.1 // 0.35 + 1.5 * 0.5
    private const val PRESSURE_THINNING = 1.5 / (2 * PRESSURE_SIZE)
    private const val SMOOTHING = 0.5
    private const val STREAMLINE = 0.15
    private const val MAX_GAP = 2.0
    private const val SMOOTH = 5.0
    private const val CORNER = (70 * PI) / 180
    private const val CORNER_SPAN = 10

    /** Pen width for a cell [cellWidth] dp wide: the pen setting scaled with the cell, within limits. */
    fun penWidth(pen: Double, cellWidth: Double): Double = pen * min(3.0, max(0.5, cellWidth / REFERENCE_CELL))

    /**
     * One stroke's outline polygon, ready to be traced with quadratic curves through its midpoints
     * and filled. [width] is the diameter at mid pressure, or everywhere with pressure off; [done]
     * means the stroke has ended, so its end cap can be closed.
     */
    fun outline(pts: List<InkPoint>, width: Double, pressure: Boolean, done: Boolean): List<DoubleArray> {
        val prepared = smooth(densify(trimEnds(pts, max(2.0, width))))
        return Freehand.getStroke(
            prepared.map { Freehand.Input(it.x, it.y, it.p) },
            Freehand.Options(
                size = if (pressure) width * PRESSURE_SIZE else width,
                thinning = if (pressure) PRESSURE_THINNING else 0.0,
                smoothing = SMOOTHING,
                streamline = STREAMLINE,
                last = done
            )
        )
    }

    /** Where an outline is traced to: a Compose Path in the app, a recorder in tests. */
    interface PathSink {
        fun moveTo(x: Double, y: Double)

        fun quadTo(x1: Double, y1: Double, x2: Double, y2: Double)

        fun close()
    }

    /**
     * Quadratic curves through the midpoints of the outline's own points: the outline is already
     * dense, and this rounds off what is left of its corners without moving it.
     */
    fun trace(outline: List<DoubleArray>, sink: PathSink) {
        val n = outline.size
        if (n < 3) return
        val first = outline[0]
        val lastPt = outline[n - 1]
        sink.moveTo((lastPt[0] + first[0]) / 2, (lastPt[1] + first[1]) / 2)
        for (i in 0 until n) {
            val a = outline[i]
            val b = outline[(i + 1) % n]
            sink.quadTo(a[0], a[1], (a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        }
        sink.close()
    }

    /** Drops the points near either end that never get more than [r] away from it. */
    internal fun trimEnds(pts: List<InkPoint>, r: Double): List<InkPoint> {
        if (pts.size < 4) return pts
        val first = pts[0]
        val last = pts[pts.size - 1]
        fun near(p: InkPoint, q: InkPoint) = hypot(p.x - q.x, p.y - q.y) < r
        var i = 1
        while (i < pts.size - 1 && near(pts[i], first)) i++
        var j = pts.size - 2
        while (j >= i && near(pts[j], last)) j--
        return listOf(first) + pts.subList(i, j + 1) + last
    }

    /**
     * Fills gaps between samples with points on a centripetal Catmull-Rom spline through them. A turn
     * sharper than a right angle is kept as a corner. Pressure is interpolated linearly.
     */
    internal fun densify(pts: List<InkPoint>): List<InkPoint> {
        if (pts.size < 2) return pts
        val out = arrayListOf(pts[0])
        fun knot(a: InkPoint, b: InkPoint) = max(1e-6, sqrt(hypot(b.x - a.x, b.y - a.y)))
        fun sharp(a: InkPoint, b: InkPoint, c: InkPoint) = (b.x - a.x) * (c.x - b.x) + (b.y - a.y) * (c.y - b.y) < 0
        fun reflect(from: InkPoint, to: InkPoint) = InkPoint(2 * from.x - to.x, 2 * from.y - to.y, Double.NaN)
        for (i in 0 until pts.size - 1) {
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val steps = ceil(hypot(p2.x - p1.x, p2.y - p1.y) / MAX_GAP).toInt()
            if (steps > 1) {
                val prev = pts.getOrNull(i - 1)
                val next = pts.getOrNull(i + 2)
                val p0 = if (prev != null && !sharp(prev, p1, p2)) prev else reflect(p1, p2)
                val p3 = if (next != null && !sharp(p1, p2, next)) next else reflect(p2, p1)
                val t1 = knot(p0, p1)
                val t2 = t1 + knot(p1, p2)
                val t3 = t2 + knot(p2, p3)
                fun lerpX(a: Double, b: Double, ta: Double, tb: Double, t: Double) = a + (b - a) * ((t - ta) / (tb - ta))
                for (k in 1 until steps) {
                    val t = t1 + ((t2 - t1) * k) / steps
                    val a1x = lerpX(p0.x, p1.x, 0.0, t1, t)
                    val a1y = lerpX(p0.y, p1.y, 0.0, t1, t)
                    val a2x = lerpX(p1.x, p2.x, t1, t2, t)
                    val a2y = lerpX(p1.y, p2.y, t1, t2, t)
                    val a3x = lerpX(p2.x, p3.x, t2, t3, t)
                    val a3y = lerpX(p2.y, p3.y, t2, t3, t)
                    val b1x = lerpX(a1x, a2x, 0.0, t2, t)
                    val b1y = lerpX(a1y, a2y, 0.0, t2, t)
                    val b2x = lerpX(a2x, a3x, t1, t3, t)
                    val b2y = lerpX(a2y, a3y, t1, t3, t)
                    out.add(
                        InkPoint(
                            lerpX(b1x, b2x, t1, t2, t),
                            lerpX(b1y, b2y, t1, t2, t),
                            p1.p + ((p2.p - p1.p) * k) / steps
                        )
                    )
                }
            }
            out.add(p2)
        }
        return out
    }

    /** Points a pixel apart along the line, pressure interpolated. */
    internal fun evenly(pts: List<InkPoint>): List<InkPoint> {
        val out = arrayListOf(pts[0])
        var carry = 0.0
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val seg = hypot(b.x - a.x, b.y - a.y)
            var d = 1 - carry
            while (d <= seg) {
                val t = d / seg
                out.add(InkPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.p + (b.p - a.p) * t))
                d += 1
            }
            carry = seg - (d - 1)
        }
        val last = pts[pts.size - 1]
        if (out[out.size - 1] !== last) out.add(last)
        return out
    }

    /** Indices of the corners in an evenly spaced line: the sharpest point of each run past CORNER. */
    internal fun cornersOf(u: List<InkPoint>): List<Int> {
        val k = CORNER_SPAN
        val found = ArrayList<Int>()
        var best = -1
        var bestTurn = 0.0
        for (i in k until u.size - k) {
            val a = u[i - k]
            val b = u[i]
            val c = u[i + k]
            val t1 = atan2(b.y - a.y, b.x - a.x)
            val t2 = atan2(c.y - b.y, c.x - b.x)
            var turn = abs(t2 - t1)
            if (turn > PI) turn = 2 * PI - turn
            if (turn > CORNER) {
                if (turn > bestTurn) {
                    best = i
                    bestTurn = turn
                }
            } else if (best >= 0) {
                found.add(best)
                best = -1
                bestTurn = 0.0
            }
        }
        if (best >= 0) found.add(best)
        return found
    }

    /** A Gaussian of SMOOTH pixels along u, from index `from` to `to`, the two ends held still. */
    private fun smoothRun(u: List<InkPoint>, from: Int, to: Int, out: Array<InkPoint?>) {
        val reach = ceil(3 * SMOOTH).toInt()
        for (i in from..to) {
            val h = minOf(reach, i - from, to - i)
            var sx = 0.0
            var sy = 0.0
            var sp = 0.0
            var sw = 0.0
            for (k in -h..h) {
                val q = u[i + k]
                val w = exp(-(k * k).toDouble() / (2 * SMOOTH * SMOOTH))
                sx += w * q.x
                sy += w * q.y
                sp += w * q.p
                sw += w
            }
            out[i] = InkPoint(sx / sw, sy / sw, sp / sw)
        }
    }

    internal fun smooth(pts: List<InkPoint>): List<InkPoint> {
        if (pts.size < 3) return pts
        val u = evenly(pts)
        val out = arrayOfNulls<InkPoint>(u.size)
        val cuts = listOf(0) + cornersOf(u) + (u.size - 1)
        for (c in 1 until cuts.size) smoothRun(u, cuts[c - 1], cuts[c], out)
        return out.map { it!! }
    }
}
