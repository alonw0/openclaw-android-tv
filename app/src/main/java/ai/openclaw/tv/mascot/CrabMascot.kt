package ai.openclaw.tv.mascot

import ai.openclaw.tv.CrabEmotion
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

/**
 * Animated crab mascot using the official OpenClaw logo.
 * Displays in the corner with emotion-based animations and particle effects.
 * Shows attention message in speech bubble when provided.
 */
@Composable
fun CrabMascot(
  emotion: CrabEmotion,
  size: Int = 160,
  message: String? = null,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "crab_animations")
  val scope = rememberCoroutineScope()

  // Animation configurations based on emotion
  val animationConfig = remember(emotion) { AnimationConfig.forEmotion(emotion) }

  // Base breathing/scale animation
  val breatheScale by infiniteTransition.animateFloat(
    initialValue = animationConfig.breatheRange.first,
    targetValue = animationConfig.breatheRange.second,
    animationSpec = infiniteRepeatable(
      animation = tween(animationConfig.breatheDuration, easing = EaseInOut),
      repeatMode = RepeatMode.Reverse
    ),
    label = "breathing"
  )

  // Bobbing animation
  val bobOffset by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = animationConfig.bobHeight,
    animationSpec = infiniteRepeatable(
      animation = tween(animationConfig.bobDuration, easing = animationConfig.bobEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bobbing"
  )

  // Tilt/rotation animation
  val rotation by animateFloatAsState(
    targetValue = animationConfig.targetRotation,
    animationSpec = tween(animationConfig.rotationDuration, easing = animationConfig.rotationEasing),
    label = "rotation"
  )

  // Shake animation for ERROR
  val shakeOffset by animateFloatAsState(
    targetValue = if (emotion == CrabEmotion.ERROR) 10f else 0f,
    animationSpec = if (emotion == CrabEmotion.ERROR) {
      infiniteRepeatable(
        animation = keyframes {
          durationMillis = 200
          -10f at 0
          10f at 50
          -10f at 100
          10f at 150
          0f at 200
        },
        repeatMode = RepeatMode.Restart
      )
    } else {
      tween(100)
    },
    label = "shake"
  )

  // Pulse/glow animation for ATTENTION and SUCCESS
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = animationConfig.pulseRange.second,
    animationSpec = infiniteRepeatable(
      animation = tween(animationConfig.pulseDuration, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse"
  )

  val finalScale = when {
    emotion == CrabEmotion.ATTENTION || emotion == CrabEmotion.SUCCESS -> pulseScale
    emotion == CrabEmotion.CELEBRATING -> breatheScale * 1.05f
    else -> breatheScale
  }

  // Show speech bubble for attention with message
  val showBubble = emotion == CrabEmotion.ATTENTION && !message.isNullOrBlank()

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Start
  ) {
    // Crab mascot container
    Box(
      modifier = Modifier
        .size(size.dp)
        .offset(x = shakeOffset.dp, y = bobOffset.dp)
        .scale(finalScale)
        .graphicsLayer {
          rotationZ = rotation
        }
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onClick
        ),
      contentAlignment = Alignment.Center
    ) {
      // Particle effects canvas (behind the crab)
      ParticleEffectsCanvas(
        emotion = emotion,
        size = size
      )

      // Main crab logo
      OpenClawLogoEnhanced(
        size = size,
        emotion = emotion,
        animationConfig = animationConfig
      )

      // Sound wave rings for LISTENING
      if (emotion == CrabEmotion.LISTENING) {
        SoundWaveRings(size = size)
      }

      // Thought bubble for THINKING
      if (emotion == CrabEmotion.THINKING) {
        ThoughtBubble(size = size)
      }
    }

    // Speech bubble on the right side
    if (showBubble) {
      AttentionSpeechBubble(message = message!!)
    }
  }
}

