package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleChange
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDaoTest {
    private lateinit var database: AppDatabase

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
    fun plantFilters_applyUpcomingAndOverdueAsUnionWithExistingFilters() = runBlocking {
        val overdue = testPlant("overdue", "Alpha", isFavorite = true)
        val upcoming = testPlant("upcoming", "Beta", isFavorite = true)
        val later = testPlant("later", "Gamma", isFavorite = true)
        listOf(overdue, upcoming, later).forEach { database.plantDao().insertPlant(it) }
        database.reminderRuleDao().upsertRule(daoRule("r1", overdue.id, 99L))
        database.reminderRuleDao().upsertRule(daoRule("r2", upcoming.id, 107L))
        database.reminderRuleDao().upsertRule(daoRule("r3", later.id, 108L))

        val both = database.plantDao().observePlants("", true, true, true, 100L, 107L).first()
        val upcomingOnly = database.plantDao().observePlants("", false, true, false, 100L, 107L).first()

        assertEquals(listOf(overdue.id, upcoming.id), both.map { it.id })
        assertEquals(listOf(upcoming.id), upcomingOnly.map { it.id })
    }

    @Test
    fun applyChanges_rollsBackAllRulesWhenOneWriteFails() = runBlocking {
        database.plantDao().insertPlant(testPlant("plant-1", "Ficus"))
        val changes = listOf(
            ReminderRuleChange(daoRule("valid", "plant-1", 100L), false),
            ReminderRuleChange(daoRule("invalid", "missing", 100L), false),
        )

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.reminderRuleDao().applyChanges(changes, 5L) }
        }

        assertEquals(emptyList<ReminderRuleEntity>(), database.reminderRuleDao().getRules("plant-1"))
    }

    @Test
    fun latestCareAdvancesDueAndReadsNoticeWhileBackdatedCareDoesNotRegress() = runBlocking {
        val plant = testPlant("plant-1", "Ficus")
        val rule = daoRule("rule-1", plant.id, 200L)
        database.plantDao().insertPlant(plant)
        database.reminderRuleDao().upsertRule(rule)
        database.noticeDao().insertNotice(
            daoNotice("notice-1", rule.id, requireNotNull(rule.nextDueEpochDay)),
        )
        database.careEventDao().insertEvent(careEvent("latest", plant.id, 10_000L))

        database.careEventDao().insertEventAndAdvanceReminder(
            careEvent("backdated", plant.id, 9_000L),
            performedEpochDay = 100L,
        )
        assertEquals(200L, database.reminderRuleDao().getRule(rule.id)?.nextDueEpochDay)
        assertEquals(1, database.noticeDao().observeUnreadCount().first())

        database.careEventDao().insertEventAndAdvanceReminder(
            careEvent("newest", plant.id, 11_000L),
            performedEpochDay = 201L,
        )
        assertEquals(208L, database.reminderRuleDao().getRule(rule.id)?.nextDueEpochDay)
        assertEquals(0, database.noticeDao().observeUnreadCount().first())
    }

    @Test
    fun clearingSelectedHistoryPreservesReminderDueDate() = runBlocking {
        val plant = testPlant("plant-1", "Ficus")
        val rule = daoRule("rule-1", plant.id, 250L)
        database.plantDao().insertPlant(plant)
        database.reminderRuleDao().upsertRule(rule)
        database.careEventDao().insertEvent(careEvent("event-1", plant.id, 10_000L))

        database.careEventDao().deleteEventsByType(plant.id, "WATERING")

        assertEquals(250L, database.reminderRuleDao().getRule(rule.id)?.nextDueEpochDay)
    }

    @Test
    fun matchingSeasonalCare_resolvesOnlyCurrentOccurrenceNotice() = runBlocking {
        val plant = testPlant("plant-1", "Hydrangea")
        val rule = ReminderRuleEntity(
            id = "season-1",
            plantId = plant.id,
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
        val occurrenceStart = java.time.LocalDate.of(2026, 3, 1).toEpochDay()
        database.plantDao().insertPlant(plant)
        database.reminderRuleDao().upsertRule(rule)
        database.noticeDao().insertNotice(
            NoticeEntity(
                id = "season-notice",
                ruleId = rule.id,
                occurrenceKey = "seasonal:${rule.id}:$occurrenceStart",
                careType = rule.careType,
                dueEpochDay = occurrenceStart,
                createdAt = 1L,
                readAt = null,
                deliveryState = "DELIVERED",
            ),
        )

        val resolved = database.careEventDao().insertEventAndAdvanceReminder(
            CareEventEntity(
                id = "season-care",
                plantId = plant.id,
                careType = rule.careType,
                performedAt = 2L,
                note = null,
                createdAt = 2L,
                updatedAt = 2L,
            ),
            performedEpochDay = java.time.LocalDate.of(2026, 4, 10).toEpochDay(),
        )

        assertEquals(listOf("season-notice"), resolved)
        assertEquals(0, database.noticeDao().observeUnreadCount().first())
    }

    @Test
    fun notices_areDeduplicatedOrderedAndJoinedWithCurrentPlantName() = runBlocking {
        val plant = testPlant("plant-1", "Current name")
        val rule = daoRule("rule-1", plant.id, 100L)
        database.plantDao().insertPlant(plant)
        database.reminderRuleDao().upsertRule(rule)
        val first = daoNotice("a-notice", rule.id, 100L).copy(createdAt = 5L)
        val second = daoNotice("b-notice", rule.id, 101L).copy(
            occurrenceKey = "${rule.id}:101",
            dueEpochDay = 101L,
            createdAt = 5L,
        )

        database.noticeDao().insertNotice(first)
        database.noticeDao().insertNotice(second)
        val duplicateResult = database.noticeDao().insertNotice(
            first.copy(id = "duplicate-id"),
        )

        assertEquals(-1L, duplicateResult)
        val notices = database.noticeDao().observeNotices().first()
        assertEquals(listOf(second.id, first.id), notices.map { it.id })
        assertEquals(listOf("Current name", "Current name"), notices.map { it.plantName })
    }

    private fun careEvent(id: String, plantId: String, performedAt: Long) = CareEventEntity(
        id = id,
        plantId = plantId,
        careType = "WATERING",
        performedAt = performedAt,
        note = null,
        createdAt = performedAt,
        updatedAt = performedAt,
    )
}

private fun daoRule(id: String, plantId: String, due: Long) = ReminderRuleEntity(
    id = id,
    plantId = plantId,
    careType = "WATERING",
    scheduleType = "INTERVAL",
    enabled = true,
    intervalDays = 7,
    nextDueEpochDay = due,
    createdAt = 1L,
    updatedAt = 1L,
)

private fun daoNotice(id: String, ruleId: String, due: Long) = NoticeEntity(
    id = id,
    ruleId = ruleId,
    occurrenceKey = "$ruleId:$due",
    careType = "WATERING",
    dueEpochDay = due,
    createdAt = 1L,
    readAt = null,
    deliveryState = "PENDING",
)
