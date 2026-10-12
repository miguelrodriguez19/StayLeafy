package com.miguelrodriguez19.stayleafy.platform.reminder

import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

enum class ReminderRunResult {
    SUCCESS,
    RETRY,
}

class ReminderWorkProcessor @Inject constructor(
    private val deliveryStore: ReminderDeliveryStore,
    private val notificationGateway: ReminderNotificationGateway,
    private val clock: ReminderClock,
) {
    suspend fun run(): ReminderRunResult {
        val now = clock.instant()
        val todayEpochDay = LocalDate.ofInstant(now, clock.zoneId()).toEpochDay()
        val pending = try {
            deliveryStore.preparePendingDeliveries(todayEpochDay)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return ReminderRunResult.RETRY
        }

        var shouldRetry = false
        pending.forEach { delivery ->
            val delivered = try {
                when (notificationGateway.publish(delivery)) {
                    ReminderNotificationResult.POSTED ->
                        deliveryStore.markDelivered(delivery.noticeId, now.toEpochMilli())

                    ReminderNotificationResult.BLOCKED -> {
                        // Suppression is terminal for this occurrence. Granting permission later
                        // must not surface an old system notification retroactively.
                        deliveryStore.markSuppressed(delivery.noticeId, now.toEpochMilli())
                    }
                }
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
            if (!delivered) shouldRetry = true
        }

        return if (shouldRetry) ReminderRunResult.RETRY else ReminderRunResult.SUCCESS
    }
}
