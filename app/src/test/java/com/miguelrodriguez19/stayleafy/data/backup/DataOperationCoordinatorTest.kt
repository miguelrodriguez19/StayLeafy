package com.miguelrodriguez19.stayleafy.data.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataOperationCoordinatorTest {

    @Test
    fun sameCoordinatorReentersWithoutDeadlocking() = runTest {
        val coordinator = DataOperationCoordinator()
        var calls = 0

        withTimeout(TIMEOUT_MILLIS) {
            coordinator.withOperation {
                calls += 1
                coordinator.withOperation { calls += 1 }
            }
        }

        assertEquals(2, calls)
    }

    @Test
    fun independentInstancesDoNotBypassEachOthersMutex() = runTest {
        val outer = DataOperationCoordinator()
        val independent = DataOperationCoordinator()
        val independentEntered = CompletableDeferred<Unit>()
        val releaseIndependent = CompletableDeferred<Unit>()
        val independentHolder = async {
            independent.withOperation {
                independentEntered.complete(Unit)
                releaseIndependent.await()
            }
        }
        awaitWithin(independentEntered)

        val nestedEntered = CompletableDeferred<Unit>()
        val nested = async {
            outer.withOperation {
                independent.withOperation { nestedEntered.complete(Unit) }
            }
        }
        runCurrent()
        assertFalse(nestedEntered.isCompleted)

        releaseIndependent.complete(Unit)
        awaitWithin(independentHolder)
        awaitWithin(nested)
        assertTrue(nestedEntered.isCompleted)
    }

    @Test
    fun parallelWritesAndMaintenanceAreSerialized() = runTest {
        val coordinator = DataOperationCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var active = 0
        var maximumActive = 0

        fun enter(label: String) {
            active += 1
            maximumActive = maxOf(maximumActive, active)
            events += "$label-start"
        }

        fun leave(label: String) {
            events += "$label-end"
            active -= 1
        }

        val first = async {
            coordinator.withOperation {
                enter("write-1")
                firstEntered.complete(Unit)
                releaseFirst.await()
                leave("write-1")
            }
        }
        awaitWithin(firstEntered)

        val second = async {
            coordinator.withOperation {
                enter("write-2")
                leave("write-2")
            }
        }
        val maintenance = async {
            coordinator.withMaintenance {
                enter("maintenance")
                leave("maintenance")
            }
        }

        runCurrent()
        assertEquals(listOf("write-1-start"), events)

        releaseFirst.complete(Unit)
        awaitWithin(first)
        awaitWithin(second)
        awaitWithin(maintenance)

        assertEquals(1, maximumActive)
        assertEquals(0, active)
        assertEquals(6, events.size)
        assertTrue(events.indexOf("write-1-end") < events.indexOf("write-2-start"))
        assertTrue(events.indexOf("write-1-end") < events.indexOf("maintenance-start"))
    }

    @Test
    fun operationsWaitForStartupInitialization() = runTest {
        val coordinator = DataOperationCoordinator()
        coordinator.beginStartup()
        val writeEntered = CompletableDeferred<Unit>()
        val releaseInitialization = CompletableDeferred<Unit>()
        val initializationEntered = CompletableDeferred<Unit>()

        val write = async {
            coordinator.withOperation { writeEntered.complete(Unit) }
        }
        runCurrent()
        assertFalse(writeEntered.isCompleted)

        val initialization = async {
            coordinator.initialize {
                initializationEntered.complete(Unit)
                releaseInitialization.await()
            }
        }
        awaitWithin(initializationEntered)
        assertFalse(writeEntered.isCompleted)

        releaseInitialization.complete(Unit)
        awaitWithin(initialization)
        awaitWithin(write)

        assertTrue(writeEntered.isCompleted)
        assertEquals(1L, coordinator.state.value.generation)
        assertFalse(coordinator.state.value.isBusy)
    }

    @Test
    fun failedInitializationCanRetryAndThenReleasesWaitingWrites() = runTest {
        val coordinator = DataOperationCoordinator()
        coordinator.beginStartup()
        val failure = IllegalStateException("recovery failed")

        val thrown = runCatching {
            coordinator.initialize { throw failure }
        }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertEquals(failure.message, thrown?.message)
        assertEquals(failure.javaClass, thrown?.javaClass)
        assertEquals(
            DataMaintenanceState(isBusy = false, error = true, generation = 0L),
            coordinator.state.value,
        )

        val retryEntered = CompletableDeferred<Unit>()
        val releaseRetry = CompletableDeferred<Unit>()
        val retry = async {
            coordinator.initialize {
                retryEntered.complete(Unit)
                releaseRetry.await()
            }
        }
        awaitWithin(retryEntered)

        val writeEntered = CompletableDeferred<Unit>()
        val write = async {
            coordinator.withOperation { writeEntered.complete(Unit) }
        }
        runCurrent()
        assertFalse(writeEntered.isCompleted)

        releaseRetry.complete(Unit)
        awaitWithin(retry)
        awaitWithin(write)

        assertTrue(writeEntered.isCompleted)
        assertEquals(
            DataMaintenanceState(isBusy = false, error = false, generation = 1L),
            coordinator.state.value,
        )
    }

    @Test
    fun maintenanceStateTracksBusyErrorAndGeneration() = runTest {
        val coordinator = DataOperationCoordinator()
        assertEquals(DataMaintenanceState(), coordinator.state.value)

        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val successfulMaintenance = async {
            coordinator.withMaintenance {
                entered.complete(Unit)
                release.await()
            }
        }
        awaitWithin(entered)
        assertEquals(
            DataMaintenanceState(isBusy = true, error = false, generation = 0L),
            coordinator.state.value,
        )
        release.complete(Unit)
        awaitWithin(successfulMaintenance)
        assertEquals(
            DataMaintenanceState(isBusy = false, error = false, generation = 1L),
            coordinator.state.value,
        )

        val failure = IllegalStateException("maintenance failed")
        val thrown = runCatching {
            coordinator.withMaintenance { throw failure }
        }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertEquals(failure.message, thrown?.message)
        assertEquals(failure.javaClass, thrown?.javaClass)
        assertEquals(
            DataMaintenanceState(isBusy = false, error = true, generation = 1L),
            coordinator.state.value,
        )

        val blockedWrite = runCatching { coordinator.withOperation { Unit } }.exceptionOrNull()
        assertTrue(blockedWrite is RecoveryRequiredException)

        val recoveryEntered = CompletableDeferred<Unit>()
        val recoveryRelease = CompletableDeferred<Unit>()
        val recovery = async {
            coordinator.initialize {
                recoveryEntered.complete(Unit)
                recoveryRelease.await()
            }
        }
        awaitWithin(recoveryEntered)
        assertEquals(
            DataMaintenanceState(isBusy = true, error = false, generation = 1L),
            coordinator.state.value,
        )
        recoveryRelease.complete(Unit)
        awaitWithin(recovery)
        assertEquals(
            DataMaintenanceState(isBusy = false, error = false, generation = 2L),
            coordinator.state.value,
        )
    }

    @Test
    fun writeQueuedDuringMaintenanceFailsAsStaleAfterGenerationChanges() = runTest {
        val coordinator = DataOperationCoordinator()
        val maintenanceEntered = CompletableDeferred<Unit>()
        val releaseMaintenance = CompletableDeferred<Unit>()
        val maintenance = async {
            coordinator.withMaintenance {
                maintenanceEntered.complete(Unit)
                releaseMaintenance.await()
            }
        }
        awaitWithin(maintenanceEntered)

        var writeEntered = false
        var stale: DatasetReplacedException? = null
        val queuedWrite = async {
            try {
                coordinator.withOperation { writeEntered = true }
            } catch (error: DatasetReplacedException) {
                stale = error
            }
        }
        runCurrent()
        assertFalse(writeEntered)

        releaseMaintenance.complete(Unit)
        awaitWithin(maintenance)
        awaitWithin(queuedWrite)

        val exception: DatasetReplacedException =
            stale ?: throw AssertionError("Expected the queued write to become stale")
        assertEquals(0L, exception.expectedGeneration)
        assertEquals(1L, exception.actualGeneration)
        assertFalse(writeEntered)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 1_000L
    }

    private suspend fun <T> awaitWithin(deferred: Deferred<T>): T =
        withTimeout(TIMEOUT_MILLIS) { deferred.await() }

}
