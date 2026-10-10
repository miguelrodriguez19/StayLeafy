package com.miguelrodriguez19.stayleafy.data

import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteDao
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
import com.miguelrodriguez19.stayleafy.model.PlantNote
import com.miguelrodriguez19.stayleafy.model.PlantNoteDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface PlantNoteRepository {
    fun observeNotes(plantId: String): Flow<List<PlantNote>>

    fun observeNote(plantId: String, noteId: String): Flow<PlantNote?>

    suspend fun createNote(plantId: String, draft: PlantNoteDraft): String

    suspend fun updateNote(plantId: String, noteId: String, draft: PlantNoteDraft)

    suspend fun deleteNote(plantId: String, noteId: String)
}

class DefaultPlantNoteRepository @Inject constructor(
    private val plantNoteDao: PlantNoteDao,
    private val epochMillisProvider: EpochMillisProvider,
    private val uuidProvider: UuidProvider,
) : PlantNoteRepository {

    override fun observeNotes(plantId: String): Flow<List<PlantNote>> =
        plantNoteDao.observeNotes(plantId).map { notes -> notes.map(PlantNoteEntity::toModel) }

    override fun observeNote(plantId: String, noteId: String): Flow<PlantNote?> =
        plantNoteDao.observeNote(plantId, noteId).map { note -> note?.toModel() }

    override suspend fun createNote(plantId: String, draft: PlantNoteDraft): String {
        val body = draft.body.trim()
        require(body.isNotBlank()) { "body must not be blank" }

        val id = uuidProvider.newUuid()
        val now = epochMillisProvider.now()
        plantNoteDao.insertNote(
            PlantNoteEntity(
                id = id,
                plantId = plantId,
                body = body,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    override suspend fun updateNote(plantId: String, noteId: String, draft: PlantNoteDraft) {
        val body = draft.body.trim()
        require(body.isNotBlank()) { "body must not be blank" }

        plantNoteDao.updateNote(
            plantId = plantId,
            noteId = noteId,
            body = body,
            updatedAt = epochMillisProvider.now(),
        )
    }

    override suspend fun deleteNote(plantId: String, noteId: String) {
        plantNoteDao.deleteNote(plantId, noteId)
    }
}

private fun PlantNoteEntity.toModel(): PlantNote =
    PlantNote(
        id = id,
        plantId = plantId,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
