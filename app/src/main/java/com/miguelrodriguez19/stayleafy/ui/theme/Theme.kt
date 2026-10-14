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
    background = LeafDark, onBackground = Color(0xFFE2EAE3),
    surface = LeafDarkSurface, onSurface = Color(0xFFE2EAE3),
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceDim = Color(0xFF08120D),
    surfaceBright = Color(0xFF30453A),
    surfaceContainerLowest = Color(0xFF07100B),
    surfaceContainerLow = Color(0xFF101F18),
    surfaceContainer = Color(0xFF14241C),
    surfaceContainerHigh = Color(0xFF1A2B22),
    surfaceContainerHighest = Color(0xFF21342A),
    outline = DarkOutline, outlineVariant = DarkOutlineVariant,
    error = DarkExtendedColors.accent, onError = DarkExtendedColors.onAccent,
    errorContainer = Color(0xFF57251B), onErrorContainer = Color(0xFFFFDDD4),
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary, onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer, onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE7DA), onSecondaryContainer = Color(0xFF182719),
    tertiary = LightTertiary, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD0), onTertiaryContainer = Color(0xFF3B0A02),
    background = LeafCream, onBackground = LeafInk,
    surface = LeafPaper, onSurface = LeafInk,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightOnSurfaceVariant,
    surfaceDim = Color(0xFFE2DED6),
    surfaceBright = LeafPaper,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F4ED),
    surfaceContainer = Color(0xFFF2EEE7),
    surfaceContainerHigh = Color(0xFFECE8E1),
    surfaceContainerHighest = Color(0xFFE6E2DB),
    outline = LightOutline, outlineVariant = LightOutlineVariant,
    error = LightExtendedColors.accent, onError = LightExtendedColors.onAccent,
    errorContainer = Color(0xFFF5D7CD), onErrorContainer = Color(0xFF4A1B0F),
)

private val LeafyShapes = Shapes(
    extraSmall = RoundedCornerShape(leafyLayoutTokens.radiusXs),
    small = RoundedCornerShape(leafyLayoutTokens.radiusSm),
    medium = RoundedCornerShape(leafyLayoutTokens.radiusMd),
    large = RoundedCornerShape(leafyLayoutTokens.radiusLg),
    extraLarge = RoundedCornerShape(leafyLayoutTokens.radiusXl),
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
