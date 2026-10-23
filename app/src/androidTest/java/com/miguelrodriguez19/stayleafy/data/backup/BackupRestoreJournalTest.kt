package com.miguelrodriguez19.stayleafy.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.local.database.DatabaseBackupSnapshot
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreJournalTest {
    @Test
    fun preparedJournalSurvivesRecreatedProcessAndCommitsDurably() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.noBackupFilesDir, "journal-test-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        try {
            val files = BackupFiles(directory, directory)
            val first = BackupRestoreJournal(files)
            first.writePrepared(
                oldSnapshot = DatabaseBackupSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
                oldPreferences = SettingsPreferences(),
                newPhotoPaths = listOf("plant_photos/new-photo.jpg"),
            )

            val recreated = BackupRestoreJournal(files)
            assertEquals(BackupJournalState.PREPARED, recreated.read()?.state)
            recreated.markCommitted()
            assertEquals(BackupJournalState.COMMITTED, BackupRestoreJournal(files).read()?.state)
            recreated.clear()
            assertFalse(File(directory, "backup-restore.journal").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun assertTrue(value: Boolean) {
        check(value) { "Expected test directory to be created" }
    }
}
