package br.com.managerfoot.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TacticalDarkColorScheme = darkColorScheme(
    primary                = GreenElectric,
    onPrimary              = Color(0xFF003322),
    primaryContainer       = GreenDim,
    onPrimaryContainer     = GreenElectric,
    secondary              = AmberAccent,
    onSecondary            = Color(0xFF1A1200),
    secondaryContainer     = Color(0xFF3A2C00),
    onSecondaryContainer   = AmberAccent,
    tertiary               = Color(0xFF80CBC4),
    onTertiary             = Color(0xFF002020),
    tertiaryContainer      = Color(0xFF003533),
    onTertiaryContainer    = Color(0xFF9EF2EA),
    error                  = RedAccent,
    onError                = Color(0xFF1A0000),
    errorContainer         = Color(0xFF4D0000),
    onErrorContainer       = RedAccent,
    background             = BgPrimary,
    onBackground           = TextPrimary,
    surface                = SurfaceCard,
    onSurface              = TextPrimary,
    surfaceVariant         = SurfaceVariant,
    onSurfaceVariant       = TextMuted,
    outline                = OutlineSubtle,
    outlineVariant         = OutlineSubtle,
    inverseSurface         = TextPrimary,
    inverseOnSurface       = BgPrimary,
    inversePrimary         = Color(0xFF006633),
)

@Composable
fun ManagerFootTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TacticalDarkColorScheme,
        typography  = ManagerFootTypography,
        content     = content
    )
}