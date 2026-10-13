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
    secondaryContainer = Color(0xFF293C31), onSecondaryContainer = Color(0xFFD5E8D1),
    tertiary = DarkTertiary, onTertiary = Color(0xFF5A1B0D),
    tertiaryContainer = Color(0xFF5C2A1D), onTertiaryContainer = Color(0xFFFFDAD0),
    background = LeafDark, onBackground = Color(0xFFE4E9E1),
    surface = LeafDark, onSurface = Color(0xFFE4E9E1),
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceDim = Color(0xFF08120D),
    surfaceBright = Color(0xFF2A3A31),
    surfaceContainerLowest = Color(0xFF07100B),
    surfaceContainerLow = Color(0xFF101F18),
    surfaceContainer = Color(0xFF14241C),
    surfaceContainerHigh = Color(0xFF1A2B22),
    surfaceContainerHighest = Color(0xFF21342A),
    outline = DarkOutline, outlineVariant = DarkOutlineVariant,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary, onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer, onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE7DA), onSecondaryContainer = Color(0xFF182719),
    tertiary = LightTertiary, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD0), onTertiaryContainer = Color(0xFF3B0A02),
    background = LeafCream, onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFFFFCF6), onSurface = Color(0xFF1A1C18),
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightOnSurfaceVariant,
    surfaceDim = Color(0xFFE2DED6),
    surfaceBright = Color(0xFFFFFCF6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F4ED),
    surfaceContainer = Color(0xFFF2EEE7),
    surfaceContainerHigh = Color(0xFFECE8E1),
    surfaceContainerHighest = Color(0xFFE6E2DB),
    outline = LightOutline, outlineVariant = LightOutlineVariant,
    error = Color(0xFFBA1A1A), onError = Color.White,
)

private val LeafyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
