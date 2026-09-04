package com.nikac.guider.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.nikac.guider.R

val bodyFontFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)

val displayFontFamily = FontFamily(
    Font(R.font.manrope_semi_bold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

val AppTypography = run {
    val baseline = Typography()
    Typography(
        displayLarge = baseline.displayLarge.copy(fontFamily = displayFontFamily, fontWeight = FontWeight.SemiBold),
        displayMedium = baseline.displayMedium.copy(fontFamily = displayFontFamily, fontWeight = FontWeight.SemiBold),
        displaySmall = baseline.displaySmall.copy(fontFamily = displayFontFamily, fontWeight = FontWeight.SemiBold),
        headlineLarge = baseline.headlineLarge.copy(fontFamily = displayFontFamily, fontWeight = FontWeight.SemiBold),
        headlineMedium = baseline.headlineMedium.copy(fontFamily = displayFontFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = baseline.headlineSmall.copy(fontFamily = displayFontFamily, fontWeight = FontWeight.SemiBold),
        titleLarge = baseline.titleLarge.copy(fontFamily = displayFontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = baseline.titleMedium.copy(fontFamily = displayFontFamily, fontWeight = FontWeight.SemiBold),
        titleSmall = baseline.titleSmall.copy(fontFamily = displayFontFamily, fontWeight = FontWeight.SemiBold),
        bodyLarge = baseline.bodyLarge.copy(fontFamily = bodyFontFamily),
        bodyMedium = baseline.bodyMedium.copy(fontFamily = bodyFontFamily),
        bodySmall = baseline.bodySmall.copy(fontFamily = bodyFontFamily),
        labelLarge = baseline.labelLarge.copy(fontFamily = bodyFontFamily),
        labelMedium = baseline.labelMedium.copy(fontFamily = bodyFontFamily),
        labelSmall = baseline.labelSmall.copy(fontFamily = bodyFontFamily),
    )
}
