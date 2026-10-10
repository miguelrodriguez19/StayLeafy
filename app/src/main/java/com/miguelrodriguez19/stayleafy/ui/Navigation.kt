package com.miguelrodriguez19.stayleafy.ui

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.miguelrodriguez19.stayleafy.ui.plant.PlantDetailRoute
import com.miguelrodriguez19.stayleafy.ui.plant.PlantEditorRoute
import com.miguelrodriguez19.stayleafy.ui.plant.PlantListRoute

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(PlantList)
    val saveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.safeDrawingPadding(),
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(saveableStateDecorator, viewModelStoreDecorator),
        entryProvider = entryProvider {
            entry<PlantList> {
                PlantListRoute(
                    onAddPlant = { backStack.add(PlantEditor()) },
                    onPlantClick = { plantId -> backStack.add(PlantDetail(plantId)) },
                )
            }
            entry<PlantDetail> { key ->
                PlantDetailRoute(
                    plantId = key.plantId,
                    onBack = { backStack.removeLastOrNull() },
                    onEdit = { backStack.add(PlantEditor(key.plantId)) },
                    onDeleted = { backStack.removeLastOrNull() },
                )
            }
            entry<PlantEditor> { key ->
                PlantEditorRoute(
                    plantId = key.plantId,
                    onBack = { backStack.removeLastOrNull() },
                    onSaved = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
