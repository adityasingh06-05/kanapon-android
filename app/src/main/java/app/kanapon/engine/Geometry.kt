package app.kanapon.engine

import kotlin.math.hypot

/** A point in whatever space the caller is working in: canvas pixels, or KanjiVG's 109 box. */
data class Pt(val x: Double, val y: Double)

internal fun dist(a: Pt, b: Pt): Double = hypot(a.x - b.x, a.y - b.y)

internal fun clamp(v: Double, lo: Double, hi: Double): Double = minOf(hi, maxOf(lo, v))

/**
 * An SVG path to a polyline, ported from the web app's src/lib/stroke-match.js. KanjiVG only uses
 * M and c and the scoring reference only M and implicit line-tos, but the other common commands are
 * cheap to support and keep this honest if the data changes. Cubics are flattened at [CURVE_STEPS].
 */
object PathParser {
    const val CURVE_STEPS = 16
    private val TOKEN = Regex("""[MmLlHhVvCcSsZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")

    fun parse(d: String): List<Pt> {
        val tokens = TOKEN.findAll(d).map { it.value }.toList()
        val pts = ArrayList<Pt>()
        var i = 0
        var cmd: String? = null
        var x = 0.0
        var y = 0.0
        var sx = 0.0
        var sy = 0.0
        var cx: Double? = null
        var cy: Double? = null

        fun num(): Double = tokens[i++].toDouble()

        fun cubic(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double) {
            for (s in 1..CURVE_STEPS) {
                val t = s.toDouble() / CURVE_STEPS
                val u = 1 - t
                pts.add(
                    Pt(
                        u * u * u * x + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                        u * u * u * y + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
                    )
                )
            }
            cx = x2
            cy = y2
            x = x3
            y = y3
        }

        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) cmd = tokens[i++]
            val c = cmd
            if (c == null) {
                i++
                continue
            }
            val rel = c == c.lowercase()
            val ox = if (rel) x else 0.0
            val oy = if (rel) y else 0.0
            when (c.lowercase()) {
                "m" -> {
                    x = ox + num()
                    y = oy + num()
                    sx = x
                    sy = y
                    pts.add(Pt(x, y))
                    cmd = if (rel) "l" else "L" // further pairs are implicit line-tos
                    cx = null
                    cy = null
                }
                "l" -> {
                    x = ox + num()
                    y = oy + num()
                    pts.add(Pt(x, y))
                    cx = null
                    cy = null
                }
                "h" -> {
                    x = ox + num()
                    pts.add(Pt(x, y))
                    cx = null
                    cy = null
                }
                "v" -> {
                    y = oy + num()
                    pts.add(Pt(x, y))
                    cx = null
                    cy = null
                }
                "c" -> {
                    val x1 = ox + num()
                    val y1 = oy + num()
                    val x2 = ox + num()
                    val y2 = oy + num()
                    val x3 = ox + num()
                    val y3 = oy + num()
                    cubic(x1, y1, x2, y2, x3, y3)
                }
                "s" -> {
                    val px = cx
                    val py = cy
                    val x1 = if (px == null) x else 2 * x - px
                    val y1 = if (py == null) y else 2 * y - py
                    val x2 = ox + num()
                    val y2 = oy + num()
                    val x3 = ox + num()
                    val y3 = oy + num()
                    cubic(x1, y1, x2, y2, x3, y3)
                }
                "z" -> {
                    x = sx
                    y = sy
                    pts.add(Pt(x, y))
                    cx = null
                    cy = null
                }
                else -> i++ // unknown token: skip rather than loop forever
            }
        }
        return pts
    }
}
