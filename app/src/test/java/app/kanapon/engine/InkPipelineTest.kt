package app.kanapon.engine

import app.kanapon.TestData
import kotlin.math.abs
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drawn ink against the web app's strokePath: the same curves, command for command. */
class InkPipelineTest {
    private class Recorder : InkPipeline.PathSink {
        val cmds = ArrayList<List<Any>>()

        override fun moveTo(x: Double, y: Double) {
            cmds += listOf("M", x, y)
        }

        override fun quadTo(x1: Double, y1: Double, x2: Double, y2: Double) {
            cmds += listOf("Q", x1, y1, x2, y2)
        }

        override fun close() {
            cmds += listOf("Z")
        }
    }

    @Test
    fun matchesTheWebOutlines() {
        val cases = TestData.fixture("ink.json").map { it.jsonObject }
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            val flat = c["pts"]!!.jsonArray.map { it.jsonPrimitive.double }
            val pts = (flat.indices step 3).map { InkPipeline.InkPoint(flat[it], flat[it + 1], flat[it + 2]) }
            val width = c["width"]!!.jsonPrimitive.double
            val pressure = c["pressure"]!!.jsonPrimitive.boolean
            val done = c["done"]!!.jsonPrimitive.boolean
            val rec = Recorder()
            InkPipeline.trace(InkPipeline.outline(pts, width, pressure, done), rec)
            val expected = c["path"]!!.jsonArray
            val tag = "${c["name"]} w=$width p=$pressure done=$done"
            assertEquals("$tag command count", expected.size, rec.cmds.size)
            expected.zip(rec.cmds).forEachIndexed { i, (e, got) ->
                val ea = e.jsonArray
                assertEquals("$tag cmd $i", ea[0].jsonPrimitive.content, got[0])
                for (k in 1 until ea.size) {
                    val want = ea[k].jsonPrimitive.double
                    val have = got[k] as Double
                    assertTrue("$tag cmd $i arg $k: $have != $want", abs(want - have) < 1e-6)
                }
            }
        }
    }

    @Test
    fun penWidthScalesWithTheCell() {
        assertEquals(3.2, InkPipeline.penWidth(3.2, 193.2), 1e-9)
        assertEquals(1.6, InkPipeline.penWidth(3.2, 50.0), 1e-9)
        assertEquals(9.6, InkPipeline.penWidth(3.2, 2000.0), 1e-9)
    }
}
