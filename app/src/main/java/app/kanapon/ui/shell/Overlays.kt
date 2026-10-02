package app.kanapon.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kanapon.ui.components.Txt
import app.kanapon.ui.components.UnderlineButton
import app.kanapon.ui.components.box
import app.kanapon.ui.theme.KanaponEasing
import app.kanapon.ui.theme.LocalPalette

private val Scrim = Color(10, 12, 10).copy(alpha = 0.32f)

/** The dimmed backdrop behind a sheet or popover; a tap on it closes them. */
@Composable
fun Backdrop(open: Boolean, onClose: () -> Unit) {
    val a by animateFloatAsState(if (open) 1f else 0f, tween(240), label = "backdrop")
    if (a == 0f && !open) return
    Box(
        Modifier
            .fillMaxSize()
            .alpha(a)
            .background(Scrim)
            .then(if (open) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClose) else Modifier)
    )
}

/**
 * The phone's bottom sheet: slides up over everything, 86% of the height at most, with a title and
 * "done". It keeps drawing its last content while it slides away.
 */
@Composable
fun BottomSheet(open: Boolean, title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val t by animateFloatAsState(if (open) 0f else 1.05f, tween(300, easing = KanaponEasing), label = "sheet")
    if (t >= 1.05f && !open) return
    val nav = WindowInsets.navigationBars.asPaddingValues()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .capHeight(0.86f)
                .graphicsLayer { translationY = size.height * t }
                .semantics {
                    paneTitle = title
                    isTraversalGroup = true
                }
                .background(p.sheet, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .drawBehind { drawLine(p.hairline, Offset(8.dp.toPx(), 0.5f), Offset(size.width - 8.dp.toPx(), 0.5f), 1.dp.toPx()) }
                .clickable(remember { MutableInteractionSource() }, null) {}
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt(title, 14f, Modifier.weight(1f), weight = 500)
                UnderlineButton("done", onClose)
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp + nav.calculateBottomPadding()),
                content = content
            )
        }
    }
}

/** Height-capped, content-sized: the sheet hugs short content and scrolls long content. */
private fun Modifier.capHeight(fraction: Float): Modifier = this.layout { measurable, constraints ->
    val max = (constraints.maxHeight * fraction).toInt()
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = max))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** The tablet's appearance popover, under the theme button. */
@Composable
fun Popover(open: Boolean, top: Dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val a by animateFloatAsState(if (open) 1f else 0f, tween(160), label = "popover")
    if (a == 0f && !open) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Column(
            Modifier
                .padding(top = top, end = 24.dp)
                .width(320.dp)
                .graphicsLayer {
                    alpha = a
                    translationY = (1 - a) * -4.dp.toPx()
                }
                .box(p.sheet, p.hairline, RoundedCornerShape(8.dp))
                .clickable(remember { MutableInteractionSource() }, null, enabled = open) {}
                .padding(16.dp),
            content = content
        )
    }
}

/** A short confirmation over the bottom of the screen. */
@Composable
fun Toast(text: String, on: Boolean, bottom: Dp) {
    val p = LocalPalette.current
    val a by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "toast")
    if (a == 0f) return
    Box(Modifier.fillMaxSize().padding(bottom = bottom), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .alpha(a)
                .background(p.ink, RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) { Txt(text, 13f, color = p.paper, lineHeight = 1.4f, maxLines = 1) }
    }
}
