package com.miguelrodriguez19.stayleafy.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface BackupDao {
    @Query("SELECT * FROM plants ORDER BY id ASC")
    suspend fun getPlants(): List<PlantEntity>

    @Query("SELECT * FROM plant_notes ORDER BY id ASC")
    suspend fun getNotes(): List<PlantNoteEntity>

    @Query("SELECT * FROM care_events ORDER BY id ASC")
    suspend fun getCareEvents(): List<CareEventEntity>

    @Query("SELECT * FROM reminder_rules ORDER BY id ASC")
    suspend fun getReminderRules(): List<ReminderRuleEntity>

    @Query("SELECT * FROM notices ORDER BY id ASC")
    suspend fun getNotices(): List<NoticeEntity>

    @Query("DELETE FROM notices")
    suspend fun deleteNotices()

    @Query("DELETE FROM reminder_rules")
    suspend fun deleteReminderRules()

    @Query("DELETE FROM care_events")
    suspend fun deleteCareEvents()

    @Query("DELETE FROM plant_notes")
    suspend fun deletePlantNotes()

    @Query("DELETE FROM plants")
    suspend fun deletePlants()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlants(rows: List<PlantEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNotes(rows: List<PlantNoteEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCareEvents(rows: List<CareEventEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminderRules(rows: List<ReminderRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNotices(rows: List<NoticeEntity>)

    @Transaction
    suspend fun readSnapshot(): DatabaseBackupSnapshot = DatabaseBackupSnapshot(
        plants = getPlants(),
        notes = getNotes(),
        careEvents = getCareEvents(),
        reminderRules = getReminderRules(),
        notices = getNotices(),
    )

    @Transaction
    suspend fun replaceAll(snapshot: DatabaseBackupSnapshot) {
        // Foreign-key order is required even though the whole operation is transactional.
        deleteNotices()
        deleteReminderRules()
        deleteCareEvents()
        deletePlantNotes()
        deletePlants()
        if (snapshot.plants.isNotEmpty()) insertPlants(snapshot.plants)
        if (snapshot.notes.isNotEmpty()) insertNotes(snapshot.notes)
        if (snapshot.careEvents.isNotEmpty()) insertCareEvents(snapshot.careEvents)
        if (snapshot.reminderRules.isNotEmpty()) insertReminderRules(snapshot.reminderRules)
        if (snapshot.notices.isNotEmpty()) insertNotices(snapshot.notices)
    }
}

data class DatabaseBackupSnapshot(
    val plants: List<PlantEntity>,
    val notes: List<PlantNoteEntity>,
    val careEvents: List<CareEventEntity>,
    val reminderRules: List<ReminderRuleEntity>,
    val notices: List<NoticeEntity>,
)
