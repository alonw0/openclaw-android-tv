package ai.openclaw.tv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import ai.openclaw.tv.gateway.DeviceAuthStore
import ai.openclaw.tv.gateway.DeviceIdentityStore
import ai.openclaw.tv.gateway.GatewayClientInfo
import ai.openclaw.tv.gateway.GatewayConnectOptions
import ai.openclaw.tv.gateway.GatewayDiscovery
import ai.openclaw.tv.gateway.GatewayEndpoint
import ai.openclaw.tv.gateway.GatewaySession
import ai.openclaw.tv.gateway.GatewayTlsParams
import ai.openclaw.tv.node.CanvasController
import ai.openclaw.tv.node.ScreenRecordManager
import ai.openclaw.tv.protocol.OpenClawCapability
import ai.openclaw.tv.protocol.OpenClawCanvasCommand
import ai.openclaw.tv.protocol.OpenClawCanvasA2UICommand
import ai.openclaw.tv.protocol.OpenClawCrabCommand
import ai.openclaw.tv.protocol.OpenClawMediaCommand
import ai.openclaw.tv.protocol.OpenClawScreenCommand
import ai.openclaw.tv.voice.VoiceWakeManager
import ai.openclaw.tv.chat.ChatController
import ai.openclaw.tv.overlay.FloatingCrabService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ai.openclaw.tv.BuildConfig

