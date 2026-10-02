package app.kanapon.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import app.kanapon.R
import app.kanapon.data.KLEE_BASELINE
import app.kanapon.data.KLEE_SIZE
import app.kanapon.data.KLEE_X
import app.kanapon.data.Kana
import app.kanapon.data.KanaSet
import app.kanapon.data.VIEWBOX
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RULE = 0xFF8FA893.toInt()
private const val RULE_FAINT = 0xFFC6D2C6.toInt()
private const val MUTED = 0xFF5C665D.toInt()
private const val SHEET = 0xFFFFFEFB.toInt()
private const val INK = 0xFF202521.toInt()

/** The worksheet's model glyphs are solid tints, not transparency, so the ruling never shows through. */
private fun tint(o: Double): Int {
    fun ch(c: Int) = Math.round((1 - o * (1 - c / 255.0)) * 255).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(0x20) shl 16) or (ch(0x25) shl 8) or ch(0x21)
}

/**
 * Draws worksheet pages onto any Canvas in PostScript points, top-down, as the web app's
 * src/lib/worksheet-pdf.js draws them bottom-up.
 */
class WorksheetPainter(private val kana: Typeface, private val ui: Typeface) {
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = RULE
    }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = RULE_FAINT
        pathEffect = DashPathEffect(floatArrayOf(1.6f, 2.6f), 0f)
    }
    private val headRule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = RULE_FAINT
    }
    private fun text(face: Typeface, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
    }

    fun paint(c: Canvas, spec: WorksheetSpec, l: WorksheetLayout, page: Int) {
        val m = l.margin.toFloat()
        val title = WorksheetLayout.sheetTitle(spec.setName)
        val headY = m + 8
        c.drawText(title, m, headY, text(kana, 9f, MUTED))
        var cursor = (l.pageW - l.margin).toFloat()
        val labelPaint = text(kana, 8f, MUTED)
        for ((label, ruleW) in listOf("日付" to 72f, "名前" to 104f)) {
            cursor -= ruleW
            c.drawLine(cursor, headY + 2, cursor + ruleW, headY + 2, headRule)
            cursor -= labelPaint.measureText(label) + 5
            c.drawText(label, cursor, headY, labelPaint)
            cursor -= 14
        }
        if (l.pages > 1) {
            val label = "${page + 1} / ${l.pages}"
            val p = text(ui, 7f, MUTED)
            c.drawText(label, ((l.pageW - p.measureText(label)) / 2).toFloat(), (l.pageH - l.margin + 14).toFloat(), p)
        }
        val glyph = text(kana, 0f, INK)
        val number = text(ui, 6f, MUTED)
        val romaji = text(ui, 7.5f, MUTED)
        val cell = l.cell.toFloat()
        spec.chars.drop(page * l.rowsPerPage).take(l.rowsPerPage).forEachIndexed { r, k ->
            val top = (l.bodyTop + r * (l.cell + l.rowGap)).toFloat()
            for (i in 0 until spec.per) {
                val x = m + i * cell
                c.drawRect(x, top, x + cell, top + cell, rule)
                c.drawLine(x, top + cell / 2, x + cell, top + cell / 2, dash)
                c.drawLine(x + cell / 2, top, x + cell / 2, top + cell, dash)
                val o = WorksheetLayout.guideOpacity(i, spec.per, spec.guide)
                if (o > 0.004) {
                    val unit = cell / VIEWBOX
                    glyph.textSize = KLEE_SIZE * unit
                    glyph.color = tint(o)
                    c.drawText(k.char, x + KLEE_X * unit - glyph.measureText(k.char) / 2, top + KLEE_BASELINE * unit, glyph)
                }
            }
            if (spec.numbers) c.drawText(k.strokes.toString(), m + 3, top + 9, number)
            c.drawText(k.romaji, (l.margin + l.gridW + 8).toFloat(), top + cell / 2 + 3, romaji)
        }
    }
}

/** Saves into the shared Downloads and Pictures collections through MediaStore; no permission on API 29+. */
class AndroidExporter(private val context: Context) : Exporter {
    private val kanaFace by lazy { ResourcesCompat.getFont(context, R.font.klee_one_400) ?: Typeface.SERIF }

