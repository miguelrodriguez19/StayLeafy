package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_3_4
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_4_5
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_5_6
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlantV4MigrationTest {

    private val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private lateinit var databaseName: String

    @Before
    fun setUp() {
        databaseName = "v4-migration-${UUID.randomUUID()}.db"
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    fun migrate3To4_renamesNameDropsCommonColumnsAndPreservesNotes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plant = testPlant(
            id = "plant-1",
            name = "Ficus",
            scientificName = "Ficus elastica",
        )
        val note = PlantNoteEntity(
            id = "note-1",
            plantId = plant.id,
            body = "Keep near the window",
            createdAt = 1_000L,
            updatedAt = 2_000L,
        )

        migrationTestHelper.createDatabase(databaseName, 3).use { version3Database ->
            version3Database.execSQL(
                """
                INSERT INTO plants(
                    id, displayName, displayNameSearch, commonName, commonNameSearch,
                    scientificName, scientificNameSearch, location, acquiredEpochDay,
                    description, mainPhotoPath, isFavorite, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    plant.id,
                    plant.name,
                    plant.nameSearch,
                    "Rubber plant",
                    "rubber plant",
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
            version3Database.execSQL(
                "INSERT INTO plant_notes(id, plantId, body, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(note.id, note.plantId, note.body, note.createdAt, note.updatedAt),
            )
        }

        migrationTestHelper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            MIGRATION_3_4,
        ).close()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val migratedPlant = database.plantDao().observePlant(plant.id).first()!!
            assertEquals(plant.name, migratedPlant.name)
            assertEquals(plant.nameSearch, migratedPlant.nameSearch)
            assertEquals(plant.scientificName, migratedPlant.scientificName)
            assertEquals(plant.mainPhotoPath, migratedPlant.mainPhotoPath)
            assertTrue(migratedPlant.isFavorite == plant.isFavorite)
            assertEquals(note, database.plantNoteDao().observeNote(plant.id, note.id).first())

            database.plantDao().updatePlant(
                id = plant.id,
                name = "Updated Ficus",
                nameSearch = "updated ficus",
                scientificName = plant.scientificName,
                scientificNameSearch = plant.scientificNameSearch,
                location = plant.location,
                acquiredEpochDay = plant.acquiredEpochDay,
                description = plant.description,
                updatedAt = 3_000L,
            )
            assertEquals("Updated Ficus", database.plantDao().observePlant(plant.id).first()!!.name)
            assertFalse(database.plantDao().observePlant("missing").first() != null)
        } finally {
            database.close()
        }
    }
}
