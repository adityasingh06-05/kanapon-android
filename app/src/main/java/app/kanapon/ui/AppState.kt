package app.kanapon.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kanapon.data.Gojuon
import app.kanapon.data.GridPos
import app.kanapon.data.Kana
import app.kanapon.data.KanaSet
import app.kanapon.data.Settings
import app.kanapon.data.SettingsStore
import app.kanapon.data.Stats
import app.kanapon.data.StatsMath
import app.kanapon.data.StatsStore
import app.kanapon.data.StrokeData
import app.kanapon.data.ThemeMode
import app.kanapon.engine.StrokeMatcher
import app.kanapon.export.Exporter
import app.kanapon.export.Paper
import app.kanapon.export.WorksheetSpec
import app.kanapon.ui.practice.PracticeState
import app.kanapon.ui.theme.Palette
import app.kanapon.ui.theme.PaletteId
import app.kanapon.ui.theme.Palettes
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen(val label: String) {
    Practice("Practice"),
    Chart("Chart"),
    Progress("Progress"),
    Worksheets("Worksheets")
}

enum class Sheet(val title: String) {
    Picker("Gojūon"),
    Strokes("How to write it"),
    Pen("Pen and guide"),
    Settings("Settings"),
    Detail("Character")
}

/** Full-screen pages reached from the settings: everything else lives on the four tabs. */
enum class Page { Main, Credits, Privacy }

/** A chart selection: which table, and where in it. */
data class ChartSel(val set: KanaSet, val pos: GridPos)

/** The worksheet's character list: a whole syllabary, or the weakest 12 from the history. */
enum class WsSet(val id: String) {
    Hiragana("hiragana"),
    Katakana("katakana"),
    Weak("weak")
}

/**
 * Everything the app shows, in one place, as Compose state. Built by [KanaponViewModel] with the
 * real stores, and by the screenshot tests with in-memory ones.
 */
class AppState(
    private val scope: CoroutineScope,
    private val statsStore: StatsStore,
    private val settingsStore: SettingsStore,
    val strokeData: StrokeData,
    val today: () -> LocalDate = { LocalDate.now() },
    val exporter: Exporter? = null,
    io: CoroutineDispatcher = Dispatchers.IO
) {
    /** Writes go out one at a time, in order. */
    private val writer = io.limitedParallelism(1)

    val matcher = StrokeMatcher(strokeData.reference)

    var screen by mutableStateOf(Screen.Practice)
        private set
    var sheet by mutableStateOf<Sheet?>(null)
        private set

    /** The sheet last shown, kept so its content stays put while it slides away. */
    var lastSheet by mutableStateOf(Sheet.Picker)
        private set
    var page by mutableStateOf(Page.Main)

    var settings by mutableStateOf(settingsStore.load())
        private set
    var stats by mutableStateOf(statsStore.load())
        private set
    var storageBroken by mutableStateOf(false)
        private set

    val practice = PracticeState(this, scope)

    // Chart
    var chartSet by mutableStateOf(KanaSet.Hiragana)
    var chartSel by mutableStateOf<ChartSel?>(null)

    // Progress
    var confirmingReset by mutableStateOf(false)
        private set
    private var disarm: Job? = null

    // Worksheets
    var wsSet by mutableStateOf(WsSet.Hiragana)
    var wsPer by mutableIntStateOf(8)
    var wsGuide by mutableFloatStateOf(0.3f)
    var wsNumbers by mutableStateOf(true)
    var wsPaper by mutableStateOf(Paper.A4)
    var wsPage by mutableIntStateOf(0)
    var wsBusy by mutableStateOf(false)
        private set

    // Toast
    var toastText by mutableStateOf("")
        private set
    var toastOn by mutableStateOf(false)
        private set
    private var toastJob: Job? = null

    /* ---------- theme ---------- */

    fun palette(systemDark: Boolean): Palette = Palettes.of(paletteId(systemDark))

    fun paletteId(systemDark: Boolean): PaletteId = when (settings.themeMode) {
        ThemeMode.Light -> PaletteId.Light
        ThemeMode.Dark -> settings.dark
        ThemeMode.Auto -> if (systemDark) settings.dark else PaletteId.Light
    }

    fun chooseTheme(id: PaletteId?) {
        updateSettings {
            when (id) {
                null -> copy(themeMode = ThemeMode.Auto)
                PaletteId.Light -> copy(themeMode = ThemeMode.Light)
                else -> copy(themeMode = ThemeMode.Dark, dark = id)
            }
        }
    }

    fun updateSettings(change: Settings.() -> Settings) {
        val next = settings.change().clamped()
        if (next == settings) return
        settings = next
        scope.launch(writer) { settingsStore.save(next) }
    }

    /* ---------- history ---------- */

    /** Records one finished attempt and returns the previous best. */
    fun record(char: String, score: Int, strokes: Int, countsForBest: Boolean): Int {
        val (next, prevBest) = StatsMath.recordAttempt(stats, char, score, strokes, countsForBest, today())
        stats = next
        persist(next)
        return prevBest
    }

    fun bestOf(char: String): Int = stats.chars[char]?.best ?: 0

    private fun persist(next: Stats) {
        scope.launch(writer) {
            val ok = statsStore.save(next)
            if (!ok) withContext(Dispatchers.Main.immediate) { storageBroken = true }
        }
    }

    /** First tap arms the reset; a second within four seconds erases the history. */
    fun tapReset() {
        if (!confirmingReset) {
            confirmingReset = true
            disarm?.cancel()
            disarm = scope.launch {
                delay(4000)
                confirmingReset = false
            }
            return
        }
        disarm?.cancel()
        confirmingReset = false
        practice.discardPending()
        stats = Stats()
        scope.launch(writer) { statsStore.reset() }
    }

    /* ---------- navigation ---------- */

    fun nav(to: Screen) {
        if (to != Screen.Practice) practice.finaliseAll()
        screen = to
        sheet = null
        confirmingReset = false
        disarm?.cancel()
    }

    fun open(s: Sheet) {
        sheet = s
        lastSheet = s
    }

    fun closeSheet() {
        sheet = null
    }

    fun toggleSettings() {
        if (sheet == Sheet.Settings) closeSheet() else open(Sheet.Settings)
    }

    /** Leaves the chart or progress for practice on one character. */
    fun practise(kana: Kana) {
        val set = Gojuon.setOf(kana.char) ?: return
        practice.goTo(set, Gojuon.indexIn(set, kana.char))
        nav(Screen.Practice)
    }

    fun toast(text: String) {
        toastJob?.cancel()
        toastText = text
        toastOn = true
        toastJob = scope.launch {
            delay(2200)
            toastOn = false
        }
    }

    /* ---------- exports ---------- */

    fun saveGlyph(kana: Kana, set: KanaSet, jpeg: Boolean) {
        val ex = exporter ?: return
        scope.launch {
            try {
                val name = if (jpeg) ex.saveGlyphJpeg(kana, set) else ex.saveGlyphSvg(kana, set)
                toast("$name saved")
            } catch (_: Exception) {
                toast(if (jpeg) "The image could not be created." else "There is no outline on file for ${kana.char}.")
            }
        }
    }

    fun saveWorksheet(spec: WorksheetSpec) {
        val ex = exporter ?: return
        if (wsBusy) return
        wsBusy = true
        scope.launch {
            try {
                val pages = ex.saveWorksheet(spec)
                toast("PDF ready - $pages ${if (pages == 1) "page" else "pages"}")
            } catch (_: Exception) {
                toast("The PDF could not be generated.")
            } finally {
                wsBusy = false
            }
        }
    }
}
