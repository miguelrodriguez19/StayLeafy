package com.miguelrodriguez19.stayleafy.ui.theme

import androidx.compose.ui.graphics.Color

val LeafGreen = Color(0xFF115129)
val LeafTerracotta = Color(0xFFDF6645)
val LeafCream = Color(0xFFFBF8F1)
val LeafDark = Color(0xFF0D1712)

internal val LightPrimary = LeafGreen
internal val LightOnPrimary = Color.White
internal val LightPrimaryContainer = Color(0xFFD6E8D4)
internal val LightOnPrimaryContainer = Color(0xFF0A351B)
internal val LightSecondary = Color(0xFF526451)
internal val LightTertiary = Color(0xFF98462F)
internal val LightSurfaceVariant = Color(0xFFE2E4DB)
internal val LightOnSurfaceVariant = Color(0xFF43483F)
internal val LightOutline = Color(0xFF737970)

internal val DarkPrimary = Color(0xFFA7DCA8)
internal val DarkOnPrimary = Color(0xFF003916)
internal val DarkPrimaryContainer = Color(0xFF0D5128)
internal val DarkOnPrimaryContainer = Color(0xFFC3F4C3)
internal val DarkSecondary = Color(0xFFB9CCB5)
internal val DarkTertiary = Color(0xFFFFB59F)
internal val DarkSurfaceVariant = Color(0xFF414941)
internal val DarkOnSurfaceVariant = Color(0xFFC1C9BF)
internal val DarkOutline = Color(0xFF8B9389)

data class LeafyExtendedColors(
    val upcoming: Color,
    val onUpcoming: Color,
    val upcomingContainer: Color,
    val due: Color,
    val onDue: Color,
    val dueContainer: Color,
    val overdue: Color,
    val onOverdue: Color,
    val overdueContainer: Color,
    val accent: Color,
)

internal val LightExtendedColors = LeafyExtendedColors(
    upcoming = Color(0xFF3F6250),
    onUpcoming = Color(0xFF183728),
    upcomingContainer = Color(0xFFD8EADD),
    due = Color(0xFF7A5800),
    onDue = Color(0xFF3D2C00),
    dueContainer = Color(0xFFFFE08A),
    overdue = Color(0xFFA23F2B),
    onOverdue = Color(0xFF571407),
    overdueContainer = Color(0xFFFFDAD0),
    accent = LeafTerracotta,
)

internal val DarkExtendedColors = LeafyExtendedColors(
    upcoming = Color(0xFFA7D3B3),
    onUpcoming = Color(0xFFC8F2D3),
    upcomingContainer = Color(0xFF19382A),
    due = Color(0xFFFFD36A),
    onDue = Color(0xFFFFE6A8),
    dueContainer = Color(0xFF3E2E00),
    overdue = Color(0xFFFFB59F),
    onOverdue = Color(0xFFFFDAD0),
    overdueContainer = Color(0xFF5A1B0D),
    accent = Color(0xFFFFB59F),
)
