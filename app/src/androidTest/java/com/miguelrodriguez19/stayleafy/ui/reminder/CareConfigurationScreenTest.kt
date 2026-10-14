package com.miguelrodriguez19.stayleafy.ui.reminder

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.AnnualDate
import com.miguelrodriguez19.stayleafy.model.CareScheduleMode
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareConfigurationScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun content_displaysAllRulesAndForwardsAtomicSave() {
        var saves = 0
        setScreen(
            CareConfigurationUiState.Content(
                plantName = "Monstera",
                forms = CareType.values().map { CareRuleForm(careType = it) },
                latestCareEpochDays = emptyMap(),
            ),
            onSave = { saves++ },
        )

        composeTestRule.onNodeWithText(string(R.string.care_watering)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.care_pruning)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.care_fertilizing)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.save)).performClick()

        assertEquals(1, saves)
    }

    @Test
    fun validationErrors_areVisibleForEnabledRule() {
        setScreen(
            CareConfigurationUiState.Content(
                plantName = "Monstera",
                forms = listOf(
                    CareRuleForm(
                        careType = CareType.WATERING,
                        enabled = true,
                        intervalError = true,
                        sourceError = true,
                    ),
                    CareRuleForm(CareType.PRUNING),
                    CareRuleForm(CareType.FERTILIZING),
                ),
                latestCareEpochDays = emptyMap(),
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.interval_days_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.select_due_source)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.latest_care)).assertIsNotEnabled()
    }

    @Test
    fun intervalWithoutLatestCare_offersCustomStartDate() {
        var selectedSource: ReminderDueSourceChoice? = null
        setScreen(
            CareConfigurationUiState.Content(
                plantName = "Monstera",
                forms = listOf(
                    CareRuleForm(
                        careType = CareType.WATERING,
                        enabled = true,
                        mode = CareScheduleMode.INTERVAL,
                        intervalDays = "7",
                    ),
                    CareRuleForm(CareType.PRUNING),
                    CareRuleForm(CareType.FERTILIZING),
                ),
                latestCareEpochDays = emptyMap(),
            ),
            onSourceChange = { _, source -> selectedSource = source },
        )

        composeTestRule.onNodeWithText(string(R.string.choose_start_date)).performClick()
        composeTestRule.onNodeWithText(string(R.string.select_date)).assertIsDisplayed()
        assertEquals(ReminderDueSourceChoice.CustomDate, selectedSource)
    }

    @Test
    fun customStartDate_canSwitchBackToLatestCareChoice() {
        var selectedSource: ReminderDueSourceChoice? = null
        setScreen(
            CareConfigurationUiState.Content(
                plantName = "Monstera",
                forms = listOf(
                    CareRuleForm(
                        careType = CareType.WATERING,
                        enabled = true,
                        mode = CareScheduleMode.INTERVAL,
                        intervalDays = "7",
                        dueSource = ReminderDueSourceChoice.CustomDate,
                        selectedEpochDay = 21_000,
                    ),
                    CareRuleForm(CareType.PRUNING),
                    CareRuleForm(CareType.FERTILIZING),
                ),
                latestCareEpochDays = emptyMap(),
            ),
            onSourceChange = { _, source -> selectedSource = source },
        )

        composeTestRule.onNodeWithText(string(R.string.use_latest_care_instead))
            .assertIsDisplayed()
            .performClick()

        assertEquals(ReminderDueSourceChoice.LatestCare, selectedSource)
    }

    @Test
    fun permissionEducation_forwardsContinue() {
        var continues = 0
        setScreen(
            CareConfigurationUiState.Content(
                plantName = "Monstera",
                forms = CareType.values().map { CareRuleForm(careType = it) },
                latestCareEpochDays = emptyMap(),
                showPermissionEducation = true,
            ),
            onPermissionEducationContinue = { continues++ },
        )

        composeTestRule.onNodeWithText(string(R.string.notification_permission_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.continue_action)).performClick()

        assertEquals(1, continues)
    }

    @Test
    fun unsavedBack_showsConfirmationBeforeLeaving() {
        var backClicks = 0
        setScreen(
            CareConfigurationUiState.Content(
                plantName = "Monstera",
                forms = CareType.values().map { CareRuleForm(careType = it) },
                initialForms = emptyList(),
                latestCareEpochDays = emptyMap(),
            ),
            onBack = { backClicks++ },
        )

        composeTestRule.onNodeWithContentDescription(string(R.string.back)).performClick()
        composeTestRule.onNodeWithText(string(R.string.discard_changes_title)).assertIsDisplayed()
        assertEquals(0, backClicks)
        composeTestRule.onNodeWithText(string(R.string.keep_editing)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.back)).performClick()
        composeTestRule.onNodeWithText(string(R.string.discard)).performClick()

        assertEquals(1, backClicks)
    }

    @Test
    fun annualRule_displaysSingleSelectionAndOpensRangeCalendar() {
        setScreen(
            CareConfigurationUiState.Content(
                plantName = "Hydrangea",
                forms = listOf(
                    CareRuleForm(
                        careType = CareType.PRUNING,
                        mode = CareScheduleMode.SEASONAL,
                        enabled = true,
                        seasonStart = AnnualDate(11, 15),
                        seasonEnd = AnnualDate(2, 10),
                    ),
                    CareRuleForm(CareType.WATERING),
                    CareRuleForm(CareType.FERTILIZING),
                ),
                latestCareEpochDays = emptyMap(),
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.season_crosses_year)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            string(R.string.annual_care_timing),
            substring = true,
        ).performClick()

        composeTestRule.onNodeWithText(string(R.string.annual_picker_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.annual_picker_help)).assertIsDisplayed()
    }

    @Test
    fun notFound_forwardsBack() {
        var back = 0
        setScreen(CareConfigurationUiState.NotFound, onBack = { back++ })

        composeTestRule.onNodeWithText(string(R.string.plant_not_found)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.back_to_plants)).onFirst().performClick()

        assertEquals(1, back)
    }

    private fun setScreen(
        state: CareConfigurationUiState,
        onBack: () -> Unit = {},
        onSave: () -> Unit = {},
        onPermissionEducationContinue: () -> Unit = {},
        onSourceChange: (CareType, ReminderDueSourceChoice) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                CareConfigurationScreen(
                    state = state,
                    onBack = onBack,
                    onRetry = {},
                    onEnabledChange = { _, _ -> },
                    onIntervalChange = { _, _ -> },
                    onSourceChange = onSourceChange,
                    onDateChange = { _, _ -> },
                    onSave = onSave,
                    onPermissionEducationContinue = onPermissionEducationContinue,
                )
            }
        }
    }

    private fun string(id: Int): String = composeTestRule.activity.getString(id)
}
