package app.kanapon.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.kanapon.ui.theme.LocalPalette

/** A scrolling tab page with the design's padding: 24/20/40 on a phone, 32/32/56 on a tablet. */
@Composable
fun TabPage(tablet: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = if (tablet) 32.dp else 20.dp,
                end = if (tablet) 32.dp else 20.dp,
                top = if (tablet) 32.dp else 24.dp,
                bottom = if (tablet) 56.dp else 40.dp
            ),
        content = content
    )
}

@Composable
fun PageTitle(text: String) = Txt(text, 28f, Modifier.padding(bottom = 8.dp), weight = 500, lineHeight = 1.3f, letterSpacing = (-0.01).em)

@Composable
fun PageLede(text: String, bottom: Dp, maxWidth: Dp) =
    Txt(text, 14.5f, Modifier.padding(bottom = bottom).widthIn(max = maxWidth), color = LocalPalette.current.inkMuted, lineHeight = 1.75f)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Txt(text, 14f, modifier, weight = 500, lineHeight = 1.3f)

fun Modifier.topRule(color: Color) = drawBehind {
    drawLine(color, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx())
}

fun Modifier.bottomRule(color: Color) = drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}
