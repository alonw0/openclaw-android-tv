package ai.openclaw.tv

import android.Manifest
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ai.openclaw.tv.ui.TvRootScreen
import ai.openclaw.tv.ui.TvTheme
import kotlinx.coroutines.launch

/**
 * Main activity for OpenClaw TV Node.
 * Entry point for the leanback UI.
 */
class MainActivity : ComponentActivity() {
  private val viewModel: TvViewModel by viewModels()
  private lateinit var permissionRequester: PermissionRequester

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Enable WebView debugging in debug builds
    val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    WebView.setWebContentsDebuggingEnabled(isDebuggable)

    // Request permissions if needed
    requestPermissionsIfNeeded()

    // Start foreground service
    TvForegroundService.start(this)

    // Set up permission requester
    permissionRequester = PermissionRequester(this)

    // Attach screen capture requester for screen recording/snapshot
    val screenCaptureRequester = ScreenCaptureRequester(this)
    (application as TvNodeApp).runtime.screenRecorder.attachScreenCaptureRequester(screenCaptureRequester)

    // Note: Canvas WebView is attached in CanvasScreen composable

    setContent {
      TvTheme {
        TvRootScreen(viewModel = viewModel)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    viewModel.onForeground(true)
    // Notify runtime of foreground state for voice wake mode handling
    (application as TvNodeApp).runtime.setForeground(true)
  }

  override fun onStop() {
    super.onStop()
    viewModel.onForeground(false)
    // Notify runtime of background state for voice wake mode handling
    (application as TvNodeApp).runtime.setForeground(false)
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    // Enter PiP mode when user presses HOME
    if (viewModel.isConnected.value) {
      enterPictureInPictureMode()
    }
  }

  private fun requestPermissionsIfNeeded() {
    // Request microphone permission for voice input (required for Android 6+ at runtime)
    val micGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!micGranted) {
      requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 102)
    }

    // Request nearby WiFi permission for discovery (Android 13+)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      val granted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.NEARBY_WIFI_DEVICES
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      if (!granted) {
        requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 100)
      }
    }

    // Request notification permission (Android 13+)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      val granted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      if (!granted) {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
      }
    }
  }
}
