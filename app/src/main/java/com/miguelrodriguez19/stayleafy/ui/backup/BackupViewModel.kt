package com.miguelrodriguez19.stayleafy.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miguelrodriguez19.stayleafy.data.backup.BackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var operationJob: Job? = null
    private val preparedToken = AtomicReference<String?>(null)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun exportBackup(uri: Uri) {
        if (_uiState.value.isBusy) return
        startOperation(BackupOperation.EXPORT)
        launchOperation {
            try {
                withContext(Dispatchers.IO) { repository.exportBackup(uri) }
                _uiState.update { it.copy(exportCompleted = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(error = BackupError.EXPORT) }
            } finally {
                finishOperation()
            }
        }
    }

    fun prepareRestore(uri: Uri) {
        if (_uiState.value.isBusy) return
        val previousToken = _uiState.value.preparedRestore?.token
        startOperation(BackupOperation.PREPARE_RESTORE)
        _uiState.update { it.copy(preparedRestore = null) }
        launchOperation {
            var createdToken: String? = null
            var handedOffToUi = false
            try {
                previousToken?.let { token ->
                    withContext(NonCancellable + Dispatchers.IO) {
                        repository.discardPreparedRestore(token)
                    }
                    preparedToken.compareAndSet(token, null)
                }
                val prepared = withContext(Dispatchers.IO) {
                    val result = repository.prepareRestore(uri)
                    createdToken = result.token
                    preparedToken.set(result.token)
                    result
                }
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(preparedRestore = prepared) }
                handedOffToUi = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(error = BackupError.INVALID_BACKUP) }
            } finally {
                try {
                    if (!handedOffToUi) {
                        createdToken?.let { token -> discardPreparedTokenIfOwned(token) }
                    }
                } finally {
                    // Returning from non-cancellable IO cleanup can rethrow cancellation.
                    // The visible operation must still leave its busy state.
                    finishOperation()
                }
            }
        }
    }

    fun confirmRestore() {
        val token = _uiState.value.preparedRestore?.token ?: return
        if (_uiState.value.isBusy) return
        startOperation(BackupOperation.RESTORE)
        launchOperation {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    repository.confirmRestore(token)
                }
                _uiState.update {
                    it.copy(
                        preparedRestore = null,
                        restoreCompleted = true,
                    )
                }
                preparedToken.compareAndSet(token, null)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(error = BackupError.RESTORE) }
            } finally {
                finishOperation()
            }
        }
    }

    fun discardPreparedRestore() {
        val token = _uiState.value.preparedRestore?.token ?: return
        if (_uiState.value.isBusy) return
        startOperation(BackupOperation.PREPARE_RESTORE)
        launchOperation {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    repository.discardPreparedRestore(token)
                }
                preparedToken.compareAndSet(token, null)
                _uiState.update { it.copy(preparedRestore = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(error = BackupError.RESTORE) }
            } finally {
                finishOperation()
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun cancelOperation() {
        when (_uiState.value.operation) {
            BackupOperation.EXPORT,
            BackupOperation.PREPARE_RESTORE,
            -> operationJob?.cancel()
            BackupOperation.RESTORE,
            null,
            -> Unit
        }
    }

    fun acknowledgeExportCompleted() {
        _uiState.update { it.copy(exportCompleted = false) }
    }

    fun acknowledgeRestoreCompleted() {
        _uiState.update { it.copy(restoreCompleted = false) }
    }

    private fun startOperation(operation: BackupOperation) {
        operationJob?.cancel()
        _uiState.update {
            it.copy(
                isBusy = true,
                operation = operation,
                error = null,
                exportCompleted = false,
            )
        }
    }

    private fun finishOperation() {
        _uiState.update { it.copy(isBusy = false, operation = null) }
    }

    private fun launchOperation(block: suspend CoroutineScope.() -> Unit) {
        // Register the job before it can complete or request cancellation on a fast IO path.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY, block = block)
        operationJob = job
        job.start()
    }

    private suspend fun discardPreparedTokenIfOwned(token: String) {
        if (!preparedToken.compareAndSet(token, null)) return
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { repository.discardPreparedRestore(token) }
        }
    }

    override fun onCleared() {
        val operation = _uiState.value.operation
        operationJob?.cancel()
        if (operation != BackupOperation.RESTORE) {
            val token = preparedToken.getAndSet(null)
                ?: _uiState.value.preparedRestore?.token
            token?.let { cleanupToken ->
                cleanupScope.launch {
                    runCatching { repository.discardPreparedRestore(cleanupToken) }
                }
            }
        }
    }
}
