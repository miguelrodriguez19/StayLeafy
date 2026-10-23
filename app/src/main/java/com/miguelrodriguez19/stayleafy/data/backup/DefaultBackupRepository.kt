package com.miguelrodriguez19.stayleafy.data.backup

import android.content.Context
import android.net.Uri
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import com.miguelrodriguez19.stayleafy.data.local.database.BackupDao
import com.miguelrodriguez19.stayleafy.data.local.database.DatabaseBackupSnapshot
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.photo.PhotoFileStore
import com.miguelrodriguez19.stayleafy.data.settings.AppLanguage
import com.miguelrodriguez19.stayleafy.data.settings.AppLocaleController
import com.miguelrodriguez19.stayleafy.data.settings.AppVersionProvider
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.data.settings.SettingsRepository
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderClock
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationGateway
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Singleton
class DefaultBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val settingsRepository: SettingsRepository,
    private val appVersionProvider: AppVersionProvider,
    private val photoFileStore: PhotoFileStore,
    private val uuidProvider: UuidProvider,
    private val reminderClock: ReminderClock,
    private val localeController: AppLocaleController,
    private val reminderNotificationGateway: ReminderNotificationGateway,
    private val reminderScheduler: ReminderScheduler,
    private val operations: DataOperationCoordinator,
    private val journal: BackupRestoreJournal,
    private val archiveCodec: BackupArchiveCodec,
    private val files: BackupFiles,
) : BackupRepository {
    private val contentResolver = context.contentResolver
    private val preparedRestores = ConcurrentHashMap<String, PreparedRestoreState>()
    private val activeExportFiles = ConcurrentHashMap.newKeySet<File>()
    private val activeRestoreDirectories = ConcurrentHashMap.newKeySet<File>()
    private val privateArtifactLock = Any()

    override suspend fun exportBackup(uri: Uri) {
        // Capture and ZIP creation are protected; copying to a potentially slow SAF provider is
        // intentionally outside the gate.
        val temporary = File(files.noBackupDirectory, "backup-export-${uuidProvider.newUuid()}.zip")
        try {
            operations.withOperation {
                synchronized(privateArtifactLock) {
                    check(activeExportFiles.add(temporary.canonicalFile)) {
                        "Backup export is already active"
                    }
                }
                withContext(Dispatchers.IO) {
                    val preferences = settingsRepository.snapshotPreferences()
                    val database = backupDao.readSnapshot()
                    val photos = database.plants.mapNotNull { plant ->
                        plant.mainPhotoPath?.let { path ->
                            path to (photoFileStore.resolve(path)
                                ?: error("Referenced plant photo is missing: $path"))
                        }
                    }.toMap()
                    archiveCodec.write(
                        destination = temporary,
                        snapshot = BackupSnapshot(
                            plants = database.plants,
                            notes = database.notes,
                            careEvents = database.careEvents,
                            reminderRules = database.reminderRules,
                            notices = database.notices,
                            preferences = preferences,
                        ),
                        photos = photos,
                        versionName = appVersionProvider.versionName,
                        versionCode = appVersionProvider.versionCode,
                        exportedAtEpochMillis = System.currentTimeMillis(),
                    )
                }
            }
            copyToUri(temporary, uri)
        } finally {
            synchronized(privateArtifactLock) { activeExportFiles.remove(temporary.canonicalFile) }
            temporary.delete()
        }
    }

    override suspend fun prepareRestore(uri: Uri): PreparedRestore = withContext(Dispatchers.IO) {
        val token = uuidProvider.newUuid()
        val directory = privateChild("backup-restore-$token")
        synchronized(privateArtifactLock) {
            require(directory.mkdirs()) { "Unable to create private restore staging directory" }
            check(activeRestoreDirectories.add(directory.canonicalFile)) {
                "Restore staging directory is already active"
            }
        }
        val source = File(directory, "source.zip")
        try {
            copyFromUri(uri, source)
            val decoded = archiveCodec.read(source, File(directory, "decoded"))
            source.delete()
            val prepared = PreparedRestoreState(directory, decoded)
            check(preparedRestores.putIfAbsent(token, prepared) == null) { "Restore token collision" }
            PreparedRestore(token, decoded.manifest.exportedAtEpochMillis, decoded.manifest.versionName, decoded.manifest.counts)
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        } finally {
            synchronized(privateArtifactLock) { activeRestoreDirectories.remove(directory.canonicalFile) }
        }
    }

    override suspend fun confirmRestore(token: String) = withContext(NonCancellable + Dispatchers.IO) {
        val prepared = preparedRestores[token] ?: throw IllegalArgumentException("Unknown restore token")
        check(prepared.claimed.compareAndSet(false, true)) {
            "Restore token is already being confirmed or discarded"
        }
        try {
            operations.withMaintenance { applyRestore(prepared) }
        } finally {
            // Confirmation consumes the preview. Neither PREPARED rollback nor COMMITTED
            // recovery needs the imported staging files; release them even after a failure.
            if (preparedRestores.remove(token, prepared)) {
                prepared.directory.deleteRecursively()
            }
        }
    }

    override suspend fun discardPreparedRestore(token: String) = withContext(NonCancellable + Dispatchers.IO) {
        val prepared = preparedRestores[token] ?: return@withContext
        check(prepared.claimed.compareAndSet(false, true)) {
            "Restore token is already being confirmed or discarded"
        }
        if (preparedRestores.remove(token, prepared)) {
            prepared.directory.deleteRecursively()
        }
    }

    /** Application calls this after [DataOperationCoordinator.beginStartup()]. */
    suspend fun recoverBeforeUse() {
        operations.initialize {
            recoverJournal()
            cleanupStalePrivateArtifacts()
        }
    }

    private fun cleanupStalePrivateArtifacts() = synchronized(privateArtifactLock) {
        val root = files.noBackupDirectory.canonicalFile
        val children = root.listFiles() ?: error("Unable to inspect private backup directory")
        val activeDirectories = preparedRestores.values
            .map { it.directory.canonicalFile }
            .toSet() + activeRestoreDirectories
        val activeExports = activeExportFiles.toSet()

        children.forEach { candidate ->
            val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return@forEach
            val directChild = File(root, candidate.name)
            if (canonical.parentFile != root || canonical != directChild.absoluteFile) {
                return@forEach
            }

            when {
                candidate.isDirectory && validUuidSuffix(
                    candidate.name.removePrefix(RESTORE_DIRECTORY_PREFIX),
                ) && candidate.name.startsWith(RESTORE_DIRECTORY_PREFIX) -> {
                    if (canonical !in activeDirectories) {
                        check(candidate.deleteRecursively()) {
                            "Unable to remove abandoned backup staging directory"
                        }
                    }
                }

                candidate.isFile && isValidatedExportTemporaryName(candidate.name) -> {
                    if (canonical !in activeExports) {
                        check(candidate.delete()) { "Unable to remove abandoned backup archive" }
                    }
                }
            }
        }
    }

    private fun validUuidSuffix(value: String): Boolean =
        UUID_REGEX.matches(value) && UUID.fromString(value).toString().equals(value, ignoreCase = true)

    private fun isValidatedExportTemporaryName(name: String): Boolean {
        val archiveUuid = name
            .removePrefix(EXPORT_FILE_PREFIX)
            .removeSuffix(EXPORT_FILE_SUFFIX)
        if (name.startsWith(EXPORT_FILE_PREFIX) && name.endsWith(EXPORT_FILE_SUFFIX)) {
            return validUuidSuffix(archiveUuid)
        }
        val match = EXPORT_PART_REGEX.matchEntire(name) ?: return false
        return validUuidSuffix(match.groupValues[1]) && validUuidSuffix(match.groupValues[2])
    }

    private suspend fun applyRestore(prepared: PreparedRestoreState) = withContext(Dispatchers.IO) {
        val oldSnapshot = backupDao.readSnapshot()
        val oldPreferences = settingsRepository.snapshotPreferences()
        val today = LocalDate.ofInstant(reminderClock.instant(), reminderClock.zoneId()).toEpochDay()
        val plan = planPhotoPaths(prepared.decoded.photos)
        val replacement = prepared.decoded.snapshot.toDatabaseSnapshot(plan, today)
        val newPreferences = prepared.decoded.snapshot.preferences
        ensureSpace(plan.values.map { it.file }, oldSnapshot, replacement)

        // Targets are checked absent before this durable PREPARED record is written.
        journal.writePrepared(oldSnapshot, oldPreferences, plan.values.map { it.relativePath })
        try {
            plan.values.forEach { copyIntoStable(it.file, it.relativePath) }
            backupDao.replaceAll(replacement)
            settingsRepository.replacePreferences(newPreferences)
            journal.markCommitted()

            // The journal remains COMMITTED until all process/platform side effects and old-file
            // cleanup finish successfully. Startup will retry this block after a crash/failure.
            applyLocale(newPreferences.language)
            reminderNotificationGateway.cancelAll()
            reminderScheduler.ensureDailyEvaluationScheduled(newPreferences.reminderTime)
            deletePhotoPaths(oldSnapshot.photoPaths() - replacement.photoPaths())
            journal.clear()
        } catch (error: Throwable) {
            if (journal.read()?.state == BackupJournalState.PREPARED) {
                rollbackPrepared(oldSnapshot, oldPreferences, plan.values.map { it.relativePath }, error)
            }
            throw error
        }
    }

    private suspend fun recoverJournal() = withContext(Dispatchers.IO) {
        val record = journal.read() ?: return@withContext
        when (record.state) {
                BackupJournalState.PREPARED -> {
                    backupDao.replaceAll(record.oldSnapshot)
                    settingsRepository.replacePreferences(record.oldPreferences)
                    applyLocale(record.oldPreferences.language)
                    deletePhotoPaths(record.newPhotoPaths.toSet())
                    journal.clear()
                }

                BackupJournalState.COMMITTED -> {
                    val current = backupDao.readSnapshot()
                    val currentPreferences = settingsRepository.snapshotPreferences()
                    applyLocale(currentPreferences.language)
                    reminderNotificationGateway.cancelAll()
                    reminderScheduler.ensureDailyEvaluationScheduled(currentPreferences.reminderTime)
                    deletePhotoPaths(record.oldSnapshot.photoPaths() - current.photoPaths())
                    journal.clear()
                }
        }
    }

    private suspend fun rollbackPrepared(
        oldSnapshot: DatabaseBackupSnapshot,
        oldPreferences: SettingsPreferences,
        newPhotoPaths: List<String>,
        originalError: Throwable,
    ) {
        try {
            backupDao.replaceAll(oldSnapshot)
            settingsRepository.replacePreferences(oldPreferences)
            applyLocale(oldPreferences.language)
            deletePhotoPaths(newPhotoPaths.toSet())
            journal.clear()
        } catch (rollbackError: Throwable) {
            originalError.addSuppressed(rollbackError)
        }
    }

    private suspend fun applyLocale(language: AppLanguage) {
        withContext(Dispatchers.Main) { localeController.apply(language) }
    }

    private suspend fun deletePhotoPaths(paths: Set<String>) {
        for (path in paths) {
            currentCoroutineContext().ensureActive()
            require(isStablePhotoPath(path)) { "Invalid stable photo path in recovery journal" }
            val removed = photoFileStore.remove(path)
            if (!removed && photoFileStore.resolve(path)?.exists() == true) {
                error("Unable to remove old photo: $path")
            }
        }
    }

    private suspend fun copyFromUri(uri: Uri, destination: File) {
        val input = contentResolver.openInputStream(uri) ?: error("Unable to read backup document")
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    copied += read
                    require(copied <= MAX_TOTAL_BYTES) { "Backup archive is too large" }
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
    }

    private suspend fun copyToUri(source: File, destination: Uri) {
        val output = contentResolver.openOutputStream(destination) ?: error("Unable to write backup document")
        try {
            output.use { target ->
                FileInputStream(source).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        target.write(buffer, 0, read)
                    }
                }
            }
        } catch (error: Throwable) {
            runCatching { contentResolver.delete(destination, null, null) }
            throw error
        }
    }

    private fun planPhotoPaths(photos: Map<String, File>): Map<String, PlannedPhoto> = buildMap {
        photos.forEach { (originalPath, stagedFile) ->
            require(isStablePhotoPath(originalPath)) { "Invalid source photo path: $originalPath" }
            val extension = originalPath.substringAfterLast('.', "jpg").lowercase(Locale.ROOT)
            require(extension.matches(SAFE_EXTENSION)) { "Invalid source photo extension" }
            val relative = "plant_photos/backup-${uuidProvider.newUuid().replace("-", "")}.${extension}"
            require(!stableFile(relative).exists()) { "Generated restore photo path already exists" }
            put(originalPath, PlannedPhoto(stagedFile, relative))
        }
    }

    private fun copyIntoStable(source: File, relativePath: String) {
        val target = stableFile(relativePath)
        require(!target.exists()) { "Restore photo target already exists" }
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.${uuidProvider.newUuid()}.part")
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            require(temporary.renameTo(target)) { "Unable to publish restored photo" }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private fun ensureSpace(
        photos: Collection<File>,
        oldSnapshot: DatabaseBackupSnapshot,
        replacement: DatabaseBackupSnapshot,
    ) {
        val photoBytes = photos.fold(0L) { total, photo -> Math.addExact(total, photo.length()) }
        val oldLogicalBytes = oldSnapshot.estimatedLogicalBytes()
        val replacementLogicalBytes = replacement.estimatedLogicalBytes()
        // Reserve for the new photos, the durable old-snapshot journal, and Room's replacement
        // transaction plus WAL. Exact SQLite growth is device/filesystem dependent, so the
        // logical payload is deliberately charged twice for database/WAL overhead.
        val journalBytes = Math.addExact(oldLogicalBytes, JOURNAL_OVERHEAD_BYTES)
        val databaseAndWalBytes = Math.multiplyExact(
            Math.addExact(oldLogicalBytes, replacementLogicalBytes),
            ROOM_STORAGE_OVERHEAD_MULTIPLIER,
        )
        val required = checkedAdd(
            MIN_FREE_SPACE_BYTES,
            Math.multiplyExact(photoBytes, PHOTO_STORAGE_OVERHEAD_MULTIPLIER),
            journalBytes,
            databaseAndWalBytes,
        )
        require(files.filesDirectory.usableSpace >= required) { "Insufficient free space for restore" }
    }

    private fun checkedAdd(vararg values: Long): Long = values.fold(0L) { total, value ->
        Math.addExact(total, value)
    }

    private fun DatabaseBackupSnapshot.estimatedLogicalBytes(): Long {
        fun textBytes(value: String?): Long = Math.addExact(
            4L,
            value?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L,
        )

        fun add(vararg values: Long): Long = values.fold(0L) { total, value ->
            Math.addExact(total, value)
        }

        val plantsBytes = plants.fold(0L) { total, row ->
            add(
                total,
                64L,
                textBytes(row.id),
                textBytes(row.name),
                textBytes(row.nameSearch),
                textBytes(row.scientificName),
                textBytes(row.scientificNameSearch),
                textBytes(row.location),
                textBytes(row.description),
                textBytes(row.mainPhotoPath),
            )
        }
        val notesBytes = notes.fold(0L) { total, row ->
            add(total, 40L, textBytes(row.id), textBytes(row.plantId), textBytes(row.body))
        }
        val careEventsBytes = careEvents.fold(0L) { total, row ->
            add(total, 48L, textBytes(row.id), textBytes(row.plantId), textBytes(row.careType), textBytes(row.note))
        }
        val rulesBytes = reminderRules.fold(0L) { total, row ->
            add(
                total,
                64L,
                textBytes(row.id),
                textBytes(row.plantId),
                textBytes(row.careType),
                textBytes(row.scheduleType),
            )
        }
        val noticesBytes = notices.fold(0L) { total, row ->
            add(
                total,
                48L,
                textBytes(row.id),
                textBytes(row.ruleId),
                textBytes(row.occurrenceKey),
                textBytes(row.careType),
                textBytes(row.deliveryState),
            )
        }
        return add(32L, plantsBytes, notesBytes, careEventsBytes, rulesBytes, noticesBytes)
    }

    private fun stableFile(relativePath: String): File {
        require(isStablePhotoPath(relativePath)) { "Invalid stable photo path: $relativePath" }
        val root = File(files.filesDirectory, "plant_photos").canonicalFile
        val target = File(files.filesDirectory, relativePath).canonicalFile
        require(target.parentFile == root) { "Photo path escapes stable directory" }
        return target
    }

    private fun privateChild(name: String): File {
        val root = files.noBackupDirectory.canonicalFile
        val child = File(root, name).canonicalFile
        require(child.parentFile == root) { "Invalid private backup path" }
        return child
    }

    private fun isStablePhotoPath(path: String): Boolean =
        path.startsWith("plant_photos/") &&
            path.removePrefix("plant_photos/").matches(SAFE_STABLE_FILE)

    private fun BackupSnapshot.toDatabaseSnapshot(
        photoPlan: Map<String, PlannedPhoto>,
        todayEpochDay: Long,
    ): DatabaseBackupSnapshot {
        val referenced = plants.mapNotNull { it.mainPhotoPath }.toSet()
        require(referenced == photoPlan.keys) { "Backup photo paths do not match plant references" }
        return DatabaseBackupSnapshot(
            plants = plants.map { plant ->
                plant.copy(
                    nameSearch = plant.name.lowercase(Locale.ROOT),
                    scientificNameSearch = plant.scientificName?.lowercase(Locale.ROOT).orEmpty(),
                    mainPhotoPath = plant.mainPhotoPath?.let { photoPlan.getValue(it).relativePath },
                )
            },
            notes = notes,
            careEvents = careEvents,
            reminderRules = reminderRules,
            notices = notices.map { notice ->
                if (notice.deliveryState == NoticeDeliveryState.PENDING.name && notice.dueEpochDay <= todayEpochDay) {
                    NoticeEntity(
                        notice.id, notice.ruleId, notice.occurrenceKey, notice.careType,
                        notice.dueEpochDay, notice.createdAt, notice.readAt,
                        NoticeDeliveryState.SUPPRESSED.name,
                    )
                } else notice
            },
        )
    }

    private fun DatabaseBackupSnapshot.photoPaths(): Set<String> =
        plants.mapNotNull { it.mainPhotoPath }.toSet()

    private fun DatabaseBackupSnapshot.toBackupSnapshot(preferences: SettingsPreferences) = BackupSnapshot(
        plants = plants,
        notes = notes,
        careEvents = careEvents,
        reminderRules = reminderRules,
        notices = notices,
        preferences = preferences,
    )

    private data class PreparedRestoreState(
        val directory: File,
        val decoded: DecodedBackup,
        val claimed: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false),
    )

    private data class PlannedPhoto(val file: File, val relativePath: String)

    private companion object {
        const val RESTORE_DIRECTORY_PREFIX = "backup-restore-"
        const val EXPORT_FILE_PREFIX = "backup-export-"
        const val EXPORT_FILE_SUFFIX = ".zip"
        val UUID_REGEX = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}",
        )
        val EXPORT_PART_REGEX = Regex(
            "\\.backup-export-(${UUID_REGEX.pattern})\\.zip\\.(${UUID_REGEX.pattern})\\.part",
        )
        val SAFE_STABLE_FILE = Regex("[A-Za-z0-9-]{1,128}\\.[a-z0-9]{1,16}")
        val SAFE_EXTENSION = Regex("[a-z0-9]{1,16}")
        const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L
        const val MIN_FREE_SPACE_BYTES = 64L * 1024L
        const val JOURNAL_OVERHEAD_BYTES = 4L * 1024L
        const val PHOTO_STORAGE_OVERHEAD_MULTIPLIER = 2L
        const val ROOM_STORAGE_OVERHEAD_MULTIPLIER = 2L
        const val BUFFER_SIZE = 32 * 1024
    }
}
