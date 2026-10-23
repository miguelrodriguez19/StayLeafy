package com.miguelrodriguez19.stayleafy.data.backup

import android.content.Context
import com.miguelrodriguez19.stayleafy.data.local.database.AppDatabase
import com.miguelrodriguez19.stayleafy.data.local.database.BackupDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupBindings {
    @Binds
    @Singleton
    abstract fun bindBackupRepository(repository: DefaultBackupRepository): BackupRepository

    companion object {
        @Provides
        @Singleton
        fun provideBackupDao(database: AppDatabase): BackupDao = database.backupDao()

        @Provides
        @Singleton
        fun provideBackupFilesDirectory(@ApplicationContext context: Context): BackupFiles =
            BackupFiles(
                noBackupDirectory = context.noBackupFilesDir,
                filesDirectory = context.filesDir,
            )
    }
}

data class BackupFiles(
    val noBackupDirectory: java.io.File,
    val filesDirectory: java.io.File,
)
