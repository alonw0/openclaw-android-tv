package ai.openclaw.tv

object WakeWords {
  fun sanitize(words: List<String>, defaults: List<String>): List<String> {
    val sanitized = words
      .map { it.trim().lowercase() }
      .filter { it.isNotEmpty() && it.length <= 32 }
      .distinct()
      .take(5)
    return if (sanitized.isEmpty()) defaults else sanitized
  }
}
