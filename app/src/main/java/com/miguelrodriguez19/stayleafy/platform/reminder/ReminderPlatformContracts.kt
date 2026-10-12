package com.miguelrodriguez19.stayleafy.platform.reminder

import com.miguelrodriguez19.stayleafy.model.CareType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A semantic notification request. The Room-backed notice remains the source of truth; this model
 * only contains the information Android needs to render and route one occurrence.
 */
data class PendingReminderDelivery(
    val noticeId: String,
    val occurrenceKey: String,
    val plantId: String,
    val plantName: String,
    val careType: CareType,
    val dueEpochDay: Long,
)

/**
 * Port implemented by the Phase 4 NoticeRepository adapter.
 *
 * [preparePendingDeliveries] must atomically create or return one PENDING notice per occurrence.
 * DELIVERED and SUPPRESSED notices must not be returned again. Keeping those transitions in the
 * repository makes process restarts and repeated WorkManager runs safe.
 */
interface ReminderDeliveryStore {
    suspend fun preparePendingDeliveries(todayEpochDay: Long): List<PendingReminderDelivery>

    suspend fun markDelivered(noticeId: String, deliveredAtEpochMillis: Long)

    suspend fun markSuppressed(noticeId: String, suppressedAtEpochMillis: Long)
}

interface ReminderNotificationGateway {
    fun ensureChannel()

    fun canPostNotifications(): Boolean

    /** Replaces an earlier notification for the same notice rather than creating a duplicate. */
    fun publish(delivery: PendingReminderDelivery): ReminderNotificationResult

    fun cancel(noticeId: String)
}

enum class ReminderNotificationResult {
    POSTED,
    BLOCKED,
}

interface ReminderScheduler {
    fun ensureDailyEvaluationScheduled()
}

interface ReminderClock {
    fun instant(): Instant

    fun zoneId(): ZoneId
}

@Singleton
class SystemReminderClock @Inject constructor() : ReminderClock {
    override fun instant(): Instant = Clock.systemUTC().instant()

    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}

internal val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

internal fun delayUntilNextReminderWindow(
    now: Instant,
    zoneId: ZoneId,
    targetTime: LocalTime = DEFAULT_REMINDER_TIME,
): Duration {
    val localNow = now.atZone(zoneId)
    var nextRun = localNow.toLocalDate().atTime(targetTime).atZone(zoneId)
    if (!nextRun.toInstant().isAfter(now)) {
        nextRun = localNow.toLocalDate().plusDays(1).atTime(targetTime).atZone(zoneId)
    }
    return Duration.between(now, nextRun.toInstant())
}

internal const val REMINDER_NOTIFICATION_CHANNEL_ID = "care_reminders"
internal const val REMINDER_NOTIFICATION_ID = 1_004
internal const val REMINDER_NOTIFICATION_TAG_PREFIX = "stay_leafy_reminder:"
internal const val EXTRA_NOTICE_ID = "com.miguelrodriguez19.stayleafy.extra.NOTICE_ID"
internal const val EXTRA_PLANT_ID = "com.miguelrodriguez19.stayleafy.extra.PLANT_ID"
internal const val EXTRA_OPEN_NOTICES = "com.miguelrodriguez19.stayleafy.extra.OPEN_NOTICES"

internal fun reminderNotificationTag(noticeId: String): String =
    "$REMINDER_NOTIFICATION_TAG_PREFIX$noticeId"
