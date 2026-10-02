package app.kanapon.export

import app.kanapon.data.Kana
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Paper sizes in PostScript points. */
enum class Paper(val label: String, val w: Double, val h: Double, val note: String) {
    A4("A4", 595.28, 841.89, "A4, portrait, 210 × 297 mm."),
    Letter("Letter", 612.0, 792.0, "US Letter, portrait, 8.5 × 11 in.")
}

/** What to print. [setName] is hiragana, katakana or weak, as the web app names them. */
data class WorksheetSpec(
    val chars: List<Kana>,
    val per: Int,
    val guide: Double,
    val numbers: Boolean,
    val paper: Paper,
    val setName: String
)

data class WorksheetLayout(
    val paper: Paper,
    val pageW: Double,
    val pageH: Double,
    val per: Int,
    val cell: Double,
    val gridW: Double,
    val margin: Double,
    val rowGap: Double,
    val bodyTop: Double,
    val rowsPerPage: Int,
    val pages: Int
) {
    companion object {
        const val MARGIN = 40.0 // ~14 mm, inside every printer's dead zone
        const val HEADER_H = 26.0
        const val LABEL_GUTTER = 34.0 // romaji at the end of each row
        const val ROW_GAP = 9.0

        /**
         * Worksheet geometry, shared by the PDF and the preview, ported from the web app's
         * src/lib/worksheet-layout.js. Rows sit at a 9pt gap; whatever height is left is spread into
         * the gaps up to a limit, and what the limit refuses is centred.
         */
        fun of(paper: Paper, per: Int, count: Int): WorksheetLayout {
            val gridW = paper.w - MARGIN * 2 - LABEL_GUTTER
            val cell = gridW / per
            val headTop = MARGIN + HEADER_H
            val bodyH = paper.h - headTop - MARGIN
            val rows = max(1, floor((bodyH + ROW_GAP) / (cell + ROW_GAP)).toInt())
            val used = rows * cell + (rows - 1) * ROW_GAP
            val spare = max(0.0, bodyH - used)
            val grow = if (rows > 1) min(spare / (rows - 1), ROW_GAP * 3) else 0.0
            return WorksheetLayout(
                paper = paper,
                pageW = paper.w,
                pageH = paper.h,
                per = per,
                cell = cell,
                gridW = gridW,
                margin = MARGIN,
                rowGap = ROW_GAP + grow,
                bodyTop = headTop + (spare - grow * (rows - 1)) / 2,
                rowsPerPage = rows,
                pages = max(1, ceil(count.toDouble() / rows).toInt())
            )
        }

        /** The model sits solid in the first cell; the guide fades to nothing by the last. */
        fun guideOpacity(i: Int, per: Int, strength: Double): Double = when {
            i == 0 -> 1.0
            per <= 1 -> 0.0
            else -> max(0.0, strength * (1 - i.toDouble() / (per - 1)))
        }

        fun sheetTitle(setName: String): String = when (setName) {
            "weak" -> "かな練習 - 弱点"
            "katakana" -> "カタカナ練習"
            else -> "ひらがな練習"
        }

        fun fileName(setName: String, paper: Paper) = "kanapon-$setName-${paper.label.lowercase()}.pdf"
    }
}

/** Saving the things the app makes. Implemented against MediaStore; absent in previews. */
interface Exporter {
    /** Saves the outline SVG and returns its file name. */
    suspend fun saveGlyphSvg(kana: Kana, set: app.kanapon.data.KanaSet): String

    /** Saves the 2048px JPEG and returns its file name. */
    suspend fun saveGlyphJpeg(kana: Kana, set: app.kanapon.data.KanaSet): String

    /** Writes the worksheet PDF and returns its page count. */
    suspend fun saveWorksheet(spec: WorksheetSpec): Int
}
