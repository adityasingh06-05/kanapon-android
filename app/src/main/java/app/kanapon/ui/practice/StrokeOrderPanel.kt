package app.kanapon.ui.practice

import android.provider.Settings.Global
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kanapon.data.STROKE_WIDTH
import app.kanapon.data.StrokeOrder
import app.kanapon.data.VIEWBOX
import app.kanapon.ui.components.OutlineButton
import app.kanapon.ui.components.RuledCell
import app.kanapon.ui.theme.LocalPalette
import app.kanapon.ui.theme.UiFont
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Plays the KanjiVG stroke order, as the web app's StrokeOrder.jsx does: each stroke wipes in over
 * (180 + 4.4 x its length) / speed ms, then a 200ms pause the speed does not touch. Replay starts
 * again from nothing; Step draws one stroke and wraps round after the last.
 */
class StrokeOrderPlayer(private val lengths: List<Float>, private val scope: CoroutineScope, private val speed: () -> Float) {
    val n = lengths.size
    val progress = mutableStateListOf<Float>().apply { repeat(n) { add(0f) } }
    var active by mutableIntStateOf(-1)
        private set
    var done by mutableIntStateOf(0)
        private set
    private var job: Job? = null

    private fun reset() {
        for (i in 0 until n) progress[i] = 0f
        done = 0
        active = -1
    }

    fun showAll() {
        job?.cancel()
        for (i in 0 until n) progress[i] = 1f
        done = n
        active = -1
    }

    fun replay() {
        job?.cancel()
        reset()
        job = scope.launch { run(0, once = false) }
    }

    fun step() {
        job?.cancel()
        if (done >= n) reset()
        // A stroke left half drawn by an interrupted replay starts again.
        for (i in done until n) progress[i] = 0f
        job = scope.launch { run(done, once = true) }
    }

    private suspend fun run(from: Int, once: Boolean) {
        var i = from
        while (i < n) {
            active = i
            var t = 0f
            var last = withFrameNanos { it }
            while (t < 1f) {
                val now = withFrameNanos { it }
                val dt = (now - last) / 1e6f
                last = now
                t = min(1f, t + dt / ((180f + lengths[i] * 4.4f) / speed()))
                progress[i] = t
            }
            done = i + 1
            if (once || i + 1 >= n) {
                active = -1
                return
            }
            delay(200)
            i++
        }
        yield()
    }
}

/**
 * The stroke-order diagram for one character on its ruled cell, with Replay and Step under it.
 * KanjiVG's centrelines, numbered as they appear; the stroke being drawn is vermilion. It plays on
 * its own when it appears, and shows the finished diagram when animations are switched off.
 */
@Composable
fun StrokeOrderView(char: String, order: StrokeOrder, speed: Float, modifier: Modifier, buttonPadH: Dp, buttonsTop: Dp) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val currentSpeed by rememberUpdatedState(speed)
    val scope = rememberCoroutineScope()
    val paths = remember(char) { order.paths.map { PathParser().parsePathString(it).toPath() } }
    val measures = remember(char) { paths.map { PathMeasure().apply { setPath(it, false) } } }
    val player = remember(char) { StrokeOrderPlayer(measures.map { it.length }, scope) { currentSpeed } }
    val measurer = rememberTextMeasurer(cacheSize = 8)
    LaunchedEffect(player) {
        val still = Global.getFloat(context.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        if (still) {
            player.showAll()
        } else {
            delay(60)
            player.replay()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RuledCell(modifier) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Stroke order for $char, ${player.n} strokes" }) {
                val k = size.minDimension / VIEWBOX
                scale(k, k, pivot = Offset.Zero) {
                    for (i in 0 until player.n) {
                        val t = player.progress[i]
                        if (t <= 0f) continue
                        val color = if (i == player.active) p.vermilion else p.ink
                        val seg = if (t >= 1f) {
                            paths[i]
                        } else {
                            Path().also { measures[i].getSegment(0f, measures[i].length * t, it, true) }
                        }
                        drawPath(seg, color, style = Stroke(width = STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                val numberStyle = TextStyle(fontFamily = UiFont, fontSize = (8f * k).toSp(), color = p.inkMuted)
                order.numbers.forEachIndexed { i, (x, y) ->
                    if (i <= player.active || i < player.done) {
                        val layout = measurer.measure((i + 1).toString(), numberStyle)
                        drawText(layout, topLeft = Offset(x * k, y * k - layout.firstBaseline))
                    }
                }
            }
        }
        Row(Modifier.padding(top = buttonsTop), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton("Replay", { player.replay() }, padH = buttonPadH)
            OutlineButton("Step", { player.step() }, padH = buttonPadH)
        }
    }
}
