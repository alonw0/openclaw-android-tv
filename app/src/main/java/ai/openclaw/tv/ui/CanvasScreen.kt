package ai.openclaw.tv.ui

import ai.openclaw.tv.node.CanvasController
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Screen that displays the Canvas WebView for A2UI rendering.
 * Supports focus mode for D-pad interaction.
 */
@Composable
fun CanvasScreen(
  canvas: CanvasController,
  isFocused: Boolean = false,
  onBackPressed: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  // Focus the WebView when isFocused becomes true
  LaunchedEffect(isFocused) {
    if (isFocused) {
      webViewRef?.requestFocus()
    }
  }

  AndroidView(
    factory = { context ->
      WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false

        // Make focusable for D-pad navigation
        isFocusable = true
        isFocusableInTouchMode = true

        // Handle Back key to exit focus mode
        setOnKeyListener { _, keyCode, event ->
          if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBackPressed()
            true
          } else {
            false
          }
        }

        // Attach to canvas controller
        canvas.attach(this)
        webViewRef = this
      }
    },
    update = { webView ->
      webViewRef = webView
    },
    modifier = modifier
  )
}
