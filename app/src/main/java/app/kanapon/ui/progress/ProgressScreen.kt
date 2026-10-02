package app.kanapon.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.kanapon.data.Gojuon
import app.kanapon.data.StatsMath
import app.kanapon.ui.AppState
import app.kanapon.ui.Screen
import app.kanapon.ui.components.ControlShape
import app.kanapon.ui.components.KanaGlyph
import app.kanapon.ui.components.PageLede
import app.kanapon.ui.components.PageTitle
import app.kanapon.ui.components.Pressable
import app.kanapon.ui.components.SectionTitle
import app.kanapon.ui.components.TabPage
import app.kanapon.ui.components.Txt
import app.kanapon.ui.components.bottomRule
import app.kanapon.ui.components.kStyle
import app.kanapon.ui.components.topRule
import app.kanapon.ui.theme.LocalPalette
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val WEEKS = 26
private const val LIST_LIMIT = 16

/** Body copy with an inline link, as the empty states write them. */
@Composable
private fun LinkedLine(
    before: String,
    link: String,
    after: String,
    size: Float,
    lineHeight: Float,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val p = LocalPalette.current
    val text = buildAnnotatedString {
        append(before)
        withLink(
            LinkAnnotation.Clickable(
                "go",
                TextLinkStyles(SpanStyle(color = p.vermilionText, textDecoration = TextDecoration.Underline))
            ) { onClick() }
        ) { append(link) }
        append(after)
    }
    BasicText(text, modifier, style = kStyle(size, p.inkMuted, lineHeight = lineHeight))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(app: AppState, tablet: Boolean) {
    val p = LocalPalette.current
    val st = app.stats
    val today = app.today()
    val attempted = st.attempted
    val empty = attempted == 0
    val weeks = remember(st.days, today) { StatsMath.heatmap(st.days, today, WEEKS) }
    val max = remember(st.days) { st.days.values.maxOrNull() ?: 0 }
    val ranked = remember(st) { StatsMath.weakest(st) }
    val fmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    TabPage(tablet) {
        PageTitle("Progress")
        PageLede(
            "Everything below is stored on this device only. It is never uploaded, and deleting the app removes it.",
            if (app.storageBroken) 16.dp else 28.dp,
            620.dp
        )
        if (app.storageBroken) {
            Box(
                Modifier
                    .padding(bottom = 28.dp)
                    .drawBehind { drawRect(p.vermilion, size = Size(2.dp.toPx(), size.height)) }
                    .padding(start = 10.dp)
            ) {
                Txt(
                    "This device is refusing to store data. Anything written this session is not being saved.",
                    12.5f,
                    color = p.vermilionText,
                    lineHeight = 1.65f
                )
            }
        }

        FlowRow(
            Modifier
                .fillMaxWidth()
                .bottomRule(p.hairline)
                .padding(bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(64.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            val counters = listOf(
                StatsMath.streak(st.days, today).toString() to "day streak",
                attempted.toString() to "of ${Gojuon.TOTAL} characters attempted",
                NumberFormat.getIntegerInstance().format(st.strokes) to "strokes written"
            )
            counters.forEach { (n, label) ->
                Column {
                    Txt(n, 40f, weight = 500, lineHeight = 1f, tabular = true)
                    Txt(label, 12.5f, Modifier.padding(top = 8.dp), color = p.inkMuted, lineHeight = 1.3f)
                }
            }
        }
        Box(Modifier.height(30.dp))

        if (empty) {
            LinkedLine(
                "Nothing recorded yet. Every character you write in ",
                "practice",
                " is counted here - the day, the stroke count and your best shape score for each kana.",
                14f,
                1.75f,
                { app.nav(Screen.Practice) },
                Modifier.padding(bottom = 30.dp).widthIn(max = 560.dp)
            )
        }

        Column(Modifier.padding(bottom = 40.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                SectionTitle("Practice days", Modifier.weight(1f))
                val first = weeks.firstOrNull()?.firstOrNull()?.key?.let(LocalDate::parse)
                Txt(
                    if (first != null) "${fmt.format(first)} - ${fmt.format(today)}" else "last 26 weeks",
                    11.5f,
                    color = p.inkMuted,
                    lineHeight = 1.3f
                )
            }
            val scroll = rememberScrollState()
            LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
            Row(
                Modifier
                    .horizontalScroll(scroll)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                weeks.forEach { col ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        col.forEach { d ->
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(p.heat(StatsMath.bucket(d.count, max)))
                                    .border(1.dp, p.hairline)
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Txt("fewer", 11f, color = p.inkMuted, lineHeight = 1.2f)
                for (b in 0..4) Box(Modifier.size(12.dp).background(p.heat(b)).border(1.dp, p.hairline))
                Txt("more", 11f, color = p.inkMuted, lineHeight = 1.2f)
            }
        }

        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                SectionTitle("By character", Modifier.weight(1f))
                if (!empty) Txt("weakest first", 11.5f, color = p.inkMuted, lineHeight = 1.3f)
            }
            if (empty) {
                LinkedLine(
                    "This becomes a list of the kana you have written, weakest first, once there is something to rank. ",
                    "Pick one from the chart",
                    " to start.",
                    13.5f,
                    1.75f,
                    { app.nav(Screen.Chart) },
                    Modifier
                        .widthIn(max = 520.dp)
                        .topRule(p.hairline)
                        .padding(top = 18.dp)
                )
            } else {
                Column(Modifier.fillMaxWidth().topRule(p.hairline)) {
                    ranked.take(LIST_LIMIT).forEach { k ->
                        val c = st.chars.getValue(k.char)
                        Pressable(
                            { app.practise(k) },
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .bottomRule(p.hairline),
                            description = "${k.char}, ${k.romaji}, best ${c.best} percent",
                            contentAlignment = Alignment.CenterStart
                        ) { pressed ->
                            if (pressed) Box(Modifier.matchParentSize().background(p.ruleWash))
                            Row(
                                Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(Modifier.width(30.dp)) { KanaGlyph(k.char, Modifier.size(26.dp)) }
                                Txt(k.romaji, 12.5f, Modifier.width(34.dp), color = p.inkMuted, lineHeight = 1.3f)
                                Txt(
                                    "${c.attempts} ${if (c.attempts == 1) "attempt" else "attempts"}",
                                    12.5f,
                                    Modifier.width(74.dp),
                                    color = p.inkMuted,
                                    lineHeight = 1.3f,
                                    tabular = true
                                )
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .widthIn(min = 40.dp)
                                        .height(3.dp)
                                        .background(p.hairline)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(c.best.coerceIn(0, 100) / 100f)
                                            .background(p.rule)
                                    )
                                }
                                Txt(
                                    "best ${c.best}%",
                                    12.5f,
                                    Modifier.width(64.dp),
                                    lineHeight = 1.3f,
                                    tabular = true,
                                    align = TextAlign.End
                                )
                            }
                        }
                    }
                }
                val untouched = Gojuon.TOTAL - attempted
                val after = (
                    if (untouched == 0) {
                        "Every character has been attempted at least once."
                    } else {
                        "$untouched ${if (untouched == 1) "character" else "characters"} not yet attempted."
                    }
                    ) + if (attempted > LIST_LIMIT) " Showing the $LIST_LIMIT weakest of $attempted." else ""
                Txt(after, 12.5f, Modifier.padding(top = 16.dp), color = p.inkMuted)
            }
        }

        FlowRow(
            Modifier
                .padding(top = 40.dp)
                .fillMaxWidth()
                .topRule(p.hairline)
                .padding(top = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Txt(
                "Stored locally on this device. Resetting removes every attempt, score and practice day permanently.",
                11.5f,
                Modifier.widthIn(max = 520.dp),
                color = p.inkMuted,
                lineHeight = 1.7f
            )
            val disabled = st.isEmpty
            val armed = app.confirmingReset
            Pressable(
                { app.tapReset() },
                Modifier
                    .height(44.dp)
                    .alpha(if (disabled) 0.45f else 1f)
                    .border(1.dp, if (armed) p.vermilion else p.hairline, ControlShape),
                enabled = !disabled
            ) {
                Txt(
                    if (armed) "Tap again to erase everything" else "Reset all local data",
                    14f,
                    Modifier.padding(horizontal = 22.dp),
                    color = if (armed) p.vermilionText else p.ink,
                    lineHeight = 1.2f,
                    softWrap = false
                )
            }
        }
    }
}
