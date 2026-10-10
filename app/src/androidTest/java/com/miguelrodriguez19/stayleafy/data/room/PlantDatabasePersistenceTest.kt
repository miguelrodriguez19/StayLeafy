package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlantDatabasePersistenceTest {

    @Test
    fun fileBackedDatabase_preservesRowsAfterCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "persistence-${UUID.randomUUID()}.db"
        val plant = testPlant(
            id = "persistent-plant",
            displayName = "Persistent Ficus",
            commonName = "Ficus",
            isFavorite = true,
        )
        context.deleteDatabase(databaseName)

        try {
            val firstDatabase = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                databaseName,
            ).allowMainThreadQueries().build()
            try {
                firstDatabase.plantDao().insertPlant(plant)
            } finally {
                firstDatabase.close()
            }

            val reopenedDatabase = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                databaseName,
            ).allowMainThreadQueries().build()
            try {
                assertEquals(plant, reopenedDatabase.plantDao().observePlant(plant.id).first())
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
