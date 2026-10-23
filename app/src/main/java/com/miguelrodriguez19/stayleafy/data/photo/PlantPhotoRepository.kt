package com.miguelrodriguez19.stayleafy.data.photo

import android.net.Uri
import com.miguelrodriguez19.stayleafy.data.backup.DataOperationCoordinator
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
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

    suspend fun prepareCameraCrop(token: String): PendingPhotoCrop =
        throw UnsupportedOperationException("Photo cropping is not supported")

    suspend fun prepareImageCrop(sourceUri: Uri): PendingPhotoCrop =
        throw UnsupportedOperationException("Photo cropping is not supported")

    fun resumePhotoCrop(sourceToken: String, outputToken: String): PendingPhotoCrop? = null

    suspend fun completePhotoCrop(
        plantId: String?,
        sourceToken: String,
        outputToken: String,
    ): StagedPhoto? = throw UnsupportedOperationException("Photo cropping is not supported")

    suspend fun discardPhotoCrop(sourceToken: String?, outputToken: String?): Boolean =
        throw UnsupportedOperationException("Photo cropping is not supported")

    suspend fun stageCameraCapture(token: String): StagedPhoto =
        throw UnsupportedOperationException("Photo staging is not supported")

    suspend fun stageImage(sourceUri: Uri): StagedPhoto =
        throw UnsupportedOperationException("Photo staging is not supported")

    suspend fun commitStagedPhoto(token: String): StoredPhoto =
        throw UnsupportedOperationException("Photo staging is not supported")

    suspend fun discardStagedPhoto(token: String): Boolean = discardCameraCapture(token)

    fun resolveStagedPhoto(token: String): File? = null

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
    private val operations: DataOperationCoordinator = DataOperationCoordinator(),
) : PlantPhotoRepository {

    override fun prepareCameraCapture(): PendingPhotoCapture =
        photoFileStore.prepareCameraCapture()

    override suspend fun completeCameraCapture(
        plantId: String,
        token: String,
    ): StoredPhoto = operations.withOperation {
        val stored = photoFileStore.completeCameraCapture(token)
        attachNewPhoto(plantId, stored)
    }

    override suspend fun discardCameraCapture(token: String): Boolean =
        photoFileStore.discardCameraCapture(token)

    override suspend fun importImage(plantId: String, sourceUri: Uri): StoredPhoto =
        operations.withOperation { replaceWithSource(plantId, sourceUri) }

    override suspend fun replacePhoto(plantId: String, sourceUri: Uri): StoredPhoto =
        operations.withOperation { replaceWithSource(plantId, sourceUri) }

    override suspend fun prepareCameraCrop(token: String): PendingPhotoCrop =
        operations.withOperation {
            try {
                photoFileStore.stageCameraCapture(token)
                photoFileStore.prepareCrop(token)
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    runCatching { photoFileStore.discardCrop(token, null) }
                }
                throw error
            }
        }

    override suspend fun prepareImageCrop(sourceUri: Uri): PendingPhotoCrop =
        operations.withOperation {
            val source = photoFileStore.stageImage(inputResolver.resolve(sourceUri))
            try {
                photoFileStore.prepareCrop(source.token)
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    runCatching { photoFileStore.discardCrop(source.token, null) }
                }
                throw error
            }
        }

    override fun resumePhotoCrop(sourceToken: String, outputToken: String): PendingPhotoCrop? =
        photoFileStore.resumeCrop(sourceToken, outputToken)

    override suspend fun completePhotoCrop(
        plantId: String?,
        sourceToken: String,
        outputToken: String,
    ): StagedPhoto? = operations.withOperation {
        val staged = photoFileStore.completeCrop(sourceToken, outputToken)
        if (plantId == null) return@withOperation staged

        try {
            val stored = photoFileStore.commitStagedPhoto(staged.token)
            attachNewPhoto(plantId, stored)
            withContext(NonCancellable) {
                runCatching { photoFileStore.discardStagedPhoto(staged.token) }
            }
            null
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                runCatching { photoFileStore.discardStagedPhoto(staged.token) }
            }
            throw error
        }
    }

    override suspend fun discardPhotoCrop(
        sourceToken: String?,
        outputToken: String?,
    ): Boolean = operations.withOperation {
        photoFileStore.discardCrop(sourceToken, outputToken)
    }

    override suspend fun stageCameraCapture(token: String): StagedPhoto = operations.withOperation {
        photoFileStore.stageCameraCapture(token)
    }

    override suspend fun stageImage(sourceUri: Uri): StagedPhoto = operations.withOperation {
        photoFileStore.stageImage(inputResolver.resolve(sourceUri))
    }

    override suspend fun commitStagedPhoto(token: String): StoredPhoto = operations.withOperation {
        photoFileStore.commitStagedPhoto(token)
    }

    override suspend fun discardStagedPhoto(token: String): Boolean = operations.withOperation {
        photoFileStore.discardStagedPhoto(token)
    }

    override fun resolveStagedPhoto(token: String): File? = photoFileStore.resolveStagedPhoto(token)

    internal suspend fun importSourceForTest(
        plantId: String,
        source: PhotoInputSource,
    ): StoredPhoto = operations.withOperation {
        attachNewPhoto(plantId, photoFileStore.importImage(source))
    }

    override suspend fun removePhoto(plantId: String): Boolean = operations.withOperation {
        val owner = ownerPort.read(plantId)
        if (!owner.exists) throw PlantPhotoNotFoundException(plantId)
        val oldPath = owner.relativePath ?: return@withOperation false

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

    override suspend fun reconcileOrphans(): PhotoOrphanReconciliation = operations.withOperation {
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
