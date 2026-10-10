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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlantDatabaseMigrationTest {

    private val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesLegacyRowsAndAllowsFurtherReadsAndWrites() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)

        try {
            migrationTestHelper.createDatabase(databaseName, 1).use { database ->
                database.execSQL("INSERT INTO Plant(uid, name) VALUES (10, '  Ficus  ')")
                database.execSQL("INSERT INTO Plant(uid, name) VALUES (11, '   ')")
                database.execSQL(
                    "INSERT INTO Plant(uid, name) VALUES (?, ?)",
                    arrayOf<Any?>(12L, "\t\n\r"),
                )
                database.execSQL(
                    "INSERT INTO Plant(uid, name) VALUES (?, ?)",
                    arrayOf<Any?>(13L, "\u00A0"),
                )
            }

            val migratedDatabase = migrationTestHelper.runMigrationsAndValidate(
                databaseName,
                2,
                true,
                MIGRATION_1_2,
            )
            migratedDatabase.close()

            val database = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                databaseName,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).allowMainThreadQueries().build()
            try {
                val migratedPlants = database.plantDao()
                    .observePlants(escapedQuery = "", favoritesOnly = false)
                    .first()

                assertEquals(4, migratedPlants.size)
                assertEquals(
                    setOf("Ficus", "Plant 11", "Plant 12", "Plant 13"),
                    migratedPlants.map { it.displayName }.toSet(),
                )
                assertEquals(
                    setOf("ficus", "plant 11", "plant 12", "plant 13"),
                    migratedPlants.map { it.displayNameSearch }.toSet(),
                )
                assertTrue(migratedPlants.all { it.commonNameSearch.isEmpty() })
                assertTrue(migratedPlants.all { it.scientificNameSearch.isEmpty() })
                assertEquals(4, migratedPlants.map { it.id }.toSet().size)
                assertTrue(migratedPlants.all { UUID.fromString(it.id) != null })
                assertTrue(migratedPlants.all { it.createdAt > 0L })
                assertTrue(migratedPlants.all { it.createdAt == it.updatedAt })
                assertTrue(migratedPlants.all { !it.isFavorite })
                assertTrue(migratedPlants.all { it.commonName == null })
                assertTrue(migratedPlants.all { it.scientificName == null })
                assertTrue(migratedPlants.all { it.location == null })
                assertTrue(migratedPlants.all { it.acquiredEpochDay == null })
                assertTrue(migratedPlants.all { it.description == null })
                assertTrue(migratedPlants.all { it.mainPhotoPath == null })

                val newPlant = testPlant(id = "new-v2-row", displayName = "New v2 plant")
                database.plantDao().insertPlant(newPlant)
                assertNotNull(database.plantDao().observePlant(newPlant.id).first())
                assertFalse(database.plantDao().observePlant(newPlant.id).first()!!.isFavorite)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
