package com.miguelrodriguez19.stayleafy.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeContrastTest {
    @Test
    fun brandedTextPairsMeetWcagAa() {
        listOf(
            0xFFFFFFFF to 0xFF115129,
            0xFF0A351B to 0xFFD6E8D4,
            0xFF1A1C18 to 0xFFFBF8F1,
            0xFF183728 to 0xFFD8EADD,
            0xFF3D2C00 to 0xFFFFE08A,
            0xFF571407 to 0xFFFFDAD0,
            0xFF003916 to 0xFFA7DCA8,
            0xFFC3F4C3 to 0xFF0D5128,
            0xFFE0E4DC to 0xFF0D1712,
            0xFFC8F2D3 to 0xFF19382A,
            0xFFFFE6A8 to 0xFF3E2E00,
            0xFFFFDAD0 to 0xFF5A1B0D,
            0xFF0E1A15 to 0xFFC97052,
            0xFF0E1A15 to 0xFFE2A08A,
        ).forEach { (foreground, background) ->
            assertTrue(
                "Contrast was ${contrast(foreground, background)} for ${foreground.toString(16)} on ${background.toString(16)}",
                contrast(foreground, background) >= 4.5,
            )
        }
    }

    @Test
    fun unreadAccentIsVisibleAgainstBothAppBackgrounds() {
        assertTrue(contrast(0xFFDF6645, 0xFFFBF8F1) >= 3.0)
        assertTrue(contrast(0xFFFFB59F, 0xFF0D1712) >= 3.0)
    }

    private fun contrast(first: Long, second: Long): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun luminance(color: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF).toDouble() / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
