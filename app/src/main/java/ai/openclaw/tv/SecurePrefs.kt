@file:Suppress("DEPRECATION")

package ai.openclaw.tv

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

class SecurePrefs(context: Context) {
  companion object {
    val defaultWakeWords: List<String> = listOf("openclaw", "claude")
    private const val displayNameKey = "node.displayName"
    private const val voiceWakeModeKey = "voiceWake.mode"
  }

  private val appContext = context.applicationContext
  private val json = Json { ignoreUnknownKeys = true }

  private val masterKey =
    MasterKey.Builder(context)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()

  private val prefs: SharedPreferences by lazy {
    createPrefs(appContext, "openclaw.tv.secure")
  }

  private val _instanceId = MutableStateFlow(loadOrCreateInstanceId())
  val instanceId: StateFlow<String> = _instanceId

  private val _displayName =
    MutableStateFlow(loadOrMigrateDisplayName(context = context))
  val displayName: StateFlow<String> = _displayName

  private val _preventSleep = MutableStateFlow(prefs.getBoolean("screen.preventSleep", true))
  val preventSleep: StateFlow<Boolean> = _preventSleep

  private val _manualEnabled =
    MutableStateFlow(prefs.getBoolean("gateway.manual.enabled", false))
  val manualEnabled: StateFlow<Boolean> = _manualEnabled

  private val _manualHost =
    MutableStateFlow(prefs.getString("gateway.manual.host", "") ?: "")
  val manualHost: StateFlow<String> = _manualHost

  private val _manualPort =
    MutableStateFlow(prefs.getInt("gateway.manual.port", 18789))
  val manualPort: StateFlow<Int> = _manualPort

  private val _manualTls =
    MutableStateFlow(prefs.getBoolean("gateway.manual.tls", false))
  val manualTls: StateFlow<Boolean> = _manualTls

  private val _lastDiscoveredStableId =
    MutableStateFlow(
      prefs.getString("gateway.lastDiscoveredStableID", "") ?: "",
    )
  val lastDiscoveredStableId: StateFlow<String> = _lastDiscoveredStableId

  private val _canvasDebugStatusEnabled =
    MutableStateFlow(prefs.getBoolean("canvas.debugStatusEnabled", false))
  val canvasDebugStatusEnabled: StateFlow<Boolean> = _canvasDebugStatusEnabled

  private val _wakeWords = MutableStateFlow(loadWakeWords())
  val wakeWords: StateFlow<List<String>> = _wakeWords

  private val _voiceWakeMode = MutableStateFlow(loadVoiceWakeMode())
  val voiceWakeMode: StateFlow<VoiceWakeMode> = _voiceWakeMode

  private val _talkEnabled = MutableStateFlow(prefs.getBoolean("talk.enabled", false))
  val talkEnabled: StateFlow<Boolean> = _talkEnabled

  // TV-specific: Crab mascot settings
  private val _crabVisible = MutableStateFlow(prefs.getBoolean("crab.visible", true))
  val crabVisible: StateFlow<Boolean> = _crabVisible

  private val _crabSize = MutableStateFlow(prefs.getInt("crab.size", 160))
  val crabSize: StateFlow<Int> = _crabSize

  // Floating overlay crab (appears on all screens)
  private val _floatingCrabEnabled = MutableStateFlow(prefs.getBoolean("crab.floating.enabled", false))
  val floatingCrabEnabled: StateFlow<Boolean> = _floatingCrabEnabled

  // Auto-navigate to gateway dashboard on connect
  private val _showGatewayCanvasOnConnect = MutableStateFlow(prefs.getBoolean("canvas.showGatewayOnConnect", true))
  val showGatewayCanvasOnConnect: StateFlow<Boolean> = _showGatewayCanvasOnConnect

  // Easter egg: DVD-style screensaver
  private val _dvdScreensaverEnabled = MutableStateFlow(prefs.getBoolean("screensaver.dvdMode", false))
  val dvdScreensaverEnabled: StateFlow<Boolean> = _dvdScreensaverEnabled

  fun setLastDiscoveredStableId(value: String) {
    val trimmed = value.trim()
    prefs.edit { putString("gateway.lastDiscoveredStableID", trimmed) }
    _lastDiscoveredStableId.value = trimmed
  }

  fun setDisplayName(value: String) {
    val trimmed = value.trim()
    prefs.edit { putString(displayNameKey, trimmed) }
    _displayName.value = trimmed
  }

  fun setPreventSleep(value: Boolean) {
    prefs.edit { putBoolean("screen.preventSleep", value) }
    _preventSleep.value = value
  }

  fun setManualEnabled(value: Boolean) {
    prefs.edit { putBoolean("gateway.manual.enabled", value) }
    _manualEnabled.value = value
  }

  fun setManualHost(value: String) {
    val trimmed = value.trim()
    prefs.edit { putString("gateway.manual.host", trimmed) }
    _manualHost.value = trimmed
  }

  fun setManualPort(value: Int) {
    prefs.edit { putInt("gateway.manual.port", value) }
    _manualPort.value = value
  }

