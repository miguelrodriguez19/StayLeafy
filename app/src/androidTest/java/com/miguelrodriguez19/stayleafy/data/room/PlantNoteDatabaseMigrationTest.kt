package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_1_2
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_2_3
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlantNoteDatabaseMigrationTest {

    private val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private lateinit var databaseName: String
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        databaseName = "note-migration-${UUID.randomUUID()}.db"
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        database?.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    fun migrate2To3_createsNotesAndPreservesCascadeBehavior() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plant = testPlant(id = "plant-1", displayName = "Ficus")

        migrationTestHelper.createDatabase(databaseName, 2).use { version2Database ->
            version2Database.execSQL(
                """
                INSERT INTO plants(
                    id, displayName, displayNameSearch, commonName, commonNameSearch,
                    scientificName, scientificNameSearch, location, acquiredEpochDay,
                    description, mainPhotoPath, isFavorite, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    plant.id,
                    plant.displayName,
                    plant.displayNameSearch,
                    plant.commonName,
                    plant.commonNameSearch,
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
            3,
            true,
            MIGRATION_2_3,
        ).close()

        database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName,
        ).allowMainThreadQueries().build()
        val note = com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity(
            id = "note-1",
            plantId = plant.id,
            body = "Migrated note",
            createdAt = 3_000L,
            updatedAt = 3_000L,
        )
        database!!.plantNoteDao().insertNote(note)
        assertEquals(note, database!!.plantNoteDao().observeNote(plant.id, note.id).first())

        database!!.plantDao().deletePlant(plant.id)

        assertNull(database!!.plantNoteDao().observeNote(plant.id, note.id).first())
    }

    @Test
    fun migrate1To3_preservesLegacyPlantAndAllowsNotes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        migrationTestHelper.createDatabase(databaseName, 1).use { version1Database ->
            version1Database.execSQL("INSERT INTO Plant(uid, name) VALUES (7, 'Legacy fern')")
        }

        migrationTestHelper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
        ).close()

        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        val plant = database!!.plantDao().observePlants("", false).first().single()
        assertEquals("Legacy fern", plant.displayName)

        val note = com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity(
            id = "note-after-chain",
            plantId = plant.id,
            body = "Still writable",
            createdAt = 4_000L,
            updatedAt = 4_000L,
        )
        database!!.plantNoteDao().insertNote(note)
        assertEquals(note, database!!.plantNoteDao().observeNote(plant.id, note.id).first())
    }
}
