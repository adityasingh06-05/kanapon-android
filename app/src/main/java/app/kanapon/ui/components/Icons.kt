package app.kanapon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import app.kanapon.ui.theme.PaletteId

/** The theme button's three glyphs, from the design's 16-unit SVGs: sun, moon, filled disc. */
@Composable
fun ThemeIcon(id: PaletteId, modifier: Modifier, color: Color) {
    val rays = remember {
        PathParser().parsePathString(
            "M8 1.5v1.6M8 12.9v1.6M1.5 8h1.6M12.9 8h1.6M3.4 3.4l1.1 1.1M11.5 11.5l1.1 1.1M3.4 12.6l1.1-1.1M11.5 4.5l1.1-1.1"
        )
            .toPath()
    }
    val moon = remember { PathParser().parsePathString("M13 9.6A5.5 5.5 0 0 1 6.4 3 5.5 5.5 0 1 0 13 9.6Z").toPath() }
    Canvas(modifier) {
        scale(size.width / 16f, size.height / 16f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (id) {
                PaletteId.Light -> {
                    drawCircle(color, 2.8f, Offset(8f, 8f), style = stroke)
                    drawPath(rays, color, style = stroke)
                }
                PaletteId.Stone -> drawPath(moon, color, style = stroke)
                PaletteId.Sumi -> drawCircle(color, 5f, Offset(8f, 8f))
            }
        }
    }
}

/** A back chevron for the full-screen pages. */
@Composable
fun BackIcon(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.2f)
            lineTo(size.width * 0.32f, size.height * 0.5f)
            lineTo(size.width * 0.62f, size.height * 0.8f)
        }
        drawPath(p, color, style = Stroke(width = size.width / 12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
