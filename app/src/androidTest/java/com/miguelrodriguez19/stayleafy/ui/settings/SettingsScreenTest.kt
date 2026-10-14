package com.miguelrodriguez19.stayleafy.ui.settings

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.data.settings.AppLanguage
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsScreen_displaysPreferencesPermissionsAndAbout() {
        setScreen()

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Language").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily check time").assertIsDisplayed()
        scrollToText("Notifications")
        scrollToText("Camera")
    }

    @Test
    fun themeAndLanguageChoices_forwardCallbacks() {
        var selectedTheme: ThemePreference? = null
        var selectedLanguage: AppLanguage? = null
        setScreen(
            onThemeSelected = { selectedTheme = it },
            onLanguageSelected = { selectedLanguage = it },
        )

        composeTestRule.onNodeWithTag("settings_theme_dark").performClick()
        composeTestRule.onNodeWithTag("settings_language_spanish").performClick()

        assertEquals(ThemePreference.DARK, selectedTheme)
        assertEquals(AppLanguage.SPANISH, selectedLanguage)
    }

    @Test
    fun notificationAction_forwardsRequestedHostAction() {
        var action: NotificationPermissionAction? = null
        setScreen(
            state = SettingsUiState(
                isLoading = false,
                notificationPermission = NotificationPermissionSummary(
                    NotificationPermissionStatus.NEEDS_PERMISSION,
                    NotificationPermissionAction.REQUEST_PERMISSION,
                ),
            ),
            onNotificationPermissionAction = { action = it },
        )

        scrollToText("Allow notifications")
        composeTestRule.onNodeWithText("Allow notifications").performClick()

        assertEquals(NotificationPermissionAction.REQUEST_PERMISSION, action)
    }

    @Test
    fun backCallbackAndReminderControl_areExposed() {
        var backCount = 0
        var selectedTime: LocalTime? = null
        setScreen(
            onNavigateBack = { backCount++ },
            onReminderTimeSelected = { selectedTime = it },
        )

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backCount)

        // The Android time picker is platform UI; this test verifies the field is exposed to users.
        composeTestRule.onNodeWithText("Daily check time").assertIsDisplayed()
        assertEquals(null, selectedTime)
    }

    @Test
    fun aboutSection_displaysVersionAndOfflinePrivacySummary() {
        setScreen()

        scrollToText("Version test-build")
        scrollToText("Stay Leafy works fully offline. No account, cloud sync or analytics.")
    }

    @Test
    fun screenUsesSpanishResources() {
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
                    SettingsScreen(
                        state = readyState(),
                        onNavigateBack = {},
                        onThemeSelected = {},
                        onLanguageSelected = {},
                        onReminderTimeSelected = {},
                        onNotificationPermissionAction = {},
                        onRetry = {},
                        onDismissError = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Ajustes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Aspecto").assertIsDisplayed()
        scrollToText("Inglés")
        scrollToText("Cámara")
        composeTestRule
            .onAllNodesWithText("No requiere permisos adicionales")
            .onFirst()
            .assertIsDisplayed()
    }

    private fun setScreen(
        state: SettingsUiState = readyState(),
        onNavigateBack: () -> Unit = {},
        onThemeSelected: (ThemePreference) -> Unit = {},
        onLanguageSelected: (AppLanguage) -> Unit = {},
        onReminderTimeSelected: (LocalTime) -> Unit = {},
        onNotificationPermissionAction: (NotificationPermissionAction) -> Unit = {},
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
                    SettingsScreen(
                        state = state,
                        onNavigateBack = onNavigateBack,
                        onThemeSelected = onThemeSelected,
                        onLanguageSelected = onLanguageSelected,
                        onReminderTimeSelected = onReminderTimeSelected,
                        onNotificationPermissionAction = onNotificationPermissionAction,
                        onRetry = {},
                        onDismissError = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun scrollToText(text: String) {
        composeTestRule
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasText(text, substring = false))
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }
}

private fun readyState() = SettingsUiState(
    isLoading = false,
    preferences = SettingsPreferences(),
    notificationPermission = NotificationPermissionSummary(
        NotificationPermissionStatus.ENABLED,
        NotificationPermissionAction.NONE,
    ),
    appVersionName = "test-build",
)
