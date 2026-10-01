package app.kanapon.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/*
 * A Kotlin port of perfect-freehand 1.2.3's getStroke (https://github.com/steveruizok/perfect-freehand),
 * Copyright (c) 2021 Stephen Ruiz Ltd, MIT licence (assets/licenses/perfect-freehand-MIT.txt).
 *
 * Only what the app uses is carried over: no tapering, no custom easing, no simulated pressure,
 * round caps at both ends. The arithmetic, constants and loop bounds are kept as they are in the
 * original, floating-point accumulation included, so the outline matches the web app's ink.
 */
object Freehand {
    data class Options(
        val size: Double = 16.0,
        val thinning: Double = 0.5,
        val smoothing: Double = 0.5,
        val streamline: Double = 0.5,
        val last: Boolean = false
    )

    /** An input sample; pressure < 0 or NaN means "not given". */
    data class Input(val x: Double, val y: Double, val pressure: Double = Double.NaN)

    private const val FIXED_PI = PI + 0.0001
    private const val START_CAP_SEGMENTS = 13
    private const val END_CAP_SEGMENTS = 29
    private const val CORNER_CAP_SEGMENTS = 13
    private const val END_NOISE_THRESHOLD = 3
    private const val MIN_STREAMLINE_T = 0.15
    private const val STREAMLINE_T_RANGE = 0.85
    private const val MIN_RADIUS = 0.01
    private const val DEFAULT_FIRST_PRESSURE = 0.25
    private const val DEFAULT_PRESSURE = 0.5

    private class StrokePoint(
        val point: DoubleArray,
        val pressure: Double,
        var vector: DoubleArray,
        val distance: Double,
        val runningLength: Double
    )

    /** The outline polygon around [points], as (x, y) pairs. */
    fun getStroke(points: List<Input>, options: Options): List<DoubleArray> = outline(strokePoints(points, options), options)

    private fun valid(p: Double) = !p.isNaN() && p >= 0

    private fun lrp(a: DoubleArray, b: DoubleArray, t: Double) = doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

    private fun uni(a: DoubleArray): DoubleArray {
        val l = hypot(a[0], a[1])
        return doubleArrayOf(a[0] / l, a[1] / l)
    }

    private fun dpr(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1]

    private fun per(a: DoubleArray) = doubleArrayOf(a[1], -a[0])

