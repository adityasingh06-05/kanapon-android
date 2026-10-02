package app.kanapon.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The three palettes, with every token from the web app's src/styles/tokens.css. */
enum class PaletteId(val key: String, val label: String, val caption: String) {
    Light("light", "Light", "Paper and sage ruling"),
    Stone("stone", "Ink-stone", "Warm tea-brown"),
    Sumi("sumi", "Sumi", "Near-black, lifted red")
    ;

    companion object {
        fun of(key: String?): PaletteId? = entries.firstOrNull { it.key == key }
    }
}

@Immutable
data class Palette(
    val id: PaletteId,
    val paper: Color,
    val sheet: Color,
    val rule: Color,
    val ruleFaint: Color,
    val hairline: Color,
    val ink: Color,
    val inkMuted: Color,
    val vermilion: Color,
    val vermilionText: Color,
    val vermilionDeep: Color,
    val vermilionWash: Color,
    val ruleWash: Color,
    val onVerm: Color,
    val heat1: Color,
    val heat2: Color,
    val heat3: Color,
    val heat4: Color,
    /** How strongly a guide glyph shows: dark sheets need more ink to read the same. */
    val gain: Float
) {
    val isDark: Boolean get() = id != PaletteId.Light

    fun heat(level: Int): Color = when (level) {
        1 -> heat1
        2 -> heat2
        3 -> heat3
        4 -> heat4
        else -> sheet
    }

    /** A guide glyph at [strength] 0..1: ink mixed into the sheet, as CSS color-mix(in srgb) does. */
    fun guide(strength: Float): Color = mixSrgb(sheet, ink, minOf(1f, strength * gain))
}

/** Linear interpolation in gamma-encoded sRGB, the way CSS color-mix(in srgb, ...) mixes. */
fun mixSrgb(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t
)

object Palettes {
    val Light = Palette(
        id = PaletteId.Light,
        paper = Color(0xFFF7F8F4),
        sheet = Color(0xFFFFFEFB),
        rule = Color(0xFF8FA893),
        ruleFaint = Color(0xFFC6D2C6),
        hairline = Color(0xFFE8EBE3),
        ink = Color(0xFF202521),
        inkMuted = Color(0xFF5C665D),
        vermilion = Color(0xFFB0463A),
        vermilionText = Color(0xFFB0463A),
        vermilionDeep = Color(0xFF8E3830),
        vermilionWash = Color(176, 70, 58).copy(alpha = 0.08f),
        ruleWash = Color(143, 168, 147).copy(alpha = 0.14f),
        onVerm = Color(0xFFFFFEFB),
        heat1 = Color(0xFFDCE5DC),
        heat2 = Color(0xFFB7C9B8),
        heat3 = Color(0xFF8FA893),
        heat4 = Color(0xFF6C8571),
        gain = 1f
    )

    val Sumi = Palette(
        id = PaletteId.Sumi,
        paper = Color(0xFF161815),
        sheet = Color(0xFF1E211D),
        rule = Color(0xFF6E8873),
        ruleFaint = Color(0xFF3E4B41),
        hairline = Color(0xFF2C302B),
        ink = Color(0xFFE9EBE4),
        inkMuted = Color(0xFF9BA79C),
        vermilion = Color(0xFFD96E5C),
        vermilionText = Color(0xFFD96E5C),
        vermilionDeep = Color(0xFFE68873),
        vermilionWash = Color(217, 110, 92).copy(alpha = 0.1f),
        ruleWash = Color(110, 136, 115).copy(alpha = 0.16f),
        onVerm = Color(0xFF161815),
        heat1 = Color(0xFF382A22),
        heat2 = Color(0xFF5E382C),
        heat3 = Color(0xFF90503E),
        heat4 = Color(0xFFD96E5C),
        gain = 1.13f
    )

    val Stone = Palette(
        id = PaletteId.Stone,
        paper = Color(0xFF191612),
        sheet = Color(0xFF221E18),
        rule = Color(0xFF7F8767),
        ruleFaint = Color(0xFF47402F),
        hairline = Color(0xFF302A22),
        ink = Color(0xFFEEE8DA),
        inkMuted = Color(0xFFA89E89),
        vermilion = Color(0xFFB0463A),
        vermilionText = Color(0xFFD4685A),
        vermilionDeep = Color(0xFFC4564A),
        vermilionWash = Color(176, 70, 58).copy(alpha = 0.12f),
        ruleWash = Color(127, 135, 103).copy(alpha = 0.16f),
        onVerm = Color(0xFFFFFEFB),
        heat1 = Color(0xFF3A2A20),
        heat2 = Color(0xFF6A3A2C),
        heat3 = Color(0xFF96453A),
        heat4 = Color(0xFFB0463A),
        gain = 1.2f
    )

    fun of(id: PaletteId): Palette = when (id) {
        PaletteId.Light -> Light
        PaletteId.Stone -> Stone
        PaletteId.Sumi -> Sumi
    }
}

/** Worksheet and export colours: the printed page keeps paper colours in every theme. */
object Print {
    val sheet = Color(0xFFFFFEFB)
    val rule = Color(0xFF8FA893)
    val ruleFaint = Color(0xFFC6D2C6)
    val ink = Color(0xFF202521)
    val inkMuted = Color(0xFF5C665D)
}

val LocalPalette = staticCompositionLocalOf { Palettes.Light }

/** The design's one curve: cubic-bezier(.22, 1, .36, 1). */
val KanaponEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
