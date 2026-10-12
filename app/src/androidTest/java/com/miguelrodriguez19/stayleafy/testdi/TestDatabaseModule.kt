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

package com.miguelrodriguez19.stayleafy.testdi

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import com.miguelrodriguez19.stayleafy.data.CareEventRepository
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.NoticeRepository
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.data.PlantNoteRepository
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import com.miguelrodriguez19.stayleafy.data.ZoneIdProvider
import com.miguelrodriguez19.stayleafy.model.AppNotice
import com.miguelrodriguez19.stayleafy.model.NoticeDeliveryState
import com.miguelrodriguez19.stayleafy.data.photo.PlantPhotoRepository
import com.miguelrodriguez19.stayleafy.data.di.DataModule
import javax.inject.Singleton
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneId

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataModule::class]
)
interface FakeDataModule {

    @Singleton
    @Binds
    abstract fun bindRepository(
        fakeRepository: FakePlantRepository
    ): PlantRepository

    @Singleton
    @Binds
    abstract fun bindNoteRepository(
        fakeRepository: FakePlantNoteRepository,
    ): PlantNoteRepository

    @Singleton
    @Binds
    abstract fun bindPhotoRepository(
        fakeRepository: FakePlantPhotoRepository,
    ): PlantPhotoRepository

    @Singleton
    @Binds
    abstract fun bindCareEventRepository(
        fakeRepository: FakeCareEventRepository,
    ): CareEventRepository

    @Singleton
    @Binds
    abstract fun bindEpochMillisProvider(
        provider: FakeEpochMillisProvider,
    ): EpochMillisProvider

    @Singleton
    @Binds
    abstract fun bindNoticeRepository(
        repository: FakeNoticeRepository,
    ): NoticeRepository

    @Singleton
    @Binds
    abstract fun bindUuidProvider(
        provider: FakeUuidProvider,
    ): UuidProvider

    @Singleton
    @Binds
    abstract fun bindZoneIdProvider(
        provider: FakeZoneIdProvider,
    ): ZoneIdProvider
}

@Singleton
class FakeEpochMillisProvider @Inject constructor() : EpochMillisProvider {
    override fun now(): Long = System.currentTimeMillis()
}

@Singleton
class FakeUuidProvider @Inject constructor() : UuidProvider {
    private var next = 0L
    override fun newUuid(): String = "test-uuid-${next++}"
}

@Singleton
class FakeZoneIdProvider @Inject constructor() : ZoneIdProvider {
    override fun currentZoneId(): ZoneId = ZoneId.of("Europe/Madrid")
}

@Singleton
class FakeNoticeRepository @Inject constructor() : NoticeRepository {
    private val notices = MutableStateFlow<List<AppNotice>>(emptyList())

    override fun observeNotices(): Flow<List<AppNotice>> = notices
    override fun observeNotice(id: String): Flow<AppNotice?> =
        MutableStateFlow(notices.value.firstOrNull { it.id == id })
    override fun observeUnreadCount(): Flow<Int> = MutableStateFlow(0)
    override suspend fun createPendingNotice(ruleId: String): String? = null
    override suspend fun setDeliveryState(id: String, state: NoticeDeliveryState) = Unit
    override suspend fun markRead(id: String) = Unit
    override suspend fun markAllRead() = Unit
}