@Composable
private fun ParticleEffectsCanvas(
  emotion: CrabEmotion,
  size: Int
) {
  val particles = remember { mutableStateListOf<Particle>() }
  val frameCount = remember { mutableIntStateOf(0) }

  LaunchedEffect(emotion) {
    particles.clear()
    frameCount.intValue = 0

    while (true) {
      frameCount.intValue++

      // Spawn new particles based on emotion
      when (emotion) {
        CrabEmotion.SLEEPING -> {
          if (frameCount.intValue % 60 == 0) {
            particles.add(createZzzParticle(size))
          }
        }
        CrabEmotion.TALKING -> {
          if (frameCount.intValue % 20 == 0) {
            particles.add(createSpeechParticle(size))
          }
        }
        CrabEmotion.EXCITED -> {
          if (frameCount.intValue % 15 == 0) {
            particles.add(createStarParticle(size))
          }
        }
        CrabEmotion.CELEBRATING -> {
          if (frameCount.intValue % 8 == 0) {
            particles.add(createConfettiParticle(size))
            particles.add(createStarParticle(size))
          }
        }
        CrabEmotion.ERROR -> {
          if (frameCount.intValue % 25 == 0) {
            particles.add(createSweatDropParticle(size))
          }
        }
        CrabEmotion.ATTENTION -> {
          if (frameCount.intValue % 12 == 0) {
            particles.add(createExclamationParticle(size))
          }
        }
        else -> {}
      }

      // Update particles
      val iterator = particles.iterator()
      while (iterator.hasNext()) {
        val particle = iterator.next()
        particle.update()
        if (particle.isDead()) {
          iterator.remove()
        }
      }

      delay(16) // ~60fps
    }
  }

  Canvas(modifier = Modifier.fillMaxSize()) {
    val s = size / 120f

    particles.forEach { particle ->
      when (particle.type) {
        ParticleType.ZZZ -> drawZZZ(particle, s)
        ParticleType.SPEECH -> drawSpeechLine(particle, s)
        ParticleType.STAR -> drawStar(particle, s)
        ParticleType.CONFETTI -> drawConfetti(particle, s)
        ParticleType.SWEAT -> drawSweatDrop(particle, s)
        ParticleType.EXCLAMATION -> drawExclamation(particle, s)
      }
    }
  }
}

@Composable
private fun SoundWaveRings(size: Int) {
  val infiniteTransition = rememberInfiniteTransition(label = "sound_waves")
  
  // Pre-calculate ring animations outside Canvas
  val ringProgress1 by infiniteTransition.animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, delayMillis = 0, easing = EaseOut),
      repeatMode = RepeatMode.Restart
    ), label = "ring1"
  )
  val ringProgress2 by infiniteTransition.animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, delayMillis = 400, easing = EaseOut),
      repeatMode = RepeatMode.Restart
    ), label = "ring2"
  )
  val ringProgress3 by infiniteTransition.animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, delayMillis = 800, easing = EaseOut),
      repeatMode = RepeatMode.Restart
    ), label = "ring3"
  )

  Canvas(modifier = Modifier.fillMaxSize()) {
    val s = size / 120f
    val centerX = size / 2f
    val centerY = size * 0.45f

    // Draw 3 expanding rings
    val ringProgresses = listOf(ringProgress1, ringProgress2, ringProgress3)
    
    ringProgresses.forEach { progress ->
      val ringRadius = 20 * s + (40 * s * progress)
      val ringAlpha = 1f - progress
      val ringStroke = 3 * s * (1f - progress * 0.5f)

      drawCircle(
        color = Color(0xFF00E5CC).copy(alpha = ringAlpha * 0.6f),
        radius = ringRadius,
        center = Offset(centerX, centerY),
        style = Stroke(width = ringStroke)
      )
    }
  }
}

@Composable
private fun ThoughtBubble(size: Int) {
  val infiniteTransition = rememberInfiniteTransition(label = "thought_bubble")

  val bubbleScale by infiniteTransition.animateFloat(
    initialValue = 0.9f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = EaseInOut),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bubble_scale"
  )

  val gearRotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(3000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "gear_rotation"
  )

  Canvas(
    modifier = Modifier
      .fillMaxSize()
      .scale(bubbleScale)
  ) {
    val s = size / 120f

    // Thought bubble cloud shape
    val bubblePath = Path().apply {
      // Main bubble
      addOval(
        androidx.compose.ui.geometry.Rect(
          70 * s, -20 * s, 110 * s, 20 * s
        )
      )
      // Small bubble 1
      addOval(
        androidx.compose.ui.geometry.Rect(
          60 * s, 15 * s, 70 * s, 25 * s
        )
      )
      // Small bubble 2
      addOval(
        androidx.compose.ui.geometry.Rect(
          55 * s, 25 * s, 62 * s, 32 * s
        )
      )
    }

    drawPath(
      path = bubblePath,
      color = Color.White.copy(alpha = 0.95f),
      style = Stroke(width = 2 * s)
    )

    drawPath(
      path = bubblePath,
      color = Color.White.copy(alpha = 0.3f)
    )

    // Draw rotating gear inside
    rotate(gearRotation, pivot = Offset(90 * s, 0f)) {
      drawGear(center = Offset(90 * s, 0f), radius = 12 * s, s = s)
    }
  }
}

