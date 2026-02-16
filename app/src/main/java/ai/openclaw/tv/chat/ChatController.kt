package ai.openclaw.tv.chat

import ai.openclaw.tv.gateway.GatewaySession
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class ChatController(
  private val scope: CoroutineScope,
  private val operatorSession: GatewaySession,
  private val nodeSession: GatewaySession,
) {
  private val json = Json { ignoreUnknownKeys = true }
  
  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
  
  private val _sessionKey = MutableStateFlow("main")
  val sessionKey: StateFlow<String> = _sessionKey.asStateFlow()
  
  private val _isSending = MutableStateFlow(false)
  val isSending: StateFlow<Boolean> = _isSending.asStateFlow()
  
  private val _streamingText = MutableStateFlow<String?>(null)
  val streamingText: StateFlow<String?> = _streamingText.asStateFlow()
  
  private val _pendingToolCalls = MutableStateFlow<List<ChatPendingToolCall>>(emptyList())
  val pendingToolCalls: StateFlow<List<ChatPendingToolCall>> = _pendingToolCalls.asStateFlow()
  
  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
  
  private var currentRunId: String? = null
  
  fun setSessionKey(key: String) {
    _sessionKey.value = key
    loadHistory()
  }
  
  fun applyMainSessionKey(key: String?) {
    if (key != null && _sessionKey.value == "main") {
      _sessionKey.value = key
      loadHistory()
    }
  }
  
  fun sendMessage(text: String, thinking: String = "low") {
    if (text.isBlank()) return
    
    val idempotencyKey = UUID.randomUUID().toString()
    val timestamp = System.currentTimeMillis()
    
    // Add user message optimistically
    val userMessage = ChatMessage(
      id = UUID.randomUUID().toString(),
      role = "user",
      content = listOf(ChatMessageContent(type = "text", text = text)),
      timestampMs = timestamp,
    )
    _messages.value = _messages.value + userMessage
    _isSending.value = true
    _errorMessage.value = null
    
    scope.launch {
      try {
        val params = buildJsonObject {
          put("sessionKey", _sessionKey.value)
          put("message", text)
          put("thinking", thinking)
          put("idempotencyKey", idempotencyKey)
        }
        
        val result = operatorSession.request("chat.send", params.toString(), timeoutMs = 30_000)
        Log.d("ChatController", "chat.send response: $result")
        
        // Parse response to get runId
        try {
          val responseObj = json.parseToJsonElement(result).jsonObject
          val runId = responseObj["runId"]?.toString()?.trim('"')
          if (runId != null) {
            Log.d("ChatController", "Started run: $runId")
          }
        } catch (e: Exception) {
          Log.w("ChatController", "Could not parse runId from response", e)
        }
      } catch (e: Exception) {
        Log.e("ChatController", "Failed to send message", e)
        _errorMessage.value = "Failed to send: ${e.message}"
        _isSending.value = false
      }
    }
  }
  
  fun abortCurrentRun() {
    val runId = currentRunId ?: return
    scope.launch {
      try {
        val params = buildJsonObject {
          put("runId", runId)
        }
        operatorSession.request("chat.abort", params.toString(), timeoutMs = 5_000)
      } catch (e: Exception) {
        Log.e("ChatController", "Failed to abort run", e)
      }
    }
  }
  
  fun loadHistory() {
    scope.launch {
      try {
        val params = buildJsonObject {
          put("sessionKey", _sessionKey.value)
          put("limit", 50)
        }
        val result = operatorSession.request("chat.history", params.toString(), timeoutMs = 10_000)
        val history = ChatModels.deserializeHistory(result)
        if (history != null) {
          _messages.value = history.messages
        }
      } catch (e: Exception) {
        Log.e("ChatController", "Failed to load history", e)
      }
    }
  }
  
  fun clearMessages() {
    _messages.value = emptyList()
    _streamingText.value = null
    _pendingToolCalls.value = emptyList()
    currentRunId = null
  }
  
  fun handleGatewayEvent(event: String, payloadJson: String?) {
    when (event) {
      "chat" -> handleChatEvent(payloadJson)
      "agent" -> handleAgentEvent(payloadJson)
      else -> { /* Ignore */ }
    }
  }
  
  private fun handleChatEvent(payloadJson: String?) {
    if (payloadJson.isNullOrBlank()) return
    
    Log.d("ChatController", "Chat event received: $payloadJson")
    
    try {
      val obj = json.parseToJsonElement(payloadJson).jsonObject
      val runId = obj["runId"]?.toString()?.trim('"')
      val status = obj["status"]?.toString()?.trim('"')
      val state = obj["state"]?.toString()?.trim('"')
      
      // Handle both 'status' and 'state' fields
      when (status ?: state) {
        "started" -> {
          currentRunId = runId
          _isSending.value = true
        }
        "completed", "final" -> {
          currentRunId = null
          _isSending.value = false
          _streamingText.value = null
          _pendingToolCalls.value = emptyList()
          // Extract message from chat event and add to list
          val messageObj = obj["message"]?.jsonObject
          if (messageObj != null) {
            val message = try {
              json.decodeFromJsonElement(ChatMessage.serializer(), messageObj)
            } catch (e: Exception) {
              Log.e("ChatController", "Failed to parse message from chat event", e)
              null
            }
            // Debug logging to see what message we received
            Log.d("ChatController", "Final message parsed: id=${message?.id}, role=${message?.role}, content.size=${message?.content?.size}")
            message?.content?.forEachIndexed { idx, content ->
              Log.d("ChatController", "  Content[$idx]: type=${content.type}, text=${content.text?.take(50)}")
            }
            
            // Add message to list if it has any content (even if text is temporarily empty during streaming)
            if (message != null && message.content.isNotEmpty()) {
              // Add or update message in list
              // Only treat as update if message has a valid non-empty ID and matches existing
              val existingIndex = if (message.id.isNotBlank()) {
                _messages.value.indexOfFirst { it.id == message.id }
              } else {
                -1  // No ID means it's a new message, don't update existing
              }
              if (existingIndex >= 0) {
                val updated = _messages.value.toMutableList()
                updated[existingIndex] = message
                _messages.value = updated
                Log.d("ChatController", "Updated existing message at index $existingIndex, id=${message.id}")
              } else {
                _messages.value = _messages.value + message
                Log.d("ChatController", "Added new message to list, total: ${_messages.value.size}, id='${message.id}'")
              }
            } else {
              Log.w("ChatController", "Message filtered out: null=${message == null}, emptyContent=${message?.content?.isEmpty()}")
            }
          }
        }
        "aborted", "error" -> {
          currentRunId = null
          _isSending.value = false
          _streamingText.value = null
          _pendingToolCalls.value = emptyList()
        }
      }
    } catch (e: Exception) {
      Log.e("ChatController", "Error handling chat event", e)
    }
  }
  
  private fun handleAgentEvent(payloadJson: String?) {
    if (payloadJson.isNullOrBlank()) return
    
    Log.d("ChatController", "Agent event received: $payloadJson")
    
    try {
      val obj = json.parseToJsonElement(payloadJson).jsonObject
      val type = obj["type"]?.toString()?.trim('"')
      val stream = obj["stream"]?.toString()?.trim('"')
      
      // Handle both old format (type) and new format (stream)
      when (type ?: stream) {
        "assistant.text", "assistant" -> {
          val data = obj["data"]?.jsonObject
          val text = data?.get("text")?.toString()?.trim('"')
            ?: obj["text"]?.toString()?.trim('"')
          if (text != null) {
            _streamingText.value = text
          }
        }
        "tool.start" -> {
          val data = obj["data"]?.jsonObject
          val toolId = data?.get("toolCallId")?.toString()?.trim('"')
            ?: obj["toolCallId"]?.toString()?.trim('"')
            ?: UUID.randomUUID().toString()
          val tool = data?.get("tool")?.toString()?.trim('"')
            ?: obj["tool"]?.toString()?.trim('"')
            ?: "unknown"
          val label = data?.get("label")?.toString()?.trim('"')
            ?: obj["label"]?.toString()?.trim('"')
            ?: tool
          
          val toolCall = ChatPendingToolCall(
            id = toolId,
            tool = tool,
            label = label,
            status = "running"
          )
          _pendingToolCalls.value = _pendingToolCalls.value + toolCall
        }
        "tool.end" -> {
          val data = obj["data"]?.jsonObject
          val toolId = data?.get("toolCallId")?.toString()?.trim('"')
            ?: obj["toolCallId"]?.toString()?.trim('"')
          if (toolId != null) {
            _pendingToolCalls.value = _pendingToolCalls.value.filter { it.id != toolId }
          }
        }
        "run.end", "lifecycle" -> {
          val data = obj["data"]?.jsonObject
          val phase = data?.get("phase")?.toString()?.trim('"')
          if (phase == "end" || type == "run.end") {
            _isSending.value = false
            _streamingText.value = null
            _pendingToolCalls.value = emptyList()
          }
        }
      }
    } catch (e: Exception) {
      Log.e("ChatController", "Error handling agent event", e)
    }
  }
}
