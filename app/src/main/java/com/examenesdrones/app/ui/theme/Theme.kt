package com.examenesdrones.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examenesdrones.app.data.ThemeMode

// Forest green + warm ochre: a calm, professional study palette.
private val Light = lightColorScheme(
    primary = Color(0xFF2E5E3E), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6CF), onPrimaryContainer = Color(0xFF0B2914),
    secondary = Color(0xFF7A5A1E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E2B8), onSecondaryContainer = Color(0xFF2A1A00),
    tertiary = Color(0xFF3F5F75), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6E3F8), onTertiaryContainer = Color(0xFF001E2E),
    background = Color(0xFFF8F7F2), onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFF8F7F2), onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFDFE4DB), onSurfaceVariant = Color(0xFF424940),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F2EC),
    surfaceContainer = Color(0xFFECEDE6), surfaceContainerHigh = Color(0xFFE6E7E0),
    surfaceContainerHighest = Color(0xFFE0E2DA),
    outline = Color(0xFF727970), outlineVariant = Color(0xFFC2C8BE),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DD3AC), onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF15462A), onPrimaryContainer = Color(0xFFC8E6CF),
    secondary = Color(0xFFE6C48A), onSecondary = Color(0xFF412D00),
    secondaryContainer = Color(0xFF5D4206), onSecondaryContainer = Color(0xFFF6E2B8),
    tertiary = Color(0xFFA8CBE2), onTertiary = Color(0xFF0D3446),
    tertiaryContainer = Color(0xFF274B5E), onTertiaryContainer = Color(0xFFC6E3F8),
    background = Color(0xFF121411), onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF121411), onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF424940), onSurfaceVariant = Color(0xFFC2C8BE),
    surfaceContainerLowest = Color(0xFF0D0F0C), surfaceContainerLow = Color(0xFF1A1C19),
    surfaceContainer = Color(0xFF1E201D), surfaceContainerHigh = Color(0xFF282B27),
    surfaceContainerHighest = Color(0xFF333531),
    outline = Color(0xFF8C9389), outlineVariant = Color(0xFF424940),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

/** Semantic colors for answer feedback. Always paired with an icon and text, never color alone. */
@Immutable
data class FeedbackColors(
    val correct: Color, val onCorrect: Color, val correctContainer: Color, val onCorrectContainer: Color,
    val wrong: Color, val wrongContainer: Color, val onWrongContainer: Color,
    val neutralContainer: Color,
)

private val LightFeedback = FeedbackColors(
    correct = Color(0xFF1E7B3A), onCorrect = Color.White, correctContainer = Color(0xFFD3F0DA), onCorrectContainer = Color(0xFF07361A),
    wrong = Color(0xFFB3261E), wrongContainer = Color(0xFFFADBD8), onWrongContainer = Color(0xFF4A0B07),
    neutralContainer = Color(0xFFE9EAE3),
)
private val DarkFeedback = FeedbackColors(
    correct = Color(0xFF7FD69A), onCorrect = Color(0xFF00391B), correctContainer = Color(0xFF184D2A), onCorrectContainer = Color(0xFFD3F0DA),
    wrong = Color(0xFFFFB4AB), wrongContainer = Color(0xFF6B1A14), onWrongContainer = Color(0xFFFADBD8),
    neutralContainer = Color(0xFF2A2D29),
)

val LocalFeedbackColors = staticCompositionLocalOf { LightFeedback }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Comfortable long-form reading style used by the theory reader. */
fun readingStyle(scale: Float) = TextStyle(
    fontFamily = FontFamily.Serif, fontSize = (17 * scale).sp, lineHeight = (27 * scale).sp,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
)

@Composable
fun ExamTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme: ColorScheme = if (dark) Dark else Light
    CompositionLocalProvider(LocalFeedbackColors provides if (dark) DarkFeedback else LightFeedback) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
