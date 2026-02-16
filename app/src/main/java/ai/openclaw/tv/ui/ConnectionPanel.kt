package ai.openclaw.tv.ui

import ai.openclaw.tv.TvViewModel
import ai.openclaw.tv.gateway.GatewayEndpoint
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ConnectionPanel(
  viewModel: TvViewModel,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val gateways by viewModel.runtime.gateways.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val statusText by viewModel.statusText.collectAsState()
  val discoveryStatus by viewModel.runtime.discoveryStatusText.collectAsState()
  val manualEnabled by viewModel.runtime.prefs.manualEnabled.collectAsState()
  val manualHost by viewModel.runtime.prefs.manualHost.collectAsState()
  val manualPort by viewModel.runtime.prefs.manualPort.collectAsState()
  val manualTls by viewModel.runtime.prefs.manualTls.collectAsState()

  var showManualEntry by remember { mutableStateOf(false) }
  val isEmulator = remember { isEmulator() }

  Column(
    modifier = modifier
      .fillMaxWidth(0.6f)
      .background(MaterialTheme.colorScheme.surface)
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "Connect to Gateway",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(16.dp))

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = if (isConnected) Color(0xFF51CF66) else MaterialTheme.colorScheme.surfaceVariant
      )
    ) {
      Text(
        text = if (isConnected) "✓ Connected" else statusText,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = if (isConnected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Discovery: $discoveryStatus",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    if (gateways.isNotEmpty()) {
      Text(
        text = "Discovered Gateways:",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(8.dp))

      LazyColumn(
        modifier = Modifier.height(200.dp),
        userScrollEnabled = false
      ) {
        items(gateways) { gateway ->
          GatewayCard(
            gateway = gateway,
            isConnected = isConnected && viewModel.runtime.serverName.value == gateway.name,
            onClick = {
              viewModel.runtime.connect(gateway)
              onDismiss()
            }
          )
        }
      }
    } else {
      Text(
        text = "No gateways discovered on your network.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Text(
        text = "Make sure your OpenClaw Gateway is running.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (isEmulator) {
      Spacer(modifier = Modifier.height(16.dp))
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "ℹ️ Running in Emulator",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
          )
          Text(
            text = "Use 10.0.2.2 to reach your computer's localhost",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
          )
          Text(
            text = "Example: 10.0.2.2:18789",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    TvButton(
      text = if (manualEnabled) "Manual: $manualHost:$manualPort" else "Enter Gateway Manually",
      onClick = { showManualEntry = true }
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (isConnected) {
      TvButton(
        text = "Disconnect",
        onClick = {
          viewModel.disconnect()
          onDismiss()
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error
        )
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    TvButton(
      text = "Close",
      onClick = onDismiss
    )
  }

  if (showManualEntry) {
    ManualGatewayDialog(
      initialHost = manualHost,
      initialPort = manualPort,
      initialTls = manualTls,
      onConfirm = { host, port, useTls, token ->
        viewModel.runtime.prefs.setManualEnabled(true)
        viewModel.runtime.prefs.setManualHost(host)
        viewModel.runtime.prefs.setManualPort(port)
        viewModel.runtime.prefs.setManualTls(useTls)
        if (token.isNotBlank()) {
          viewModel.runtime.prefs.saveGatewayToken(token)
        }
        viewModel.runtime.connect(GatewayEndpoint.manual(host, port, useTls))
        showManualEntry = false
        onDismiss()
      },
      onDismiss = { showManualEntry = false }
    )
  }
}

@Composable
private fun GatewayCard(
  gateway: GatewayEndpoint,
  isConnected: Boolean,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(
      containerColor = if (isConnected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      }
    )
  ) {
    Column(
      modifier = Modifier.padding(16.dp)
    ) {
      Text(
        text = gateway.name,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = "${gateway.host}:${gateway.port}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      if (gateway.tlsEnabled) {
        Text(
          text = "🔒 TLS Enabled",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

@Composable
internal fun TvButton(
  text: String,
  onClick: () -> Unit,
  colors: ButtonColors = ButtonDefaults.buttonColors(),
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }
  
  Button(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .onFocusChanged { isFocused = it.isFocused }
      .scale(if (isFocused) 1.08f else 1f),
    colors = colors
  ) {
    Text(text)
  }
}

@Composable
private fun ManualGatewayDialog(
  initialHost: String,
  initialPort: Int,
  initialTls: Boolean,
  onConfirm: (String, Int, Boolean, String) -> Unit,
  onDismiss: () -> Unit
) {
  var host by remember { mutableStateOf(initialHost) }
  var port by remember { mutableStateOf(initialPort.toString()) }
  var useTls by remember { mutableStateOf(initialTls) }
  var token by remember { mutableStateOf("") }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth(0.8f)
        .padding(32.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "Enter Gateway Address",
          style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
          value = host,
          onValueChange = { host = it },
          label = { Text("Host") },
          placeholder = { Text("192.168.1.100") },
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = port,
          onValueChange = { port = it.filter { c -> c.isDigit() } },
          label = { Text("Port") },
          placeholder = { Text("18789") },
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "Use TLS (wss://)",
            style = MaterialTheme.typography.bodyLarge
          )
          Switch(
            checked = useTls,
            onCheckedChange = { useTls = it }
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
          value = token,
          onValueChange = { token = it },
          label = { Text("Token (optional)") },
          placeholder = { Text("Enter gateway token") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          var isCancelFocused by remember { mutableStateOf(false) }
          var isConnectFocused by remember { mutableStateOf(false) }
          
          Button(
            onClick = onDismiss,
            modifier = Modifier
              .onFocusChanged { isCancelFocused = it.isFocused }
              .scale(if (isCancelFocused) 1.12f else 1f)
          ) {
            Text("Cancel")
          }
          Button(
            onClick = {
              val portNum = port.toIntOrNull() ?: 18789
              if (host.isNotBlank()) {
                onConfirm(host, portNum, useTls, token)
              }
            },
            enabled = host.isNotBlank(),
            modifier = Modifier
              .onFocusChanged { isConnectFocused = it.isFocused }
              .scale(if (isConnectFocused) 1.12f else 1f)
          ) {
            Text("Connect")
          }
        }
      }
    }
  }
}

private fun isEmulator(): Boolean {
  return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
      || Build.FINGERPRINT.startsWith("generic")
      || Build.FINGERPRINT.startsWith("unknown")
      || Build.HARDWARE.contains("goldfish")
      || Build.HARDWARE.contains("ranchu")
      || Build.MODEL.contains("google_sdk")
      || Build.MODEL.contains("Emulator")
      || Build.MODEL.contains("Android SDK built for x86")
      || Build.MANUFACTURER.contains("Google")
      || Build.PRODUCT.contains("sdk_google")
      || Build.PRODUCT.contains("google_sdk")
      || Build.PRODUCT.contains("sdk")
      || Build.PRODUCT.contains("sdk_x86")
      || Build.PRODUCT.contains("vbox86p")
      || Build.PRODUCT.contains("emulator")
      || Build.PRODUCT.contains("simulator")
}
