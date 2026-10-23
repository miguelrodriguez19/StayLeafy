package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_5_6
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_6_7
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderDatabaseMigrationTest {
    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        databaseName = "reminder-migration-${UUID.randomUUID()}.db"
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    fun migrate5To7_preservesPlantsAndIntervalDataAndAllowsSeasonalRules() = runBlocking {
        helper.createDatabase(databaseName, 5).use { database ->
            database.execSQL(
                """
                INSERT INTO plants(
                    id, name, nameSearch, scientificName, scientificNameSearch, location,
                    acquiredEpochDay, description, mainPhotoPath, isFavorite, createdAt, updatedAt
                ) VALUES ('plant-1', 'Ficus', 'ficus', NULL, '', NULL, NULL, NULL, NULL, 0, 1, 1)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            MIGRATION_5_6,
            MIGRATION_6_7,
        ).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            databaseName,
        ).allowMainThreadQueries().build()
        try {
            assertEquals("Ficus", database.plantDao().getPlant("plant-1")?.name)
            database.reminderRuleDao().upsertRule(reminderRule("rule-1", "plant-1", 21_000L))
            database.noticeDao().insertNotice(notice("notice-1", "rule-1", 21_000L))
            database.reminderRuleDao().upsertRule(seasonalRule("season-1", "plant-1"))
            assertEquals(1, database.noticeDao().observeUnreadCount().first())
            val rules = database.reminderRuleDao().getAllRules("plant-1")
            assertEquals(2, rules.size)
            assertEquals(21_000L, rules.single { it.id == "rule-1" }.nextDueEpochDay)
            assertEquals(3, rules.single { it.id == "season-1" }.seasonStartMonth)
            assertEquals(31, rules.single { it.id == "season-1" }.seasonEndDay)

            database.plantDao().deletePlant("plant-1")

            assertEquals(0, database.noticeDao().observeUnreadCount().first())
            assertEquals(emptyList<ReminderRuleEntity>(), database.reminderRuleDao().getRules("plant-1"))
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate5To6Then6To7_preservesSeededRuleAndNotice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper.createDatabase(databaseName, 5).use { database ->
            database.execSQL(
                """
                INSERT INTO plants(
                    id, name, nameSearch, scientificName, scientificNameSearch, location,
                    acquiredEpochDay, description, mainPhotoPath, isFavorite, createdAt, updatedAt
                ) VALUES (
                    'seeded-plant', 'Seeded plant', 'seeded plant', 'Ficus elastica',
                    'ficus elastica', 'Office', 19000, 'Seeded description',
                    'plant_photos/seeded.jpg', 1, 100, 200
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO plant_notes(id, plantId, body, createdAt, updatedAt)
                VALUES ('seeded-note', 'seeded-plant', 'Seeded note', 300, 400)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO care_events(id, plantId, careType, performedAt, note, createdAt, updatedAt)
                VALUES ('seeded-care', 'seeded-plant', 'WATERING', 500, 'Seeded care', 600, 700)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(databaseName, 6, true, MIGRATION_5_6).use { database ->
            database.execSQL(
                """
                INSERT INTO reminder_rules(
                    id, plantId, careType, scheduleType, enabled, intervalDays,
                    nextDueEpochDay, createdAt, updatedAt
                ) VALUES ('seeded-rule', 'seeded-plant', 'WATERING', 'INTERVAL', 1, 7, 42000, 800, 900)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO notices(
                    id, ruleId, occurrenceKey, careType, dueEpochDay, createdAt, readAt, deliveryState
                ) VALUES (
                    'seeded-notice', 'seeded-rule', 'interval:seeded-rule:42000',
                    'WATERING', 42000, 1000, NULL, 'PENDING'
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(databaseName, 7, true, MIGRATION_6_7).close()
        val version7 = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            val plant = version7.plantDao().getPlant("seeded-plant")
            requireNotNull(plant)
            assertEquals("Seeded plant", plant.name)
            assertEquals("seeded plant", plant.nameSearch)
            assertEquals("Ficus elastica", plant.scientificName)
            assertEquals("ficus elastica", plant.scientificNameSearch)
            assertEquals("Office", plant.location)
            assertEquals(19_000L, plant.acquiredEpochDay)
            assertEquals("Seeded description", plant.description)
            assertEquals("plant_photos/seeded.jpg", plant.mainPhotoPath)
            assertEquals(true, plant.isFavorite)
            assertEquals(100L, plant.createdAt)
            assertEquals(200L, plant.updatedAt)

            val notes = version7.plantNoteDao().observeNotes("seeded-plant").first()
            assertEquals(1, notes.size)
            assertEquals("seeded-note", notes.single().id)
            assertEquals("Seeded note", notes.single().body)
            assertEquals(300L, notes.single().createdAt)
            assertEquals(400L, notes.single().updatedAt)

            val careEvents = version7.careEventDao().observeEvents("seeded-plant").first()
            assertEquals(1, careEvents.size)
            assertEquals("seeded-care", careEvents.single().id)
            assertEquals("WATERING", careEvents.single().careType)
            assertEquals(500L, careEvents.single().performedAt)
            assertEquals("Seeded care", careEvents.single().note)
            assertEquals(600L, careEvents.single().createdAt)
            assertEquals(700L, careEvents.single().updatedAt)

            val rule = version7.reminderRuleDao().getRule("seeded-rule")
            requireNotNull(rule)
            assertEquals("seeded-plant", rule.plantId)
            assertEquals("WATERING", rule.careType)
            assertEquals("INTERVAL", rule.scheduleType)
            assertEquals(true, rule.enabled)
            assertEquals(7, rule.intervalDays)
            assertEquals(42_000L, rule.nextDueEpochDay)
            assertEquals(800L, rule.createdAt)
            assertEquals(900L, rule.updatedAt)

            val migratedNotice = version7.noticeDao().getPendingNotices().single()
            assertEquals("seeded-notice", migratedNotice.id)
            assertEquals("seeded-rule", migratedNotice.ruleId)
            assertEquals("interval:seeded-rule:42000", migratedNotice.occurrenceKey)
            assertEquals("WATERING", migratedNotice.careType)
            assertEquals(42_000L, migratedNotice.dueEpochDay)
            assertEquals(1_000L, migratedNotice.createdAt)
            assertEquals(null, migratedNotice.readAt)
            assertEquals("PENDING", migratedNotice.deliveryState)
        } finally {
            version7.close()
        }
    }
}

private fun seasonalRule(id: String, plantId: String) = ReminderRuleEntity(
    id = id,
    plantId = plantId,
    careType = "FERTILIZING",
    scheduleType = "SEASONAL",
    enabled = true,
    intervalDays = null,
    nextDueEpochDay = null,
    seasonStartMonth = 3,
    seasonStartDay = 1,
    seasonEndMonth = 5,
    seasonEndDay = 31,
    createdAt = 2L,
    updatedAt = 2L,
)

private fun reminderRule(id: String, plantId: String, due: Long) = ReminderRuleEntity(
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

private fun notice(id: String, ruleId: String, due: Long) = NoticeEntity(
    id = id,
    ruleId = ruleId,
    occurrenceKey = "$ruleId:$due",
    careType = "WATERING",
    dueEpochDay = due,
    createdAt = 1L,
    readAt = null,
    deliveryState = "PENDING",
)
