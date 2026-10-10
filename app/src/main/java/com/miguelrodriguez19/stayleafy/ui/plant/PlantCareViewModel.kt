package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = PlantCareViewModel.Factory::class)
class PlantCareViewModel @AssistedInject constructor(
    @Assisted private val plantId: String,
    private val repository: CareEventRepository,
    private val epochMillisProvider: EpochMillisProvider,
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val _mutationError = MutableStateFlow<UiErrorCategory?>(null)
    val mutationError: StateFlow<UiErrorCategory?> = _mutationError.asStateFlow()
    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    val uiState: StateFlow<CareEventsUiState> = reload.flatMapLatest {
        repository.observeEvents(plantId)
            .map<List<CareEvent>, CareEventsUiState> { events ->
                if (events.isEmpty()) CareEventsUiState.Empty else CareEventsUiState.Content(events)
            }
            .onStart { emit(CareEventsUiState.Loading) }
            .catch { emit(CareEventsUiState.Error()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareEventsUiState.Loading)

    fun retry() {
        reload.value += 1
    }

    fun record(type: CareType, epochDay: Long? = null) {
        if (_isMutating.value) return
        _isMutating.value = true
        _mutationError.value = null
        viewModelScope.launch {
            runCatching {
                val now = epochMillisProvider.now()
                repository.createEvent(
                    plantId,
                    CareEventDraft(
                        careType = type,
                        performedAt = epochDay?.toTimestampUsingTimeFrom(now) ?: now,
                        note = null,
                    ),
                )
            }.onFailure { _mutationError.value = UiErrorCategory.CareSave }
            _isMutating.value = false
        }
    }

    fun clearHistory(type: CareType) {
        if (_isMutating.value) return
        _isMutating.value = true
        _mutationError.value = null
        viewModelScope.launch {
            runCatching { repository.deleteEventsByType(plantId, type) }
                .onFailure { _mutationError.value = UiErrorCategory.CareDelete }
            _isMutating.value = false
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(plantId: String): PlantCareViewModel
    }
}

private fun Long.toTimestampUsingTimeFrom(now: Long): Long {
    val zone = ZoneId.systemDefault()
    val currentTime = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
    return LocalDate.ofEpochDay(this)
        .atTime(currentTime)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
