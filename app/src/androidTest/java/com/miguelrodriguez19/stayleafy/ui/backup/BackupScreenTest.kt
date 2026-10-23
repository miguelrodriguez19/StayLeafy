package com.miguelrodriguez19.stayleafy.ui.backup

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.backup.BackupCounts
import com.miguelrodriguez19.stayleafy.data.backup.PreparedRestore
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class BackupScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun screenShowsOfflinePrivacyAndRestorePreview() {
        setScreen(
            state = BackupUiState(preparedRestore = preview()),
        )

        composeTestRule.onNodeWithText("Backups are unencrypted ZIP files. Store them somewhere private. Stay Leafy does not upload backups or request storage permission.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Plants: 2").performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Restoring replaces all local plants, photos, notes, care history, reminder rules, inbox notices and preferences. Existing data will not be merged.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun restoreRequiresExplicitConfirmation() {
        var confirmed = 0
        setScreen(
            state = BackupUiState(preparedRestore = preview()),
            onConfirmRestore = { confirmed++ },
        )

        composeTestRule.onNodeWithText("Restore this backup").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Replace all local data?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Replace and restore").performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun spanishAndLargeFontRemainReadable() {
        val base = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("es"))
            fontScale = 2f
        }
        val localizedContext = composeTestRule.activity.createConfigurationContext(base)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides base,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
            ) {
                StayLeafyTheme(dynamicColor = false) {
                    BackupScreen(
                        state = BackupUiState(),
                        onNavigateBack = {},
                        onExportClick = {},
                        onRestoreClick = {},
                        onConfirmRestore = {},
                        onDiscardRestore = {},
                        onCancelOperation = {},
                        onDismissError = {},
                        onDismissExportMessage = {},
                    )
                }
            }
        }

        composeTestRule.onAllNodesWithText("Copia de seguridad").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Exportar copia").onFirst().performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Atrás").assertIsDisplayed()
    }

    private fun setScreen(
        state: BackupUiState,
        onConfirmRestore: () -> Unit = {},
    ) {
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val localizedContext = composeTestRule.activity.createConfigurationContext(configuration)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
            ) {
                StayLeafyTheme(dynamicColor = false) {
                    BackupScreen(
                        state = state,
                        onNavigateBack = {},
                        onExportClick = {},
                        onRestoreClick = {},
                        onConfirmRestore = onConfirmRestore,
                        onDiscardRestore = {},
                        onCancelOperation = {},
                        onDismissError = {},
                        onDismissExportMessage = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun preview() = PreparedRestore(
        token = "test-token",
        exportedAtEpochMillis = 1_700_000_000_000,
        sourceVersionName = "0.11.0",
        counts = BackupCounts(2, 3, 4, 5, 6, 1),
    )
}
