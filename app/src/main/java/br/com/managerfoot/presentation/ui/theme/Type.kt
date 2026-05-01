package br.com.managerfoot.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import br.com.managerfoot.R

// ── Tipografia — Tactical Dark ──────────────────────────────
//
// Fontes via Google Fonts (downloadable):
//   - Barlow: corpo (regular, medium, semibold)
//   - Barlow Condensed: títulos, placares e KPIs (semibold, bold, black)
//
// Caso o device não tenha Google Play Services, o Compose cai para
// o fallback (FontFamily.SansSerif = Roboto).

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

private val BarlowGF          = GoogleFont("Barlow")
private val BarlowCondensedGF = GoogleFont("Barlow Condensed")

private val BodyFamily = FontFamily(
    Font(googleFont = BarlowGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = BarlowGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = BarlowGF, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = BarlowGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold)
)

private val DisplayFamily = FontFamily(
    Font(googleFont = BarlowCondensedGF, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = BarlowCondensedGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
    Font(googleFont = BarlowCondensedGF, fontProvider = GoogleFontsProvider, weight = FontWeight.Black)
)

val ManagerFootTypography = Typography(
    // Display + Headline + TitleLarge usam Barlow Condensed
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
    // Title médio/pequeno + body + label usam Barlow regular
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
