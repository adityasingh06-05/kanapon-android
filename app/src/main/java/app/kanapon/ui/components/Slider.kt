package app.kanapon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import app.kanapon.ui.theme.LocalPalette
import kotlin.math.roundToInt

/**
 * A range input in the design's accent colour: a 4px track filled with vermilion up to a 16px
 * thumb, 44px tall to keep the touch target. Values snap to [step], like <input type=range>.
 */
@Composable
fun KSlider(
    value: Float,
    onChange: (Float) -> Unit,
    min: Float,
    max: Float,
    step: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null
) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    fun snap(v: Float): Float {
        val n = ((v - min) / step).roundToInt()
        return (min + n * step).coerceIn(min, max)
    }
    fun fromX(x: Float, width: Float, thumb: Float): Float {
        val t = ((x - thumb / 2) / (width - thumb)).coerceIn(0f, 1f)
        return snap(min + t * (max - min))
    }
    fun emit(v: Float) {
        if (v != current) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            change(v)
        }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .semantics {
                if (label != null) contentDescription = label
                if (!enabled) disabled()
                setProgress { v ->
                    emit(snap(v))
                    true
                }
            }
            .progressSemantics(value, min..max, ((max - min) / step).roundToInt() - 1)
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(min, max, step) {
                            detectTapGestures { emit(fromX(it.x, size.width.toFloat(), 16.dp.toPx())) }
                        }
                        .pointerInput(min, max, step) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                emit(fromX(change.position.x, size.width.toFloat(), 16.dp.toPx()))
                            }
                        }
                } else {
                    Modifier
                }
            )
    ) {
        val thumb = 16.dp.toPx()
        val track = 4.dp.toPx()
        val t = if (max > min) (value - min) / (max - min) else 0f
        val cx = thumb / 2 + t * (size.width - thumb)
        val cy = size.height / 2
        val r = CornerRadius(track / 2)
        drawRoundRect(p.ruleFaint, Offset(0f, cy - track / 2), Size(size.width, track), r)
        drawRoundRect(p.vermilion, Offset(0f, cy - track / 2), Size(cx, track), r)
        drawCircle(p.vermilion, thumb / 2, Offset(cx, cy))
    }
}
