package com.miguelrodriguez19.stayleafy.ui.plant

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.photo.PendingPhotoCapture
import com.miguelrodriguez19.stayleafy.data.photo.PlantPhotoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlantPhotoViewModel.Factory::class)
class PlantPhotoViewModel @AssistedInject constructor(
    private val savedStateHandle: SavedStateHandle,
    @Assisted private val plantId: String,
    private val repository: PlantPhotoRepository,
) : ViewModel() {
    private val _pendingCaptureToken = MutableStateFlow(
        savedStateHandle.get<String>(PENDING_CAPTURE_KEY),
    )
    val pendingCaptureToken: StateFlow<String?> = _pendingCaptureToken.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _mutationError = MutableStateFlow<UiErrorCategory?>(null)
    val mutationError: StateFlow<UiErrorCategory?> = _mutationError.asStateFlow()

    fun prepareCameraCapture(): PendingPhotoCapture? {
        if (_pendingCaptureToken.value != null || _isMutating.value) return null
        return try {
            repository.prepareCameraCapture().also { capture ->
                savedStateHandle[PENDING_CAPTURE_KEY] = capture.token
                _pendingCaptureToken.value = capture.token
                _mutationError.value = null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            _mutationError.value = UiErrorCategory.PhotoImport
            null
        }
    }

    fun onCameraResult(success: Boolean) {
        val token = _pendingCaptureToken.value ?: return
        if (_isMutating.value) return
        _isMutating.value = true
        _mutationError.value = null
        viewModelScope.launch {
            try {
                if (success) repository.completeCameraCapture(plantId, token)
                else repository.discardCameraCapture(token)
                clearPendingCapture()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (success && discardCaptureBestEffort(token)) clearPendingCapture()
                _mutationError.value = UiErrorCategory.PhotoImport
            } finally {
                _isMutating.value = false
            }
        }
    }

    fun onCameraLaunchFailed() {
        val token = _pendingCaptureToken.value
        if (token == null) {
            _mutationError.value = UiErrorCategory.PhotoImport
            return
        }
        if (_isMutating.value) return
        _isMutating.value = true
        viewModelScope.launch {
            try {
                if (discardCaptureBestEffort(token)) clearPendingCapture()
            } finally {
                _mutationError.value = UiErrorCategory.PhotoImport
                _isMutating.value = false
            }
        }
    }

    fun retryPendingCaptureCleanup() {
        if (_pendingCaptureToken.value != null) onCameraResult(success = false)
    }

    fun onPickerLaunchFailed() {
        _mutationError.value = UiErrorCategory.PhotoImport
    }

    fun importPhoto(uri: Uri) {
        mutate(UiErrorCategory.PhotoImport) {
            repository.importImage(plantId, uri)
        }
    }

    fun removePhoto() {
        mutate(UiErrorCategory.PhotoDelete) {
            repository.removePhoto(plantId)
        }
    }

    fun resolvePhoto(relativePath: String): File? = repository.resolvePhoto(relativePath)

    private fun clearPendingCapture() {
        savedStateHandle[PENDING_CAPTURE_KEY] = null
        _pendingCaptureToken.value = null
    }

    private fun mutate(category: UiErrorCategory, action: suspend () -> Any?) {
        if (_isMutating.value) return
        _isMutating.value = true
        _mutationError.value = null
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _mutationError.value = category
            } finally {
                _isMutating.value = false
            }
        }
    }

    private suspend fun discardCaptureBestEffort(token: String): Boolean =
        try {
            repository.discardCameraCapture(token)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // The original mutation error is more useful to the UI. The file store also attempts
            // cleanup when completion itself fails.
            false
        }

    @AssistedFactory
    interface Factory {
        fun create(plantId: String): PlantPhotoViewModel
    }

    private companion object {
        const val PENDING_CAPTURE_KEY = "pending_capture_token"
    }
}
