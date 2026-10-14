package com.miguelrodriguez19.stayleafy.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import javax.inject.Inject

interface AppLocaleController {
    fun current(): AppLanguage = AppLanguage.SYSTEM

    fun apply(language: AppLanguage)
}

class AppCompatAppLocaleController @Inject constructor() : AppLocaleController {
    override fun current(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .substringBefore(',')
            .substringBefore('-')
        return AppLanguage.entries.firstOrNull { it.localeTag == tag } ?: AppLanguage.SYSTEM
    }

    override fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.localeTag),
        )
    }
}