@Composable
private fun AttentionSpeechBubble(message: String) {
  Row(
    modifier = Modifier.padding(start = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left-pointing triangle
    Box(
      modifier = Modifier
        .size(12.dp)
        .background(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
            moveTo(0f, size.height / 2)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            close()
          }
        )
    )

    // Bubble background - larger for TV readability
    Box(
      modifier = Modifier
        .width(420.dp)
        .background(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(16.dp)
        )
        .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Agent:",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          softWrap = false
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 4,
          softWrap = true,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun OpenClawLogoEnhanced(
  size: Int,
  emotion: CrabEmotion,
  animationConfig: AnimationConfig
) {
  val infiniteTransition = rememberInfiniteTransition(label = "logo_effects")

  // Antenna wiggle for LISTENING
  val antennaWiggle by infiniteTransition.animateFloat(
    initialValue = -5f,
    targetValue = 5f,
    animationSpec = infiniteRepeatable(
      animation = tween(400, easing = EaseInOut),
      repeatMode = RepeatMode.Reverse
    ),
    label = "antenna_wiggle"
  )

  // Eye scanning for THINKING
  val eyeScanOffset by infiniteTransition.animateFloat(
    initialValue = -3f,
    targetValue = 3f,
    animationSpec = infiniteRepeatable(
      animation = tween(1500, easing = EaseInOut),
      repeatMode = RepeatMode.Reverse
    ),
    label = "eye_scan"
  )

  // Mouth animation for TALKING
  val mouthOpen by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(200, easing = EaseInOut),
      repeatMode = RepeatMode.Reverse
    ),
    label = "mouth"
  )

  // Rainbow gradient offset for CELEBRATING
  val rainbowOffset by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(3000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "rainbow"
  )

  // Glow alpha for SUCCESS
  val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 0.8f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = EaseInOut),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glow"
  )

  Canvas(modifier = Modifier.size(size.dp)) {
    val s = size / 120f

    // Determine colors based on emotion
    val baseColor = when (emotion) {
      CrabEmotion.ERROR -> Color(0xFFFF6B6B)
      CrabEmotion.SUCCESS -> Color(0xFF51CF66)
      CrabEmotion.ATTENTION -> Color(0xFFFFD700)
      else -> Color(0xFFFF4D4D)
    }

    // Create gradient brush
    val gradientBrush = when (emotion) {
      CrabEmotion.CELEBRATING -> {
        // Rainbow gradient
        Brush.sweepGradient(
          colors = listOf(
            Color(0xFFFF0000),
            Color(0xFFFF7F00),
            Color(0xFFFFFF00),
            Color(0xFF00FF00),
            Color(0xFF0000FF),
            Color(0xFF4B0082),
            Color(0xFF9400D3),
            Color(0xFFFF0000)
          ),
          center = Offset(size / 2f, size / 2f)
        )
      }
      CrabEmotion.SUCCESS -> {
        Brush.radialGradient(
          colors = listOf(
            Color(0xFF51CF66).copy(alpha = glowAlpha),
            Color(0xFF2E8B3A)
          ),
          center = Offset(size / 2f, size / 2f),
          radius = size * 0.8f
        )
      }
      else -> {
        Brush.linearGradient(
          colors = listOf(
            if (emotion == CrabEmotion.ERROR) Color(0xFFFF6B6B) else Color(0xFFFF4D4D),
            if (emotion == CrabEmotion.ERROR) Color(0xFFCC0000) else Color(0xFF991B1B)
          ),
          start = Offset(0f, 0f),
          end = Offset(size.toFloat(), size.toFloat())
        )
      }
    }

    // Draw glow for SUCCESS and ATTENTION
    if (emotion == CrabEmotion.SUCCESS || emotion == CrabEmotion.ATTENTION) {
      val glowColor = if (emotion == CrabEmotion.SUCCESS) Color(0xFF51CF66) else Color(0xFFFFD700)
      drawCircle(
        color = glowColor.copy(alpha = glowAlpha * 0.4f),
        radius = 70 * s,
        center = Offset(60 * s, 60 * s)
      )
    }

    // Body path
    val bodyPath = createCrabBodyPath(s)

    // Draw body
    drawPath(
      path = bodyPath,
      brush = gradientBrush
    )

    // Draw claws
    val leftClawPath = createLeftClawPath(s)
    val rightClawPath = createRightClawPath(s)

    drawPath(path = leftClawPath, brush = gradientBrush)
    drawPath(path = rightClawPath, brush = gradientBrush)

    // Draw antennae with wiggle effect for LISTENING
    val leftAntennaEnd = if (emotion == CrabEmotion.LISTENING) {
      Offset((35 + antennaWiggle) * s, 8 * s)
    } else {
      Offset(30 * s, 8 * s)
    }

    val rightAntennaEnd = if (emotion == CrabEmotion.LISTENING) {
      Offset((85 - antennaWiggle) * s, 8 * s)
    } else {
      Offset(90 * s, 8 * s)
    }

    drawPath(
      path = Path().apply {
        moveTo(45 * s, 15 * s)
        quadraticBezierTo(35 * s, 5 * s, leftAntennaEnd.x, leftAntennaEnd.y)
      },
      color = baseColor,
      style = Stroke(width = 3 * s, cap = StrokeCap.Round)
    )

    drawPath(
      path = Path().apply {
        moveTo(75 * s, 15 * s)
        quadraticBezierTo(85 * s, 5 * s, rightAntennaEnd.x, rightAntennaEnd.y)
      },
      color = baseColor,
      style = Stroke(width = 3 * s, cap = StrokeCap.Round)
    )

    // Draw eyes based on emotion and eye state
    drawEars(
      emotion = emotion,
      s = s,
      eyeScanOffset = eyeScanOffset,
      animationConfig = animationConfig
    )

    // Draw mouth for TALKING
    if (emotion == CrabEmotion.TALKING) {
      drawMouth(s, mouthOpen, baseColor)
    }

    // Draw checkmark for SUCCESS
    if (emotion == CrabEmotion.SUCCESS) {
      drawCheckmark(s)
    }
  }
}

