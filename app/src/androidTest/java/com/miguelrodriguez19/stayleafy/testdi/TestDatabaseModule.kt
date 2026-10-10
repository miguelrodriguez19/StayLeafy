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
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.data.PlantNoteRepository
import com.miguelrodriguez19.stayleafy.data.photo.PlantPhotoRepository
import com.miguelrodriguez19.stayleafy.data.di.DataModule
import javax.inject.Singleton

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
}
