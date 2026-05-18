package com.smartscreenshot.domain.model

enum class Category(val label: String) {
  SHOPPING("Shopping"),
  RECEIPTS("Receipts"),
  CHAT("Chat"),
  SOCIAL_MEDIA("Social Media"),
  BANKING("Banking"),
  TRAVEL("Travel"),
  WORK("Work"),
  CODING("Coding"),
  MEMES("Memes"),
  DOCUMENTS("Documents"),
  OTHER("Other");

  companion object {
    /** Lenient parse: matches enum name or label, case-insensitively. Falls back to OTHER. */
    fun fromString(value: String?): Category {
      if (value.isNullOrBlank()) return OTHER
      val normalized = value.trim()
      return entries.firstOrNull {
        it.name.equals(normalized, ignoreCase = true) ||
          it.label.equals(normalized, ignoreCase = true)
      } ?: OTHER
    }
  }
}
