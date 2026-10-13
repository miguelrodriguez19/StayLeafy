package com.miguelrodriguez19.stayleafy.ui.reminder

import androidx.activity.ComponentActivity
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.model.ReminderScheduleType
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class NoticeScreensTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyState_displaysGuidance() {
        setList(NoticeListUiState.Empty)

        composeTestRule.onNodeWithText(string(R.string.no_notices)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.no_notices_hint)).assertIsDisplayed()
    }

    @Test
    fun emptyState_usesSpanishResources() {
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("es"))
        }
        val localizedContext = composeTestRule.activity.createConfigurationContext(configuration)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
            ) {
                StayLeafyTheme(dynamicColor = false) {
                    NoticeListScreen(
                        state = NoticeListUiState.Empty,
                        notificationWarning = false,
                        onNoticeClick = {}, onRetry = {}, onMarkAllRead = {},
                        onOpenNotificationSettings = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Avisos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Aún no hay avisos").assertIsDisplayed()
    }

    @Test
    fun content_forwardsNoticeAndMarkAllClicks() {
        var clicked: String? = null
        var markAll = 0
        val notice = notice()
        setList(
            NoticeListUiState.Content(listOf(notice)),
            onNoticeClick = { clicked = it },
            onMarkAllRead = { markAll++ },
        )

        composeTestRule.onNodeWithText(
            string(R.string.notice_row_title, string(R.string.care_watering), "Monstera"),
        ).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.more_options)).performClick()
        composeTestRule.onNodeWithText(string(R.string.mark_all_read)).assertIsEnabled().performClick()

        assertEquals(notice.id, clicked)
        assertEquals(1, markAll)
    }

    @Test
    fun notificationWarning_forwardsSettings() {
        var settings = 0
        setList(
            NoticeListUiState.Empty,
            notificationWarning = true,
            onOpenNotificationSettings = { settings++ },
        )

        composeTestRule.onNodeWithText(string(R.string.notifications_blocked_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.open_settings)).performClick()
        assertEquals(1, settings)
    }

    @Test
    fun seasonalNotice_usesSeasonSpecificWording() {
        val notice = notice().copy(scheduleType = ReminderScheduleType.SEASONAL)
        setList(NoticeListUiState.Content(listOf(notice)))

        composeTestRule.onNodeWithText(
            string(R.string.season_notice_row_title, string(R.string.care_watering), "Monstera"),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            string(R.string.notice_season_start, com.miguelrodriguez19.stayleafy.ui.plant.formatEpochDay(21_000)),
        ).assertIsDisplayed()
    }

    @Test
    fun detail_displaysSemanticFieldsAndForwardsPlant() {
        var plantId: String? = null
        val notice = notice()
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                NoticeDetailScreen(
                    state = NoticeDetailUiState.Content(notice),
                    onBack = {},
                    onRetry = {},
                    onViewPlant = { plantId = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Monstera").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.view_plant)).performClick()
        assertEquals("plant-id", plantId)
    }

    @Test
    fun detailNotFound_forwardsBack() {
        var back = 0
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                NoticeDetailScreen(
                    state = NoticeDetailUiState.NotFound,
                    onBack = { back++ },
                    onRetry = {},
                    onViewPlant = {},
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.notice_not_found)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.back_to_notices)).performClick()
        assertEquals(1, back)
    }

    private fun setList(
        state: NoticeListUiState,
        notificationWarning: Boolean = false,
        onNoticeClick: (String) -> Unit = {},
        onMarkAllRead: () -> Unit = {},
        onOpenNotificationSettings: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                NoticeListScreen(
                    state = state,
                    notificationWarning = notificationWarning,
                    onNoticeClick = onNoticeClick,
                    onRetry = {},
                    onMarkAllRead = onMarkAllRead,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                )
            }
        }
    }

    private fun notice() = AppNotice(
        id = "notice-id",
        ruleId = "rule-id",
        plantId = "plant-id",
        plantName = "Monstera",
        careType = CareType.WATERING,
        dueEpochDay = 21_000,
        createdAt = 1_800_000_000_000,
        readAt = null,
        deliveryState = NoticeDeliveryState.DELIVERED,
    )

    private fun string(id: Int, vararg arguments: Any): String =
        composeTestRule.activity.getString(id, *arguments)
}
