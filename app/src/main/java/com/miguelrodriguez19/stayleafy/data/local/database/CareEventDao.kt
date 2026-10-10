package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CareEventDao {
    @Query(
        """
        SELECT * FROM care_events
        WHERE plantId = :plantId
        ORDER BY performedAt DESC, id DESC
        """,
    )
    fun observeEvents(plantId: String): Flow<List<CareEventEntity>>

    @Query("SELECT * FROM care_events WHERE plantId = :plantId AND id = :eventId")
    fun observeEvent(plantId: String, eventId: String): Flow<CareEventEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: CareEventEntity)

    @Query(
        """
        UPDATE care_events
        SET careType = :careType,
            performedAt = :performedAt,
            note = :note,
            updatedAt = :updatedAt
        WHERE plantId = :plantId AND id = :eventId
        """,
    )
    suspend fun updateEvent(
        plantId: String,
        eventId: String,
        careType: String,
        performedAt: Long,
        note: String?,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM care_events WHERE plantId = :plantId AND id = :eventId")
    suspend fun deleteEvent(plantId: String, eventId: String): Int

    @Query("DELETE FROM care_events WHERE plantId = :plantId AND careType = :careType")
    suspend fun deleteEventsByType(plantId: String, careType: String): Int
}
