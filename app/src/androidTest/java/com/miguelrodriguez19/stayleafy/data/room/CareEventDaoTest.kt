package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareEventDaoTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun crud_insertsReadsUpdatesAndDeletesEvent() = runBlocking {
        val plant = testPlant(id = "plant-1", name = "Ficus")
        val inserted = careEvent(
            id = "event-1",
            plantId = plant.id,
            performedAt = 1_000L,
            note = "Watered thoroughly",
        )
        database.plantDao().insertPlant(plant)

        database.careEventDao().insertEvent(inserted)
        assertEquals(inserted, database.careEventDao().observeEvent(plant.id, inserted.id).first())

        database.careEventDao().updateEvent(
            plantId = plant.id,
            eventId = inserted.id,
            careType = "PRUNING",
            performedAt = 2_000L,
            note = "Removed dry leaves",
            updatedAt = 3_000L,
        )
        assertEquals(
            inserted.copy(
                careType = "PRUNING",
                performedAt = 2_000L,
                note = "Removed dry leaves",
                updatedAt = 3_000L,
            ),
            database.careEventDao().observeEvent(plant.id, inserted.id).first(),
        )

        database.careEventDao().deleteEvent(plant.id, inserted.id)
        assertNull(database.careEventDao().observeEvent(plant.id, inserted.id).first())
    }

    @Test
    fun observeEvents_ordersByPerformedAtDescendingThenIdDescending() = runBlocking {
        val firstPlant = testPlant(id = "plant-1", name = "Ficus")
        val secondPlant = testPlant(id = "plant-2", name = "Monstera")
        database.plantDao().insertPlant(firstPlant)
        database.plantDao().insertPlant(secondPlant)
        val events = listOf(
            careEvent("b-event", firstPlant.id, 1_000L),
            careEvent("z-event", firstPlant.id, 2_000L),
            careEvent("a-event", firstPlant.id, 2_000L),
            careEvent("other", secondPlant.id, 9_000L),
        )
        events.forEach { event -> database.careEventDao().insertEvent(event) }

        assertEquals(
            listOf(events[1], events[2], events[0]),
            database.careEventDao().observeEvents(firstPlant.id).first(),
        )
    }

    @Test
    fun insertingEventForMissingPlant_isRejectedByForeignKey() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.careEventDao().insertEvent(careEvent("orphan", "missing", 1_000L))
            }
        }
    }

    @Test
    fun deletingPlant_cascadesToItsCareEvents() = runBlocking {
        val deletedPlant = testPlant(id = "deleted", name = "Deleted")
        val retainedPlant = testPlant(id = "retained", name = "Retained")
        val deletedEvent = careEvent("deleted-event", deletedPlant.id, 1_000L)
        val retainedEvent = careEvent("retained-event", retainedPlant.id, 1_000L)
        database.plantDao().insertPlant(deletedPlant)
        database.plantDao().insertPlant(retainedPlant)
        database.careEventDao().insertEvent(deletedEvent)
        database.careEventDao().insertEvent(retainedEvent)

        database.plantDao().deletePlant(deletedPlant.id)

        assertNull(database.careEventDao().observeEvent(deletedPlant.id, deletedEvent.id).first())
        assertEquals(
            retainedEvent,
            database.careEventDao().observeEvent(retainedPlant.id, retainedEvent.id).first(),
        )
    }

    private fun careEvent(id: String, plantId: String, performedAt: Long, note: String? = null) =
        CareEventEntity(
            id = id,
            plantId = plantId,
            careType = "WATERING",
            performedAt = performedAt,
            note = note,
            createdAt = performedAt,
            updatedAt = performedAt,
        )
}
