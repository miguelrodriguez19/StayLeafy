package com.miguelrodriguez19.stayleafy.ui.plant

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.data.photo.PLANT_PHOTO_DIRECTORY
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.CareDueStatus
import com.miguelrodriguez19.stayleafy.model.BulkCareResult
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import com.miguelrodriguez19.stayleafy.ui.components.SettingsSectionCard
import com.miguelrodriguez19.stayleafy.ui.components.SettingsSegment
import com.miguelrodriguez19.stayleafy.ui.components.SettingsSegmentedControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
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

        composeTestRule.onNodeWithContentDescription(string(R.string.add_plant)).performClick()
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
    fun bulkCareAction_usesChecklistDescriptionBesideSettings() {
        var startCount = 0
        setScreen(
            state = PlantListUiState.Content(listOf(testPlant("fern-id", "Fern"))),
            onStartBulkCare = { startCount++ },
        )

        composeTestRule.onNodeWithContentDescription(string(R.string.bulk_care_action_description)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.settings)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.plants_title)).assertIsDisplayed()
        assertEquals(1, startCount)
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
    fun careFilters_forwardIndependentSelections() {
        var upcoming = false
        var overdue = false
        setScreen(
            state = PlantListUiState.Content(listOf(testPlant("fern-id", "Fern"))),
            onUpcomingOnlyChange = { upcoming = it },
            onOverdueOnlyChange = { overdue = it },
        )

        composeTestRule.onNodeWithText(string(R.string.upcoming_filter)).performClick()
        composeTestRule.onNodeWithText(string(R.string.overdue_filter)).performClick()

        assertEquals(true, upcoming)
        assertEquals(true, overdue)
    }

    @Test
    fun careSummary_displaysMostUrgentAndAdditionalCount() {
        val plant = testPlant("fern-id", "Fern")
        setScreen(
            state = PlantListUiState.Content(listOf(plant)),
            careSummaries = mapOf(
                plant.id to listOf(
                    CareStatusSummary(plant.id, CareType.PRUNING, 21_001, CareDueStatus.UPCOMING),
                    CareStatusSummary(plant.id, CareType.WATERING, 21_000, CareDueStatus.OVERDUE),
                ),
            ),
        )

        composeTestRule.onNodeWithText(
            string(R.string.compact_care_status, string(R.string.care_watering), formatEpochDay(21_000)),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.more_care_rules, 1)).assertIsDisplayed()
    }

    @Test
    fun selectionMode_togglesWholeRowsAndSelectsAllVisible_withoutFavoriteOrAddActions() {
        val visiblePlant = testPlant(id = "aloe", name = "Aloe")
        var toggledPlant: Plant? = null
        var selectedVisible: List<Plant> = emptyList()
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                Box(ModifierForNarrowScreen()) {
                    PlantListScreen(
                        state = PlantListUiState.Content(listOf(visiblePlant)),
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
                        bulkCare = BulkCareUiState(
                            isSelectionMode = true,
                            selectedPlantIds = listOf("hidden-fern"),
                            selectedPlants = mapOf(
                                "hidden-fern" to BulkCarePlantPreview("hidden-fern", "Hidden fern", null),
                            ),
                            todayEpochDay = 20_000,
                            performedEpochDay = 20_000,
                        ),
                        onToggleBulkCarePlant = { toggledPlant = it },
                        onSelectVisiblePlants = { selectedVisible = it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(string(R.string.bulk_care_select_plants)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.bulk_care_selection_count, 1)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Hidden: 1").assertDoesNotExist()
        composeTestRule.onNodeWithText("Hidden fern").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(string(R.string.add_plant)).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(string(R.string.add_favorite)).assertDoesNotExist()
        composeTestRule.onNode(hasText("Aloe", substring = false) and hasClickAction()).performClick()
        assertEquals(visiblePlant, toggledPlant)

        composeTestRule.onNodeWithText(string(R.string.bulk_care_select_visible)).performClick()
        assertEquals(listOf(visiblePlant), selectedVisible)
    }

    @Test
    fun careForm_showsEverySelectedPlantAndDefaultDate_withoutChoosingCareType() {
        val selectedDay = 20_000L
        val photoDirectory = File(composeTestRule.activity.filesDir, PLANT_PHOTO_DIRECTORY)
        check(photoDirectory.mkdirs() || photoDirectory.isDirectory)
        val photoFile = File(photoDirectory, "bulk-preview-${System.currentTimeMillis()}.jpg")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(photoFile).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
            setBulkCareScreen(
                state = BulkCareUiState(
                    isSelectionMode = true,
                    selectedPlantIds = listOf("hidden-id", "visible-id"),
                    selectedPlants = mapOf(
                        "hidden-id" to BulkCarePlantPreview(
                            "hidden-id",
                            "Hidden Fern",
                            "$PLANT_PHOTO_DIRECTORY/${photoFile.name}",
                        ),
                        "visible-id" to BulkCarePlantPreview("visible-id", "Visible Aloe", null),
                    ),
                    isFormOpen = true,
                    todayEpochDay = selectedDay,
                    performedEpochDay = selectedDay,
                ),
            )
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                photoFile.isFile && photoFile.length() > 0L &&
                    composeTestRule.onAllNodesWithContentDescription(string(R.string.plant_photo))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
            }

            composeTestRule.onNodeWithText(string(R.string.bulk_care_form_title)).assertIsDisplayed()
            composeTestRule.onNodeWithText(string(R.string.bulk_care_selection_count, 2)).assertIsDisplayed()
            composeTestRule.onNodeWithText("Hidden Fern").assertIsDisplayed()
            composeTestRule.onNodeWithText("Visible Aloe").assertIsDisplayed()
            composeTestRule.onNodeWithTag("bulk_care_preview_photo_hidden-id")
                .assertWidthIsEqualTo(40.dp)
                .assertHeightIsEqualTo(40.dp)
            composeTestRule.onNodeWithContentDescription(string(R.string.plant_photo)).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(string(R.string.no_plant_photo)).assertIsDisplayed()
            composeTestRule.onNodeWithText(formatEpochDay(selectedDay)).assertIsDisplayed()
            composeTestRule.onNodeWithText(string(R.string.bulk_care_type_water)).assertIsDisplayed()
            composeTestRule.onNodeWithText(string(R.string.bulk_care_type_prune)).assertIsDisplayed()
            composeTestRule.onNodeWithText(string(R.string.bulk_care_type_fertilize)).assertIsDisplayed()
            composeTestRule.onNodeWithText(string(R.string.bulk_care_record)).assertIsDisplayed().assertIsNotEnabled()
        } finally {
            bitmap.recycle()
            photoFile.delete()
            photoDirectory.delete()
        }
    }

    @Test
    fun confirmationDialog_showsCareDateAndCount_andCancelKeepsForm() {
        var cancelCount = 0
        val selectedDay = 20_000L
        setBulkCareScreen(
            state = BulkCareUiState(
                isSelectionMode = true,
                selectedPlantIds = listOf("hidden-id", "visible-id"),
                selectedPlants = mapOf(
                    "hidden-id" to BulkCarePlantPreview("hidden-id", "Hidden Fern", null),
                    "visible-id" to BulkCarePlantPreview("visible-id", "Visible Aloe", null),
                ),
                isFormOpen = true,
                isConfirming = true,
                careType = CareType.WATERING,
                todayEpochDay = selectedDay,
                performedEpochDay = selectedDay,
            ),
            onCancel = { cancelCount++ },
        )

        composeTestRule.onNodeWithText(string(R.string.bulk_care_confirm_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            string(
                R.string.bulk_care_confirm_summary,
                string(R.string.care_watering),
                formatEpochDay(selectedDay),
                2,
            ),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.cancel)).performClick()
        assertEquals(1, cancelCount)
        composeTestRule.onNodeWithText("Hidden Fern").assertIsDisplayed()
    }

    @Test
    fun completionBanner_isVisibleWhenFilteredListIsEmpty_andShowsMixedResult() {
        var dismissCount = 0
        val performedDay = 20_000L
        setBulkCareScreen(
            state = BulkCareUiState(
                completion = BulkCareCompletion(
                    careType = CareType.WATERING,
                    performedEpochDay = performedDay,
                    result = BulkCareResult(listOf("aloe"), listOf("fern")),
                ),
            ),
            screenState = PlantListUiState.Empty(isFiltered = true),
            filterQuery = "nothing matches",
            onDismiss = { dismissCount++ },
        )

        composeTestRule.onNodeWithText(string(R.string.no_matching_plants)).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            string(
                R.string.bulk_care_completion_context,
                string(R.string.care_watering),
                formatEpochDay(performedDay),
            ),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(quantity(R.plurals.bulk_care_recorded_count, 1)).assertIsDisplayed()
        composeTestRule.onNodeWithText(quantity(R.plurals.bulk_care_skipped_count, 1)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.bulk_care_dismiss_result)).performClick()
        assertEquals(1, dismissCount)
    }

    @Test
    fun completionBanner_allDuplicatesExplainsWhyNothingWasRecorded() {
        val performedDay = 20_000L
        setBulkCareScreen(
            state = BulkCareUiState(
                completion = BulkCareCompletion(
                    careType = CareType.PRUNING,
                    performedEpochDay = performedDay,
                    result = BulkCareResult(emptyList(), listOf("fern", "aloe")),
                ),
            ),
        )

        composeTestRule.onNodeWithText(quantity(R.plurals.bulk_care_skipped_count, 2)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.bulk_care_duplicates_only)).assertIsDisplayed()
        composeTestRule.onNodeWithText(quantity(R.plurals.bulk_care_recorded_count, 0)).assertDoesNotExist()
    }

    @Test
    fun sharedSettingsSegmentControl_startsUnselectedAndRetainsSettingsTagsAndCallbacks() {
        val selected = mutableStateOf<String?>(null)
        var selectedCallback: String? = null
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                SettingsSectionCard(title = "Appearance", icon = R.drawable.ic_leaf) {
                    SettingsSegmentedControl(
                        options = listOf(
                            SettingsSegment("system", "System", "settings_theme_system"),
                            SettingsSegment("light", "Light", "settings_theme_light"),
                            SettingsSegment("dark", "Dark", "settings_theme_dark"),
                        ),
                        selectedKey = selected.value,
                        enabled = true,
                        onSelected = { key ->
                            selectedCallback = key
                            selected.value = key
                        },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("settings_theme_system").assertIsNotSelected()
        composeTestRule.onNodeWithTag("settings_theme_light").performClick()
        composeTestRule.onNodeWithTag("settings_theme_light").assertIsSelected()
        assertEquals("light", selectedCallback)
    }

    @Test
    fun narrowLargeTextForm_wrapsPlantNamesAndDateRowWithoutClipping() {
        val longPlantName = "Monstera deliciosa with broad leaves in a terracotta pot beside the sunny kitchen window"
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("es"))
        }
        val localizedContext = composeTestRule.activity.createConfigurationContext(configuration)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1.3f),
                LocalConfiguration provides configuration,
                LocalContext provides localizedContext,
            ) {
                StayLeafyTheme(darkTheme = true, dynamicColor = false) {
                    Box(androidx.compose.ui.Modifier.width(320.dp).height(640.dp)) {
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
                            bulkCare = BulkCareUiState(
                                isSelectionMode = true,
                                selectedPlantIds = listOf("long-name"),
                                selectedPlants = mapOf(
                                    "long-name" to BulkCarePlantPreview("long-name", longPlantName, null),
                                ),
                                isFormOpen = true,
                                careType = CareType.WATERING,
                                todayEpochDay = 20_000,
                                performedEpochDay = 20_000,
                            ),
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Registrar cuidado").assertIsDisplayed()
        composeTestRule.onNodeWithText(longPlantName).assertHeightIsAtLeast(40.dp)
        composeTestRule.onNodeWithText("Fecha del cuidado").assertIsDisplayed()
        composeTestRule.onNodeWithText(formatEpochDay(20_000)).assertIsDisplayed()
        val dateBounds = composeTestRule.onNodeWithText(formatEpochDay(20_000)).getUnclippedBoundsInRoot()
        assertTrue(dateBounds.right <= 320.dp)
        composeTestRule.onNodeWithText("Registrar").assertIsDisplayed()
    }

    @Test
    fun narrowSelectionTopBar_keepsTitleAndSelectionControlsVisible() {
        val plant = testPlant(id = "aloe", name = "Aloe")
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                Box(ModifierForNarrowScreen()) {
                    PlantListScreen(
                        state = PlantListUiState.Content(listOf(plant)),
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
                        bulkCare = BulkCareUiState(
                            isSelectionMode = true,
                            selectedPlantIds = listOf("hidden"),
                            selectedPlants = mapOf("hidden" to BulkCarePlantPreview("hidden", "Hidden plant", null)),
                            todayEpochDay = 20_000,
                            performedEpochDay = 20_000,
                        ),
                    )
                }
            }
        }
        composeTestRule.onNodeWithText(string(R.string.bulk_care_select_plants)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.bulk_care_select_visible)).assertIsDisplayed()
    }

    @Test
    fun listPosition_survivesContentLoadingContentWithExactIndexAndOffset() {
        val plants = numberedPlants(40)
        val state = mutableStateOf<PlantListUiState>(PlantListUiState.Content(plants))
        val listState = LazyListState()

        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantListScreen(
                    state = state.value,
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
                    listState = listState,
                )
            }
        }

        scrollTo(listState, index = 18, offset = 27)
        composeTestRule.runOnIdle { state.value = PlantListUiState.Loading }
        composeTestRule.onNodeWithText(string(R.string.loading)).assertIsDisplayed()
        composeTestRule.runOnIdle { state.value = PlantListUiState.Content(plants) }
        composeTestRule.waitForIdle()

        assertListPosition(listState, index = 18, offset = 27)
    }

    @Test
    fun listPosition_clampsToLastIndexWhenContentShrinks() {
        val plants = numberedPlants(40)
        val state = mutableStateOf<PlantListUiState>(PlantListUiState.Content(plants))
        val listState = LazyListState()

        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantListScreen(
                    state = state.value,
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
                    listState = listState,
                )
            }
        }

        scrollTo(listState, index = 25, offset = 31)
        composeTestRule.runOnIdle {
            state.value = PlantListUiState.Content(plants.take(18))
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertTrue(listState.firstVisibleItemIndex in 0 until 18)
            assertTrue(listState.layoutInfo.visibleItemsInfo.any { it.index == 17 })
        }
        composeTestRule.onNodeWithText("Plant 17").assertIsDisplayed()
        composeTestRule.onNodeWithText("Plant 18").assertDoesNotExist()
    }

    @Test
    fun listPosition_preservesNumericIndexAndOffsetWhenItemsReorder() {
        val plants = numberedPlants(40)
        val state = mutableStateOf<PlantListUiState>(PlantListUiState.Content(plants))
        val listState = LazyListState()

        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantListScreen(
                    state = state.value,
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
                    listState = listState,
                )
            }
        }

        scrollTo(listState, index = 18, offset = 27)
        val reordered = plants.toMutableList().apply {
            val formerlyVisible = removeAt(18)
            add(0, formerlyVisible)
        }
        composeTestRule.runOnIdle {
            state.value = PlantListUiState.Content(reordered)
        }
        composeTestRule.waitForIdle()

        assertListPosition(listState, index = 18, offset = 27)
        composeTestRule.onNodeWithText("Plant 19").assertIsDisplayed()
    }

    @Test
    fun manualQueryChangeWhileLoading_resetsPositionWhenContentReturns() {
        val plants = numberedPlants(40)
        val query = mutableStateOf("")
        val state = mutableStateOf<PlantListUiState>(PlantListUiState.Content(plants))
        val listState = LazyListState()

        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantListScreen(
                    state = state.value,
                    filterQuery = query.value,
                    favoritesOnly = false,
                    mutationError = null,
                    onQueryChange = {
                        query.value = it
                        state.value = PlantListUiState.Loading
                    },
                    onFavoritesOnlyChange = {},
                    onClearFilters = {
                        query.value = ""
                        state.value = PlantListUiState.Loading
                    },
                    onRetry = {},
                    onAddPlant = {},
                    onPlantClick = {},
                    onFavoriteClick = { _, _ -> },
                    onDismissMutationError = {},
                    listState = listState,
                )
            }
        }

        scrollTo(listState, index = 29, offset = 35)
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Plant")
        composeTestRule.onNodeWithText(string(R.string.loading)).assertIsDisplayed()
        composeTestRule.runOnIdle {
            state.value = PlantListUiState.Content(
                plants.filter { it.name.contains(query.value, ignoreCase = true) },
            )
        }
        composeTestRule.waitForIdle()

        assertListPosition(listState, index = 0, offset = 0)
        composeTestRule.onNodeWithText("Plant 00").assertIsDisplayed()

        scrollTo(listState, index = 29, offset = 35)
        composeTestRule.onNodeWithText(string(R.string.clear_filters)).performClick()
        composeTestRule.onNodeWithText(string(R.string.loading)).assertIsDisplayed()
        composeTestRule.runOnIdle {
            state.value = PlantListUiState.Content(plants)
        }
        composeTestRule.waitForIdle()

        assertListPosition(listState, index = 0, offset = 0)
        composeTestRule.onNodeWithText("Plant 00").assertIsDisplayed()
    }

    @Test
    fun manualFavoriteAndCareFilters_resetListPosition() {
        val plants = numberedPlants(40).map { it.copy(isFavorite = true) }
        val state = mutableStateOf<PlantListUiState>(PlantListUiState.Content(plants))
        val favoritesOnly = mutableStateOf(false)
        val upcomingOnly = mutableStateOf(false)
        val overdueOnly = mutableStateOf(false)
        val listState = LazyListState()

        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantListScreen(
                    state = state.value,
                    filterQuery = "",
                    favoritesOnly = favoritesOnly.value,
                    mutationError = null,
                    onQueryChange = {},
                    onFavoritesOnlyChange = {
                        favoritesOnly.value = it
                        state.value = PlantListUiState.Loading
                    },
                    onClearFilters = {},
                    onRetry = {},
                    onAddPlant = {},
                    onPlantClick = {},
                    onFavoriteClick = { _, _ -> },
                    onDismissMutationError = {},
                    upcomingOnly = upcomingOnly.value,
                    overdueOnly = overdueOnly.value,
                    onUpcomingOnlyChange = {
                        upcomingOnly.value = it
                        state.value = PlantListUiState.Loading
                    },
                    onOverdueOnlyChange = {
                        overdueOnly.value = it
                        state.value = PlantListUiState.Loading
                    },
                    listState = listState,
                )
            }
        }

        fun assertFilterReturnsAtStart() {
            composeTestRule.onNodeWithText(string(R.string.loading)).assertIsDisplayed()
            composeTestRule.runOnIdle { state.value = PlantListUiState.Content(plants) }
            composeTestRule.waitForIdle()
            assertListPosition(listState, index = 0, offset = 0)
        }

        scrollTo(listState, index = 24, offset = 19)
        composeTestRule.onNodeWithText(string(R.string.favorites_only)).performClick()
        assertFilterReturnsAtStart()

        scrollTo(listState, index = 20, offset = 23)
        composeTestRule.onNodeWithText(string(R.string.upcoming_filter)).performClick()
        assertFilterReturnsAtStart()

        scrollTo(listState, index = 15, offset = 35)
        composeTestRule.onNodeWithText(string(R.string.overdue_filter)).performClick()
        assertFilterReturnsAtStart()
    }

    @Test
    fun listPosition_survivesEmptyAndErrorStates() {
        val plants = numberedPlants(40)
        val state = mutableStateOf<PlantListUiState>(PlantListUiState.Content(plants))
        val listState = LazyListState()

        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantListScreen(
                    state = state.value,
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
                    listState = listState,
                )
            }
        }

        scrollTo(listState, index = 23, offset = 41)
        composeTestRule.runOnIdle { state.value = PlantListUiState.Empty(isFiltered = false) }
        composeTestRule.onNodeWithText(string(R.string.no_plants_yet)).assertIsDisplayed()
        composeTestRule.runOnIdle { state.value = PlantListUiState.Loading }
        composeTestRule.runOnIdle { state.value = PlantListUiState.Content(plants) }
        composeTestRule.waitForIdle()
        assertListPosition(listState, index = 23, offset = 41)

        scrollTo(listState, index = 16, offset = 29)
        composeTestRule.runOnIdle { state.value = PlantListUiState.Error() }
        composeTestRule.onNodeWithText(string(R.string.load_plants_error)).assertIsDisplayed()
        composeTestRule.runOnIdle { state.value = PlantListUiState.Loading }
        composeTestRule.runOnIdle { state.value = PlantListUiState.Content(plants) }
        composeTestRule.waitForIdle()
        assertListPosition(listState, index = 16, offset = 29)
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
        composeTestRule.onNodeWithText("Tu rincón verde empieza aquí").assertIsDisplayed()
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
        onStartBulkCare: () -> Unit = {},
        upcomingOnly: Boolean = false,
        overdueOnly: Boolean = false,
        onUpcomingOnlyChange: (Boolean) -> Unit = {},
        onOverdueOnlyChange: (Boolean) -> Unit = {},
        careSummaries: Map<String, List<CareStatusSummary>> = emptyMap(),
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
                    onStartBulkCare = onStartBulkCare,
                    upcomingOnly = upcomingOnly,
                    overdueOnly = overdueOnly,
                    onUpcomingOnlyChange = onUpcomingOnlyChange,
                    onOverdueOnlyChange = onOverdueOnlyChange,
                    careSummaries = careSummaries,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun setBulkCareScreen(
        state: BulkCareUiState,
        screenState: PlantListUiState = PlantListUiState.Empty(isFiltered = false),
        filterQuery: String = "",
        onSubmit: () -> Unit = {},
        onCancel: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantListScreen(
                    state = screenState,
                    filterQuery = filterQuery,
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
                    bulkCare = state,
                    onSubmitBulkCare = onSubmit,
                    onCancelBulkCareConfirmation = onCancel,
                    onDismissBulkCareCompletion = onDismiss,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun ModifierForNarrowScreen(): androidx.compose.ui.Modifier =
        androidx.compose.ui.Modifier.width(360.dp).height(720.dp)

    private fun string(id: Int, vararg arguments: Any): String =
        composeTestRule.activity.getString(id, *arguments)

    private fun quantity(id: Int, count: Int): String =
        composeTestRule.activity.resources.getQuantityString(id, count, count)

    private fun scrollTo(listState: LazyListState, index: Int, offset: Int) {
        composeTestRule.runOnUiThread {
            CoroutineScope(Dispatchers.Main).launch {
                listState.scrollToItem(index, offset)
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            var reachedPosition = false
            composeTestRule.runOnIdle {
                reachedPosition = listState.firstVisibleItemIndex == index &&
                    listState.firstVisibleItemScrollOffset == offset
            }
            reachedPosition
        }
    }

    private fun assertListPosition(listState: LazyListState, index: Int, offset: Int) {
        composeTestRule.runOnIdle {
            assertEquals(index, listState.firstVisibleItemIndex)
            assertEquals(offset, listState.firstVisibleItemScrollOffset)
        }
    }
}

private fun numberedPlants(count: Int): List<Plant> =
    (0 until count).map { index ->
        testPlant(id = "plant-$index", name = "Plant %02d".format(index))
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
