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
    fun migrate5To6_preservesPlantsAndAllowsRulesNoticesAndCascades() = runBlocking {
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

        helper.runMigrationsAndValidate(databaseName, 6, true, MIGRATION_5_6).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            databaseName,
        ).allowMainThreadQueries().build()
        try {
            assertEquals("Ficus", database.plantDao().getPlant("plant-1")?.name)
            database.reminderRuleDao().upsertRule(reminderRule("rule-1", "plant-1", 21_000L))
            database.noticeDao().insertNotice(notice("notice-1", "rule-1", 21_000L))
            assertEquals(1, database.noticeDao().observeUnreadCount().first())

            database.plantDao().deletePlant("plant-1")

            assertEquals(0, database.noticeDao().observeUnreadCount().first())
            assertEquals(emptyList<ReminderRuleEntity>(), database.reminderRuleDao().getRules("plant-1"))
        } finally {
            database.close()
        }
    }
}

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
