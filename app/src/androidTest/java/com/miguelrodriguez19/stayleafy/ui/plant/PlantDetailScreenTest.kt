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
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
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
            state = PlantDetailUiState.Content(detailPlant("Monstera")),
            onDelete = { deleteClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.delete_plant)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.delete_plant_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.delete_plant_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.cancel)).performClick()
        assertEquals(0, deleteClicks)

        composeTestRule.onNodeWithText(string(R.string.delete_plant)).performScrollTo().performClick()
        composeTestRule
            .onAllNodesWithText(string(R.string.delete_plant))
            .onLast()
            .performClick()

        assertEquals(1, deleteClicks)
    }

    @Test
    fun photoActions_withoutPhoto_offerDirectSources() {
        var takeClicks = 0
        var pickClicks = 0
        setScreen(
            state = PlantDetailUiState.Content(detailPlant("Monstera")),
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
            state = PlantDetailUiState.Content(
                detailPlant("Monstera", mainPhotoPath = "plant_photos/example.jpg"),
            ),
            onTakePhoto = { takeClicks++ },
            onPickPhoto = { pickClicks++ },
            onRemovePhotoRequest = { removeClicks++ },
        )

        composeTestRule.onNodeWithText(string(R.string.replace_photo)).performClick()
        composeTestRule.onNodeWithText(string(R.string.take_photo)).performClick()
        composeTestRule.onNodeWithText(string(R.string.replace_photo)).performClick()
        composeTestRule.onNodeWithText(string(R.string.choose_photo)).performClick()
        composeTestRule.onNodeWithText(string(R.string.remove_photo)).performClick()

        assertEquals(1, takeClicks)
        assertEquals(1, pickClicks)
        assertEquals(1, removeClicks)
    }

    @Test
    fun failedPendingPhotoCleanup_offersRetry() {
        var retryClicks = 0
        setScreen(
            state = PlantDetailUiState.Content(detailPlant("Monstera")),
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
            state = PlantDetailUiState.Content(detailPlant("Monstera")),
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
        notesState: PlantNotesUiState = PlantNotesUiState.Empty,
        onAddNote: () -> Unit = {},
        onEditNote: (String) -> Unit = {},
        onDeleteNote: (String) -> Unit = {},
        onTakePhoto: () -> Unit = {},
        onPickPhoto: () -> Unit = {},
        onRemovePhotoRequest: () -> Unit = {},
        photoMutationError: UiErrorCategory? = null,
        photoCleanupRetryEnabled: Boolean = false,
        onRetryPhotoCleanup: () -> Unit = {},
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
                    notesState = notesState,
                    onAddNote = onAddNote,
                    onEditNote = onEditNote,
                    onDeleteNote = onDeleteNote,
                    onTakePhoto = onTakePhoto,
                    onPickPhoto = onPickPhoto,
                    onRemovePhotoRequest = onRemovePhotoRequest,
                    photoMutationError = photoMutationError,
                    photoCleanupRetryEnabled = photoCleanupRetryEnabled,
                    onRetryPhotoCleanup = onRetryPhotoCleanup,
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
    mainPhotoPath: String? = null,
): Plant = Plant(
    id = "detail-id",
    displayName = displayName,
    commonName = commonName,
    scientificName = scientificName,
    location = location,
    acquiredEpochDay = acquiredEpochDay,
    description = description,
    mainPhotoPath = mainPhotoPath,
    isFavorite = false,
    createdAt = 1L,
    updatedAt = 1L,
)