class TvNodeRuntime(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val prefs = SecurePrefs(appContext)
  private val deviceAuthStore = DeviceAuthStore(prefs)
  val canvas = CanvasController()
  val screenRecorder = ScreenRecordManager(appContext)
  private val json = Json { ignoreUnknownKeys = true }

  private val externalAudioCaptureActive = MutableStateFlow(false)

  private val voiceWake: VoiceWakeManager by lazy {
    VoiceWakeManager(
      context = appContext,
      scope = scope,
      onCommand = { command ->
        nodeSession.sendNodeEvent(
          event = "agent.request",
          payloadJson = buildJsonObject {
            put("message", JsonPrimitive(command))
            put("sessionKey", JsonPrimitive(resolveMainSessionKey()))
            put("thinking", JsonPrimitive("low"))
            put("deliver", JsonPrimitive(false))
          }.toString(),
        )
      },
    )
  }

  val voiceWakeIsListening: StateFlow<Boolean>
    get() = voiceWake.isListening

  val voiceWakeStatusText: StateFlow<String>
    get() = voiceWake.statusText

  private val discovery = GatewayDiscovery(appContext, scope = scope)
  val gateways: StateFlow<List<GatewayEndpoint>> = discovery.gateways
  val discoveryStatusText: StateFlow<String> = discovery.statusText

  private val identityStore = DeviceIdentityStore(appContext)

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  private val _statusText = MutableStateFlow("Offline")
  val statusText: StateFlow<String> = _statusText.asStateFlow()

  private val _mainSessionKey = MutableStateFlow("main")
  val mainSessionKey: StateFlow<String> = _mainSessionKey.asStateFlow()

  private val _screenRecordActive = MutableStateFlow(false)
  val screenRecordActive: StateFlow<Boolean> = _screenRecordActive.asStateFlow()

  private val _serverName = MutableStateFlow<String?>(null)
  val serverName: StateFlow<String?> = _serverName.asStateFlow()

  private val _remoteAddress = MutableStateFlow<String?>(null)
  val remoteAddress: StateFlow<String?> = _remoteAddress.asStateFlow()

  private val _isForeground = MutableStateFlow(true)
  val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

  private var lastAutoA2uiUrl: String? = null
  private var operatorConnected = false
  private var nodeConnected = false
  private var operatorStatusText: String = "Offline"
  private var nodeStatusText: String = "Offline"
  private var connectedEndpoint: GatewayEndpoint? = null

  // Crab emotion state
  private val _crabEmotion = MutableStateFlow(CrabEmotion.SLEEPING)
  val crabEmotion: StateFlow<CrabEmotion> = _crabEmotion.asStateFlow()

  private val _shouldCloseChat = MutableStateFlow(false)
  val shouldCloseChat: StateFlow<Boolean> = _shouldCloseChat.asStateFlow()

  // Chat controller - declared lateinit since sessions need it and it needs sessions
  lateinit var chatController: ChatController
    private set

  private val operatorSession = GatewaySession(
    scope = scope,
    identityStore = identityStore,
    deviceAuthStore = deviceAuthStore,
    onConnected = { name, remote, mainSessionKey ->
      operatorConnected = true
      operatorStatusText = "Connected"
      _serverName.value = name
      _remoteAddress.value = remote
      applyMainSessionKey(mainSessionKey)
      updateStatus()
      scope.launch { refreshWakeWordsFromGateway() }
    },
    onDisconnected = { message ->
      operatorConnected = false
      operatorStatusText = message
      _serverName.value = null
      _remoteAddress.value = null
      if (!isCanonicalMainSessionKey(_mainSessionKey.value)) {
        _mainSessionKey.value = "main"
      }
      val mainKey = resolveMainSessionKey()
      if (::chatController.isInitialized) {
        chatController.applyMainSessionKey(mainKey)
        chatController.clearMessages()
      }
      updateStatus()
    },
    onEvent = { event, payloadJson ->
      handleGatewayEvent(event, payloadJson)
    },
  )

  private val nodeSession = GatewaySession(
    scope = scope,
    identityStore = identityStore,
    deviceAuthStore = deviceAuthStore,
    onConnected = { _, _, _ ->
      nodeConnected = true
      nodeStatusText = "Connected"
      updateStatus()
      maybeNavigateToA2uiOnConnect()
    },
    onDisconnected = { message ->
      nodeConnected = false
      nodeStatusText = message
      updateStatus()
      showLocalCanvasOnDisconnect()
    },
    onEvent = { _, _ -> },
    onInvoke = { req ->
      handleInvoke(req.command, req.paramsJson)
    },
    onTlsFingerprint = { stableId, fingerprint ->
      prefs.saveGatewayTlsFingerprint(stableId, fingerprint)
    },
  )

  // Initialize chat controller after both sessions are created
  init {
    chatController = ChatController(
      scope = scope,
      operatorSession = operatorSession,
      nodeSession = nodeSession,
    )
  }

  private fun applyMainSessionKey(candidate: String?) {
    val trimmed = candidate?.trim().orEmpty()
    if (trimmed.isEmpty()) return
    if (isCanonicalMainSessionKey(_mainSessionKey.value)) return
    if (_mainSessionKey.value == trimmed) return
    _mainSessionKey.value = trimmed
    chatController.applyMainSessionKey(trimmed)
  }

  private fun updateStatus() {
    _isConnected.value = operatorConnected
    _statusText.value = when {
      operatorConnected && nodeConnected -> "Connected"
      operatorConnected && !nodeConnected -> "Connected (node offline)"
      !operatorConnected && nodeConnected -> "Connected (operator offline)"
      operatorStatusText.isNotBlank() && operatorStatusText != "Offline" -> operatorStatusText
      else -> nodeStatusText
    }
  }

  private fun resolveMainSessionKey(): String {
    val trimmed = _mainSessionKey.value.trim()
    return if (trimmed.isEmpty()) "main" else trimmed
  }

  private fun maybeNavigateToA2uiOnConnect() {
    val a2uiUrl = resolveA2uiHostUrl() ?: return
    val current = canvas.currentUrl()?.trim().orEmpty()
    if (current.isEmpty() || current == lastAutoA2uiUrl) {
      lastAutoA2uiUrl = a2uiUrl
      // Always navigate to A2UI for readiness, but only mark activity if setting is ON
      canvas.navigate(a2uiUrl)
      if (prefs.showGatewayCanvasOnConnect.value) {
        canvas.markCanvasActivity() // Hide screensaver immediately
      }
    }
  }

  private fun showLocalCanvasOnDisconnect() {
    lastAutoA2uiUrl = null
    canvas.resetCanvasActivity()
    canvas.navigate("")
  }

  val instanceId: StateFlow<String> = prefs.instanceId
  val displayName: StateFlow<String> = prefs.displayName
  val preventSleep: StateFlow<Boolean> = prefs.preventSleep
  val wakeWords: StateFlow<List<String>> = prefs.wakeWords
  val voiceWakeMode: StateFlow<VoiceWakeMode> = prefs.voiceWakeMode
  val manualEnabled: StateFlow<Boolean> = prefs.manualEnabled
  val manualHost: StateFlow<String> = prefs.manualHost
  val manualPort: StateFlow<Int> = prefs.manualPort
  val manualTls: StateFlow<Boolean> = prefs.manualTls
  val lastDiscoveredStableId: StateFlow<String> = prefs.lastDiscoveredStableId
  val canvasDebugStatusEnabled: StateFlow<Boolean> = prefs.canvasDebugStatusEnabled

  // TV-specific: Crab settings
  val crabVisible: StateFlow<Boolean> = prefs.crabVisible
  val crabSize: StateFlow<Int> = prefs.crabSize
  val floatingCrabEnabled: StateFlow<Boolean> = prefs.floatingCrabEnabled

  // Canvas settings
  val showGatewayCanvasOnConnect: StateFlow<Boolean> = prefs.showGatewayCanvasOnConnect

  private var didAutoConnect = false
  private var suppressWakeWordsSync = false
  private var wakeWordsSyncJob: Job? = null

  init {
    scope.launch {
      combine(
        voiceWakeMode,
        isForeground,
        externalAudioCaptureActive,
        wakeWords,
      ) { mode, foreground, externalAudio, words ->
        Quad(mode, foreground, externalAudio, words)
      }.distinctUntilChanged()
        .collect { (mode, foreground, externalAudio, words) ->
          voiceWake.setTriggerWords(words)

          val shouldListen = when (mode) {
            VoiceWakeMode.Off -> false
            VoiceWakeMode.Foreground -> foreground
            VoiceWakeMode.Always -> true
          } && !externalAudio

          if (!shouldListen) {
            voiceWake.stop(statusText = if (mode == VoiceWakeMode.Off) "Off" else "Paused")
            return@collect
          }

          if (!hasRecordAudioPermission()) {
            voiceWake.stop(statusText = "Microphone permission required")
            return@collect
          }

          voiceWake.start()
        }
    }

    scope.launch(Dispatchers.Default) {
      gateways.collect { list ->
        if (list.isNotEmpty()) {
          prefs.setLastDiscoveredStableId(list.last().stableId)
        }

        if (didAutoConnect) return@collect
        if (_isConnected.value) return@collect

        if (manualEnabled.value) {
          val host = manualHost.value.trim()
          val port = manualPort.value
          if (host.isNotEmpty() && port in 1..65535) {
            didAutoConnect = true
            connect(GatewayEndpoint.manual(host = host, port = port))
          }
          return@collect
        }

        val targetStableId = lastDiscoveredStableId.value.trim()
        if (targetStableId.isEmpty()) return@collect
        val target = list.firstOrNull { it.stableId == targetStableId } ?: return@collect
        didAutoConnect = true
        connect(target)
      }
    }

    scope.launch {
      combine(
        canvasDebugStatusEnabled,
        statusText,
        serverName,
        remoteAddress,
      ) { debugEnabled, status, server, remote ->
        Quad(debugEnabled, status, server, remote)
      }.distinctUntilChanged()
        .collect { (debugEnabled, status, server, remote) ->
          canvas.setDebugStatusEnabled(debugEnabled)
          if (!debugEnabled) return@collect
          canvas.setDebugStatus(status, server ?: remote)
        }
    }

    // Handle floating crab service
    scope.launch {
      floatingCrabEnabled.collect { enabled ->
        if (enabled) {
          FloatingCrabService.start(appContext)
        } else {
          FloatingCrabService.stop(appContext)
        }
      }
    }

    // Set up attention callback for floating crab
    FloatingCrabService.setAttentionCallback {
      _shouldCloseChat.value = false
    }
  }

  fun setForeground(value: Boolean) {
    _isForeground.value = value
  }

  fun setDisplayName(value: String) {
    prefs.setDisplayName(value)
  }

  fun setPreventSleep(value: Boolean) {
    prefs.setPreventSleep(value)
  }

  fun setManualEnabled(value: Boolean) {
    prefs.setManualEnabled(value)
  }

  fun setManualHost(value: String) {
    prefs.setManualHost(value)
  }

  fun setManualPort(value: Int) {
    prefs.setManualPort(value)
  }

  fun setManualTls(value: Boolean) {
    prefs.setManualTls(value)
  }

  fun setCanvasDebugStatusEnabled(value: Boolean) {
    prefs.setCanvasDebugStatusEnabled(value)
  }

  fun setWakeWords(words: List<String>) {
    prefs.setWakeWords(words)
    scheduleWakeWordsSyncIfNeeded()
  }

  fun resetWakeWordsDefaults() {
    setWakeWords(SecurePrefs.defaultWakeWords)
  }

  fun setVoiceWakeMode(mode: VoiceWakeMode) {
    prefs.setVoiceWakeMode(mode)
  }

  fun setShowGatewayCanvasOnConnect(enabled: Boolean) {
    prefs.setShowGatewayCanvasOnConnect(enabled)
    // Apply immediately
    if (enabled) {
      // Show gateway canvas - navigate to A2UI and hide screensaver
      val a2uiUrl = resolveA2uiHostUrl()
      if (a2uiUrl != null) {
        canvas.navigate(a2uiUrl)
        canvas.markCanvasActivity()
      }
    } else {
      // Show screensaver - reset canvas activity
      canvas.resetCanvasActivity()
    }
  }

  fun toggleVoiceWakeForChat() {
    val currentMode = voiceWakeMode.value
    val newMode = when (currentMode) {
      VoiceWakeMode.Off -> VoiceWakeMode.Foreground
      VoiceWakeMode.Foreground -> VoiceWakeMode.Off
      VoiceWakeMode.Always -> VoiceWakeMode.Off
    }
    setVoiceWakeMode(newMode)
  }

  // TV-specific: Crab controls
  fun setCrabVisible(value: Boolean) {
    prefs.setCrabVisible(value)
  }

  fun setCrabSize(value: Int) {
    prefs.setCrabSize(value)
  }

  fun setFloatingCrabEnabled(value: Boolean) {
    prefs.setFloatingCrabEnabled(value)
  }

  fun setCrabEmotion(emotion: CrabEmotion) {
    _crabEmotion.value = emotion
    FloatingCrabService.instance?.setEmotion(emotion)
  }

  fun resetCloseChat() {
    _shouldCloseChat.value = false
  }

  fun sendChatMessage(text: String, thinking: String = "low") {
    chatController.sendMessage(text, thinking)
  }

  fun abortChat() {
    chatController.abortCurrentRun()
  }

  fun clearChat() {
    chatController.clearMessages()
  }

  fun loadChatHistory() {
    chatController.loadHistory()
  }

  fun setChatSessionKey(key: String) {
    chatController.setSessionKey(key)
  }

  private fun buildInvokeCommands(): List<String> = buildList {
    add(OpenClawCanvasCommand.Present.rawValue)
    add(OpenClawCanvasCommand.Hide.rawValue)
    add(OpenClawCanvasCommand.Navigate.rawValue)
    add(OpenClawCanvasCommand.Eval.rawValue)
    add(OpenClawCanvasCommand.Snapshot.rawValue)
    add(OpenClawCanvasA2UICommand.Push.rawValue)
    add(OpenClawCanvasA2UICommand.PushJSONL.rawValue)
    add(OpenClawCanvasA2UICommand.Reset.rawValue)
    add(OpenClawScreenCommand.Record.rawValue)
    add(OpenClawScreenCommand.Snapshot.rawValue)
    add(OpenClawScreenCommand.RequestPermission.rawValue)
    // TV-specific: Crab commands
    add(OpenClawCrabCommand.Notify.rawValue)
    add(OpenClawCrabCommand.Clear.rawValue)
    // TV-specific: Media playback commands
    add(OpenClawMediaCommand.Play.rawValue)
    android.util.Log.d("TvNodeRuntime", "buildInvokeCommands: final list = ${this.toList()}")
  }

  private fun buildCapabilities(): List<String> = buildList {
    add(OpenClawCapability.Canvas.rawValue)
    add(OpenClawCapability.Screen.rawValue)
    add(OpenClawCapability.Crab.rawValue)
    add(OpenClawCapability.Media.rawValue)
    if (voiceWakeMode.value != VoiceWakeMode.Off && hasRecordAudioPermission()) {
      add(OpenClawCapability.VoiceWake.rawValue)
    }
  }

  private fun resolvedVersionName(): String {
    val versionName = BuildConfig.VERSION_NAME.trim().ifEmpty { "dev" }
    return if (BuildConfig.DEBUG && !versionName.contains("dev", ignoreCase = true)) {
      "$versionName-dev"
    } else {
      versionName
    }
  }

  private fun resolveModelIdentifier(): String? {
    return listOfNotNull(Build.MANUFACTURER, Build.MODEL)
      .joinToString(" ")
      .trim()
      .ifEmpty { null }
  }

  private fun buildUserAgent(): String {
    val version = resolvedVersionName()
    val release = Build.VERSION.RELEASE?.trim().orEmpty()
    val releaseLabel = if (release.isEmpty()) "unknown" else release
    return "OpenClawAndroid/$version (Android $releaseLabel; SDK ${Build.VERSION.SDK_INT})"
  }

  private fun buildClientInfo(clientId: String, clientMode: String): GatewayClientInfo {
    return GatewayClientInfo(
      id = clientId,
      displayName = displayName.value,
      version = resolvedVersionName(),
      platform = "android-tv",
      mode = clientMode,
      instanceId = instanceId.value,
      deviceFamily = "Android TV",
      modelIdentifier = resolveModelIdentifier(),
    )
  }

  private fun buildNodeConnectOptions(): GatewayConnectOptions {
    val commands = buildInvokeCommands()
    val caps = buildCapabilities()
    android.util.Log.d("TvNodeRuntime", "buildNodeConnectOptions: caps=$caps, commands=$commands")
    return GatewayConnectOptions(
      role = "node",
      scopes = emptyList(),
      caps = caps,
      commands = commands,
      permissions = emptyMap(),
      client = buildClientInfo(clientId = "cli", clientMode = "node"),
      userAgent = buildUserAgent(),
    )
  }

  private fun buildOperatorConnectOptions(): GatewayConnectOptions {
    return GatewayConnectOptions(
      role = "operator",
      scopes = emptyList(),
      caps = emptyList(),
      commands = emptyList(),
      permissions = emptyMap(),
      client = buildClientInfo(clientId = "cli", clientMode = "ui"),
      userAgent = buildUserAgent(),
    )
  }

  fun refreshGatewayConnection() {
    val endpoint = connectedEndpoint ?: return
    val token = prefs.loadGatewayToken()
    val password = prefs.loadGatewayPassword()
    val tls = resolveTlsParams(endpoint)
    operatorSession.connect(endpoint, token, password, buildOperatorConnectOptions(), tls)
    nodeSession.connect(endpoint, token, password, buildNodeConnectOptions(), tls)
    operatorSession.reconnect()
    nodeSession.reconnect()
  }

  fun connect(endpoint: GatewayEndpoint) {
    connectedEndpoint = endpoint
    operatorStatusText = "Connecting"
    nodeStatusText = "Connecting"
    updateStatus()
    val token = prefs.loadGatewayToken()
    val password = prefs.loadGatewayPassword()
    val tls = resolveTlsParams(endpoint)
    operatorSession.connect(endpoint, token, password, buildOperatorConnectOptions(), tls)
    nodeSession.connect(endpoint, token, password, buildNodeConnectOptions(), tls)
  }

  private fun hasRecordAudioPermission(): Boolean {
    return ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
  }

  fun connectManual() {
    val host = manualHost.value.trim()
    val port = manualPort.value
    if (host.isEmpty() || port <= 0 || port > 65535) {
      _statusText.value = "Failed: invalid manual host/port"
      return
    }
    connect(GatewayEndpoint.manual(host = host, port = port))
  }

  fun disconnect() {
    connectedEndpoint = null
    operatorSession.disconnect()
    nodeSession.disconnect()
  }

  private fun resolveTlsParams(endpoint: GatewayEndpoint): GatewayTlsParams? {
    val stored = prefs.loadGatewayTlsFingerprint(endpoint.stableId)
    val hinted = endpoint.tlsEnabled || !endpoint.tlsFingerprintSha256.isNullOrBlank()
    val manual = endpoint.stableId.startsWith("manual|")

    if (manual) {
      if (!manualTls.value) return null
      return GatewayTlsParams(
        required = true,
        expectedFingerprint = endpoint.tlsFingerprintSha256 ?: stored,
        allowTOFU = stored == null,
        stableId = endpoint.stableId,
      )
    }

    if (hinted) {
      return GatewayTlsParams(
        required = true,
        expectedFingerprint = endpoint.tlsFingerprintSha256 ?: stored,
        allowTOFU = stored == null,
        stableId = endpoint.stableId,
      )
    }

    if (!stored.isNullOrBlank()) {
      return GatewayTlsParams(
        required = true,
        expectedFingerprint = stored,
        allowTOFU = false,
        stableId = endpoint.stableId,
      )
    }

    return null
  }

  private fun handleGatewayEvent(event: String, payloadJson: String?) {
    if (event == "voicewake.changed") {
      if (payloadJson.isNullOrBlank()) return
      try {
        val payload = json.parseToJsonElement(payloadJson).asObjectOrNull() ?: return
        val array = payload["triggers"] as? JsonArray ?: return
        val triggers = array.mapNotNull { it.asStringOrNull() }
        applyWakeWordsFromGateway(triggers)
      } catch (_: Throwable) {
        // ignore
      }
      return
    }

    chatController.handleGatewayEvent(event, payloadJson)
  }

  private fun applyWakeWordsFromGateway(words: List<String>) {
    suppressWakeWordsSync = true
    prefs.setWakeWords(words)
    suppressWakeWordsSync = false
  }

  private fun scheduleWakeWordsSyncIfNeeded() {
    if (suppressWakeWordsSync) return
    if (!_isConnected.value) return

    val snapshot = prefs.wakeWords.value
    wakeWordsSyncJob?.cancel()
    wakeWordsSyncJob = scope.launch {
      delay(650)
      val jsonList = snapshot.joinToString(separator = ",") { it.toJsonString() }
      val params = """{"triggers":[$jsonList]}"""
      try {
        operatorSession.request("voicewake.set", params)
      } catch (_: Throwable) {
        // ignore
      }
    }
  }

  private suspend fun refreshWakeWordsFromGateway() {
    if (!_isConnected.value) return
    try {
      val res = operatorSession.request("voicewake.get", "{}")
      val payload = json.parseToJsonElement(res).asObjectOrNull() ?: return
      val array = payload["triggers"] as? JsonArray ?: return
      val triggers = array.mapNotNull { it.asStringOrNull() }
      applyWakeWordsFromGateway(triggers)
    } catch (_: Throwable) {
      // ignore
    }
  }

  private fun resolveA2uiHostUrl(): String? {
    val nodeRaw = nodeSession.currentCanvasHostUrl()?.trim().orEmpty()
    val operatorRaw = operatorSession.currentCanvasHostUrl()?.trim().orEmpty()
    val raw = if (nodeRaw.isNotBlank()) nodeRaw else operatorRaw
    if (raw.isBlank()) return null
    val base = raw.trimEnd('/')
    return "${base}/__openclaw__/a2ui/?platform=android-tv"
  }

  private suspend fun handleInvoke(command: String, paramsJson: String?): GatewaySession.InvokeResult {
    // Check foreground for canvas commands and most screen commands
    // BUT allow screen.snapshot to work in background
    if (command.startsWith(OpenClawCanvasCommand.NamespacePrefix) ||
        command.startsWith(OpenClawCanvasA2UICommand.NamespacePrefix) ||
        (command.startsWith(OpenClawScreenCommand.NamespacePrefix) && 
         command != OpenClawScreenCommand.Snapshot.rawValue)) {
      if (!isForeground.value) {
        return GatewaySession.InvokeResult.error(
          code = "NODE_BACKGROUND_UNAVAILABLE",
          message = "NODE_BACKGROUND_UNAVAILABLE: canvas/screen commands require foreground",
        )
      }
    }

    return when (command) {
      OpenClawCanvasCommand.Present.rawValue -> {
        android.util.Log.d("OpenClawCanvas", "canvas.present command received")
        val url = CanvasController.parseNavigateUrl(paramsJson)
        canvas.markCanvasActivity() // Hide screensaver when content is presented
        canvas.navigate(url)
        GatewaySession.InvokeResult.ok(null)
      }
      OpenClawCanvasCommand.Hide.rawValue -> GatewaySession.InvokeResult.ok(null)
      OpenClawCanvasCommand.Navigate.rawValue -> {
        android.util.Log.d("OpenClawCanvas", "canvas.navigate command received")
        val url = CanvasController.parseNavigateUrl(paramsJson)
        canvas.navigate(url)
        GatewaySession.InvokeResult.ok(null)
      }
      OpenClawCanvasCommand.Eval.rawValue -> {
        android.util.Log.d("OpenClawCanvas", "canvas.eval command received")
        val js = CanvasController.parseEvalJs(paramsJson)
          ?: return GatewaySession.InvokeResult.error(
            code = "INVALID_REQUEST",
            message = "INVALID_REQUEST: javaScript required",
          )
        canvas.markCanvasActivity() // Hide screensaver when JS is evaluated
        val result = try {
          canvas.eval(js)
        } catch (err: Throwable) {
          return GatewaySession.InvokeResult.error(
            code = "NODE_BACKGROUND_UNAVAILABLE",
            message = "NODE_BACKGROUND_UNAVAILABLE: canvas unavailable",
          )
        }
        GatewaySession.InvokeResult.ok("""{"result":${result.toJsonString()}}""")
      }
      OpenClawCanvasCommand.Snapshot.rawValue -> {
        val snapshotParams = CanvasController.parseSnapshotParams(paramsJson)
        val base64 = try {
          canvas.snapshotBase64(
            format = snapshotParams.format,
            quality = snapshotParams.quality,
            maxWidth = snapshotParams.maxWidth,
          )
        } catch (err: Throwable) {
          return GatewaySession.InvokeResult.error(
            code = "NODE_BACKGROUND_UNAVAILABLE",
            message = "NODE_BACKGROUND_UNAVAILABLE: canvas unavailable",
          )
        }
        GatewaySession.InvokeResult.ok("""{"format":"${snapshotParams.format.rawValue}","base64":"$base64"}""")
      }
      OpenClawCanvasA2UICommand.Reset.rawValue -> {
        val a2uiUrl = resolveA2uiHostUrl()
          ?: return GatewaySession.InvokeResult.error(
            code = "A2UI_HOST_NOT_CONFIGURED",
            message = "A2UI_HOST_NOT_CONFIGURED: gateway did not advertise canvas host",
          )
        val ready = ensureA2uiReady(a2uiUrl)
        if (!ready) {
          return GatewaySession.InvokeResult.error(
            code = "A2UI_HOST_UNAVAILABLE",
            message = "A2UI host not reachable",
          )
        }
        canvas.markCanvasActivity() // Hide screensaver when canvas is reset
        val res = canvas.eval(a2uiResetJS)
        GatewaySession.InvokeResult.ok(res)
      }
      OpenClawCanvasA2UICommand.Push.rawValue, OpenClawCanvasA2UICommand.PushJSONL.rawValue -> {
        android.util.Log.d("OpenClawCanvas", "A2UI push command received: $command")
        val messages = try {
          decodeA2uiMessages(command, paramsJson)
        } catch (err: Throwable) {
          return GatewaySession.InvokeResult.error(code = "INVALID_REQUEST", message = err.message ?: "invalid A2UI payload")
        }
        val a2uiUrl = resolveA2uiHostUrl()
          ?: return GatewaySession.InvokeResult.error(
            code = "A2UI_HOST_NOT_CONFIGURED",
            message = "A2UI_HOST_NOT_CONFIGURED: gateway did not advertise canvas host",
          )
        val ready = ensureA2uiReady(a2uiUrl)
        if (!ready) {
          return GatewaySession.InvokeResult.error(
            code = "A2UI_HOST_UNAVAILABLE",
            message = "A2UI host not reachable",
          )
        }
        canvas.markCanvasActivity() // Hide screensaver when content is pushed
        val js = a2uiApplyMessagesJS(messages)
        val res = canvas.eval(js)
        GatewaySession.InvokeResult.ok(res)
      }
      OpenClawScreenCommand.Record.rawValue -> {
        if (!isForeground.value) {
          return@handleInvoke GatewaySession.InvokeResult.error(
            code = "NODE_BACKGROUND_UNAVAILABLE",
            message = "NODE_BACKGROUND_UNAVAILABLE: screen recording requires foreground",
          )
        }
        _screenRecordActive.value = true
        try {
          val payload = screenRecorder.record(paramsJson)
          GatewaySession.InvokeResult.ok(payload.payloadJson)
        } finally {
          _screenRecordActive.value = false
        }
      }
      OpenClawScreenCommand.Snapshot.rawValue -> {
        // Screen snapshot works in background too
        try {
          val payload = screenRecorder.snapshot(paramsJson)
          GatewaySession.InvokeResult.ok(payload.payloadJson)
        } catch (e: Exception) {
          android.util.Log.e("TvNodeRuntime", "Screen snapshot failed", e)
          GatewaySession.InvokeResult.error(
            code = "SNAPSHOT_FAILED",
            message = "SNAPSHOT_FAILED: ${e.message}"
          )
        }
      }
      OpenClawScreenCommand.RequestPermission.rawValue -> {
        // Open the app foreground to trigger permission dialog
        val intent = Intent(appContext, ai.openclaw.tv.MainActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
          putExtra("REQUEST_SCREEN_PERMISSION", true)
        }
        appContext.startActivity(intent)
        GatewaySession.InvokeResult.ok("""{"action":"opened_app","message":"Please accept screen capture permission on TV"}""")
      }
      // TV-specific: Crab commands
      OpenClawCrabCommand.Notify.rawValue -> {
        val message = parseCrabMessage(paramsJson)
        val emotion = parseCrabEmotion(paramsJson)
        _crabEmotion.value = emotion
        if (emotion == CrabEmotion.ATTENTION) {
          FloatingCrabService.notifyAttention(appContext, message)
        } else {
          FloatingCrabService.instance?.setEmotion(emotion)
        }
        GatewaySession.InvokeResult.ok("""{"notified":true, "emotion":"${emotion.name}"}""")
      }
      OpenClawCrabCommand.Clear.rawValue -> {
        _crabEmotion.value = CrabEmotion.SLEEPING
        FloatingCrabService.clearAttention()
        GatewaySession.InvokeResult.ok("""{"cleared":true}""")
      }
      // TV-specific: Media playback commands
      OpenClawMediaCommand.Play.rawValue -> {
        val result = launchMediaApp(paramsJson)
        result
      }
      else -> GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: unknown command",
      )
    }
  }

  private fun invokeErrorFromThrowable(err: Throwable): Pair<String, String> {
    val raw = (err.message ?: "").trim()
    if (raw.isEmpty()) return "UNAVAILABLE" to "UNAVAILABLE: screen error"

    val idx = raw.indexOf(':')
    if (idx <= 0) return "UNAVAILABLE" to raw
    val code = raw.substring(0, idx).trim().ifEmpty { "UNAVAILABLE" }
    val message = raw.substring(idx + 1).trim().ifEmpty { raw }
    return code to "$code: $message"
  }

  private fun parseCrabMessage(paramsJson: String?): String? {
    if (paramsJson.isNullOrBlank()) return null
    return try {
      val obj = json.parseToJsonElement(paramsJson).asObjectOrNull()
      obj?.get("message")?.asStringOrNull()
    } catch (_: Throwable) {
      null
    }
  }

  private fun parseCrabEmotion(paramsJson: String?): CrabEmotion {
    if (paramsJson.isNullOrBlank()) return CrabEmotion.ATTENTION
    return try {
      val obj = json.parseToJsonElement(paramsJson).asObjectOrNull()
      val emotionStr = obj?.get("emotion")?.asStringOrNull() ?: "attention"
      CrabEmotion.values().find { it.name.equals(emotionStr, ignoreCase = true) } ?: CrabEmotion.ATTENTION
    } catch (_: Throwable) {
      CrabEmotion.ATTENTION
    }
  }

  private fun launchMediaApp(paramsJson: String?): GatewaySession.InvokeResult {
    if (paramsJson.isNullOrBlank()) {
      return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: params required with 'app' field (youtube|netflix|disney)"
      )
    }
    
    return try {
      val obj = json.parseToJsonElement(paramsJson).asObjectOrNull()
      val app = obj?.get("app")?.asStringOrNull()?.lowercase()
      // Support both "id" (video ID) and "url" (full URL) parameters
      val videoId = obj?.get("id")?.asStringOrNull()
      val videoUrl = obj?.get("url")?.asStringOrNull()
      
      // Extract video ID from URL if provided
      val finalVideoId = videoId ?: extractVideoIdFromUrl(videoUrl, app)
      
      if (app.isNullOrBlank()) {
        return GatewaySession.InvokeResult.error(
          code = "INVALID_REQUEST", 
          message = "INVALID_REQUEST: 'app' field required (youtube|netflix|disney)"
        )
      }
      
      val intent = when (app) {
        "youtube" -> {
          if (finalVideoId.isNullOrBlank()) {
            // Just open YouTube app
            Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube.launch://"))
          } else {
            // Open specific video
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$finalVideoId"))
          }
        }
        "netflix" -> {
          if (finalVideoId.isNullOrBlank()) {
            // Just open Netflix app
            Intent(Intent.ACTION_VIEW, Uri.parse("nflx://"))
          } else {
            // Open specific title - try multiple formats
            Intent(Intent.ACTION_VIEW, Uri.parse("http://www.netflix.com/title/$finalVideoId"))
          }
        }
        "disney", "disneyplus", "disney+" -> {
          if (finalVideoId.isNullOrBlank()) {
            // Just open Disney+ app  
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.disneyplus.com"))
          } else {
            // Open specific video
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.disneyplus.com/video/$finalVideoId"))
          }
        }
        else -> {
          return GatewaySession.InvokeResult.error(
            code = "INVALID_REQUEST",
            message = "INVALID_REQUEST: unsupported app '$app'. Supported: youtube, netflix, disney"
          )
        }
      }
      
      // Add flag to start as new task since we're launching from service/context
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      
      // Check if the app can handle this intent
      val packageManager = appContext.packageManager
      val resolveInfo = intent.resolveActivity(packageManager)
      
      if (resolveInfo != null) {
        appContext.startActivity(intent)
        val response = if (finalVideoId.isNullOrBlank()) {
          """{"opened":true, "app":"$app"}"""
        } else {
          """{"opened":true, "app":"$app", "videoId":"$finalVideoId"}"""
        }
        GatewaySession.InvokeResult.ok(response)
      } else {
        // App not installed, try to open in browser as fallback
        val webIntent = when (app) {
          "youtube" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com${if (finalVideoId != null) "/watch?v=$finalVideoId" else ""}"))
          "netflix" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.netflix.com${if (finalVideoId != null) "/title/$finalVideoId" else ""}"))
          else -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.disneyplus.com${if (finalVideoId != null) "/video/$finalVideoId" else ""}"))
        }
        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(webIntent)
        GatewaySession.InvokeResult.ok("""{"opened":true, "app":"$app", "fallback":"browser"}""")
      }
    } catch (e: Exception) {
      android.util.Log.e("TvNodeRuntime", "Failed to launch media app", e)
      GatewaySession.InvokeResult.error(
        code = "LAUNCH_FAILED",
        message = "LAUNCH_FAILED: ${e.message}"
      )
    }
  }

  /**
   * Extract video ID from a URL if provided.
   * Supports YouTube URLs (youtube.com/watch?v=..., youtu.be/...)
   * Netflix URLs (netflix.com/title/...)
   * Disney+ URLs (disneyplus.com/video/...)
   */
  private fun extractVideoIdFromUrl(url: String?, app: String?): String? {
    if (url.isNullOrBlank()) return null
    
    return try {
      val uri = Uri.parse(url)
      when (app?.lowercase()) {
        "youtube" -> {
          // YouTube: youtube.com/watch?v=VIDEO_ID or youtu.be/VIDEO_ID
          when {
            uri.host?.contains("youtu.be") == true -> {
              // Short URL: youtu.be/VIDEO_ID
              uri.lastPathSegment
            }
            uri.host?.contains("youtube.com") == true -> {
              // Standard URL: youtube.com/watch?v=VIDEO_ID
              uri.getQueryParameter("v")
            }
            else -> null
          }
        }
        "netflix" -> {
          // Netflix: netflix.com/title/TITLE_ID
          if (uri.pathSegments.size >= 2 && uri.pathSegments[0] == "title") {
            uri.pathSegments[1]
          } else {
            null
          }
        }
        "disney", "disneyplus", "disney+" -> {
          // Disney+: disneyplus.com/video/VIDEO_ID
          if (uri.pathSegments.size >= 2 && uri.pathSegments[0] == "video") {
            uri.pathSegments[1]
          } else {
            null
          }
        }
        else -> null
      }
    } catch (_: Exception) {
      null
    }
  }

  private suspend fun ensureA2uiReady(a2uiUrl: String): Boolean {
    try {
      val already = canvas.eval(a2uiReadyCheckJS)
      if (already == "true") return true
    } catch (_: Throwable) {
      // ignore
    }

    canvas.navigate(a2uiUrl)
    repeat(50) {
      try {
        val ready = canvas.eval(a2uiReadyCheckJS)
        if (ready == "true") return true
      } catch (_: Throwable) {
        // ignore
      }
      delay(120)
    }
    return false
  }

  private fun decodeA2uiMessages(command: String, paramsJson: String?): String {
    val raw = paramsJson?.trim().orEmpty()
    if (raw.isBlank()) throw IllegalArgumentException("INVALID_REQUEST: paramsJSON required")

    val obj = json.parseToJsonElement(raw) as? JsonObject
      ?: throw IllegalArgumentException("INVALID_REQUEST: expected object params")

    val jsonlField = (obj["jsonl"] as? JsonPrimitive)?.content?.trim().orEmpty()
    val hasMessagesArray = obj["messages"] is JsonArray

    if (command == OpenClawCanvasA2UICommand.PushJSONL.rawValue || (!hasMessagesArray && jsonlField.isNotBlank())) {
      val jsonl = jsonlField
      if (jsonl.isBlank()) throw IllegalArgumentException("INVALID_REQUEST: jsonl required")
      val messages = jsonl.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapIndexed { idx, line ->
          val el = json.parseToJsonElement(line)
          val msg = el as? JsonObject
            ?: throw IllegalArgumentException("A2UI JSONL line ${idx + 1}: expected a JSON object")
          validateA2uiV0_8(msg, idx + 1)
          msg
        }
        .toList()
      return JsonArray(messages).toString()
    }

    val arr = obj["messages"] as? JsonArray ?: throw IllegalArgumentException("INVALID_REQUEST: messages[] required")
    val out = arr.mapIndexed { idx, el ->
      val msg = el as? JsonObject
        ?: throw IllegalArgumentException("A2UI messages[${idx}]: expected a JSON object")
      validateA2uiV0_8(msg, idx + 1)
      msg
    }
    return JsonArray(out).toString()
  }

  private fun validateA2uiV0_8(msg: JsonObject, lineNumber: Int) {
    if (msg.containsKey("createSurface")) {
      throw IllegalArgumentException(
        "A2UI JSONL line $lineNumber: looks like A2UI v0.9 (createSurface). Canvas supports v0.8 messages only.",
      )
    }
    val allowed = setOf("beginRendering", "surfaceUpdate", "dataModelUpdate", "deleteSurface")
    val matched = msg.keys.filter { allowed.contains(it) }
    if (matched.size != 1) {
      val found = msg.keys.sorted().joinToString(", ")
      throw IllegalArgumentException(
        "A2UI JSONL line $lineNumber: expected exactly one of ${allowed.sorted().joinToString(", ")}; found: $found",
      )
    }
  }

  private fun isCanonicalMainSessionKey(key: String): Boolean {
    return key == "main" || key == "default"
  }
}

