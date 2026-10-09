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

package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.ui.theme.StayLeafyTheme

@Composable
fun PlantScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlantViewModel = hiltViewModel(),
) {
    val items by viewModel.uiState.collectAsStateWithLifecycle()
    if (items is PlantUiState.Success) {
        PlantScreen(
            items = (items as PlantUiState.Success).data,
            onSave = viewModel::addPlant,
            modifier = modifier,
        )
    }
}

@Composable
internal fun PlantScreen(
    items: List<String>,
    onSave: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        var namePlant by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextField(
                modifier = Modifier.weight(1f),
                value = namePlant,
                onValueChange = { namePlant = it },
                label = { Text(stringResource(R.string.plant_name)) },
            )

            Button(
                modifier = Modifier.width(96.dp),
                onClick = { onSave(namePlant) }
            ) {
                Text(stringResource(R.string.save))
            }
        }
        items.forEach {
            Text(stringResource(R.string.saved_item, it))
        }
    }
}

// Previews

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    StayLeafyTheme {
        PlantScreen(listOf("Compose", "Room", "Kotlin"), onSave = {})
    }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
private fun PortraitPreview() {
    StayLeafyTheme {
        PlantScreen(listOf("Compose", "Room", "Kotlin"), onSave = {})
    }
}
