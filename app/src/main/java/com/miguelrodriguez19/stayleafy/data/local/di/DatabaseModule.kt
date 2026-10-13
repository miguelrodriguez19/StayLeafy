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

package com.miguelrodriguez19.stayleafy.data.local.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_1_2
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_2_3
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_3_4
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_4_5
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_5_6
import com.miguelrodriguez19.stayleafy.data.local.database.MIGRATION_6_7
import com.miguelrodriguez19.stayleafy.data.local.database.CareEventDao
import com.miguelrodriguez19.stayleafy.data.local.database.NoticeDao
import com.miguelrodriguez19.stayleafy.data.local.database.PlantDao
import com.miguelrodriguez19.stayleafy.data.local.database.PlantNoteDao
import com.miguelrodriguez19.stayleafy.data.local.database.ReminderRuleDao
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {
    @Provides
    fun providePlantDao(appDatabase: AppDatabase): PlantDao {
        return appDatabase.plantDao()
    }

    @Provides
    fun providePlantNoteDao(appDatabase: AppDatabase): PlantNoteDao {
        return appDatabase.plantNoteDao()
    }

    @Provides
    fun provideCareEventDao(appDatabase: AppDatabase): CareEventDao {
        return appDatabase.careEventDao()
    }

    @Provides
    fun provideReminderRuleDao(appDatabase: AppDatabase): ReminderRuleDao {
        return appDatabase.reminderRuleDao()
    }

    @Provides
    fun provideNoticeDao(appDatabase: AppDatabase): NoticeDao {
        return appDatabase.noticeDao()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "Plant"
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
        ).build()
    }
}
