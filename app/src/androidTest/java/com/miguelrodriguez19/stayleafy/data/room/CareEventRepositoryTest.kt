package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.DefaultCareEventRepository
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.data.backup.DataOperationCoordinator
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.model.BulkCareResult
import com.miguelrodriguez19.stayleafy.model.CareEventCreateResult
import com.miguelrodriguez19.stayleafy.model.CareEventDraft
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.platform.reminder.PendingReminderDelivery
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationGateway
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareEventRepositoryTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun bulkRecording_validatesEveryPlantBeforeInsertingAnything() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Ficus"))
        val today = LocalDate.of(2025, 4, 1).toEpochDay()
        val repository = repository(
            now = Instant.parse("2025-04-01T10:00:00Z").toEpochMilli(),
            zoneId = ZoneId.of("UTC"),
        )

        val failure = runCatching {
            repository.recordBulkCare(listOf("plant-1", "missing"), CareType.WATERING, today)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(database.careEventDao().observeEvents("plant-1").first().isEmpty())
    }

    @Test
    fun roomTransaction_rollsBackEventsWhenLaterInsertFails() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Ficus"))
        database.plantDao().insertPlant(testPlant("plant-2", "Monstera"))
        val day = LocalDate.of(2025, 4, 1).toEpochDay()
        val zone = ZoneId.of("UTC")
        val start = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.ofEpochDay(day).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val eventTime = Instant.parse("2025-04-01T10:00:00Z").toEpochMilli()

        val failure = runCatching {
            database.careEventDao().recordEventsIfNotRecorded(
                events = listOf(
                    careEvent("same-id", "plant-1", eventTime),
                    careEvent("same-id", "plant-2", eventTime),
                ),
                dayStartInclusive = start,
                dayEndExclusive = end,
                performedEpochDay = day,
            )
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertTrue(database.careEventDao().observeEvents("plant-1").first().isEmpty())
        assertTrue(database.careEventDao().observeEvents("plant-2").first().isEmpty())
    }

    @Test
    fun bulkRecording_reportsMixedAndAllDuplicates() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Ficus"))
        database.plantDao().insertPlant(testPlant("plant-2", "Monstera"))
        val now = Instant.parse("2025-04-01T10:00:00Z").toEpochMilli()
        val day = LocalDate.of(2025, 4, 1).toEpochDay()
        database.careEventDao().insertEvent(careEvent("existing", "plant-1", now - 60_000L))
        val repository = repository(now, ZoneId.of("UTC"))

        val mixed = repository.recordBulkCare(
            plantIds = listOf("plant-1", "plant-2", "plant-2"),
            careType = CareType.WATERING,
            performedEpochDay = day,
        )
        val allDuplicate = repository.recordBulkCare(
            plantIds = listOf("plant-1", "plant-2"),
            careType = CareType.WATERING,
            performedEpochDay = day,
        )

        assertEquals(BulkCareResult(listOf("plant-2"), listOf("plant-1")), mixed)
        assertEquals(BulkCareResult(emptyList(), listOf("plant-1", "plant-2")), allDuplicate)
        assertEquals(1, database.careEventDao().observeEvents("plant-1").first().size)
        assertEquals(1, database.careEventDao().observeEvents("plant-2").first().size)
    }

    @Test
    fun bulkRecording_usesSelectedLocalTimeAndDstDayBoundaries() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Ficus"))
        database.plantDao().insertPlant(testPlant("plant-2", "Monstera"))
        val zone = ZoneId.of("Europe/Madrid")
        val now = java.time.ZonedDateTime.of(2024, 4, 1, 2, 30, 0, 0, zone).toInstant().toEpochMilli()
        val selectedDate = LocalDate.of(2024, 3, 31)
        val day = selectedDate.toEpochDay()
        val start = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        database.careEventDao().insertEvent(careEvent("same-day", "plant-1", start + 60_000L))
        database.careEventDao().insertEvent(careEvent("next-day", "plant-2", end))
        val repository = repository(now, zone)

        val result = repository.recordBulkCare(
            plantIds = listOf("plant-1", "plant-2"),
            careType = CareType.WATERING,
            performedEpochDay = day,
        )
        val newlyRecorded = database.careEventDao().observeEvents("plant-2").first()
            .single { it.id != "next-day" }

        assertEquals(BulkCareResult(listOf("plant-2"), listOf("plant-1")), result)
        assertEquals(
            java.time.ZonedDateTime.of(2024, 3, 31, 3, 30, 0, 0, zone).toInstant().toEpochMilli(),
            newlyRecorded.performedAt,
        )
        assertEquals(23 * 60 * 60 * 1_000L, end - start)
    }

    @Test
    fun concurrentWritesForSamePlantAndDay_onlyOneEventIsInserted() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Ficus"))
        val day = LocalDate.of(2025, 4, 1).toEpochDay()
        val zone = ZoneId.of("UTC")
        val start = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.ofEpochDay(day).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val eventTime = Instant.parse("2025-04-01T10:00:00Z").toEpochMilli()
        val dao = database.careEventDao()

        val results = coroutineScope {
            listOf("event-a", "event-b").map { id ->
                async(Dispatchers.IO) {
                    dao.recordEventsIfNotRecorded(
                        events = listOf(careEvent(id, "plant-1", eventTime)),
                        dayStartInclusive = start,
                        dayEndExclusive = end,
                        performedEpochDay = day,
                    )
                }
            }.map { it.await() }
        }

        assertEquals(1, results.sumOf { it.recordedPlantIds.size })
        assertEquals(1, results.sumOf { it.skippedPlantIds.size })
        assertEquals(1, dao.observeEvents("plant-1").first().size)
    }

    @Test
    fun reminderResolutionAndNotificationCancellation_applyOnlyToInsertedEvents() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Ficus"))
        database.plantDao().insertPlant(testPlant("plant-2", "Monstera"))
        val now = Instant.parse("2025-04-01T10:00:00Z").toEpochMilli()
        val day = LocalDate.of(2025, 4, 1).toEpochDay()
        val firstRule = intervalRule("rule-1", "plant-1", day - 1)
        val skippedRule = intervalRule("rule-2", "plant-2", day - 1)
        database.reminderRuleDao().upsertRule(firstRule)
        database.reminderRuleDao().upsertRule(skippedRule)
        database.noticeDao().insertNotice(notice("notice-1", firstRule, day - 1))
        database.noticeDao().insertNotice(notice("notice-2", skippedRule, day - 1))
        database.careEventDao().insertEvent(careEvent("existing", "plant-2", now - 60_000L))
        val notifications = RecordingNotificationGateway(failOnCancel = true)
        val repository = repository(now, ZoneId.of("UTC"), notificationGateway = notifications)

        val result = repository.recordBulkCare(
            plantIds = listOf("plant-1", "plant-2"),
            careType = CareType.WATERING,
            performedEpochDay = day,
        )

        assertEquals(BulkCareResult(listOf("plant-1"), listOf("plant-2")), result)
        assertEquals(day + 7, database.reminderRuleDao().getRule(firstRule.id)?.nextDueEpochDay)
        assertEquals(day - 1, database.reminderRuleDao().getRule(skippedRule.id)?.nextDueEpochDay)
        assertEquals(now, database.noticeDao().observeNotice("notice-1").first()?.readAt)
        assertEquals(null, database.noticeDao().observeNotice("notice-2").first()?.readAt)
        assertEquals(listOf("notice-1"), notifications.cancelledIds)
    }

    @Test
    fun singleCreationReturnsAlreadyRecordedForExistingPlantCareDay() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Ficus"))
        val now = Instant.parse("2025-04-01T10:00:00Z").toEpochMilli()
        database.careEventDao().insertEvent(careEvent("existing", "plant-1", now - 60_000L))
        val repository = repository(now, ZoneId.of("UTC"))

        assertEquals(
            CareEventCreateResult.AlreadyRecorded,
            repository.createEvent("plant-1", CareEventDraft(CareType.WATERING, now, null)),
        )
        assertEquals(1, database.careEventDao().observeEvents("plant-1").first().size)
    }

    private fun repository(
        now: Long,
        zoneId: ZoneId,
        notificationGateway: ReminderNotificationGateway = RecordingNotificationGateway(),
    ) = DefaultCareEventRepository(
        careEventDao = database.careEventDao(),
        epochMillisProvider = FixedNow(now),
        uuidProvider = SequenceUuid(),
        zoneIdProvider = FixedZone(zoneId),
        notificationGateway = notificationGateway,
        operations = DataOperationCoordinator(),
    )

    private fun careEvent(id: String, plantId: String, performedAt: Long) = CareEventEntity(
        id = id,
        plantId = plantId,
        careType = CareType.WATERING.name,
        performedAt = performedAt,
        note = null,
        createdAt = performedAt,
        updatedAt = performedAt,
    )

    private fun intervalRule(id: String, plantId: String, dueEpochDay: Long) = ReminderRuleEntity(
        id = id,
        plantId = plantId,
        careType = CareType.WATERING.name,
        scheduleType = "INTERVAL",
        enabled = true,
        intervalDays = 7,
        nextDueEpochDay = dueEpochDay,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private fun notice(id: String, rule: ReminderRuleEntity, dueEpochDay: Long) = NoticeEntity(
        id = id,
        ruleId = rule.id,
        occurrenceKey = "interval:${rule.id}:$dueEpochDay",
        careType = rule.careType,
        dueEpochDay = dueEpochDay,
        createdAt = 2_000L,
        readAt = null,
        deliveryState = "PENDING",
    )

    private class FixedNow(private val value: Long) : EpochMillisProvider {
        override fun now(): Long = value
    }

    private class FixedZone(private val zoneId: ZoneId) : ZoneIdProvider {
        override fun currentZoneId(): ZoneId = zoneId
    }

    private class SequenceUuid : UuidProvider {
        private val next = AtomicInteger()
        override fun newUuid(): String = "generated-${next.incrementAndGet()}"
    }

    private class RecordingNotificationGateway(
        private val failOnCancel: Boolean = false,
    ) : ReminderNotificationGateway {
        val cancelledIds = mutableListOf<String>()

        override fun ensureChannel() = Unit
        override fun canPostNotifications(): Boolean = true
        override fun publish(delivery: PendingReminderDelivery): ReminderNotificationResult =
            ReminderNotificationResult.POSTED

        override fun cancel(noticeId: String) {
            cancelledIds += noticeId
            if (failOnCancel) error("notification cancellation failed")
        }
    }
}
