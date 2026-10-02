package app.kanapon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kanapon.data.KLEE_BASELINE
import app.kanapon.data.KLEE_SIZE
import app.kanapon.data.KLEE_X
import app.kanapon.data.VIEWBOX
import app.kanapon.ui.theme.KanaFont
import app.kanapon.ui.theme.LocalPalette
import app.kanapon.ui.theme.UiFont

/** Text in the design's terms: size in CSS px (sp here), CSS line-height as a multiplier. */
fun kStyle(
    size: Float,
    color: Color = Color.Unspecified,
    weight: Int = 400,
    lineHeight: Float = 1.6f,
    family: FontFamily = UiFont,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    tabular: Boolean = false,
    align: TextAlign = TextAlign.Unspecified
): TextStyle = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = if (weight >= 500) FontWeight.Medium else FontWeight.Normal,
    fontFamily = family,
    lineHeight = (size * lineHeight).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    letterSpacing = letterSpacing,
    fontFeatureSettings = if (tabular) "tnum" else null,
    textAlign = align
)

@Composable
fun Txt(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.ink,
    weight: Int = 400,
    lineHeight: Float = 1.6f,
    family: FontFamily = UiFont,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    tabular: Boolean = false,
    align: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = kStyle(size, color, weight, lineHeight, family, letterSpacing, tabular, align),
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
    )
}

/**
 * Text with the design's underline: 1px under the baseline, [offset] below it, as CSS
 * text-underline-offset sets it (the platform underline cannot be moved).
 */
@Composable
fun UnderlinedTxt(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.ink,
    weight: Int = 400,
    lineHeight: Float = 1.6f,
    offset: Dp = 3.dp
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = text,
        modifier = modifier.drawBehind {
            val l = layout ?: return@drawBehind
            val y = l.firstBaseline + offset.toPx()
            val thickness = maxOf(1f, size.sp.toPx() / 14f)
            drawLine(color, Offset(0f, y), Offset(this.size.width, y), strokeWidth = thickness)
        },
        style = kStyle(size, color, weight, lineHeight),
        onTextLayout = { layout = it },
        maxLines = 1
    )
}

/**
 * One kana in Klee One at the placement every model uses (src/data/klee.js): the em is the box,
 * the glyph is centred across it and sits on a baseline 91.7/109 of the way down. Drawn this way
 * the guide lies exactly on the scoring reference, and every glyph in the app lines up the same.
 */
@Composable
fun KanaGlyph(char: String, modifier: Modifier, color: Color = LocalPalette.current.ink) {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    Canvas(modifier) {
        val box = size.minDimension
        drawKana(measurer, char, (size.width - box) / 2, (size.height - box) / 2, box, color)
    }
}

/** Draws [char] at the Klee placement in the square [box] px wide whose top left is ([left], [top]). */
fun DrawScope.drawKana(measurer: TextMeasurer, char: String, left: Float, top: Float, box: Float, color: Color) {
    if (box <= 0f) return
    val layout = measurer.measure(
        char,
        TextStyle(fontFamily = KanaFont, fontSize = (box * KLEE_SIZE / VIEWBOX).toSp(), letterSpacing = 0.em)
    )
    val x = left + box * KLEE_X / VIEWBOX - layout.size.width / 2f
    val y = top + box * KLEE_BASELINE / VIEWBOX - layout.firstBaseline
    drawText(layout, color = color, topLeft = Offset(x, y))
}
