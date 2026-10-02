package app.kanapon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kanapon.ui.theme.LocalPalette

/**
 * Genkō-yōshi ruling: a dotted crosshair through the centre (3px on, 4px off, as the design's
 * repeating gradients draw it) and, with [quarters], fainter dotted lines at 25% and 75%.
 * Drawn inside a [border]-wide frame, the way CSS paints a background inside its border.
 */
fun DrawScope.drawRuling(color: Color, quarters: Boolean = false, border: Dp = 1.dp, on: Dp = 3.dp, off: Dp = 4.dp) {
    val b = border.toPx()
    val line = 1.dp.toPx()
    val w = size.width - 2 * b
    val h = size.height - 2 * b
    fun hLine(frac: Float, dash: PathEffect) {
        val y = b + (h - line) * frac + line / 2
        drawLine(color, Offset(b, y), Offset(b + w, y), strokeWidth = line, pathEffect = dash)
    }
    fun vLine(frac: Float, dash: PathEffect) {
        val x = b + (w - line) * frac + line / 2
        drawLine(color, Offset(x, b), Offset(x, b + h), strokeWidth = line, pathEffect = dash)
    }
    val main = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx()))
    hLine(0.5f, main)
    vLine(0.5f, main)
    if (quarters) {
        val q = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
        hLine(0.25f, q)
        hLine(0.75f, q)
        vLine(0.25f, q)
        vLine(0.75f, q)
    }
}

/** A writing cell: the sheet, a rule-coloured border and the ruling, with [content] on top. */
@Composable
fun RuledCell(
    modifier: Modifier = Modifier,
    quarters: Boolean = false,
    sheet: Color = LocalPalette.current.sheet,
    border: Color = LocalPalette.current.rule,
    ruling: Color = LocalPalette.current.ruleFaint,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier
            .background(sheet)
            .border(1.dp, border)
            .drawBehind { drawRuling(ruling, quarters) },
        content = content
    )
}
