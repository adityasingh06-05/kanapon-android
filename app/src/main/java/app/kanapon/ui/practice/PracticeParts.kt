package app.kanapon.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kanapon.data.Gojuon
import app.kanapon.data.KanaSet
import app.kanapon.ui.AppState
import app.kanapon.ui.components.Chip
import app.kanapon.ui.components.KSlider
import app.kanapon.ui.components.KanaGlyph
import app.kanapon.ui.components.OutlineButton
import app.kanapon.ui.components.Pressable
import app.kanapon.ui.components.RuledCell
import app.kanapon.ui.components.Txt
import app.kanapon.ui.components.UnderlineButton
import app.kanapon.ui.components.UnderlinedTxt
import app.kanapon.ui.theme.LocalPalette
import app.kanapon.ui.theme.UiFont
import java.util.Locale

/** "3 strokes · hiragana", with " · character hidden" in recall mode. */
fun kanaSub(practice: PracticeState): String {
    val n = practice.kana.strokes
    return "$n ${if (n == 1) "stroke" else "strokes"} · ${practice.set.id}" + if (practice.recall) " · character hidden" else ""
}

/** The character at the head of the page, or a faint "?" in recall mode. */
@Composable
fun HeadGlyph(practice: PracticeState, size: Dp) {
    val p = LocalPalette.current
    if (practice.recall) {
        // Klee One carries no Latin, so the "?" falls back to the interface face, as on the web.
        Txt("?", size.value, Modifier.width(size), color = p.ruleFaint, family = UiFont, lineHeight = 0.9f, align = TextAlign.Center)
    } else {
        KanaGlyph(practice.kana.char, Modifier.size(size))
    }
}

/** One writing cell: ruling, the fading model, and the ink surface. */
@Composable
fun WritingCell(app: AppState, id: Int, modifier: Modifier, active: Boolean = true) {
    val practice = app.practice
    val p = LocalPalette.current
    val spec = CellSpec.entries[id]
    val g = practice.guideFor(spec)
    RuledCell(modifier.aspectRatio(1f), quarters = app.settings.quarters) {
        if (g > 0f) KanaGlyph(practice.kana.char, Modifier.fillMaxSize(), color = p.guide(g))
        InkCanvas(practice, id, app.settings.pen, app.settings.pressure, Modifier.fillMaxSize(), active)
    }
}

/** shape / strokes / note, under a cell or in the phone's result block. */
@Composable
fun ResultLines(r: CellResult, size: Float) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt("shape", size, lineHeight = 1.7f)
        Row {
            Txt("${r.score}%", size, weight = 500, lineHeight = 1.7f, tabular = true)
            if (r.notCounted != null) Txt(" · not counted", size, color = p.inkMuted, lineHeight = 1.7f)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt("strokes", size, lineHeight = 1.7f)
        Txt(
            "${r.drawn} of ${r.expected}",
            size,
            color = if (r.drawn == r.expected) p.ink else p.vermilionText,
            weight = 500,
            lineHeight = 1.7f,
            tabular = true
        )
    }
    Txt(r.note, size, Modifier.padding(top = 3.dp), color = p.inkMuted, lineHeight = 1.7f)
}

/** The live pen pressure. Its own scope, so drawing only recomposes this line. */
@Composable
fun PressureReadout(practice: PracticeState, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier) {
        Txt("pressure ", 11.5f, color = p.inkMuted, tabular = true, lineHeight = 1.2f)
        Txt("%.2f".format(Locale.ROOT, practice.pressure.floatValue), 11.5f, tabular = true, lineHeight = 1.2f)
    }
}

/** Shown once a palm has been turned away. */
@Composable
fun TouchNote(practice: PracticeState, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier
            .drawBehind { drawRect(p.vermilion, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }
            .padding(start = 10.dp)
    ) {
        Txt("Touch was ignored because a stylus has been used.", 12.5f, color = p.vermilionText, lineHeight = 1.65f)
        Pressable({ practice.allowTouch() }) {
            UnderlinedTxt("Turn finger drawing back on", 12.5f, color = p.vermilionText, lineHeight = 1.65f, offset = 2.dp)
        }
    }
}

