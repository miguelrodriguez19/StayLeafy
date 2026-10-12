package com.miguelrodriguez19.stayleafy.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.IntervalReminderDraft
import com.miguelrodriguez19.stayleafy.model.IntervalReminderRule
import com.miguelrodriguez19.stayleafy.model.ReminderDueSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlantCareSummaryViewModel @Inject constructor(
    gateway: Phase4UiGateway,
) : ViewModel() {
    val summaries: StateFlow<List<CareStatusSummary>> = gateway.observeCareSummaries()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = PlantCareStatusViewModel.Factory::class)
class PlantCareStatusViewModel @AssistedInject constructor(
    @Assisted private val plantId: String,
    private val gateway: Phase4UiGateway,
) : ViewModel() {
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<CareReminderUiState> = reload.flatMapLatest {
        gateway.observeCareSummaries()
            .map<List<CareStatusSummary>, CareReminderUiState> { summaries ->
                CareReminderUiState.Content(summaries.filter { it.plantId == plantId })
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

@HiltViewModel(assistedFactory = CareConfigurationViewModel.Factory::class)
class CareConfigurationViewModel @AssistedInject constructor(
    @Assisted private val plantId: String,
    private val plantRepository: PlantRepository,
    private val careEventRepository: CareEventRepository,
    private val gateway: Phase4UiGateway,
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
        copy(enabled = enabled, intervalError = false, sourceError = false)
    }

    fun setInterval(type: CareType, value: String) = updateForm(type) {
        copy(intervalDays = value.filter(Char::isDigit), intervalError = false)
    }

    fun setSource(type: CareType, source: ReminderDueSourceChoice) = updateForm(type) {
        copy(dueSource = source, sourceError = false)
    }

    fun setDate(type: CareType, epochDay: Long) = updateForm(type) {
        copy(selectedEpochDay = epochDay, dueSource = ReminderDueSourceChoice.Date, sourceError = false)
    }

    fun save() {
        val state = _uiState.value as? CareConfigurationUiState.Content ?: return
        if (state.isSaving) return
        val validated = state.forms.map { form ->
            val interval = form.intervalDays.toIntOrNull()
            val invalidInterval = form.enabled && (interval == null || interval <= 0)
            val invalidSource = form.enabled && when (form.dueSource) {
                ReminderDueSourceChoice.LatestCare -> state.latestCareEpochDays[form.careType] == null
                ReminderDueSourceChoice.Date -> form.selectedEpochDay == null
                null -> true
            }
            form.copy(intervalError = invalidInterval, sourceError = invalidSource)
        }
        if (validated.any { it.intervalError || it.sourceError }) {
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
                        ReminderDueSourceChoice.Date -> form.selectedEpochDay?.let(ReminderDueSource::Date)
                        null -> null
                    }
                    IntervalReminderDraft(
                        careType = form.careType,
                        enabled = form.enabled,
                        intervalDays = interval.takeIf { source != null },
                        dueSource = source,
                    )
                }
                gateway.saveRules(plantId, drafts)
                val active = drafts.any(IntervalReminderDraft::enabled)
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
                careEventRepository.observeEvents(plantId),
            ) { plant, rules, events -> Triple(plant, rules, events) }
                .catch { _uiState.value = CareConfigurationUiState.Error }
                .collect { (plant, rules, events) ->
                    if (plant == null) {
                        _uiState.value = CareConfigurationUiState.NotFound
                        return@collect
                    }
                    val latest = events.latestEpochDays(zoneIdProvider.currentZoneId())
                    val current = _uiState.value as? CareConfigurationUiState.Content
                    val forms = if (initialized && current != null) current.forms else {
                        initialized = true
                        CareType.values().map { type -> rules.toForm(type, latest[type]) }
                    }
                    _uiState.value = CareConfigurationUiState.Content(
                        plantName = plant.name,
                        forms = forms,
                        latestCareEpochDays = latest,
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoticeListViewModel @Inject constructor(
    private val gateway: Phase4UiGateway,
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val warningReload = MutableStateFlow(0)

    val uiState: StateFlow<NoticeListUiState> = reload.flatMapLatest {
        gateway.observeNotices()
            .map<List<AppNotice>, NoticeListUiState> { notices ->
                val sorted = notices.sortedWith(compareByDescending<AppNotice> { it.createdAt }.thenByDescending { it.id })
                if (sorted.isEmpty()) NoticeListUiState.Empty else NoticeListUiState.Content(sorted)
            }
            .onStart { emit(NoticeListUiState.Loading) }
            .catch { emit(NoticeListUiState.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoticeListUiState.Loading)

    val notificationWarning: StateFlow<Boolean> = warningReload.flatMapLatest {
        gateway.observeNotificationWarning().catch { emit(false) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun retry() { reload.value += 1 }
    fun refreshNotificationWarning() { warningReload.value += 1 }
    fun markAllRead() { viewModelScope.launch { runCatching { gateway.markAllNoticesRead() } } }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = NoticeDetailViewModel.Factory::class)
class NoticeDetailViewModel @AssistedInject constructor(
    @Assisted private val noticeId: String,
    private val gateway: Phase4UiGateway,
) : ViewModel() {
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<NoticeDetailUiState> = reload.flatMapLatest {
        gateway.observeNotice(noticeId)
            .map<AppNotice?, NoticeDetailUiState> { notice ->
                if (notice == null) NoticeDetailUiState.NotFound else NoticeDetailUiState.Content(notice)
            }
            .onStart { emit(NoticeDetailUiState.Loading) }
            .catch { emit(NoticeDetailUiState.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoticeDetailUiState.Loading)

    init { viewModelScope.launch { runCatching { gateway.markNoticeRead(noticeId) } } }

    fun retry() { reload.value += 1 }

    @AssistedFactory
    interface Factory {
        fun create(noticeId: String): NoticeDetailViewModel
    }
}

@HiltViewModel
class RootNavigationViewModel @Inject constructor(gateway: Phase4UiGateway) : ViewModel() {
    val unreadCount: StateFlow<Int> = gateway.observeUnreadCount()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

private fun List<IntervalReminderRule>.toForm(type: CareType, latestCareEpochDay: Long?): CareRuleForm {
    val rule = firstOrNull { it.careType == type }
    return CareRuleForm(
        careType = type,
        enabled = rule?.enabled == true,
        intervalDays = rule?.intervalDays?.toString().orEmpty(),
        dueSource = when {
            rule != null -> ReminderDueSourceChoice.Date
            else -> null
        },
        selectedEpochDay = rule?.nextDueEpochDay,
    )
}

private fun List<CareEvent>.latestEpochDays(zoneId: java.time.ZoneId): Map<CareType, Long> =
    groupBy(CareEvent::careType).mapValues { (_, values) ->
        val latest = values.maxBy(CareEvent::performedAt)
        Instant.ofEpochMilli(latest.performedAt).atZone(zoneId).toLocalDate().toEpochDay()
    }
