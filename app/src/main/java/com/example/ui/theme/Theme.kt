package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldVibrant,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldLightContainer,
    secondary = IndigoSoft,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = IndigoDarkContainer,
    onSecondaryContainer = IndigoLightContainer,
    tertiary = WhatsAppBright,
    onTertiary = Color(0xFF052E16),
    background = SlateBgDark,
    onBackground = SlateTextPrimaryDark,
    surface = SlateSurfaceDark,
    onSurface = SlateTextPrimaryDark,
    surfaceVariant = SlateSurfaceVariantDark,
    onSurfaceVariant = SlateTextSecondaryDark,
    outline = SlateOutlineDark,
    error = RoseError,
    errorContainer = RoseContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = EmeraldDarkContainer,
    secondary = IndigoAccent,
    onSecondary = Color.White,
    secondaryContainer = IndigoLightContainer,
    onSecondaryContainer = IndigoDarkContainer,
    tertiary = WhatsAppAccent,
    onTertiary = Color.White,
    background = SlateBgLight,
    onBackground = SlateTextPrimaryLight,
    surface = SlateSurfaceLight,
    onSurface = SlateTextPrimaryLight,
    surfaceVariant = SlateSurfaceVariantLight,
    onSurfaceVariant = SlateTextSecondaryLight,
    outline = SlateOutlineLight,
    error = RoseError,
    errorContainer = RoseContainerLight
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LeadFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
