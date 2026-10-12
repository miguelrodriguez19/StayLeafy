package com.miguelrodriguez19.stayleafy.platform.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val processor: ReminderWorkProcessor,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = when (processor.run()) {
        ReminderRunResult.SUCCESS -> Result.success()
        ReminderRunResult.RETRY -> Result.retry()
    }
}
