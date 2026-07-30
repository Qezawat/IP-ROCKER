package com.qezawat.iprocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A single dark palette. The app is a night-time diagnostic tool, so a light
// theme would only hurt legibility of the status colours.
val RockerBackground = Color(0xFF0A0E14)
val RockerSurface = Color(0xFF121821)
val RockerSurfaceHigh = Color(0xFF1A2230)
val RockerOutline = Color(0xFF263140)

val RockerAccent = Color(0xFF00E5A0)
val RockerAccentDim = Color(0xFF0C8C64)

// Verdict colours are deliberately far apart in hue so the traffic light is
// readable at a glance and distinguishable for the most common colour vision
// deficiencies when paired with the icon and label that always accompany it.
val VerdictClean = Color(0xFF29D97C)
val VerdictCaution = Color(0xFFFFC53D)
val VerdictDirty = Color(0xFFFF5C5C)
val VerdictUnknown = Color(0xFF7C8A9C)

val TextPrimary = Color(0xFFE8EEF6)
val TextSecondary = Color(0xFF9AA7B8)

private val RockerColors = darkColorScheme(
    primary = RockerAccent,
    onPrimary = Color(0xFF00281B),
    primaryContainer = RockerAccentDim,
    onPrimaryContainer = Color(0xFFB8FFE2),
    secondary = Color(0xFF6FA8FF),
    background = RockerBackground,
    onBackground = TextPrimary,
    surface = RockerSurface,
    onSurface = TextPrimary,
    surfaceVariant = RockerSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = RockerOutline,
    error = VerdictDirty,
    onError = Color(0xFF3A0000),
)

private val RockerTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 1.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
    ),
    // Addresses and measurements use a monospace face so columns line up and
    // digits cannot be misread.
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun IPRockerTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme is read so the composable participates in
    // configuration changes, but the palette stays dark either way.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = RockerColors,
        typography = RockerTypography,
        content = content,
    )
}
