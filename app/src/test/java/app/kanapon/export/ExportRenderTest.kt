package app.kanapon.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import app.kanapon.TestData
import app.kanapon.data.Gojuon
import app.kanapon.data.KanaSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ExportRenderTest {
    /**
     * PdfDocument has no native side under Robolectric, so the page painter is checked on a bitmap:
     * the same calls the PDF page canvas receives, at 2px to the point.
     */
    @Test
    fun aWorksheetPagePaintsItsRulingAndFadingModels() {
        val spec = WorksheetSpec(Gojuon.listFor(KanaSet.Hiragana), 8, 0.3, true, Paper.A4, "hiragana")
        val l = WorksheetLayout.of(Paper.A4, 8, spec.chars.size)
        val bmp = Bitmap.createBitmap(1191, 1684, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(2f, 2f)
        WorksheetPainter(Typeface.SERIF, Typeface.SANS_SERIF).paint(c, spec, l, 0)

        fun px(x: Double, y: Double) = bmp.getPixel((x * 2).toInt(), (y * 2).toInt())
        fun inkIn(cellIndex: Int, row: Int): Int {
            val x0 = l.margin + cellIndex * l.cell
            val y0 = l.bodyTop + row * (l.cell + l.rowGap)
            var darkest = 255
            for (dy in 4 until (l.cell * 2).toInt() - 4) {
                for (dx in 4 until (l.cell * 2).toInt() - 4) {
                    val p = bmp.getPixel((x0 * 2).toInt() + dx, (y0 * 2).toInt() + dy)
                    darkest = minOf(darkest, Color.red(p))
                }
            }
            return darkest
        }
        // The rule along the first cell's left edge.
        val edge = px(l.margin, l.bodyTop + l.cell / 2)
        assertTrue("rule at the cell edge: ${Integer.toHexString(edge)}", Color.green(edge) < 0xF0)
        // Solid model in the first cell, fading across the row, nothing in the last.
        val first = inkIn(0, 0)
        val third = inkIn(2, 0)
        val last = inkIn(7, 0)
        assertTrue("model is dark: $first", first < 0x40)
        assertTrue("guide is fainter: $third", third in (first + 1)..0xF0)
        assertTrue("last cell holds only ruling: $last", last > 0xB0)
    }

    @Test
    fun theJpegIsTheGlyphOnItsRuling() {
        val svg = TestData.assetText("glyphs/304b.svg") // か
        val bmp = AndroidExporter.renderGlyph(svg, 2048)
        assertEquals(2048, bmp.width)
        // The sheet in a corner, the rule along the border, ink somewhere in the glyph's body.
        assertEquals(Color.rgb(0xFF, 0xFE, 0xFB), bmp.getPixel(100, 100) or 0xFF000000.toInt())
        assertEquals(Color.rgb(0x8F, 0xA8, 0x93), bmp.getPixel(2, 1024) or 0xFF000000.toInt())
        var ink = 0
        for (y in 0 until 2048 step 8) for (x in 0 until 2048 step 8) if (bmp.getPixel(x, y) == Color.rgb(0x20, 0x25, 0x21)) ink++
        assertTrue("ink pixels $ink", ink > 1000)
    }
}
