package com.screenusdeletus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.screenusdeletus.R

// A fixed brand palette (no dynamic colour): deep "specimen" green, paper neutrals, an amber
// accent for warnings and a clear red for blocks.
private val Light = lightColorScheme(
    primary = Color(0xFF1F5C4D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBDF),
    onPrimaryContainer = Color(0xFF052019),
    secondary = Color(0xFF52635B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE8E1),
    onSecondaryContainer = Color(0xFF101F19),
    tertiary = Color(0xFF9A5700),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2E1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F7F2),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF6F7F2),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3EE),
    surfaceContainer = Color(0xFFEAEEE8),
    surfaceContainerHigh = Color(0xFFE4E9E3),
    surfaceContainerHighest = Color(0xFFDEE3DD),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD5BE),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF0F4A3D),
    onPrimaryContainer = Color(0xFFABF2DA),
    secondary = Color(0xFFB6CCC1),
    onSecondary = Color(0xFF21352D),
    secondaryContainer = Color(0xFF384B43),
    onSecondaryContainer = Color(0xFFD2E8DD),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6B3B00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1412),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0E1412),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF3F4944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF3F4944),
    surfaceContainerLowest = Color(0xFF090F0D),
    surfaceContainerLow = Color(0xFF161D1A),
    surfaceContainer = Color(0xFF1A211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF2F3633),
)

@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
val Grotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

private val base = Typography()

private fun TextStyle.display(weight: FontWeight, letterSpacing: Float = -0.5f) =
    copy(fontFamily = Grotesk, fontWeight = weight, letterSpacing = letterSpacing.sp)

val AppTypography = Typography(
    displayLarge = base.displayLarge.display(FontWeight.Bold, -1.5f),
    displayMedium = base.displayMedium.display(FontWeight.Bold, -1f),
    displaySmall = base.displaySmall.display(FontWeight.Bold, -0.75f),
    headlineLarge = base.headlineLarge.display(FontWeight.Bold),
    headlineMedium = base.headlineMedium.display(FontWeight.Bold),
    headlineSmall = base.headlineSmall.display(FontWeight.SemiBold, -0.25f),
    titleLarge = base.titleLarge.display(FontWeight.SemiBold, -0.25f),
    titleMedium = base.titleMedium.display(FontWeight.SemiBold, 0f),
    titleSmall = base.titleSmall.display(FontWeight.SemiBold, 0f),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

/** Extra semantic colours that Material's scheme doesn't have. */
@Immutable
data class ExtraColors(
    val success: Color,
    val warning: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val block: Color,
    val onBlock: Color,
)

private val LightExtra = ExtraColors(
    success = Color(0xFF1F7A56),
    warning = Color(0xFF9A5700),
    heroStart = Color(0xFF1F5C4D),
    heroEnd = Color(0xFF0D3A30),
    onHero = Color(0xFFF2FBF6),
    block = Color(0xFF0B0F0E),
    onBlock = Color(0xFFEDF3EF),
)

private val DarkExtra = ExtraColors(
    success = Color(0xFF7DDBAE),
    warning = Color(0xFFFFB86B),
    heroStart = Color(0xFF194C40),
    heroEnd = Color(0xFF0B2620),
    onHero = Color(0xFFE6F6EE),
    block = Color(0xFF050807),
    onBlock = Color(0xFFEDF3EF),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object Brand {
    val extra: ExtraColors
        @Composable get() = LocalExtraColors.current
}

/** Chart palette that reads in both themes. */
val ChartColors = listOf(
    Color(0xFF2F8F7A), Color(0xFFE0A13B), Color(0xFF5B7FD6), Color(0xFFC0617E),
    Color(0xFF8C9A3B), Color(0xFF4FA3C7), Color(0xFFB07AD1), Color(0xFF8A7360),
)

@Composable
fun ScreenusDeletusTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) Dark else Light,
            typography = AppTypography,
            content = content,
        )
    }
}
