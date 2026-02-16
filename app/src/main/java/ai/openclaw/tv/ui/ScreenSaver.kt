package ai.openclaw.tv.ui

import ai.openclaw.tv.CrabEmotion
import ai.openclaw.tv.mascot.CrabMascot
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * Screensaver with bouncing crab mascot and ambient particles.
 * Shows when canvas is empty/default.
 * Supports DVD mode easter egg.
 */
@Composable
fun ScreenSaver(
  onCrabClick: () -> Unit = {},
  dvdMode: Boolean = false,
  modifier: Modifier = Modifier
) {
  if (dvdMode) {
    DvdScreenSaver(onClick = onCrabClick, modifier = modifier)
  } else {
    DefaultScreenSaver(onCrabClick = onCrabClick, modifier = modifier)
  }
}

/**
 * Classic DVD-style bouncing logo screensaver.
 * Changes color when hitting edges, special effect on corner hits.
 */
@Composable
private fun DvdScreenSaver(
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var containerSize by remember { mutableStateOf(IntSize.Zero) }
  val density = LocalDensity.current

  // Logo position and velocity
  var logoX by remember { mutableFloatStateOf(100f) }
  var logoY by remember { mutableFloatStateOf(100f) }
  var velocityX by remember { mutableFloatStateOf(3f) }
  var velocityY by remember { mutableFloatStateOf(2f) }

  // Current color
  var colorIndex by remember { mutableIntStateOf(0) }
  val colors = remember {
    listOf(
      Color(0xFFFF0000), // Red
      Color(0xFF00FF00), // Green
      Color(0xFF0000FF), // Blue
      Color(0xFFFFFF00), // Yellow
      Color(0xFFFF00FF), // Magenta
      Color(0xFF00FFFF), // Cyan
      Color(0xFFFF6600), // Orange
      Color(0xFF9933FF), // Purple
    )
  }

  // Corner hit counter for bragging rights
  var cornerHits by remember { mutableIntStateOf(0) }
  var showCornerCelebration by remember { mutableStateOf(false) }

  val logoWidthDp = 200
  val logoHeightDp = 100
  val logoWidthPx = with(density) { logoWidthDp.dp.toPx() }
  val logoHeightPx = with(density) { logoHeightDp.dp.toPx() }

  // Animation loop
  LaunchedEffect(containerSize) {
    if (containerSize.width == 0 || containerSize.height == 0) return@LaunchedEffect

    // Initialize position randomly
    if (logoX == 100f && logoY == 100f) {
      logoX = Random.nextFloat() * (containerSize.width - logoWidthPx)
      logoY = Random.nextFloat() * (containerSize.height - logoHeightPx)
    }

    while (true) {
      // Update position
      logoX += velocityX
      logoY += velocityY

      // Check for edge collisions
      val maxX = containerSize.width - logoWidthPx
      val maxY = containerSize.height - logoHeightPx

      var hitX = false
      var hitY = false

      if (logoX <= 0 || logoX >= maxX) {
        velocityX = -velocityX
        logoX = logoX.coerceIn(0f, maxX)
        hitX = true
      }
      if (logoY <= 0 || logoY >= maxY) {
        velocityY = -velocityY
        logoY = logoY.coerceIn(0f, maxY)
        hitY = true
      }

      // Change color on any edge hit
      if (hitX || hitY) {
        colorIndex = (colorIndex + 1) % colors.size
      }

      // Corner hit - the legendary moment!
      if (hitX && hitY) {
        cornerHits++
        showCornerCelebration = true
        delay(100)
        showCornerCelebration = false
      }

      delay(16) // ~60fps
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .onSizeChanged { containerSize = it }
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
      )
  ) {
    // Bouncing DVD logo - using graphicsLayer for GPU-accelerated animation (no recomposition)
    if (containerSize.width > 0 && containerSize.height > 0) {
      Box(
        modifier = Modifier
          .width(logoWidthDp.dp)
          .height(logoHeightDp.dp)
          .graphicsLayer {
            translationX = logoX
            translationY = logoY
          },
        contentAlignment = Alignment.Center
      ) {
        // The "DVD" text, but OpenClaw themed
        Column(
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "OPEN",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = colors[colorIndex],
            letterSpacing = 8.sp
          )
          Text(
            text = "CLAW",
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = colors[colorIndex],
            letterSpacing = 12.sp
          )
        }
      }
    }

    // Corner hit celebration
    if (showCornerCelebration) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "CORNER HIT!",
          fontSize = 64.sp,
          fontWeight = FontWeight.Black,
          color = Color.White
        )
      }
    }

    // Corner hit counter (subtle)
    if (cornerHits > 0) {
      Text(
        text = "Corner hits: $cornerHits",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.3f),
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
      )
    }
  }
}

/**
 * Default OpenClaw screensaver with bouncing crab and particles.
 */
