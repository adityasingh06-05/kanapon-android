package app.kanapon.engine

import app.kanapon.data.VIEWBOX
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Stroke-by-stroke scoring against the model, ported line for line from the web app's
 * src/lib/stroke-match.js (see its comments and docs/scoring.md for why each constant is what it
 * is). Each drawn stroke is compared with a reference stroke from klee-strokes.json: the centreline
 * of the Klee One glyph every model shows, divided into strokes and put in order by KanjiVG. Both
 * are resampled to the same number of points and walked end to end together.
 *
 * Coordinates are KanjiVG's 109 box throughout, and the box is the whole practice cell.
 */

/** Where the ink sits against the model before alignment: dx, dy as fractions of the cell (+ is right / low). */
data class Diagnostics(val dx: Double, val dy: Double, val extentRatio: Double)

/**
 * swapped: pairs of 1-based reference stroke numbers [earlier, later] where `later` was drawn first.
 * reversed: reference stroke numbers drawn end to start.
 */
data class Order(val swapped: List<Pair<Int, Int>> = emptyList(), val reversed: List<Int> = emptyList())

/** Per drawn stroke: ref is 1-based, or 0 if unmatched; d is the shape distance in box units. */
data class StrokeDetail(val ref: Int, val d: Double?, val score: Double, val reversed: Boolean)

data class MatchResult(
    /** 0..1, the mean stroke score over max(expected, drawn). */
    val raw: Double,
    val diagnostics: Diagnostics?,
    val order: Order,
    val strokes: List<StrokeDetail>
)

class Reference(val dense: List<List<Pt>>, val strokes: List<List<Pt>>, val extent: Extent?)

class Extent(val cx: Double, val cy: Double, val w: Double, val h: Double)

class StrokeMatcher(private val source: Map<String, List<String>>) {
    private val cache = HashMap<String, Reference?>()

    /** Dense polylines and resampled strokes for one character, or null if there is no data for it. */
    fun reference(char: String): Reference? = synchronized(cache) {
        cache.getOrPut(char) {
            source[char]?.let { d ->
                val dense = d.map(PathParser::parse)
                Reference(dense, dense.map { resample(it) }, extent(dense))
            }
        }
    }

    /**
     * The whole comparison. [strokes] are the learner's strokes in the canvas's own pixels, [width]
     * and [height] the canvas size in the same units. Returns null when there is no reference.
     */
    fun match(strokes: List<List<Pt>>, char: String, width: Double, height: Double): MatchResult? {
        val ref = reference(char) ?: return null
        if (width == 0.0 || height == 0.0) return null

        val sx = VIEWBOX / width
        val sy = VIEWBOX / height
        val drawnDense = strokes.filter { it.isNotEmpty() }.map { pts -> thin(pts.map { Pt(it.x * sx, it.y * sy) }) }

        val k = ref.strokes.size
        val n = drawnDense.size
        if (n == 0) return MatchResult(0.0, null, Order(), emptyList())

        // Placement, measured before anything is moved.
        val mine = extent(drawnDense)
        val model = ref.extent
        val diagnostics = if (mine != null && model != null) {
            Diagnostics(
                dx = (mine.cx - model.cx) / VIEWBOX,
                dy = (mine.cy - model.cy) / VIEWBOX,
                extentRatio = (mine.w + mine.h) / (model.w + model.h)
            )
        } else {
            null
        }

        var drawn = drawnDense.map { resample(it) }

        // A complete attempt is laid onto the model by its centre and size, as a whole, before strokes
        // are compared. A partial attempt is not moved, since one stroke's centre is not the character's.
        if (n >= k && mine != null && model != null) {
            val s = clamp((model.w + model.h) / max(1e-6, mine.w + mine.h), MIN_SCALE, MAX_SCALE)
            val tx = clamp(model.cx - mine.cx, -MAX_SHIFT, MAX_SHIFT)
            val ty = clamp(model.cy - mine.cy, -MAX_SHIFT, MAX_SHIFT)
            drawn = drawn.map { pts ->
                pts.map { p -> Pt((p.x - mine.cx) * s + mine.cx + tx, (p.y - mine.cy) * s + mine.cy + ty) }
            }
        }

        val cost = costMatrix(drawn, ref.strokes)
        val pairs = assign(cost, n, k)

        val detail = MutableList(drawn.size) { StrokeDetail(0, null, 0.0, false) }
        var total = 0.0
        for ((i, j) in pairs) {
            val c = cost[i][j]
            val score = strokeScore(c.d)
            detail[i] = StrokeDetail(j + 1, c.d, score, c.reversed)
            total += score
        }

        // Order: walking the drawn strokes in writing order, every pair of matched strokes whose
        // reference numbers go down is a swap.
        val swapped = ArrayList<Pair<Int, Int>>()
        val written = detail.filter { it.ref != 0 && it.score >= ORDER_MIN_SCORE }
        for (a in written.indices) {
            for (b in a + 1 until written.size) {
                if (written[a].ref > written[b].ref) swapped.add(written[b].ref to written[a].ref)
            }
        }
        val reversed = written.filter { it.reversed }.map { it.ref }

        return MatchResult(total / max(n, k), diagnostics, Order(swapped, reversed), detail)
    }

