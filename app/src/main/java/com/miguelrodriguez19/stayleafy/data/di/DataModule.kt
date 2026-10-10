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

package com.miguelrodriguez19.stayleafy.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.miguelrodriguez19.stayleafy.data.PlantRepository
import com.miguelrodriguez19.stayleafy.data.DefaultPlantRepository
import com.miguelrodriguez19.stayleafy.data.DefaultPlantNoteRepository
import com.miguelrodriguez19.stayleafy.data.PlantNoteRepository
import com.miguelrodriguez19.stayleafy.data.EpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.SystemEpochMillisProvider
import com.miguelrodriguez19.stayleafy.data.RandomUuidProvider
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import com.miguelrodriguez19.stayleafy.data.photo.AndroidPhotoFileStore
import com.miguelrodriguez19.stayleafy.data.photo.ContentResolverPhotoInputResolver
import com.miguelrodriguez19.stayleafy.data.photo.DefaultPlantPhotoRepository
import com.miguelrodriguez19.stayleafy.data.photo.PhotoFileStore
import com.miguelrodriguez19.stayleafy.data.photo.PhotoInputResolver
import com.miguelrodriguez19.stayleafy.data.photo.PlantPhotoOwnerPort
import com.miguelrodriguez19.stayleafy.data.photo.PlantPhotoRepository
import com.miguelrodriguez19.stayleafy.data.photo.RoomPlantPhotoOwnerPort
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Singleton
    @Binds
    abstract fun bindsPlantRepository(
        plantRepository: DefaultPlantRepository
    ): PlantRepository

    @Singleton
    @Binds
    abstract fun bindsPlantNoteRepository(
        repository: DefaultPlantNoteRepository,
    ): PlantNoteRepository

    @Singleton
    @Binds
    abstract fun bindsPlantPhotoRepository(
        repository: DefaultPlantPhotoRepository,
    ): PlantPhotoRepository

    @Singleton
    @Binds
    abstract fun bindsPhotoFileStore(
        store: AndroidPhotoFileStore,
    ): PhotoFileStore

    @Singleton
    @Binds
    abstract fun bindsPhotoInputResolver(
        resolver: ContentResolverPhotoInputResolver,
    ): PhotoInputResolver

    @Singleton
    @Binds
    abstract fun bindsPlantPhotoOwnerPort(
        port: RoomPlantPhotoOwnerPort,
    ): PlantPhotoOwnerPort

    @Singleton
    @Binds
    abstract fun bindsEpochMillisProvider(
        provider: SystemEpochMillisProvider,
    ): EpochMillisProvider

    @Singleton
    @Binds
    abstract fun bindsUuidProvider(
        provider: RandomUuidProvider,
    ): UuidProvider
}