private fun DrawScope.createCrabBodyPath(s: Float): Path {
  return Path().apply {
    moveTo(60 * s, 10 * s)
    cubicTo(30 * s, 10 * s, 15 * s, 35 * s, 15 * s, 55 * s)
    cubicTo(15 * s, 75 * s, 30 * s, 95 * s, 45 * s, 100 * s)
    lineTo(45 * s, 110 * s)
    lineTo(55 * s, 110 * s)
    lineTo(55 * s, 100 * s)
    cubicTo(55 * s, 100 * s, 60 * s, 102 * s, 65 * s, 100 * s)
    lineTo(65 * s, 110 * s)
    lineTo(75 * s, 110 * s)
    lineTo(75 * s, 100 * s)
    cubicTo(90 * s, 95 * s, 105 * s, 75 * s, 105 * s, 55 * s)
    cubicTo(105 * s, 35 * s, 90 * s, 10 * s, 60 * s, 10 * s)
    close()
  }
}

private fun DrawScope.createLeftClawPath(s: Float): Path {
  return Path().apply {
    moveTo(20 * s, 45 * s)
    cubicTo(5 * s, 40 * s, 0 * s, 50 * s, 5 * s, 60 * s)
    cubicTo(10 * s, 70 * s, 20 * s, 65 * s, 25 * s, 55 * s)
    cubicTo(28 * s, 48 * s, 25 * s, 45 * s, 20 * s, 45 * s)
    close()
  }
}

private fun DrawScope.createRightClawPath(s: Float): Path {
  return Path().apply {
    moveTo(100 * s, 45 * s)
    cubicTo(115 * s, 40 * s, 120 * s, 50 * s, 115 * s, 60 * s)
    cubicTo(110 * s, 70 * s, 100 * s, 65 * s, 95 * s, 55 * s)
    cubicTo(92 * s, 48 * s, 95 * s, 45 * s, 100 * s, 45 * s)
    close()
  }
}

