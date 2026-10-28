package dev.ftassi.cockpit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.ftassi.cockpit.R

val Accent = Color(0xFF7BE495)
val RadioColor = Color(0xFFFF7A59)
val SpotifyColor = Color(0xFF1ED760)
val Outline = Color(0xFF2A2F36)
val Muted = Color(0xFF8A939D)
val Placeholder = Color(0xFF1A1D21)

private val Barlow = FontFamily(
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
)

// Always dark, on pure black: the app is used in a car, often at night.
@Composable
fun CockpitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Color.Black,
            surface = Color.Black,
            onSurface = Color.White,
        ),
        typography = Typography().withFamily(Barlow),
        content = content,
    )
}

private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    headlineMedium = headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    headlineSmall = headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    titleLarge = titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    titleMedium = titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    titleSmall = titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    labelMedium = labelMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    labelSmall = labelSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
)
