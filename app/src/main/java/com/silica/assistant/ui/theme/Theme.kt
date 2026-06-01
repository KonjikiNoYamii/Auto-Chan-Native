package com.silica.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Cream = Color(0xFFF2EAE1)
val Taupe = Color(0xFFD7C4B1)
val DeepRose = Color(0xFFA64B54)
val DeepRoseLight = Color(0xFFC96A74)
val Espresso = Color(0xFF2C221E)
val MutedBrown = Color(0xFF8C7A70)
val WarmGray = Color(0xFFA69E98)
val DarkBg = Color(0xFF1A1512)
val DarkSurface = Color(0xFF2A221E)
val DarkCard = Color(0xFF3A302A)
val GlassWhite = Color(0x33FFFFFF)
val GlassRose = Color(0x33A64B54)

private val LightColorScheme = lightColorScheme(
    primary = DeepRose,
    onPrimary = Color.White,
    primaryContainer = Taupe,
    onPrimaryContainer = Espresso,
    secondary = Taupe,
    onSecondary = Espresso,
    secondaryContainer = Cream,
    onSecondaryContainer = Espresso,
    tertiary = DeepRose,
    background = Cream,
    onBackground = Espresso,
    surface = Cream,
    onSurface = Espresso,
    surfaceVariant = Taupe,
    onSurfaceVariant = MutedBrown,
    outline = Espresso,
    outlineVariant = Taupe,
)

private val DarkColorScheme = darkColorScheme(
    primary = DeepRoseLight,
    onPrimary = Color.White,
    primaryContainer = DarkCard,
    onPrimaryContainer = Taupe,
    secondary = Taupe,
    onSecondary = Espresso,
    secondaryContainer = DarkCard,
    onSecondaryContainer = Taupe,
    tertiary = DeepRoseLight,
    background = DarkBg,
    onBackground = Taupe,
    surface = DarkSurface,
    onSurface = Taupe,
    surfaceVariant = DarkCard,
    onSurfaceVariant = MutedBrown,
    outline = MutedBrown,
    outlineVariant = DarkCard,
)

val SilicaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

val SilicaTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.5.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun SilicaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = SilicaShapes,
        typography = SilicaTypography,
        content = content,
    )
}
