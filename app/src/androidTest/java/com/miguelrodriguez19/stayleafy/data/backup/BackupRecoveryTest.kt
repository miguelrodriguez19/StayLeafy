package com.miguelrodriguez19.stayleafy.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.data.local.database.DatabaseBackupSnapshot
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.data.settings.AppLanguage
import com.miguelrodriguez19.stayleafy.data.settings.AppLocaleController
import com.miguelrodriguez19.stayleafy.data.settings.AppVersionProvider
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.data.settings.SettingsRepository
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import com.miguelrodriguez19.stayleafy.data.photo.FilePhotoFileStore
import com.miguelrodriguez19.stayleafy.data.photo.PhotoContentUriFactory
import com.miguelrodriguez19.stayleafy.data.photo.PhotoFileStore
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderClock
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationGateway
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderScheduler
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderWorkProcessor
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderRunResult
import com.miguelrodriguez19.stayleafy.platform.reminder.RoomReminderDeliveryStore
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRecoveryTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: AppDatabase
    private lateinit var filesRoot: File
    private lateinit var noBackupRoot: File
    private lateinit var cacheRoot: File
    private lateinit var files: BackupFiles
    private lateinit var settings: RecordingSettingsRepository
    private lateinit var uuidProvider: SequenceUuidProvider
    private lateinit var localeController: RecordingLocaleController
    private lateinit var gateway: RecordingGateway
    private lateinit var scheduler: RecordingScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val suffix = UUID.randomUUID().toString()
        databaseName = "backup-recovery-$suffix"
        filesRoot = File(context.filesDir, "backup-recovery-files-$suffix").also { it.mkdirs() }
        noBackupRoot = File(context.noBackupFilesDir, "backup-recovery-private-$suffix").also { it.mkdirs() }
        cacheRoot = File(context.cacheDir, "backup-recovery-cache-$suffix").also { it.mkdirs() }
        files = BackupFiles(noBackupDirectory = noBackupRoot, filesDirectory = filesRoot)
        settings = RecordingSettingsRepository()
        uuidProvider = SequenceUuidProvider()
        localeController = RecordingLocaleController()
        gateway = RecordingGateway()
        scheduler = RecordingScheduler()
        database = openDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
        filesRoot.deleteRecursively()
        noBackupRoot.deleteRecursively()
        cacheRoot.deleteRecursively()
    }

    @Test
    fun preparedJournalRestoresFullDatasetAndKeepsOriginalPhotoAfterRecreatedRepository() = runBlocking {
        val old = oldSnapshot()
        val oldPreferences = oldPreferences()
        val new = newSnapshot()
        val newPreferences = newPreferences()
        database.backupDao().replaceAll(old)
        settings.force(oldPreferences)
        val oldPhoto = stablePhoto("old.jpg", "old-photo")
        val newPhoto = stablePhoto("new.jpg", "new-photo")

        val journal = BackupRestoreJournal(files)
        journal.writePrepared(old, oldPreferences, listOf("plant_photos/new.jpg"))
        database.backupDao().replaceAll(new)
        settings.force(newPreferences)
        assertTrue(newPhoto.exists())
        database.close()

        database = openDatabase()
        val bundle = repositoryBundle()
        start(bundle)

        assertEquals(old, database.backupDao().readSnapshot())
        assertEquals(oldPreferences, settings.preferences.first())
        assertEquals("old-photo", oldPhoto.readText())
        assertFalse(newPhoto.exists())
        assertEquals(AppLanguage.SPANISH, localeController.lastApplied)
    }

    @Test
    fun committedJournalKeepsNewDatasetDeletesOldPhotoAndReappliesSideEffects() = runBlocking {
        val old = oldSnapshot()
        val oldPreferences = oldPreferences()
        val new = newSnapshot()
        val newPreferences = newPreferences()
        database.backupDao().replaceAll(new)
        settings.force(newPreferences)
        val oldPhoto = stablePhoto("old.jpg", "old-photo")
        val newPhoto = stablePhoto("new.jpg", "new-photo")

        val journal = BackupRestoreJournal(files)
        journal.writePrepared(old, oldPreferences, emptyList())
        journal.markCommitted()
        database.close()

        database = openDatabase()
        val bundle = repositoryBundle()
        start(bundle)

        assertEquals(new, database.backupDao().readSnapshot())
        assertEquals(newPreferences, settings.preferences.first())
        assertFalse(oldPhoto.exists())
        assertTrue(newPhoto.exists())
        assertEquals(1, gateway.cancelAllCalls)
        assertEquals(newPreferences.reminderTime, scheduler.lastTarget)
        assertEquals(AppLanguage.ENGLISH, localeController.lastApplied)
    }

    @Test
    fun failedPreferenceReplacementRollsBackRoomPreferencesAndPhotosAndReleasesStaging() = runBlocking {
        val old = oldSnapshot()
        val oldPreferences = oldPreferences()
        val new = newSnapshot()
        val newPreferences = newPreferences()
        database.backupDao().replaceAll(old)
        settings.force(oldPreferences)
        val oldPhoto = stablePhoto("old.jpg", "old-photo")
        val archive = File(cacheRoot, "restore.zip")
        val sourcePhoto = File(cacheRoot, "new-source.jpg").also { it.writeText("new-photo") }
        val archiveSnapshot = new.copy(
            plants = new.plants.map {
                it.copy(nameSearch = it.name.lowercase(), scientificNameSearch = "ficus elastica")
            },
        )
        BackupArchiveCodec().write(
            destination = archive,
            snapshot = BackupSnapshot(
                plants = archiveSnapshot.plants,
                notes = archiveSnapshot.notes,
                careEvents = archiveSnapshot.careEvents,
                reminderRules = archiveSnapshot.reminderRules,
                notices = archiveSnapshot.notices,
                preferences = newPreferences,
            ),
            photos = mapOf("plant_photos/new.jpg" to sourcePhoto),
            versionName = "0.10.0",
            versionCode = 26L,
            exportedAtEpochMillis = 1L,
        )

        val bundle = repositoryBundle()
        start(bundle)
        val prepared = bundle.repository.prepareRestore(Uri.fromFile(archive))
        settings.failNextReplace = true
        val failure = runCatching { bundle.repository.confirmRestore(prepared.token) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(old, database.backupDao().readSnapshot())
        assertEquals(oldPreferences, settings.preferences.first())
        assertEquals("old-photo", oldPhoto.readText())
        assertEquals(listOf("old.jpg"), stablePhotoDirectory().listFiles().orEmpty().map { it.name })
        assertEquals(null, BackupRestoreJournal(files).read())
        assertFalse(
            noBackupRoot.listFiles().orEmpty().any { it.name.startsWith("backup-restore-") },
        )

        // Freeing the imported files also leaves room for a recovery retry after low storage.
        bundle.operations.beginStartup()
        bundle.repository.recoverBeforeUse()
        assertFalse(
            noBackupRoot.listFiles().orEmpty().any { it.name.startsWith("backup-restore-") },
        )
        bundle.repository.discardPreparedRestore(prepared.token)
    }

    @Test
    fun invalidArchiveLeavesFileBackedDatasetPreferencesAndPhotoUntouched() = runBlocking {
        val old = oldSnapshot()
        val oldPreferences = oldPreferences()
        database.backupDao().replaceAll(old)
        settings.force(oldPreferences)
        val oldPhoto = stablePhoto("old.jpg", "old-photo")
        val invalid = File(cacheRoot, "invalid.zip").also { it.writeBytes("not-a-zip".toByteArray()) }
        val bundle = repositoryBundle()
        start(bundle)

        val failure = runCatching {
            bundle.repository.prepareRestore(Uri.fromFile(invalid))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(old, database.backupDao().readSnapshot())
        assertEquals(oldPreferences, settings.preferences.first())
        assertEquals("old-photo", oldPhoto.readText())
        assertTrue(noBackupRoot.listFiles().orEmpty().none { it.name.startsWith("backup-restore-") })
    }

    @Test
    fun startupRecoveryCleansOnlyValidatedAbandonedPrivateArtifacts() = runBlocking {
        val validUuid = "00000000-0000-4000-8000-000000000001"
        val secondValidUuid = "00000000-0000-4000-8000-000000000002"
        val abandonedDirectory = File(noBackupRoot, "backup-restore-$validUuid").also { it.mkdirs() }
        val abandonedArchive = File(noBackupRoot, "backup-export-$validUuid.zip").also { it.writeText("temporary") }
        val abandonedPart = File(
            noBackupRoot,
            ".backup-export-$validUuid.zip.$secondValidUuid.part",
        ).also { it.writeText("temporary") }
        val invalidDirectory = File(noBackupRoot, "backup-restore-not-a-uuid").also { it.mkdirs() }
        val invalidArchive = File(noBackupRoot, "backup-export-not-a-uuid.zip").also { it.writeText("keep") }

        val bundle = repositoryBundle()
        start(bundle)

        assertFalse(abandonedDirectory.exists())
        assertFalse(abandonedArchive.exists())
        assertFalse(abandonedPart.exists())
        assertTrue(invalidDirectory.exists())
        assertTrue(invalidArchive.exists())
    }

    @Test
    fun fullRoundTripRetainsFutureAnnualFertilizingAndNeverReplaysOldNotices() = runBlocking {
        val original = oldSnapshot()
        val annual = ReminderRuleEntity(
            id = "annual-fertilizing", plantId = "old-plant", careType = "FERTILIZING",
            scheduleType = "SEASONAL", enabled = true, intervalDays = null, nextDueEpochDay = null,
            seasonStartMonth = 2, seasonStartDay = 1, createdAt = 12, updatedAt = 13,
        )
        val source = original.copy(
            reminderRules = original.reminderRules.map { it.copy(enabled = false) } + annual,
        )
        database.backupDao().replaceAll(source)
        settings.force(oldPreferences())
        stablePhoto("old.jpg", "original-uncropped-photo")
        val bundle = repositoryBundle()
        start(bundle)
        val archive = File(cacheRoot, "round-trip.zip")
        bundle.repository.exportBackup(Uri.fromFile(archive))
        database.backupDao().replaceAll(
            DatabaseBackupSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
        )
        settings.force(newPreferences())

        val preview = bundle.repository.prepareRestore(Uri.fromFile(archive))
        assertEquals(2, preview.counts.reminderRules)
        bundle.repository.confirmRestore(preview.token)

        val restored = database.backupDao().readSnapshot()
        assertEquals(source.reminderRules.sortedBy { it.id }, restored.reminderRules)
        assertEquals(source.notes, restored.notes)
        assertEquals(source.careEvents, restored.careEvents)
        assertEquals(source.notices, restored.notices)
        assertEquals(oldPreferences(), settings.preferences.first())
        assertEquals(AppLanguage.SPANISH, localeController.lastApplied)
        val restoredPath = requireNotNull(restored.plants.single().mainPhotoPath)
        assertTrue(restoredPath != "plant_photos/old.jpg")
        assertEquals("original-uncropped-photo", File(filesRoot, restoredPath).readText())
        assertEquals(0, gateway.published)

        var day = LocalDate.of(2027, 1, 31)
        val clock = object : ReminderClock {
            override fun instant() = day.atStartOfDay(ZoneId.of("UTC")).toInstant()
            override fun zoneId(): ZoneId = ZoneId.of("UTC")
        }
        val store = RoomReminderDeliveryStore(
            database, database.reminderRuleDao(), database.careEventDao(), database.noticeDao(),
            object : EpochMillisProvider { override fun now() = clock.instant().toEpochMilli() },
            uuidProvider, object : ZoneIdProvider { override fun currentZoneId() = clock.zoneId() },
        )
        val worker = ReminderWorkProcessor(store, gateway, clock, bundle.operations)
        assertEquals(ReminderRunResult.SUCCESS, worker.run())
        assertEquals(0, gateway.published)

        day = LocalDate.of(2027, 2, 1)
        worker.run()
        worker.run()
        assertEquals(1, gateway.published)
        assertEquals(1, database.backupDao().getNotices().count { it.ruleId == annual.id })
        val careTime = clock.instant().toEpochMilli()
        val careEpochDay = day.toEpochDay()
        val careDayStart = day.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val nextDayStart = day.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        database.careEventDao().recordEventsIfNotRecorded(
            events = listOf(
                CareEventEntity("annual-care", "old-plant", "FERTILIZING", careTime, null, careTime, careTime),
            ),
            dayStartInclusive = careDayStart,
            dayEndExclusive = nextDayStart,
            performedEpochDay = careEpochDay,
        )
        assertTrue(store.preparePendingDeliveries(day.toEpochDay()).isEmpty())
        assertNotNull(database.backupDao().getNotices().single { it.ruleId == annual.id }.readAt)

        day = LocalDate.of(2028, 2, 1)
        worker.run()
        worker.run()
        assertEquals(2, gateway.published)
        assertEquals(2, database.backupDao().getNotices().count { it.ruleId == annual.id })
    }

    private suspend fun start(bundle: RepositoryBundle) {
        bundle.operations.beginStartup()
        bundle.repository.recoverBeforeUse()
    }

    private fun repositoryBundle(): RepositoryBundle {
        val operations = DataOperationCoordinator()
        val photoStore: PhotoFileStore = FilePhotoFileStore(
            filesDirectory = filesRoot,
            cacheDirectory = cacheRoot,
            uuidProvider = uuidProvider,
            contentUriFactory = PhotoContentUriFactory { Uri.fromFile(it) },
        )
        return RepositoryBundle(
            operations = operations,
            repository = DefaultBackupRepository(
                context = context,
                backupDao = database.backupDao(),
                settingsRepository = settings,
                appVersionProvider = TestAppVersionProvider,
                photoFileStore = photoStore,
                uuidProvider = uuidProvider,
                reminderClock = FixedReminderClock,
                localeController = localeController,
                reminderNotificationGateway = gateway,
                reminderScheduler = scheduler,
                operations = operations,
                journal = BackupRestoreJournal(files),
                archiveCodec = BackupArchiveCodec(),
                files = files,
            ),
        )
    }

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()

    private fun stablePhoto(name: String, contents: String): File {
        val file = File(stablePhotoDirectory(), name)
        file.parentFile?.mkdirs()
        file.writeText(contents)
        return file
    }

    private fun stablePhotoDirectory(): File = File(filesRoot, "plant_photos").also { it.mkdirs() }

    private fun oldSnapshot() = snapshot("old", "old-rule", NoticeDeliveryState.DELIVERED.name)

    private fun newSnapshot() = snapshot("new", "new-rule", NoticeDeliveryState.PENDING.name)

    private fun snapshot(
        idPrefix: String,
        ruleId: String,
        deliveryState: String,
    ) = DatabaseBackupSnapshot(
        plants = listOf(
            PlantEntity(
                id = "$idPrefix-plant",
                name = "$idPrefix plant",
                nameSearch = "stale-$idPrefix",
                scientificName = "Ficus elastica",
                scientificNameSearch = "stale-scientific-$idPrefix",
                location = "Balcony",
                acquiredEpochDay = 20_000L,
                description = "description-$idPrefix",
                mainPhotoPath = "plant_photos/$idPrefix.jpg",
                isFavorite = idPrefix == "old",
                createdAt = 1L,
                updatedAt = 2L,
            ),
        ),
        notes = listOf(
            PlantNoteEntity("$idPrefix-note", "$idPrefix-plant", "note-$idPrefix", 3L, 4L),
        ),
        careEvents = listOf(
            CareEventEntity(
                "$idPrefix-care",
                "$idPrefix-plant",
                "WATERING",
                5L,
                "care-$idPrefix",
                6L,
                7L,
            ),
        ),
        reminderRules = listOf(
            ReminderRuleEntity(
                id = ruleId,
                plantId = "$idPrefix-plant",
                careType = "WATERING",
                scheduleType = "INTERVAL",
                enabled = true,
                intervalDays = 7,
                nextDueEpochDay = 20_001L,
                createdAt = 8L,
                updatedAt = 9L,
            ),
        ),
        notices = listOf(
            NoticeEntity(
                id = "$idPrefix-notice",
                ruleId = ruleId,
                occurrenceKey = "interval:$ruleId:20001",
                careType = "WATERING",
                dueEpochDay = 20_001L,
                createdAt = 10L,
                readAt = if (deliveryState == NoticeDeliveryState.DELIVERED.name) 11L else null,
                deliveryState = deliveryState,
            ),
        ),
    )

    private fun oldPreferences() = SettingsPreferences(
        theme = ThemePreference.DARK,
        language = AppLanguage.SPANISH,
        reminderMinuteOfDay = 8 * 60,
    )

    private fun newPreferences() = SettingsPreferences(
        theme = ThemePreference.LIGHT,
        language = AppLanguage.ENGLISH,
        reminderMinuteOfDay = 17 * 60,
    )

    private data class RepositoryBundle(
        val operations: DataOperationCoordinator,
        val repository: DefaultBackupRepository,
    )

    private class RecordingSettingsRepository(
        initial: SettingsPreferences = SettingsPreferences(),
    ) : SettingsRepository {
        private val current = MutableStateFlow(initial)
        var failNextReplace = false

        override val preferences: Flow<SettingsPreferences> = current

        fun force(preferences: SettingsPreferences) {
            current.value = preferences
        }

        override suspend fun setTheme(theme: ThemePreference) {
            current.value = current.value.copy(theme = theme)
        }

        override suspend fun setLanguage(language: AppLanguage) {
            current.value = current.value.copy(language = language)
        }

        override suspend fun setReminderTime(time: LocalTime) {
            current.value = current.value.copy(reminderMinuteOfDay = time.hour * 60 + time.minute)
        }

        override suspend fun replacePreferences(preferences: SettingsPreferences) {
            if (failNextReplace) {
                failNextReplace = false
                error("injected settings replacement failure")
            }
            current.value = preferences
        }
    }

    private class SequenceUuidProvider : UuidProvider {
        private var next = 0

        override fun newUuid(): String =
            "00000000-0000-4000-8000-${next++.toString().padStart(12, '0')}"
    }

    private object TestAppVersionProvider : AppVersionProvider {
        override val versionName: String = "0.10.0"
        override val versionCode: Long = 26L
    }

    private object FixedReminderClock : ReminderClock {
        override fun instant(): Instant = Instant.parse("2026-01-10T12:00:00Z")
        override fun zoneId(): ZoneId = ZoneId.of("UTC")
    }

    private class RecordingLocaleController : AppLocaleController {
        var lastApplied: AppLanguage? = null

        override fun apply(language: AppLanguage) {
            lastApplied = language
        }
    }

    private class RecordingGateway : ReminderNotificationGateway {
        var cancelAllCalls = 0
        var published = 0

        override fun ensureChannel() = Unit
        override fun canPostNotifications(): Boolean = true
        override fun publish(delivery: com.miguelrodriguez19.stayleafy.platform.reminder.PendingReminderDelivery):
            com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationResult {
            published++
            return com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationResult.POSTED
        }
        override fun cancel(noticeId: String) = Unit
        override fun cancelAll() {
            cancelAllCalls++
        }
    }

    private class RecordingScheduler : ReminderScheduler {
        var lastTarget: LocalTime? = null

        override fun ensureDailyEvaluationScheduled(targetTime: LocalTime) {
            lastTarget = targetTime
        }
    }
}
