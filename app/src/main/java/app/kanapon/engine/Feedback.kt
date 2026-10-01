package app.kanapon.engine

import kotlin.math.abs

/*
 * Turning an attempt into one line of honest feedback, ported from the web app's src/lib/feedback.js.
 * Every branch is backed by something actually measured: the stroke count, the order and direction
 * each stroke was matched in, the previous best, or the placement from StrokeMatcher.
 */
object Feedback {
    /** Thresholds in fractions of the cell. */
    private const val OFFSET = 0.055
    private const val SMALL = 0.82
    private const val LARGE = 1.18

    fun describeAttempt(
        score: Int,
        prevBest: Int,
        drawn: Int,
        expected: Int,
        diagnostics: Diagnostics?,
        order: Order?,
        strokes: List<StrokeDetail>?
    ): String {
        if (drawn > expected) {
            return if (drawn == expected + 1) {
                "one stroke too many - check where two strokes join"
            } else {
                "${drawn - expected} strokes too many - check where strokes join"
            }
        }
        if (drawn < expected) {
            return if (expected - drawn == 1) "a stroke is missing" else "${expected - drawn} strokes are missing"
        }

        // Order and direction outrank a personal best: a best-yet drawn in the wrong order is still wrong.
        if (order != null && order.swapped.isNotEmpty()) {
            val (earlier, later) = order.swapped[0]
            return if (later == earlier + 1 && order.swapped.size == 1) {
                "stroke $later came before stroke $earlier - swap them"
            } else {
                "stroke $later came before stroke $earlier - check the order"
            }
        }
        if (order != null && order.reversed.isNotEmpty()) {
            val r = order.reversed
            return if (r.size == 1) {
                "stroke ${r[0]} runs the wrong way - start from the other end"
            } else {
                "strokes ${r.dropLast(1).joinToString(", ")} and ${r.last()} run the wrong way"
            }
        }

        if (prevBest > 0 && score > prevBest) return "your best yet"

        // Placement is only worth mentioning once there is a shape to place.
        val d = if (score >= 25) diagnostics else null
        if (d != null) {
            if (d.extentRatio < SMALL) return "sits small in the cell - fill more of the square"
            if (d.extentRatio > LARGE) return "overruns the cell - the model sits inside the ruling"
            if (abs(d.dx) > OFFSET || abs(d.dy) > OFFSET) {
                if (abs(d.dx) >= abs(d.dy)) {
                    return if (d.dx < 0) "sitting left of centre" else "sitting right of centre"
                }
                return if (d.dy < 0) "sitting high in the cell" else "sitting low in the cell"
            }
        }

        // Name the stroke that cost the most.
        var weakest: StrokeDetail? = null
        for (st in strokes.orEmpty()) {
            if (st.ref == 0) continue
            if (weakest == null || st.score < weakest.score) weakest = st
        }
        if (score < 90 && weakest != null && weakest.score < 0.8) {
            return "stroke ${weakest.ref} strays furthest from the model"
        }

        if (score >= 80) return "steady; proportions are holding"
        if (score >= 60) return "centred and the right size - the shape is still coming"
        return "the shape is not tracking the model yet"
    }

    /** Why an attempt may not set a best, or null if it may. Checked in the order describeAttempt reports them. */
    fun whyNotCounted(drawn: Int, expected: Int, order: Order?): String? = when {
        drawn != expected -> "stroke count"
        order != null && order.swapped.isNotEmpty() -> "stroke order"
        order != null && order.reversed.isNotEmpty() -> "stroke direction"
        else -> null
    }

    /** The stroke match, 0..1, as a percentage. Deliberately no curve; see the web app's feedback.js. */
    fun toScore(raw: Double): Int = Math.round(raw * 100).toInt()
}
