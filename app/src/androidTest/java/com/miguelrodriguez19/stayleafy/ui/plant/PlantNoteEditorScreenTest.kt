package com.miguelrodriguez19.stayleafy.ui.plant

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlantNoteEditorScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun createForm_displaysKeyTextAndForwardsCallbacks() {
        var body = ""
        var backClicks = 0
        var saveClicks = 0
        var state by mutableStateOf(PlantNoteEditorState(noteId = null, isLoading = false))
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantNoteEditorScreen(
                    state = state,
                    onBack = { backClicks++ },
                    onBodyChange = {
                        body = it
                        state = state.copy(body = it)
                    },
                    onRetry = {},
                    onSave = { saveClicks++ },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.add_note)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.note_body)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.note_character_count, 0, MAX_NOTE_BODY_LENGTH)).assertIsDisplayed()

        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Watered today")
        composeTestRule.onAllNodesWithText(string(R.string.save)).onFirst().performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.back)).performClick()
        composeTestRule.onNodeWithText(string(R.string.discard_changes_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.discard)).performClick()

        assertEquals("Watered today", body)
        assertEquals(1, saveClicks)
        assertEquals(1, backClicks)
    }

    @Test
    fun unsavedBack_showsConfirmationBeforeLeaving() {
        var backClicks = 0
        var state by mutableStateOf(PlantNoteEditorState(noteId = null, isLoading = false))
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantNoteEditorScreen(
                    state = state,
                    onBack = { backClicks++ },
                    onBodyChange = { state = state.copy(body = it) },
                    onRetry = {},
                    onSave = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        state = state.copy(body = "Draft note")
        composeTestRule.onNodeWithContentDescription(string(R.string.back)).performClick()

        composeTestRule.onNodeWithText(string(R.string.discard_changes_title)).assertIsDisplayed()
        assertEquals(0, backClicks)
        composeTestRule.onNodeWithText(string(R.string.keep_editing)).performClick()
        assertEquals(0, backClicks)
        composeTestRule.onNodeWithContentDescription(string(R.string.back)).performClick()
        composeTestRule.onNodeWithText(string(R.string.discard)).performClick()
        assertEquals(1, backClicks)
    }

    @Test
    fun editForm_displaysExistingBodyAndUsesEditTitle() {
        setScreen(
            state = PlantNoteEditorState(
                noteId = "note-1",
                body = "Moved closer to the window",
                isLoading = false,
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.edit_note)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Moved closer to the window").assertIsDisplayed()
    }

    @Test
    fun validationError_displaysLocalizedRequiredText() {
        setScreen(
            state = PlantNoteEditorState(
                noteId = null,
                isLoading = false,
                bodyError = true,
            ),
        )

        composeTestRule.onNodeWithText(string(R.string.note_required)).assertIsDisplayed()
    }

    @Test
    fun form_usesSpanishResources() {
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
                    PlantNoteEditorScreen(
                        state = PlantNoteEditorState(noteId = null, isLoading = false),
                        onBack = {},
                        onBodyChange = {},
                        onRetry = {},
                        onSave = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Añadir nota").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nota").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Guardar").onFirst().assertIsDisplayed()
    }

    private fun setScreen(
        state: PlantNoteEditorState,
        onBack: () -> Unit = {},
        onBodyChange: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            StayLeafyTheme(dynamicColor = false) {
                PlantNoteEditorScreen(
                    state = state,
                    onBack = onBack,
                    onBodyChange = onBodyChange,
                    onRetry = onRetry,
                    onSave = onSave,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int, vararg arguments: Any): String =
        composeTestRule.activity.getString(id, *arguments)
}
