package app.kanapon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kanapon.data.ThemeMode
import app.kanapon.ui.AppState
import app.kanapon.ui.Page
import app.kanapon.ui.components.BackIcon
import app.kanapon.ui.components.OptionBox
import app.kanapon.ui.components.Pressable
import app.kanapon.ui.components.Txt
import app.kanapon.ui.components.UnderlineButton
import app.kanapon.ui.components.UnderlinedTxt
import app.kanapon.ui.theme.KanaFont
import app.kanapon.ui.theme.LocalPalette
import app.kanapon.ui.theme.PaletteId
import app.kanapon.ui.theme.Palettes

/** Follow system, Light, Ink-stone, Sumi: each with an あ swatch in its own colours. */
@Composable
fun AppearanceOptions(app: AppState, systemDark: Boolean, compact: Boolean) {
    val p = LocalPalette.current
    val auto = app.settings.themeMode == ThemeMode.Auto
    val current = app.paletteId(systemDark)
    val nowAuto = if (systemDark) app.settings.dark else PaletteId.Light
    data class Opt(val id: PaletteId?, val name: String, val cap: String, val swatch: PaletteId, val on: Boolean)
    val opts = listOf(Opt(null, "Follow system", "Now ${nowAuto.label.lowercase()}", nowAuto, auto)) +
        PaletteId.entries.map { Opt(it, it.label, it.caption, it, !auto && current == it) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        opts.forEach { o ->
            val sw = Palettes.of(o.swatch)
            OptionBox(o.on, { app.chooseTheme(o.id) }, Modifier.fillMaxWidth(), minHeight = if (compact) 48.dp else 52.dp) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(if (compact) 26.dp else 30.dp)
                            .background(sw.paper)
                            .border(1.dp, sw.rule),
                        contentAlignment = Alignment.Center
                    ) { Txt("あ", if (compact) 14f else 16f, color = sw.ink, family = KanaFont, lineHeight = 1f) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(o.name, if (compact) 12.5f else 13f, weight = if (o.on) 500 else 400, lineHeight = 1.2f)
                        Txt(o.cap, if (compact) 11f else 11.5f, color = p.inkMuted, lineHeight = 1.3f)
                    }
                }
            }
        }
    }
}

/** The phone's settings sheet. */
@Composable
fun ColumnScope.SettingsSheet(app: AppState, systemDark: Boolean) {
    val p = LocalPalette.current
    Txt("Appearance", 12.5f, Modifier.padding(top = 4.dp, bottom = 10.dp), weight = 500)
    AppearanceOptions(app, systemDark, compact = false)
    Box(
        Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .drawBehind { drawLine(p.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(top = 16.dp)
    ) {
        Txt("No accounts. All practice data is stored on this device only.", 12.5f, color = p.inkMuted, lineHeight = 1.7f)
    }
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Pressable({ app.page = Page.Privacy }) { UnderlinedTxt("Privacy", 12.5f, color = p.vermilionText, offset = 2.dp) }
        Txt(" · ", 12.5f, color = p.inkMuted)
        Pressable({ app.page = Page.Credits }) { UnderlinedTxt("Credits", 12.5f, color = p.vermilionText, offset = 2.dp) }
    }
}

private class Source(
    val name: String,
    val covers: String,
    val by: String,
    val source: String,
    val licence: String,
    val note: String,
    val file: String
)

private val SOURCES = listOf(
    Source(
        "KanjiVG",
        "The stroke order animation, and the order, direction and division into strokes that your writing is checked " +
            "against. One path per stroke, in writing order, with the position of each stroke's number.",
        "Ulrich Apel and the KanjiVG project",
        "kanjivg.tagaini.net",
        "Creative Commons Attribution-Share Alike 3.0",
        "Share Alike applies to that data and anything derived from it, which in this app means the stroke order data and " +
            "the scoring reference, which takes its strokes from KanjiVG and its shapes from Klee One. It does not extend to " +
            "the code that reads them.",
        "licenses/KanjiVG-CC-BY-SA-3.0.txt"
    ),
    Source(
        "Klee One, Zen Kaku Gothic New",
        "The type. Klee One is every model character in the app - the practice cells, the chart, the saved images and the " +
            "worksheets - and the shape your writing is scored against, as well as the Japanese words. Zen Kaku Gothic New is " +
            "the interface.",
        "Fontworks Inc. and the respective font authors",
        "fonts.google.com",
        "SIL Open Font License 1.1",
        "The families are subsetted to the kana and the handful of kanji the app uses, and ship inside the app.",
        "licenses/KleeOne-OFL.txt"
    ),
    Source(
        "perfect-freehand",
        "The outline of the ink: how the points your pen reports become a smooth line whose width follows the pressure.",
        "Steve Ruiz",
        "github.com/steveruizok/perfect-freehand",
        "MIT License",
        "Ported to Kotlin for this app; the arithmetic is unchanged, so the ink matches the web version.",
        "licenses/perfect-freehand-MIT.txt"
    )
)

/** Credits and Privacy: plain pages over everything, with a way back. */
@Composable
fun InfoPage(app: AppState, page: Page, tablet: Boolean) {
    val p = LocalPalette.current
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().background(p.paper)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (tablet) 60.dp else 52.dp)
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(p.hairline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                .padding(horizontal = if (tablet) 24.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pressable({ app.page = Page.Main }, Modifier.size(44.dp), description = "Back") { BackIcon(Modifier.size(20.dp), p.ink) }
            Txt(if (page == Page.Credits) "Credits" else "Privacy", 14f, weight = 500)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (tablet) 32.dp else 20.dp,
                    end = if (tablet) 32.dp else 20.dp,
                    top = if (tablet) 32.dp else 24.dp,
                    bottom = 40.dp + nav
                )
                .widthIn(max = 680.dp)
        ) {
            if (page == Page.Credits) Credits() else Privacy()
        }
    }
}