/** hiragana / katakana, as underlined text (tablet) or as tabs with a 2px rule (phone sheet). */
@Composable
fun SetToggle(current: KanaSet, onPick: (KanaSet) -> Unit, tabs: Boolean, fontSize: Float, labels: (KanaSet) -> String = { it.id }) {
    val p = LocalPalette.current
    KanaSet.entries.forEach { s ->
        val on = s == current
        if (tabs) {
            Pressable(
                { onPick(s) },
                Modifier
                    .height(44.dp)
                    .drawBehind {
                        if (on) {
                            drawRect(
                                p.ink,
                                Offset(0f, size.height - 1.dp.toPx()),
                                androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())
                            )
                        }
                    },
                selected = on
            ) { Txt(labels(s), fontSize, color = if (on) p.ink else p.inkMuted, weight = if (on) 500 else 400, lineHeight = 1.2f) }
        } else {
            Pressable({ onPick(s) }, Modifier.height(32.dp), selected = on) {
                if (on) {
                    UnderlinedTxt(labels(s), fontSize, weight = 500, lineHeight = 1.2f, offset = 4.dp)
                } else {
                    Txt(labels(s), fontSize, color = p.inkMuted, lineHeight = 1.2f)
                }
            }
        }
    }
}

/**
 * The gojūon table to pick from: 5 columns, gaps kept. The character being practised has a
 * vermilion border, except in recall mode, where that would give it away.
 */
@Composable
fun PickerGrid(app: AppState, cell: Dp?, glyph: Dp, onPicked: () -> Unit) {
    val practice = app.practice
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Gojuon.rowsFor(practice.set).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { k ->
                    val m = if (cell != null) Modifier.size(cell) else Modifier.weight(1f).height(52.dp)
                    if (k == null) {
                        Box(m)
                    } else {
                        val act = k.char == practice.kana.char && !practice.recall
                        Pressable(
                            {
                                practice.goTo(practice.set, Gojuon.indexIn(practice.set, k.char))
                                onPicked()
                            },
                            m
                                .background(p.sheet)
                                .border(1.dp, if (act) p.vermilion else p.hairline),
                            description = "${k.char}, ${k.romaji}"
                        ) { KanaGlyph(k.char, Modifier.size(glyph), color = if (act) p.vermilionText else p.ink) }
                    }
                }
            }
        }
    }
}

/** A labelled range, as the pen settings lay them out. */
@Composable
fun LabelledSlider(
    label: String,
    labelWidth: Dp,
    fontSize: Float,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    enabled: Boolean = true,
    onChange: (Float) -> Unit
) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt(label, fontSize, Modifier.width(labelWidth), color = p.inkMuted, lineHeight = 1.3f)
        KSlider(value, onChange, min, max, step, Modifier.weight(1f), enabled = enabled, label = label)
    }
}

@Composable
fun PenSliders(app: AppState, labelWidth: Dp, fontSize: Float) {
    val s = app.settings
    LabelledSlider("guide", labelWidth, fontSize, s.guide, 0f, 0.5f, 0.01f, enabled = !app.practice.recall) {
        app.updateSettings { copy(guide = it) }
    }
    LabelledSlider("pen width", labelWidth, fontSize, s.pen, 1f, 9f, 0.5f) { app.updateSettings { copy(pen = it) } }
    LabelledSlider("stroke speed", labelWidth, fontSize, s.strokeSpeed, 0.5f, 2f, 0.25f) {
        app.updateSettings { copy(strokeSpeed = it) }
    }
}

/** pressure, (tablet) recall mode, quarter ruling, and the way back to finger drawing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PenToggles(app: AppState, withRecall: Boolean, modifier: Modifier = Modifier) {
    val s = app.settings
    val practice = app.practice
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("pressure ${if (s.pressure) "on" else "off"}", s.pressure, { app.updateSettings { copy(pressure = !pressure) } })
        if (withRecall) Chip("recall mode ${if (practice.recall) "on" else "off"}", practice.recall, { practice.toggleRecall() })
        Chip("quarter ruling ${if (s.quarters) "on" else "off"}", s.quarters, { app.updateSettings { copy(quarters = !quarters) } })
        if (practice.touchBlocked) Chip("touch drawing is off - turn it back on", false, { practice.allowTouch() })
    }
}

/** How to write it: the diagram, or the recall-mode cover with a way to look anyway. */
@Composable
fun StrokeOrderSection(app: AppState, modifier: Modifier, buttonPadH: Dp, buttonsTop: Dp) {
    val practice = app.practice
    val p = LocalPalette.current
    val k = practice.kana
    if (practice.recall && !practice.revealed) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Txt("Hidden while recall mode is on.", 13f, color = p.inkMuted, align = TextAlign.Center)
            OutlineButton("Show stroke order", { practice.revealed = true })
        }
    } else {
        val order = app.strokeData.order[k.char] ?: return
        StrokeOrderView(k.char, order, app.settings.strokeSpeed, modifier, buttonPadH, buttonsTop)
    }
}

@Composable
fun UndoClear(practice: PracticeState, fontSize: Float, padH: Dp) {
    UnderlineButton("undo", { practice.undo() }, fontSize = fontSize, padH = padH)
    UnderlineButton("clear", { practice.clearAll() }, fontSize = fontSize, padH = padH)
}
