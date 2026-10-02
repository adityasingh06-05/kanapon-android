package app.kanapon.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kanapon.data.Gojuon
import app.kanapon.data.GridPos
import app.kanapon.data.Kana
import app.kanapon.data.KanaSet
import app.kanapon.ui.AppState
import app.kanapon.ui.ChartSel
import app.kanapon.ui.Sheet
import app.kanapon.ui.components.KanaGlyph
import app.kanapon.ui.components.OutlineButton
import app.kanapon.ui.components.PageLede
import app.kanapon.ui.components.PageTitle
import app.kanapon.ui.components.Pressable
import app.kanapon.ui.components.PrimaryButton
import app.kanapon.ui.components.RuledCell
import app.kanapon.ui.components.TabPage
import app.kanapon.ui.components.Txt
import app.kanapon.ui.components.topRule
import app.kanapon.ui.practice.SetToggle
import app.kanapon.ui.theme.KanaFont
import app.kanapon.ui.theme.LocalPalette

/** A chart cell: 96:78, but never shorter than 62dp. */
private fun Modifier.chartCell() = layout { m, c ->
    val w = c.maxWidth
    val h = maxOf(62.dp.roundToPx(), w * 78 / 96)
    val pl = m.measure(c.copy(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h))
    layout(w, h) { pl.place(0, 0) }
}

@Composable
fun ChartScreen(app: AppState, tablet: Boolean) {
    val p = LocalPalette.current
    TabPage(tablet) {
        PageTitle("Gojūon chart")
        PageLede(
            "Both syllabaries in dictionary order, with romaji and stroke count. Rows are 行, the consonant rows; " +
                "columns are 段, the vowel columns.",
            20.dp,
            496.dp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.widthIn(max = 496.dp).then(if (tablet) Modifier.weight(1f) else Modifier.fillMaxWidth())) {
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
                    SetToggle(app.chartSet, { app.chartSet = it }, tabs = true, fontSize = 14f, labels = { it.label })
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Gojuon.rowsFor(app.chartSet).forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEachIndexed { c, k ->
                                Box(Modifier.weight(1f).chartCell()) {
                                    if (k != null) ChartCell(app, k, r, c, tablet)
                                }
                            }
                        }
                    }
                }
            }
            if (tablet) {
                Column(Modifier.weight(1f).padding(top = 58.dp)) {
                    if (app.chartSel == null) {
                        Box(
                            Modifier
                                .widthIn(max = 420.dp)
                                .drawBehind {
                                    drawRoundRect(
                                        p.hairline,
                                        cornerRadius = CornerRadius(8.dp.toPx()),
                                        style = Stroke(
                                            1.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                                        )
                                    )
                                }
                                .padding(horizontal = 24.dp, vertical = 28.dp)
                        ) {
                            Txt(
                                "Choose a character to see it large, alongside its counterpart in the other syllabary.",
                                13f,
                                color = p.inkMuted,
                                lineHeight = 1.7f
                            )
                        }
                    } else {
                        CharacterDetail(app, tablet = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCell(app: AppState, k: Kana, r: Int, c: Int, tablet: Boolean) {
    val p = LocalPalette.current
    val sel = app.chartSel
    val on = sel != null && sel.set == app.chartSet && sel.pos.row == r && sel.pos.col == c
    Pressable(
        {
            app.chartSel = ChartSel(app.chartSet, GridPos(r, c))
            if (!tablet) app.open(Sheet.Detail)
        },
        Modifier.fillMaxSize(),
        description = "${k.char}, ${k.romaji}, ${k.strokes} strokes",
        selected = on
    ) { pressed ->
        Box(
            Modifier
                .matchParentSize()
                .background(if (on) p.vermilionWash else p.sheet)
                .border(1.dp, if (on || pressed) p.vermilion else p.hairline)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KanaGlyph(k.char, Modifier.size(30.dp), color = if (on) p.vermilionText else p.ink)
            Txt(k.romaji, 11f, color = p.inkMuted, lineHeight = 1.2f)
        }
        Txt(
            k.strokes.toString(),
            10f,
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 6.dp),
            color = p.inkMuted,
            lineHeight = 1f,
            tabular = true
        )
    }
}

/** "3 strokes · hiragana · あ行 · あ段", or the syllabic n's own line. */
fun facts(set: KanaSet, sel: ChartSel, k: Kana): String {
    val rows = Gojuon.rowsFor(set)
    val head = "${k.strokes} ${if (k.strokes == 1) "stroke" else "strokes"} · ${set.id} · "
    return head + if (sel.pos.row == Gojuon.N_ROW) {
        "syllabic n, in no 行"
    } else {
        "${rows[sel.pos.row][0]!!.char}行 · ${rows[0][sel.pos.col]!!.char}段"
    }
}

/** The character large, its counterpart beside it, the facts, and what to do with it. */
@Composable
fun CharacterDetail(app: AppState, tablet: Boolean) {
    val p = LocalPalette.current
    val sel = app.chartSel ?: return
    val k = Gojuon.at(sel.set, sel.pos) ?: return
    val other = sel.set.other
    val partner = Gojuon.at(other, sel.pos)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(if (tablet) 16.dp else 14.dp)) {
        RuledCell(Modifier.weight(1f, fill = false).widthIn(max = if (tablet) 300.dp else 240.dp).fillMaxWidth().aspectRatio(1f)) {
            KanaGlyph(k.char, Modifier.fillMaxSize())
        }
        if (partner != null) {
            Pressable(
                {
                    app.chartSel = sel.copy(set = other)
                    app.chartSet = other
                },
                description = "${partner.char}, ${other.id}"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RuledCell(Modifier.size(if (tablet) 120.dp else 92.dp)) { KanaGlyph(partner.char, Modifier.fillMaxSize()) }
                    Txt(other.nativeName, 11.5f, color = p.inkMuted, family = KanaFont, align = TextAlign.Center, lineHeight = 1.3f)
                }
            }
        }
    }
    Txt(k.romaji, 26f, Modifier.padding(top = if (tablet) 24.dp else 20.dp), weight = 500, lineHeight = 1.3f)
    Txt(facts(sel.set, sel, k), 12.5f, Modifier.padding(top = 4.dp), color = p.inkMuted)
    Column(
        Modifier
            .padding(top = if (tablet) 26.dp else 22.dp)
            .fillMaxWidth()
            .topRule(p.hairline)
            .padding(top = if (tablet) 22.dp else 20.dp)
    ) {
        PrimaryButton("Practise this character", { app.practise(k) }, height = if (tablet) 44.dp else 48.dp, fullWidth = !tablet)
        if (tablet) {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Download SVG", { app.saveGlyph(k, sel.set, jpeg = false) })
                OutlineButton("Download JPEG", { app.saveGlyph(k, sel.set, jpeg = true) })
            }
        } else {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Save SVG", { app.saveGlyph(k, sel.set, jpeg = false) }, Modifier.weight(1f), padH = 0.dp)
                OutlineButton("Save JPEG", { app.saveGlyph(k, sel.set, jpeg = true) }, Modifier.weight(1f), padH = 0.dp)
            }
        }
        Txt(
            "Vector outline, or a 2048px image. Both are the character alone on its ruling - no watermark.",
            11.5f,
            Modifier.padding(top = 10.dp).widthIn(max = 340.dp),
            color = p.inkMuted,
            lineHeight = 1.65f
        )
    }
}

/** The phone's detail sheet. */
@Composable
fun ColumnScope.DetailSheet(app: AppState) {
    Column { CharacterDetail(app, tablet = false) }
}