    private class Cost(val d: Double, val reversed: Boolean)

    private fun costMatrix(drawn: List<List<Pt>>, ref: List<List<Pt>>): List<List<Cost>> = drawn.map { a ->
        ref.map { b ->
            val fwd = warpedDistance(a, b)
            val rev = warpedDistance(a, b, true)
            // Shape is scored whichever way the stroke was drawn; direction is reported, not charged.
            Cost(min(fwd, rev), rev < fwd * REVERSE_RATIO && fwd - rev > REVERSE_MARGIN)
        }
    }

    /**
     * Pairs each reference stroke with at most one drawn stroke and vice versa, as many pairs as the
     * smaller side allows, for the lowest total distance. Returns (drawnIndex, refIndex) pairs.
     */
    private fun assign(cost: List<List<Cost>>, n: Int, k: Int): List<Pair<Int, Int>> {
        val small = min(n, k)
        if (small == 0) return emptyList()
        val byDrawn = n <= k
        fun at(a: Int, b: Int): Double = if (byDrawn) cost[a][b].d else cost[b][a].d
        val outer = if (byDrawn) n else k
        val inner = if (byDrawn) k else n

        var best: List<Pair<Int, Int>> = emptyList()
        if (inner > EXHAUSTIVE_LIMIT) {
            // Greedy: cheapest remaining pair first. sortedBy is stable, like Array.prototype.sort.
            val pairs = ArrayList<Triple<Double, Int, Int>>()
            for (a in 0 until outer) for (b in 0 until inner) pairs.add(Triple(at(a, b), a, b))
            val usedA = HashSet<Int>()
            val usedB = HashSet<Int>()
            val out = ArrayList<Pair<Int, Int>>()
            for ((_, a, b) in pairs.sortedBy { it.first }) {
                if (a in usedA || b in usedB) continue
                usedA.add(a)
                usedB.add(b)
                out.add(a to b)
            }
            best = out
        } else {
            val used = BooleanArray(inner)
            val cur = ArrayList<Pair<Int, Int>>()
            var bestCost = Double.POSITIVE_INFINITY
            fun walk(a: Int, sum: Double) {
                if (sum >= bestCost) return
                if (a == outer) {
                    bestCost = sum
                    best = cur.toList()
                    return
                }
                for (b in 0 until inner) {
                    if (used[b]) continue
                    used[b] = true
                    cur.add(a to b)
                    walk(a + 1, sum + at(a, b))
                    cur.removeAt(cur.size - 1)
                    used[b] = false
                }
            }
            walk(0, 0.0)
        }
        return best.map { (a, b) -> if (byDrawn) a to b else b to a }
    }