    private fun add(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] + b[0], a[1] + b[1])

    private fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1])

    private fun mul(a: DoubleArray, n: Double) = doubleArrayOf(a[0] * n, a[1] * n)

    private fun prj(a: DoubleArray, b: DoubleArray, c: Double) = doubleArrayOf(a[0] + b[0] * c, a[1] + b[1] * c)

    private fun dist2(a: DoubleArray, b: DoubleArray): Double {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        return dx * dx + dy * dy
    }

    private fun rotAround(a: DoubleArray, c: DoubleArray, r: Double): DoubleArray {
        val s = sin(r)
        val co = cos(r)
        val px = a[0] - c[0]
        val py = a[1] - c[1]
        return doubleArrayOf(px * co - py * s + c[0], px * s + py * co + c[1])
    }

    private fun radius(size: Double, thinning: Double, pressure: Double) = size * (0.5 - thinning * (0.5 - pressure))

    private fun strokePoints(input: List<Input>, o: Options): List<StrokePoint> {
        if (input.isEmpty()) return emptyList()
        val t = MIN_STREAMLINE_T + (1 - o.streamline) * STREAMLINE_T_RANGE

        // [x, y, pressure]; NaN pressure stands for a point that carries none.
        var pts: List<DoubleArray> = input.map { doubleArrayOf(it.x, it.y, it.pressure) }
        if (pts.size == 2) {
            // Extra points between the two, as in the original; lrp drops the pressure.
            val last = pts[1]
            val first = pts[0]
            pts = listOf(first) + (1 until 5).map { i -> lrp(first, last, i / 4.0).let { doubleArrayOf(it[0], it[1], Double.NaN) } }
        }
        if (pts.size == 1) {
            pts = listOf(pts[0], doubleArrayOf(pts[0][0] + 1, pts[0][1] + 1, pts[0][2]))
        }

        val out = ArrayList<StrokePoint>()
        out.add(
            StrokePoint(
                doubleArrayOf(pts[0][0], pts[0][1]),
                if (valid(pts[0][2])) pts[0][2] else DEFAULT_FIRST_PRESSURE,
                doubleArrayOf(1.0, 1.0),
                0.0,
                0.0
            )
        )
        var reachedMinimum = false
        var running = 0.0
        var prev = out[0]
        val max = pts.size - 1
        for (i in 1 until pts.size) {
            val point = if (o.last && i == max) doubleArrayOf(pts[i][0], pts[i][1]) else lrp(prev.point, pts[i], t)
            if (prev.point[0] == point[0] && prev.point[1] == point[1]) continue
            val distance = hypot(point[1] - prev.point[1], point[0] - prev.point[0])
            running += distance
            if (i < max && !reachedMinimum) {
                if (running < o.size) continue
                reachedMinimum = true
            }
            prev = StrokePoint(
                point,
                if (valid(pts[i][2])) pts[i][2] else DEFAULT_PRESSURE,
                uni(sub(prev.point, point)),
                distance,
                running
            )
            out.add(prev)
        }
        out[0].vector = if (out.size > 1) out[1].vector else doubleArrayOf(0.0, 0.0)
        return out
    }

    private fun outline(points: List<StrokePoint>, o: Options): List<DoubleArray> {
        val size = o.size
        if (points.isEmpty() || size <= 0) return emptyList()
        val totalLength = points[points.size - 1].runningLength
        val minDistance = (size * o.smoothing).pow(2)
        val leftPts = ArrayList<DoubleArray>()
        val rightPts = ArrayList<DoubleArray>()

        var r = radius(size, o.thinning, points[points.size - 1].pressure)
        var firstRadius: Double? = null
        var prevVector = points[0].vector
        var prevLeft = points[0].point
        var prevRight = prevLeft
        var tempLeft = prevLeft
        var tempRight = prevRight
        var prevSharp = false

        for (i in points.indices) {
            val sp = points[i]
            val point = sp.point
            val vector = sp.vector
            val isLast = i == points.size - 1
            if (!isLast && totalLength - sp.runningLength < END_NOISE_THRESHOLD) continue

            r = if (o.thinning != 0.0) radius(size, o.thinning, sp.pressure) else size / 2
            if (firstRadius == null) firstRadius = r
            r = max(MIN_RADIUS, r * 1.0) // no tapering

            val nextVector = (if (!isLast) points[i + 1] else sp).vector
            val nextDpr = if (!isLast) dpr(vector, nextVector) else 1.0
            val prevDpr = dpr(vector, prevVector)
            val isSharp = prevDpr < 0 && !prevSharp
            val isNextSharp = nextDpr < 0

            if (isSharp || isNextSharp) {
                val offset = mul(per(prevVector), r)
                val step = 1.0 / CORNER_CAP_SEGMENTS
                var t = 0.0
                while (t <= 1) {
                    tempLeft = rotAround(sub(point, offset), point, FIXED_PI * t)
                    leftPts.add(tempLeft)
                    tempRight = rotAround(add(point, offset), point, FIXED_PI * -t)
                    rightPts.add(tempRight)
                    t += step
                }
                prevLeft = tempLeft
                prevRight = tempRight
                if (isNextSharp) prevSharp = true
                continue
            }
            prevSharp = false

            if (isLast) {
                val offset = mul(per(vector), r)
                leftPts.add(sub(point, offset))
                rightPts.add(add(point, offset))
                continue
            }

            val offset = mul(per(lrp(nextVector, vector, nextDpr)), r)
            tempLeft = sub(point, offset)
            if (i <= 1 || dist2(prevLeft, tempLeft) > minDistance) {
                leftPts.add(tempLeft)
                prevLeft = tempLeft
            }
            tempRight = add(point, offset)
            if (i <= 1 || dist2(prevRight, tempRight) > minDistance) {
                rightPts.add(tempRight)
                prevRight = tempRight
            }
            prevVector = vector
        }

        val firstPoint = doubleArrayOf(points[0].point[0], points[0].point[1])
        val lastPoint = if (points.size > 1) {
            doubleArrayOf(points[points.size - 1].point[0], points[points.size - 1].point[1])
        } else {
            add(points[0].point, doubleArrayOf(1.0, 1.0))
        }

        if (points.size == 1) return dot(firstPoint, firstRadius ?: r)

        val startCap = ArrayList<DoubleArray>()
        if (rightPts.isNotEmpty()) {
            val step = 1.0 / START_CAP_SEGMENTS
            var t = step
            while (t <= 1) {
                startCap.add(rotAround(rightPts[0], firstPoint, FIXED_PI * t))
                t += step
            }
        }
        val direction = per(mul(points[points.size - 1].vector, -1.0))
        val endCap = ArrayList<DoubleArray>()
        val start = prj(lastPoint, direction, r)
        val step = 1.0 / END_CAP_SEGMENTS
        var t = step
        while (t < 1) {
            endCap.add(rotAround(start, lastPoint, FIXED_PI * 3 * t))
            t += step
        }

        return leftPts + endCap + rightPts.asReversed() + startCap
    }

    private fun dot(center: DoubleArray, radius: Double): List<DoubleArray> {
        val offsetPoint = add(center, doubleArrayOf(1.0, 1.0))
        val start = prj(center, uni(per(sub(center, offsetPoint))), -radius)
        val out = ArrayList<DoubleArray>()
        val step = 1.0 / START_CAP_SEGMENTS
        var t = step
        while (t <= 1) {
            out.add(rotAround(start, center, FIXED_PI * 2 * t))
            t += step
        }
        return out
    }
}
