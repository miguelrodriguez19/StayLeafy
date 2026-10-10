package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.activity.ComponentActivity

@RunWith(AndroidJUnit4::class)
class CareEventScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editor_displaysTypeDateTimeAndNote_andForwardsCallbacks() {
        val performedAt = 1_700_000_000_000L
        var selectedType: CareType? = null
        var changedTime: Long? = null
        var note = ""
        setScreen(
            state = CareEventEditorState(
                eventId = null,
                performedAt = performedAt,
                isLoading = false,
            ),
            onCareTypeChange = { selectedType = it },
            onPerformedAtChange = { changedTime = it },
            onNoteChange = { note = it },
        )

        composeTestRule.onNodeWithText(string(R.string.care_type)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.care_pruning)).performClick()
        composeTestRule.onNodeWithText(string(R.string.care_date_value, formatEpochDay(19675L))).performClick()
        composeTestRule.onAllNodesWithText(string(R.string.select_date)).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.cancel)).onLast().performClick()
        composeTestRule.onNodeWithText(string(R.string.care_time_value, formatCareTimeForTest(performedAt))).performClick()
        composeTestRule.onNodeWithText(string(R.string.care_time)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.cancel)).onLast().performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Fertilized this week")

        assertEquals(CareType.PRUNING, selectedType)
        assertEquals("Fertilized this week", note)
        assertEquals(null, changedTime)
    }

    @Test
    fun editor_futureState_displaysLocalizedValidationText() {
        setScreen(
            state = CareEventEditorState(
                eventId = null,
                performedAt = System.currentTimeMillis() + 60_000,
                isLoading = false,
                futureError = true,
                saveError = UiErrorCategory.CareFuture,
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.care_date_future_error)).assertIsDisplayed()
    }

    @Test
    fun editor_notFoundState_displaysKeyTextAndBackCallback() {
        var backClicks = 0
        setScreen(
            state = CareEventEditorState(
                eventId = "missing-event",
                isLoading = false,
                notFound = true,
            ),
            onBack = { backClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.care_event_not_found)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.back_to_plant)).performClick()
        assertEquals(1, backClicks)
    }

    @Test
    fun editor_loadError_displaysRetryCallback() {
        var retryClicks = 0
        setScreen(
            state = CareEventEditorState(
                eventId = null,
                isLoading = false,
                loadError = UiErrorCategory.CareLoad,
            ),
            onRetry = { retryClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.load_care_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.retry)).performClick()
        assertEquals(1, retryClicks)
    }

    private fun setScreen(
        state: CareEventEditorState,
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        onCareTypeChange: (CareType) -> Unit = {},
        onPerformedAtChange: (Long) -> Unit = {},
        onNoteChange: (String) -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                CareEventEditorScreen(
                    state = state,
                    onBack = onBack,
                    onRetry = onRetry,
                    onCareTypeChange = onCareTypeChange,
                    onPerformedAtChange = onPerformedAtChange,
                    onNoteChange = onNoteChange,
                    onSave = onSave,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int, vararg args: Any): String =
        composeTestRule.activity.getString(id, *args)
}

private fun formatCareTimeForTest(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