@Composable
private fun DefaultScreenSaver(
  onCrabClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var containerSize by remember { mutableStateOf(IntSize.Zero) }
  val density = LocalDensity.current

  // Crab position and velocity
  var crabX by remember { mutableFloatStateOf(100f) }
  var crabY by remember { mutableFloatStateOf(100f) }
  var velocityX by remember { mutableFloatStateOf(2f) }
  var velocityY by remember { mutableFloatStateOf(1.5f) }

  // Crab emotion cycles through states
  var emotionIndex by remember { mutableIntStateOf(0) }
  val emotions = remember {
    listOf(
      CrabEmotion.SLEEPING,
      CrabEmotion.THINKING,
      CrabEmotion.EXCITED,
      CrabEmotion.CELEBRATING,
      CrabEmotion.SUCCESS
    )
  }

  // Current time for clock display
  var currentTime by remember { mutableStateOf("") }
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

  // Particles for ambient effect
  val particles = remember { mutableStateListOf<ScreenSaverParticle>() }

  val crabSizeDp = 120
  val crabSizePx = with(density) { crabSizeDp.dp.toPx() }

  // Animation loop
  LaunchedEffect(containerSize) {
    if (containerSize.width == 0 || containerSize.height == 0) return@LaunchedEffect

    // Initialize position if needed
    if (crabX == 100f && crabY == 100f) {
      crabX = Random.nextFloat() * (containerSize.width - crabSizePx)
      crabY = Random.nextFloat() * (containerSize.height - crabSizePx)
    }

    var frameCount = 0
    while (true) {
      frameCount++

      // Update crab position
      crabX += velocityX
      crabY += velocityY

      // Bounce off edges
      val maxX = containerSize.width - crabSizePx
      val maxY = containerSize.height - crabSizePx

      if (crabX <= 0 || crabX >= maxX) {
        velocityX = -velocityX
        crabX = crabX.coerceIn(0f, maxX)
        // Change emotion on bounce
        emotionIndex = (emotionIndex + 1) % emotions.size
      }
      if (crabY <= 0 || crabY >= maxY) {
        velocityY = -velocityY
        crabY = crabY.coerceIn(0f, maxY)
      }

      // Update time every second
      if (frameCount % 60 == 0) {
        currentTime = timeFormat.format(Date())
      }

      // Spawn particles occasionally (limited to 25 for performance)
      if (frameCount % 30 == 0 && particles.size < 25) {
        particles.add(
          ScreenSaverParticle(
            x = Random.nextFloat() * containerSize.width,
            y = containerSize.height + 20f,
            size = Random.nextFloat() * 8f + 4f,
            speed = Random.nextFloat() * 1.5f + 0.5f,
            alpha = Random.nextFloat() * 0.5f + 0.2f,
            color = listOf(
              Color(0xFF00E5CC),
              Color(0xFF4FC3F7),
              Color(0xFFBA68C8),
              Color(0xFFFFD54F)
            ).random()
          )
        )
      }

      // Update particles
      val iterator = particles.iterator()
      while (iterator.hasNext()) {
        val particle = iterator.next()
        particle.y -= particle.speed
        particle.alpha -= 0.002f
        if (particle.y < -20f || particle.alpha <= 0f) {
          iterator.remove()
        }
      }

      delay(16) // ~60fps
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.radialGradient(
          colors = listOf(
            Color(0xFF1a1a2e),
            Color(0xFF0f0f1a)
          ),
          radius = 1500f
        )
      )
      .onSizeChanged { containerSize = it }
  ) {
    // Ambient particles
    Canvas(modifier = Modifier.fillMaxSize()) {
      particles.forEach { particle ->
        drawCircle(
          color = particle.color.copy(alpha = particle.alpha),
          radius = particle.size,
          center = Offset(particle.x, particle.y)
        )
      }
    }

    // Subtle grid overlay
    Canvas(modifier = Modifier.fillMaxSize()) {
      val gridSpacing = 80f
      val gridColor = Color.White.copy(alpha = 0.03f)

      // Vertical lines
      var x = 0f
      while (x < size.width) {
        drawLine(
          color = gridColor,
          start = Offset(x, 0f),
          end = Offset(x, size.height),
          strokeWidth = 1f
        )
        x += gridSpacing
      }

      // Horizontal lines
      var y = 0f
      while (y < size.height) {
        drawLine(
          color = gridColor,
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = 1f
        )
        y += gridSpacing
      }
    }

    // Clock in top-right corner (below status bar)
    if (currentTime.isNotEmpty()) {
      Text(
        text = currentTime,
        style = MaterialTheme.typography.displayLarge,
        color = Color.White.copy(alpha = 0.3f),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 100.dp, end = 48.dp)
      )
    }

    // Bouncing crab - using graphicsLayer for GPU-accelerated animation (no recomposition)
    if (containerSize.width > 0 && containerSize.height > 0) {
      Box(
        modifier = Modifier
          .size(crabSizeDp.dp)
          .graphicsLayer {
            translationX = crabX
            translationY = crabY
          }
      ) {
        CrabMascot(
          emotion = emotions[emotionIndex],
          size = crabSizeDp,
          onClick = onCrabClick
        )
      }
    }

    // OpenClaw branding in bottom-left
    Text(
      text = "OpenClaw",
      style = MaterialTheme.typography.titleLarge,
      color = Color.White.copy(alpha = 0.15f),
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(48.dp)
    )
  }
}

private data class ScreenSaverParticle(
  val x: Float,
  var y: Float,
  val size: Float,
  val speed: Float,
  var alpha: Float,
  val color: Color
)
