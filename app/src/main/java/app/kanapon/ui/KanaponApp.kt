package app.kanapon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.kanapon.ui.settings.InfoPage
import app.kanapon.ui.shell.PhoneShell
import app.kanapon.ui.shell.TabletShell
import app.kanapon.ui.theme.LocalPalette
import app.kanapon.ui.theme.Palette

/**
 * The whole app. A wide, tall window (840 x 600 dp and up: a tablet in landscape) gets the web
 * layout in columns; anything else, a landscape phone included, gets the phone layout.
 */
@Composable
fun KanaponApp(app: AppState, systemDark: Boolean = isSystemInDarkTheme(), onDarkChange: (Boolean) -> Unit = {}) {
    val target = app.palette(systemDark)
    val palette = animatedPalette(target)
    LaunchedEffect(target.isDark) { onDarkChange(target.isDark) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { app.practice.finaliseAll() }
    BackHandler(enabled = app.sheet != null || app.page != Page.Main) {
        if (app.page != Page.Main) app.page = Page.Main else app.closeSheet()
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        BoxWithConstraints(Modifier.fillMaxSize().background(palette.paper)) {
            val tablet = maxWidth >= 840.dp && maxHeight >= 600.dp
            if (tablet) TabletShell(app, systemDark) else PhoneShell(app, systemDark)
            if (app.page != Page.Main) InfoPage(app, app.page, tablet)
        }
    }
}

/** Theme changes cross-fade, as the web app's do (0.22s). */
@Composable
private fun animatedPalette(t: Palette): Palette {
    @Composable
    fun c(v: Color) = animateColorAsState(v, tween(220), label = "palette").value
    return t.copy(
        paper = c(t.paper),
        sheet = c(t.sheet),
        rule = c(t.rule),
        ruleFaint = c(t.ruleFaint),
        hairline = c(t.hairline),
        ink = c(t.ink),
        inkMuted = c(t.inkMuted),
        vermilion = c(t.vermilion),
        vermilionText = c(t.vermilionText),
        vermilionDeep = c(t.vermilionDeep),
        vermilionWash = c(t.vermilionWash),
        ruleWash = c(t.ruleWash),
        onVerm = c(t.onVerm),
        heat1 = c(t.heat1),
        heat2 = c(t.heat2),
        heat3 = c(t.heat3),
        heat4 = c(t.heat4)
    )
}