@Composable
private fun H1(text: String) = Txt(text, 28f, Modifier.padding(bottom = 8.dp), weight = 500, lineHeight = 1.3f)

@Composable
private fun Lede(text: String) =
    Txt(text, 14.5f, Modifier.padding(bottom = 28.dp), color = LocalPalette.current.inkMuted, lineHeight = 1.75f)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(p.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Txt(title, 17f, weight = 500, lineHeight = 1.4f)
        content()
    }
}

@Composable
private fun Credits() {
    val p = LocalPalette.current
    val context = LocalContext.current
    H1("Credits")
    Lede("Three bodies of other people's work make this app possible.")
    SOURCES.forEach { s ->
        var open by rememberSaveable(s.name) { mutableStateOf(false) }
        Section(s.name) {
            Txt(s.covers, 14f, lineHeight = 1.7f)
            listOf("By" to s.by, "Source" to s.source, "Licence" to s.licence).forEach { (k, v) ->
                Row {
                    Txt(k, 12.5f, Modifier.widthIn(min = 72.dp), color = p.inkMuted)
                    Txt(v, 12.5f)
                }
            }
            Txt(s.note, 12.5f, color = p.inkMuted, lineHeight = 1.7f)
            UnderlineButton(if (open) "Hide licence text" else "Full licence text", { open = !open }, padH = 0.dp, fontSize = 12.5f)
            if (open) {
                val text = remember(s.file) {
                    runCatching { context.assets.open(s.file).bufferedReader().readText() }.getOrDefault("")
                }
                BasicText(
                    text,
                    style = TextStyle(color = p.inkMuted, fontSize = 11.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

@Composable
private fun Privacy() {
    val p = LocalPalette.current
    H1("Privacy")
    Lede("Kanapon has no accounts, no servers and no analytics. What you write stays on this device.")
    Section("What is stored") {
        Txt(
            "Your practice history: for each kana, how many attempts and your best counted shape score; how many attempts on " +
                "each day; and the total number of strokes written. Separately, your preferences: pen width, guide strength, " +
                "pressure, quarter ruling, stroke speed and theme.",
            14f,
            lineHeight = 1.7f
        )
        Txt(
            "Both are kept in the app's private storage on this device. The ink itself is never stored.",
            14f,
            color = p.inkMuted,
            lineHeight = 1.7f
        )
    }
    Section("What leaves the device") {
        Txt(
            "Nothing, unless you save it. The app makes no network requests. Images and worksheets you save go to your " +
                "Downloads and Pictures folders, where other apps can see them.",
            14f,
            lineHeight = 1.7f
        )
    }
    Section("Removing it") {
        Txt(
            "Reset all local data on the Progress tab erases the history at once; your preferences stay. Uninstalling the app " +
                "removes both. If your device backs up app data to your Google account, the history may be included in that " +
                "backup, under your account's own settings.",
            14f,
            lineHeight = 1.7f
        )
    }
}
