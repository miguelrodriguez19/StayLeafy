package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.Plant
import com.miguelrodriguez19.stayleafy.model.PlantNote
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
    fun createMode_displaysUnifiedPlantFieldsAndActions() {
        setScreen(
            state = PlantDetailUiState.Content(
                mode = PlantDetailMode.Create,
                plant = null,
                form = PlantForm(),
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.add_plant)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.plant_name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.scientific_name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.location)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.acquisition_date)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.description)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.notes)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.take_photo)).assertIsDisplayed()
    }

    @Test
    fun editMode_displaysSameFieldsAndForwardsSaveAndCancel() {
        val plant = detailPlant(
            name = "Monstera",
            scientificName = "Monstera deliciosa",
            location = "Living room",
            acquiredEpochDay = 0L,
            description = "Bright indirect light",
        )
        var saveClicks = 0
        var cancelClicks = 0
        setScreen(
            state = PlantDetailUiState.Content(
                mode = PlantDetailMode.Edit,
                plant = plant,
                form = plant.toForm(),
            ),
            onSave = { saveClicks++ },
            onCancelEdit = { cancelClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.edit_plant)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.plant_name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.scientific_name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.location)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.description)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.save)).performClick()
        composeTestRule.onAllNodesWithText(string(R.string.cancel)).onLast().performClick()

        assertEquals(1, saveClicks)
        assertEquals(1, cancelClicks)
    }

    @Test
    fun viewMode_displaysDetailsAndForwardsFavoriteAndEdit() {
        val plant = detailPlant(
            name = "Monstera",
            scientificName = "Monstera deliciosa",
            location = "Living room",
            acquiredEpochDay = 0L,
            description = "Bright indirect light",
        )
        var favorite: Boolean? = null
        var editClicks = 0
        setScreen(
            state = PlantDetailUiState.Content(
                mode = PlantDetailMode.View,
                plant = plant,
                form = plant.toForm(),
            ),
            onFavoriteClick = { favorite = it },
            onBeginEdit = { editClicks++ },
        )

        composeTestRule.onAllNodesWithText("Monstera").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Monstera deliciosa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Living room").assertIsDisplayed()
        composeTestRule.onNodeWithText(formatEpochDay(0L)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Bright indirect light").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.add_favorite)).performClick()
        composeTestRule.onNodeWithText(string(R.string.edit_plant)).performClick()

        assertEquals(true, favorite)
        assertEquals(1, editClicks)
    }

    @Test
    fun deleteDialog_cancelDoesNotDelete_confirmForwardsDelete() {
        var deleteClicks = 0
        setScreen(
            state = viewState(detailPlant(name = "Monstera")),
            onDelete = { deleteClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.delete_plant)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.delete_plant_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.delete_plant_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.cancel)).performClick()
        assertEquals(0, deleteClicks)

        composeTestRule.onNodeWithText(string(R.string.delete_plant)).performScrollTo().performClick()
        composeTestRule.onAllNodesWithText(string(R.string.delete_plant)).onLast().performClick()

        assertEquals(1, deleteClicks)
    }

    @Test
    fun photoActions_withoutPhoto_offerDirectSources() {
        var takeClicks = 0
        var pickClicks = 0
        setScreen(
            state = viewState(detailPlant(name = "Monstera")),
            onTakePhoto = { takeClicks++ },
            onPickPhoto = { pickClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.take_photo)).performClick()
        composeTestRule.onNodeWithText(string(R.string.choose_photo)).performClick()

        assertEquals(1, takeClicks)
        assertEquals(1, pickClicks)
    }

    @Test
    fun photoActions_withPhoto_offerReplacementSourcesAndRemoval() {
        var takeClicks = 0
        var pickClicks = 0
        var removeClicks = 0
        setScreen(
            state = viewState(
                detailPlant(name = "Monstera", mainPhotoPath = "plant_photos/example.jpg"),
            ),
            onTakePhoto = { takeClicks++ },
            onPickPhoto = { pickClicks++ },
            onRemovePhoto = { removeClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.replace_photo)).performClick()
        composeTestRule.onNodeWithText(string(R.string.take_photo)).performClick()
        composeTestRule.onNodeWithText(string(R.string.replace_photo)).performClick()
        composeTestRule.onNodeWithText(string(R.string.choose_photo)).performClick()
        composeTestRule.onNodeWithText(string(R.string.remove_photo)).performClick()
        composeTestRule.onAllNodesWithText(string(R.string.remove_photo)).onLast().performClick()

        assertEquals(1, takeClicks)
        assertEquals(1, pickClicks)
        assertEquals(1, removeClicks)
    }

    @Test
    fun failedPendingPhotoCleanup_offersRetry() {
        var retryClicks = 0
        setScreen(
            state = viewState(detailPlant(name = "Monstera")),
            photoMutationError = UiErrorCategory.PhotoImport,
            photoCleanupRetryEnabled = true,
            onRetryPhotoCleanup = { retryClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.photo_import_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.retry)).performClick()

        assertEquals(1, retryClicks)
    }

    @Test
    fun notesContent_forwardsAddEditAndConfirmedDelete() {
        val note = PlantNote(
            id = "note-id",
            plantId = "detail-id",
            body = "Watered today",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
        )
        var addClicks = 0
        var editedId: String? = null
        var deletedId: String? = null
        setScreen(
            state = viewState(detailPlant(name = "Monstera")),
            notesState = PlantNotesUiState.Content(listOf(note)),
            onAddNote = { addClicks++ },
            onEditNote = { editedId = it },
            onDeleteNote = { deletedId = it },
        )

        composeTestRule.onNodeWithText(string(R.string.add_note)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.edit_note)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.delete_note)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.delete_note_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.cancel)).performClick()
        assertEquals(null, deletedId)

        composeTestRule.onNodeWithText(string(R.string.delete_note)).performScrollTo().performClick()
        composeTestRule.onAllNodesWithText(string(R.string.delete_note)).onLast().performClick()

        assertEquals(1, addClicks)
        assertEquals("note-id", editedId)
        assertEquals("note-id", deletedId)
    }

    @Test
    fun careSummary_displaysLatestAndNeverStates() {
        val event = careEvent(
            id = "watering-id",
            careType = CareType.WATERING,
            performedAt = 1_700_000_000_000L,
            note = "Watered today",
        )
        setScreen(
            state = viewState(detailPlant(name = "Monstera")),
            careState = CareEventsUiState.Content(listOf(event)),
        )

        composeTestRule.onNodeWithText(string(R.string.care_last_watering)).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(formatTimestamp(event.performedAt)).onFirst().performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.care_last_pruning)).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.care_never)).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Watered today").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun careLoadingState_isDisplayed() {
        setScreen(
            state = viewState(detailPlant(name = "Monstera")),
            careState = CareEventsUiState.Loading,
        )
        composeTestRule.onNodeWithText(string(R.string.care_history)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.care_never)).assertDoesNotExist()
    }

    @Test
    fun careErrorState_forwardsRetry() {
        var retryClicks = 0
        setScreen(
            state = viewState(detailPlant(name = "Monstera")),
            careState = CareEventsUiState.Error(),
            onRetryCare = { retryClicks++ },
        )
        composeTestRule.onNodeWithText(string(R.string.load_care_error)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.care_never)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.retry)).performScrollTo().performClick()
        assertEquals(1, retryClicks)
    }

    @Test
    fun careEmptyState_isDisplayed() {
        setScreen(
            state = viewState(detailPlant(name = "Monstera")),
            careState = CareEventsUiState.Empty,
        )
        composeTestRule.onNodeWithText(string(R.string.no_care_events)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun careActions_forwardQuickAddEditAndDeleteAfterConfirmation() {
        val event = careEvent("care-id", CareType.WATERING, 1_700_000_000_000L)
        var addedType: CareType? = null
        var editedId: String? = null
        var deletedId: String? = null
        setScreen(
            state = viewState(detailPlant(name = "Monstera")),
            careState = CareEventsUiState.Content(listOf(event)),
            onAddCare = { addedType = it },
            onEditCare = { editedId = it },
            onDeleteCare = { deletedId = it },
        )

        composeTestRule.onNodeWithText(string(R.string.add_pruning)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.edit_care_event)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.delete_care_event)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.delete_care_event_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.cancel)).performClick()
        assertEquals(CareType.PRUNING, addedType)
        assertEquals("care-id", editedId)
        assertEquals(null, deletedId)

        composeTestRule.onNodeWithText(string(R.string.delete_care_event)).performScrollTo().performClick()
        composeTestRule.onAllNodesWithText(string(R.string.delete_care_event)).onLast().performClick()
        assertEquals("care-id", deletedId)
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
                        state = viewState(detailPlant(name = "Monstera")),
                        notesState = PlantNotesUiState.Empty,
                        notesMutationError = null,
                        notesMutating = false,
                        photoMutating = false,
                        photoMutationError = null,
                        photoCleanupRetryEnabled = false,
                        mutationError = null,
                        isDeleting = false,
                        onBack = {},
                        onRetry = {},
                        onBeginEdit = {},
                        onCancelEdit = {},
                        onNameChange = {},
                        onScientificNameChange = {},
                        onLocationChange = {},
                        onAcquiredEpochDayChange = {},
                        onDescriptionChange = {},
                        onFavoriteClick = {},
                        onSave = {},
                        onDelete = {},
                        onAddNote = {},
                        onEditNote = {},
                        onDeleteNote = {},
                        onRetryNotes = {},
                        onAddStagedNote = {},
                        onUpdateStagedNote = { _, _ -> },
                        onRemoveStagedNote = {},
                        onTakePhoto = {},
                        onPickPhoto = {},
                        onRemoveStagedPhoto = {},
                        onRemovePhoto = {},
                        onRetryPhotoCleanup = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Nombre").assertIsDisplayed()
        composeTestRule.onNodeWithText("Editar planta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Eliminar planta").performScrollTo().assertIsDisplayed()
    }

    private fun setScreen(
        state: PlantDetailUiState,
        notesState: PlantNotesUiState = PlantNotesUiState.Empty,
        notesMutationError: UiErrorCategory? = null,
        notesMutating: Boolean = false,
        careState: CareEventsUiState = CareEventsUiState.Empty,
        careMutationError: UiErrorCategory? = null,
        careMutating: Boolean = false,
        onRetryCare: () -> Unit = {},
        onDeleteCare: (String) -> Unit = {},
        onAddCare: (CareType) -> Unit = {},
        onEditCare: (String) -> Unit = {},
        photoMutating: Boolean = false,
        photoMutationError: UiErrorCategory? = null,
        photoCleanupRetryEnabled: Boolean = false,
        mutationError: UiErrorCategory? = null,
        isDeleting: Boolean = false,
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        onBeginEdit: () -> Unit = {},
        onCancelEdit: () -> Unit = {},
        onNameChange: (String) -> Unit = {},
        onScientificNameChange: (String) -> Unit = {},
        onLocationChange: (String) -> Unit = {},
        onAcquiredEpochDayChange: (Long?) -> Unit = {},
        onDescriptionChange: (String) -> Unit = {},
        onFavoriteClick: (Boolean) -> Unit = {},
        onSave: () -> Unit = {},
        onDelete: () -> Unit = {},
        onAddNote: () -> Unit = {},
        onEditNote: (String) -> Unit = {},
        onDeleteNote: (String) -> Unit = {},
        onRetryNotes: () -> Unit = {},
        onAddStagedNote: (String) -> Unit = {},
        onUpdateStagedNote: (String, String) -> Unit = { _, _ -> },
        onRemoveStagedNote: (String) -> Unit = {},
        onTakePhoto: () -> Unit = {},
        onPickPhoto: () -> Unit = {},
        onRemoveStagedPhoto: () -> Unit = {},
        onRemovePhoto: () -> Unit = {},
        onRetryPhotoCleanup: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantDetailScreen(
                    state = state,
                    notesState = notesState,
                    notesMutationError = notesMutationError,
                    notesMutating = notesMutating,
                    photoMutating = photoMutating,
                    photoMutationError = photoMutationError,
                    photoCleanupRetryEnabled = photoCleanupRetryEnabled,
                    mutationError = mutationError,
                    isDeleting = isDeleting,
                    onBack = onBack,
                    onRetry = onRetry,
                    onBeginEdit = onBeginEdit,
                    onCancelEdit = onCancelEdit,
                    onNameChange = onNameChange,
                    onScientificNameChange = onScientificNameChange,
                    onLocationChange = onLocationChange,
                    onAcquiredEpochDayChange = onAcquiredEpochDayChange,
                    onDescriptionChange = onDescriptionChange,
                    onFavoriteClick = onFavoriteClick,
                    onSave = onSave,
                    onDelete = onDelete,
                    onAddNote = onAddNote,
                    onEditNote = onEditNote,
                    onDeleteNote = onDeleteNote,
                    onRetryNotes = onRetryNotes,
                    careState = careState,
                    careMutationError = careMutationError,
                    careMutating = careMutating,
                    onRetryCare = onRetryCare,
                    onDeleteCare = onDeleteCare,
                    onAddCare = onAddCare,
                    onEditCare = onEditCare,
                    onAddStagedNote = onAddStagedNote,
                    onUpdateStagedNote = onUpdateStagedNote,
                    onRemoveStagedNote = onRemoveStagedNote,
                    onTakePhoto = onTakePhoto,
                    onPickPhoto = onPickPhoto,
                    onRemoveStagedPhoto = onRemoveStagedPhoto,
                    onRemovePhoto = onRemovePhoto,
                    onRetryPhotoCleanup = onRetryPhotoCleanup,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun viewState(plant: Plant): PlantDetailUiState.Content =
        PlantDetailUiState.Content(
            mode = PlantDetailMode.View,
            plant = plant,
            form = plant.toForm(),
        )

    private fun string(id: Int): String = composeTestRule.activity.getString(id)
}

private fun detailPlant(
    name: String,
    scientificName: String? = null,
    location: String? = null,
    acquiredEpochDay: Long? = null,
    description: String? = null,
    mainPhotoPath: String? = null,
): Plant = Plant(
    id = "detail-id",
    name = name,
    scientificName = scientificName,
    location = location,
    acquiredEpochDay = acquiredEpochDay,
    description = description,
    mainPhotoPath = mainPhotoPath,
    isFavorite = false,
    createdAt = 1L,
    updatedAt = 1L,
)

private fun Plant.toForm(): PlantForm = PlantForm(
    name = name,
    scientificName = scientificName.orEmpty(),
    location = location.orEmpty(),
    acquiredEpochDay = acquiredEpochDay,
    description = description.orEmpty(),
)

private fun careEvent(
    id: String,
    careType: CareType,
    performedAt: Long,
    note: String? = null,
): CareEvent = CareEvent(
    id = id,
    plantId = "detail-id",
    careType = careType,
    performedAt = performedAt,
    note = note,
    createdAt = performedAt,
    updatedAt = performedAt,
)
