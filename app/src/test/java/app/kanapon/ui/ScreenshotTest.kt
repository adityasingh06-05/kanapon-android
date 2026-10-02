package app.kanapon.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.kanapon.TestData
import app.kanapon.data.GridPos
import app.kanapon.data.KanaSet
import app.kanapon.data.MemorySettingsStore
import app.kanapon.data.MemoryStatsStore
import app.kanapon.data.Settings
import app.kanapon.data.Stats
import app.kanapon.data.ThemeMode
import app.kanapon.ui.theme.PaletteId
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val TODAY: LocalDate = LocalDate.of(2026, 10, 1)

/** The four states the design shows (Kanapon Mobile.dc.html, 1a-1d), on every tab and sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase {
    @get:Rule
    val compose = createComposeRule()

    protected fun app(theme: PaletteId, seeded: Boolean): AppState {
        val settings = when (theme) {
            PaletteId.Light -> Settings(themeMode = ThemeMode.Light)
            else -> Settings(themeMode = ThemeMode.Dark, dark = theme)
        }
        return AppState(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            statsStore = MemoryStatsStore(if (seeded) seedHistory(TODAY) else Stats()),
            settingsStore = MemorySettingsStore(settings),
            strokeData = TestData.strokeData,
            today = { TODAY }
        )
    }

    protected fun shoot(name: String, app: AppState, setup: AppState.() -> Unit = {}) {
        // Animations off: the stroke order shows its finished diagram, so every run draws the same frame.
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        android.provider.Settings.Global.putFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.setContent { KanaponApp(app, systemDark = false) }
        compose.runOnIdle { app.setup() }
        compose.mainClock.advanceTimeBy(4000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            "src/test/screenshots/$name.png",
            // Anti-aliasing may differ by a hair between machines; layout and colour changes are far larger.
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f))
        )
    }
}

@Config(sdk = [36], qualifiers = "w390dp-h844dp-port-xhdpi")
class PhoneScreenshots : ScreenshotBase() {
    @Test fun practiceLight() = shoot("1a-phone-light-practice", app(PaletteId.Light, false))

    @Test fun practiceStone() = shoot("1b-phone-stone-practice", app(PaletteId.Stone, true))

    @Test fun practiceSumi() = shoot("1c-phone-sumi-practice", app(PaletteId.Sumi, true))

    @Test fun chartLight() = shoot("1a-phone-light-chart", app(PaletteId.Light, false)) { nav(Screen.Chart) }

    @Test fun progressLight() = shoot("1a-phone-light-progress", app(PaletteId.Light, false)) { nav(Screen.Progress) }

    @Test fun progressStone() = shoot("1b-phone-stone-progress", app(PaletteId.Stone, true)) { nav(Screen.Progress) }

    @Test fun worksheetsLight() = shoot("1a-phone-light-worksheets", app(PaletteId.Light, false)) { nav(Screen.Worksheets) }

    @Test fun pickerSheet() = shoot("1b-phone-stone-sheet-picker", app(PaletteId.Stone, true)) { open(Sheet.Picker) }

    @Test fun strokesSheet() = shoot("1a-phone-light-sheet-strokes", app(PaletteId.Light, false)) { open(Sheet.Strokes) }

    @Test fun penSheet() = shoot("1c-phone-sumi-sheet-pen", app(PaletteId.Sumi, true)) { open(Sheet.Pen) }

    @Test fun settingsSheet() = shoot("1a-phone-light-sheet-settings", app(PaletteId.Light, false)) { open(Sheet.Settings) }

    @Test fun detailSheet() = shoot("1b-phone-stone-sheet-detail", app(PaletteId.Stone, true)) {
        nav(Screen.Chart)
        chartSel = ChartSel(KanaSet.Hiragana, GridPos(1, 0))
        open(Sheet.Detail)
    }
}

@Config(sdk = [36], qualifiers = "w1180dp-h820dp-land-mdpi")
class TabletScreenshots : ScreenshotBase() {
    @Test fun practice() = shoot("1d-tablet-light-practice", app(PaletteId.Light, true))

    @Test fun chart() = shoot("1d-tablet-light-chart", app(PaletteId.Light, true)) {
        nav(Screen.Chart)
        chartSel = ChartSel(KanaSet.Hiragana, GridPos(1, 0))
    }

    @Test fun progress() = shoot("1d-tablet-light-progress", app(PaletteId.Light, true)) { nav(Screen.Progress) }

    @Test fun worksheets() = shoot("1d-tablet-light-worksheets", app(PaletteId.Light, true)) { nav(Screen.Worksheets) }

    @Test fun settings() = shoot("1d-tablet-stone-settings", app(PaletteId.Stone, true)) { open(Sheet.Settings) }
}
