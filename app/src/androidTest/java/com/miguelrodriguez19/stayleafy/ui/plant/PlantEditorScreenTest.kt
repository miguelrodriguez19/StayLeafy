package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PlantEditorScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editForm_displaysFieldsAndAcquisitionDate() {
        val date = 0L
        setScreen(
            PlantEditorUiState(
                mode = PlantEditorMode.Edit,
                isLoading = false,
                form = PlantEditorForm(
                    displayName = "Ficus",
                    commonName = "Rubber plant",
                    scientificName = "Ficus elastica",
                    location = "Office",
                    acquiredEpochDay = date,
                    description = "Near the window",
                ),
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.edit_plant)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Ficus").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rubber plant").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ficus elastica").assertIsDisplayed()
        composeTestRule.onNodeWithText("Office").assertIsDisplayed()
        composeTestRule.onNodeWithText(formatEpochDay(date)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Near the window").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.clear_date)).assertIsDisplayed()
    }

    @Test
    fun acquisitionDateField_opensDatePicker() {
        setScreen(
            PlantEditorUiState(
                mode = PlantEditorMode.Create,
                isLoading = false,
                form = PlantEditorForm(),
            ),
        )

        composeTestRule
            .onNodeWithContentDescription(string(R.string.acquisition_date))
            .performClick()

        composeTestRule
            .onAllNodesWithText(string(R.string.select_date))
            .onLast()
            .assertExists()
        composeTestRule
            .onAllNodesWithText(string(R.string.cancel))
            .onLast()
            .assertExists()
    }

    @Test
    fun form_forwardsFieldDateSaveAndBackCallbacks() {
        var displayName: String? = null
        var commonName: String? = null
        var scientificName: String? = null
        var location: String? = null
        var description: String? = null
        var selectedDate: Long? = Long.MIN_VALUE
        var saveClicks = 0
        var backClicks = 0
        var state by mutableStateOf(
            PlantEditorUiState(
                mode = PlantEditorMode.Create,
                isLoading = false,
                form = PlantEditorForm(
                    displayName = "Ficus",
                    commonName = "Rubber",
                    scientificName = "Ficus elastica",
                    location = "Office",
                    acquiredEpochDay = 0L,
                    description = "Notes",
                ),
            ),
        )
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantEditorScreen(
                    state = state,
                    onBack = { backClicks++ },
                    onDisplayNameChange = {
                        displayName = it
                        state = state.copy(form = state.form.copy(displayName = it))
                    },
                    onCommonNameChange = {
                        commonName = it
                        state = state.copy(form = state.form.copy(commonName = it))
                    },
                    onScientificNameChange = {
                        scientificName = it
                        state = state.copy(form = state.form.copy(scientificName = it))
                    },
                    onLocationChange = {
                        location = it
                        state = state.copy(form = state.form.copy(location = it))
                    },
                    onAcquiredEpochDayChange = {
                        selectedDate = it
                        state = state.copy(form = state.form.copy(acquiredEpochDay = it))
                    },
                    onDescriptionChange = {
                        description = it
                        state = state.copy(form = state.form.copy(description = it))
                    },
                    onRetryLoad = {},
                    onSave = { saveClicks++ },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNode(hasText("Ficus", substring = false) and hasSetTextAction())
            .performTextReplacement("Ficus new")
        composeTestRule
            .onNode(hasText("Rubber", substring = false) and hasSetTextAction())
            .performTextReplacement("Rubber plant")
        composeTestRule
            .onNode(hasText("Ficus elastica", substring = false) and hasSetTextAction())
            .performTextReplacement("Ficus updated")
        composeTestRule
            .onNode(hasText("Office", substring = false) and hasSetTextAction())
            .performTextReplacement("Living room")
        composeTestRule
            .onNode(hasText("Notes", substring = false) and hasSetTextAction())
            .performTextReplacement("Updated notes")
        composeTestRule.onNodeWithText(string(R.string.clear_date)).performClick()
        composeTestRule.onNodeWithText(string(R.string.save)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.cancel)).performClick()

        assertEquals("Ficus new", displayName)
        assertEquals("Rubber plant", commonName)
        assertEquals("Ficus updated", scientificName)
        assertEquals("Living room", location)
        assertEquals("Updated notes", description)
        assertEquals(null, selectedDate)
        assertEquals(1, saveClicks)
        assertEquals(1, backClicks)
    }

    @Test
    fun blankName_showsValidationMessage() {
        setScreen(
            PlantEditorUiState(
                mode = PlantEditorMode.Create,
                isLoading = false,
                form = PlantEditorForm(),
                displayNameError = true,
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.display_name_required)).assertIsDisplayed()
    }

    @Test
    fun loadingState_isDisplayed() {
        setScreen(
            PlantEditorUiState(
                mode = PlantEditorMode.Edit,
                isLoading = true,
                form = PlantEditorForm(),
            ),
        )
        composeTestRule.onNodeWithText(string(R.string.loading)).assertIsDisplayed()
    }

    @Test
    fun loadErrorState_offersRetry() {
        var retryClicks = 0
        setScreen(
            state = PlantEditorUiState(
                mode = PlantEditorMode.Edit,
                isLoading = false,
                form = PlantEditorForm(),
                loadError = UiErrorCategory.Load,
            ),
            onRetryLoad = { retryClicks++ },
        )
        composeTestRule.onNodeWithText(string(R.string.load_plant_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.retry)).performClick()

        assertEquals(1, retryClicks)
    }

    @Test
    fun createForm_usesSpanishResources() {
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
                    PlantEditorScreen(
                        state = PlantEditorUiState(
                            mode = PlantEditorMode.Create,
                            isLoading = false,
                            form = PlantEditorForm(),
                        ),
                        onBack = {},
                        onDisplayNameChange = {},
                        onCommonNameChange = {},
                        onScientificNameChange = {},
                        onLocationChange = {},
                        onAcquiredEpochDayChange = {},
                        onDescriptionChange = {},
                        onRetryLoad = {},
                        onSave = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Añadir planta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nombre").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar").assertIsDisplayed()
    }

    private fun setScreen(
        state: PlantEditorUiState,
        onBack: () -> Unit = {},
        onDisplayNameChange: (String) -> Unit = {},
        onCommonNameChange: (String) -> Unit = {},
        onScientificNameChange: (String) -> Unit = {},
        onLocationChange: (String) -> Unit = {},
        onAcquiredEpochDayChange: (Long?) -> Unit = {},
        onDescriptionChange: (String) -> Unit = {},
        onRetryLoad: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantEditorScreen(
                    state = state,
                    onBack = onBack,
                    onDisplayNameChange = onDisplayNameChange,
                    onCommonNameChange = onCommonNameChange,
                    onScientificNameChange = onScientificNameChange,
                    onLocationChange = onLocationChange,
                    onAcquiredEpochDayChange = onAcquiredEpochDayChange,
                    onDescriptionChange = onDescriptionChange,
                    onRetryLoad = onRetryLoad,
                    onSave = onSave,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = composeTestRule.activity.getString(id)
}
