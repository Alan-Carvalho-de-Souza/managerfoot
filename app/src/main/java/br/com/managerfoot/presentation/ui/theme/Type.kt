package br.com.managerfoot.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Tipografia — Tactical Dark ───────────────────────────────
// Fontes: FontFamily.SansSerif (fallback seguro sem arquivos de fonte).
// Para ativar Barlow: adicione os arquivos TTF em res/font/ e descomente
// as linhas BarlowFamily / BarlowCondensedFamily abaixo.
//
// Arquivos necessários em res/font/:
//   barlow_regular.ttf, barlow_medium.ttf, barlow_semibold.ttf
//   barlow_condensed_semibold.ttf, barlow_condensed_bold.ttf, barlow_condensed_black.ttf
//
// Descomente e substitua FontFamily.SansSerif por BarlowFamily /
// BarlowCondensedFamily após adicionar os arquivos.

// private val BarlowFamily = FontFamily(
//     Font(R.font.barlow_regular,   FontWeight.Normal),
//     Font(R.font.barlow_medium,    FontWeight.Medium),
//     Font(R.font.barlow_semibold,  FontWeight.SemiBold),
// )
// private val BarlowCondensedFamily = FontFamily(
//     Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
//     Font(R.font.barlow_condensed_bold,     FontWeight.Bold),
//     Font(R.font.barlow_condensed_black,    FontWeight.Black),
// )

private val BodyFamily      = FontFamily.SansSerif   // → BarlowFamily
private val DisplayFamily   = FontFamily.SansSerif   // → BarlowCondensedFamily

val ManagerFootTypography = Typography(
    displayLarge = TextStyle(
        fontFamily   = DisplayFamily,
        fontWeight   = FontWeight.Black,
        fontSize     = 57.sp,
        lineHeight   = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily   = DisplayFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 45.sp,
        lineHeight   = 52.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily   = DisplayFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 32.sp,
        lineHeight   = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily   = DisplayFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 28.sp,
        lineHeight   = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily   = DisplayFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 24.sp,
        lineHeight   = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily   = DisplayFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 22.sp,
        lineHeight   = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily   = BodyFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily   = BodyFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily   = BodyFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily   = BodyFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily   = BodyFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily   = BodyFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily   = BodyFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily   = BodyFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 11.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)