package com.miguelrodriguez19.stayleafy.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.AnnualDate
import com.miguelrodriguez19.stayleafy.model.CareEvent
import com.miguelrodriguez19.stayleafy.model.CareScheduleDraft
import com.miguelrodriguez19.stayleafy.model.CareScheduleMode
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.IntervalReminderDraft
import com.miguelrodriguez19.stayleafy.model.IntervalReminderConfiguration
import com.miguelrodriguez19.stayleafy.model.IntervalReminderRule
import com.miguelrodriguez19.stayleafy.model.ReminderDueSource
import com.miguelrodriguez19.stayleafy.model.SeasonalReminderConfiguration
import com.miguelrodriguez19.stayleafy.model.SeasonalReminderRule
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoticeListViewModel @Inject constructor(
    private val gateway: Phase4UiGateway,
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val warningReload = MutableStateFlow(0)
    private val unreadOnly = MutableStateFlow(true)

    private val noticesState = reload.flatMapLatest {
        gateway.observeNotices()
            .map<List<AppNotice>, NoticeSourceState> { notices ->
                NoticeSourceState.Content(notices)
            }
            .onStart { emit(NoticeSourceState.Loading) }
            .catch { emit(NoticeSourceState.Error) }
    }

    val uiState: StateFlow<NoticeInboxUiState> = combine(noticesState, unreadOnly) { source, showUnreadOnly ->
        when (source) {
            NoticeSourceState.Loading -> NoticeInboxUiState(
                unreadOnly = showUnreadOnly,
                listState = NoticeListUiState.Loading,
            )
            NoticeSourceState.Error -> NoticeInboxUiState(
                unreadOnly = showUnreadOnly,
                listState = NoticeListUiState.Error,
            )
            is NoticeSourceState.Content -> {
                val sorted = source.notices
                    .sortedWith(compareByDescending<AppNotice> { it.createdAt }.thenByDescending { it.id })
                val visible = if (showUnreadOnly) sorted.filter { it.readAt == null } else sorted
                NoticeInboxUiState(
                    unreadOnly = showUnreadOnly,
                    listState = if (visible.isEmpty()) {
                        NoticeListUiState.Empty
                    } else {
                        NoticeListUiState.Content(visible)
                    },
                    unreadCount = source.notices.count { it.readAt == null },
                )
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        NoticeInboxUiState(),
    )

    val notificationWarning: StateFlow<Boolean> = warningReload.flatMapLatest {
        gateway.observeNotificationWarning().catch { emit(false) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun retry() { reload.value += 1 }
    fun setUnreadOnly(unreadOnly: Boolean) { this.unreadOnly.value = unreadOnly }
    fun refreshNotificationWarning() { warningReload.value += 1 }
    fun markAllRead() { viewModelScope.launch { runCatching { gateway.markAllNoticesRead() } } }
}

private sealed interface NoticeSourceState {
    data object Loading : NoticeSourceState
    data object Error : NoticeSourceState
    data class Content(val notices: List<AppNotice>) : NoticeSourceState
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
