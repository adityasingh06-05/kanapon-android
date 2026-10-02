package app.kanapon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app on a device: a stroke written with a finger is scored, and the tabs lead where they say. */
@RunWith(AndroidJUnit4::class)
class PracticeDeviceTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aStrokeIsScoredWhenThePenLifts() {
        rule.onAllNodesWithContentDescription("Writing area")[0].performTouchInput {
            // あ's first stroke, roughly: a line across the upper third, left to right.
            val y = height * 0.27f
            down(Offset(width * 0.22f, y))
            for (i in 1..20) moveTo(Offset(width * (0.22f + 0.5f * i / 20), y - height * 0.04f * i / 20))
            up()
        }
        rule.onNodeWithText("1 of 3").assertExists()
        rule.onNodeWithText("2 strokes are missing").assertExists()
    }

    @Test
    fun theChartOpensACharacterAndSendsItToPractice() {
        rule.onNodeWithText("Chart").performClick()
        rule.onAllNodesWithContentDescription("か, ka, 3 strokes")[0].performClick()
        rule.onNodeWithText("3 strokes · hiragana · か行 · あ段").assertExists()
        rule.onNodeWithText("Practise this character").performClick()
        rule.onNodeWithText("ka").assertExists()
    }
}
