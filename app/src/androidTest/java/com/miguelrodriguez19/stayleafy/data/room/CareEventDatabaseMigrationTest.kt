package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_1_2
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_2_3
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_3_4
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_4_5
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_5_6
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_6_7
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CareEventDatabaseMigrationTest {

    private val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private lateinit var databaseName: String

    @Before
    fun setUp() {
        databaseName = "care-event-migration-${UUID.randomUUID()}.db"
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    fun migrate4To5_createsCareEventsAndAllowsWrites() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plant = testPlant(id = "plant-1", name = "Ficus")

        migrationTestHelper.createDatabase(databaseName, 4).use { version4Database ->
            version4Database.execSQL(
                """
                INSERT INTO plants(
                    id, name, nameSearch, scientificName, scientificNameSearch, location,
                    acquiredEpochDay, description, mainPhotoPath, isFavorite, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    plant.id,
                    plant.name,
                    plant.nameSearch,
                    plant.scientificName,
                    plant.scientificNameSearch,
                    plant.location,
                    plant.acquiredEpochDay,
                    plant.description,
                    plant.mainPhotoPath,
                    if (plant.isFavorite) 1 else 0,
                    plant.createdAt,
                    plant.updatedAt,
                ),
            )
        }

        migrationTestHelper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            MIGRATION_4_5,
        ).close()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            val event = CareEventEntity(
                id = "event-1",
                plantId = plant.id,
                careType = "WATERING",
                performedAt = 5_000L,
                note = "Migrated database",
                createdAt = 5_000L,
                updatedAt = 5_000L,
            )
            database.careEventDao().insertEvent(event)
            assertEquals(event, database.careEventDao().observeEvent(plant.id, event.id).first())
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate1To7_registersFullChainAndAllowsCareEvents() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        migrationTestHelper.createDatabase(databaseName, 1).use { version1Database ->
            version1Database.execSQL("INSERT INTO Plant(uid, name) VALUES (42, 'Legacy plant')")
        }

        migrationTestHelper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
        ).close()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            val plant = database.plantDao().observePlants("", false).first().single()
            assertNotNull(plant)
            val event = CareEventEntity(
                id = "event-after-full-chain",
                plantId = plant.id,
                careType = "PRUNING",
                performedAt = 6_000L,
                note = null,
                createdAt = 6_000L,
                updatedAt = 6_000L,
            )
            database.careEventDao().insertEvent(event)
            assertEquals(event, database.careEventDao().observeEvent(plant.id, event.id).first())
        } finally {
            database.close()
        }
    }
}
