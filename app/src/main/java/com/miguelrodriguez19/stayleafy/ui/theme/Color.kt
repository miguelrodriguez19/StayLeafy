package com.miguelrodriguez19.stayleafy.ui.theme

import androidx.compose.ui.graphics.Color

/** Botanical Calm palette from the V2 visual direction. */
val LeafPine = Color(0xFF184A36)
val LeafSage = Color(0xFF7FA58A)
val LeafClay = Color(0xFFC97052)
val LeafCream = Color(0xFFF7F3E9)
val LeafPaper = Color(0xFFFFFCF7)
val LeafInk = Color(0xFF1F2923)
val LeafDark = Color(0xFF0E1A15)
val LeafDarkSurface = Color(0xFF17271F)

// Compatibility aliases for callers that still use the original brand names.
val LeafGreen = LeafPine
val LeafTerracotta = LeafClay

internal val LightPrimary = LeafPine
internal val LightOnPrimary = Color.White
internal val LightPrimaryContainer = Color(0xFFD5E8D7)
internal val LightOnPrimaryContainer = Color(0xFF0B291B)
internal val LightSecondary = LeafSage
internal val LightTertiary = LeafClay
internal val LightSurfaceVariant = Color(0xFFE7E4DA)
internal val LightOnSurfaceVariant = Color(0xFF5D675F)
internal val LightOutline = Color(0xFF7D877F)
internal val LightOutlineVariant = Color(0xFFC7CEC8)

internal val DarkPrimary = Color(0xFFA5CFAD)
internal val DarkOnPrimary = Color(0xFF0F2B1D)
internal val DarkPrimaryContainer = Color(0xFF28583F)
internal val DarkOnPrimaryContainer = Color(0xFFD5F1D8)
internal val DarkSecondary = Color(0xFFAFC9B5)
internal val DarkTertiary = Color(0xFFDD8F73)
internal val DarkSurfaceVariant = Color(0xFF26382E)
internal val DarkOnSurfaceVariant = Color(0xFFB7C2BA)
internal val DarkOutline = Color(0xFF8B9A90)
internal val DarkOutlineVariant = Color(0xFF3D5044)

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
    val onAccent: Color,
)

internal val LightExtendedColors = LeafyExtendedColors(
    upcoming = LeafPine,
    onUpcoming = Color(0xFF123423),
    upcomingContainer = Color(0xFFDCEADD),
    due = Color(0xFF755300),
    onDue = Color(0xFF3C2A00),
    dueContainer = Color(0xFFFFE4A8),
    overdue = Color(0xFF9E4B35),
    onOverdue = Color(0xFF4A1B0F),
    overdueContainer = Color(0xFFF5D7CD),
    accent = LeafClay,
    onAccent = LeafDark,
)

internal val DarkExtendedColors = LeafyExtendedColors(
    upcoming = LeafSage,
    onUpcoming = Color(0xFFD6F0DB),
    upcomingContainer = Color(0xFF1F3A2A),
    due = Color(0xFFFFD36A),
    onDue = Color(0xFFFFE6A8),
    dueContainer = Color(0xFF3E2E00),
    overdue = LeafClay,
    onOverdue = Color(0xFFFFDDD4),
    overdueContainer = Color(0xFF57251B),
    accent = Color(0xFFE2A08A),
    onAccent = LeafDark,
)
