package com.smartscreenshot.domain

import com.smartscreenshot.domain.model.Category

object PromptTemplates {

  val SYSTEM: String =
    "You analyze a phone screenshot's OCR text. " +
      "Return ONLY one JSON object. No prose, no explanations, no markdown code fences."

  private val CATEGORIES = Category.entries.joinToString("|") { it.label }

  /** Builds the user prompt embedding the OCR text and the strict output schema. */
  fun userPrompt(ocrText: String): String {
    val clipped = ocrText.take(MAX_OCR_CHARS)
    return buildString {
      append("Screenshot OCR text:\n\"\"\"\n")
      append(clipped)
      append("\n\"\"\"\n\n")
      append("Return a JSON object with EXACTLY these keys:\n")
      append("{\n")
      append("  \"title\": string (<= 8 words),\n")
      append("  \"summary\": string (1-2 sentences),\n")
      append("  \"category\": one of [").append(CATEGORIES).append("],\n")
      append("  \"tags\": array of 3-6 short lowercase strings,\n")
      append("  \"detected_apps\": array of app or website names visible in the text,\n")
      append("  \"important_text\": array of key snippets (codes, amounts, names, dates),\n")
      append("  \"priority_score\": integer 0-10 (10 = highly important to keep)\n")
      append("}")
    }
  }

  /** Terse re-prompt used once when the first response failed to parse. */
  fun repairPrompt(): String =
    "Your previous answer was not valid JSON. Return ONLY the JSON object, " +
      "no markdown, no commentary."

  private const val MAX_OCR_CHARS = 6000
}
