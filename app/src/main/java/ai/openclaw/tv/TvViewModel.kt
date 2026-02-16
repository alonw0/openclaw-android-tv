package ai.openclaw.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel that bridges the TvNodeRuntime with the UI.
 * Exposes runtime state as Compose-friendly StateFlows.
 */
class TvViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as TvNodeApp
  val runtime = app.runtime
  val canvas = runtime.canvas

  // Connection state
  val isConnected: StateFlow<Boolean> = runtime.isConnected
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  val statusText: StateFlow<String> = runtime.statusText
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Offline")

  val serverName: StateFlow<String?> = runtime.serverName
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  // Crab state
  val crabEmotion: StateFlow<CrabEmotion> = runtime.crabEmotion
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CrabEmotion.SLEEPING)

  val crabVisible: StateFlow<Boolean> = runtime.crabVisible
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

  val crabSize: StateFlow<Int> = runtime.crabSize
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 160)

  // Voice wake - default to Off for privacy (user must opt-in)
  val voiceWakeMode: StateFlow<VoiceWakeMode> = runtime.voiceWakeMode
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoiceWakeMode.Off)

  // Screen recording
  val screenRecordActive: StateFlow<Boolean> = runtime.screenRecordActive
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  // Canvas state - true when showing local/default canvas (not gateway content)
  val isCanvasDefault: StateFlow<Boolean> = canvas.isDefaultCanvas
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

  // Show screensaver when no canvas activity (content hasn't been pushed yet)
  val showScreensaver: StateFlow<Boolean> = canvas.hasCanvasActivity
    .map { hasActivity -> !hasActivity }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

  // UI state
  val shouldCloseChat: StateFlow<Boolean> = runtime.shouldCloseChat
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  fun resetCloseChat() {
    runtime.resetCloseChat()
  }

  // Settings
  val displayName: StateFlow<String> = runtime.displayName
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "TV Node")

  fun onForeground(foreground: Boolean) {
    // Handle foreground state if needed
  }

  fun setCrabVisible(visible: Boolean) {
    runtime.setCrabVisible(visible)
  }

  fun setCrabSize(size: Int) {
    runtime.setCrabSize(size)
  }

  fun setVoiceWakeMode(mode: VoiceWakeMode) {
    runtime.setVoiceWakeMode(mode)
  }

  fun disconnect() {
    runtime.disconnect()
  }

  fun setManualTls(enabled: Boolean) {
    runtime.prefs.setManualTls(enabled)
  }
}
