package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = WhitePure,
    primaryContainer = PurpleLight,
    onPrimaryContainer = PurpleDark,
    secondary = PurpleVibrant,
    onSecondary = WhitePure,
    secondaryContainer = PurpleTint,
    onSecondaryContainer = PurplePrimary,
    tertiary = StatusConnected,
    onTertiary = WhitePure,
    tertiaryContainer = StatusConnectedContainer,
    onTertiaryContainer = TextPrimary,
    background = SurfaceOffWhite,
    onBackground = TextPrimary,
    surface = WhitePure,
    onSurface = TextPrimary,
    surfaceVariant = PurpleTint,
    onSurfaceVariant = TextSecondary,
    outline = BorderLavender,
    error = StatusError,
    onError = WhitePure,
    errorContainer = StatusErrorContainer,
    onErrorContainer = StatusError
)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleVibrant,
    onPrimary = WhitePure,
    primaryContainer = PurplePrimary,
    onPrimaryContainer = PurpleLight,
    secondary = PurpleLight,
    onSecondary = PurpleDark,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = PurpleLight,
    tertiary = StatusConnected,
    onTertiary = WhitePure,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = PurpleLight,
    outline = PurplePrimary,
    error = StatusError,
    onError = WhitePure
)

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FastXVpnTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
