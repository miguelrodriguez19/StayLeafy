package com.miguelrodriguez19.stayleafy.ui.reminder.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.ui.reminder.CareReminderUiState
import com.miguelrodriguez19.stayleafy.ui.reminder.UiGateway
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = PlantCareStatusViewModel.Factory::class)
class PlantCareStatusViewModel @AssistedInject constructor(
    @Assisted private val plantId: String,
    private val gateway: UiGateway,
) : ViewModel() {
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<CareReminderUiState> = reload.flatMapLatest {
        combine(
            gateway.observeCareSummaries(),
            gateway.observeSeasonalSummaries(plantId),
        ) { summaries, seasonal ->
            CareReminderUiState.Content(
                summaries = summaries.filter { it.plantId == plantId },
                seasonalSummaries = seasonal,
            ) as CareReminderUiState
        }
            .onStart { emit(CareReminderUiState.Loading) }
            .catch { emit(CareReminderUiState.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareReminderUiState.Loading)

    fun retry() { reload.value += 1 }

    @AssistedFactory
    interface Factory {
        fun create(plantId: String): PlantCareStatusViewModel
    }
}
