package com.miguelrodriguez19.stayleafy.data.photo

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlantPhotoRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun replacePhoto_updatesOwnerAndRemovesPreviousPath() = runTest {
        val store = RecordingPhotoFileStore()
        val owner = RecordingOwner(PlantPhotoOwner(exists = true, relativePath = "plant_photos/old.jpg"))
        val repository = DefaultPlantPhotoRepository(owner, store, PhotoInputResolver { source })

        val result = repository.importSourceForTest("plant-1", source)

        assertEquals(store.imported, result)
        assertEquals("plant_photos/new.jpg", owner.updatedPath)
        assertEquals(listOf("plant_photos/old.jpg"), store.removedPaths)
    }

    @Test
    fun failedOwnerUpdate_removesNewPhotoAsCompensation() = runTest {
        val store = RecordingPhotoFileStore()
        val owner = RecordingOwner(
            current = PlantPhotoOwner(exists = true, relativePath = null),
            updateResult = false,
        )
        val repository = DefaultPlantPhotoRepository(owner, store, PhotoInputResolver { source })

        val error = runCatching {
            repository.importSourceForTest("plant-1", source)
        }.exceptionOrNull()

        assertTrue(error is PlantPhotoNotFoundException)
        assertEquals(listOf("plant_photos/new.jpg"), store.removedPaths)
    }

    @Test
    fun missingPlant_removesImportedPhoto() = runTest {
        val store = RecordingPhotoFileStore()
        val owner = RecordingOwner(PlantPhotoOwner(exists = false, relativePath = null))
        val repository = DefaultPlantPhotoRepository(owner, store, PhotoInputResolver { source })

        val error = runCatching {
            repository.importSourceForTest("missing", source)
        }.exceptionOrNull()

        assertTrue(error is PlantPhotoNotFoundException)
        assertEquals(listOf("plant_photos/new.jpg"), store.removedPaths)
    }

    @Test
    fun ownerReadFailure_removesImportedPhoto() = runTest {
        val store = RecordingPhotoFileStore()
        val owner = RecordingOwner(
            current = PlantPhotoOwner(exists = true, relativePath = null),
            readError = IllegalStateException("database unavailable"),
        )
        val repository = DefaultPlantPhotoRepository(owner, store, PhotoInputResolver { source })

        val error = runCatching {
            repository.importSourceForTest("plant-1", source)
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(listOf("plant_photos/new.jpg"), store.removedPaths)
    }

    @Test
    fun removePhoto_clearsOwnerBeforeDeletingFile() = runTest {
        val store = RecordingPhotoFileStore()
        val owner = RecordingOwner(PlantPhotoOwner(exists = true, relativePath = "plant_photos/old.jpg"))
        val repository = DefaultPlantPhotoRepository(owner, store, PhotoInputResolver { source })

        assertTrue(repository.removePhoto("plant-1"))

        assertEquals(null, owner.updatedPath)
        assertEquals(listOf("plant_photos/old.jpg"), store.removedPaths)
    }

    @Test
    fun reconcileOrphans_passesAllRoomReferencesToFileStore() = runTest {
        val store = RecordingPhotoFileStore()
        val owner = RecordingOwner(PlantPhotoOwner(exists = true, relativePath = null))
        owner.references = setOf("plant_photos/a.jpg", "plant_photos/b.png")
        val repository = DefaultPlantPhotoRepository(owner, store, PhotoInputResolver { source })

        repository.reconcileOrphans()

        assertEquals(owner.references, store.reconciledReferences)
    }

    @Test
    fun stagedPhotoOperations_areSerializedThroughTheFileStore() = runTest {
        val store = RecordingPhotoFileStore()
        val owner = RecordingOwner(PlantPhotoOwner(exists = true, relativePath = null))
        val repository = DefaultPlantPhotoRepository(owner, store, PhotoInputResolver { source })

        val staged = repository.stageCameraCapture("pending-id")
        val stored = repository.commitStagedPhoto(staged.token)
        val discarded = repository.discardStagedPhoto(staged.token)

        assertEquals("pending-id", staged.token)
        assertEquals("plant_photos/new.jpg", stored.relativePath)
        assertTrue(discarded)
        assertEquals(listOf("stage:pending-id", "commit:pending-id", "discard:pending-id"), store.stagedCalls)
    }

    @Test
    fun reconciliation_waitsUntilPhotoImportIsAttachedToRoom() = runTest {
        val importStarted = CompletableDeferred<Unit>()
        val allowImport = CompletableDeferred<Unit>()
        val store = GatedPhotoFileStore(importStarted, allowImport)
        val owner = RecordingOwner(PlantPhotoOwner(exists = true, relativePath = null))
        val repository = DefaultPlantPhotoRepository(owner, store, PhotoInputResolver { source })

        val import = async { repository.importSourceForTest("plant-1", source) }
        importStarted.await()
        val reconciliation = async { repository.reconcileOrphans() }
        runCurrent()

        assertFalse(reconciliation.isCompleted)

        allowImport.complete(Unit)
        import.await()
        reconciliation.await()
        assertEquals("plant_photos/new.jpg", owner.updatedPath)
        assertTrue(store.reconciled)
    }

    private val source = object : PhotoInputSource {
        override val extensionHint: String = "jpg"
        override fun openStream(): InputStream = "bytes".byteInputStream()
    }

    private class RecordingOwner(
        private val current: PlantPhotoOwner,
        private val updateResult: Boolean = true,
        private val readError: Throwable? = null,
    ) : PlantPhotoOwnerPort {
        var updatedPath: String? = null
        var references: Set<String> = emptySet()

        override suspend fun read(plantId: String): PlantPhotoOwner {
            readError?.let { throw it }
            return current
        }

        override suspend fun updateMainPhotoPath(plantId: String, relativePath: String?): Boolean {
            updatedPath = relativePath
            return updateResult
        }

        override suspend fun referencedPhotoPaths(): Set<String> = references
    }

    private class RecordingPhotoFileStore : PhotoFileStore {
        val imported = StoredPhoto("plant_photos/new.jpg", File("/tmp/new.jpg"))
        val removedPaths = mutableListOf<String>()
        var reconciledReferences: Set<String> = emptySet()
        val stagedCalls = mutableListOf<String>()

        override fun prepareCameraCapture(): PendingPhotoCapture = error("unused")

        override suspend fun completeCameraCapture(token: String): StoredPhoto = error("unused")

        override suspend fun discardCameraCapture(token: String): Boolean = error("unused")

        override suspend fun importImage(source: PhotoInputSource): StoredPhoto = imported

        override suspend fun stageCameraCapture(token: String): StagedPhoto {
            stagedCalls += "stage:$token"
            return StagedPhoto(token, "pending_plant_photos/$token.jpg", File("/tmp/$token.jpg"))
        }

        override suspend fun commitStagedPhoto(token: String): StoredPhoto {
            stagedCalls += "commit:$token"
            return imported
        }

        override suspend fun discardStagedPhoto(token: String): Boolean {
            stagedCalls += "discard:$token"
            return true
        }

        override suspend fun remove(relativePath: String): Boolean {
            removedPaths += relativePath
            return true
        }

        override fun resolve(relativePath: String): File? = null

        override suspend fun reconcileOrphans(
            referencedPaths: Set<String>,
        ): PhotoOrphanReconciliation {
            reconciledReferences = referencedPaths
            return PhotoOrphanReconciliation(emptySet(), emptySet())
        }
    }

    private class GatedPhotoFileStore(
        private val importStarted: CompletableDeferred<Unit>,
        private val allowImport: CompletableDeferred<Unit>,
    ) : PhotoFileStore {
        var reconciled = false

        override fun prepareCameraCapture(): PendingPhotoCapture = error("unused")

        override suspend fun completeCameraCapture(token: String): StoredPhoto = error("unused")

        override suspend fun discardCameraCapture(token: String): Boolean = false

        override suspend fun importImage(source: PhotoInputSource): StoredPhoto {
            importStarted.complete(Unit)
            allowImport.await()
            return StoredPhoto("plant_photos/new.jpg", File("/tmp/new.jpg"))
        }

        override suspend fun remove(relativePath: String): Boolean = true

        override fun resolve(relativePath: String): File? = null

        override suspend fun reconcileOrphans(
            referencedPaths: Set<String>,
        ): PhotoOrphanReconciliation {
            reconciled = true
            return PhotoOrphanReconciliation(emptySet(), emptySet())
        }
    }
}
