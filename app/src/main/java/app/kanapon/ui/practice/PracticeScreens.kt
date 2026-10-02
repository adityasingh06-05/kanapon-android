package app.kanapon.ui.practice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.kanapon.ui.AppState
import app.kanapon.ui.Sheet
import app.kanapon.ui.components.Chip
import app.kanapon.ui.components.KanaGlyph
import app.kanapon.ui.components.OutlineButton
import app.kanapon.ui.components.Pressable
import app.kanapon.ui.components.Txt
import app.kanapon.ui.components.UnderlinedTxt
import app.kanapon.ui.theme.KanaponEasing
import app.kanapon.ui.theme.LocalPalette
import kotlin.math.abs
import kotlin.math.roundToInt

private fun Modifier.topHairline(color: Color) = drawBehind {
    drawLine(color, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx())
}

private val HINTS = listOf(
    "Trace the model. The shape and stroke count appear here when you lift the pen.",
    "The guide is fainter here. Swipe this row or tap a square to move between cells.",
    "From memory - no guide. Check yourself against the model afterwards."
)

/** Phone practice (design 1a-1c): one large cell at a time on a sliding track. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhonePractice(app: AppState) {
    val practice = app.practice
    val p = LocalPalette.current
    val k = practice.kana
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pressable(
                { app.open(Sheet.Picker) },
                Modifier.weight(1f),
                description = "Choose another character",
                contentAlignment = Alignment.BottomStart
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HeadGlyph(practice, 64.dp)
                    Column(Modifier.padding(bottom = 2.dp)) {
                        Txt(k.romaji, 19f, weight = 500, lineHeight = 1.3f)
                        Txt(kanaSub(practice), 13f, Modifier.padding(top = 2.dp), color = p.inkMuted, lineHeight = 1.4f)
                        UnderlinedTxt("choose another", 12.5f, Modifier.padding(top = 4.dp), lineHeight = 1.4f)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("‹", {
                    practice.step(-1)
                }, Modifier.width(44.dp), fontSize = 18f, padH = 0.dp, washWhenPressed = true, description = "Previous character")
                OutlineButton("›", {
                    practice.step(1)
                }, Modifier.width(44.dp), fontSize = 18f, padH = 0.dp, washWhenPressed = true, description = "Next character")
            }
        }

        if (practice.touchBlocked) TouchNote(practice)

        // The track runs edge to edge under the page's 20dp gutters, so the next cell just shows.
        BoxWithConstraints(
            Modifier
                .bleed(20.dp)
                .clipToBounds()
                .padding(horizontal = 20.dp)
        ) {
            val cellW = maxWidth
            val x by animateFloatAsState(
                -practice.cell * (cellW.value + 12f),
                tween(320, easing = KanaponEasing),
                label = "track"
            )
            Row(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .offset { IntOffset((x * density).roundToInt(), 0) },
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Only the cell in view takes ink; the edge of the next one shows but does not draw.
                CellSpec.entries.indices.forEach { i -> WritingCell(app, i, Modifier.width(cellW), active = i == practice.cell) }
            }
        }

        // Swipe this row, or tap a square, to change cell.
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                            val x0 = down.position.x
                            var last = down.position.x
                            while (true) {
                                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                last = c.position.x
                                if (!c.pressed) break
                            }
                            val dx = (last - x0) / density
                            if (abs(dx) > 40f) practice.cell = (practice.cell + if (dx < 0) 1 else -1).coerceIn(0, 3)
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val spec = CellSpec.entries[practice.cell]
            Txt("${spec.label} · ${practice.cell + 1} of 4", 12.5f, Modifier.weight(1f), color = p.inkMuted, lineHeight = 1.4f)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CellSpec.entries.forEachIndexed { i, c ->
                    val tint = if (practice.recall) Color.Transparent else p.guide(maxOf(0.12f, c.guide * 0.6f))
                    Pressable(
                        { practice.cell = i },
                        Modifier
                            .size(44.dp)
                            .background(p.sheet)
                            .border(1.dp, if (practice.cell == i) p.inkMuted else p.hairline),
                        role = Role.Tab,
                        selected = practice.cell == i,
                        description = "Cell ${i + 1}, ${c.label}"
                    ) {
                        KanaGlyph(k.char, Modifier.fillMaxSize(), color = tint)
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
                                .fillMaxWidth()
                                .size(width = 0.dp, height = 2.dp)
                                .alpha(if (practice.results.containsKey(i)) 1f else 0f)
                                .background(p.rule)
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .topHairline(p.hairline)
                .padding(top = 10.dp)
        ) {
            val r = practice.results[practice.cell]
            if (r != null) {
                ResultLines(r, 12.5f)
            } else {
                Txt(HINTS[minOf(practice.cell, 2)], 12.5f, color = p.inkMuted, lineHeight = 1.7f)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (-6).dp)
                .padding(bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            UndoClear(practice, 13f, 10.dp)
            Spacer(Modifier.weight(1f))
            PressureReadout(practice)
        }

        FlowRow(
            Modifier.offset(y = (-10).dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlineButton("How to write it", { app.open(Sheet.Strokes) })
            OutlineButton("Pen and guide", { app.open(Sheet.Pen) })
            Chip("recall ${if (practice.recall) "on" else "off"}", practice.recall, { practice.toggleRecall() })
        }
    }
}

/** Lays the content out [x] wider on both sides than its parent allows, into the parent's padding. */
private fun Modifier.bleed(x: Dp) = layout { measurable, constraints ->
    val extra = x.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** The phone's character picker sheet. */
@Composable
fun ColumnScope.PickerSheet(app: AppState) {
    val practice = app.practice
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(p.hairline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            },
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SetToggle(practice.set, { practice.switchSet(it) }, tabs = true, fontSize = 13f)
    }
    PickerGrid(app, cell = null, glyph = 28.dp) { app.closeSheet() }
}

