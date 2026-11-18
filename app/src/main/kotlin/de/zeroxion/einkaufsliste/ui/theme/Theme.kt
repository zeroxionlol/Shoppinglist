package de.zeroxion.einkaufsliste.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Fresh "market" palette: mint green to deep teal.
val Mint = Color(0xFF0E9F6E)
val Teal = Color(0xFF0B6E8C)
val Lime = Color(0xFF8BD17C)

val BrandGradient = Brush.linearGradient(listOf(Mint, Teal))

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B8A5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F2DC),
    onPrimaryContainer = Color(0xFF00391F),
    secondary = Color(0xFF0B6E8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9EAF5),
    onSecondaryContainer = Color(0xFF00313F),
    tertiary = Color(0xFFB3478F),
    background = Color(0xFFF5FBF6),
    onBackground = Color(0xFF161D19),
    surface = Color(0xFFF5FBF6),
    onSurface = Color(0xFF161D19),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF414943),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF6F0),
    surfaceContainer = Color(0xFFE9F0EA),
    surfaceContainerHigh = Color(0xFFE3EAE4),
    surfaceContainerHighest = Color(0xFFDEE5DF),
    outline = Color(0xFF717973),
    outlineVariant = Color(0xFFC0C9C1),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EDBA6),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF005233),
    onPrimaryContainer = Color(0xFFC7F2DC),
    secondary = Color(0xFF86D1EC),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004D62),
    onSecondaryContainer = Color(0xFFC9EAF5),
    tertiary = Color(0xFFF2A8D8),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE5DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE5DF),
    surfaceVariant = Color(0xFF414943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF414943),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun EinkaufslisteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
