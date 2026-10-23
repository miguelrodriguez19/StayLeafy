package com.miguelrodriguez19.stayleafy.data.backup

import android.util.AtomicFile
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.data.local.database.DatabaseBackupSnapshot
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.data.settings.AppLanguage
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class BackupJournalState { PREPARED, COMMITTED }

data class BackupJournalRecord(
    val state: BackupJournalState,
    val oldSnapshot: DatabaseBackupSnapshot,
    val oldPreferences: SettingsPreferences,
    val newPhotoPaths: List<String>,
)

/** Small, dependency-free, durable journal used before replacing the local collection. */
@Singleton
class BackupRestoreJournal @Inject constructor(
    files: BackupFiles,
) {
    private val journalFile = File(files.noBackupDirectory, JOURNAL_FILE_NAME)
    private val atomicFile = AtomicFile(journalFile)

    @Synchronized
    fun writePrepared(
        oldSnapshot: DatabaseBackupSnapshot,
        oldPreferences: SettingsPreferences,
        newPhotoPaths: List<String>,
    ) {
        write(
            BackupJournalRecord(
                state = BackupJournalState.PREPARED,
                oldSnapshot = oldSnapshot,
                oldPreferences = oldPreferences,
                newPhotoPaths = newPhotoPaths,
            ),
        )
    }

    @Synchronized
    fun markCommitted() {
        val current = read() ?: throw IllegalStateException("Cannot commit a missing backup journal")
        write(current.copy(state = BackupJournalState.COMMITTED))
    }

    @Synchronized
    fun read(): BackupJournalRecord? {
        val input = try {
            DataInputStream(atomicFile.openRead())
        } catch (_: java.io.FileNotFoundException) {
            if (journalFile.exists()) throw IOException("Unable to read backup recovery journal")
            return null
        }
        return input.use { stream ->
            try {
                require(stream.readInt() == MAGIC) { "Invalid backup recovery journal" }
                require(stream.readInt() == FORMAT_VERSION) { "Unsupported backup recovery journal" }
                val state = BackupJournalState.entries.getOrNull(stream.readInt())
                    ?: error("Invalid backup recovery journal state")
                val snapshot = readSnapshot(stream)
                val preferences = readPreferences(stream)
                val photoCount = stream.readBoundedCount(MAX_PHOTO_PATHS)
                val photoPaths = List(photoCount) { readString(stream, MAX_PATH_LENGTH) }
                BackupJournalRecord(state, snapshot, preferences, photoPaths)
            } catch (error: EOFException) {
                throw IOException("Truncated backup recovery journal", error)
            } catch (error: IllegalArgumentException) {
                throw IOException("Corrupt backup recovery journal", error)
            }
        }
    }

    @Synchronized
    fun clear() {
        atomicFile.delete()
        check(!journalFile.exists()) { "Unable to clear backup recovery journal" }
    }

    private fun write(record: BackupJournalRecord) {
        val output = atomicFile.startWrite()
        var finished = false
        try {
            val stream = DataOutputStream(output)
            stream.writeInt(MAGIC)
            stream.writeInt(FORMAT_VERSION)
            stream.writeInt(record.state.ordinal)
            writeSnapshot(stream, record.oldSnapshot)
            writePreferences(stream, record.oldPreferences)
            require(record.newPhotoPaths.size <= MAX_PHOTO_PATHS) { "Too many journal photo paths" }
            stream.writeInt(record.newPhotoPaths.size)
            record.newPhotoPaths.forEach { writeString(stream, it, MAX_PATH_LENGTH) }
            stream.flush()
            require(output.channel.position() <= MAX_JOURNAL_BYTES) { "Backup recovery journal is too large" }
            output.fd.sync()
            atomicFile.finishWrite(output)
            finished = true
            check(read() == record) { "Backup recovery journal was not durably published" }
        } catch (error: Throwable) {
            if (!finished) atomicFile.failWrite(output)
            throw error
        }
    }

    private fun writeSnapshot(stream: DataOutputStream, snapshot: DatabaseBackupSnapshot) {
        writeList(stream, snapshot.plants, MAX_ROWS, ::writePlant)
        writeList(stream, snapshot.notes, MAX_ROWS, ::writeNote)
        writeList(stream, snapshot.careEvents, MAX_ROWS, ::writeCareEvent)
        writeList(stream, snapshot.reminderRules, MAX_ROWS, ::writeReminderRule)
        writeList(stream, snapshot.notices, MAX_ROWS, ::writeNotice)
    }

    private fun readSnapshot(stream: DataInputStream): DatabaseBackupSnapshot = DatabaseBackupSnapshot(
        plants = readList(stream, MAX_ROWS, ::readPlant),
        notes = readList(stream, MAX_ROWS, ::readNote),
        careEvents = readList(stream, MAX_ROWS, ::readCareEvent),
        reminderRules = readList(stream, MAX_ROWS, ::readReminderRule),
        notices = readList(stream, MAX_ROWS, ::readNotice),
    )

    private fun writePreferences(stream: DataOutputStream, preferences: SettingsPreferences) {
        stream.writeInt(preferences.theme.ordinal)
        stream.writeInt(preferences.language.ordinal)
        stream.writeInt(preferences.reminderMinuteOfDay)
    }

    private fun readPreferences(stream: DataInputStream): SettingsPreferences {
        val theme = ThemePreference.entries.getOrNull(stream.readInt())
            ?: error("Invalid theme preference in journal")
        val language = AppLanguage.entries.getOrNull(stream.readInt())
            ?: error("Invalid language preference in journal")
        return SettingsPreferences(theme, language, stream.readInt()).also {
            require(it.reminderMinuteOfDay in 0 until SettingsPreferences.MINUTES_PER_DAY)
        }
    }

    private fun writePlant(stream: DataOutputStream, row: PlantEntity) {
        writeString(stream, row.id, MAX_STRING_LENGTH)
        writeString(stream, row.name, MAX_STRING_LENGTH)
        writeString(stream, row.nameSearch, MAX_STRING_LENGTH)
        writeNullableString(stream, row.scientificName)
        writeString(stream, row.scientificNameSearch, MAX_STRING_LENGTH)
        writeNullableString(stream, row.location)
        writeNullableLong(stream, row.acquiredEpochDay)
        writeNullableString(stream, row.description)
        writeNullableString(stream, row.mainPhotoPath)
        stream.writeBoolean(row.isFavorite)
        stream.writeLong(row.createdAt)
        stream.writeLong(row.updatedAt)
    }

    private fun readPlant(stream: DataInputStream) = PlantEntity(
        id = readString(stream, MAX_STRING_LENGTH),
        name = readString(stream, MAX_STRING_LENGTH),
        nameSearch = readString(stream, MAX_STRING_LENGTH),
        scientificName = readNullableString(stream),
        scientificNameSearch = readString(stream, MAX_STRING_LENGTH),
        location = readNullableString(stream),
        acquiredEpochDay = readNullableLong(stream),
        description = readNullableString(stream),
        mainPhotoPath = readNullableString(stream),
        isFavorite = stream.readBoolean(),
        createdAt = stream.readLong(),
        updatedAt = stream.readLong(),
    )

    private fun writeNote(stream: DataOutputStream, row: PlantNoteEntity) {
        writeString(stream, row.id, MAX_STRING_LENGTH)
        writeString(stream, row.plantId, MAX_STRING_LENGTH)
        writeString(stream, row.body, MAX_STRING_LENGTH)
        stream.writeLong(row.createdAt)
        stream.writeLong(row.updatedAt)
    }

    private fun readNote(stream: DataInputStream) = PlantNoteEntity(
        readString(stream, MAX_STRING_LENGTH),
        readString(stream, MAX_STRING_LENGTH),
        readString(stream, MAX_STRING_LENGTH),
        stream.readLong(),
        stream.readLong(),
    )

    private fun writeCareEvent(stream: DataOutputStream, row: CareEventEntity) {
        writeString(stream, row.id, MAX_STRING_LENGTH)
        writeString(stream, row.plantId, MAX_STRING_LENGTH)
        writeString(stream, row.careType, MAX_STRING_LENGTH)
        stream.writeLong(row.performedAt)
        writeNullableString(stream, row.note)
        stream.writeLong(row.createdAt)
        stream.writeLong(row.updatedAt)
    }

    private fun readCareEvent(stream: DataInputStream) = CareEventEntity(
        readString(stream, MAX_STRING_LENGTH),
        readString(stream, MAX_STRING_LENGTH),
        readString(stream, MAX_STRING_LENGTH),
        stream.readLong(),
        readNullableString(stream),
        stream.readLong(),
        stream.readLong(),
    )

    private fun writeReminderRule(stream: DataOutputStream, row: ReminderRuleEntity) {
        writeString(stream, row.id, MAX_STRING_LENGTH)
        writeString(stream, row.plantId, MAX_STRING_LENGTH)
        writeString(stream, row.careType, MAX_STRING_LENGTH)
        writeString(stream, row.scheduleType, MAX_STRING_LENGTH)
        stream.writeBoolean(row.enabled)
        writeNullableInt(stream, row.intervalDays)
        writeNullableLong(stream, row.nextDueEpochDay)
        writeNullableInt(stream, row.seasonStartMonth)
        writeNullableInt(stream, row.seasonStartDay)
        writeNullableInt(stream, row.seasonEndMonth)
        writeNullableInt(stream, row.seasonEndDay)
        stream.writeLong(row.createdAt)
        stream.writeLong(row.updatedAt)
    }

    private fun readReminderRule(stream: DataInputStream) = ReminderRuleEntity(
        readString(stream, MAX_STRING_LENGTH),
        readString(stream, MAX_STRING_LENGTH),
        readString(stream, MAX_STRING_LENGTH),
        readString(stream, MAX_STRING_LENGTH),
        stream.readBoolean(),
        readNullableInt(stream),
        readNullableLong(stream),
        readNullableInt(stream),
        readNullableInt(stream),
        readNullableInt(stream),
        readNullableInt(stream),
        stream.readLong(),
        stream.readLong(),
    )

    private fun writeNotice(stream: DataOutputStream, row: NoticeEntity) {
        writeString(stream, row.id, MAX_STRING_LENGTH)
        writeString(stream, row.ruleId, MAX_STRING_LENGTH)
        writeString(stream, row.occurrenceKey, MAX_STRING_LENGTH)
        writeString(stream, row.careType, MAX_STRING_LENGTH)
        stream.writeLong(row.dueEpochDay)
        stream.writeLong(row.createdAt)
        writeNullableLong(stream, row.readAt)
        writeString(stream, row.deliveryState, MAX_STRING_LENGTH)
    }

    private fun readNotice(stream: DataInputStream) = NoticeEntity(
        readString(stream, MAX_STRING_LENGTH),
        readString(stream, MAX_STRING_LENGTH),
        readString(stream, MAX_STRING_LENGTH),
        readString(stream, MAX_STRING_LENGTH),
        stream.readLong(),
        stream.readLong(),
        readNullableLong(stream),
        readString(stream, MAX_STRING_LENGTH),
    )

    private fun writeNullableString(stream: DataOutputStream, value: String?) {
        stream.writeBoolean(value != null)
        if (value != null) writeString(stream, value, MAX_STRING_LENGTH)
    }

    private fun readNullableString(stream: DataInputStream): String? =
        if (stream.readBoolean()) readString(stream, MAX_STRING_LENGTH) else null

    private fun writeNullableLong(stream: DataOutputStream, value: Long?) {
        stream.writeBoolean(value != null)
        if (value != null) stream.writeLong(value)
    }

    private fun readNullableLong(stream: DataInputStream): Long? =
        if (stream.readBoolean()) stream.readLong() else null

    private fun writeNullableInt(stream: DataOutputStream, value: Int?) {
        stream.writeBoolean(value != null)
        if (value != null) stream.writeInt(value)
    }

    private fun readNullableInt(stream: DataInputStream): Int? =
        if (stream.readBoolean()) stream.readInt() else null

    private fun <T> writeList(
        stream: DataOutputStream,
        values: List<T>,
        max: Int,
        writeValue: (DataOutputStream, T) -> Unit,
    ) {
        require(values.size <= max) { "Too many rows in recovery journal" }
        stream.writeInt(values.size)
        values.forEach { writeValue(stream, it) }
    }

    private fun <T> readList(
        stream: DataInputStream,
        max: Int,
        readValue: (DataInputStream) -> T,
    ): List<T> {
        val count = stream.readBoundedCount(max)
        return List(count) { readValue(stream) }
    }

    private fun writeString(stream: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes) { "Recovery journal string is too large" }
        stream.writeInt(bytes.size)
        stream.write(bytes)
    }

    private fun readString(stream: DataInputStream, maxBytes: Int): String {
        val size = stream.readInt()
        require(size in 0..maxBytes) { "Invalid recovery journal string length" }
        return String(ByteArray(size).also(stream::readFully), Charsets.UTF_8)
    }

    private fun streamReadNullableLong(stream: DataInputStream): Long? = readNullableLong(stream)

    private fun DataInputStream.readBoundedCount(max: Int): Int {
        val count = readInt()
        require(count in 0..max) { "Invalid recovery journal row count" }
        return count
    }

    private companion object {
        const val JOURNAL_FILE_NAME = "backup-restore.journal"
        const val MAGIC = 0x534C424A
        const val FORMAT_VERSION = 1
        const val MAX_ROWS = 1_000_000
        const val MAX_PHOTO_PATHS = 10_002
        const val MAX_PATH_LENGTH = 4 * 1024
        const val MAX_STRING_LENGTH = 16 * 1024 * 1024
        const val MAX_JOURNAL_BYTES = 128L * 1024L * 1024L
    }
}