private fun DrawScope.drawEars(
  emotion: CrabEmotion,
  s: Float,
  eyeScanOffset: Float,
  animationConfig: AnimationConfig
) {
  when (animationConfig.eyeState) {
    EyeState.OPEN -> {
      // Open eyes with pupils
      drawCircle(
        color = Color(0xFF050810),
        radius = 6 * s,
        center = Offset(45 * s, 35 * s)
      )
      drawCircle(
        color = Color(0xFF050810),
        radius = 6 * s,
        center = Offset(75 * s, 35 * s)
      )

      val pupilOffset = when {
        emotion == CrabEmotion.LISTENING -> Offset(2f, -1f)
        emotion == CrabEmotion.TALKING -> Offset(0f, -2f)
        emotion == CrabEmotion.EXCITED -> Offset(0f, -2f)
        else -> Offset(1f, -1f)
      }

      drawCircle(
        color = Color(0xFF00E5CC),
        radius = 2.5f * s,
        center = Offset((46 + pupilOffset.x) * s, (34 + pupilOffset.y) * s)
      )
      drawCircle(
        color = Color(0xFF00E5CC),
        radius = 2.5f * s,
        center = Offset((76 + pupilOffset.x) * s, (34 + pupilOffset.y) * s)
      )
    }
    EyeState.CLOSED -> {
      // Sleeping closed eyes
      drawLine(
        color = Color(0xFF050810),
        start = Offset(39 * s, 35 * s),
        end = Offset(51 * s, 35 * s),
        strokeWidth = 2 * s,
        cap = StrokeCap.Round
      )
      drawLine(
        color = Color(0xFF050810),
        start = Offset(69 * s, 35 * s),
        end = Offset(81 * s, 35 * s),
        strokeWidth = 2 * s,
        cap = StrokeCap.Round
      )
    }
    EyeState.SCANNING -> {
      // Scanning eyes that move
      drawCircle(
        color = Color(0xFF050810),
        radius = 6 * s,
        center = Offset(45 * s, 35 * s)
      )
      drawCircle(
        color = Color(0xFF050810),
        radius = 6 * s,
        center = Offset(75 * s, 35 * s)
      )
      drawCircle(
        color = Color(0xFF00E5CC),
        radius = 2.5f * s,
        center = Offset((46 + eyeScanOffset) * s, 34 * s)
      )
      drawCircle(
        color = Color(0xFF00E5CC),
        radius = 2.5f * s,
        center = Offset((76 + eyeScanOffset) * s, 34 * s)
      )
    }
    EyeState.WORRIED -> {
      // X eyes for ERROR state
      val xColor = Color(0xFF050810)
      // Left eye X
      drawLine(
        color = xColor,
        start = Offset(41 * s, 31 * s),
        end = Offset(49 * s, 39 * s),
        strokeWidth = 2.5f * s,
        cap = StrokeCap.Round
      )
      drawLine(
        color = xColor,
        start = Offset(49 * s, 31 * s),
        end = Offset(41 * s, 39 * s),
        strokeWidth = 2.5f * s,
        cap = StrokeCap.Round
      )
      // Right eye X
      drawLine(
        color = xColor,
        start = Offset(71 * s, 31 * s),
        end = Offset(79 * s, 39 * s),
        strokeWidth = 2.5f * s,
        cap = StrokeCap.Round
      )
      drawLine(
        color = xColor,
        start = Offset(79 * s, 31 * s),
        end = Offset(71 * s, 39 * s),
        strokeWidth = 2.5f * s,
        cap = StrokeCap.Round
      )
    }
    EyeState.WIDE -> {
      // Wide excited eyes
      drawCircle(
        color = Color(0xFF050810),
        radius = 7 * s,
        center = Offset(45 * s, 35 * s)
      )
      drawCircle(
        color = Color(0xFF050810),
        radius = 7 * s,
        center = Offset(75 * s, 35 * s)
      )
      // Large bright pupils
      drawCircle(
        color = Color(0xFF00E5CC),
        radius = 4 * s,
        center = Offset(45 * s, 35 * s)
      )
      drawCircle(
        color = Color(0xFF00E5CC),
        radius = 4 * s,
        center = Offset(75 * s, 35 * s)
      )
      // Sparkle highlights
      drawCircle(
        color = Color.White,
        radius = 1.5f * s,
        center = Offset(46 * s, 33 * s)
      )
      drawCircle(
        color = Color.White,
        radius = 1.5f * s,
        center = Offset(76 * s, 33 * s)
      )
    }
  }
}

