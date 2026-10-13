package com.miguelrodriguez19.stayleafy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.miguelrodriguez19.stayleafy.R

private val Lora = FontFamily(
    Font(R.font.lora_variable, FontWeight.Normal),
    Font(R.font.lora_variable, FontWeight.SemiBold),
)
private val BaseTypography = Typography()

val Typography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(lineHeight = 20.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)
