package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
class PlantScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingState_displaysProgress() {
        setScreen(state = PlantListUiState.Loading)

        composeTestRule.onNodeWithText(string(R.string.loading)).assertIsDisplayed()
    }

    @Test
    fun emptyState_offersAddingFirstPlant() {
        var addClicks = 0
        setScreen(
            state = PlantListUiState.Empty(isFiltered = false),
            onAddPlant = { addClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.no_plants_yet)).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(string(R.string.add_plant))
            .onLast()
            .performClick()

        assertEquals(1, addClicks)
    }

    @Test
    fun filteredEmptyState_clearsFilters() {
        var clearClicks = 0
        setScreen(
            state = PlantListUiState.Empty(isFiltered = true),
            filterQuery = "fern",
            onClearFilters = { clearClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.no_matching_plants)).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(string(R.string.clear_filters))
            .onLast()
            .performClick()

        assertEquals(1, clearClicks)
    }

    @Test
    fun contentState_displaysFieldsAndForwardsPlantClick() {
        val plant = testPlant(
            id = "aloe-id",
            name = "Aloe",
            scientificName = "Aloe barbadensis",
            location = "Kitchen",
        )
        var clickedId: String? = null
        var addClicks = 0
        setScreen(
            state = PlantListUiState.Content(listOf(plant)),
            onAddPlant = { addClicks++ },
            onPlantClick = { clickedId = it },
        )

        composeTestRule.onNodeWithText(string(R.string.add_plant)).performClick()
        composeTestRule.onNodeWithText("Aloe").assertIsDisplayed()
        composeTestRule.onNodeWithText("Aloe barbadensis").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kitchen").assertIsDisplayed()
        composeTestRule
            .onNode(hasText("Aloe", substring = false) and hasClickAction())
            .performClick()

        assertEquals("aloe-id", clickedId)
        assertEquals(1, addClicks)
    }

    @Test
    fun favoriteButton_forwardsNewFavoriteValue() {
        val plant = testPlant(id = "fern-id", name = "Fern")
        var favoriteChange: Pair<String, Boolean>? = null
        setScreen(
            state = PlantListUiState.Content(listOf(plant)),
            onFavoriteClick = { id, favorite -> favoriteChange = id to favorite },
        )

        composeTestRule
            .onNodeWithContentDescription(string(R.string.add_favorite))
            .performClick()

        assertEquals("fern-id" to true, favoriteChange)
    }

    @Test
    fun favoriteMutationError_canBeDismissed() {
        var dismissClicks = 0
        setScreen(
            state = PlantListUiState.Content(listOf(testPlant("fern-id", "Fern"))),
            mutationError = UiErrorCategory.Favorite,
            onDismissMutationError = { dismissClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.favorite_update_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.dismiss)).performClick()

        assertEquals(1, dismissClicks)
    }

    @Test
    fun errorState_forwardsRetry() {
        var retryClicks = 0
        setScreen(
            state = PlantListUiState.Error(),
            onRetry = { retryClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.load_plants_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.retry)).performClick()

        assertEquals(1, retryClicks)
    }

    @Test
    fun clearFiltersButton_isHiddenWithoutFilters() {
        setScreen(state = PlantListUiState.Empty(isFiltered = false))

        composeTestRule.onNodeWithText(string(R.string.clear_filters)).assertDoesNotExist()
    }

    @Test
    fun emptyState_usesSpanishResources() {
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
                    PlantListScreen(
                        state = PlantListUiState.Empty(isFiltered = false),
                        filterQuery = "",
                        favoritesOnly = false,
                        mutationError = null,
                        onQueryChange = {},
                        onFavoritesOnlyChange = {},
                        onClearFilters = {},
                        onRetry = {},
                        onAddPlant = {},
                        onPlantClick = {},
                        onFavoriteClick = { _, _ -> },
                        onDismissMutationError = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Mis plantas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Aún no hay plantas").assertIsDisplayed()
    }

    private fun setScreen(
        state: PlantListUiState,
        filterQuery: String = "",
        favoritesOnly: Boolean = false,
        mutationError: UiErrorCategory? = null,
        onQueryChange: (String) -> Unit = {},
        onFavoritesOnlyChange: (Boolean) -> Unit = {},
        onClearFilters: () -> Unit = {},
        onRetry: () -> Unit = {},
        onAddPlant: () -> Unit = {},
        onPlantClick: (String) -> Unit = {},
        onFavoriteClick: (String, Boolean) -> Unit = { _, _ -> },
        onDismissMutationError: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantListScreen(
                    state = state,
                    filterQuery = filterQuery,
                    favoritesOnly = favoritesOnly,
                    mutationError = mutationError,
                    onQueryChange = onQueryChange,
                    onFavoritesOnlyChange = onFavoritesOnlyChange,
                    onClearFilters = onClearFilters,
                    onRetry = onRetry,
                    onAddPlant = onAddPlant,
                    onPlantClick = onPlantClick,
                    onFavoriteClick = onFavoriteClick,
                    onDismissMutationError = onDismissMutationError,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = composeTestRule.activity.getString(id)
}

private fun testPlant(
    id: String,
    name: String,
    scientificName: String? = null,
    location: String? = null,
    isFavorite: Boolean = false,
): Plant = Plant(
    id = id,
    name = name,
    scientificName = scientificName,
    location = location,
    acquiredEpochDay = null,
    description = null,
    mainPhotoPath = null,
    isFavorite = isFavorite,
    createdAt = 1L,
    updatedAt = 1L,
)
