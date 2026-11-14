package com.yoligym.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
)

@Composable
fun YoliGymTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) YoliDarkColorScheme else YoliLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GymTypography,
        content = content
    )
}
