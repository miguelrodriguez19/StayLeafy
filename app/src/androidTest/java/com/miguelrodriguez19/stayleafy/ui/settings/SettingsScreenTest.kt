package com.miguelrodriguez19.stayleafy.ui.settings

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.data.settings.AppLanguage
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs

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
    fun segmentedOptionsKeep48dpSemanticsAtLargeEnglishFontScale() {
        setScreen(fontScale = 2f)

        assertSegmentOption("settings_theme_system", "System")
        assertSegmentOption("settings_theme_light", "Light")
        assertSegmentOption("settings_theme_dark", "Dark")
    }

    @Test
    fun segmentedOptionsKeep48dpSemanticsAtLargeSpanishFontScale() {
        setScreen(locale = Locale.forLanguageTag("es"), fontScale = 2f)

        assertSegmentOption("settings_theme_system", "Sistema")
        assertSegmentOption("settings_theme_light", "Claro")
        assertSegmentOption("settings_theme_dark", "Oscuro")
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
    fun unknownNotificationStatus_showsStatusWithoutAction() {
        setScreen(
            state = SettingsUiState(
                isLoading = false,
                notificationPermission = NotificationPermissionSummary(
                    NotificationPermissionStatus.UNKNOWN,
                    NotificationPermissionAction.NONE,
                ),
            ),
        )

        scrollToText("Notifications")
        composeTestRule
            .onNodeWithTag("settings_notifications_status")
            .assertIsDisplayed()
            .onChildren()
            .onFirst()
            .assertTextEquals("Status not checked yet")
        composeTestRule.onNodeWithTag("settings_notifications_action").assertDoesNotExist()
    }

    @Test
    fun blockedNotification_stacksAt360dpAndAccessibleAction() {
        var action: NotificationPermissionAction? = null
        setScreen(
            state = blockedState(),
            screenWidthDp = 360,
            onNotificationPermissionAction = { action = it },
        )

        scrollToNotificationAction()
        assertNotificationLayout(stacked = true)
        composeTestRule
            .onNodeWithTag("settings_notifications_action")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(NotificationPermissionAction.OPEN_SYSTEM_SETTINGS, action)
    }

    @Test
    fun blockedNotification_stacksAt320dpAndKeepsActionAccessible() {
        var action: NotificationPermissionAction? = null
        setScreen(
            state = blockedState(),
            screenWidthDp = 320,
            onNotificationPermissionAction = { action = it },
        )

        scrollToNotificationAction()
        assertNotificationLayout(stacked = true)
        composeTestRule
            .onNodeWithTag("settings_notifications_action")
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(NotificationPermissionAction.OPEN_SYSTEM_SETTINGS, action)
    }

    @Test
    fun blockedNotification_isReadableInEnglishAndSpanishThemesAndFontScales() {
        val cases = listOf(
            320 to 1f,
            320 to 1.5f,
            320 to 2f,
            360 to 1f,
            360 to 1.5f,
            360 to 2f,
        ).flatMap { (screenWidthDp, fontScale) ->
            listOf(
                SettingsLayoutCase(screenWidthDp, fontScale, Locale.ENGLISH, false),
                SettingsLayoutCase(screenWidthDp, fontScale, Locale.ENGLISH, true),
                SettingsLayoutCase(screenWidthDp, fontScale, Locale.forLanguageTag("es"), false),
                SettingsLayoutCase(screenWidthDp, fontScale, Locale.forLanguageTag("es"), true),
            )
        }
        lateinit var updateCase: (SettingsLayoutCase) -> Unit
        val baseConfiguration = Configuration(composeTestRule.activity.resources.configuration)
        val initialCase = cases.first()
        composeTestRule.setContent {
            val currentCase = remember { mutableStateOf(initialCase) }
            updateCase = { currentCase.value = it }
            val configuration = Configuration(baseConfiguration).apply {
                setLocale(currentCase.value.locale)
                fontScale = currentCase.value.fontScale
            }
            val localizedContext = composeTestRule.activity.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(
                    density = composeTestRule.activity.resources.displayMetrics.density,
                    fontScale = currentCase.value.fontScale,
                ),
            ) {
                StayLeafyTheme(darkTheme = currentCase.value.darkTheme, dynamicColor = false) {
                    Box(
                        Modifier
                            .width(currentCase.value.screenWidthDp.dp)
                            .fillMaxHeight(),
                    ) {
                        SettingsScreen(
                            state = blockedState(),
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
        }
        composeTestRule.waitForIdle()

        cases.forEach { testCase ->
            composeTestRule.runOnIdle { updateCase(testCase) }
            composeTestRule.waitForIdle()
            scrollToNotificationAction()
            assertNotificationLayout(stacked = true)
            assertIconAlignedWithTitle("settings_notifications_icon", "settings_notifications_title")
            composeTestRule
                .onNodeWithTag("settings_notifications_status")
                .assertIsDisplayed()
            composeTestRule
                .onNodeWithTag("settings_notifications_action")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)

            val statusText = if (testCase.locale.language == "es") {
                "Bloqueadas en los ajustes del sistema"
            } else {
                "Blocked in system settings"
            }
            val actionText = if (testCase.locale.language == "es") {
                "Ajustes de notificaciones"
            } else {
                "Notification settings"
            }
            val caseDescription = "width=${testCase.screenWidthDp}dp, fontScale=${testCase.fontScale}, " +
                "locale=${testCase.locale.language}, darkTheme=${testCase.darkTheme}"
            assertTextHasNoVisualOverflow(
                if (testCase.locale.language == "es") "Notificaciones" else "Notifications",
                caseDescription,
            )
            assertTextHasNoVisualOverflow(statusText, caseDescription)
            assertTextHasNoVisualOverflow(actionText, caseDescription)
        }
    }

    @Test
    fun blockedNotification_staysBesideInformationWhenChipFitsTwoLines() {
        setScreen(state = blockedState(), screenWidthDp = 411)

        scrollToNotificationAction()
        assertNotificationLayout(stacked = false)
        assertLabelLineCount("settings_notifications_status", "Blocked in system settings", 2)
        assertIconAlignedWithTitle("settings_notifications_icon", "settings_notifications_title")
    }

    @Test
    fun enabledNotification_keepsOneWordChipAndTwoLineActionTogether() {
        setScreen(
            state = readyState().copy(
                notificationPermission = NotificationPermissionSummary(
                    NotificationPermissionStatus.ENABLED,
                    NotificationPermissionAction.OPEN_SYSTEM_SETTINGS,
                ),
            ),
            screenWidthDp = 360,
        )

        scrollToNotificationAction()
        assertNotificationLayout(stacked = false)
        assertLabelLineCount("settings_notifications_status", "Allowed", 1)
        assertLabelLineCount("settings_notifications_action", "Notification settings", 2)
    }

    @Test
    fun enabledNotification_keepsSpanishTwoLineActionBesideInformation() {
        setScreen(
            state = readyState().copy(
                notificationPermission = NotificationPermissionSummary(
                    NotificationPermissionStatus.ENABLED,
                    NotificationPermissionAction.OPEN_SYSTEM_SETTINGS,
                ),
            ),
            locale = Locale.forLanguageTag("es"),
            screenWidthDp = 360,
        )

        scrollToNotificationAction()
        assertNotificationLayout(stacked = false)
        assertLabelLineCount("settings_notifications_status", "Permitidas", 1)
        assertLabelLineCount("settings_notifications_action", "Ajustes de notificaciones", 2)
    }

    @Test
    fun permissionRequest_keepsTwoLineChipAndActionTogether() {
        setScreen(
            state = readyState().copy(
                notificationPermission = NotificationPermissionSummary(
                    NotificationPermissionStatus.NEEDS_PERMISSION,
                    NotificationPermissionAction.REQUEST_PERMISSION,
                ),
            ),
            screenWidthDp = 360,
        )

        scrollToNotificationAction()
        assertNotificationLayout(stacked = false)
        assertLabelLineCount("settings_notifications_status", "Permission needed", 2)
        assertLabelLineCount("settings_notifications_action", "Allow notifications", 2)
    }

    @Test
    fun enabledNotification_usesHorizontalLayoutWhenLabelsFit() {
        setScreen(screenWidthDp = 411)

        scrollToText("Notifications")
        assertNotificationLayout(stacked = false)
    }

    @Test
    fun permissionIcons_alignWithTheirTitles() {
        setScreen(state = blockedState(), screenWidthDp = 360)

        scrollToText("Notifications")
        assertIconAlignedWithTitle("settings_notifications_icon", "settings_notifications_title")
        scrollToText("Camera")
        assertIconAlignedWithTitle("settings_camera_icon", "settings_camera_title")
        scrollToText("Photos")
        assertIconAlignedWithTitle("settings_photos_icon", "settings_photos_title")
    }

    @Test
    fun blockedNotification_recomposesFromAllowedAndRetainsActionCallback() {
        var action: NotificationPermissionAction? = null
        lateinit var updateState: (SettingsUiState) -> Unit
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
            fontScale = 1f
        }
        val localizedContext = composeTestRule.activity.createConfigurationContext(configuration)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(
                    density = composeTestRule.activity.resources.displayMetrics.density,
                    fontScale = 1f,
                ),
            ) {
                StayLeafyTheme(dynamicColor = false) {
                    val state = remember { mutableStateOf(readyState()) }
                    updateState = { state.value = it }
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        SettingsScreen(
                            state = state.value,
                            onNavigateBack = {},
                            onThemeSelected = {},
                            onLanguageSelected = {},
                            onReminderTimeSelected = {},
                            onNotificationPermissionAction = { action = it },
                            onRetry = {},
                            onDismissError = {},
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        scrollToText("Notifications")
        composeTestRule.onNodeWithTag("settings_notifications_action").assertDoesNotExist()
        assertNotificationLayout(stacked = false)

        composeTestRule.runOnIdle { updateState(blockedState()) }
        composeTestRule.waitForIdle()
        scrollToNotificationAction()
        assertNotificationLayout(stacked = true)
        composeTestRule.onNodeWithTag("settings_notifications_action").performClick()
        assertEquals(NotificationPermissionAction.OPEN_SYSTEM_SETTINGS, action)
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
        locale: Locale = Locale.ENGLISH,
        fontScale: Float = 1f,
        darkTheme: Boolean = false,
        screenWidthDp: Int? = null,
    ) {
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(locale)
            this.fontScale = fontScale
        }
        val localizedContext = composeTestRule.activity.createConfigurationContext(configuration)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(
                    density = composeTestRule.activity.resources.displayMetrics.density,
                    fontScale = fontScale,
                ),
            ) {
                StayLeafyTheme(darkTheme = darkTheme, dynamicColor = false) {
                    val content: @Composable () -> Unit = {
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
                    if (screenWidthDp == null) {
                        content()
                    } else {
                        Box(Modifier.width(screenWidthDp.dp).fillMaxHeight()) {
                            content()
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun assertSegmentOption(tag: String, label: String) {
        composeTestRule
            .onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertTextEquals(label)
    }

    private fun scrollToText(text: String) {
        composeTestRule
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasText(text, substring = false))
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun scrollToNotificationAction() {
        composeTestRule
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_notifications_action"))
        composeTestRule.onNodeWithTag("settings_notifications_action").assertIsDisplayed()
    }

    private fun assertNotificationLayout(stacked: Boolean) {
        val row = composeTestRule
            .onNodeWithTag("settings_notifications_row")
            .fetchSemanticsNode()
            .boundsInRoot
        val information = composeTestRule
            .onNodeWithTag("settings_notifications_information")
            .fetchSemanticsNode()
            .boundsInRoot
        val trailing = composeTestRule
            .onNodeWithTag("settings_notifications_trailing")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue("trailing content should stay within the row", trailing.right <= row.right)
        if (stacked) {
            assertTrue("trailing content should be below the information area", trailing.top >= information.bottom)
        } else {
            assertTrue("trailing content should be beside the information area", trailing.left >= information.right)
        }
    }

    private fun assertLabelLineCount(tag: String, text: String, expectedLines: Int) {
        val node = composeTestRule.onNode(
            hasText(text) and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(
            "missing measured layout for '$text'",
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results) == true,
        )
        assertEquals(expectedLines, results.single().lineCount)
    }

    private fun assertIconAlignedWithTitle(iconTag: String, titleTag: String) {
        val icon = composeTestRule.onNodeWithTag(iconTag).fetchSemanticsNode().boundsInRoot
        val title = composeTestRule.onNodeWithTag(titleTag).fetchSemanticsNode().boundsInRoot
        val iconCenter = (icon.top + icon.bottom) / 2f
        val titleCenter = (title.top + title.bottom) / 2f
        assertTrue(
            "icon should align with title center: icon=$icon title=$title",
            abs(iconCenter - titleCenter) <= 1f,
        )
    }

    private fun assertTextHasNoVisualOverflow(text: String, caseDescription: String) {
        val nodes = composeTestRule
            .onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("expected text node for '$text' ($caseDescription)", nodes.isNotEmpty())
        nodes.forEach { node ->
            val nodeBounds = node.boundsInRoot
            val results = mutableListOf<TextLayoutResult>()
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)
            assertTrue(
                "missing text layout result for '$text' ($caseDescription)",
                action?.action?.invoke(results) == true,
            )
            assertTrue(
                "missing measured text layout for '$text' ($caseDescription)",
                results.isNotEmpty(),
            )
            results.forEach { result ->
                // Centered text can have line coordinates offset inside a wider paragraph
                // constraint; compare the painted span with the actual semantics node bounds.
                // hasVisualOverflow compares the measured size with paragraph width, which can
                // include the available constraint even when the painted glyphs fit.
                val lineBounds = (0 until result.lineCount).map { lineIndex ->
                    TextLineBounds(
                        left = result.getLineLeft(lineIndex),
                        right = result.getLineRight(lineIndex),
                        top = result.getLineTop(lineIndex),
                        bottom = result.getLineBottom(lineIndex),
                        ellipsized = result.isLineEllipsized(lineIndex),
                    )
                }
                val paintedTextOverflow = lineBounds.any { line ->
                    line.right - line.left > nodeBounds.width
                } || lineBounds.maxOf { it.bottom } - lineBounds.minOf { it.top } > nodeBounds.height
                val ellipsized = lineBounds.any { it.ellipsized }
                assertTrue(
                    "text should not visually overflow: '$text' ($caseDescription); " +
                        "didOverflowWidth=${result.didOverflowWidth}, " +
                        "didOverflowHeight=${result.didOverflowHeight}, " +
                        "paintedTextOverflow=$paintedTextOverflow, ellipsized=$ellipsized, " +
                        "size=${result.size}, nodeBounds=$nodeBounds, lineCount=${result.lineCount}, " +
                        "lineBounds=$lineBounds, " +
                        "constraints=${result.layoutInput.constraints}",
                    !paintedTextOverflow && !result.didOverflowHeight && !ellipsized,
                )
            }
        }
    }
}

private data class TextLineBounds(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val ellipsized: Boolean,
)

private data class SettingsLayoutCase(
    val screenWidthDp: Int,
    val fontScale: Float,
    val locale: Locale,
    val darkTheme: Boolean,
)

private fun readyState() = SettingsUiState(
    isLoading = false,
    preferences = SettingsPreferences(),
    notificationPermission = NotificationPermissionSummary(
        NotificationPermissionStatus.ENABLED,
        NotificationPermissionAction.NONE,
    ),
    appVersionName = "test-build",
)

private fun blockedState() = SettingsUiState(
    isLoading = false,
    preferences = SettingsPreferences(),
    notificationPermission = NotificationPermissionSummary(
        NotificationPermissionStatus.BLOCKED,
        NotificationPermissionAction.OPEN_SYSTEM_SETTINGS,
    ),
    appVersionName = "test-build",
)
