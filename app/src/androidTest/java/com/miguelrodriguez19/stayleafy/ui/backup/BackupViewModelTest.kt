package com.miguelrodriguez19.stayleafy.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miguelrodriguez19.stayleafy.data.backup.BackupCounts
import com.miguelrodriguez19.stayleafy.data.backup.BackupRepository
import com.miguelrodriguez19.stayleafy.data.backup.PreparedRestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.rules.TestWatcher

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = LocalMainDispatcherRule()

    @Test
    fun prepareRestore_exposesPreviewAndConfirmUsesPreviewToken() = runBlocking {
        val repository = FakeBackupRepository()
        val viewModel = BackupViewModel(repository)

        viewModel.prepareRestore(Uri.parse("content://backup.zip"))
        await { viewModel.uiState.value.preparedRestore != null && !viewModel.uiState.value.isBusy }

        assertEquals(repository.preview, viewModel.uiState.value.preparedRestore)

        viewModel.confirmRestore()
        await { viewModel.uiState.value.restoreCompleted }

        assertEquals(listOf(repository.preview.token), repository.confirmedTokens)
        assertEquals(null, viewModel.uiState.value.preparedRestore)
    }

    @Test
    fun invalidRestore_exposesGenericLocalizedErrorCategory() = runBlocking {
        val repository = FakeBackupRepository().apply { failPrepare = true }
        val viewModel = BackupViewModel(repository)

        viewModel.prepareRestore(Uri.parse("content://broken.zip"))
        await { viewModel.uiState.value.error == BackupError.INVALID_BACKUP }

        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun preparingAgain_discardsPreviousPreparedTokenFirst() = runBlocking {
        val repository = FakeBackupRepository()
        val viewModel = BackupViewModel(repository)

        viewModel.prepareRestore(Uri.parse("content://first.zip"))
        await { viewModel.uiState.value.preparedRestore != null && !viewModel.uiState.value.isBusy }
        repository.preview = repository.preview.copy(token = "second-token")

        viewModel.prepareRestore(Uri.parse("content://second.zip"))
        await { viewModel.uiState.value.preparedRestore?.token == "second-token" }

        assertEquals(listOf("restore-token"), repository.discardedTokens)
    }

    @Test
    fun cancellingAfterPreparationReturns_discardsCreatedToken() = runBlocking {
        lateinit var viewModel: BackupViewModel
        val repository = FakeBackupRepository().apply {
            cancelWhenPrepareReturns = { viewModel.cancelOperation() }
        }
        viewModel = BackupViewModel(repository)

        viewModel.prepareRestore(Uri.parse("content://cancelled.zip"))
        await {
            repository.discardedTokens.contains("restore-token") && !viewModel.uiState.value.isBusy
        }

        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun clearingViewModel_discardsPreparedTokenFromIndependentCleanupScope() = runBlocking {
        val repository = FakeBackupRepository()
        val viewModel = BackupViewModel(repository)

        viewModel.prepareRestore(Uri.parse("content://prepared.zip"))
        await { viewModel.uiState.value.preparedRestore != null && !viewModel.uiState.value.isBusy }
        ViewModelStore().also { store ->
            store.put("backup", viewModel)
            store.clear()
        }

        await { repository.discardedTokens.contains("restore-token") }
        assertTrue(repository.discardedTokens.contains("restore-token"))
    }

    private suspend fun await(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) delay(10)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LocalMainDispatcherRule : TestWatcher() {
    private val dispatcher = UnconfinedTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeBackupRepository : BackupRepository {
    var preview = PreparedRestore(
        token = "restore-token",
        exportedAtEpochMillis = 1_700_000_000_000,
        sourceVersionName = "0.11.0",
        counts = BackupCounts(
            plants = 2,
            notes = 3,
            careEvents = 4,
            reminderRules = 5,
            notices = 6,
            photos = 1,
        ),
    )
    val confirmedTokens = mutableListOf<String>()
    val discardedTokens = mutableListOf<String>()
    var failPrepare = false
    var cancelWhenPrepareReturns: (() -> Unit)? = null

    override suspend fun exportBackup(uri: Uri) = Unit

    override suspend fun prepareRestore(uri: Uri): PreparedRestore {
        if (failPrepare) error("invalid")
        cancelWhenPrepareReturns?.invoke()
        return preview
    }

    override suspend fun confirmRestore(token: String) {
        confirmedTokens += token
    }

    override suspend fun discardPreparedRestore(token: String) {
        discardedTokens += token
    }
}
