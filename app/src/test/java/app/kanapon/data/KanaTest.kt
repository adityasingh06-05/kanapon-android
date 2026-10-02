package app.kanapon.data

import app.kanapon.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

class KanaTest {
    @Test
    fun ninetyTwoKanaInTwoAlignedTables() {
        assertEquals(92, Gojuon.TOTAL)
        assertEquals(46, Gojuon.listFor(KanaSet.Hiragana).size)
        assertEquals(46, Gojuon.listFor(KanaSet.Katakana).size)
        for (r in 0 until 11) {
            for (c in 0 until 5) {
                val pos = GridPos(r, c)
                assertEquals((Gojuon.at(KanaSet.Hiragana, pos) == null), (Gojuon.at(KanaSet.Katakana, pos) == null))
            }
        }
        assertEquals(KanaSet.Katakana, Gojuon.setOf("ン"))
        assertEquals(GridPos(10, 0), Gojuon.posOf(KanaSet.Hiragana, "ん"))
    }

    @Test
    fun strokeDataAgreesWithTheTables() {
        val data = TestData.strokeData
        for (k in Gojuon.ALL) {
            assertEquals(k.char, k.strokes, data.order.getValue(k.char).paths.size)
            assertEquals(k.char, k.strokes, data.order.getValue(k.char).numbers.size)
            assertEquals(k.char, k.strokes, data.reference.getValue(k.char).size)
        }
    }
}
