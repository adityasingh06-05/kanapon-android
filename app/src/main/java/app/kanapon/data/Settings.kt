package app.kanapon.data

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.kanapon.ui.theme.PaletteId
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

enum class ThemeMode(val key: String) {
    Auto("auto"),
    Light("light"),
    Dark("dark")
}

/**
 * Preferences, kept apart from the practice history so a reset of the history never throws away
 * the pen width someone has settled on. Defaults and ranges are the web app's (src/lib/stats.js).
 * Recall is a mode, not a preference, so it is not here.
 */
data class Settings(
    val pen: Float = 3.2f,
    val guide: Float = 0.22f,
    val pressure: Boolean = true,
    val quarters: Boolean = false,
    val strokeSpeed: Float = 1f,
    val themeMode: ThemeMode = ThemeMode.Auto,
    val dark: PaletteId = PaletteId.Stone
) {
    fun clamped(): Settings = copy(
        pen = pen.takeIf { it.isFinite() }?.coerceIn(1f, 9f) ?: 3.2f,
        guide = guide.takeIf { it.isFinite() }?.coerceIn(0f, 0.5f) ?: 0.22f,
        strokeSpeed = strokeSpeed.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f,
        dark = if (dark == PaletteId.Light) PaletteId.Stone else dark
    )
}

interface SettingsStore {
    fun load(): Settings

    suspend fun save(settings: Settings)
}

class MemorySettingsStore(private var settings: Settings = Settings()) : SettingsStore {
    override fun load() = settings

    override suspend fun save(settings: Settings) {
        this.settings = settings
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "kana-settings-v1")

class DataStoreSettingsStore(context: Context) : SettingsStore {
    private val store = context.applicationContext.settingsDataStore

    private object K {
        val pen = floatPreferencesKey("pen")
        val guide = floatPreferencesKey("guide")
        val pressure = booleanPreferencesKey("pressure")
        val quarters = booleanPreferencesKey("quarters")
        val strokeSpeed = floatPreferencesKey("strokeSpeed")
        val mode = stringPreferencesKey("themeMode")
        val dark = stringPreferencesKey("dark")
    }

    /** Read once at start-up, before the first frame, so the theme never flashes. The file is tiny. */
    override fun load(): Settings = try {
        val p = runBlocking { store.data.first() }
        val d = Settings()
        Settings(
            pen = p[K.pen] ?: d.pen,
            guide = p[K.guide] ?: d.guide,
            pressure = p[K.pressure] ?: d.pressure,
            quarters = p[K.quarters] ?: d.quarters,
            strokeSpeed = p[K.strokeSpeed] ?: d.strokeSpeed,
            themeMode = ThemeMode.entries.firstOrNull { it.key == p[K.mode] } ?: d.themeMode,
            dark = PaletteId.of(p[K.dark]) ?: d.dark
        ).clamped()
    } catch (_: Exception) {
        Settings()
    }

    override suspend fun save(settings: Settings) {
        try {
            store.edit {
                it[K.pen] = settings.pen
                it[K.guide] = settings.guide
                it[K.pressure] = settings.pressure
                it[K.quarters] = settings.quarters
                it[K.strokeSpeed] = settings.strokeSpeed
                it[K.mode] = settings.themeMode.key
                it[K.dark] = settings.dark.key
            }
        } catch (_: IOException) {
            // Preferences are a convenience; losing one write is not worth interrupting practice.
        }
    }
}

/** Where the practice history lives. Writes report failure so the Progress screen can say so. */
interface StatsStore {
    fun load(): Stats

    fun save(stats: Stats): Boolean

    fun reset(): Boolean
}

class MemoryStatsStore(private var stats: Stats = Stats()) : StatsStore {
    override fun load() = stats

    override fun save(stats: Stats): Boolean {
        this.stats = stats
        return true
    }

    override fun reset(): Boolean {
        stats = Stats()
        return true
    }
}

/** The history as one JSON file in the app's private storage, written atomically. */
class FileStatsStore(dir: File) : StatsStore {
    private val file = AtomicFile(File(dir, "kana-practice-v1.json"))

    override fun load(): Stats = try {
        StatsMath.parse(file.readFully().decodeToString())
    } catch (_: Exception) {
        Stats()
    }

    @Synchronized
    override fun save(stats: Stats): Boolean {
        val out = try {
            file.startWrite()
        } catch (_: IOException) {
            return false
        }
        return try {
            out.write(StatsMath.encode(stats).encodeToByteArray())
            file.finishWrite(out)
            true
        } catch (_: IOException) {
            file.failWrite(out)
            false
        }
    }

    @Synchronized
    override fun reset(): Boolean = try {
        file.delete()
        true
    } catch (_: Exception) {
        false
    }
}