  fun setManualTls(value: Boolean) {
    prefs.edit { putBoolean("gateway.manual.tls", value) }
    _manualTls.value = value
  }

  fun setManualToken(value: String) {
    val trimmed = value.trim()
    prefs.edit { putString("gateway.manual.token", trimmed) }
  }

  fun setCanvasDebugStatusEnabled(value: Boolean) {
    prefs.edit { putBoolean("canvas.debugStatusEnabled", value) }
    _canvasDebugStatusEnabled.value = value
  }

  fun setCrabVisible(value: Boolean) {
    prefs.edit { putBoolean("crab.visible", value) }
    _crabVisible.value = value
  }

  fun setCrabSize(value: Int) {
    prefs.edit { putInt("crab.size", value) }
    _crabSize.value = value
  }

  fun setFloatingCrabEnabled(value: Boolean) {
    prefs.edit { putBoolean("crab.floating.enabled", value) }
    _floatingCrabEnabled.value = value
  }

  fun setShowGatewayCanvasOnConnect(value: Boolean) {
    prefs.edit { putBoolean("canvas.showGatewayOnConnect", value) }
    _showGatewayCanvasOnConnect.value = value
  }

  fun setDvdScreensaverEnabled(value: Boolean) {
    prefs.edit { putBoolean("screensaver.dvdMode", value) }
    _dvdScreensaverEnabled.value = value
  }

  fun loadGatewayToken(): String? {
    val key = "gateway.token.${_instanceId.value}"
    val stored = prefs.getString(key, null)?.trim()
    return stored?.takeIf { it.isNotEmpty() }
  }

  fun saveGatewayToken(token: String) {
    val key = "gateway.token.${_instanceId.value}"
    prefs.edit { putString(key, token.trim()) }
  }

  fun loadGatewayPassword(): String? {
    val key = "gateway.password.${_instanceId.value}"
    val stored = prefs.getString(key, null)?.trim()
    return stored?.takeIf { it.isNotEmpty() }
  }

  fun saveGatewayPassword(password: String) {
    val key = "gateway.password.${_instanceId.value}"
    prefs.edit { putString(key, password.trim()) }
  }

  fun loadGatewayTlsFingerprint(stableId: String): String? {
    val key = "gateway.tls.$stableId"
    return prefs.getString(key, null)?.trim()?.takeIf { it.isNotEmpty() }
  }

  fun saveGatewayTlsFingerprint(stableId: String, fingerprint: String) {
    val key = "gateway.tls.$stableId"
    prefs.edit { putString(key, fingerprint.trim()) }
  }

  fun getString(key: String): String? {
    return prefs.getString(key, null)
  }

  fun putString(key: String, value: String) {
    prefs.edit { putString(key, value) }
  }

  fun remove(key: String) {
    prefs.edit { remove(key) }
  }

  private fun createPrefs(context: Context, name: String): SharedPreferences {
    return EncryptedSharedPreferences.create(
      context,
      name,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  private fun loadOrCreateInstanceId(): String {
    val existing = prefs.getString("node.instanceId", null)?.trim()
    if (!existing.isNullOrBlank()) return existing
    val fresh = UUID.randomUUID().toString()
    prefs.edit { putString("node.instanceId", fresh) }
    return fresh
  }

  private fun loadOrMigrateDisplayName(context: Context): String {
    val existing = prefs.getString(displayNameKey, null)?.trim().orEmpty()
    if (existing.isNotEmpty() && existing != "TV Node") return existing
    val resolved = "TV Node"
    prefs.edit { putString(displayNameKey, resolved) }
    return resolved
  }

  fun setWakeWords(words: List<String>) {
    val sanitized = WakeWords.sanitize(words, defaultWakeWords)
    val encoded = JsonArray(sanitized.map { JsonPrimitive(it) }).toString()
    prefs.edit { putString("voiceWake.triggerWords", encoded) }
    _wakeWords.value = sanitized
  }

  fun setVoiceWakeMode(mode: VoiceWakeMode) {
    prefs.edit { putString(voiceWakeModeKey, mode.rawValue) }
    _voiceWakeMode.value = mode
  }

  fun setTalkEnabled(value: Boolean) {
    prefs.edit { putBoolean("talk.enabled", value) }
    _talkEnabled.value = value
  }

  private fun loadVoiceWakeMode(): VoiceWakeMode {
    val raw = prefs.getString(voiceWakeModeKey, null)
    // Default to Off for privacy - user must explicitly enable voice wake
    return VoiceWakeMode.fromRawValue(raw)
  }

  private fun loadWakeWords(): List<String> {
    val raw = prefs.getString("voiceWake.triggerWords", null)?.trim()
    if (raw.isNullOrEmpty()) return defaultWakeWords
    return try {
      val element = json.parseToJsonElement(raw)
      val array = element as? JsonArray ?: return defaultWakeWords
      val decoded =
        array.mapNotNull { item ->
          when (item) {
            is JsonNull -> null
            is JsonPrimitive -> item.content.trim().takeIf { it.isNotEmpty() }
            else -> null
          }
        }
      WakeWords.sanitize(decoded, defaultWakeWords)
    } catch (_: Throwable) {
      defaultWakeWords
    }
  }
}