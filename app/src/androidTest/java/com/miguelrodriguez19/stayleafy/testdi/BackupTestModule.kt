package com.miguelrodriguez19.stayleafy.testdi

import com.miguelrodriguez19.stayleafy.data.photo.AndroidPhotoFileStore
import com.miguelrodriguez19.stayleafy.data.photo.PhotoFileStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** DataModule is replaced by UI fakes; the backup adapter still needs a managed file store. */
@Module
@InstallIn(SingletonComponent::class)
interface BackupTestModule {
    @Binds
    @Singleton
    fun bindBackupPhotoStore(store: AndroidPhotoFileStore): PhotoFileStore
}
