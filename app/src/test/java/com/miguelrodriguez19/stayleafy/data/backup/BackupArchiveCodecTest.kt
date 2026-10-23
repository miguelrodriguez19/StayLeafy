package com.miguelrodriguez19.stayleafy.data.backup

import com.miguelrodriguez19.stayleafy.data.local.database.CareEventEntity
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantEntity
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteEntity
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.data.local.database.SEASONAL_SCHEDULE_TYPE
import com.miguelrodriguez19.stayleafy.data.settings.AppLanguage
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.data.settings.ThemePreference
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveCodecTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun canonicalParentAliasesAreAllowedButArchiveSymlinksAreRejected() = runBlocking<Unit> {
        val actual = temporaryFolder.newFolder("actual")
        assertTrue(File(actual, "nested").mkdir())
        val alias = File(temporaryFolder.root, "parent-alias")
        java.nio.file.Files.createSymbolicLink(alias.toPath(), actual.toPath())
        val archive = File(alias, "nested/backup.zip")
        val codec = BackupArchiveCodec()

        codec.write(archive, BackupSnapshot(), emptyMap(), "0.11.0", 27, 1)
        assertEquals(BackupSnapshot(), codec.read(archive, File(actual, "decoded")).snapshot)

        val archiveLink = File(actual, "archive-link.zip")
        java.nio.file.Files.createSymbolicLink(archiveLink.toPath(), archive.canonicalFile.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { codec.read(archiveLink, File(actual, "rejected")) }
        }
    }

    private val codec = BackupArchiveCodec()

    @Test
    fun roundTripPreservesLogicalDataAndRecomputesSearchFields() = runBlocking<Unit> {
        val photoBytes = byteArrayOf(0, 1, 2, 3, 127, -1, 42)
        val photo = temporaryFolder.newFile("legacy-original.jpg").also { it.writeBytes(photoBytes) }
        val snapshot = sampleSnapshot(
            plant = samplePlant(
                name = "Monstera Deliciosa",
                nameSearch = "stale value",
                scientificName = "Monstera deliciosa",
                scientificNameSearch = "also stale",
                mainPhotoPath = "plant_photos/legacy-original.jpg",
            ),
        )
        val archive = temporaryFolder.newFile("backup.zip").also { it.delete() }

        val manifest = codec.write(
            destination = archive,
            snapshot = snapshot,
            photos = mapOf("plant_photos/legacy-original.jpg" to photo),
            versionName = "0.11.0",
            versionCode = 27,
            exportedAtEpochMillis = 1_700_000_000_000,
        )
        val decoded = codec.read(archive, temporaryFolder.newFolder("restore"))

        assertEquals(1, manifest.counts.plants)
        assertEquals(1, manifest.counts.photos)
        assertEquals("monstera deliciosa", decoded.snapshot.plants.single().nameSearch)
        assertEquals("monstera deliciosa", decoded.snapshot.plants.single().scientificNameSearch)
        assertEquals(snapshot.reminderRules, decoded.snapshot.reminderRules)
        assertEquals(snapshot.preferences, decoded.snapshot.preferences)
        assertArrayEquals(photoBytes, decoded.photos.getValue("plant_photos/legacy-original.jpg").readBytes())
    }

    @Test
    fun futureAnnualFertilizingRuleAndNoticeStateArePreserved() = runBlocking<Unit> {
        val rule = ReminderRuleEntity(
            id = "rule-annual",
            plantId = "plant-1",
            careType = "FERTILIZING",
            scheduleType = SEASONAL_SCHEDULE_TYPE,
            enabled = true,
            intervalDays = null,
            nextDueEpochDay = null,
            seasonStartMonth = 12,
            seasonStartDay = 15,
            seasonEndMonth = 1,
            seasonEndDay = 15,
            createdAt = 10,
            updatedAt = 11,
        )
        val notice = NoticeEntity(
            id = "notice-1",
            ruleId = rule.id,
            occurrenceKey = "seasonal:${rule.id}:21167",
            careType = "FERTILIZING",
            dueEpochDay = 21_167,
            createdAt = 12,
            readAt = null,
            deliveryState = "PENDING",
        )
        val snapshot = sampleSnapshot(
            reminderRules = listOf(rule),
            notices = listOf(notice),
        )
        val archive = temporaryFolder.newFile("annual.zip").also { it.delete() }
        codec.write(archive, snapshot, emptyMap(), "0.11.0", 27, 1)

        val restored = codec.read(archive, temporaryFolder.newFolder("annual-restore"))

        assertEquals(rule, restored.snapshot.reminderRules.single())
        assertEquals("PENDING", restored.snapshot.notices.single().deliveryState)
        assertEquals("seasonal:rule-annual:21167", restored.snapshot.notices.single().occurrenceKey)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                codec.write(
                    temporaryFolder.newFile("invalid-occurrence.zip").also { it.delete() },
                    snapshot.copy(notices = listOf(notice.copy(occurrenceKey = "seasonal:${rule.id}:21168"))),
                    emptyMap(),
                    "0.11.0",
                    27,
                    1,
                )
            }
        }
    }

    @Test
    fun rejectsForeignPlantReferenceAndDuplicateRuleKey() = runBlocking<Unit> {
        val foreignNote = PlantNoteEntity("note-1", "missing-plant", "orphan", 1, 2)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                codec.write(
                    temporaryFolder.newFile("invalid.zip").also { it.delete() },
                    sampleSnapshot(notes = listOf(foreignNote)),
                    emptyMap(),
                    "0.11.0",
                    27,
                    1,
                )
            }
        }

        val duplicateEnabledCareRules = listOf(
            intervalRule("rule-enabled-1"),
            intervalRule("rule-enabled-2", nextDueEpochDay = 101).copy(scheduleType = SEASONAL_SCHEDULE_TYPE, intervalDays = null, nextDueEpochDay = null, seasonStartMonth = 3, seasonStartDay = 1),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                codec.write(
                    temporaryFolder.newFile("duplicate-enabled.zip").also { it.delete() },
                    sampleSnapshot(reminderRules = duplicateEnabledCareRules),
                    emptyMap(),
                    "0.11.0",
                    27,
                    1,
                )
            }
        }

        val duplicateKeyRules = listOf(
            intervalRule("rule-1", nextDueEpochDay = 100),
            intervalRule("rule-2", nextDueEpochDay = 101),
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                codec.write(
                    temporaryFolder.newFile("duplicate.zip").also { it.delete() },
                    sampleSnapshot(reminderRules = duplicateKeyRules),
                    emptyMap(),
                    "0.11.0",
                    27,
                    1,
                )
            }
        }
    }

    @Test
    fun rejectsPathTraversalAndMissingDataEntry() = runBlocking<Unit> {
        val traversal = temporaryFolder.newFile("traversal.zip")
        writeZip(traversal, mapOf("../outside" to byteArrayOf(1)))
        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(traversal, temporaryFolder.newFolder("traversal-restore")) }
        }

        val missingData = temporaryFolder.newFile("missing-data.zip")
        writeZip(missingData, mapOf("manifest.json" to "{}".toByteArray()))
        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(missingData, temporaryFolder.newFolder("missing-data-restore")) }
        }
    }

    @Test
    fun rejectsChecksumMismatchAndTruncatedZip() = runBlocking<Unit> {
        val sourcePhoto = temporaryFolder.newFile("photo.jpg").also { it.writeText("photo") }
        val valid = temporaryFolder.newFile("valid.zip").also { it.delete() }
        codec.write(
            valid,
            sampleSnapshot(samplePlant(mainPhotoPath = "plant_photos/photo.jpg")),
            mapOf("plant_photos/photo.jpg" to sourcePhoto),
            "0.11.0",
            27,
            1,
        )

        val corrupted = temporaryFolder.newFile("corrupted.zip")
        ZipFile(valid).use { zip ->
            ZipOutputStream(FileOutputStream(corrupted)).use { output ->
                zip.entries().asSequence().forEach { entry ->
                    output.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "data.json") {
                        output.write("corrupt".toByteArray())
                    } else {
                        zip.getInputStream(entry).use { it.copyTo(output) }
                    }
                    output.closeEntry()
                }
            }
        }
        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(corrupted, temporaryFolder.newFolder("checksum-restore")) }
        }

        val bytes = FileInputStream(valid).use { it.readBytes() }
        val truncated = temporaryFolder.newFile("truncated.zip")
        truncated.writeBytes(bytes.copyOf(bytes.size - 2))
        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(truncated, temporaryFolder.newFolder("truncated-restore")) }
        }
    }

    @Test
    fun rejectsUnknownStrictJsonFieldsAndPhotosArray() = runBlocking<Unit> {
        val malformedData = "{\"plants\":[],\"photos\":[]}".toByteArray()
        val archive = temporaryFolder.newFile("unknown-field.zip")
        writeZip(
            archive,
            mapOf(
                "manifest.json" to manifestJson(malformedData),
                "data.json" to malformedData,
            ),
        )

        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(archive, temporaryFolder.newFolder("unknown-field-restore")) }
        }
    }

    @Test
    fun rejectsManifestCountMismatch() = runBlocking<Unit> {
        val data = kotlinx.serialization.json.Json.encodeToString(
            BackupSnapshot.serializer(),
            BackupSnapshot(),
        ).toByteArray()
        val archive = temporaryFolder.newFile("count-mismatch.zip")
        writeZip(
            archive,
            mapOf(
                "manifest.json" to manifestJson(data, BackupCounts(1, 0, 0, 0, 0, 0)),
                "data.json" to data,
            ),
        )

        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(archive, temporaryFolder.newFolder("count-mismatch-restore")) }
        }
    }

    @Test
    fun rejectsZipCrcCorruption() = runBlocking<Unit> {
        val data = kotlinx.serialization.json.Json.encodeToString(
            BackupSnapshot.serializer(),
            BackupSnapshot(),
        ).toByteArray()
        val archive = temporaryFolder.newFile("crc-corrupt.zip")
        writeStoredZip(
            archive,
            linkedMapOf(
                "manifest.json" to manifestJson(data),
                "data.json" to data,
            ),
        )
        val bytes = archive.readBytes()
        val manifestNameLength = littleEndianUnsignedShort(bytes, 26)
        val manifestExtraLength = littleEndianUnsignedShort(bytes, 28)
        val manifestDataOffset = 30 + manifestNameLength + manifestExtraLength
        bytes[manifestDataOffset] = (bytes[manifestDataOffset].toInt() xor 1).toByte()
        archive.writeBytes(bytes)

        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(archive, temporaryFolder.newFolder("crc-restore")) }
        }
    }

    @Test
    fun rejectsUnreferencedPhotosBlankTextAndOutOfRangeEpochDays() = runBlocking<Unit> {
        val photo = temporaryFolder.newFile("extra.jpg").also { it.writeText("photo") }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                codec.write(
                    temporaryFolder.newFile("extra-photo.zip").also { it.delete() },
                    sampleSnapshot(),
                    mapOf("plant_photos/extra.jpg" to photo),
                    "0.11.0",
                    27,
                    1,
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                codec.write(
                    temporaryFolder.newFile("blank-name.zip").also { it.delete() },
                    sampleSnapshot(plant = samplePlant(name = "   ")),
                    emptyMap(),
                    "0.11.0",
                    27,
                    1,
                )
            }
        }
        assertThrows(BackupArchiveException::class.java) {
            runBlocking {
                codec.write(
                    temporaryFolder.newFile("huge-date.zip").also { it.delete() },
                    sampleSnapshot(plant = samplePlant().copy(acquiredEpochDay = Long.MAX_VALUE)),
                    emptyMap(),
                    "0.11.0",
                    27,
                    1,
                )
            }
        }
    }

    @Test
    fun rejectsSparseSourceAboveTwoGiBBeforeOpeningZip() = runBlocking<Unit> {
        val source = temporaryFolder.newFile("oversized-source.zip")
        RandomAccessFile(source, "rw").use { file ->
            file.setLength(2L * 1024L * 1024L * 1024L + 1L)
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { codec.read(source, temporaryFolder.newFolder("oversized-source-restore")) }
        }
    }

    @Test
    fun rejectsSparsePhotoAboveOneHundredMiBWithoutReadingIt() = runBlocking<Unit> {
        val photo = temporaryFolder.newFile("oversized-photo.jpg")
        RandomAccessFile(photo, "rw").use { file ->
            file.setLength(100L * 1024L * 1024L + 1L)
        }
        val destination = temporaryFolder.newFile("oversized-photo.zip").also { it.delete() }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                codec.write(
                    destination,
                    sampleSnapshot(samplePlant(mainPhotoPath = "plant_photos/oversized-photo.jpg")),
                    mapOf("plant_photos/oversized-photo.jpg" to photo),
                    "0.11.0",
                    27,
                    1,
                )
            }
        }
        assertFalse(destination.exists())
    }

    @Test
    fun rejectsManifestAndDataJustAboveTheirStreamingLimits() = runBlocking<Unit> {
        val oversizedManifest = temporaryFolder.newFile("oversized-manifest.zip")
        writeZeroZip(oversizedManifest, "manifest.json", 8L * 1024L * 1024L + 1L)
        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(oversizedManifest, temporaryFolder.newFolder("oversized-manifest-restore")) }
        }

        val oversizedData = temporaryFolder.newFile("oversized-data.zip")
        writeZeroZip(oversizedData, "data.json", 32L * 1024L * 1024L + 1L)
        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(oversizedData, temporaryFolder.newFolder("oversized-data-restore")) }
        }
    }

    @Test
    fun rejectsMoreThanTenThousandAndTwoZipEntries() = runBlocking<Unit> {
        val archive = temporaryFolder.newFile("too-many-entries.zip")
        ZipOutputStream(FileOutputStream(archive)).use { output ->
            repeat(10_003) { index ->
                output.putNextEntry(ZipEntry("photos/${index.toString().padStart(5, '0')}.jpg"))
                output.write(0)
                output.closeEntry()
            }
        }

        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(archive, temporaryFolder.newFolder("too-many-entries-restore")) }
        }
    }

    @Test
    fun rejectsDuplicateZipEntryAfterEqualLengthRawNamePatch() = runBlocking<Unit> {
        val archive = temporaryFolder.newFile("duplicate-entry.zip")
        writeZip(
            archive,
            linkedMapOf(
                "photos/a.jpg" to byteArrayOf(1),
                "photos/b.jpg" to byteArrayOf(2),
            ),
        )
        val patched = replaceAllBytes(archive.readBytes(), "photos/b.jpg", "photos/a.jpg")
        archive.writeBytes(patched)

        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(archive, temporaryFolder.newFolder("duplicate-entry-restore")) }
        }
    }

    @Test
    fun rejectsUnsupportedFormatAndRoomSchemaVersions() = runBlocking<Unit> {
        val data = kotlinx.serialization.json.Json.encodeToString(
            BackupSnapshot.serializer(),
            BackupSnapshot(),
        ).toByteArray()
        val unsupportedFormat = temporaryFolder.newFile("unsupported-format.zip")
        writeZip(
            unsupportedFormat,
            mapOf(
                "manifest.json" to manifestJson(data, formatVersion = 2),
                "data.json" to data,
            ),
        )
        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(unsupportedFormat, temporaryFolder.newFolder("unsupported-format-restore")) }
        }

        val unsupportedSchema = temporaryFolder.newFile("unsupported-schema.zip")
        writeZip(
            unsupportedSchema,
            mapOf(
                "manifest.json" to manifestJson(data, roomSchemaVersion = 8),
                "data.json" to data,
            ),
        )
        assertThrows(BackupArchiveException::class.java) {
            runBlocking { codec.read(unsupportedSchema, temporaryFolder.newFolder("unsupported-schema-restore")) }
        }
    }

    private fun sampleSnapshot(
        plant: PlantEntity = samplePlant(),
        notes: List<PlantNoteEntity> = listOf(PlantNoteEntity("note-1", plant.id, "note", 2, 3)),
        careEvents: List<CareEventEntity> = listOf(CareEventEntity("care-1", plant.id, "WATERING", 4, null, 5, 6)),
        reminderRules: List<ReminderRuleEntity> = listOf(intervalRule("rule-1")),
        notices: List<NoticeEntity> = emptyList(),
        preferences: SettingsPreferences = SettingsPreferences(
            theme = ThemePreference.DARK,
            language = AppLanguage.SPANISH,
            reminderMinuteOfDay = 1_230,
        ),
    ): BackupSnapshot = BackupSnapshot(
        plants = listOf(plant),
        notes = notes,
        careEvents = careEvents,
        reminderRules = reminderRules,
        notices = notices,
        preferences = preferences,
    )

    private fun samplePlant(
        name: String = "Plant",
        nameSearch: String = "plant",
        scientificName: String? = "Species",
        scientificNameSearch: String = "species",
        mainPhotoPath: String? = null,
    ): PlantEntity = PlantEntity(
        id = "plant-1",
        name = name,
        nameSearch = nameSearch,
        scientificName = scientificName,
        scientificNameSearch = scientificNameSearch,
        location = "Living room",
        acquiredEpochDay = 10,
        description = "Description",
        mainPhotoPath = mainPhotoPath,
        isFavorite = true,
        createdAt = 1,
        updatedAt = 2,
    )

    private fun intervalRule(id: String, nextDueEpochDay: Long = 100): ReminderRuleEntity =
        ReminderRuleEntity(
            id = id,
            plantId = "plant-1",
            careType = "WATERING",
            scheduleType = "INTERVAL",
            enabled = true,
            intervalDays = 7,
            nextDueEpochDay = nextDueEpochDay,
            createdAt = 1,
            updatedAt = 2,
        )

    private fun writeZip(destination: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(destination)).use { output ->
            entries.forEach { (path, bytes) ->
                output.putNextEntry(ZipEntry(path))
                output.write(bytes)
                output.closeEntry()
            }
        }
    }

    private fun manifestJson(
        data: ByteArray,
        counts: BackupCounts = BackupCounts(0, 0, 0, 0, 0, 0),
        formatVersion: Int = 1,
        roomSchemaVersion: Int = 7,
    ): ByteArray {
        val manifest = BackupManifest(
            formatVersion = formatVersion,
            versionName = "0.11.0",
            versionCode = 27,
            roomSchemaVersion = roomSchemaVersion,
            exportedAtEpochMillis = 1,
            counts = counts,
            entries = listOf(
                BackupArchiveEntry(
                    path = "data.json",
                    size = data.size.toLong(),
                    sha256 = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(data)
                        .joinToString("") { "%02x".format(it.toInt() and 0xff) },
                ),
            ),
        )
        return kotlinx.serialization.json.Json.encodeToString(
            BackupManifest.serializer(),
            manifest,
        ).toByteArray()
    }

    private fun writeStoredZip(destination: File, entries: LinkedHashMap<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(destination)).use { output ->
            entries.forEach { (path, bytes) ->
                val crc = CRC32().also { it.update(bytes) }.value
                val entry = ZipEntry(path).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    this.crc = crc
                }
                output.putNextEntry(entry)
                output.write(bytes)
                output.closeEntry()
            }
        }
    }

    private fun littleEndianUnsignedShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun writeZeroZip(destination: File, path: String, size: Long) {
        ZipOutputStream(FileOutputStream(destination)).use { output ->
            output.putNextEntry(ZipEntry(path))
            val zeros = ByteArray(32 * 1024)
            var remaining = size
            while (remaining > 0) {
                val count = minOf(remaining, zeros.size.toLong()).toInt()
                output.write(zeros, 0, count)
                remaining -= count
            }
            output.closeEntry()
        }
    }

    private fun replaceAllBytes(source: ByteArray, from: String, to: String): ByteArray {
        val fromBytes = from.toByteArray()
        val toBytes = to.toByteArray()
        require(fromBytes.size == toBytes.size)
        var replacements = 0
        for (offset in 0..source.size - fromBytes.size) {
            if (fromBytes.indices.all { source[offset + it] == fromBytes[it] }) {
                toBytes.copyInto(source, offset)
                replacements++
            }
        }
        assertTrue("expected local and central directory names", replacements >= 2)
        return source
    }
}
