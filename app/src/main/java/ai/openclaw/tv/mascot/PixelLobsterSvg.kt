package ai.openclaw.tv.mascot

import ai.openclaw.tv.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp

/**
 * Renders the pixel lobster SVG scaled up for TV display.
 * Uses the drawable resource created from docs/assets/pixel-lobster.svg
 */
@Composable
fun PixelLobsterSvg(
  size: Int,
  bodyColor: Color,
  clawColor: Color,
  eyeState: EyeState = EyeState.OPEN,
  tint: Color? = null
) {
  // For now, use a placeholder that will be replaced with the actual SVG
  // The actual SVG drawable will be created in resources
  Image(
    imageVector = ImageVector.vectorResource(id = R.drawable.ic_crab_lobster),
    contentDescription = "Clawd the Crab",
    modifier = Modifier.size(size.dp),
    colorFilter = tint?.let { ColorFilter.tint(it) }
  )
}
