package app.kanapon.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.kanapon.R

/** Zen Kaku Gothic New for the interface, at 400 and 500 only. */
val UiFont = FontFamily(
    Font(R.font.zen_kaku_gothic_new_400, FontWeight.Normal),
    Font(R.font.zen_kaku_gothic_new_500, FontWeight.Medium)
)

/** Klee One, the textbook hand every model glyph is drawn in. */
val KanaFont = FontFamily(Font(R.font.klee_one_400, FontWeight.Normal))
