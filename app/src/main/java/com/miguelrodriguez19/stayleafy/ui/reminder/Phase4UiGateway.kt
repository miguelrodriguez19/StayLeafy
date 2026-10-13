package com.miguelrodriguez19.stayleafy.ui.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.miguelrodriguez19.stayleafy.data.NoticeRepository
import com.miguelrodriguez19.stayleafy.data.ReminderRepository
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.CareStatusSummary
import com.miguelrodriguez19.stayleafy.model.CareScheduleDraft
import com.miguelrodriguez19.stayleafy.model.IntervalReminderDraft
import com.miguelrodriguez19.stayleafy.model.IntervalReminderRule
import com.miguelrodriguez19.stayleafy.model.SeasonalCareSummary
import com.miguelrodriguez19.stayleafy.model.SeasonalReminderRule
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface Phase4UiGateway {
    fun observeRules(plantId: String): Flow<List<IntervalReminderRule>>
    fun observeSeasonalRules(plantId: String): Flow<List<SeasonalReminderRule>>
    fun observeCareSummaries(): Flow<List<CareStatusSummary>>
    fun observeSeasonalSummaries(plantId: String): Flow<List<SeasonalCareSummary>>
    suspend fun saveRules(plantId: String, drafts: List<IntervalReminderDraft>)
    suspend fun saveSchedules(plantId: String, drafts: List<CareScheduleDraft>)
    fun observeNotices(): Flow<List<AppNotice>>
    fun observeNotice(noticeId: String): Flow<AppNotice?>
    fun observeUnreadCount(): Flow<Int>
    suspend fun markNoticeRead(noticeId: String)
    suspend fun markAllNoticesRead()
    fun observeNotificationWarning(): Flow<Boolean>
    suspend fun shouldShowPermissionEducation(): Boolean
    suspend fun markPermissionEducationShown()
}

/** Adapts the Room-backed Phase 4 repositories to the state-oriented UI surface. */
@Singleton
class RepositoryPhase4UiGateway @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val noticeRepository: NoticeRepository,
    private val notificationGateway: ReminderNotificationGateway,
    @ApplicationContext private val context: Context,
) : Phase4UiGateway {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun observeRules(plantId: String): Flow<List<IntervalReminderRule>> =
        reminderRepository.observeRules(plantId)

    override fun observeCareSummaries(): Flow<List<CareStatusSummary>> =
        reminderRepository.observePlantSummaries()

    override fun observeSeasonalRules(plantId: String): Flow<List<SeasonalReminderRule>> =
        reminderRepository.observeSeasonalRules(plantId)

    override fun observeSeasonalSummaries(plantId: String): Flow<List<SeasonalCareSummary>> =
        reminderRepository.observeSeasonalSummaries(plantId)

    override suspend fun saveRules(plantId: String, drafts: List<IntervalReminderDraft>) =
        reminderRepository.saveRules(plantId, drafts)

    override suspend fun saveSchedules(plantId: String, drafts: List<CareScheduleDraft>) =
        reminderRepository.saveSchedules(plantId, drafts)

    override fun observeNotices(): Flow<List<AppNotice>> = noticeRepository.observeNotices()

    override fun observeNotice(noticeId: String): Flow<AppNotice?> = noticeRepository.observeNotice(noticeId)

    override fun observeUnreadCount(): Flow<Int> = noticeRepository.observeUnreadCount()

    override suspend fun markNoticeRead(noticeId: String) {
        noticeRepository.markRead(noticeId)
        notificationGateway.cancel(noticeId)
    }

    override suspend fun markAllNoticesRead() {
        val unreadIds = noticeRepository.observeNotices().first().filter { it.readAt == null }.map(AppNotice::id)
        noticeRepository.markAllRead()
        unreadIds.forEach(notificationGateway::cancel)
    }

    override fun observeNotificationWarning(): Flow<Boolean> = reminderRepository.observeHasEnabledRules()
        .map { hasActiveRules -> hasActiveRules && !canPostNotifications() }

    override suspend fun shouldShowPermissionEducation(): Boolean =
        !preferences.getBoolean(PERMISSION_EDUCATION_SHOWN, false) && !canPostNotifications()

    override suspend fun markPermissionEducationShown() {
        preferences.edit().putBoolean(PERMISSION_EDUCATION_SHOWN, true).apply()
    }

    private fun canPostNotifications(): Boolean {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private companion object {
        const val PREFERENCES_NAME = "phase4_ui"
        const val PERMISSION_EDUCATION_SHOWN = "notification_permission_education_shown"
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface Phase4UiGatewayModule {
    @Binds
    fun bindPhase4UiGateway(implementation: RepositoryPhase4UiGateway): Phase4UiGateway
}
