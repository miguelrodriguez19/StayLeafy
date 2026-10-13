package com.miguelrodriguez19.stayleafy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary, onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer, onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary, onSecondary = Color(0xFF253426),
    tertiary = DarkTertiary, onTertiary = Color(0xFF5A1B0D),
    background = LeafDark, onBackground = Color(0xFFE0E4DC),
    surface = LeafDark, onSurface = Color(0xFFE0E4DC),
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline, error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary, onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer, onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary, onSecondary = Color.White,
    tertiary = LightTertiary, onTertiary = Color.White,
    background = LeafCream, onBackground = Color(0xFF1A1C18),
    surface = LeafCream, onSurface = Color(0xFF1A1C18),
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline, error = Color(0xFFBA1A1A), onError = Color.White,
)

private val LeafyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

val LocalLeafyColors = staticCompositionLocalOf { LightExtendedColors }
val MaterialTheme.leafyColors: LeafyExtendedColors
    @Composable get() = LocalLeafyColors.current

@Suppress("UNUSED_PARAMETER")
@Composable
fun StayLeafyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Kept for source compatibility with deterministic previews. Brand colors are always used.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLeafyColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = LeafyShapes,
            content = content,
        )
    }
}
