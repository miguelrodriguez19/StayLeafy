package com.miguelrodriguez19.stayleafy.data.backup

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
data class BackupCounts(
    val plants: Int,
    val notes: Int,
    val careEvents: Int,
    val reminderRules: Int,
    val notices: Int,
    val photos: Int,
)

data class PreparedRestore(
    val token: String,
    val exportedAtEpochMillis: Long,
    val sourceVersionName: String,
    val counts: BackupCounts,
)

/** Preparation is read-only. Only explicit confirmation replaces the local collection. */
interface BackupRepository {
    suspend fun exportBackup(uri: Uri)
    suspend fun prepareRestore(uri: Uri): PreparedRestore
    suspend fun confirmRestore(token: String)
    suspend fun discardPreparedRestore(token: String)
}
