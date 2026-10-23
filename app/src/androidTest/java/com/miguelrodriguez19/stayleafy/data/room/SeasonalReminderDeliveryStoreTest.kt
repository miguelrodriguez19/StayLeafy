package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.model.ReminderScheduleType
import com.miguelrodriguez19.stayleafy.platform.reminder.RoomReminderDeliveryStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeasonalReminderDeliveryStoreTest {
    private lateinit var database: AppDatabase
    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 4, 15).atStartOfDay(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun lateEvaluationWithinWindow_createsOneSeasonalOccurrence() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Hydrangea"))
        database.reminderRuleDao().upsertRule(seasonalRule("rule-1"))
        val store = store()
        val today = LocalDate.of(2026, 4, 15).toEpochDay()

        val first = store.preparePendingDeliveries(today)
        val repeated = store.preparePendingDeliveries(today)

        assertEquals(1, first.size)
        assertEquals(first.single().noticeId, repeated.single().noticeId)
        assertEquals(ReminderScheduleType.SEASONAL, first.single().scheduleType)
        assertEquals(
            "seasonal:rule-1:${LocalDate.of(2026, 3, 1).toEpochDay()}",
            first.single().occurrenceKey,
        )
        assertEquals(1, database.noticeDao().observeNotices().first().size)
    }

    @Test
    fun matchingCareInsideWindow_satisfiesOccurrenceWithoutNotice() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Hydrangea"))
        database.reminderRuleDao().upsertRule(seasonalRule("rule-1"))
        val performedAt = LocalDate.of(2026, 4, 2).atStartOfDay(zone).toInstant().toEpochMilli()
        database.careEventDao().insertEvent(
            CareEventEntity(
                id = "care-1",
                plantId = "plant-1",
                careType = "PRUNING",
                performedAt = performedAt,
                note = null,
                createdAt = performedAt,
                updatedAt = performedAt,
            ),
        )

        val pending = store().preparePendingDeliveries(LocalDate.of(2026, 4, 15).toEpochDay())

        assertEquals(emptyList<Any>(), pending)
        assertEquals(0, database.noticeDao().observeUnreadCount().first())
    }

    @Test
    fun pendingSeasonalDelivery_isSuppressedAfterItsWindowEnds() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Hydrangea"))
        database.reminderRuleDao().upsertRule(seasonalRule("rule-1"))
        val start = LocalDate.of(2026, 3, 1).toEpochDay()
        database.noticeDao().insertNotice(
            NoticeEntity(
                id = "notice-1",
                ruleId = "rule-1",
                occurrenceKey = "seasonal:rule-1:$start",
                careType = "PRUNING",
                dueEpochDay = start,
                createdAt = now,
                readAt = null,
                deliveryState = NoticeDeliveryState.PENDING.name,
            ),
        )

        val pending = store().preparePendingDeliveries(LocalDate.of(2026, 8, 1).toEpochDay())

        assertEquals(emptyList<Any>(), pending)
        assertEquals(
            NoticeDeliveryState.SUPPRESSED.name,
            database.noticeDao().observeNotices().first().single().deliveryState,
        )
    }

    @Test
    fun futurePendingSeasonalNotice_waitsForItsYearWithoutBeingSuppressed() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Hydrangea"))
        database.reminderRuleDao().upsertRule(seasonalRule("rule-1").copy(careType = "FERTILIZING"))
        val start = LocalDate.of(2027, 3, 1).toEpochDay()
        database.noticeDao().insertNotice(
            NoticeEntity(
                id = "future-notice", ruleId = "rule-1", occurrenceKey = "seasonal:rule-1:$start",
                careType = "FERTILIZING", dueEpochDay = start, createdAt = now,
                readAt = null, deliveryState = NoticeDeliveryState.PENDING.name,
            ),
        )

        assertEquals(emptyList<Any>(), store().preparePendingDeliveries(LocalDate.of(2026, 10, 16).toEpochDay()))
        assertEquals(NoticeDeliveryState.PENDING.name, database.noticeDao().observeNotices().first().single().deliveryState)
        val due = store().preparePendingDeliveries(start)
        assertEquals("future-notice", due.single().noticeId)
        assertEquals(1, database.noticeDao().observeNotices().first().size)
    }

    @Test
    fun futurePendingIntervalNotice_isNotDeliveredBeforeDueDate() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Hydrangea"))
        val due = LocalDate.of(2027, 10, 16).toEpochDay()
        database.reminderRuleDao().upsertRule(
            ReminderRuleEntity(
                id = "annual-interval", plantId = "plant-1", careType = "FERTILIZING",
                scheduleType = "INTERVAL", enabled = true, intervalDays = 365,
                nextDueEpochDay = due, createdAt = now, updatedAt = now,
            ),
        )
        database.noticeDao().insertNotice(
            NoticeEntity(
                id = "future-interval", ruleId = "annual-interval", occurrenceKey = "interval:annual-interval:$due",
                careType = "FERTILIZING", dueEpochDay = due, createdAt = now,
                readAt = null, deliveryState = NoticeDeliveryState.PENDING.name,
            ),
        )
        val store = store()
        assertEquals(emptyList<Any>(), store.preparePendingDeliveries(due - 1))
        assertEquals(NoticeDeliveryState.PENDING.name, database.noticeDao().observeNotices().first().single().deliveryState)
        assertEquals("future-interval", store.preparePendingDeliveries(due).single().noticeId)
    }

    private fun store() = RoomReminderDeliveryStore(
        database = database,
        reminderRuleDao = database.reminderRuleDao(),
        careEventDao = database.careEventDao(),
        noticeDao = database.noticeDao(),
        epochMillisProvider = object : EpochMillisProvider { override fun now() = now },
        uuidProvider = object : UuidProvider { override fun newUuid() = UUID.randomUUID().toString() },
        zoneIdProvider = object : ZoneIdProvider { override fun currentZoneId(): ZoneId = zone },
    )

    private fun seasonalRule(id: String) = ReminderRuleEntity(
        id = id,
        plantId = "plant-1",
        careType = "PRUNING",
        scheduleType = "SEASONAL",
        enabled = true,
        intervalDays = null,
        nextDueEpochDay = null,
        seasonStartMonth = 3,
        seasonStartDay = 1,
        seasonEndMonth = 5,
        seasonEndDay = 31,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