/** The phone's "How to write it" sheet. */
@Composable
fun ColumnScope.StrokesSheet(app: AppState) {
    val p = LocalPalette.current
    val k = app.practice.kana
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(
            "${k.strokes} ${if (k.strokes == 1) "stroke" else "strokes"} · ${k.romaji}",
            12.5f,
            Modifier.padding(bottom = 12.dp),
            color = p.inkMuted
        )
        StrokeOrderSection(app, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectSquare(), 18.dp, 16.dp)
    }
}

/** The phone's "Pen and guide" sheet. */
@Composable
fun ColumnScope.PenSheet(app: AppState) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PenSliders(app, 92.dp, 13f)
        PenToggles(
            app,
            withRecall = false,
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .topHairline(p.hairline)
                .padding(top = 16.dp)
        )
        Txt(
            "Settings are kept on this device and survive a reset of your practice history.",
            11.5f,
            Modifier.padding(top = 12.dp),
            color = p.inkMuted,
            lineHeight = 1.65f
        )
    }
}

private fun Modifier.aspectSquare() = this.aspectRatio(1f)

/** Tablet practice (design 1d): picker | 2 x 2 cells | stroke order and pen settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabletPractice(app: AppState) {
    val practice = app.practice
    val p = LocalPalette.current
    val k = practice.kana
    Row(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Row(
                Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt("Gojūon", 14f, weight = 500, lineHeight = 1.3f)
                SetToggle(practice.set, { practice.switchSet(it) }, tabs = false, fontSize = 12.5f)
            }
            PickerGrid(app, cell = 44.dp, glyph = 24.dp) {}
        }

        Column(Modifier.weight(1f)) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                itemVerticalAlignment = Alignment.Bottom
            ) {
                HeadGlyph(practice, 88.dp)
                Column(Modifier.padding(bottom = 6.dp)) {
                    Txt(k.romaji, 19f, weight = 500, lineHeight = 1.3f)
                    Txt(kanaSub(practice), 13f, Modifier.padding(top = 4.dp), color = p.inkMuted, lineHeight = 1.4f)
                }
                // margin-left: auto. On its own line once it wraps, it still sits at the right.
                Row(
                    Modifier
                        .weight(1f)
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    UndoClear(practice, 12.5f, 6.dp)
                    PressureReadout(practice, Modifier.padding(start = 4.dp, end = 10.dp))
                    OutlineButton("Previous", { practice.step(-1) })
                    OutlineButton("Next", { practice.step(1) })
                }
            }
            if (practice.touchBlocked) TouchNote(practice, Modifier.padding(bottom = 16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (col in 0 until 2) {
                            val i = row * 2 + col
                            Column(Modifier.weight(1f)) {
                                WritingCell(app, i, Modifier.fillMaxWidth())
                                Txt(CellSpec.entries[i].label, 11.5f, Modifier.padding(top = 8.dp), color = p.inkMuted, lineHeight = 1.3f)
                                practice.results[i]?.let { r ->
                                    Column(
                                        Modifier
                                            .padding(top = 8.dp)
                                            .fillMaxWidth()
                                            .topHairline(p.hairline)
                                            .padding(top = 8.dp)
                                    ) { ResultLines(r, 11.5f) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.width(270.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Txt("How to write it", 14f, Modifier.alignByBaseline(), weight = 500, lineHeight = 1.3f)
                    Txt(
                        "${k.strokes} ${if (k.strokes == 1) "stroke" else "strokes"}",
                        12.5f,
                        Modifier.alignByBaseline(),
                        color = p.inkMuted,
                        lineHeight = 1.3f
                    )
                }
                StrokeOrderSection(app, Modifier.fillMaxWidth().aspectSquare(), 16.dp, 14.dp)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .topHairline(p.hairline)
                    .padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PenSliders(app, 84.dp, 12.5f)
                PenToggles(app, withRecall = true, Modifier.padding(top = 8.dp))
            }
        }
    }
}
