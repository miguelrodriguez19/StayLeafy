package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PlantDetailScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contentState_displaysDetailsAndForwardsActions() {
        val plant = detailPlant(
            displayName = "Monstera",
            commonName = "Swiss cheese plant",
            scientificName = "Monstera deliciosa",
            location = "Living room",
            acquiredEpochDay = 0L,
            description = "Bright indirect light",
        )
        var favorite: Boolean? = null
        var editClicks = 0
        setScreen(
            state = PlantDetailUiState.Content(plant),
            onFavoriteClick = { favorite = it },
            onEdit = { editClicks++ },
        )

        composeTestRule.onAllNodesWithText("Monstera").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Swiss cheese plant").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monstera deliciosa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Living room").assertIsDisplayed()
        composeTestRule.onNodeWithText(formatEpochDay(0L)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Bright indirect light").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.add_favorite)).performClick()
        composeTestRule.onNodeWithText(string(R.string.edit_plant)).performClick()

        assertEquals(true, favorite)
        assertEquals(1, editClicks)
    }

    @Test
    fun deleteDialog_cancelDoesNotDelete_confirmForwardsDelete() {
        var deleteClicks = 0
        setScreen(
            state = PlantDetailUiState.Content(detailPlant("Monstera")),
            onDelete = { deleteClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.delete_plant)).performClick()
        composeTestRule.onNodeWithText(string(R.string.delete_plant_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.delete_plant_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.cancel)).performClick()
        assertEquals(0, deleteClicks)

        composeTestRule.onNodeWithText(string(R.string.delete_plant)).performClick()
        composeTestRule
            .onAllNodesWithText(string(R.string.delete_plant))
            .onLast()
            .performClick()

        assertEquals(1, deleteClicks)
    }

    @Test
    fun loadingState_isDisplayed() {
        setScreen(state = PlantDetailUiState.Loading)
        composeTestRule.onNodeWithText(string(R.string.loading)).assertIsDisplayed()
    }

    @Test
    fun errorState_offersRetry() {
        var retryClicks = 0
        setScreen(
            state = PlantDetailUiState.Error(),
            onRetry = { retryClicks++ },
        )
        composeTestRule.onNodeWithText(string(R.string.load_plant_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.retry)).performClick()
        assertEquals(1, retryClicks)
    }

    @Test
    fun notFoundState_offersBackNavigation() {
        var backClicks = 0
        setScreen(
            state = PlantDetailUiState.NotFound,
            onBack = { backClicks++ },
        )
        composeTestRule.onNodeWithText(string(R.string.plant_not_found)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.back_to_plants)).performClick()
        assertEquals(1, backClicks)
    }

    @Test
    fun contentState_usesSpanishResources() {
        val configuration = Configuration(
            composeTestRule.activity.resources.configuration,
        ).apply {
            setLocale(Locale.forLanguageTag("es"))
        }
        val localizedContext = composeTestRule.activity.createConfigurationContext(configuration)

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
            ) {
                StayLeafyTheme(dynamicColor = false) {
                    PlantDetailScreen(
                        state = PlantDetailUiState.Content(detailPlant("Monstera")),
                        mutationError = null,
                        isDeleting = false,
                        onBack = {},
                        onRetry = {},
                        onEdit = {},
                        onFavoriteClick = {},
                        onDelete = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Nombre").assertIsDisplayed()
        composeTestRule.onNodeWithText("Editar planta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Eliminar planta").assertIsDisplayed()
    }

    private fun setScreen(
        state: PlantDetailUiState,
        mutationError: UiErrorCategory? = null,
        isDeleting: Boolean = false,
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        onEdit: () -> Unit = {},
        onFavoriteClick: (Boolean) -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantDetailScreen(
                    state = state,
                    mutationError = mutationError,
                    isDeleting = isDeleting,
                    onBack = onBack,
                    onRetry = onRetry,
                    onEdit = onEdit,
                    onFavoriteClick = onFavoriteClick,
                    onDelete = onDelete,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = composeTestRule.activity.getString(id)
}

private fun detailPlant(
    displayName: String,
    commonName: String? = null,
    scientificName: String? = null,
    location: String? = null,
    acquiredEpochDay: Long? = null,
    description: String? = null,
): Plant = Plant(
    id = "detail-id",
    displayName = displayName,
    commonName = commonName,
    scientificName = scientificName,
    location = location,
    acquiredEpochDay = acquiredEpochDay,
    description = description,
    mainPhotoPath = null,
    isFavorite = false,
    createdAt = 1L,
    updatedAt = 1L,
)
