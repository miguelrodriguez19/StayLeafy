package com.miguelrodriguez19.stayleafy.data.settings

import android.app.LocaleManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLocaleControllerTest {
    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun applicationContextAppliesRestoredLanguageWithoutAnActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(LocaleManager::class.java)
        val previous = manager.applicationLocales
        val controller = AppCompatAppLocaleController(context)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync { controller.apply(AppLanguage.SPANISH) }
            assertEquals("es", manager.applicationLocales.toLanguageTags())
            assertEquals(AppLanguage.SPANISH, controller.current())
            instrumentation.runOnMainSync { controller.apply(AppLanguage.SYSTEM) }
            assertEquals("", manager.applicationLocales.toLanguageTags())
            assertEquals(AppLanguage.SYSTEM, controller.current())
        } finally {
            instrumentation.runOnMainSync { manager.applicationLocales = previous }
        }
    }
}
