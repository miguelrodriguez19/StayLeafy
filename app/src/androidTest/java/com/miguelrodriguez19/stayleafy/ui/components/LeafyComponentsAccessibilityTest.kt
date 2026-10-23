package com.miguelrodriguez19.stayleafy.ui.components

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LeafyComponentsAccessibilityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun buttonsRemainAtLeast48dpAtLargeEnglishFontScale() {
        setContent(
            locale = Locale.ENGLISH,
            labels = listOf("Save", "Delete", "Remove"),
        )

        assertButtonHeight("Save")
        assertButtonHeight("Delete")
        assertButtonHeight("Remove")
    }

    @Test
    fun buttonsRemainAtLeast48dpAtLargeSpanishFontScale() {
        setContent(
            locale = Locale.forLanguageTag("es"),
            labels = listOf("Guardar", "Eliminar", "Quitar"),
        )

        assertButtonHeight("Guardar")
        assertButtonHeight("Eliminar")
        assertButtonHeight("Quitar")
    }

    private fun setContent(locale: Locale, labels: List<String>) {
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(locale)
            fontScale = 2f
        }
        val localizedContext = composeTestRule.activity.createConfigurationContext(configuration)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                StayLeafyTheme(dynamicColor = false) {
                    Column(Modifier.fillMaxWidth()) {
                        LeafyPrimaryButton(
                            text = labels[0],
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LeafyDestructiveButton(
                            text = labels[1],
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LeafyDestructiveOutlinedButton(
                            text = labels[2],
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun assertButtonHeight(label: String) {
        composeTestRule
            .onNodeWithText(label)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }
}
