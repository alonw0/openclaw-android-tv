package ai.openclaw.tv.ui

import ai.openclaw.tv.TvViewModel
import ai.openclaw.tv.VoiceWakeMode
import ai.openclaw.tv.overlay.FloatingCrabService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/**
 * Settings panel for TV Node configuration.
 */
@Composable
fun SettingsPanel(
  viewModel: TvViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val floatingEnabled by viewModel.runtime.prefs.floatingCrabEnabled.collectAsState()
  val canDrawOverlays = remember { FloatingCrabService.canDrawOverlays(context) }
  var showPermissionDialog by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxWidth(0.6f)
      .background(MaterialTheme.colorScheme.surface)
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "Settings",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(24.dp))

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Floating Crab Toggle
      item {
        SettingsToggleItem(
          title = "Floating Crab",
          subtitle = if (floatingEnabled) {
            if (canDrawOverlays) "Active - appears on all screens" else "Needs permission"
          } else "Disabled",
          checked = floatingEnabled,
          onCheckedChange = { checked ->
            if (checked && !canDrawOverlays) {
              showPermissionDialog = true
            } else {
              viewModel.runtime.prefs.setFloatingCrabEnabled(checked)
            }
          }
        )
      }

      // Voice Wake Toggle
      item {
        val voiceWakeMode by viewModel.runtime.voiceWakeMode.collectAsState()
        val hasMicPermission = remember {
          androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
          ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        SettingsToggleItem(
          title = "Voice Wake",
          subtitle = when (voiceWakeMode) {
            VoiceWakeMode.Off -> "Disabled - say \"openclaw\" to activate"
            VoiceWakeMode.Foreground -> "Active when app is open"
            VoiceWakeMode.Always -> "Always listening"
          } + if (!hasMicPermission && voiceWakeMode != VoiceWakeMode.Off) " (needs mic permission)" else "",
          checked = voiceWakeMode != VoiceWakeMode.Off,
          onCheckedChange = { checked ->
            val newMode = if (checked) VoiceWakeMode.Foreground else VoiceWakeMode.Off
            viewModel.runtime.setVoiceWakeMode(newMode)
          }
        )
      }

      // Display Name Setting
      item {
        val displayName by viewModel.displayName.collectAsState()
        SettingsTextItem(
          title = "Display Name",
          value = displayName,
          onClick = { /* TODO: Edit name dialog */ }
        )
      }

      // Crab Size Setting
      item {
        val crabSize by viewModel.crabSize.collectAsState()
        SettingsValueItem(
          title = "Crab Size",
          value = "${crabSize}px",
          onClick = { /* TODO: Size picker */ }
        )
      }

      // Gateway Canvas on Connect Toggle
      item {
        val showGatewayCanvas by viewModel.runtime.showGatewayCanvasOnConnect.collectAsState()
        SettingsToggleItem(
          title = "Gateway Canvas",
          subtitle = if (showGatewayCanvas) {
            "Shows gateway dashboard on connect"
          } else {
            "Shows local canvas on connect"
          },
          checked = showGatewayCanvas,
          onCheckedChange = { checked ->
            viewModel.runtime.setShowGatewayCanvasOnConnect(checked)
          }
        )
      }

      // DVD Mode Easter Egg
      item {
        val dvdMode by viewModel.runtime.prefs.dvdScreensaverEnabled.collectAsState()
        SettingsToggleItem(
          title = "DVD Mode",
          subtitle = if (dvdMode) {
            "Classic bouncing logo screensaver"
          } else {
            "Default crab screensaver"
          },
          checked = dvdMode,
          onCheckedChange = { checked ->
            viewModel.runtime.prefs.setDvdScreensaverEnabled(checked)
          }
        )
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    TvButton(
      text = "Close",
      onClick = onDismiss
    )
  }

  // Permission Dialog
  if (showPermissionDialog) {
    AlertDialog(
      onDismissRequest = { showPermissionDialog = false },
      title = { Text("Permission Required") },
      text = {
        Text("To show the floating crab on all screens, you need to grant 'Draw over other apps' permission in Android Settings.")
      },
      confirmButton = {
        var isOpenSettingsFocused by remember { mutableStateOf(false) }
        TextButton(
          onClick = {
            FloatingCrabService.requestPermission(context)
            showPermissionDialog = false
          },
          modifier = Modifier
            .onFocusChanged { isOpenSettingsFocused = it.isFocused }
            .scale(if (isOpenSettingsFocused) 1.1f else 1f)
        ) {
          Text("Open Settings")
        }
      },
      dismissButton = {
        var isCancelFocused by remember { mutableStateOf(false) }
        TextButton(
          onClick = { showPermissionDialog = false },
          modifier = Modifier
            .onFocusChanged { isCancelFocused = it.isFocused }
            .scale(if (isCancelFocused) 1.1f else 1f)
        ) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun SettingsToggleItem(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = { onCheckedChange(!checked) }),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Row(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Switch(
        checked = checked,
        onCheckedChange = null // Handled by parent click
      )
    }
  }
}

@Composable
private fun SettingsTextItem(
  title: String,
  value: String,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Row(
      modifier = Modifier
        .padding(16.dp)
      .fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
      )
    }
  }
}

@Composable
private fun SettingsValueItem(
  title: String,
  value: String,
  onClick: () -> Unit
) {
  SettingsTextItem(title = title, value = value, onClick = onClick)
}
