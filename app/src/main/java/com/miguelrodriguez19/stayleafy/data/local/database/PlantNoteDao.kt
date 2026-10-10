package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantNoteDao {
    @Query(
        """
        SELECT * FROM plant_notes
        WHERE plantId = :plantId
        ORDER BY createdAt DESC, id DESC
        """,
    )
    fun observeNotes(plantId: String): Flow<List<PlantNoteEntity>>

    @Query("SELECT * FROM plant_notes WHERE plantId = :plantId AND id = :noteId")
    fun observeNote(plantId: String, noteId: String): Flow<PlantNoteEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNote(note: PlantNoteEntity)

    @Query(
        """
        UPDATE plant_notes
        SET body = :body,
            updatedAt = :updatedAt
        WHERE plantId = :plantId AND id = :noteId
        """,
    )
    suspend fun updateNote(plantId: String, noteId: String, body: String, updatedAt: Long)

    @Query("DELETE FROM plant_notes WHERE plantId = :plantId AND id = :noteId")
    suspend fun deleteNote(plantId: String, noteId: String)
}
