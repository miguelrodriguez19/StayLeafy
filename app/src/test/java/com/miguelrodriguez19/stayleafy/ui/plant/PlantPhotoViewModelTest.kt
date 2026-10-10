package com.miguelrodriguez19.stayleafy.ui.plant

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.miguelrodriguez19.stayleafy.data.photo.PhotoFileStore
import com.miguelrodriguez19.stayleafy.data.photo.PhotoInputSource
import com.miguelrodriguez19.stayleafy.data.photo.PhotoOrphanReconciliation
import com.miguelrodriguez19.stayleafy.data.photo.PlantPhotoRepository
import com.miguelrodriguez19.stayleafy.data.photo.StoredPhoto
import com.miguelrodriguez19.stayleafy.test.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlantPhotoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun restoredCaptureToken_survivesViewModelRecreationAndCompletesCameraResult() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("pending_capture_token" to "capture-1"))
        val repository = FakePhotoRepository()
        val viewModel = PlantPhotoViewModel(savedStateHandle, "plant-1", repository)

        assertEquals("capture-1", viewModel.pendingCaptureToken.value)
        assertEquals(
            "capture-1",
            PlantPhotoViewModel(savedStateHandle, "plant-1", repository).pendingCaptureToken.value,
        )

        viewModel.onCameraResult(success = true)
        advanceUntilIdle()

        assertEquals(listOf("plant-1" to "capture-1"), repository.completedCaptures)
        assertNull(viewModel.pendingCaptureToken.value)
        assertFalse(viewModel.isMutating.value)
    }

    @Test
    fun cancelledCameraResult_discardsPendingCaptureAndClearsToken() = runTest {
        val repository = FakePhotoRepository()
        val viewModel = PlantPhotoViewModel(
            SavedStateHandle(mapOf("pending_capture_token" to "capture-1")),
            "plant-1",
            repository,
        )

        viewModel.onCameraResult(success = false)
        advanceUntilIdle()

        assertEquals(listOf("capture-1"), repository.discardedCaptures)
        assertNull(viewModel.pendingCaptureToken.value)
    }

    @Test
    fun removePhoto_delegatesToRepository() = runTest {
        val repository = FakePhotoRepository()
        val viewModel = PlantPhotoViewModel(SavedStateHandle(), "plant-1", repository)

        viewModel.removePhoto()
        advanceUntilIdle()

        assertEquals(listOf("plant-1"), repository.removedPlants)
        assertNull(viewModel.mutationError.value)
        assertFalse(viewModel.isMutating.value)
    }

    @Test
    fun photoMutationFailure_exposesLocalizedCategoryAndResetsState() = runTest {
        val repository = FakePhotoRepository().apply {
            removeError = IllegalStateException("remove failed")
        }
        val viewModel = PlantPhotoViewModel(SavedStateHandle(), "plant-1", repository)

        viewModel.removePhoto()
        advanceUntilIdle()

        assertEquals(UiErrorCategory.PhotoDelete, viewModel.mutationError.value)
        assertFalse(viewModel.isMutating.value)
    }

    @Test
    fun prepareCaptureFailure_exposesImportCategory() {
        val repository = FakePhotoRepository().apply {
            prepareError = IllegalStateException("camera unavailable")
        }
        val viewModel = PlantPhotoViewModel(SavedStateHandle(), "plant-1", repository)

        assertNull(viewModel.prepareCameraCapture())
        assertEquals(UiErrorCategory.PhotoImport, viewModel.mutationError.value)
    }

    @Test
    fun restoredCaptureToken_blocksReentrantCameraPreparation() {
        val repository = FakePhotoRepository()
        val viewModel = PlantPhotoViewModel(
            SavedStateHandle(mapOf("pending_capture_token" to "capture-1")),
            "plant-1",
            repository,
        )

        assertNull(viewModel.prepareCameraCapture())
        assertEquals(0, repository.prepareCalls)
        assertEquals("capture-1", viewModel.pendingCaptureToken.value)
    }

    @Test
    fun failedCameraCompletion_discardsTemporaryCaptureAndExposesError() = runTest {
        val repository = FakePhotoRepository().apply {
            completeError = IllegalStateException("copy failed")
        }
        val viewModel = PlantPhotoViewModel(
            SavedStateHandle(mapOf("pending_capture_token" to "capture-1")),
            "plant-1",
            repository,
        )

        viewModel.onCameraResult(success = true)
        advanceUntilIdle()

        assertEquals(listOf("capture-1"), repository.discardedCaptures)
        assertNull(viewModel.pendingCaptureToken.value)
        assertEquals(UiErrorCategory.PhotoImport, viewModel.mutationError.value)
    }

    @Test
    fun failedCaptureDiscard_keepsTokenUntilRetrySucceeds() = runTest {
        val repository = FakePhotoRepository().apply {
            discardError = IllegalStateException("delete failed")
        }
        val viewModel = PlantPhotoViewModel(
            SavedStateHandle(mapOf("pending_capture_token" to "capture-1")),
            "plant-1",
            repository,
        )

        viewModel.onCameraResult(success = false)
        advanceUntilIdle()

        assertEquals("capture-1", viewModel.pendingCaptureToken.value)
        assertEquals(UiErrorCategory.PhotoImport, viewModel.mutationError.value)

        repository.discardError = null
        viewModel.retryPendingCaptureCleanup()
        advanceUntilIdle()

        assertNull(viewModel.pendingCaptureToken.value)
        assertFalse(viewModel.isMutating.value)
    }
}

private class FakePhotoRepository : PlantPhotoRepository {
    val completedCaptures = mutableListOf<Pair<String, String>>()
    val discardedCaptures = mutableListOf<String>()
    val removedPlants = mutableListOf<String>()
    var prepareError: Throwable? = null
    var prepareCalls: Int = 0
    var completeError: Throwable? = null
    var discardError: Throwable? = null
    var removeError: Throwable? = null

    override fun prepareCameraCapture(): com.miguelrodriguez19.stayleafy.data.photo.PendingPhotoCapture {
        prepareCalls += 1
        prepareError?.let { throw it }
        error("prepare is covered by the instrumented FileProvider test")
    }

    override suspend fun completeCameraCapture(plantId: String, token: String): StoredPhoto {
        completeError?.let { throw it }
        completedCaptures += plantId to token
        return StoredPhoto("plant_photos/photo.jpg", File("/tmp/photo.jpg"))
    }

    override suspend fun discardCameraCapture(token: String): Boolean {
        discardError?.let { throw it }
        discardedCaptures += token
        return true
    }

    override suspend fun importImage(plantId: String, sourceUri: Uri): StoredPhoto =
        StoredPhoto("plant_photos/photo.jpg", File("/tmp/photo.jpg"))

    override suspend fun replacePhoto(plantId: String, sourceUri: Uri): StoredPhoto =
        importImage(plantId, sourceUri)

    override suspend fun removePhoto(plantId: String): Boolean {
        removeError?.let { throw it }
        removedPlants += plantId
        return true
    }

    override fun resolvePhoto(relativePath: String): File? = null

    override suspend fun reconcileOrphans(): PhotoOrphanReconciliation =
        PhotoOrphanReconciliation(emptySet(), emptySet())
}
