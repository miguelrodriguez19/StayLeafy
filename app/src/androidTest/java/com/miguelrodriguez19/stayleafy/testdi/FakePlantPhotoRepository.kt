package com.miguelrodriguez19.stayleafy.testdi

import android.net.Uri
import com.miguelrodriguez19.stayleafy.data.photo.PendingPhotoCapture
import com.miguelrodriguez19.stayleafy.data.photo.PhotoOrphanReconciliation
import com.miguelrodriguez19.stayleafy.data.photo.PlantPhotoRepository
import com.miguelrodriguez19.stayleafy.data.photo.StoredPhoto
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakePlantPhotoRepository @Inject constructor() : PlantPhotoRepository {
    override fun prepareCameraCapture(): PendingPhotoCapture = error("Camera is not launched by navigation tests")
    override suspend fun completeCameraCapture(plantId: String, token: String): StoredPhoto = error("unused")
    override suspend fun discardCameraCapture(token: String): Boolean = false
    override suspend fun importImage(plantId: String, sourceUri: Uri): StoredPhoto = error("unused")
    override suspend fun replacePhoto(plantId: String, sourceUri: Uri): StoredPhoto = error("unused")
    override suspend fun removePhoto(plantId: String): Boolean = false
    override fun resolvePhoto(relativePath: String): File? = null
    override suspend fun reconcileOrphans() = PhotoOrphanReconciliation(emptySet(), emptySet())
}
