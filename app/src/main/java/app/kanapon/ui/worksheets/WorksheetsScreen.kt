package app.kanapon.ui.worksheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.kanapon.data.Gojuon
import app.kanapon.data.Kana
import app.kanapon.data.KanaSet
import app.kanapon.data.StatsMath
import app.kanapon.export.Paper
import app.kanapon.export.WorksheetLayout
import app.kanapon.export.WorksheetSpec
import app.kanapon.ui.AppState
import app.kanapon.ui.WsSet
import app.kanapon.ui.components.Chip
import app.kanapon.ui.components.KSlider
import app.kanapon.ui.components.OptionBox
import app.kanapon.ui.components.OutlineButton
import app.kanapon.ui.components.PageLede
import app.kanapon.ui.components.PageTitle
import app.kanapon.ui.components.PrimaryButton
import app.kanapon.ui.components.TabPage
import app.kanapon.ui.components.Txt
import app.kanapon.ui.components.drawKana
import app.kanapon.ui.components.topRule
import app.kanapon.ui.theme.KanaFont
import app.kanapon.ui.theme.LocalPalette
import app.kanapon.ui.theme.Print
import app.kanapon.ui.theme.UiFont
import app.kanapon.ui.theme.mixSrgb

/** What the worksheet will hold, from the options as they stand. */
private class WsModel(val chars: List<Kana>, val weak: List<Kana>, val layout: WorksheetLayout, val page: Int)

private fun model(app: AppState): WsModel {
    val weak = StatsMath.weakest(app.stats, 12)
    val chars = when (app.wsSet) {
        WsSet.Weak -> weak
        WsSet.Hiragana -> Gojuon.listFor(KanaSet.Hiragana)
        WsSet.Katakana -> Gojuon.listFor(KanaSet.Katakana)
    }
    val layout = WorksheetLayout.of(app.wsPaper, app.wsPer, chars.size)
    return WsModel(chars, weak, layout, minOf(app.wsPage, layout.pages - 1))
}

private fun plural(n: Int, one: String) = "$n ${if (n == 1) one else one + "s"}"

@Composable
fun WorksheetsScreen(app: AppState, tablet: Boolean) {
    val m = model(app)
    TabPage(tablet) {
        PageTitle("Printable worksheets")
        PageLede(
            "Genkō-yōshi ruling, a textbook model in the first cell, the guide fading across the row. The file is generated " +
                "on this device and never leaves it.",
            28.dp,
            620.dp
        )
        if (tablet) {
            Row(horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.width(420.dp)) { Options(app, m, tablet) }
                Column(Modifier.weight(1f)) { Preview(app, m) }
            }
        } else {
            Preview(app, m)
            Column(Modifier.padding(top = 32.dp)) { Options(app, m, tablet) }
        }
    }
}

@Composable
private fun Preview(app: AppState, m: WsModel) {
    val p = LocalPalette.current
    val l = m.layout
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Txt("Preview", 11.5f, Modifier.weight(1f), color = p.inkMuted, lineHeight = 1.3f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlineButton(
                "‹",
                { app.wsPage = maxOf(0, m.page - 1) },
                Modifier.width(44.dp),
                fontSize = 15f,
                padH = 0.dp,
                enabled =
                m.page > 0,
                description = "Previous page"
            )
            Txt("Page ${m.page + 1} of ${l.pages}", 11.5f, color = p.inkMuted, tabular = true, lineHeight = 1.3f, softWrap = false)
            OutlineButton(
                "›",
                { app.wsPage = minOf(l.pages - 1, m.page + 1) },
                Modifier.width(44.dp),
                fontSize = 15f,
                padH = 0.dp,
                enabled = m.page < l.pages - 1,
                description = "Next page"
            )
        }
        Txt(app.wsPaper.label, 11.5f, Modifier.weight(1f), color = p.inkMuted, lineHeight = 1.3f, align = TextAlign.End)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        SheetPreview(app, m, Modifier.widthIn(max = 460.dp).fillMaxWidth())
    }
    Txt(
        "The sheet keeps its paper colours in every theme - it is a preview of something that will be printed on white paper.",
        11.5f,
        Modifier.padding(top = 8.dp),
        color = p.inkMuted,
        lineHeight = 1.65f
    )
}

