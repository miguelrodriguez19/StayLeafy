package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CareEventEditorState(
    val eventId: String?,
    val careType: CareType = CareType.WATERING,
    val performedAt: Long = 0L,
    val note: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val notFound: Boolean = false,
    val loadError: UiErrorCategory? = null,
    val saveError: UiErrorCategory? = null,
    val futureError: Boolean = false,
)

@HiltViewModel(assistedFactory = CareEventEditorViewModel.Factory::class)
class CareEventEditorViewModel @AssistedInject constructor(
    @Assisted("plantId") private val plantId: String,
    @Assisted("careType") private val initialCareType: CareType?,
    @Assisted("eventId") private val eventId: String?,
    private val repository: CareEventRepository,
    private val plantRepository: PlantRepository,
    private val epochMillisProvider: EpochMillisProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(
        CareEventEditorState(
            eventId = eventId,
            careType = initialCareType ?: CareType.WATERING,
            performedAt = epochMillisProvider.now(),
        ),
    )
    val state: StateFlow<CareEventEditorState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun updateCareType(type: CareType) {
        _state.value = _state.value.copy(careType = type, saveError = null)
    }

    fun updatePerformedAt(value: Long) {
        _state.value = _state.value.copy(performedAt = value, futureError = false, saveError = null)
    }

    fun updateDate(epochDay: Long) {
        val current = localDateTime(_state.value.performedAt)
        updatePerformedAt(localDateTime(epochDayToLocalDate(epochDay), current.second))
    }

    fun updateTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        val current = localDateTime(_state.value.performedAt)
        updatePerformedAt(localDateTime(current.first, LocalTime.of(hour, minute)))
    }

    fun updateNote(value: String) {
        _state.value = _state.value.copy(note = value, saveError = null)
    }

    fun retryLoad() {
        load()
    }

    fun save() {
        val current = _state.value
        if (current.isLoading || current.notFound || current.isSaving) return
        if (current.performedAt > epochMillisProvider.now()) {
            _state.value = current.copy(futureError = true, saveError = UiErrorCategory.CareFuture)
            return
        }
        _state.value = current.copy(isSaving = true, saveError = null, futureError = false)
        viewModelScope.launch {
            try {
                val draft = CareEventDraft(
                    careType = current.careType,
                    performedAt = current.performedAt,
                    note = current.note.trim().takeIf { it.isNotEmpty() },
                )
                if (eventId == null) repository.createEvent(plantId, draft)
                else repository.updateEvent(plantId, eventId, draft)
                _state.value = _state.value.copy(isSaving = false, saved = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                val future = _state.value.performedAt > epochMillisProvider.now()
                _state.value = _state.value.copy(
                    isSaving = false,
                    futureError = future,
                    saveError = if (future) UiErrorCategory.CareFuture else UiErrorCategory.CareSave,
                )
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, loadError = null, notFound = false)
        loadJob = viewModelScope.launch {
            try {
                val plant = plantRepository.observePlant(plantId).first()
                if (plant == null) {
                    _state.value = _state.value.copy(isLoading = false, notFound = true)
                    return@launch
                }
                if (eventId == null) {
                    _state.value = _state.value.copy(isLoading = false)
                } else {
                    val event = repository.observeEvent(plantId, eventId).first()
                    _state.value = if (event == null) {
                        _state.value.copy(isLoading = false, notFound = true)
                    } else {
                        _state.value.copy(
                            isLoading = false,
                            careType = event.careType,
                            performedAt = event.performedAt,
                            note = event.note.orEmpty(),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.value = _state.value.copy(isLoading = false, loadError = UiErrorCategory.CareLoad)
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("plantId") plantId: String,
            @Assisted("careType") careType: CareType?,
            @Assisted("eventId") eventId: String?,
        ): CareEventEditorViewModel
    }
}

private fun localDateTime(epochMillis: Long): Pair<LocalDate, LocalTime> {
    val local = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return local.toLocalDate() to local.toLocalTime().withSecond(0).withNano(0)
}

private fun localDateTime(date: LocalDate, time: LocalTime): Long =
    date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun epochDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
