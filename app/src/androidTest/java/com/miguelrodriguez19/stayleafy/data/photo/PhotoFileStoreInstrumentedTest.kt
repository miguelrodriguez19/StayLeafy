package com.miguelrodriguez19.stayleafy.data.photo

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoFileStoreInstrumentedTest {

    @Test
    fun fileProviderCapture_roundTripsPendingPhotoIntoStableStorage() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDirectory = context.cacheDir
        val filesDirectory = context.filesDir
        val pendingFile = File(cacheDirectory, "$PENDING_PLANT_PHOTO_DIRECTORY/capture-1.jpg")
        val stableFile = File(filesDirectory, "$PLANT_PHOTO_DIRECTORY/stored-1.jpg")
        pendingFile.delete()
        stableFile.delete()
        val store = FilePhotoFileStore(
            filesDirectory = filesDirectory,
            cacheDirectory = cacheDirectory,
            uuidProvider = FixedUuidProvider("capture-1", "stored-1"),
            contentUriFactory = PhotoContentUriFactory { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            },
        )

        val pending = store.prepareCameraCapture()
        assertNotNull(pending.contentUri)
        val pendingBytes = byteArrayOf(1, 2, 3, 4)
        context.contentResolver.openOutputStream(pending.contentUri).use { output ->
            requireNotNull(output).write(pendingBytes)
        }

        val stored = store.completeCameraCapture(pending.token)

        assertEquals("plant_photos/stored-1.jpg", stored.relativePath)
        assertArrayEquals(pendingBytes, stored.file.readBytes())
        assertFalse(pendingFile.exists())
        stableFile.delete()
    }

    @Test
    fun failedCameraCompletion_removesPendingCapture() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pendingFile = File(
            context.cacheDir,
            "$PENDING_PLANT_PHOTO_DIRECTORY/capture-failure.jpg",
        )
        val stableDirectory = File(context.filesDir, PLANT_PHOTO_DIRECTORY)
        pendingFile.delete()
        stableDirectory.deleteRecursively()
        stableDirectory.writeText("blocks directory creation")
        val store = FilePhotoFileStore(
            filesDirectory = context.filesDir,
            cacheDirectory = context.cacheDir,
            uuidProvider = FixedUuidProvider("capture-failure", "stored-failure"),
            contentUriFactory = PhotoContentUriFactory { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            },
        )

        val pending = store.prepareCameraCapture()
        context.contentResolver.openOutputStream(pending.contentUri).use { output ->
            requireNotNull(output).write(byteArrayOf(1, 2, 3))
        }

        val error = runCatching { store.completeCameraCapture(pending.token) }.exceptionOrNull()

        assertTrue(error is PhotoFileStoreException)
        assertFalse(pendingFile.exists())
        stableDirectory.delete()
    }
}

private class FixedUuidProvider(
    private val ids: List<String>,
) : UuidProvider {
    private var index = 0

    constructor(vararg ids: String) : this(ids.toList())

    override fun newUuid(): String = ids[index++]
}
