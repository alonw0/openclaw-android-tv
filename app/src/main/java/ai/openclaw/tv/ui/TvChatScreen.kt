package ai.openclaw.tv.ui

import ai.openclaw.tv.TvViewModel
import ai.openclaw.tv.chat.ChatController
import ai.openclaw.tv.chat.ChatMessage
import ai.openclaw.tv.chat.ChatPendingToolCall
import ai.openclaw.tv.CrabEmotion
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * TV-optimized chat screen with voice and text input.
 * Designed for 10-foot UI with remote navigation.
 */
@Composable
fun TvChatScreen(
  viewModel: TvViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val chatController = viewModel.runtime.chatController
  val messages by chatController.messages.collectAsState()
  val isSending by chatController.isSending.collectAsState()
  val streamingText by chatController.streamingText.collectAsState()
  val pendingTools by chatController.pendingToolCalls.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val crabEmotion by viewModel.runtime.crabEmotion.collectAsState()
  val context = LocalContext.current

  // Speech recognition launcher
  val speechRecognizerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
      if (!spokenText.isNullOrBlank()) {
        chatController.sendMessage(spokenText)
      }
    }
  }

  // Debug logging
  LaunchedEffect(messages.size) {
    android.util.Log.d("TvChatScreen", "Messages count: ${messages.size}")
    messages.forEach { msg ->
      android.util.Log.d("TvChatScreen", "Message: ${msg.role} - ${msg.content.firstOrNull()?.text?.take(30)}")
    }
  }

  var showTextInput by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  // Auto-scroll to latest message
  LaunchedEffect(messages.size, streamingText) {
    if (messages.isNotEmpty()) {
      scope.launch {
        listState.animateScrollToItem(messages.size - 1)
      }
    }
  }

  // Update crab emotion based on chat state
  LaunchedEffect(isSending, streamingText) {
    when {
      isSending && streamingText != null -> viewModel.runtime.setCrabEmotion(CrabEmotion.TALKING)
      isSending -> viewModel.runtime.setCrabEmotion(CrabEmotion.THINKING)
      else -> viewModel.runtime.setCrabEmotion(CrabEmotion.LISTENING)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(16.dp)
  ) {
    // Header
    TvChatHeader(
      isConnected = isConnected,
      onDismiss = onDismiss
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Messages list
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
    ) {
      if (messages.isEmpty() && streamingText == null) {
        // Empty state
        TvChatEmptyState(
          modifier = Modifier.align(Alignment.Center)
        )
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
              when (keyEvent.key) {
                Key.DirectionUp -> {
                  scope.launch { listState.animateScrollToItem(0) }
                  true
                }
                Key.DirectionDown -> {
                  scope.launch {
                    listState.animateScrollToItem(messages.size - 1)
                  }
                  true
                }
                else -> false
              }
            },
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(messages) { message ->
            TvChatMessageItem(message = message)
          }

          // Streaming text (partial assistant response)
          streamingText?.let { text ->
            item {
              TvStreamingMessage(text = text)
            }
          }
        }
      }
    }

    // Tool calls indicator
    AnimatedVisibility(
      visible = pendingTools.isNotEmpty(),
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      TvPendingToolsIndicator(tools = pendingTools)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Input controls
    TvChatInputControls(
      isSending = isSending,
      onVoiceClick = {
        // Open Android TV voice input (speech recognizer)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message...")
        }
        speechRecognizerLauncher.launch(intent)
      },
      onTextClick = { showTextInput = true }
    )
  }

  // Text input dialog
  if (showTextInput) {
    TvChatTextInputDialog(
      onSend = { text ->
        chatController.sendMessage(text)
        showTextInput = false
      },
      onDismiss = { showTextInput = false }
    )
  }
}

@Composable
private fun TvChatHeader(
  isConnected: Boolean,
  onDismiss: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column {
      Text(
        text = "Chat with Clawdbot",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground
      )
      Text(
        text = if (isConnected) "● Connected" else "○ Disconnected",
        style = MaterialTheme.typography.bodyMedium,
        color = if (isConnected) Color(0xFF51CF66) else MaterialTheme.colorScheme.error
      )
    }

    // Close button with visible focus indicator
    var isCloseFocused by remember { mutableStateOf(false) }
    Box(
      modifier = Modifier
        .size(48.dp)
        .onFocusChanged { isCloseFocused = it.isFocused }
        .clickable(onClick = onDismiss)
        .background(
          color = if (isCloseFocused) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else Color.Transparent,
          shape = MaterialTheme.shapes.small
        )
        .border(
          width = if (isCloseFocused) 3.dp else 0.dp,
          color = if (isCloseFocused) MaterialTheme.colorScheme.error else Color.Transparent,
          shape = MaterialTheme.shapes.small
        )
        .scale(if (isCloseFocused) 1.1f else 1f),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "✕",
        style = MaterialTheme.typography.titleMedium,
        color = if (isCloseFocused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
      )
    }
  }
}