private fun DrawScope.drawMouth(s: Float, openAmount: Float, color: Color) {
  val mouthY = 55 * s
  val mouthWidth = 12 * s
  val mouthHeight = 6 * s * openAmount

  if (mouthHeight > 0.5f) {
    // Open mouth (ellipse)
    drawOval(
      color = Color(0xFF050810),
      topLeft = Offset((60 - mouthWidth / 2) * s, (mouthY - mouthHeight / 2)),
      size = Size(mouthWidth, mouthHeight)
    )
  } else {
    // Closed mouth (small line)
    drawLine(
      color = Color(0xFF050810),
      start = Offset(54 * s, mouthY),
      end = Offset(66 * s, mouthY),
      strokeWidth = 1.5f * s,
      cap = StrokeCap.Round
    )
  }
}

private fun DrawScope.drawCheckmark(s: Float) {
  val checkPath = Path().apply {
    moveTo(100 * s, 20 * s)
    lineTo(108 * s, 28 * s)
    lineTo(118 * s, 12 * s)
  }

  drawPath(
    path = checkPath,
    color = Color(0xFF51CF66),
    style = Stroke(width = 4 * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
  )

  // Circle badge
  drawCircle(
    color = Color(0xFF51CF66).copy(alpha = 0.3f),
    radius = 14 * s,
    center = Offset(109 * s, 20 * s),
    style = Stroke(width = 2 * s)
  )
}

private fun DrawScope.drawGear(center: Offset, radius: Float, s: Float) {
  val teeth = 8
  val innerRadius = radius * 0.6f
  val outerRadius = radius

  val gearPath = Path().apply {
    for (i in 0 until teeth) {
      val angle = (i * 2 * PI / teeth).toFloat()
      val nextAngle = ((i + 1) * 2 * PI / teeth).toFloat()
      val toothAngle = (angle + nextAngle) / 2

      if (i == 0) {
        moveTo(
          center.x + innerRadius * cos(angle),
          center.y + innerRadius * sin(angle)
        )
      }

      // Tooth outer edge
      lineTo(
        center.x + outerRadius * cos(toothAngle - 0.15f),
        center.y + outerRadius * sin(toothAngle - 0.15f)
      )
      lineTo(
        center.x + outerRadius * cos(toothAngle + 0.15f),
        center.y + outerRadius * sin(toothAngle + 0.15f)
      )

      // Back to inner
      lineTo(
        center.x + innerRadius * cos(nextAngle),
        center.y + innerRadius * sin(nextAngle)
      )
    }
    close()
  }

  drawPath(
    path = gearPath,
    color = Color(0xFF666666),
    style = Stroke(width = 2 * s)
  )

  // Center dot
  drawCircle(
    color = Color(0xFF666666),
    radius = 3 * s,
    center = center
  )
}

// Particle drawing functions
private fun DrawScope.drawZZZ(particle: Particle, s: Float) {
  val alpha = 1f - (particle.age / particle.lifetime.toFloat())
  val yOffset = -particle.age * 0.5f * s

  drawContext.canvas.nativeCanvas.apply {
    drawText(
      "Z",
      particle.x,
      particle.y + yOffset,
      android.graphics.Paint().apply {
        this.color = android.graphics.Color.argb(
          (alpha * 255).toInt(), 100, 100, 255
        )
        this.textSize = (8 + particle.size * 4) * s
        this.isAntiAlias = true
      }
    )
  }
}

private fun DrawScope.drawSpeechLine(particle: Particle, s: Float) {
  val alpha = 1f - (particle.age / particle.lifetime.toFloat())
  val yOffset = -particle.age * 0.8f * s

  drawLine(
    color = Color(0xFF00E5CC).copy(alpha = alpha),
    start = Offset(particle.x - 5 * s, particle.y + yOffset),
    end = Offset(particle.x + 5 * s, particle.y + yOffset - 8 * s),
    strokeWidth = 2 * s,
    cap = StrokeCap.Round
  )
}

private fun DrawScope.drawStar(particle: Particle, s: Float) {
  val alpha = 1f - (particle.age / particle.lifetime.toFloat())
  val scale = 1f - (particle.age / particle.lifetime.toFloat()) * 0.5f

  drawStarShape(
    center = Offset(particle.x, particle.y),
    radius = particle.size * 4 * s * scale,
    alpha = alpha,
    color = Color(0xFFFFD700)
  )
}

private fun DrawScope.drawStarShape(center: Offset, radius: Float, alpha: Float, color: Color) {
  val points = 5
  val path = Path()

  for (i in 0 until points * 2) {
    val angle = (i * PI / points - PI / 2).toFloat()
    val r = if (i % 2 == 0) radius else radius * 0.4f
    val x = center.x + r * cos(angle)
    val y = center.y + r * sin(angle)

    if (i == 0) {
      path.moveTo(x, y)
    } else {
      path.lineTo(x, y)
    }
  }
  path.close()

  drawPath(path = path, color = color.copy(alpha = alpha))
}

private fun DrawScope.drawConfetti(particle: Particle, s: Float) {
  val alpha = 1f - (particle.age / particle.lifetime.toFloat())
  val yOffset = particle.age * 0.5f * s
  val rotation = (particle.age * 5f).toFloat()

  rotate(rotation, pivot = Offset(particle.x, particle.y + yOffset)) {
    drawRect(
      color = particle.color.copy(alpha = alpha),
      topLeft = Offset(particle.x - 3 * s, particle.y + yOffset - 2 * s),
      size = Size(6 * s, 4 * s)
    )
  }
}

private fun DrawScope.drawSweatDrop(particle: Particle, s: Float) {
  val alpha = 1f - (particle.age / particle.lifetime.toFloat())
  val yOffset = particle.age * 0.6f * s

  drawCircle(
    color = Color(0xFF4FC3F7).copy(alpha = alpha * 0.8f),
    radius = particle.size * 2 * s,
    center = Offset(particle.x, particle.y + yOffset)
  )
}

private fun DrawScope.drawExclamation(particle: Particle, s: Float) {
  val alpha = 1f - (particle.age / particle.lifetime.toFloat())
  val yOffset = -particle.age * 0.7f * s

  drawContext.canvas.nativeCanvas.apply {
    drawText(
      "!",
      particle.x,
      particle.y + yOffset,
      android.graphics.Paint().apply {
        this.color = android.graphics.Color.argb(
          (alpha * 255).toInt(), 255, 215, 0
        )
        this.textSize = (12 + particle.size * 6) * s
        this.isAntiAlias = true
        this.isFakeBoldText = true
      }
    )
  }
}

// Particle creation functions
private fun createZzzParticle(size: Int): Particle {
  val s = size / 120f
  return Particle(
    type = ParticleType.ZZZ,
    x = 60 * s + Random.nextFloat() * 20 * s - 10 * s,
    y = 20 * s,
    size = Random.nextFloat() * 0.5f + 0.5f,
    lifetime = 120
  )
}

private fun createSpeechParticle(size: Int): Particle {
  val s = size / 120f
  return Particle(
    type = ParticleType.SPEECH,
    x = 60 * s + Random.nextFloat() * 30 * s - 15 * s,
    y = 30 * s,
    size = Random.nextFloat() * 0.5f + 0.8f,
    lifetime = 40
  )
}

private fun createStarParticle(size: Int): Particle {
  val s = size / 120f
  val angle = Random.nextFloat() * 2 * PI.toFloat()
  val distance = 40 * s + Random.nextFloat() * 30 * s

  return Particle(
    type = ParticleType.STAR,
    x = 60 * s + cos(angle) * distance,
    y = 60 * s + sin(angle) * distance,
    size = Random.nextFloat() * 0.8f + 0.5f,
    lifetime = 60
  )
}

private fun createConfettiParticle(size: Int): Particle {
  val s = size / 120f
  val colors = listOf(
    Color(0xFFFF0000),
    Color(0xFF00FF00),
    Color(0xFF0000FF),
    Color(0xFFFFFF00),
    Color(0xFFFF00FF),
    Color(0xFF00FFFF)
  )

  return Particle(
    type = ParticleType.CONFETTI,
    x = 20 * s + Random.nextFloat() * 80 * s,
    y = -10 * s,
    size = Random.nextFloat() * 0.6f + 0.4f,
    lifetime = 100 + Random.nextInt(50),
    color = colors.random()
  )
}

private fun createSweatDropParticle(size: Int): Particle {
  val s = size / 120f
  return Particle(
    type = ParticleType.SWEAT,
    x = 50 * s + Random.nextFloat() * 20 * s,
    y = 15 * s,
    size = Random.nextFloat() * 0.4f + 0.6f,
    lifetime = 50
  )
}

private fun createExclamationParticle(size: Int): Particle {
  val s = size / 120f
  return Particle(
    type = ParticleType.EXCLAMATION,
    x = 60 * s + Random.nextFloat() * 30 * s - 15 * s,
    y = 25 * s,
    size = Random.nextFloat() * 0.4f + 0.8f,
    lifetime = 50
  )
}

// Data classes
enum class EyeState {
  OPEN,
  CLOSED,
  SCANNING,
  WORRIED,
  WIDE
}

enum class ParticleType {
  ZZZ,
  SPEECH,
  STAR,
  CONFETTI,
  SWEAT,
  EXCLAMATION
}

data class Particle(
  val type: ParticleType,
  var x: Float,
  var y: Float,
  val size: Float,
  val lifetime: Int,
  val color: Color = Color.White,
  var age: Int = 0
) {
  fun update() {
    age++
  }

  fun isDead(): Boolean = age >= lifetime
}

data class AnimationConfig(
  val breatheRange: Pair<Float, Float> = 0.95f to 1.05f,
  val breatheDuration: Int = 2000,
  val bobHeight: Float = 8f,
  val bobDuration: Int = 1000,
  val bobEasing: Easing = EaseInOut,
  val targetRotation: Float = 0f,
  val rotationDuration: Int = 300,
  val rotationEasing: Easing = EaseInOut,
  val pulseRange: Pair<Float, Float> = 1f to 1.1f,
  val pulseDuration: Int = 1000,
  val eyeState: EyeState = EyeState.OPEN
) {
  companion object {
    fun forEmotion(emotion: CrabEmotion): AnimationConfig {
      return when (emotion) {
        CrabEmotion.SLEEPING -> AnimationConfig(
          breatheRange = 0.97f to 1.03f,
          breatheDuration = 3000,
          bobHeight = 3f,
          bobDuration = 2500,
          eyeState = EyeState.CLOSED
        )
        CrabEmotion.LISTENING -> AnimationConfig(
          breatheRange = 0.98f to 1.02f,
          breatheDuration = 1500,
          bobHeight = 2f,
          bobDuration = 2000,
          targetRotation = 5f,
          eyeState = EyeState.OPEN
        )
        CrabEmotion.THINKING -> AnimationConfig(
          breatheRange = 0.96f to 1.04f,
          breatheDuration = 1800,
          bobHeight = 6f,
          bobDuration = 1500,
          eyeState = EyeState.SCANNING
        )
        CrabEmotion.TALKING -> AnimationConfig(
          breatheRange = 0.95f to 1.08f,
          breatheDuration = 400,
          bobHeight = 10f,
          bobDuration = 300,
          eyeState = EyeState.OPEN
        )
        CrabEmotion.EXCITED -> AnimationConfig(
          breatheRange = 0.9f to 1.15f,
          breatheDuration = 400,
          bobHeight = 15f,
          bobDuration = 250,
          targetRotation = 8f,
          rotationEasing = EaseOutBack,
          eyeState = EyeState.WIDE
        )
        CrabEmotion.CELEBRATING -> AnimationConfig(
          breatheRange = 0.85f to 1.2f,
          breatheDuration = 350,
          bobHeight = 25f,
          bobDuration = 200,
          pulseRange = 1f to 1.3f,
          pulseDuration = 300,
          eyeState = EyeState.WIDE
        )
        CrabEmotion.SUCCESS -> AnimationConfig(
          breatheRange = 0.95f to 1.08f,
          breatheDuration = 800,
          bobHeight = 8f,
          bobDuration = 600,
          pulseRange = 1f to 1.15f,
          pulseDuration = 800,
          targetRotation = 360f,
          rotationDuration = 600,
          rotationEasing = EaseOutBack,
          eyeState = EyeState.OPEN
        )
        CrabEmotion.ERROR -> AnimationConfig(
          breatheRange = 0.95f to 1.02f,
          breatheDuration = 500,
          bobHeight = 2f,
          bobDuration = 200,
          targetRotation = -8f,
          eyeState = EyeState.WORRIED
        )
        CrabEmotion.ATTENTION -> AnimationConfig(
          breatheRange = 0.9f to 1.2f,
          breatheDuration = 400,
          bobHeight = 20f,
          bobDuration = 200,
          pulseRange = 1f to 1.4f,
          pulseDuration = 500,
          targetRotation = 5f,
          rotationDuration = 200,
          eyeState = EyeState.WIDE
        )
      }
    }
  }
}
