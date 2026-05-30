package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = ElegantPrimary,
    onPrimary = ElegantOnPrimary,
    primaryContainer = ElegantContainer,
    onPrimaryContainer = ElegantPrimary,
    background = ElegantBg,
    onBackground = ElegantOnBackground,
    surface = ElegantCard,
    onSurface = ElegantOnBackground,
    secondary = ElegantSecondary,
    outline = ElegantOutline
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ElegantContainer,
    onPrimary = ElegantPrimary,
    primaryContainer = ElegantPrimary,
    onPrimaryContainer = ElegantContainer,
    background = ElegantBg,
    onBackground = ElegantOnBackground,
    surface = ElegantCard,
    onSurface = ElegantOnBackground,
    secondary = ElegantSecondary,
    outline = ElegantOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color so the custom Elegant Dark colors are always visible
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> DarkColorScheme // Stay in elegant dark theme for high impact
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
