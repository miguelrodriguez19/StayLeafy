package com.miguelrodriguez19.stayleafy.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.testdi.FakePlantRepository
import com.miguelrodriguez19.stayleafy.testdi.fakePlant
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
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

    @Before
    fun setUp() {
        hiltRule.inject()
        repository.reset()
        repository.replacePlants(
            listOf(
                fakePlant(
                    id = "monstera-id",
                    displayName = "Monstera",
                    commonName = "Swiss cheese plant",
                    scientificName = "Monstera deliciosa",
                    location = "Living room",
                    description = "Bright indirect light",
                ),
                fakePlant(
                    id = "fern-id",
                    displayName = "Fern",
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

        composeTestRule.onNodeWithText("Swiss cheese plant").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monstera deliciosa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Living room").assertIsDisplayed()
    }

    @Test
    fun detailEditAndBack_returnsToDetail() {
        clickListPlant("Monstera")
        composeTestRule.onNodeWithText(string(R.string.edit_plant)).performClick()
        composeTestRule.onNodeWithText(string(R.string.edit_plant)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(string(R.string.cancel)).performClick()

        composeTestRule.onNodeWithText("Swiss cheese plant").assertIsDisplayed()
    }

    @Test
    fun detailDeleteConfirm_returnsToListWithoutDeletedPlant() {
        clickListPlant("Monstera")
        composeTestRule.onNodeWithText(string(R.string.delete_plant)).performClick()
        composeTestRule.onNodeWithText(string(R.string.cancel)).performClick()
        composeTestRule.onNodeWithText("Swiss cheese plant").assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.delete_plant)).performClick()
        composeTestRule
            .onAllNodesWithText(string(R.string.delete_plant))
            .onLast()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.plants_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Monstera").assertDoesNotExist()
    }

    private fun clickListPlant(name: String) {
        composeTestRule
            .onNode(hasText(name, substring = false) and hasClickAction())
            .performClick()
    }

    private fun string(id: Int): String = composeTestRule.activity.getString(id)
}
