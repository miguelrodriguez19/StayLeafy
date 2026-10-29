package com.miguelrodriguez19.stayleafy.ui.reminder.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.model.AnnualDate
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareScheduleDraft
import com.miguelrodriguez19.stayleafy.model.CareScheduleMode
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.IntervalReminderConfiguration
import com.miguelrodriguez19.stayleafy.model.IntervalReminderRule
import com.miguelrodriguez19.stayleafy.model.ReminderDueSource
import com.miguelrodriguez19.stayleafy.model.SeasonalReminderConfiguration
import com.miguelrodriguez19.stayleafy.model.SeasonalReminderRule
import com.miguelrodriguez19.stayleafy.ui.reminder.CareConfigurationUiState
import com.miguelrodriguez19.stayleafy.ui.reminder.CareRuleForm
import com.miguelrodriguez19.stayleafy.ui.reminder.UiGateway
import com.miguelrodriguez19.stayleafy.ui.reminder.ReminderDueSourceChoice
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = CareConfigurationViewModel.Factory::class)
class CareConfigurationViewModel @AssistedInject constructor(
    @Assisted private val plantId: String,
    private val plantRepository: PlantRepository,
    private val careEventRepository: CareEventRepository,
    private val gateway: UiGateway,
    private val zoneIdProvider: ZoneIdProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CareConfigurationUiState>(CareConfigurationUiState.Loading)
    val uiState: StateFlow<CareConfigurationUiState> = _uiState.asStateFlow()
    private var observation: Job? = null
    private var initialized = false

    init { observe() }

    fun retry() {
        initialized = false
        observe()
    }

    fun setEnabled(type: CareType, enabled: Boolean) = updateForm(type) {
        val latestCare = (_uiState.value as? CareConfigurationUiState.Content)
            ?.latestCareEpochDays
            ?.get(type)
        copy(
            enabled = enabled,
            mode = if (enabled) CareScheduleMode.INTERVAL else CareScheduleMode.DISABLED,
            dueSource = if (enabled && dueSource == null && latestCare != null) {
                ReminderDueSourceChoice.LatestCare
            } else {
                dueSource
            },
            intervalError = false,
            sourceError = false,
            seasonStartError = false,
        )
    }

    fun setMode(type: CareType, mode: CareScheduleMode) = updateForm(type) {
        val latestCare = (_uiState.value as? CareConfigurationUiState.Content)
            ?.latestCareEpochDays
            ?.get(type)
        copy(
            enabled = mode != CareScheduleMode.DISABLED,
            mode = mode,
            dueSource = if (mode == CareScheduleMode.INTERVAL && dueSource == null && latestCare != null) {
                ReminderDueSourceChoice.LatestCare
            } else {
                dueSource
            },
            intervalError = false,
            sourceError = false,
            seasonStartError = false,
        )
    }

    fun setInterval(type: CareType, value: String) = updateForm(type) {
        copy(intervalDays = value.filter(Char::isDigit), intervalError = false)
    }

    fun setSource(type: CareType, source: ReminderDueSourceChoice) = updateForm(type) {
        copy(dueSource = source, sourceError = false)
    }

    fun setDate(type: CareType, epochDay: Long) = updateForm(type) {
        copy(selectedEpochDay = epochDay, dueSource = ReminderDueSourceChoice.CustomDate, sourceError = false)
    }

    fun setSeasonStart(type: CareType, date: AnnualDate) = updateForm(type) {
        copy(seasonStart = date, seasonStartError = false)
    }

    fun setSeasonEnd(type: CareType, date: AnnualDate?) = updateForm(type) {
        copy(seasonEnd = date)
    }

    fun save() {
        val state = _uiState.value as? CareConfigurationUiState.Content ?: return
        if (state.isSaving) return
        val validated = state.forms.map { form ->
            val interval = form.intervalDays.toIntOrNull()
            val invalidInterval = form.mode == CareScheduleMode.INTERVAL && (interval == null || interval <= 0)
            val invalidSource = form.mode == CareScheduleMode.INTERVAL && when (form.dueSource) {
                ReminderDueSourceChoice.LatestCare -> state.latestCareEpochDays[form.careType] == null
                ReminderDueSourceChoice.CustomDate -> form.selectedEpochDay == null
                null -> true
            }
            form.copy(
                intervalError = invalidInterval,
                sourceError = invalidSource,
                seasonStartError = form.mode == CareScheduleMode.SEASONAL && form.seasonStart == null,
            )
        }
        if (validated.any { it.intervalError || it.sourceError || it.seasonStartError }) {
            _uiState.value = state.copy(forms = validated, saveError = false)
            return
        }

        _uiState.value = state.copy(forms = validated, isSaving = true, saveError = false)
        viewModelScope.launch {
            try {
                val drafts = validated.map { form ->
                    val interval = form.intervalDays.toIntOrNull()
                    val source = when (form.dueSource) {
                        ReminderDueSourceChoice.LatestCare -> {
                            if (state.latestCareEpochDays[form.careType] != null) ReminderDueSource.LatestCare else null
                        }
                        ReminderDueSourceChoice.CustomDate -> form.selectedEpochDay?.let(ReminderDueSource::Date)
                        null -> null
                    }
                    CareScheduleDraft(
                        careType = form.careType,
                        mode = form.mode,
                        interval = interval?.takeIf { source != null && it > 0 }?.let { days ->
                            IntervalReminderConfiguration(days, requireNotNull(source))
                        },
                        seasonal = form.seasonStart?.let { start ->
                            SeasonalReminderConfiguration(start, form.seasonEnd)
                        },
                    )
                }
                gateway.saveSchedules(plantId, drafts)
                val active = drafts.any { it.mode != CareScheduleMode.DISABLED }
                val explain = active && gateway.shouldShowPermissionEducation()
                val current = _uiState.value as? CareConfigurationUiState.Content ?: return@launch
                _uiState.value = current.copy(
                    isSaving = false,
                    showPermissionEducation = explain,
                    saved = !explain,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                val current = _uiState.value as? CareConfigurationUiState.Content ?: return@launch
                _uiState.value = current.copy(isSaving = false, saveError = true)
            }
        }
    }

    fun completePermissionEducation() {
        val state = _uiState.value as? CareConfigurationUiState.Content ?: return
        viewModelScope.launch {
            gateway.markPermissionEducationShown()
            _uiState.value = state.copy(showPermissionEducation = false, saved = true)
        }
    }

    private fun observe() {
        observation?.cancel()
        _uiState.value = CareConfigurationUiState.Loading
        observation = viewModelScope.launch {
            combine(
                plantRepository.observePlant(plantId),
                gateway.observeRules(plantId),
                gateway.observeSeasonalRules(plantId),
                careEventRepository.observeEvents(plantId),
            ) { plant, rules, seasonalRules, events ->
                ConfigurationObservation(plant, rules, seasonalRules, events)
            }
                .catch { _uiState.value = CareConfigurationUiState.Error }
                .collect { (plant, rules, seasonalRules, events) ->
                    if (plant == null) {
                        _uiState.value = CareConfigurationUiState.NotFound
                        return@collect
                    }
                    val latest = events.latestEpochDays(zoneIdProvider.currentZoneId())
                    val current = _uiState.value as? CareConfigurationUiState.Content
                    val forms = if (initialized && current != null) current.forms else {
                        initialized = true
                        CareType.values().map { type ->
                            rules.toForm(type, latest[type], seasonalRules)
                        }
                    }
                    _uiState.value = CareConfigurationUiState.Content(
                        plantName = plant.name,
                        forms = forms,
                        latestCareEpochDays = latest,
                        initialForms = current?.initialForms ?: forms,
                        plantScientificName = plant.scientificName,
                        plantPhotoPath = plant.mainPhotoPath,
                        isSaving = current?.isSaving == true,
                        saveError = current?.saveError == true,
                        showPermissionEducation = current?.showPermissionEducation == true,
                        saved = current?.saved == true,
                    )
                }
        }
    }

    private fun updateForm(type: CareType, transform: CareRuleForm.() -> CareRuleForm) {
        val state = _uiState.value as? CareConfigurationUiState.Content ?: return
        if (state.isSaving) return
        _uiState.value = state.copy(
            forms = state.forms.map { if (it.careType == type) it.transform() else it },
            saveError = false,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(plantId: String): CareConfigurationViewModel
    }
}

private data class ConfigurationObservation(
    val plant: com.miguelrodriguez19.stayleafy.model.Plant?,
    val intervalRules: List<IntervalReminderRule>,
    val seasonalRules: List<SeasonalReminderRule>,
    val events: List<CareEvent>,
)

private fun List<CareEvent>.latestEpochDays(zoneId: java.time.ZoneId): Map<CareType, Long> =
    groupBy(CareEvent::careType).mapValues { (_, values) ->
        val latest = values.maxBy(CareEvent::performedAt)
        Instant.ofEpochMilli(latest.performedAt).atZone(zoneId).toLocalDate().toEpochDay()
    }

private fun List<IntervalReminderRule>.toForm(
    type: CareType,
    latestCareEpochDay: Long?,
    seasonalRules: List<SeasonalReminderRule>,
): CareRuleForm {
    val rule = firstOrNull { it.careType == type }
    val seasonal = seasonalRules.firstOrNull { it.careType == type }
    val mode = when {
        seasonal?.enabled == true -> CareScheduleMode.SEASONAL
        rule?.enabled == true -> CareScheduleMode.INTERVAL
        else -> CareScheduleMode.DISABLED
    }
    return CareRuleForm(
        careType = type,
        enabled = mode != CareScheduleMode.DISABLED,
        intervalDays = rule?.intervalDays?.toString().orEmpty(),
        dueSource = when {
            rule != null -> ReminderDueSourceChoice.CustomDate
            latestCareEpochDay != null -> ReminderDueSourceChoice.LatestCare
            else -> null
        },
        selectedEpochDay = rule?.nextDueEpochDay,
        mode = mode,
        seasonStart = seasonal?.start,
        seasonEnd = seasonal?.end,
    )
}