    private fun hex(k: Kana) = k.char.codePointAt(0).toString(16)

    private fun glyphFile(k: Kana, set: KanaSet, ext: String) = "kanapon-${set.id}-${k.romaji}.$ext"

    private fun save(name: String, mime: String, picture: Boolean, write: (OutputStream) -> Unit) {
        val resolver = context.contentResolver
        val collection: Uri = if (picture) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (picture) "Pictures/Kanapon" else "Download/Kanapon")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused $name")
        try {
            (resolver.openOutputStream(uri) ?: throw IOException("No stream for $name")).use(write)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    override suspend fun saveGlyphSvg(kana: Kana, set: KanaSet): String = withContext(Dispatchers.IO) {
        val bytes = context.assets.open("glyphs/${hex(kana)}.svg").use { it.readBytes() }
        val name = glyphFile(kana, set, "svg")
        save(name, "image/svg+xml", picture = false) { it.write(bytes) }
        name
    }

    override suspend fun saveGlyphJpeg(kana: Kana, set: KanaSet): String = withContext(Dispatchers.Default) {
        val svg = context.assets.open("glyphs/${hex(kana)}.svg").use { it.readBytes().decodeToString() }
        val bitmap = renderGlyph(svg, 2048)
        val name = glyphFile(kana, set, "jpg")
        withContext(Dispatchers.IO) {
            save(name, "image/jpeg", picture = true) { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }
        bitmap.recycle()
        name
    }

    override suspend fun saveWorksheet(spec: WorksheetSpec): Int = withContext(Dispatchers.Default) {
        val l = WorksheetLayout.of(spec.paper, spec.per, spec.chars.size)
        val doc = writeWorksheet(spec, l, kanaFace)
        try {
            withContext(Dispatchers.IO) {
                save(WorksheetLayout.fileName(spec.setName, spec.paper), "application/pdf", picture = false) { doc.writeTo(it) }
            }
        } finally {
            doc.close()
        }
        l.pages
    }

    companion object {
        /** One PdfDocument page per sheet. PdfDocument takes whole points, so A4 is 595 x 842. */
        fun writeWorksheet(spec: WorksheetSpec, l: WorksheetLayout, kana: Typeface): PdfDocument {
            val doc = PdfDocument()
            val painter = WorksheetPainter(kana, Typeface.SANS_SERIF)
            for (p in 0 until l.pages) {
                val info = PdfDocument.PageInfo.Builder(Math.round(l.pageW).toInt(), Math.round(l.pageH).toInt(), p + 1).create()
                val page = doc.startPage(info)
                page.canvas.drawColor(android.graphics.Color.WHITE)
                painter.paint(page.canvas, spec, l, p)
                doc.finishPage(page)
            }
            return doc
        }

        private val GLYPH =
            Regex(
                """<path d="([^"]*Q[^"]*)"\s+fill="#([0-9A-Fa-f]{6})"\s+transform="translate\(([-\d.]+) ([-\d.]+)\) scale\(([-\d.]+) ([-\d.]+)\)""""
            )

        /**
         * The saved image: the same file the SVG download is, painted at [px] square, as the web app
         * rasterises it. Sheet, dotted crosshair, rule border, then the Klee One outline.
         */
        fun renderGlyph(svg: String, px: Int): Bitmap {
            val bmp = createBitmap(px, px)
            val c = Canvas(bmp)
            c.drawColor(SHEET)
            c.scale(px / 1000f, px / 1000f)
            val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = RULE_FAINT
                pathEffect = DashPathEffect(floatArrayOf(14f, 18f), 0f)
            }
            c.drawLine(0f, 500f, 1000f, 500f, cross)
            c.drawLine(500f, 0f, 500f, 1000f, cross)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = RULE
            }
            c.drawRect(1f, 1f, 999f, 999f, border)
            val m = GLYPH.find(svg) ?: throw IOException("No outline in the glyph file")
            val (d, _, tx, ty, sx, sy) = m.destructured
            val path = PathParser().parsePathString(d).toPath().asAndroidPath()
            c.withTranslation(tx.toFloat(), ty.toFloat()) {
                scale(sx.toFloat(), sy.toFloat())
                drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK })
            }
            return bmp
        }
    }
}
