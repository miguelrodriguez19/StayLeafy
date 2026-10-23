package com.miguelrodriguez19.stayleafy.ui.backup

import com.miguelrodriguez19.stayleafy.data.backup.PreparedRestore

enum class BackupOperation {
    EXPORT,
    PREPARE_RESTORE,
    RESTORE,
}

enum class BackupError {
    EXPORT,
    INVALID_BACKUP,
    RESTORE,
}

data class BackupUiState(
    val isBusy: Boolean = false,
    val operation: BackupOperation? = null,
    val preparedRestore: PreparedRestore? = null,
    val error: BackupError? = null,
    val exportCompleted: Boolean = false,
    val restoreCompleted: Boolean = false,
)
