package com.yoligym.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

val GymBackground = Color(0xFF0D0F14)
val GymSurface = Color(0xFF161A23)
val GymSurfaceVariant = Color(0xFF202634)
val GymCardElevated = Color(0xFF1C2230)
val GymPrimaryGreen = Color(0xFF00E676)
val GymPrimaryContainer = Color(0xFF003919)
val GymSecondaryCyan = Color(0xFF00B0FF)
val GymTertiaryAmber = Color(0xFFFFB300)
val GymErrorRed = Color(0xFFFF5252)
val GymTextPrimary = Color(0xFFF3F6FC)
val GymTextSecondary = Color(0xFF9BA5B7)

private val YoliDarkColorScheme = darkColorScheme(
    primary = GymPrimaryGreen,
    onPrimary = Color(0xFF00210B),
    primaryContainer = GymPrimaryContainer,
    onPrimaryContainer = Color(0xFF75FFA6),
    secondary = GymSecondaryCyan,
    onSecondary = Color(0xFF001E2F),
    secondaryContainer = Color(0xFF00354D),
    onSecondaryContainer = Color(0xFFB5E6FF),
    tertiary = GymTertiaryAmber,
    onTertiary = Color(0xFF261900),
    background = GymBackground,
    onBackground = GymTextPrimary,
    surface = GymSurface,
    onSurface = GymTextPrimary,
    surfaceVariant = GymSurfaceVariant,
    onSurfaceVariant = GymTextSecondary,
    error = GymErrorRed,
    onError = Color.White
)

private val YoliLightColorScheme = lightColorScheme(
    primary = Color(0xFF008A42),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F5C1),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF006591),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4EEF9),
    onSecondaryContainer = Color(0xFF00354D),
    tertiary = Color(0xFF8B5F00),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF12161F),
    surface = Color.White,
    onSurface = Color(0xFF12161F),
    surfaceVariant = Color(0xFFE6EAF2),
    onSurfaceVariant = Color(0xFF4A5363)
)

private val GymTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun YoliGymTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) YoliDarkColorScheme else YoliLightColorScheme
    val currentDensity = LocalDensity.current
    val clampedDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.15f)
    )
    CompositionLocalProvider(LocalDensity provides clampedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GymTypography,
            content = content
        )
    }
}
