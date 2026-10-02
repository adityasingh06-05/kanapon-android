package app.kanapon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kanapon.ui.theme.LocalPalette

val ControlShape = RoundedCornerShape(4.dp)

/**
 * A tappable box with no ripple: every control in the design shows its own pressed state, and
 * every tap gives the short tick the design asks for ("buttons vibrate on tap").
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Button,
    description: String? = null,
    selected: Boolean? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .semantics {
                if (description != null) contentDescription = description
                if (selected != null) this.selected = selected
            }
            .clickable(source, indication = null, enabled = enabled, role = role) {
                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onClick()
            },
        contentAlignment = contentAlignment
    ) { content(pressed && enabled) }
}

/** Bordered button: hairline border, 4px radius; the border darkens to the rule while pressed. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Float = 13f,
    padH: Dp = 16.dp,
    height: Dp = 44.dp,
    enabled: Boolean = true,
    washWhenPressed: Boolean = false,
    description: String? = null
) {
    val p = LocalPalette.current
    Pressable(onClick, modifier.height(height).alpha(if (enabled) 1f else 0.35f), enabled = enabled, description = description) { pressed ->
        Box(
            Modifier
                .matchParentSize()
                .background(if (pressed && washWhenPressed) p.ruleWash else Color.Transparent, ControlShape)
                .border(1.dp, if (pressed) p.rule else p.hairline, ControlShape)
        )
        Txt(text, fontSize, Modifier.padding(horizontal = padH), lineHeight = 1.2f, softWrap = false)
    }
}

/** A link-like text button, underlined 3px below the baseline. */
@Composable
fun UnderlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Float = 13f,
    padH: Dp = 10.dp,
    height: Dp = 44.dp,
    color: Color = LocalPalette.current.ink
) {
    Pressable(onClick, modifier.height(height)) {
        UnderlinedTxt(text, fontSize, Modifier.padding(horizontal = padH), color = color, lineHeight = 1.2f)
    }
}

/** A toggle chip: rule-washed with a rule border when on, hairline and muted when off. */
@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, fontSize: Float = 12.5f, padH: Dp = 14.dp) {
    val p = LocalPalette.current
    Pressable(onClick, modifier.height(44.dp), role = Role.Switch, selected = on) {
        Box(
            Modifier
                .matchParentSize()
                .background(if (on) p.ruleWash else Color.Transparent, ControlShape)
                .border(1.dp, if (on) p.rule else p.hairline, ControlShape)
        )
        Txt(
            label,
            fontSize,
            Modifier.padding(horizontal = padH),
            color = if (on) p.ink else p.inkMuted,
            lineHeight = 1.2f,
            softWrap = false
        )
    }
}

/** A choice in a list of options, as the worksheet and appearance pickers draw them. */
@Composable
fun OptionBox(
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalPalette.current
    Pressable(
        onClick,
        modifier
            .heightIn(min = minHeight)
            .background(if (on) p.ruleWash else Color.Transparent, ControlShape)
            .border(1.dp, if (on) p.rule else p.hairline, ControlShape),
        role = Role.RadioButton,
        selected = on,
        contentAlignment = contentAlignment
    ) { content() }
}

/** The one filled button: vermilion, deepening while pressed. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    dimmed: Boolean = !enabled,
    padH: Dp = 22.dp
) {
    val p = LocalPalette.current
    Pressable(
        onClick,
        modifier
            .height(height)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .alpha(if (dimmed) 0.5f else 1f),
        enabled = enabled
    ) { pressed ->
        Box(
            Modifier
                .matchParentSize()
                .background(if (pressed) p.vermilionDeep else p.vermilion, ControlShape)
                .border(1.dp, p.vermilion, ControlShape)
        )
        Txt(text, 14f, Modifier.padding(horizontal = padH), color = p.onVerm, lineHeight = 1.2f, softWrap = false)
    }
}

/** A plain bordered square, the shape every cell and swatch shares. */
fun Modifier.box(fill: Color, border: Color, shape: Shape = RectangleShape, width: Dp = 1.dp): Modifier =
    this.background(fill, shape).border(width, border, shape)
