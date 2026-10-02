package app.kanapon.engine

import app.kanapon.TestData
import app.kanapon.isNullJson
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin matcher against the web app's own matchStrokes and describeAttempt, on the synthetic
 * ink scripts/check-scoring.mjs builds (see tools/fixtures.mjs).
 */
class MatcherParityTest {
    private val matcher = StrokeMatcher(TestData.strokeData.reference)
    private val cases = TestData.fixture("matcher.json").map { it.jsonObject }

    private fun ink(c: JsonObject): List<List<Pt>> = c["ink"]!!.jsonArray.map { s ->
        val flat = s.jsonArray.map { it.jsonPrimitive.double }
        (flat.indices step 2).map { Pt(flat[it], flat[it + 1]) }
    }

    private fun pairs(a: JsonArray) = a.map { p -> p.jsonArray[0].jsonPrimitive.int to p.jsonArray[1].jsonPrimitive.int }

    @Test
    fun matchesTheWebMatcherOnEveryCase() {
        assertTrue("fixtures loaded", cases.size > 500)
        val failures = ArrayList<String>()
        for (c in cases) {
            val char = c["char"]!!.jsonPrimitive.content
            val name = c["name"]!!.jsonPrimitive.content
            val size = c["size"]!!.jsonPrimitive.double
            val strokes = ink(c)
            val r = matcher.match(strokes, char, size, size)!!
            val tag = "$name $char"
            val raw = c["raw"]!!.jsonPrimitive.double
            if (abs(r.raw - raw) > 1e-9) failures += "$tag raw ${r.raw} != $raw"
            if (Feedback.toScore(r.raw) != c["score"]!!.jsonPrimitive.int) failures += "$tag score"
            val d = c["diagnostics"]
            if (d.isNullJson()) {
                if (r.diagnostics != null) failures += "$tag diagnostics should be null"
            } else {
                val o = d!!.jsonObject
                val rd = r.diagnostics!!
                if (abs(rd.dx - o["dx"]!!.jsonPrimitive.double) > 1e-9) failures += "$tag dx"
                if (abs(rd.dy - o["dy"]!!.jsonPrimitive.double) > 1e-9) failures += "$tag dy"
                if (abs(rd.extentRatio - o["extentRatio"]!!.jsonPrimitive.double) > 1e-9) failures += "$tag extentRatio"
            }
            if (r.order.swapped != pairs(c["swapped"]!!.jsonArray)) failures += "$tag swapped ${r.order.swapped}"
            if (r.order.reversed != c["reversed"]!!.jsonArray.map { it.jsonPrimitive.int }) failures += "$tag reversed ${r.order.reversed}"
            val expectedStrokes = c["strokes"]!!.jsonArray.map { it.jsonObject }
            if (expectedStrokes.size != r.strokes.size) {
                failures += "$tag stroke count"
            } else {
                expectedStrokes.zip(r.strokes).forEachIndexed { i, (e, s) ->
                    if (s.ref != e["ref"]!!.jsonPrimitive.int) failures += "$tag stroke $i ref ${s.ref}"
                    if (abs(s.score - e["score"]!!.jsonPrimitive.double) > 1e-9) failures += "$tag stroke $i score"
                    if (s.reversed != e["reversed"]!!.jsonPrimitive.boolean) failures += "$tag stroke $i reversed"
                }
            }
            val expected = TestData.strokeData.reference.getValue(char).size
            val note = Feedback.describeAttempt(
                Feedback.toScore(r.raw),
                c["prevBest"]!!.jsonPrimitive.int,
                strokes.size,
                expected,
                r.diagnostics,
                r.order,
                r.strokes
            )
            if (note != c["note"]!!.jsonPrimitive.content) failures += "$tag note \"$note\""
            val why = Feedback.whyNotCounted(strokes.size, expected, r.order)
            if (why != c["notCounted"]?.jsonPrimitive?.contentOrNull) failures += "$tag notCounted $why"
        }
        assertEquals(failures.take(20).joinToString("\n"), 0, failures.size)
    }

    /** The exact properties scripts/check-scoring.mjs pins, checked on the Kotlin results. */
    @Test
    fun holdsTheScoringProperties() {
        for (c in cases) {
            val char = c["char"]!!.jsonPrimitive.content
            val size = c["size"]!!.jsonPrimitive.double
            val r = matcher.match(ink(c), char, size, size)!!
            val k = TestData.strokeData.reference.getValue(char).size
            when (c["name"]!!.jsonPrimitive.content) {
                "exact trace" -> {
                    assertTrue("$char exact ${r.raw}", Feedback.toScore(r.raw) >= 95)
                    assertEquals(Order(), r.order)
                }
                "swapped order" -> assertEquals(listOf(1 to 2), r.order.swapped)
                "reversed stroke" -> assertEquals(1, r.order.reversed.size)
                "offset right 10%" -> assertTrue("$char dx", abs(r.diagnostics!!.dx - 0.1) < 0.01)
                "missing stroke" -> assertTrue("$char missing", r.raw <= (k - 1).toDouble() / k + 1e-9)
            }
        }
    }

    @Test
    fun unknownCharacterOrEmptyCanvas() {
        assertEquals(null, matcher.match(listOf(listOf(Pt(1.0, 1.0))), "x", 100.0, 100.0))
        assertEquals(null, matcher.match(listOf(listOf(Pt(1.0, 1.0))), "あ", 0.0, 100.0))
        val empty = matcher.match(emptyList(), "あ", 100.0, 100.0)!!
        assertEquals(0.0, empty.raw, 0.0)
        assertEquals(null, empty.diagnostics)
    }
}
