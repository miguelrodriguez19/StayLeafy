package com.miguelrodriguez19.stayleafy.platform.reminder

import com.miguelrodriguez19.stayleafy.model.CareType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderWorkProcessorTest {

    private val now = Instant.parse("2026-10-11T08:00:00Z")
    private val zone = ZoneId.of("Europe/Madrid")
    private val delivery = PendingReminderDelivery(
        noticeId = "notice-1",
        occurrenceKey = "rule-1:20737",
        plantId = "plant-1",
        plantName = "Ficus",
        careType = CareType.WATERING,
        dueEpochDay = LocalDate.of(2026, 10, 11).toEpochDay(),
    )

    @Test
    fun permissionGranted_publishesAndMarksDelivery() = runTest {
        val store = FakeDeliveryStore(delivery)
        val gateway = FakeNotificationGateway(canPost = true)

        val result = processor(store, gateway).run()

        assertEquals(ReminderRunResult.SUCCESS, result)
        assertEquals(listOf(delivery), gateway.published)
        assertEquals(listOf("notice-1" to now.toEpochMilli()), store.delivered)
        assertTrue(store.suppressed.isEmpty())
        assertEquals(LocalDate.of(2026, 10, 11).toEpochDay(), store.requestedEpochDay)
    }

    @Test
    fun permissionDenied_suppressesOccurrenceWithoutPublishingRetroactively() = runTest {
        val store = FakeDeliveryStore(delivery)
        val gateway = FakeNotificationGateway(canPost = false)
        val processor = processor(store, gateway)

        assertEquals(ReminderRunResult.SUCCESS, processor.run())
        gateway.canPost = true
        assertEquals(ReminderRunResult.SUCCESS, processor.run())

        assertTrue(gateway.published.isEmpty())
        assertEquals(listOf("notice-1" to now.toEpochMilli()), store.suppressed)
    }

    @Test
    fun failedPublish_leavesPendingAndRequestsRetry() = runTest {
        val store = FakeDeliveryStore(delivery)
        val gateway = FakeNotificationGateway(canPost = true, publishFailures = 1)
        val processor = processor(store, gateway)

        assertEquals(ReminderRunResult.RETRY, processor.run())
        assertTrue(store.delivered.isEmpty())
        assertEquals(ReminderRunResult.SUCCESS, processor.run())

        assertEquals(listOf(delivery), gateway.published)
        assertEquals(listOf("notice-1" to now.toEpochMilli()), store.delivered)
    }

    @Test
    fun stateWriteFailure_republishesSameStableNoticeOnRetry() = runTest {
        val store = FakeDeliveryStore(delivery, deliveredFailures = 1)
        val gateway = FakeNotificationGateway(canPost = true)
        val processor = processor(store, gateway)

        assertEquals(ReminderRunResult.RETRY, processor.run())
        assertEquals(ReminderRunResult.SUCCESS, processor.run())

        assertEquals(listOf(delivery, delivery), gateway.published)
        assertEquals(
            reminderNotificationTag(delivery.noticeId),
            reminderNotificationTag(gateway.published.first().noticeId),
        )
        assertEquals(listOf("notice-1" to now.toEpochMilli()), store.delivered)
    }

    private fun processor(
        store: ReminderDeliveryStore,
        gateway: ReminderNotificationGateway,
    ) = ReminderWorkProcessor(store, gateway, FixedReminderClock(now, zone))
}

private class FakeDeliveryStore(
    delivery: PendingReminderDelivery,
    private var deliveredFailures: Int = 0,
) : ReminderDeliveryStore {
    private val pending = mutableListOf(delivery)
    var requestedEpochDay: Long? = null
    val delivered = mutableListOf<Pair<String, Long>>()
    val suppressed = mutableListOf<Pair<String, Long>>()

    override suspend fun preparePendingDeliveries(todayEpochDay: Long): List<PendingReminderDelivery> {
        requestedEpochDay = todayEpochDay
        return pending.toList()
    }

    override suspend fun markDelivered(noticeId: String, deliveredAtEpochMillis: Long) {
        if (deliveredFailures > 0) {
            deliveredFailures -= 1
            error("database unavailable")
        }
        delivered += noticeId to deliveredAtEpochMillis
        pending.removeAll { it.noticeId == noticeId }
    }

    override suspend fun markSuppressed(noticeId: String, suppressedAtEpochMillis: Long) {
        suppressed += noticeId to suppressedAtEpochMillis
        pending.removeAll { it.noticeId == noticeId }
    }
}

private class FakeNotificationGateway(
    var canPost: Boolean = true,
    private var publishFailures: Int = 0,
) : ReminderNotificationGateway {
    val published = mutableListOf<PendingReminderDelivery>()

    override fun ensureChannel() = Unit

    override fun canPostNotifications(): Boolean = canPost

    override fun publish(delivery: PendingReminderDelivery): ReminderNotificationResult {
        if (!canPost) return ReminderNotificationResult.BLOCKED
        if (publishFailures > 0) {
            publishFailures -= 1
            error("notification service unavailable")
        }
        published += delivery
        return ReminderNotificationResult.POSTED
    }

    override fun cancel(noticeId: String) = Unit
}

private class FixedReminderClock(
    private val now: Instant,
    private val zone: ZoneId,
) : ReminderClock {
    override fun instant(): Instant = now

    override fun zoneId(): ZoneId = zone
}