/** The page as it will print, scaled to the screen: the same layout the PDF uses. */
@Composable
private fun SheetPreview(app: AppState, m: WsModel, modifier: Modifier) {
    val p = LocalPalette.current
    val l = m.layout
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val title = WorksheetLayout.sheetTitle(app.wsSet.id)
    val rows = remember(m.chars, m.page, l.rowsPerPage) { m.chars.drop(m.page * l.rowsPerPage).take(l.rowsPerPage) }
    val per = app.wsPer
    val guide = app.wsGuide.toDouble()
    val numbers = app.wsNumbers
    Canvas(
        modifier
            .aspectRatio((l.pageW / l.pageH).toFloat())
            .background(Print.sheet)
            .border(1.dp, p.hairline)
            .semantics { contentDescription = "Preview of page ${m.page + 1}" }
    ) {
        val k = (size.width / l.pageW).toFloat()
        val margin = (l.margin * k).toFloat()
        fun text(s: String, sizePt: Float, family: androidx.compose.ui.text.font.FontFamily, color: androidx.compose.ui.graphics.Color) =
            measurer.measure(s, TextStyle(fontFamily = family, fontSize = (sizePt * k).toSp(), color = color, letterSpacing = 0.04.em))
        // Header: the title at left, name and date at right.
        val head = text(title, 9f, KanaFont, Print.inkMuted)
        val headTop = ((l.margin - 2) * k).toFloat()
        drawText(head, topLeft = Offset(margin, headTop))
        val fields = text("名前 ______________　日付 ____ / ____", 9f, UiFont, Print.inkMuted)
        drawText(fields, topLeft = Offset(size.width - margin - fields.size.width, headTop))

        val cell = (l.cell * k).toFloat()
        val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
        val line = 1.dp.toPx()
        rows.forEachIndexed { r, kana ->
            val top = ((l.bodyTop + r * (l.cell + l.rowGap)) * k).toFloat()
            for (i in 0 until per) {
                val left = margin + i * cell
                val c = Offset(left + cell / 2, top + cell / 2)
                drawLine(Print.ruleFaint, Offset(left, c.y), Offset(left + cell, c.y), line, pathEffect = dash)
                drawLine(Print.ruleFaint, Offset(c.x, top), Offset(c.x, top + cell), line, pathEffect = dash)
                drawRect(Print.rule, Offset(left, top), Size(cell, cell), style = Stroke(line))
                val o = WorksheetLayout.guideOpacity(i, per, guide).toFloat()
                if (o > 0.004f) drawKana(measurer, kana.char, left, top, cell, mixSrgb(Print.sheet, Print.ink, o))
                if (numbers && i == 0) {
                    drawText(
                        text(kana.strokes.toString(), 6f, UiFont, Print.inkMuted),
                        topLeft = Offset(
                            left + 3.dp.toPx(),
                            top + 2.dp.toPx()
                        )
                    )
                }
            }
            val rom = text(kana.romaji, 7.5f, UiFont, Print.inkMuted)
            drawText(rom, topLeft = Offset(margin + per * cell + 8 * k, top + (cell - rom.size.height) / 2))
        }
    }
}

@Composable
private fun Options(app: AppState, m: WsModel, tablet: Boolean) {
    val p = LocalPalette.current
    val l = m.layout
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column {
            Txt("Characters", 12.5f, Modifier.padding(bottom = 10.dp), weight = 500)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                val weakLabel = "My weakest 12" + if (m.weak.isNotEmpty() && m.weak.size < 12) " - ${m.weak.size} so far" else ""
                listOf(
                    WsSet.Hiragana to "All hiragana - 46",
                    WsSet.Katakana to "All katakana - 46",
                    WsSet.Weak to weakLabel
                ).forEach { (id, label) ->
                    val on = app.wsSet == id
                    OptionBox(on, {
                        app.wsSet = id
                        app.wsPage = 0
                    }, Modifier.fillMaxWidth()) {
                        Txt(
                            label,
                            13f,
                            Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                            weight = if (on) 500 else 400,
                            lineHeight = 1.4f
                        )
                    }
                }
            }
            if (app.wsSet == WsSet.Weak && m.weak.isEmpty()) {
                Txt(
                    "Nothing to rank yet. Write a few characters in practice and they will appear here weakest first.",
                    11.5f,
                    Modifier.padding(top = 8.dp),
                    color = p.inkMuted,
                    lineHeight = 1.65f
                )
            }
        }
        Column {
            Txt("Cells per row - ${app.wsPer}", 12.5f, weight = 500)
            KSlider(app.wsPer.toFloat(), { app.wsPer = it.toInt() }, 4f, 12f, 1f, label = "Cells per row")
        }
        Column {
            val pct = Math.round(app.wsGuide * 100)
            Txt("Guide strength - $pct%", 12.5f, weight = 500)
            KSlider(pct.toFloat(), { app.wsGuide = it / 100f }, 0f, 45f, 1f, label = "Guide strength")
            Txt("The guide fades to nothing by the last cell in each row.", 11.5f, color = p.inkMuted, lineHeight = 1.65f)
        }
        Column {
            Txt("Stroke count", 12.5f, Modifier.padding(bottom = 10.dp), weight = 500)
            Chip(if (app.wsNumbers) "shown on the model" else "hidden", app.wsNumbers, { app.wsNumbers = !app.wsNumbers })
        }
        Column {
            Txt("Paper", 12.5f, Modifier.padding(bottom = 10.dp), weight = 500)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Paper.entries.forEach { paper ->
                    val on = app.wsPaper == paper
                    OptionBox(on, { app.wsPaper = paper }) {
                        Txt(
                            paper.label,
                            13f,
                            Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                            weight = if (on) 500 else 400,
                            lineHeight = 1.4f
                        )
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().topRule(p.hairline).padding(top = 22.dp)) {
            val canSave = !app.wsBusy && m.chars.isNotEmpty()
            PrimaryButton(
                when {
                    app.wsBusy -> "Generating…"
                    tablet -> "Download PDF"
                    else -> "Save PDF"
                },
                {
                    app.saveWorksheet(
                        WorksheetSpec(m.chars, app.wsPer, app.wsGuide.toDouble(), app.wsNumbers, app.wsPaper, app.wsSet.id)
                    )
                },
                height = 48.dp,
                fullWidth = true,
                enabled = canSave
            )
            Txt(
                "${app.wsPaper.note} ${plural(l.pages, "page")}, ${plural(m.chars.size, "character")}.",
                11.5f,
                Modifier.padding(top = 8.dp),
                color = p.inkMuted,
                lineHeight = 1.65f
            )
        }
    }
}
