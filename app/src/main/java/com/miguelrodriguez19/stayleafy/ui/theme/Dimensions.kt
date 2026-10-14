package com.miguelrodriguez19.stayleafy.ui.theme

import androidx.compose.ui.unit.dp

data class LeafyLayoutTokens(
    val spacingXs: androidx.compose.ui.unit.Dp = 4.dp,
    val spacingSm: androidx.compose.ui.unit.Dp = 8.dp,
    val spacingMd: androidx.compose.ui.unit.Dp = 12.dp,
    val spacingLg: androidx.compose.ui.unit.Dp = 16.dp,
    val spacingXl: androidx.compose.ui.unit.Dp = 24.dp,
    val spacingXxl: androidx.compose.ui.unit.Dp = 32.dp,
    val radiusXs: androidx.compose.ui.unit.Dp = 8.dp,
    val radiusSm: androidx.compose.ui.unit.Dp = 12.dp,
    val radiusMd: androidx.compose.ui.unit.Dp = 16.dp,
    val radiusLg: androidx.compose.ui.unit.Dp = 20.dp,
    val radiusXl: androidx.compose.ui.unit.Dp = 28.dp,
    val controlHeight: androidx.compose.ui.unit.Dp = 48.dp,
    val navigationHeight: androidx.compose.ui.unit.Dp = 64.dp,
    val thumbnailSize: androidx.compose.ui.unit.Dp = 72.dp,
)

val leafyLayoutTokens = LeafyLayoutTokens()

object LeafySpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
