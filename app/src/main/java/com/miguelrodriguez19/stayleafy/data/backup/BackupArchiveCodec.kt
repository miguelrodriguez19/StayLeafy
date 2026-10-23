package com.miguelrodriguez19.stayleafy.data.backup

import com.miguelrodriguez19.stayleafy.data.local.database.INTERVAL_SCHEDULE_TYPE
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleEntity
import com.miguelrodriguez19.stayleafy.data.local.database.SEASONAL_SCHEDULE_TYPE
import com.miguelrodriguez19.stayleafy.data.local.database.normalizeForPlantSearch
import com.miguelrodriguez19.stayleafy.data.settings.SettingsPreferences
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class BackupArchiveException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Encodes and validates the version-one logical, unencrypted backup format. */
class BackupArchiveCodec @Inject constructor() {
    suspend fun write(
        destination: File,
        snapshot: BackupSnapshot,
        photos: Map<String, File>,
        versionName: String,
        versionCode: Long,
        exportedAtEpochMillis: Long,
    ): BackupManifest = withContext(Dispatchers.IO) {
        requireSimpleVersion(versionName)
        require(versionCode > 0) { "Backup VERSION_CODE must be positive" }
        require(exportedAtEpochMillis >= 0) { "Backup export time cannot be negative" }
        require(!isSymbolicLink(destination)) {
            "Backup destination must not be a symbolic link"
        }
        require(!destination.exists()) { "Backup destination already exists" }
        val parent = destination.absoluteFile.parentFile
            ?: throw BackupArchiveException("Backup destination has no parent directory")
        requirePrivateDirectory(parent, "backup destination parent")
        validatePhotoInputs(photos)
        validateSnapshot(snapshot, photos.keys)

        val dataBytes = try {
            JSON.encodeToString(BackupSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)
        } catch (error: SerializationException) {
            throw BackupArchiveException("Unable to encode backup data", error)
        }
        require(dataBytes.size.toLong() <= MAX_DATA_BYTES) {
            "Backup data.json exceeds $MAX_DATA_BYTES bytes"
        }

        val metadata = mutableListOf<PhotoMetadata>()
        for ((relativePath, file) in photos.entries.sortedBy { it.key }) {
            currentCoroutineContext().ensureActive()
            val size = file.length()
            require(size <= MAX_PHOTO_BYTES) {
                "Backup photo '$relativePath' exceeds $MAX_PHOTO_BYTES bytes"
            }
            metadata += PhotoMetadata(
                relativePath = relativePath,
                archivePath = archivePhotoPath(relativePath),
                file = file,
                size = size,
                sha256 = sha256(file, MAX_PHOTO_BYTES),
            )
        }
        val entries = buildList {
            add(BackupArchiveEntry("data.json", dataBytes.size.toLong(), sha256(dataBytes)))
            metadata.forEach { add(BackupArchiveEntry(it.archivePath, it.size, it.sha256)) }
        }
        require(entries.size + 1L <= MAX_ENTRIES) { "Backup contains too many ZIP entries" }

        val manifest = BackupManifest(
            formatVersion = FORMAT_VERSION,
            versionName = versionName,
            versionCode = versionCode,
            roomSchemaVersion = ROOM_SCHEMA_VERSION,
            exportedAtEpochMillis = exportedAtEpochMillis,
            counts = BackupCounts(
                plants = snapshot.plants.size,
                notes = snapshot.notes.size,
                careEvents = snapshot.careEvents.size,
                reminderRules = snapshot.reminderRules.size,
                notices = snapshot.notices.size,
                photos = metadata.size,
            ),
            entries = entries,
        )
        val manifestBytes = try {
            JSON.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)
        } catch (error: SerializationException) {
            throw BackupArchiveException("Unable to encode backup manifest", error)
        }
        require(manifestBytes.size.toLong() <= MAX_MANIFEST_BYTES) {
            "Backup manifest exceeds $MAX_MANIFEST_BYTES bytes"
        }
        val totalBytes = manifestBytes.size.toLong() + dataBytes.size.toLong() + metadata.sumOf { it.size }
        require(totalBytes <= MAX_TOTAL_UNCOMPRESSED_BYTES) {
            "Backup exceeds $MAX_TOTAL_UNCOMPRESSED_BYTES uncompressed bytes"
        }
        require(parent.usableSpace >= totalBytes + MIN_FREE_SPACE_BYTES) {
            "Insufficient disk space for backup"
        }

