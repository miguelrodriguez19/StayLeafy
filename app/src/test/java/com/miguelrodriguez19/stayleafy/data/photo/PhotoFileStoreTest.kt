package com.miguelrodriguez19.stayleafy.data.photo

import android.net.Uri
import android.net.TestUri
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoFileStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    @Ignore("FileProvider Uri creation is covered by instrumented tests")
    fun completeCameraCapture_copiesBytesAndRemovesPendingFile() = runTest {
        val store = newStore("capture-token", "capture-id")
        val pending = store.prepareCameraCapture()
        val actualPendingFile = File(temporaryFolder.root, "cache/$PENDING_PLANT_PHOTO_DIRECTORY/${pending.token}.jpg")
        actualPendingFile.writeBytes(byteArrayOf(1, 2, 3, 4))

        val stored = store.completeCameraCapture(pending.token)

        assertEquals("plant_photos/capture-id.jpg", stored.relativePath)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), stored.file.readBytes())
        assertFalse(actualPendingFile.exists())
    }

    @Test
    fun importImage_preservesBytesAndFallsBackToSafeExtension() = runTest {
        val store = newStore("import-id")

        val stored = store.importImage(
            object : PhotoInputSource {
                override val extensionHint: String = "../../escape"
                override fun openStream() = ByteArrayInputStream(byteArrayOf(9, 8, 7))
            },
        )

        assertEquals("plant_photos/import-id.bin", stored.relativePath)
        assertArrayEquals(byteArrayOf(9, 8, 7), stored.file.readBytes())
    }

    @Test
    fun stagedImage_canBePreviewedCommittedAndDiscardedWithoutConsumingItEarly() = runTest {
        val store = newStore("stage-id", "stable-id")

        val staged = store.stageImage(source("unused", "png", byteArrayOf(4, 5, 6)))

        assertEquals("stage-id", staged.token)
        assertEquals("pending_plant_photos/stage-id.png", staged.pendingPath)
        assertArrayEquals(byteArrayOf(4, 5, 6), store.resolveStagedPhoto(staged.token)?.readBytes())

        val stored = store.commitStagedPhoto(staged.token)

        assertEquals("plant_photos/stable-id.png", stored.relativePath)
        assertArrayEquals(byteArrayOf(4, 5, 6), stored.file.readBytes())
        assertTrue(staged.file.exists())

        assertTrue(store.discardStagedPhoto(staged.token))
        assertFalse(staged.file.exists())
    }

    @Test
    fun cropSession_usesAppOwnedUrisAndKeepsOnlyValidatedOutput() = runTest {
        val store = newStore("source-id", "output-id", validator = PhotoCropOutputValidator { file ->
            file.readBytes().contentEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2))
        })
        val source = store.stageImage(source("unused", "png", byteArrayOf(4, 5, 6)))

        val session = store.prepareCrop(source.token)
        val outputFile = File(
            temporaryFolder.root,
            "cache/$PENDING_PLANT_PHOTO_DIRECTORY/${session.outputToken}.jpg",
        )
        outputFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2))

        val cropped = store.completeCrop(session.sourceToken, session.outputToken)

        assertEquals("source-id", session.sourceToken)
        assertEquals("output-id", session.outputToken)
        assertEquals(1f, session.aspectRatioX)
        assertEquals(1f, session.aspectRatioY)
        assertFalse(session.freeStyleCropEnabled)
        assertEquals(2048, session.maxWidth)
        assertEquals(2048, session.maxHeight)
        assertEquals(92, session.outputQuality)
        assertEquals("image/jpeg", session.outputMimeType)
        assertEquals(session.destinationUri, TestUri("content://test/output-id"))
        assertEquals("output-id", cropped.token)
        assertFalse(source.file.exists())
        assertTrue(cropped.file.exists())
    }

    @Test
    fun invalidCropOutput_isRejectedAndBothTemporaryFilesAreRemoved() = runTest {
        val store = newStore("source-id", "output-id", validator = PhotoCropOutputValidator { false })
        val source = store.stageImage(source("unused", "jpg", byteArrayOf(4, 5, 6)))
        val session = store.prepareCrop(source.token)
        val output = File(
            temporaryFolder.root,
            "cache/$PENDING_PLANT_PHOTO_DIRECTORY/${session.outputToken}.jpg",
        )
        output.writeBytes(byteArrayOf(1, 2, 3, 4))

        val error = runCatching {
            store.completeCrop(session.sourceToken, session.outputToken)
        }.exceptionOrNull()

        assertTrue(error is InvalidPhotoCropOutputException)
        assertFalse(source.file.exists())
        assertFalse(output.exists())
    }

    @Test
    fun cropSession_canBeResumedFromItsTokensAfterRecreation() = runTest {
        val store = newStore("source-id", "output-id")
        val source = store.stageImage(source("unused", "png", byteArrayOf(1)))
        val original = store.prepareCrop(source.token)

        val restored = store.resumeCrop(original.sourceToken, original.outputToken)

        assertEquals(original.sourceUri, restored?.sourceUri)
        assertEquals(original.destinationUri, restored?.destinationUri)
        assertEquals(original.sourceToken, restored?.sourceToken)
        assertEquals(original.outputToken, restored?.outputToken)
    }

    @Test
    fun committingMissingStagedPhotoFailsWithoutCreatingStableFile() = runTest {
        val store = newStore("stable-id")

        val error = runCatching { store.commitStagedPhoto("missing") }.exceptionOrNull()

        assertTrue(error is PendingPhotoNotFoundException)
        assertFalse(File(temporaryFolder.root, "files/$PLANT_PHOTO_DIRECTORY").listFiles().orEmpty().any())
    }

    @Test
    fun resolve_rejectsPathsOutsideManagedDirectory() = runTest {
        val store = newStore("resolve-id")

        val error = runCatching { store.resolve("plant_photos/../outside.jpg") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun reconcileOrphans_deletesOnlyUnreferencedStableFiles() = runTest {
        val store = newStore("keep-id", "orphan-id")
        val keep = store.importImage(source("keep-id", "jpg", byteArrayOf(1)))
        val orphan = store.importImage(source("orphan-id", "png", byteArrayOf(2)))

        val result = store.reconcileOrphans(setOf(keep.relativePath))

        assertEquals(setOf(orphan.relativePath), result.deletedPaths)
        assertTrue(keep.file.exists())
        assertFalse(orphan.file.exists())
        assertTrue(result.failedPaths.isEmpty())
    }

    @Test
    @Ignore("FileProvider Uri creation is covered by instrumented tests")
    fun prepareCameraCapture_exposesProviderUriAndPendingRelativePath() {
        val store = newStore("pending-id")

        val pending = store.prepareCameraCapture()

        assertEquals("pending-id", pending.token)
        assertEquals("pending_plant_photos/pending-id.jpg", pending.pendingPath)
        assertEquals(TestUri("content://test/pending-id"), pending.contentUri)
    }

    private fun newStore(
        vararg ids: String,
        validator: PhotoCropOutputValidator = PhotoCropOutputValidator { true },
    ): FilePhotoFileStore {
        val files = temporaryFolder.newFolder("files")
        val cache = temporaryFolder.newFolder("cache")
        return FilePhotoFileStore(
            filesDirectory = files,
            cacheDirectory = cache,
            uuidProvider = SequenceUuidProvider(ids.toList()),
            contentUriFactory = PhotoContentUriFactory { file ->
                TestUri("content://test/${file.nameWithoutExtension}")
            },
            cropOutputValidator = validator,
        )
    }

    private fun source(id: String, extension: String, bytes: ByteArray) = object : PhotoInputSource {
        override val extensionHint: String = extension
        override fun openStream() = ByteArrayInputStream(bytes)
    }

    private class SequenceUuidProvider(
        private val ids: List<String>,
    ) : UuidProvider {
        private var index = 0

        override fun newUuid(): String = ids[index++]
    }
}
