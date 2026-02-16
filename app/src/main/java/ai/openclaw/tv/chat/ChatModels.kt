package ai.openclaw.tv.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatMessage(
  val id: String = "",  // Optional for messages from gateway events
  val role: String,  // "user" | "assistant" | "system"
  val content: List<ChatMessageContent>,
  @SerialName("timestamp") val timestampMs: Long? = null,
  val isStreaming: Boolean = false,
  val toolCalls: List<ChatPendingToolCall> = emptyList(),
)

@Serializable
data class ChatMessageContent(
  val type: String,  // "text" | "image"
  val text: String? = null,
  val mimeType: String? = null,
  val fileName: String? = null,
  val base64: String? = null,
)

@Serializable
data class ChatPendingToolCall(
  val id: String,
  val tool: String,
  val label: String,
  val status: String,  // "running" | "completed" | "error"
)

@Serializable
data class ChatSendParams(
  val sessionKey: String,
  val message: String,
  val thinking: String = "low",
  val attachments: List<ChatAttachment> = emptyList(),
  val idempotencyKey: String,
)

@Serializable
data class ChatAttachment(
  val mimeType: String,
  val fileName: String,
  val base64: String,
)

@Serializable
data class ChatHistoryResponse(
  val messages: List<ChatMessage> = emptyList(),
  val hasMore: Boolean = false,
)

@Serializable
data class ChatSession(
  val key: String,
  val name: String? = null,
  val lastActivityMs: Long? = null,
)

@Serializable
data class SessionsListResponse(
  val sessions: List<ChatSession> = emptyList(),
)

object ChatModels {
  val json = Json { 
    ignoreUnknownKeys = true 
    prettyPrint = false
  }
  
  fun serializeMessage(message: ChatMessage): String {
    return json.encodeToString(ChatMessage.serializer(), message)
  }
  
  fun deserializeMessage(jsonStr: String): ChatMessage? {
    return try {
      json.decodeFromString(ChatMessage.serializer(), jsonStr)
    } catch (_: Exception) {
      null
    }
  }
  
  fun deserializeHistory(jsonStr: String): ChatHistoryResponse? {
    return try {
      json.decodeFromString(ChatHistoryResponse.serializer(), jsonStr)
    } catch (e: Exception) {
      println("ChatModels: Failed to deserialize history: ${e.message}")
      println("ChatModels: JSON was: ${jsonStr.take(200)}")
      null
    }
  }
  
  fun deserializeSessions(jsonStr: String): SessionsListResponse? {
    return try {
      json.decodeFromString(SessionsListResponse.serializer(), jsonStr)
    } catch (_: Exception) {
      null
    }
  }
}
