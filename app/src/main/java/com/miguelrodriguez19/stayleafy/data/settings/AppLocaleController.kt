package com.miguelrodriguez19.stayleafy.data.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface AppLocaleController {
    fun current(): AppLanguage = AppLanguage.SYSTEM

    fun apply(language: AppLanguage)
}

class AppCompatAppLocaleController @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppLocaleController {
    override fun current(): AppLanguage {
        val tags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
        val tag = tags
            .substringBefore(',')
            .substringBefore('-')
        return AppLanguage.entries.firstOrNull { it.localeTag == tag } ?: AppLanguage.SYSTEM
    }

    override fun apply(language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Recovery also runs with no Activity/delegate attached during Application startup.
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language.localeTag)
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.localeTag))
        }
    }
}
