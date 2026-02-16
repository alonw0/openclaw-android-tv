package ai.openclaw.tv.gateway

import android.util.Log

/**
 * Debug wrapper to trace "Not initialized" error
 */
object GatewayDebug {
  private const val TAG = "OpenClawGateway"
  
  fun logEnter(method: String, details: String = "") {
    Log.d(TAG, "ENTER: $method ${if (details.isNotEmpty()) "- $details" else ""}")
  }
  
  fun logExit(method: String, success: Boolean = true) {
    Log.d(TAG, "EXIT: $method (success=$success)")
  }
  
  fun logError(method: String, error: Throwable) {
    Log.e(TAG, "ERROR in $method: ${error.message}", error)
  }
  
  fun logState(state: String) {
    Log.d(TAG, "STATE: $state")
  }
}
