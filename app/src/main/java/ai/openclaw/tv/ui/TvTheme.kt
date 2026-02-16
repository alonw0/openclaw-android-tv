package ai.openclaw.tv.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFFFF6B4A),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFFF4F40),
  onPrimaryContainer = Color(0xFFFFFFFF),
  secondary = Color(0xFF4F7A9A),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFF3A5A75),
  onSecondaryContainer = Color(0xFFFFFFFF),
  tertiary = Color(0xFF4F7A9A),
  background = Color(0xFF121212),
  onBackground = Color(0xFFFFFFFF),
  surface = Color(0xFF1E1E1E),
  onSurface = Color(0xFFFFFFFF),
  surfaceVariant = Color(0xFF2D2D2D),
  onSurfaceVariant = Color(0xFFB3FFFFFF),
  error = Color(0xFFFF4444),
  onError = Color(0xFFFFFFFF),
)

@Composable
fun TvTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = DarkColorScheme // TV is always dark theme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = TvTypography,
    content = content
  )
}