    companion object {
        /** Points per stroke after resampling. */
        const val SAMPLES = 32

        /** Distance to score: exp(-(d / SIGMA)^2). See docs/scoring.md in the web app. */
        const val SIGMA = 3.5

        private const val MAX_SHIFT = 0.1 * VIEWBOX
        private const val MIN_SCALE = 0.85
        private const val MAX_SCALE = 1.18
        private const val REVERSE_RATIO = 0.6
        private const val REVERSE_MARGIN = 3.0
        private const val ORDER_MIN_SCORE = 0.25
        private const val MIN_STEP = 1.5
        private const val EXHAUSTIVE_LIMIT = 10
        private const val BAND = 2

        fun strokeScore(d: Double): Double = exp(-((d / SIGMA) * (d / SIGMA)))

        /** Keeps the first point, every point at least [step] from the last one kept, and the last point. */
        fun thin(points: List<Pt>, step: Double = MIN_STEP): List<Pt> {
            if (points.size < 3) return points
            val out = arrayListOf(points[0])
            for (i in 1 until points.size - 1) {
                if (dist(points[i], out[out.size - 1]) >= step) out.add(points[i])
            }
            out.add(points[points.size - 1])
            return out
        }

        /** [n] points evenly spaced along the polyline's length. A tap resamples to n copies of its point. */
        fun resample(points: List<Pt>, n: Int = SAMPLES): List<Pt> {
            if (points.isEmpty()) return emptyList()
            val cum = DoubleArray(points.size)
            for (i in 1 until points.size) cum[i] = cum[i - 1] + dist(points[i - 1], points[i])
            val total = cum[cum.size - 1]
            if (total == 0.0) return List(n) { Pt(points[0].x, points[0].y) }

            val out = ArrayList<Pt>(n)
            var seg = 1
            for (k in 0 until n) {
                val target = (total * k) / (n - 1)
                while (seg < points.size - 1 && cum[seg] < target) seg++
                val a = points[seg - 1]
                val b = points[seg]
                val span = cum[seg] - cum[seg - 1]
                val t = if (span != 0.0) min(1.0, max(0.0, (target - cum[seg - 1]) / span)) else 0.0
                out.add(Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
            return out
        }

        /** Length-weighted centre and bounding box of a set of polylines. */
        fun extent(polylines: List<List<Pt>>): Extent? {
            var sx = 0.0
            var sy = 0.0
            var sw = 0.0
            var x0 = Double.POSITIVE_INFINITY
            var y0 = Double.POSITIVE_INFINITY
            var x1 = Double.NEGATIVE_INFINITY
            var y1 = Double.NEGATIVE_INFINITY
            for (pts in polylines) {
                for (i in pts.indices) {
                    val p = pts[i]
                    if (p.x < x0) x0 = p.x
                    if (p.x > x1) x1 = p.x
                    if (p.y < y0) y0 = p.y
                    if (p.y > y1) y1 = p.y
                    if (i == 0) continue
                    val q = pts[i - 1]
                    val w = dist(p, q)
                    sx += (w * (p.x + q.x)) / 2
                    sy += (w * (p.y + q.y)) / 2
                    sw += w
                }
            }
            if (!x0.isFinite()) return null
            // All taps and no lines: fall back to the plain mean.
            if (sw == 0.0) {
                var count = 0
                for (pts in polylines) {
                    for (p in pts) {
                        sx += p.x
                        sy += p.y
                        count++
                    }
                }
                return Extent(sx / count, sy / count, x1 - x0, y1 - y0)
            }
            return Extent(sx / sw, sy / sw, x1 - x0, y1 - y0)
        }

        /**
         * Dynamic time warping, at most [BAND] samples off the diagonal: the mean distance along the
         * cheapest walk through both strokes from start to end together.
         */
        internal fun warpedDistance(a: List<Pt>, b: List<Pt>, reversed: Boolean = false): Double {
            val n = a.size
            val m = b.size
            fun at(i: Int) = if (reversed) a[n - 1 - i] else a[i]
            val cost = Array(n) { DoubleArray(m) { Double.POSITIVE_INFINITY } }
            val steps = Array(n) { IntArray(m) }
            for (i in 0 until n) {
                val lo = max(0, floor((i.toDouble() * m) / n).toInt() - BAND)
                val hi = min(m - 1, ceil((i.toDouble() * m) / n).toInt() + BAND)
                for (j in lo..hi) {
                    val c = dist(at(i), b[j])
                    if (i == 0 && j == 0) {
                        cost[0][0] = c
                        steps[0][0] = 1
                        continue
                    }
                    var best = Double.POSITIVE_INFINITY
                    var len = 0
                    if (i > 0 && j > 0 && cost[i - 1][j - 1] < best) {
                        best = cost[i - 1][j - 1]
                        len = steps[i - 1][j - 1]
                    }
                    if (i > 0 && cost[i - 1][j] < best) {
                        best = cost[i - 1][j]
                        len = steps[i - 1][j]
                    }
                    if (j > 0 && cost[i][j - 1] < best) {
                        best = cost[i][j - 1]
                        len = steps[i][j - 1]
                    }
                    cost[i][j] = best + c
                    steps[i][j] = len + 1
                }
            }
            return cost[n - 1][m - 1] / steps[n - 1][m - 1]
        }
    }
}
