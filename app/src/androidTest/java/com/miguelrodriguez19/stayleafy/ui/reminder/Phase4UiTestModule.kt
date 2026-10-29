package com.miguelrodriguez19.stayleafy.ui.reminder

import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.CareScheduleDraft
import com.miguelrodriguez19.stayleafy.model.IntervalReminderDraft
import com.miguelrodriguez19.stayleafy.model.IntervalReminderRule
import com.miguelrodriguez19.stayleafy.model.SeasonalCareSummary
import com.miguelrodriguez19.stayleafy.model.SeasonalReminderRule
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [Phase4UiGatewayModule::class],
)
interface Phase4UiTestModule {
    @Binds
    fun bindPhase4UiGateway(gateway: FakePhase4UiGateway): Phase4UiGateway
}

@Singleton
class FakePhase4UiGateway @Inject constructor() : Phase4UiGateway {
    private val rules = MutableStateFlow<List<IntervalReminderRule>>(emptyList())
    private val summaries = MutableStateFlow<List<CareStatusSummary>>(emptyList())
    private val notices = MutableStateFlow<List<AppNotice>>(emptyList())

    override fun observeRules(plantId: String): Flow<List<IntervalReminderRule>> =
        rules.map { values -> values.filter { it.plantId == plantId } }

    override fun observeSeasonalRules(plantId: String): Flow<List<SeasonalReminderRule>> =
        MutableStateFlow(emptyList())

    override fun observeCareSummaries(): Flow<List<CareStatusSummary>> = summaries
    override fun observeSeasonalSummaries(plantId: String): Flow<List<SeasonalCareSummary>> =
        MutableStateFlow(emptyList())
    override suspend fun saveRules(plantId: String, drafts: List<IntervalReminderDraft>) = Unit
    override suspend fun saveSchedules(plantId: String, drafts: List<CareScheduleDraft>) = Unit
    override fun observeNotices(): Flow<List<AppNotice>> = notices
    override fun observeNotice(noticeId: String): Flow<AppNotice?> =
        notices.map { values -> values.firstOrNull { it.id == noticeId } }
    override fun observeUnreadCount(): Flow<Int> = notices.map { values -> values.count { it.readAt == null } }
    override suspend fun markNoticeRead(noticeId: String) {
        notices.update { values ->
            values.map { notice ->
                if (notice.id == noticeId && notice.readAt == null) notice.copy(readAt = 1L) else notice
            }
        }
    }
    override suspend fun markAllNoticesRead() {
        notices.update { values -> values.map { it.copy(readAt = it.readAt ?: 1L) } }
    }
    override fun observeNotificationWarning(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun shouldShowPermissionEducation(): Boolean = false
    override suspend fun markPermissionEducationShown() = Unit

    fun reset() {
        rules.value = emptyList()
        summaries.value = emptyList()
        notices.value = emptyList()
    }

    fun replaceNotices(values: List<AppNotice>) { notices.value = values }
}
