package com.miguelrodriguez19.stayleafy.testdi

import com.miguelrodriguez19.stayleafy.data.PlantNoteRepository
import com.miguelrodriguez19.stayleafy.model.PlantNote
import com.miguelrodriguez19.stayleafy.model.PlantNoteDraft
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

@Singleton
class FakePlantNoteRepository @Inject constructor() : PlantNoteRepository {
    private val notes = MutableStateFlow<List<PlantNote>>(emptyList())

    fun replaceNotes(items: List<PlantNote>) {
        notes.value = items
    }

    fun reset() {
        notes.value = emptyList()
    }

    override fun observeNotes(plantId: String): Flow<List<PlantNote>> =
        notes.map { items -> items.filter { it.plantId == plantId } }

    override fun observeNote(plantId: String, noteId: String): Flow<PlantNote?> =
        notes.map { items -> items.firstOrNull { it.plantId == plantId && it.id == noteId } }

    override suspend fun createNote(plantId: String, draft: PlantNoteDraft): String {
        val id = "note-${notes.value.size + 1}"
        notes.value += PlantNote(id, plantId, draft.body.trim(), 0L, 0L)
        return id
    }

    override suspend fun updateNote(plantId: String, noteId: String, draft: PlantNoteDraft) {
        notes.value = notes.value.map { note ->
            if (note.plantId == plantId && note.id == noteId) note.copy(body = draft.body.trim()) else note
        }
    }

    override suspend fun deleteNote(plantId: String, noteId: String) {
        notes.value = notes.value.filterNot { it.plantId == plantId && it.id == noteId }
    }
}