// Crab emotion enum for the floating mascot
enum class CrabEmotion {
  SLEEPING,
  LISTENING,
  THINKING,
  TALKING,
  EXCITED,
  CELEBRATING,
  SUCCESS,
  ERROR,
  ATTENTION,
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private const val a2uiReadyCheckJS: String = """
  (() => {
    try {
      const host = globalThis.openclawA2UI;
      return !!host && typeof host.applyMessages === 'function';
    } catch (_) {
      return false;
    }
  })()
  """

private const val a2uiResetJS: String = """
  (() => {
    try {
      const host = globalThis.openclawA2UI;
      if (!host) return { ok: false, error: "missing openclawA2UI" };
      return host.reset();
    } catch (e) {
      return { ok: false, error: String(e?.message ?? e) };
    }
  })()
  """

private fun a2uiApplyMessagesJS(messagesJson: String): String {
  return """
    (() => {
      try {
        const host = globalThis.openclawA2UI;
        if (!host) return { ok: false, error: "missing openclawA2UI" };
        const messages = $messagesJson;
        return host.applyMessages(messages);
      } catch (e) {
        return { ok: false, error: String(e?.message ?? e) };
      }
    })()
  """.trimIndent()
}

private fun String.toJsonString(): String {
  val escaped = this.replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
  return "\"$escaped\""
}

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asStringOrNull(): String? =
  when (this) {
    is JsonNull -> null
    is JsonPrimitive -> content
    else -> null
  }
