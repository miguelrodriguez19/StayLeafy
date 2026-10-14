package com.miguelrodriguez19.stayleafy.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun unloadedPreferences_doNotReplacePersistedNightModeDuringRecreation() {
        val theme = mutableStateOf<ThemePreference?>(null)
        var currentMode = AppCompatDelegate.MODE_NIGHT_YES
        val appliedModes = mutableListOf<Int>()
        composeTestRule.setContent {
            ApplyThemePreference(
                theme = theme.value,
                currentMode = { currentMode },
                setMode = { mode ->
                    currentMode = mode
                    appliedModes += mode
                },
            )
        }

        composeTestRule.waitForIdle()
        assertEquals(emptyList<Int>(), appliedModes)

        composeTestRule.runOnIdle { theme.value = ThemePreference.DARK }
        composeTestRule.waitForIdle()
        assertEquals(emptyList<Int>(), appliedModes)

        composeTestRule.runOnIdle { theme.value = ThemePreference.LIGHT }
        composeTestRule.waitForIdle()
        assertEquals(listOf(AppCompatDelegate.MODE_NIGHT_NO), appliedModes)
    }
}
