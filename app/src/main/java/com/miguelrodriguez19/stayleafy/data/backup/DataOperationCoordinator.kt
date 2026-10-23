package com.miguelrodriguez19.stayleafy.data.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

data class DataMaintenanceState(
    val isBusy: Boolean = false,
    val error: Boolean = false,
    val generation: Long = 0L,
)

class DatasetReplacedException(
    val expectedGeneration: Long,
    val actualGeneration: Long,
) : IllegalStateException(
    "The local dataset was replaced while this operation was waiting; retry against the new dataset.",
)

class RecoveryRequiredException : IllegalStateException(
    "Local data recovery is required before another operation can run.",
)

/**
 * Serializes every operation that can observe or mutate the local collection.
 *
 * The context marker is intentional: the backup transaction reaches Room, DataStore and the
 * photo store through several collaborators. Those collaborators must be able to use this same
 * gate without deadlocking when they are called from an already protected operation.
 */
@Singleton
class DataOperationCoordinator @Inject constructor() {
    private val mutex = Mutex()
    private val stateInternal = MutableStateFlow(DataMaintenanceState())
    private val startupLock = Any()
    private var startupReady: CompletableDeferred<Unit> = CompletableDeferred(Unit)
    private var startupBegun = false

    val state: StateFlow<DataMaintenanceState> = stateInternal.asStateFlow()

    /** Marks the point before which startup recovery must complete. Safe to call once. */
    fun beginStartup() {
        synchronized(startupLock) {
            if (startupBegun) return
            startupBegun = true
            if (startupReady.isCompleted) startupReady = CompletableDeferred()
            stateInternal.value = stateInternal.value.copy(isBusy = true, error = false)
        }
    }

    /** Runs startup recovery without waiting on itself. A failed recovery remains retryable. */
    suspend fun initialize(block: suspend () -> Unit) {
        val deferred = synchronized(startupLock) {
            if (startupReady.isCompleted) startupReady = CompletableDeferred()
            if (!startupBegun) {
                startupBegun = true
            }
            startupReady
        }
        // Startup recovery owns the gate and enters the same reentrant context as normal
        // operations. This lets it restore DataStore preferences without waiting for the
        // readiness deferred that it is completing.
        mutex.withLock {
            stateInternal.value = stateInternal.value.copy(isBusy = true, error = false)
            withContext(OperationContext(this@DataOperationCoordinator)) {
                try {
                    withContext(NonCancellable) { block() }
                    deferred.complete(Unit)
                    stateInternal.value = stateInternal.value.copy(
                        isBusy = false,
                        error = false,
                        generation = stateInternal.value.generation + 1,
                    )
                } catch (error: Throwable) {
                    deferred.completeExceptionally(error)
                    stateInternal.value = stateInternal.value.copy(isBusy = false, error = true)
                    throw error
                }
            }
        }
    }

    suspend fun awaitReady() {
        val deferred = synchronized(startupLock) { startupReady }
        deferred.await()
    }

    suspend fun <T> withOperation(block: suspend () -> T): T {
        if (coroutineContext[OperationContext]?.owner === this) return block()
        awaitReady()
        val expectedGeneration = stateInternal.value.generation
        return mutex.withLock {
            checkRecoveryRequired()
            val actualGeneration = stateInternal.value.generation
            if (actualGeneration != expectedGeneration) {
                throw DatasetReplacedException(expectedGeneration, actualGeneration)
            }
            withContext(OperationContext(this)) { block() }
        }
    }

    suspend fun <T> withMaintenance(block: suspend () -> T): T {
        if (coroutineContext[OperationContext]?.owner === this) {
            return runMaintenance(block)
        }
        awaitReady()
        return mutex.withLock {
            checkRecoveryRequired()
            withContext(OperationContext(this)) { runMaintenance(block) }
        }
    }

    private fun checkRecoveryRequired() {
        if (stateInternal.value.error) throw RecoveryRequiredException()
    }

    private suspend fun <T> runMaintenance(block: suspend () -> T): T {
        stateInternal.value = stateInternal.value.copy(isBusy = true, error = false)
        return try {
            val result = withContext(NonCancellable) { block() }
            val previous = stateInternal.value
            stateInternal.value = previous.copy(
                isBusy = false,
                error = false,
                generation = previous.generation + 1,
            )
            result
        } catch (error: Throwable) {
            stateInternal.value = stateInternal.value.copy(isBusy = false, error = true)
            throw error
        }
    }

    private class OperationContext(val owner: DataOperationCoordinator) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<OperationContext>
    }
}
