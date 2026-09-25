package com.bithead.shelter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bithead.shelter.R

// Stitch dark operations palette.
val ShelterInk = Color(0xFF121214)
val ShelterSurface = Color(0xFF1B1B1F)
val ShelterSurfaceRaised = Color(0xFF28282D)
val ShelterOutline = Color(0xFF74747B)

val ShelterBlue = Color(0xFF385E4E)
val ShelterBlueSoft = Color(0xFFA6D0BC)
val ShelterSafe = Color(0xFF204637)
val ShelterSafeSoft = Color(0xFFC2ECD7)
val ShelterDanger = Color(0xFFBA1A1A)
val ShelterDangerSoft = Color(0xFFFFDAD6)
val ShelterAmber = Color(0xFF8D5200)
val ShelterTextDim = Color(0xFFB9B7C0)

private val ShelterColorScheme = darkColorScheme(
    primary = ShelterSafe,
    onPrimary = Color.White,
    primaryContainer = ShelterBlue,
    onPrimaryContainer = ShelterBlueSoft,
    secondary = ShelterTextDim,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E2ED),
    onSecondaryContainer = Color(0xFF3B4952),
    tertiary = Color(0xFF344333),
    onTertiary = Color.White,
    error = ShelterDanger,
    onError = Color.White,
    errorContainer = ShelterDangerSoft,
    onErrorContainer = Color(0xFF93000A),
    background = ShelterInk,
    onBackground = Color(0xFFF1EFF4),
    surface = ShelterSurface,
    onSurface = Color(0xFFF1EFF4),
    surfaceVariant = Color(0xFF303036),
    onSurfaceVariant = ShelterTextDim,
    outline = ShelterOutline,
    outlineVariant = Color(0xFFC1C8C3),
)

private val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
)
private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)
private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold),
)

private val ShelterTypography = Typography(
    headlineLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.15.sp),
    labelSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
)

@Composable
fun ShelterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShelterColorScheme,
        typography = ShelterTypography,
        content = content,
    )
}
