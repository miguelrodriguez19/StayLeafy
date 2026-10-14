/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miguelrodriguez19.stayleafy.ui

import android.os.Bundle
import android.content.Intent
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.data.settings.AppLocaleController
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.data.settings.SettingsRepository
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var localeController: AppLocaleController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContent {
            val nullablePreferences = remember(settingsRepository) {
                settingsRepository.preferences.map<SettingsPreferences, SettingsPreferences?> { it }
            }
            val preferences by nullablePreferences.collectAsStateWithLifecycle(initialValue = null)
            ApplyThemePreference(preferences?.theme)
            preferences?.let { loadedPreferences ->
                LaunchedEffect(loadedPreferences.language) {
                    val platformLanguage = localeController.current()
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        if (platformLanguage != loadedPreferences.language) {
                            localeController.apply(loadedPreferences.language)
                        }
                    } else if (platformLanguage != loadedPreferences.language) {
                        settingsRepository.setLanguage(platformLanguage)
                    }
                }
            }
            StayLeafyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
        AndroidNoticeNavigation.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AndroidNoticeNavigation.handleIntent(intent)
    }
}

@Composable
internal fun ApplyThemePreference(
    theme: ThemePreference?,
    currentMode: () -> Int = { AppCompatDelegate.getDefaultNightMode() },
    setMode: (Int) -> Unit = { AppCompatDelegate.setDefaultNightMode(it) },
) {
    if (theme == null) return
    LaunchedEffect(theme) {
        val mode = when (theme) {
            ThemePreference.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemePreference.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemePreference.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (currentMode() != mode) setMode(mode)
    }
}
