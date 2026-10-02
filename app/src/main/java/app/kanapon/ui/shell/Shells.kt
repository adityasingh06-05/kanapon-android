package app.kanapon.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.kanapon.ui.AppState
import app.kanapon.ui.Screen
import app.kanapon.ui.Sheet
import app.kanapon.ui.chart.ChartScreen
import app.kanapon.ui.chart.DetailSheet
import app.kanapon.ui.components.Pressable
import app.kanapon.ui.components.ThemeIcon
import app.kanapon.ui.components.Txt
import app.kanapon.ui.practice.PenSheet
import app.kanapon.ui.practice.PhonePractice
import app.kanapon.ui.practice.PickerSheet
import app.kanapon.ui.practice.StrokesSheet
import app.kanapon.ui.practice.TabletPractice
import app.kanapon.ui.progress.ProgressScreen
import app.kanapon.ui.settings.AppearanceOptions
import app.kanapon.ui.settings.SettingsSheet
import app.kanapon.ui.theme.KanaFont
import app.kanapon.ui.theme.LocalPalette
import app.kanapon.ui.worksheets.WorksheetsScreen

private fun Modifier.hairlineBelow(color: Color) = drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}

private fun Modifier.hairlineAbove(color: Color) = drawBehind {
    drawLine(color, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx())
}

@Composable
private fun Wordmark() {
    Txt(
        "かなぽん",
        21f,
        Modifier.semantics { heading() },
        family = KanaFont,
        letterSpacing = 0.08.em,
        lineHeight = 1f
    )
}

@Composable
private fun ScreenContent(app: AppState, tablet: Boolean) {
    when (app.screen) {
        Screen.Practice -> if (tablet) TabletPractice(app) else PhonePractice(app)
        Screen.Chart -> ChartScreen(app, tablet)
        Screen.Progress -> ProgressScreen(app, tablet)
        Screen.Worksheets -> WorksheetsScreen(app, tablet)
    }
}

/** Phone: header, one screen, bottom tabs; sheets slide up over all of it. */
@Composable
fun PhoneShell(app: AppState, systemDark: Boolean) {
    val p = LocalPalette.current
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(p.paper)
                    .hairlineBelow(p.hairline)
                    .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                Pressable(
                    { app.toggleSettings() },
                    Modifier.size(44.dp),
                    description = "Appearance and settings"
                ) { pressed ->
                    if (pressed) Box(Modifier.matchParentSize().background(p.ruleWash, RoundedCornerShape(4.dp)))
                    ThemeIcon(app.paletteId(systemDark), Modifier.size(18.dp), p.inkMuted)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { ScreenContent(app, tablet = false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(p.paper)
                    .hairlineAbove(p.hairline)
                    .padding(bottom = nav)
            ) {
                Screen.entries.forEach { s ->
                    val current = app.screen == s
                    val bar by animateFloatAsState(if (current) 1f else 0f, tween(180), label = "tab")
                    Pressable({ app.nav(s) }, Modifier.weight(1f).height(52.dp), role = Role.Tab, selected = current) { pressed ->
                        if (pressed) Box(Modifier.matchParentSize().background(p.ruleWash))
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-1).dp)
                                .fillMaxWidth(0.56f)
                                .height(2.dp)
                                .alpha(bar)
                                .background(p.vermilion)
                        )
                        Txt(s.label, 12.5f, color = if (current) p.vermilionText else p.inkMuted, lineHeight = 1.2f)
                    }
                }
            }
        }
        Backdrop(app.sheet != null) { app.closeSheet() }
        val shown = app.lastSheet
        BottomSheet(app.sheet != null, shown.title, { app.closeSheet() }) {
            when (shown) {
                Sheet.Picker -> PickerSheet(app)
                Sheet.Strokes -> StrokesSheet(app)
                Sheet.Pen -> PenSheet(app)
                Sheet.Settings -> SettingsSheet(app, systemDark)
                Sheet.Detail -> DetailSheet(app)
            }
        }
        Toast(app.toastText, app.toastOn, bottom = 74.dp + nav)
    }
}

/** Tablet: the web layout, a header with tabs across the top and the theme in a popover. */
@Composable
fun TabletShell(app: AppState, systemDark: Boolean) {
    val p = LocalPalette.current
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(p.paper)
                    .hairlineBelow(p.hairline)
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.alignByBaseline()) { Wordmark() }
                    Txt("kanapon", 12.5f, Modifier.alignByBaseline(), color = p.inkMuted, lineHeight = 1.2f)
                }
                Row(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End)
                ) {
                    Screen.entries.forEach { s ->
                        val current = app.screen == s
                        Pressable(
                            { app.nav(s) },
                            Modifier
                                .fillMaxHeight()
                                .drawBehind {
                                    if (current) {
                                        val inset = 14.dp.toPx()
                                        drawRect(
                                            p.vermilion,
                                            topLeft = Offset(inset, size.height - 1.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, 2.dp.toPx())
                                        )
                                    }
                                },
                            role = Role.Tab,
                            selected = current
                        ) {
                            Txt(
                                s.label,
                                12.5f,
                                Modifier.padding(horizontal = 14.dp),
                                color = if (current) p.vermilionText else p.inkMuted,
                                lineHeight = 1.2f
                            )
                        }
                    }
                }
                Pressable(
                    { app.toggleSettings() },
                    Modifier
                        .size(44.dp)
                        .background(p.ruleWash, RoundedCornerShape(6.dp))
                        .border(1.dp, p.hairline, RoundedCornerShape(6.dp)),
                    description = "Appearance"
                ) { ThemeIcon(app.paletteId(systemDark), Modifier.size(16.dp), p.ink) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { ScreenContent(app, tablet = true) }
        }
        Backdrop(app.sheet != null) { app.closeSheet() }
        Popover(app.sheet == Sheet.Settings, top = status + 68.dp) {
            Txt("Appearance", 12.5f, Modifier.padding(bottom = 10.dp), weight = 500)
            AppearanceOptions(app, systemDark, compact = true)
            Txt(
                "No accounts. All practice data is stored on this device only.",
                11.5f,
                Modifier.padding(top = 14.dp),
                color = p.inkMuted,
                lineHeight = 1.65f
            )
        }
        Toast(app.toastText, app.toastOn, bottom = 32.dp)
    }
}