        val temporary = File(parent, ".${destination.name}.${UUID.randomUUID()}.part")
        try {
            val output = FileOutputStream(temporary)
            try {
                val zip = ZipOutputStream(output)
                try {
                    writeZipBytes(zip, "manifest.json", manifestBytes)
                    writeZipBytes(zip, "data.json", dataBytes)
                    for (item in metadata) {
                        currentCoroutineContext().ensureActive()
                        writeZipFile(zip, item.archivePath, item.file, item.size)
                    }
                    zip.finish()
                    zip.flush()
                    output.fd.sync()
                } finally {
                    zip.close()
                }
            } finally {
                output.close()
            }
            moveWithoutReplacement(temporary, destination)
        } catch (error: Throwable) {
            temporary.delete()
            if (error is CancellationException) throw error
            if (error is BackupArchiveException) throw error
            throw BackupArchiveException("Unable to write backup archive", error)
        }
        manifest
    }

    suspend fun read(source: File, destinationDirectory: File): DecodedBackup =
        withContext(Dispatchers.IO) {
            require(source.isFile && !isSymbolicLink(source)) {
                "Backup source must be a regular file"
            }
            require(source.length() <= MAX_TOTAL_UNCOMPRESSED_BYTES) {
                "Backup compressed input exceeds the supported size limit"
            }
            requirePrivateDirectory(destinationDirectory, "backup staging directory")
            require(destinationDirectory.usableSpace >= MIN_FREE_SPACE_BYTES) {
                "Insufficient disk space for backup restore"
            }

            val stage = File(destinationDirectory, ".decoded-${UUID.randomUUID()}")
            val photosDirectory = File(stage, "photos")
            var stageCreated = false
            try {
                require(stage.mkdir()) { "Unable to create private backup staging directory" }
                stageCreated = true
                require(photosDirectory.mkdir()) { "Unable to create private backup photo directory" }
                val actualEntries = linkedSetOf<String>()
                val extracted = linkedMapOf<String, ExtractedPhoto>()
                var manifestBytes: ByteArray? = null
                var dataBytes: ByteArray? = null
                val totalBytes = longArrayOf(0L)

                val zip = ZipFile(source)
                try {
                    val entries = zip.entries()
                    var entryCount = 0L
                    var compressedBytes = 0L
                    while (entries.hasMoreElements()) {
                        currentCoroutineContext().ensureActive()
                        entryCount++
                        require(entryCount <= MAX_ENTRIES) { "Backup contains too many ZIP entries" }
                        val entry = entries.nextElement()
                        val entryCompressedSize = entry.compressedSize
                        if (entryCompressedSize >= 0) {
                            require(entryCompressedSize <= MAX_TOTAL_UNCOMPRESSED_BYTES - compressedBytes) {
                                "Backup compressed input exceeds the supported size limit"
                            }
                            compressedBytes += entryCompressedSize
                        }
                        val path = entry.name
                        require(!entry.isDirectory) { "Backup contains an unexpected directory entry: $path" }
                        require(actualEntries.add(path)) { "Backup contains duplicate ZIP entry: $path" }
                        when {
                            path == "manifest.json" -> {
                                val input = zip.getInputStream(entry)
                                try {
                                    manifestBytes = readBounded(input, MAX_MANIFEST_BYTES, totalBytes)
                                } finally {
                                    input.close()
                                }
                            }
                            path == "data.json" -> {
                                val input = zip.getInputStream(entry)
                                try {
                                    dataBytes = readBounded(input, MAX_DATA_BYTES, totalBytes)
                                } finally {
                                    input.close()
                                }
                            }
                            path.startsWith("photos/") -> {
                                val filename = safeArchivePhotoName(path)
                                val output = File(photosDirectory, filename)
                                require(output.canonicalFile.parentFile == photosDirectory.canonicalFile) {
                                    "Backup photo path escapes staging directory"
                                }
                                entry.size.takeIf { it >= 0 }?.let { size ->
                                    require(size <= MAX_PHOTO_BYTES) {
                                        "Backup photo '$path' exceeds the supported size limit"
                                    }
                                    require(destinationDirectory.usableSpace >= size + MIN_FREE_SPACE_BYTES) {
                                        "Insufficient disk space for backup restore"
                                    }
                                }
                                val input = zip.getInputStream(entry)
                                try {
                                    extracted[path] = copyBoundedToFile(input, output, MAX_PHOTO_BYTES, totalBytes)
                                } finally {
                                    input.close()
                                }
                            }
                            else -> throw BackupArchiveException("Unexpected backup path '$path'")
                        }
                    }
                } finally {
                    zip.close()
                }

                val manifest = decodeManifest(manifestBytes ?: throw BackupArchiveException("Backup is missing manifest.json"))
                require(manifest.formatVersion == FORMAT_VERSION) {
                    "Unsupported backup format version: ${manifest.formatVersion}"
                }
                require(manifest.roomSchemaVersion == ROOM_SCHEMA_VERSION) {
                    "Unsupported Room schema version: ${manifest.roomSchemaVersion}"
                }
                requireSimpleVersion(manifest.versionName)
                require(manifest.versionCode > 0) { "Backup manifest VERSION_CODE must be positive" }
                require(manifest.exportedAtEpochMillis >= 0) { "Backup manifest export time is invalid" }
                validateManifestEntries(manifest.entries)
                val manifestEntriesByPath = manifest.entries.associateBy { it.path }
                val expectedEntries = manifest.entries.mapTo(linkedSetOf()) { it.path }
                require(expectedEntries == actualEntries - "manifest.json") {
                    "Backup manifest entries do not match ZIP contents"
                }

                val data = dataBytes ?: throw BackupArchiveException("Backup is missing data.json")
                val dataMetadata = requireNotNull(manifestEntriesByPath["data.json"])
                require(dataMetadata.size == data.size.toLong() && dataMetadata.sha256 == sha256(data)) {
                    "Backup data.json checksum or size does not match the manifest"
                }
                extracted.forEach { (path, photo) ->
                    val expected = requireNotNull(manifestEntriesByPath[path])
                    require(expected.size == photo.size && expected.sha256 == photo.sha256) {
                        "Backup photo '$path' checksum or size does not match the manifest"
                    }
                }
                val decoded = decodeSnapshot(data)
                validateCounts(manifest.counts, decoded, extracted.size)
                val stablePhotoPaths = extracted.keys.map { archivePathToStablePath(it) }.toSet()
                val snapshot = validateSnapshot(decoded, stablePhotoPaths)
                DecodedBackup(
                    manifest = manifest,
                    snapshot = snapshot,
                    photos = extracted.mapKeys { archivePathToStablePath(it.key) }.mapValues { it.value.file },
                )
            } catch (error: Throwable) {
                if (stageCreated) stage.deleteRecursively()
                if (error is CancellationException) throw error
                if (error is BackupArchiveException) throw error
                throw BackupArchiveException("Unable to read backup archive", error)
            }
        }

    private fun decodeManifest(bytes: ByteArray): BackupManifest = try {
        JSON.decodeFromString(BackupManifest.serializer(), decodeUtf8(bytes, "manifest.json"))
    } catch (error: SerializationException) {
        throw BackupArchiveException("Backup manifest is not valid JSON", error)
    }

    private fun decodeSnapshot(bytes: ByteArray): BackupSnapshot = try {
        JSON.decodeFromString(BackupSnapshot.serializer(), decodeUtf8(bytes, "data.json"))
    } catch (error: SerializationException) {
        throw BackupArchiveException("Backup data.json is not valid JSON", error)
    }

    private fun decodeUtf8(bytes: ByteArray, entryName: String): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: CharacterCodingException) {
        throw BackupArchiveException("Backup $entryName is not valid UTF-8", error)
    }

    private fun validateManifestEntries(entries: List<BackupArchiveEntry>) {
        require(entries.size.toLong() + 1L <= MAX_ENTRIES) { "Backup contains too many manifest entries" }
        val paths = mutableSetOf<String>()
        entries.forEach { entry ->
            require(paths.add(entry.path)) { "Backup manifest contains duplicate path '${entry.path}'" }
            require(entry.size >= 0) { "Backup manifest contains a negative size" }
            require(entry.sha256.matches(SHA256_PATTERN)) { "Backup manifest contains an invalid SHA-256 checksum" }
            when {
                entry.path == "data.json" -> require(entry.size <= MAX_DATA_BYTES) {
                    "Backup data.json exceeds the supported size limit"
                }
                entry.path.startsWith("photos/") -> {
                    safeArchivePhotoName(entry.path)
                    require(entry.size <= MAX_PHOTO_BYTES) { "Backup photo exceeds the supported size limit" }
                }
                else -> throw BackupArchiveException("Unexpected manifest path '${entry.path}'")
            }
        }
        require(paths.contains("data.json")) { "Backup manifest is missing data.json" }
    }

    private fun validateCounts(counts: BackupCounts, snapshot: BackupSnapshot, photoCount: Int) {
        require(counts.plants == snapshot.plants.size) { "Backup plant count does not match the manifest" }
        require(counts.notes == snapshot.notes.size) { "Backup note count does not match the manifest" }
        require(counts.careEvents == snapshot.careEvents.size) { "Backup care-event count does not match the manifest" }
        require(counts.reminderRules == snapshot.reminderRules.size) { "Backup rule count does not match the manifest" }
        require(counts.notices == snapshot.notices.size) { "Backup notice count does not match the manifest" }
        require(counts.photos == photoCount) { "Backup photo count does not match the manifest" }
        require(listOf(counts.plants, counts.notes, counts.careEvents, counts.reminderRules, counts.notices, counts.photos).all { it >= 0 }) {
            "Backup counts cannot be negative"
        }
    }

    private fun validateSnapshot(snapshot: BackupSnapshot, photoPaths: Set<String>): BackupSnapshot {
        val plantIds = distinctIds(snapshot.plants.map { it.id }, "plant")
        distinctIds(snapshot.notes.map { it.id }, "note")
        distinctIds(snapshot.careEvents.map { it.id }, "care event")
        distinctIds(snapshot.reminderRules.map { it.id }, "reminder rule")
        distinctIds(snapshot.notices.map { it.id }, "notice")
        val referencedPhotoPaths = snapshot.plants.mapNotNull { it.mainPhotoPath }.toSet()
        require(referencedPhotoPaths == photoPaths) {
            "Backup photo entries must exactly match plant main photo references"
        }
        snapshot.plants.forEach { plant ->
            require(plant.name.isNotBlank()) { "Plant '${plant.id}' has a blank name" }
            validateEpochDay(plant.acquiredEpochDay, "plant '${plant.id}' acquired date")
            plant.mainPhotoPath?.let { path ->
                require(isSafeStablePhotoPath(path)) { "Invalid plant photo path '$path'" }
                require(path in photoPaths) { "Backup is missing photo '$path'" }
            }
        }
        snapshot.notes.forEach {
            require(it.plantId in plantIds) { "Note '${it.id}' references an unknown plant" }
            require(it.body.isNotBlank()) { "Note '${it.id}' has a blank body" }
        }
        snapshot.careEvents.forEach {
            require(it.plantId in plantIds) { "Care event '${it.id}' references an unknown plant" }
            requireValidCareType(it.careType)
        }
        val rulesById = snapshot.reminderRules.associateBy { it.id }
        val ruleIds = rulesById.keys
        val ruleKeys = mutableSetOf<Triple<String, String, String>>()
        val enabledCareKeys = mutableSetOf<Pair<String, String>>()
        snapshot.reminderRules.forEach { rule ->
            require(rule.plantId in plantIds) { "Reminder rule '${rule.id}' references an unknown plant" }
            requireValidCareType(rule.careType)
            require(rule.scheduleType == INTERVAL_SCHEDULE_TYPE || rule.scheduleType == SEASONAL_SCHEDULE_TYPE) {
                "Unknown reminder schedule type '${rule.scheduleType}'"
            }
            require(ruleKeys.add(Triple(rule.plantId, rule.careType, rule.scheduleType))) {
                "Duplicate reminder rule for plant, care type, and schedule"
            }
            if (rule.enabled) {
                require(enabledCareKeys.add(rule.plantId to rule.careType)) {
                    "More than one enabled reminder rule exists for the same plant and care type"
                }
            }
            validateRuleSchedule(rule)
            validateEpochDay(rule.nextDueEpochDay, "rule '${rule.id}' due date")
        }
        val occurrenceKeys = mutableSetOf<String>()
        snapshot.notices.forEach { notice ->
            require(notice.ruleId in ruleIds) { "Notice '${notice.id}' references an unknown rule" }
            requireValidCareType(notice.careType)
            requireValidDeliveryState(notice.deliveryState)
            require(notice.occurrenceKey.isNotBlank()) { "Notice '${notice.id}' has a blank occurrence key" }
            validateEpochDay(notice.dueEpochDay, "notice '${notice.id}' due date")
            require(occurrenceKeys.add(notice.occurrenceKey)) {
                "Duplicate notice occurrence key '${notice.occurrenceKey}'"
            }
            val rule = requireNotNull(rulesById[notice.ruleId])
            require(rule.careType == notice.careType) {
                "Notice '${notice.id}' care type does not match its rule"
            }
            val expectedOccurrenceKey = when (rule.scheduleType) {
                INTERVAL_SCHEDULE_TYPE -> "interval:${rule.id}:${notice.dueEpochDay}"
                SEASONAL_SCHEDULE_TYPE -> "seasonal:${rule.id}:${notice.dueEpochDay}"
                else -> error("Unknown reminder schedule type '${rule.scheduleType}'")
            }
            require(notice.occurrenceKey == expectedOccurrenceKey) {
                "Notice '${notice.id}' occurrence key does not match its rule and due date"
            }
        }
        require(snapshot.preferences.reminderMinuteOfDay in 0 until SettingsPreferences.MINUTES_PER_DAY) {
            "Backup reminder time is outside the valid day"
        }
        return snapshot.copy(
            plants = snapshot.plants.map { plant ->
                plant.copy(
                    nameSearch = plant.name.normalizeForPlantSearch(),
                    scientificNameSearch = plant.scientificName.normalizeForPlantSearch(),
                )
            },
        )
    }

    private fun validateRuleSchedule(rule: ReminderRuleEntity) {
        when (rule.scheduleType) {
            INTERVAL_SCHEDULE_TYPE -> {
                require(rule.intervalDays != null && rule.intervalDays > 0) { "Interval rule '${rule.id}' must have a positive interval" }
                require(rule.nextDueEpochDay != null) { "Interval rule '${rule.id}' must have a next due date" }
                require(rule.seasonStartMonth == null && rule.seasonStartDay == null && rule.seasonEndMonth == null && rule.seasonEndDay == null) {
                    "Interval rule '${rule.id}' cannot contain seasonal dates"
                }
            }
            SEASONAL_SCHEDULE_TYPE -> {
                require(rule.intervalDays == null && rule.nextDueEpochDay == null) { "Seasonal rule '${rule.id}' cannot contain interval fields" }
                requireValidMonthDay(rule.seasonStartMonth, rule.seasonStartDay, rule.id, "start")
                if (rule.seasonEndMonth != null || rule.seasonEndDay != null) {
                    requireValidMonthDay(rule.seasonEndMonth, rule.seasonEndDay, rule.id, "end")
                }
            }
        }
    }

    private fun requireValidMonthDay(month: Int?, day: Int?, ruleId: String, field: String) {
        require(month != null && day != null) { "Seasonal rule '$ruleId' must have a complete $field date" }
        try {
            java.time.MonthDay.of(month, day)
        } catch (error: DateTimeException) {
            throw BackupArchiveException("Seasonal rule '$ruleId' has an invalid $field date", error)
        }
    }

    private fun requireValidCareType(value: String) {
        require(runCatching { CareType.valueOf(value) }.isSuccess) { "Unknown care type '$value'" }
    }

    private fun requireValidDeliveryState(value: String) {
        require(runCatching { NoticeDeliveryState.valueOf(value) }.isSuccess) {
            "Unknown notice delivery state '$value'"
        }
    }

    private fun validateEpochDay(value: Long?, label: String) {
        if (value == null) return
        try {
            LocalDate.ofEpochDay(value)
        } catch (error: DateTimeException) {
            throw BackupArchiveException("$label is outside the supported date range", error)
        }
    }

    private fun distinctIds(ids: List<String>, kind: String): Set<String> {
        require(ids.all { it.isNotBlank() }) { "Backup $kind IDs cannot be blank" }
        require(ids.size == ids.toSet().size) { "Backup contains duplicate $kind IDs" }
        return ids.toSet()
    }

    private fun validatePhotoInputs(photos: Map<String, File>) {
        require(photos.size.toLong() + 2L <= MAX_ENTRIES) { "Backup contains too many ZIP entries" }
        photos.forEach { (path, file) ->
            require(isSafeStablePhotoPath(path)) { "Invalid photo path '$path'" }
            require(file.isFile && !isSymbolicLink(file)) { "Backup photo '$path' is not a regular file" }
        }
    }

    private fun isSafeStablePhotoPath(path: String): Boolean =
        path.startsWith("plant_photos/") && path.removePrefix("plant_photos/").matches(SAFE_STABLE_FILE)

    private fun archivePhotoPath(path: String): String = "photos/${path.removePrefix("plant_photos/")}"

    private fun archivePathToStablePath(path: String): String = "plant_photos/${safeArchivePhotoName(path)}"

    private fun safeArchivePhotoName(path: String): String {
        require(path.startsWith("photos/")) { "Invalid backup photo path '$path'" }
        val filename = path.removePrefix("photos/")
        require(filename.matches(SAFE_STABLE_FILE) && !filename.contains('/')) { "Invalid backup photo path '$path'" }
        return filename
    }

    private suspend fun writeZipBytes(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        currentCoroutineContext().ensureActive()
        zip.putNextEntry(ZipEntry(path))
        try {
            var offset = 0
            while (offset < bytes.size) {
                currentCoroutineContext().ensureActive()
                val count = minOf(BUFFER_SIZE, bytes.size - offset)
                zip.write(bytes, offset, count)
                offset += count
            }
        } finally {
            zip.closeEntry()
        }
    }

    private suspend fun writeZipFile(zip: ZipOutputStream, path: String, source: File, expectedSize: Long) {
        zip.putNextEntry(ZipEntry(path))
        try {
            val input = FileInputStream(source)
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= MAX_PHOTO_BYTES) { "Backup photo exceeds the supported size limit" }
                    zip.write(buffer, 0, count)
                }
                require(copied == expectedSize && source.length() == expectedSize) { "Backup photo changed while archived" }
            } finally {
                input.close()
            }
        } finally {
            zip.closeEntry()
        }
    }

    private suspend fun readBounded(input: java.io.InputStream, maximum: Long, total: LongArray): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var size = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            size += count
            total[0] += count
            require(size <= maximum && total[0] <= MAX_TOTAL_UNCOMPRESSED_BYTES) { "Backup entry exceeds the supported size limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private suspend fun copyBoundedToFile(
        input: java.io.InputStream,
        output: File,
        maximum: Long,
        total: LongArray,
    ): ExtractedPhoto {
        var size = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        val fileOutput = FileOutputStream(output)
        try {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                total[0] += count
                require(size <= maximum && total[0] <= MAX_TOTAL_UNCOMPRESSED_BYTES) { "Backup photo exceeds the supported size limit" }
                digest.update(buffer, 0, count)
                fileOutput.write(buffer, 0, count)
            }
            fileOutput.fd.sync()
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            fileOutput.close()
        }
        return ExtractedPhoto(output, size, digest.digest().toHex())
    }

    private suspend fun sha256(file: File, maximum: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = FileInputStream(file)
        try {
            val buffer = ByteArray(BUFFER_SIZE)
            var size = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                require(size <= maximum) { "Backup file exceeds the supported size limit" }
                digest.update(buffer, 0, count)
            }
        } finally {
            input.close()
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun requirePrivateDirectory(directory: File, label: String) {
        require(!isSymbolicLink(directory)) { "$label must not be a symbolic link" }
        if (!directory.exists()) require(directory.mkdirs()) { "Unable to create $label" }
        require(directory.isDirectory) { "$label is not a directory" }
    }

    private fun isSymbolicLink(file: File): Boolean {
        val absolute = file.absoluteFile
        val parent = absolute.parentFile ?: return false
        val parentCanonical = parent.canonicalFile
        val normalized = File(parentCanonical, absolute.name)
        return normalized.canonicalFile != normalized.absoluteFile
    }

    private fun moveWithoutReplacement(source: File, destination: File) {
        require(destination.createNewFile()) { "Backup destination already exists" }
        var moved = false
        try {
            require(source.renameTo(destination)) { "Unable to move backup into place" }
            moved = true
        } finally {
            if (!moved) destination.delete()
        }
    }

    private fun requireSimpleVersion(version: String) {
        require(SIMPLE_SEMVER.matches(version)) { "Backup version must use simple SemVer major.minor.patch" }
    }

    private data class PhotoMetadata(
        val relativePath: String,
        val archivePath: String,
        val file: File,
        val size: Long,
        val sha256: String,
    )

    private data class ExtractedPhoto(val file: File, val size: Long, val sha256: String)

    private companion object {
        const val FORMAT_VERSION = 1
        const val ROOM_SCHEMA_VERSION = 7
        const val MAX_TOTAL_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L * 1024L
        const val MAX_MANIFEST_BYTES = 8L * 1024L * 1024L
        const val MAX_DATA_BYTES = 32L * 1024L * 1024L
        const val MAX_PHOTO_BYTES = 100L * 1024L * 1024L
        const val MAX_ENTRIES = 10_002L
        const val MIN_FREE_SPACE_BYTES = 64L * 1024L
        const val BUFFER_SIZE = 32 * 1024
        val SIMPLE_SEMVER = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        val SAFE_STABLE_FILE = Regex("[A-Za-z0-9-]{1,128}\\.[a-z0-9]{1,16}")
        val JSON = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
            isLenient = false
        }
    }
}