@Composable
private fun TvChatEmptyState(
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "💬",
      style = MaterialTheme.typography.displayLarge
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = "Start a conversation",
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Use voice or type a message",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun TvChatMessageItem(
  message: ChatMessage
) {
  val isUser = message.role == "user"
  val backgroundColor = if (isUser) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceVariant
  }
  val textColor = if (isUser) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = backgroundColor),
      shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
      )
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        // Role indicator
        Text(
          text = if (isUser) "You" else "Clawdbot",
          style = MaterialTheme.typography.labelSmall,
          color = textColor.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Message content
        message.content.forEach { content ->
          when (content.type) {
            "text" -> {
              Text(
                text = content.text ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
              )
            }
            "image" -> {
              // TODO: Display images
              Text(
                text = "📎 Image attachment",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TvStreamingMessage(
  text: String
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp),
    horizontalAlignment = Alignment.Start
  ) {
    Card(
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "Clawdbot",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = text,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Typing indicator
        TypingIndicator()
      }
    }
  }
}

@Composable
private fun TypingIndicator() {
  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    repeat(3) { index ->
      val alpha by animateFloatAsState(
        targetValue = if (index == 0) 1f else 0.3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
          animation = androidx.compose.animation.core.tween(600, delayMillis = index * 200),
          repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "typing"
      )
      Box(
        modifier = Modifier
          .size(6.dp)
          .background(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            shape = RoundedCornerShape(3.dp)
          )
      )
    }
  }
}

@Composable
private fun TvPendingToolsIndicator(
  tools: List<ChatPendingToolCall>
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.tertiaryContainer
    )
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = "Running tools...",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onTertiaryContainer
      )
      Spacer(modifier = Modifier.height(4.dp))
      tools.forEach { tool ->
        Text(
          text = "• ${tool.label}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun TvChatInputControls(
  isSending: Boolean,
  onVoiceClick: () -> Unit,
  onTextClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Voice button
    TvIconButton(
      icon = Icons.Default.Mic,
      label = "Speak",
      onClick = onVoiceClick,
      isActive = false,
      modifier = Modifier.weight(1f)
    )

    Spacer(modifier = Modifier.width(16.dp))

    // Text input button
    TvIconButton(
      icon = Icons.Default.Keyboard,
      label = "Type",
      onClick = onTextClick,
      modifier = Modifier.weight(1f)
    )
  }

  if (isSending) {
    Spacer(modifier = Modifier.height(8.dp))
    LinearProgressIndicator(
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun TvIconButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isActive: Boolean = false
) {
  var isFocused by remember { mutableStateOf(false) }

  Card(
    modifier = modifier
      .height(80.dp)
      .onFocusChanged { isFocused = it.isFocused }
      .clickable(onClick = onClick)
      .scale(if (isFocused) 1.12f else 1f),
    colors = CardDefaults.cardColors(
      containerColor = if (isActive) {
        MaterialTheme.colorScheme.primary
      } else if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      }
    )
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isActive) {
          MaterialTheme.colorScheme.onPrimary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.size(32.dp)
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (isActive) {
          MaterialTheme.colorScheme.onPrimary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        }
      )
    }
  }
}

@Composable
private fun TvChatTextInputDialog(
  onSend: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var text by remember { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth(0.9f)
        .padding(32.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "Type a message",
          style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          label = { Text("Message") },
          placeholder = { Text("Ask me anything...") },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
          singleLine = false,
          maxLines = 3
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          var isCancelFocused by remember { mutableStateOf(false) }
          var isSendFocused by remember { mutableStateOf(false) }
          
          Button(
            onClick = onDismiss,
            modifier = Modifier
              .onFocusChanged { isCancelFocused = it.isFocused }
              .scale(if (isCancelFocused) 1.12f else 1f)
          ) {
            Text("Cancel")
          }
          Button(
            onClick = { onSend(text) },
            enabled = text.isNotBlank(),
            modifier = Modifier
              .onFocusChanged { isSendFocused = it.isFocused }
              .scale(if (isSendFocused) 1.12f else 1f)
          ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Send")
          }
        }
      }
    }
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}
