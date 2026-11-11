package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
  primary = VoxPrimaryVariant,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF312E81),
  onPrimaryContainer = Color(0xFFE0E7FF),
  secondary = VoxSecondary,
  onSecondary = Color.Black,
  secondaryContainer = Color(0xFF164E63),
  onSecondaryContainer = Color(0xFFCFFAFE),
  tertiary = VoxTertiary,
  onTertiary = Color.White,
  background = DarkBackground,
  onBackground = DarkOnBackground,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = Color(0xFF94A3B8),
  outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
  primary = VoxPrimary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFEEF2FF),
  onPrimaryContainer = Color(0xFF312E81),
  secondary = Color(0xFF0891B2),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFECFEFF),
  onSecondaryContainer = Color(0xFF164E63),
  tertiary = VoxTertiary,
  onTertiary = Color.White,
  background = LightBackground,
  onBackground = LightOnBackground,
  surface = LightSurface,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = Color(0xFF64748B),
  outline = LightOutline
)

@Composable
fun VoxTaskTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Use intentional VoxTask palette for brand consistency
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = VoxTypography,
    content = content
  )
}
