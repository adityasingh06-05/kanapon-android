package app.kanapon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.kanapon.data.DataStoreSettingsStore
import app.kanapon.data.FileStatsStore
import app.kanapon.data.StrokeData
import app.kanapon.export.AndroidExporter

/** Holds the app's state across configuration changes, built on the real stores. */
class KanaponViewModel(application: Application) : AndroidViewModel(application) {
    val app: AppState = run {
        val assets = application.assets
        fun read(path: String) = assets.open(path).bufferedReader().use { it.readText() }
        AppState(
            scope = viewModelScope,
            statsStore = FileStatsStore(application.filesDir),
            settingsStore = DataStoreSettingsStore(application),
            strokeData = StrokeData.parse(read("data/strokes.json"), read("data/klee-strokes.json")),
            exporter = AndroidExporter(application)
        )
    }

    /** viewModelScope is already cancelled here, so the last attempt is written synchronously. */
    override fun onCleared() {
        app.flushNow()
    }
}
