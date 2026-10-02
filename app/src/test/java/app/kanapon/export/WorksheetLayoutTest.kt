package app.kanapon.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The layout table and the four smoke cases from the web app's scripts/worksheet-smoke.mjs. */
class WorksheetLayoutTest {
    @Test
    fun cellsRowsAndPagesMatchTheWebLayout() {
        data class Row(val paper: Paper, val per: Int, val cell: Double, val rows: Int, val pages: Int)
        listOf(
            Row(Paper.A4, 4, 120.32, 5, 10),
            Row(Paper.A4, 8, 60.16, 10, 5),
            Row(Paper.A4, 12, 40.11, 15, 4),
            Row(Paper.Letter, 4, 124.5, 5, 10),
            Row(Paper.Letter, 8, 62.25, 9, 6),
            Row(Paper.Letter, 12, 41.5, 13, 4)
        ).forEach { r ->
            val l = WorksheetLayout.of(r.paper, r.per, 46)
            assertEquals("$r cell", r.cell, l.cell, 0.005)
            assertEquals("$r rows", r.rows, l.rowsPerPage)
            assertEquals("$r pages", r.pages, l.pages)
        }
    }

    @Test
    fun spareHeightGoesIntoTheGapsThenTheMargins() {
        // A4 at four per row: 98.29pt spare over four gaps, under the 27pt cap, so the gaps take it all.
        val a4 = WorksheetLayout.of(Paper.A4, 4, 46)
        val bodyH = 841.89 - 66 - 40
        assertEquals(9.0 + (bodyH - (5 * a4.cell + 4 * 9)) / 4, a4.rowGap, 1e-9)
        assertEquals(66.0, a4.bodyTop, 1e-9)
        // Whatever the cap refuses is split evenly above and below the rows.
        listOf(Paper.A4, Paper.Letter).forEach { paper ->
            (4..12).forEach { per ->
                val l = WorksheetLayout.of(paper, per, 46)
                assertTrue(l.rowGap <= 36.0 + 1e-9)
                val bottom = l.bodyTop + l.rowsPerPage * l.cell + (l.rowsPerPage - 1) * l.rowGap
                assertEquals("$paper $per", l.pageH - WorksheetLayout.MARGIN - bottom, l.bodyTop - 66.0, 1e-9)
            }
        }
    }

    @Test
    fun theGuideFadesToNothingAcrossTheRow() {
        assertEquals(1.0, WorksheetLayout.guideOpacity(0, 8, 0.3), 0.0)
        assertEquals(0.3 * (1 - 1.0 / 7), WorksheetLayout.guideOpacity(1, 8, 0.3), 1e-12)
        assertEquals(0.0, WorksheetLayout.guideOpacity(7, 8, 0.3), 0.0)
        assertEquals(0.0, WorksheetLayout.guideOpacity(3, 1, 0.3), 0.0)
    }

    @Test
    fun titlesAndFileNames() {
        assertEquals("ひらがな練習", WorksheetLayout.sheetTitle("hiragana"))
        assertEquals("カタカナ練習", WorksheetLayout.sheetTitle("katakana"))
        assertEquals("かな練習 - 弱点", WorksheetLayout.sheetTitle("weak"))
        assertEquals("kanapon-weak-letter.pdf", WorksheetLayout.fileName("weak", Paper.Letter))
    }
}
