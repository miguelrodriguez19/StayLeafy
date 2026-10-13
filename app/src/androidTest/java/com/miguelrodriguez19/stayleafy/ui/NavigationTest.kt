package com.miguelrodriguez19.stayleafy.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.PlantNote
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.testdi.FakeCareEventRepository
import com.miguelrodriguez19.stayleafy.testdi.FakePlantNoteRepository
import com.miguelrodriguez19.stayleafy.testdi.FakePlantRepository
import com.miguelrodriguez19.stayleafy.testdi.fakePlant
import com.miguelrodriguez19.stayleafy.ui.reminder.FakePhase4UiGateway
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var repository: FakePlantRepository

    @Inject
    lateinit var noteRepository: FakePlantNoteRepository

    @Inject
    lateinit var careRepository: FakeCareEventRepository

    @Inject
    lateinit var phase4Gateway: FakePhase4UiGateway

    @Before
    fun setUp() {
        hiltRule.inject()
        repository.reset()
        noteRepository.reset()
        careRepository.reset()
        phase4Gateway.reset()
        repository.replacePlants(
            listOf(
                fakePlant(
                    id = "monstera-id",
                    name = "Monstera",
                    scientificName = "Monstera deliciosa",
                    location = "Living room",
                    description = "Bright indirect light",
                ),
                fakePlant(
                    id = "fern-id",
                    name = "Fern",
                    isFavorite = true,
                ),
            ),
        )
        composeTestRule.waitForIdle()
    }

    @Test
    fun listCreateAndBack_returnsToPlantList() {
        composeTestRule.onNodeWithText(string(R.string.plants_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.add_plant)).performClick()
        composeTestRule.onNodeWithText(string(R.string.add_plant)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(string(R.string.cancel)).performClick()

        composeTestRule.onNodeWithText(string(R.string.plants_title)).assertIsDisplayed()
        clickListPlant("Monstera")
    }

    @Test
    fun listPlantClick_opensDetail() {
        clickListPlant("Monstera")

        composeTestRule.onNodeWithText("Monstera deliciosa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Living room").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.navigation_plants)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.navigation_notices)).assertIsDisplayed()
    }

    @Test
    fun plantTab_reselectedFromDetail_returnsToPlantList() {
        clickListPlant("Monstera")
        composeTestRule.onNodeWithText(string(R.string.edit_plant)).assertExists()

        composeTestRule.onNodeWithText(string(R.string.navigation_plants)).performClick()

        composeTestRule.onNodeWithText(string(R.string.plants_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.edit_plant)).assertDoesNotExist()
        composeTestRule.onNodeWithText("Monstera").assertIsDisplayed()
    }

    @Test
    fun rootTabs_switchBetweenIndependentPlantAndNoticeRoots() {
        composeTestRule.onNodeWithText(string(R.string.navigation_notices)).performClick()
        composeTestRule.onAllNodesWithText(string(R.string.notices_title)).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.no_notices)).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.navigation_plants)).performClick()
        composeTestRule.onNodeWithText(string(R.string.plants_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Monstera").assertIsDisplayed()
    }

    @Test
    fun unreadBadge_andAndroidNoticeHook_openNoticeDetail() {
        phase4Gateway.replaceNotices(listOf(testNotice()))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()

        AndroidNoticeNavigation.openNotice("notice-id")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.notice_details)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Monstera").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.navigation_notices)).assertIsDisplayed()
    }

    @Test
    fun noticesTab_reselectedFromDetail_returnsToNoticeList() {
        phase4Gateway.replaceNotices(listOf(testNotice()))
        AndroidNoticeNavigation.openNotice("notice-id")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.notice_details)).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.navigation_notices)).performClick()

        composeTestRule.onAllNodesWithText(string(R.string.notices_title)).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.notice_details)).assertDoesNotExist()
        composeTestRule.onNodeWithText(
            string(R.string.notice_row_title, string(R.string.care_watering), "Monstera"),
        ).assertIsDisplayed()
    }

    @Test
    fun detailConfigureCare_keepsBottomNavigationVisible() {
        clickListPlant("Monstera")
        composeTestRule.onNodeWithText(string(R.string.configure_care)).performScrollTo().performClick()

        composeTestRule.onNodeWithText(string(R.string.configure_care)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.navigation_plants)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.navigation_notices)).assertIsDisplayed()
    }

    @Test
    fun noticeToPlant_backThenNotices_returnsToNoticeInbox() {
        phase4Gateway.replaceNotices(listOf(testNotice()))
        AndroidNoticeNavigation.openNotice("notice-id")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.view_plant)).performClick()
        composeTestRule.onNodeWithText("Monstera deliciosa").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.back)).performClick()
        composeTestRule.onNodeWithText(string(R.string.plants_title)).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.navigation_notices)).performClick()

        composeTestRule.onAllNodesWithText(string(R.string.notices_title)).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.notice_details)).assertDoesNotExist()
        composeTestRule.onNodeWithText(
            string(R.string.notice_row_title, string(R.string.care_watering), "Monstera"),
        ).assertIsDisplayed()
    }

    @Test
    fun detailEditAndBack_returnsToDetail() {
        clickListPlant("Monstera")
        composeTestRule.onNodeWithText(string(R.string.edit_plant)).performClick()
        composeTestRule.onNodeWithText(string(R.string.edit_plant)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(string(R.string.cancel)).performClick()

        composeTestRule.onNodeWithText("Monstera deliciosa").assertIsDisplayed()
    }

    @Test
    fun detailDeleteConfirm_returnsToListWithoutDeletedPlant() {
        clickListPlant("Monstera")
        composeTestRule.onNodeWithText(string(R.string.delete_plant)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.cancel)).performClick()
        composeTestRule.onNodeWithText(string(R.string.delete_plant)).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.delete_plant)).performScrollTo().performClick()
        composeTestRule
            .onAllNodesWithText(string(R.string.delete_plant))
            .onLast()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.plants_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Monstera").assertDoesNotExist()
    }

    @Test
    fun detailCreateNoteAndSave_returnsToDetailWithNewNote() {
        clickListPlant("Monstera")
        composeTestRule.onNodeWithText(string(R.string.add_note)).performScrollTo().performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Watered today")
        composeTestRule.onAllNodesWithText(string(R.string.save)).onFirst().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Watered today").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Monstera deliciosa").assertExists()
    }

    @Test
    fun detailEditNoteAndSave_returnsToDetailWithUpdatedNote() {
        noteRepository.replaceNotes(
            listOf(
                PlantNote(
                    id = "note-id",
                    plantId = "monstera-id",
                    body = "Old note",
                    createdAt = 1_700_000_000_000L,
                    updatedAt = 1_700_000_000_000L,
                ),
            ),
        )
        clickListPlant("Monstera")
        composeTestRule.onNodeWithText(string(R.string.edit_note)).performScrollTo().performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Updated note")
        composeTestRule.onAllNodesWithText(string(R.string.save)).onFirst().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Updated note").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Old note").assertDoesNotExist()
    }

    @Test
    fun detailQuickCare_staysInlineAndOpensFilteredHistorySheet() {
        clickListPlant("Monstera")
        composeTestRule.onNodeWithText(string(R.string.add_watering)).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Monstera deliciosa").assertIsDisplayed()
        assertEquals(1, careRepository.currentEvents.size)

        composeTestRule.onNodeWithText(string(R.string.care_watering)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(
            string(R.string.care_type_history, string(R.string.care_watering)),
        ).assertIsDisplayed()
    }

    private fun clickListPlant(name: String) {
        composeTestRule
            .onNode(hasText(name, substring = false) and hasClickAction())
            .performClick()
    }

    private fun string(id: Int, vararg arguments: Any): String =
        composeTestRule.activity.getString(id, *arguments)

    private fun testNotice() = AppNotice(
        id = "notice-id",
        ruleId = "rule-id",
        plantId = "monstera-id",
        plantName = "Monstera",
        careType = CareType.WATERING,
        dueEpochDay = 21_000,
        createdAt = 1_800_000_000_000,
        readAt = null,
        deliveryState = NoticeDeliveryState.DELIVERED,
    )
}
