package br.com.managerfoot.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TacticalDarkColorScheme = darkColorScheme(
    primary                = AccentBlue,
    onPrimary              = Color(0xFFF2F7FF),
    primaryContainer       = Color(0xFF17315C),
    onPrimaryContainer     = AccentBlueBright,
    secondary              = AmberAccent,
    onSecondary            = Color(0xFF241600),
    secondaryContainer     = Color(0xFF3A2C00),
    onSecondaryContainer   = AmberAccent,
    tertiary               = Color(0xFF38BDF8),
    onTertiary             = Color(0xFF00212E),
    tertiaryContainer      = Color(0xFF0A3345),
    onTertiaryContainer    = Color(0xFF9CDDF7),
    error                  = RedAccent,
    onError                = Color(0xFF2A0004),
    errorContainer         = Color(0xFF53000A),
    onErrorContainer       = Color(0xFFFFB3B8),
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
    inversePrimary         = Color(0xFF1E40AF),
)

@Composable
fun ManagerFootTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TacticalDarkColorScheme,
        typography  = ManagerFootTypography,
        content     = content
    )
}