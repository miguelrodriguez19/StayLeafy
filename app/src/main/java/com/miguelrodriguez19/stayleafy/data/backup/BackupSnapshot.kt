package com.miguelrodriguez19.stayleafy.data.backup

import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import kotlinx.serialization.Serializable

/** The logical, Room-independent contents of a Stay Leafy backup. */
@Serializable
data class BackupSnapshot(
    val plants: List<PlantEntity> = emptyList(),
    val notes: List<PlantNoteEntity> = emptyList(),
    val careEvents: List<CareEventEntity> = emptyList(),
    val reminderRules: List<ReminderRuleEntity> = emptyList(),
    val notices: List<NoticeEntity> = emptyList(),
    val preferences: SettingsPreferences = SettingsPreferences(),
)

@Serializable
data class BackupArchiveEntry(
    val path: String,
    val size: Long,
    val sha256: String,
)

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val versionName: String,
    val versionCode: Long,
    val roomSchemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val counts: BackupCounts,
    val entries: List<BackupArchiveEntry>,
)

data class DecodedBackup(
    val manifest: BackupManifest,
    val snapshot: BackupSnapshot,
    val photos: Map<String, java.io.File>,
)
