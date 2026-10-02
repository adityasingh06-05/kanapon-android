package app.kanapon

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.kanapon.ui.KanaponApp
import app.kanapon.ui.KanaponViewModel

class MainActivity : ComponentActivity() {
    private val vm: KanaponViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KanaponApp(vm.app, onDarkChange = ::barsFor)
        }
    }

    /** System bar icons follow the app's palette, not the system's, since the two can differ. */
    private fun barsFor(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
