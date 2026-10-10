package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteDao
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
import com.miguelrodriguez19.stayleafy.model.PlantNoteDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPlantNoteRepositoryTest {

    private val fixedNow = 1_725_000_000_000L
    private val fixedUuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun createNote_trimsBodyAndUsesFixedTimestampAndUuid() = runTest {
        val dao = RecordingPlantNoteDao()
        val repository = createRepository(dao)

        val id = repository.createNote(
            plantId = "plant-1",
            draft = PlantNoteDraft(body = "  New leaf appeared.  "),
        )

        assertEquals(fixedUuid, id)
        assertEquals(
            PlantNoteEntity(
                id = id,
                plantId = "plant-1",
                body = "New leaf appeared.",
                createdAt = fixedNow,
                updatedAt = fixedNow,
            ),
            dao.insertedNote,
        )
    }

    @Test
    fun createNote_blankBody_isRejectedBeforeInsert() = runTest {
        val dao = RecordingPlantNoteDao()
        val repository = createRepository(dao)

        val error = runCatching {
            repository.createNote("plant-1", PlantNoteDraft(body = " \t\n "))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("body must not be blank", error?.message)
        assertEquals(null, dao.insertedNote)
    }

    @Test
    fun updateNote_trimsBodyAndUsesCurrentTimestamp() = runTest {
        val dao = RecordingPlantNoteDao()
        val repository = createRepository(dao)

        repository.updateNote(
            plantId = "plant-1",
            noteId = "note-1",
            draft = PlantNoteDraft(body = "  Updated observation  "),
        )

        assertEquals(
            NoteUpdateCall(
                plantId = "plant-1",
                noteId = "note-1",
                body = "Updated observation",
                updatedAt = fixedNow,
            ),
            dao.updateCall,
        )
    }

    @Test
    fun updateNote_blankBody_isRejectedBeforeUpdate() = runTest {
        val dao = RecordingPlantNoteDao()
        val repository = createRepository(dao)

        val error = runCatching {
            repository.updateNote("plant-1", "note-1", PlantNoteDraft(body = "   "))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(null, dao.updateCall)
    }

    @Test
    fun observeNotes_mapsEntitiesAndPassesPlantId() = runTest {
        val entity = noteEntity(id = "note-1", plantId = "plant-1")
        val dao = RecordingPlantNoteDao(listOf(entity))
        val repository = createRepository(dao)

        assertEquals(listOf(entity.toModelForTest()), repository.observeNotes("plant-1").first())
        assertEquals("plant-1", dao.lastObservedPlantId)
    }

    @Test
    fun observeNote_mapsEntityAndMissingRows() = runTest {
        val entity = noteEntity(id = "note-1", plantId = "plant-1")
        val dao = RecordingPlantNoteDao(listOf(entity))
        val repository = createRepository(dao)

        assertEquals(entity.toModelForTest(), repository.observeNote("plant-1", "note-1").first())
        assertEquals(null, repository.observeNote("plant-1", "missing").first())
        assertEquals(NoteKey("plant-1", "missing"), dao.lastObservedNote)
    }

    @Test
    fun deleteNote_passesPlantAndNoteIds() = runTest {
        val dao = RecordingPlantNoteDao()
        val repository = createRepository(dao)

        repository.deleteNote("plant-1", "note-1")

        assertEquals(NoteKey("plant-1", "note-1"), dao.deletedNote)
    }

    private fun createRepository(dao: PlantNoteDao) = DefaultPlantNoteRepository(
        plantNoteDao = dao,
        epochMillisProvider = FixedNoteEpochMillisProvider(fixedNow),
        uuidProvider = FixedNoteUuidProvider(fixedUuid),
    )
}

private class FixedNoteEpochMillisProvider(private val value: Long) : EpochMillisProvider {
    override fun now(): Long = value
}

private class FixedNoteUuidProvider(private val value: String) : UuidProvider {
    override fun newUuid(): String = value
}

private class RecordingPlantNoteDao(initialNotes: List<PlantNoteEntity> = emptyList()) : PlantNoteDao {
    private val data = MutableStateFlow(initialNotes)

    var insertedNote: PlantNoteEntity? = null
        private set
    var updateCall: NoteUpdateCall? = null
        private set
    var deletedNote: NoteKey? = null
        private set
    var lastObservedPlantId: String? = null
        private set
    var lastObservedNote: NoteKey? = null
        private set

    override fun observeNotes(plantId: String): Flow<List<PlantNoteEntity>> {
        lastObservedPlantId = plantId
        return data.map { notes -> notes.filter { it.plantId == plantId } }
    }

    override fun observeNote(plantId: String, noteId: String): Flow<PlantNoteEntity?> {
        lastObservedNote = NoteKey(plantId, noteId)
        return data.map { notes -> notes.firstOrNull { it.plantId == plantId && it.id == noteId } }
    }

    override suspend fun insertNote(note: PlantNoteEntity) {
        insertedNote = note
        data.value = data.value + note
    }

    override suspend fun updateNote(plantId: String, noteId: String, body: String, updatedAt: Long) {
        updateCall = NoteUpdateCall(plantId, noteId, body, updatedAt)
        data.value = data.value.map { note ->
            if (note.plantId == plantId && note.id == noteId) {
                note.copy(body = body, updatedAt = updatedAt)
            } else {
                note
            }
        }
    }

    override suspend fun deleteNote(plantId: String, noteId: String) {
        deletedNote = NoteKey(plantId, noteId)
        data.value = data.value.filterNot { it.plantId == plantId && it.id == noteId }
    }
}

private data class NoteUpdateCall(
    val plantId: String,
    val noteId: String,
    val body: String,
    val updatedAt: Long,
)

private data class NoteKey(val plantId: String, val noteId: String)

private fun noteEntity(id: String, plantId: String) = PlantNoteEntity(
    id = id,
    plantId = plantId,
    body = "New growth",
    createdAt = 1_700_000_000_000L,
    updatedAt = 1_700_000_100_000L,
)

private fun PlantNoteEntity.toModelForTest() = com.miguelrodriguez19.stayleafy.model.PlantNote(
    id = id,
    plantId = plantId,
    body = body,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
