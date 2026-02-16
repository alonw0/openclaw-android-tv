package ai.openclaw.tv

enum class VoiceWakeMode(val rawValue: String) {
  Off("off"),
  Foreground("foreground"),
  Always("always");

  companion object {
    fun fromRawValue(raw: String?): VoiceWakeMode {
      return when (raw?.lowercase()) {
        "off" -> Off
        "foreground" -> Foreground
        "always" -> Always
        else -> Off // Default to off for privacy - user must opt-in
      }
    }
  }
}
