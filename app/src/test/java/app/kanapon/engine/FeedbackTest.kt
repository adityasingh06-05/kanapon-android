package app.kanapon.engine

import app.kanapon.TestData
import app.kanapon.isNullJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackTest {
    private fun order(o: JsonObject?): Order? = o?.let {
        Order(
            it["swapped"]!!.jsonArray.map { p -> p.jsonArray[0].jsonPrimitive.int to p.jsonArray[1].jsonPrimitive.int },
            it["reversed"]!!.jsonArray.map { r -> r.jsonPrimitive.int }
        )
    }

    @Test
    fun matchesTheWebNotes() {
        for (e in TestData.fixture("feedback.json").map { it.jsonObject }) {
            val diag = e["diagnostics"]?.takeUnless { it.isNullJson() }?.jsonObject?.let {
                Diagnostics(it["dx"]!!.jsonPrimitive.double, it["dy"]!!.jsonPrimitive.double, it["extentRatio"]!!.jsonPrimitive.double)
            }
            val strokes = e["strokes"]?.jsonArray?.map {
                val o = it.jsonObject
                StrokeDetail(
                    o["ref"]!!.jsonPrimitive.int,
                    o["d"]!!.jsonPrimitive.double,
                    o["score"]!!.jsonPrimitive.double,
                    o["reversed"]!!.jsonPrimitive.boolean
                )
            }
            val ord = order(e["order"]?.jsonObject)
            val drawn = e["drawn"]!!.jsonPrimitive.int
            val expected = e["expected"]!!.jsonPrimitive.int
            assertEquals(
                e.toString(),
                e["note"]!!.jsonPrimitive.content,
                Feedback.describeAttempt(
                    e["score"]!!.jsonPrimitive.int,
                    e["prevBest"]!!.jsonPrimitive.int,
                    drawn,
                    expected,
                    diag,
                    ord,
                    strokes
                )
            )
            assertEquals(e.toString(), e["notCounted"]?.jsonPrimitive?.contentOrNull, Feedback.whyNotCounted(drawn, expected, ord))
        }
    }

    @Test
    fun toScoreRoundsHalfUp() {
        assertEquals(100, Feedback.toScore(1.0))
        assertEquals(50, Feedback.toScore(0.5))
        assertEquals(13, Feedback.toScore(0.125))
        assertEquals(0, Feedback.toScore(0.0))
    }
}
