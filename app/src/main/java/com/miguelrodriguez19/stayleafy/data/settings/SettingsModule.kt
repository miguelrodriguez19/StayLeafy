package com.miguelrodriguez19.stayleafy.data.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface AppVersionProvider {
    val versionName: String
}

@Singleton
class PackageAppVersionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppVersionProvider {
    override val versionName: String
        get() = runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName.orEmpty()
        }.getOrDefault("")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsBindings {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(repository: PreferencesSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAppLocaleController(controller: AppCompatAppLocaleController): AppLocaleController

    @Binds
    @Singleton
    abstract fun bindAppVersionProvider(provider: PackageAppVersionProvider): AppVersionProvider

    companion object {
        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.settingsDataStore
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "stay_leafy_settings",
)
