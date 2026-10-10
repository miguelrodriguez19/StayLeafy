package com.miguelrodriguez19.stayleafy.data.photo

import android.net.Uri
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PlantPhotoOwner(
    val exists: Boolean,
    val relativePath: String?,
)

/**
 * Narrow adapter for the plant row. The implementation must return row existence rather than
 * affected-row count when clearing an already-null path.
 */
interface PlantPhotoOwnerPort {
    suspend fun read(plantId: String): PlantPhotoOwner

    suspend fun updateMainPhotoPath(plantId: String, relativePath: String?): Boolean

    suspend fun referencedPhotoPaths(): Set<String>
}

interface PlantPhotoRepository {
    fun prepareCameraCapture(): PendingPhotoCapture

    suspend fun completeCameraCapture(plantId: String, token: String): StoredPhoto

    suspend fun discardCameraCapture(token: String): Boolean

    suspend fun importImage(plantId: String, sourceUri: Uri): StoredPhoto

    suspend fun replacePhoto(plantId: String, sourceUri: Uri): StoredPhoto

    suspend fun removePhoto(plantId: String): Boolean

    fun resolvePhoto(relativePath: String): File?

    suspend fun reconcileOrphans(): PhotoOrphanReconciliation
}

class PlantPhotoNotFoundException(plantId: String) : IllegalArgumentException(
    "Plant does not exist: $plantId",
)

@Singleton
class DefaultPlantPhotoRepository @Inject constructor(
    private val ownerPort: PlantPhotoOwnerPort,
    private val photoFileStore: PhotoFileStore,
    private val inputResolver: PhotoInputResolver,
) : PlantPhotoRepository {
    private val operationMutex = Mutex()

    override fun prepareCameraCapture(): PendingPhotoCapture =
        photoFileStore.prepareCameraCapture()

    override suspend fun completeCameraCapture(
        plantId: String,
        token: String,
    ): StoredPhoto = operationMutex.withLock {
        val stored = photoFileStore.completeCameraCapture(token)
        attachNewPhoto(plantId, stored)
    }

    override suspend fun discardCameraCapture(token: String): Boolean =
        photoFileStore.discardCameraCapture(token)

    override suspend fun importImage(plantId: String, sourceUri: Uri): StoredPhoto =
        operationMutex.withLock { replaceWithSource(plantId, sourceUri) }

    override suspend fun replacePhoto(plantId: String, sourceUri: Uri): StoredPhoto =
        operationMutex.withLock { replaceWithSource(plantId, sourceUri) }

    internal suspend fun importSourceForTest(
        plantId: String,
        source: PhotoInputSource,
    ): StoredPhoto = operationMutex.withLock {
        attachNewPhoto(plantId, photoFileStore.importImage(source))
    }

    override suspend fun removePhoto(plantId: String): Boolean = operationMutex.withLock {
        val owner = ownerPort.read(plantId)
        if (!owner.exists) throw PlantPhotoNotFoundException(plantId)
        val oldPath = owner.relativePath ?: return@withLock false

        if (!ownerPort.updateMainPhotoPath(plantId, null)) {
            throw PlantPhotoNotFoundException(plantId)
        }
        // A failed delete is intentionally left for reconcileOrphans(), because the database
        // update must not be rolled back by deleting user data before it succeeds.
        removeBestEffort(oldPath)
        true
    }

    override fun resolvePhoto(relativePath: String): File? =
        photoFileStore.resolve(relativePath)

    override suspend fun reconcileOrphans(): PhotoOrphanReconciliation = operationMutex.withLock {
        photoFileStore.reconcileOrphans(ownerPort.referencedPhotoPaths())
    }

    private suspend fun replaceWithSource(plantId: String, sourceUri: Uri): StoredPhoto {
        val stored = photoFileStore.importImage(inputResolver.resolve(sourceUri))
        return attachNewPhoto(plantId, stored)
    }

    private suspend fun attachNewPhoto(plantId: String, stored: StoredPhoto): StoredPhoto {
        var attached = false
        try {
            val owner = ownerPort.read(plantId)
            if (!owner.exists) throw PlantPhotoNotFoundException(plantId)
            if (!ownerPort.updateMainPhotoPath(plantId, stored.relativePath)) {
                throw PlantPhotoNotFoundException(plantId)
            }
            attached = true

            val oldPath = owner.relativePath
            if (oldPath != null && oldPath != stored.relativePath) {
                // The new row is already durable. If the old file cannot be deleted,
                // reconciliation will remove it once Room no longer references it.
                withContext(NonCancellable) { removeBestEffort(oldPath) }
            }
            return stored
        } catch (error: CancellationException) {
            if (!attached) withContext(NonCancellable) { cleanupNewPhoto(stored) }
            throw error
        } catch (error: Throwable) {
            if (!attached) cleanupNewPhoto(stored)
            throw error
        }
    }

    private suspend fun cleanupNewPhoto(stored: StoredPhoto) {
        removeBestEffort(stored.relativePath)
    }

    private suspend fun removeBestEffort(relativePath: String) {
        try {
            photoFileStore.remove(relativePath)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Reconciliation is the durable fallback for a file deletion that fails after the
            // database has already changed.
        }
    }
}
