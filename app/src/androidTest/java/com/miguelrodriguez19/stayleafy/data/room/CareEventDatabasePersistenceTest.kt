package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CareEventDatabasePersistenceTest {

    @Test
    fun fileBackedDatabase_preservesCareEventsAfterCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "care-event-persistence-${UUID.randomUUID()}.db"
        val plant = testPlant(id = "persistent-plant", name = "Persistent Ficus")
        val event = CareEventEntity(
            id = "persistent-event",
            plantId = plant.id,
            careType = "FERTILIZING",
            performedAt = 7_000L,
            note = "After repotting",
            createdAt = 7_000L,
            updatedAt = 7_000L,
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
                firstDatabase.careEventDao().insertEvent(event)
            } finally {
                firstDatabase.close()
            }

            val reopenedDatabase = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                databaseName,
            ).allowMainThreadQueries().build()
            try {
                assertEquals(event, reopenedDatabase.careEventDao().observeEvent(plant.id, event.id).first())
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
