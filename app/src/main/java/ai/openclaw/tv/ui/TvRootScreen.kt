package ai.openclaw.tv.ui

import ai.openclaw.tv.CrabEmotion
import ai.openclaw.tv.TvViewModel
import ai.openclaw.tv.mascot.CrabMascot
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Root screen for OpenClaw TV.
 * Displays the Canvas with the crab mascot overlay.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvRootScreen(viewModel: TvViewModel) {
  val isConnected by viewModel.isConnected.collectAsState()
  val statusText by viewModel.statusText.collectAsState()
  val serverName by viewModel.serverName.collectAsState()
  val crabEmotion by viewModel.crabEmotion.collectAsState()
  val crabVisible by viewModel.crabVisible.collectAsState()
  val crabSize by viewModel.crabSize.collectAsState()
  val screenRecordActive by viewModel.screenRecordActive.collectAsState()
  val showScreensaver by viewModel.showScreensaver.collectAsState()
  val dvdMode by viewModel.runtime.prefs.dvdScreensaverEnabled.collectAsState()

  var showConnectionPanel by remember { mutableStateOf(false) }
  var showChatScreen by remember { mutableStateOf(false) }
  var showSettingsPanel by remember { mutableStateOf(false) }
  var canvasFocused by remember { mutableStateOf(false) }
  val shouldCloseChat by viewModel.shouldCloseChat.collectAsState()

  // Auto-close chat when agent uses canvas
  LaunchedEffect(shouldCloseChat) {
    if (shouldCloseChat && showChatScreen) {
      showChatScreen = false
      viewModel.resetCloseChat()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    // Canvas takes full screen
    CanvasScreen(
      canvas = viewModel.canvas,
      isFocused = canvasFocused,
      onBackPressed = { canvasFocused = false },
      modifier = Modifier.fillMaxSize()
    )

    // Screensaver overlay when no canvas activity yet
    if (showScreensaver) {
      ScreenSaver(
        onCrabClick = { showConnectionPanel = true },
        dvdMode = dvdMode,
        modifier = Modifier.fillMaxSize()
      )
    }

    // Status bar at top (clickable to open connection panel) - spans full width
    // Hide when canvas is focused to give full screen to content
    if (!canvasFocused) {
      ConnectionStatusBar(
        isConnected = isConnected,
        statusText = statusText,
        serverName = serverName,
        onConnectionClick = { showConnectionPanel = true },
        onChatClick = { showChatScreen = true },
        onCanvasClick = { canvasFocused = true },
        onSettingsClick = { showSettingsPanel = true },
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(horizontal = 48.dp, vertical = 27.dp)
      )
    }

    // Recording indicator (hidden when canvas is focused)
    if (screenRecordActive && !canvasFocused) {
      RecordingIndicator(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(48.dp)
      )
    }

    // Crab mascot in bottom-right corner (hidden when screensaver is active or canvas is focused)
    if (crabVisible && !showScreensaver && !canvasFocused) {
      CrabMascot(
        emotion = crabEmotion,
        size = crabSize,
        onClick = { showConnectionPanel = true },
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(48.dp)
      )
    }

    // Connection panel dialog
    if (showConnectionPanel) {
      Dialog(
        onDismissRequest = { showConnectionPanel = false },
        properties = DialogProperties(
          usePlatformDefaultWidth = false,
          dismissOnBackPress = true,
          dismissOnClickOutside = true
        )
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
          contentAlignment = Alignment.Center
        ) {
          ConnectionPanel(
            viewModel = viewModel,
            onDismiss = { showConnectionPanel = false }
          )
        }
      }
    }

    // Chat screen dialog
    if (showChatScreen) {
      Dialog(
        onDismissRequest = { showChatScreen = false },
        properties = DialogProperties(
          usePlatformDefaultWidth = false,
          dismissOnBackPress = true,
          dismissOnClickOutside = false
        )
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
        ) {
          TvChatScreen(
            viewModel = viewModel,
            onDismiss = { showChatScreen = false }
          )
        }
      }
    }

    // Settings panel dialog
    if (showSettingsPanel) {
      Dialog(
        onDismissRequest = { showSettingsPanel = false },
        properties = DialogProperties(
          usePlatformDefaultWidth = false,
          dismissOnBackPress = true,
          dismissOnClickOutside = true
        )
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
          contentAlignment = Alignment.Center
        ) {
          SettingsPanel(
            viewModel = viewModel,
            onDismiss = { showSettingsPanel = false }
          )
        }
      }
    }
  }
}

@Composable
private fun ConnectionStatusBar(
  isConnected: Boolean,
  statusText: String,
  serverName: String?,
  onConnectionClick: () -> Unit,
  onChatClick: () -> Unit,
  onCanvasClick: () -> Unit,
  onSettingsClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Status indicator (clickable to open connection panel) on the left
    var isStatusFocused by remember { mutableStateOf(false) }
    Row(
      modifier = Modifier
        .onFocusChanged { isStatusFocused = it.isFocused }
        .focusable()
        .clickable(onClick = onConnectionClick)
        .scale(if (isStatusFocused) 1.05f else 1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Connection dot
      Box(
        modifier = Modifier
          .size(12.dp)
          .background(
            color = if (isConnected) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.error
            },
            shape = androidx.compose.foundation.shape.CircleShape
          )
      )

      Text(
        text = serverName ?: statusText,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground
      )
    }

    // Action buttons row on the right
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Chat button
      TvCompactButton(
        text = "💬",
        onClick = onChatClick
      )

      // Canvas focus button
      TvCompactButton(
        text = "🖼️",
        onClick = onCanvasClick
      )

      // Connection button
      TvCompactButton(
        text = "🔌",
        onClick = onConnectionClick
      )

      // Settings button
      TvCompactButton(
        text = "⚙️",
        onClick = onSettingsClick
      )
    }
  }
}

@Composable
private fun RecordingIndicator(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .background(
        color = MaterialTheme.colorScheme.error,
        shape = MaterialTheme.shapes.small
      )
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    Text(
      text = "● REC",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onError
    )
  }
}

@Composable
private fun TvCompactButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }
  
  Button(
    onClick = onClick,
    modifier = modifier
      .height(40.dp)
      .widthIn(min = 48.dp)
      .onFocusChanged { isFocused = it.isFocused }
      .scale(if (isFocused) 1.15f else 1f),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium
    )
  }
}
