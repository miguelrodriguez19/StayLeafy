/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miguelrodriguez19.stayleafy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.miguelrodriguez19.stayleafy.data.NoticeRepository
import com.miguelrodriguez19.stayleafy.data.photo.PlantPhotoRepository
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderNotificationGateway
import com.miguelrodriguez19.stayleafy.platform.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@HiltAndroidApp
class StayLeafy : Application(), Configuration.Provider {
    @Inject
    lateinit var plantPhotoRepository: PlantPhotoRepository

    @Inject
    lateinit var reminderNotificationGateway: ReminderNotificationGateway

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var noticeRepository: NoticeRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        reminderNotificationGateway.ensureChannel()
        reminderScheduler.ensureDailyEvaluationScheduled()
        applicationScope.launch {
            var displayedNoticeIds = emptySet<String>()
            noticeRepository.observeNotices().collect { notices ->
                val currentlyDisplayed = notices
                    .filter { it.deliveryState == NoticeDeliveryState.DELIVERED && it.readAt == null }
                    .mapTo(mutableSetOf()) { it.id }
                val resolvedOrRemoved = displayedNoticeIds - currentlyDisplayed
                val staleAfterRestart = notices
                    .filter { it.deliveryState == NoticeDeliveryState.DELIVERED && it.readAt != null }
                    .map { it.id }
                (resolvedOrRemoved + staleAfterRestart).forEach(reminderNotificationGateway::cancel)
                displayedNoticeIds = currentlyDisplayed
            }
        }
        applicationScope.launch {
            runCatching { plantPhotoRepository.reconcileOrphans() }
        }
    }
}
