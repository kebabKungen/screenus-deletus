package com.screenusdeletus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF2F6B5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F0DF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635C),
    tertiary = Color(0xFF8A5100),
    error = Color(0xFFB3261E),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF98D4C3),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF155145),
    onPrimaryContainer = Color(0xFFB4F0DF),
    secondary = Color(0xFFB2CCC3),
    tertiary = Color(0xFFFFB86E),
    error = Color(0xFFF2B8B5),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
)

/** Chart palette that reads in both themes. */
val ChartColors = listOf(
    Color(0xFF2F8F7A), Color(0xFF5B7FD6), Color(0xFFD9822B), Color(0xFFB45BA8),
    Color(0xFF8C9A3B), Color(0xFFD45A5A), Color(0xFF4FA3C7), Color(0xFF8A7360),
)

@Composable
fun ScreenusDeletusTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
