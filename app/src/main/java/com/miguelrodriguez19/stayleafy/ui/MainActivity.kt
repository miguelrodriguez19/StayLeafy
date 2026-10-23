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
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miguelrodriguez19.stayleafy.data.settings.AppLocaleController
import com.miguelrodriguez19.stayleafy.data.backup.DataOperationCoordinator
import com.miguelrodriguez19.stayleafy.data.backup.DatasetReplacedException
import com.miguelrodriguez19.stayleafy.data.backup.RecoveryRequiredException
import com.miguelrodriguez19.stayleafy.StayLeafy
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.ui.components.LeafyPrimaryButton
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.data.settings.SettingsRepository
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var dataOperations: DataOperationCoordinator

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var localeController: AppLocaleController

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContent {
            val maintenance by dataOperations.state.collectAsStateWithLifecycle()
            BackHandler(enabled = maintenance.isBusy) { /* Finish durable replacement first. */ }
            val versionedPreferences = remember(settingsRepository, dataOperations) {
                dataOperations.state.flatMapLatest { state ->
                    if (state.isBusy || state.error) flowOf<Pair<Long, SettingsPreferences>?>(null)
                    else settingsRepository.preferences.map { state.generation to it }
                }
            }
            val observedPreferences by versionedPreferences.collectAsStateWithLifecycle(initialValue = null)
            val preferences = observedPreferences?.takeIf { it.first == maintenance.generation }?.second
            if (!maintenance.isBusy && !maintenance.error) {
                ApplyThemePreference(preferences?.theme)
                preferences?.let { loadedPreferences ->
                    LaunchedEffect(maintenance.generation, loadedPreferences.language) {
                        try {
                            dataOperations.withOperation {
                                val latest = settingsRepository.preferences.first()
                                val platformLanguage = localeController.current()
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    if (platformLanguage != latest.language) localeController.apply(latest.language)
                                } else if (platformLanguage != latest.language) {
                                    settingsRepository.setLanguage(platformLanguage)
                                }
                            }
                        } catch (_: DatasetReplacedException) {
                            // A new generation will synchronize its own preferences.
                        } catch (_: RecoveryRequiredException) {
                            // Recovery owns the platform locale until it safely completes.
                        }
                    }
                }
            }
            StayLeafyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (maintenance.isBusy || maintenance.error || preferences == null) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                        ) {
                            if (maintenance.error) {
                                Text(stringResource(R.string.data_recovery_error))
                                LeafyPrimaryButton(
                                    text = stringResource(R.string.data_recovery_retry),
                                    onClick = { (application as StayLeafy).retryDataRecovery() },
                                )
                            } else {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.data_maintenance_progress))
                            }
                        }
                    } else {
                        key(maintenance.generation) { MainNavigation() }
                    }
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
