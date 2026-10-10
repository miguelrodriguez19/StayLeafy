package com.miguelrodriguez19.stayleafy.data.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
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
class PlantNoteDaoTest {

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
    fun crud_insertsReadsUpdatesAndDeletesNote() = runBlocking {
        val plant = testPlant(id = "plant-1", displayName = "Ficus")
        val inserted = PlantNoteEntity(
            id = "note-1",
            plantId = plant.id,
            body = "New leaf appeared",
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )
        database.plantDao().insertPlant(plant)

        database.plantNoteDao().insertNote(inserted)
        assertEquals(inserted, database.plantNoteDao().observeNote(plant.id, inserted.id).first())

        database.plantNoteDao().updateNote(
            plantId = plant.id,
            noteId = inserted.id,
            body = "New leaf is unfurling",
            updatedAt = 2_000L,
        )
        val updated = inserted.copy(body = "New leaf is unfurling", updatedAt = 2_000L)
        assertEquals(updated, database.plantNoteDao().observeNote(plant.id, inserted.id).first())

        database.plantNoteDao().deleteNote(plant.id, inserted.id)
        assertNull(database.plantNoteDao().observeNote(plant.id, inserted.id).first())
    }

    @Test
    fun observeNotes_returnsNewestCreatedAtThenIdAndOnlyRequestedPlant() = runBlocking {
        val firstPlant = testPlant(id = "plant-1", displayName = "Ficus")
        val secondPlant = testPlant(id = "plant-2", displayName = "Monstera")
        database.plantDao().insertPlant(firstPlant)
        database.plantDao().insertPlant(secondPlant)
        val notes = listOf(
            PlantNoteEntity("b-note", firstPlant.id, "Older", 1_000L, 1_000L),
            PlantNoteEntity("z-note", firstPlant.id, "Newest tie-breaker", 2_000L, 2_000L),
            PlantNoteEntity("a-note", firstPlant.id, "Newest first", 2_000L, 2_000L),
            PlantNoteEntity("other", secondPlant.id, "Other plant", 9_000L, 9_000L),
        )
        notes.forEach { note -> database.plantNoteDao().insertNote(note) }

        assertEquals(
            listOf(notes[1], notes[2], notes[0]),
            database.plantNoteDao().observeNotes(firstPlant.id).first(),
        )
    }

    @Test
    fun deletingPlant_cascadesToItsNotes() = runBlocking {
        val deletedPlant = testPlant(id = "deleted", displayName = "Deleted")
        val retainedPlant = testPlant(id = "retained", displayName = "Retained")
        database.plantDao().insertPlant(deletedPlant)
        database.plantDao().insertPlant(retainedPlant)
        val deletedNote = PlantNoteEntity("deleted-note", deletedPlant.id, "Gone", 1_000L, 1_000L)
        val retainedNote = PlantNoteEntity("retained-note", retainedPlant.id, "Kept", 1_000L, 1_000L)
        database.plantNoteDao().insertNote(deletedNote)
        database.plantNoteDao().insertNote(retainedNote)

        database.plantDao().deletePlant(deletedPlant.id)

        assertNull(database.plantNoteDao().observeNote(deletedPlant.id, deletedNote.id).first())
        assertEquals(retainedNote, database.plantNoteDao().observeNote(retainedPlant.id, retainedNote.id).first())
    }

    @Test
    fun insertingNoteForMissingPlant_isRejectedByForeignKey() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.plantNoteDao().insertNote(
                    PlantNoteEntity("orphan", "missing", "Invalid", 1_000L, 1_000L),
                )
            }
        }
    }
}
