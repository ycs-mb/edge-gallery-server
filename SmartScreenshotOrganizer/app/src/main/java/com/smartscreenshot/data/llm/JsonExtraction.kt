package com.smartscreenshot.data.llm

import com.smartscreenshot.domain.model.AnalysisResult
import com.smartscreenshot.domain.model.Category
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tolerant extraction of an [AnalysisResult] from raw LLM output. Neither inference
 * path guarantees structured output, so we strip markdown fences, isolate the first
 * balanced JSON object, and read fields defensively.
 */
object JsonExtraction {

  /** Returns null if no usable JSON object could be parsed. */
  fun parse(raw: String): AnalysisResult? {
    val jsonText = isolateJsonObject(raw) ?: return null
    val obj =
      try {
        JSONObject(jsonText)
      } catch (_: Throwable) {
        return null
      }

    val title = obj.optString("title").trim()
    val summary = obj.optString("summary").trim()
    if (title.isEmpty() && summary.isEmpty()) return null

    return AnalysisResult(
      title = title.ifEmpty { "Untitled screenshot" },
      summary = summary,
      category = Category.fromString(obj.optString("category")),
      tags = obj.optStringList("tags"),
      detectedApps = obj.optStringList("detected_apps"),
      importantText = obj.optStringList("important_text"),
      priorityScore = obj.optInt("priority_score", 0).coerceIn(0, 10),
      needsReview = false,
    )
  }

  private fun isolateJsonObject(raw: String): String? {
    var text = raw.trim()
    // Strip ```json ... ``` or ``` ... ``` fences.
    if (text.startsWith("```")) {
      text = text.removePrefix("```json").removePrefix("```").trim()
      val closing = text.lastIndexOf("```")
      if (closing >= 0) text = text.substring(0, closing).trim()
    }
    val start = text.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
      val c = text[i]
      when {
        escaped -> escaped = false
        c == '\\' && inString -> escaped = true
        c == '"' -> inString = !inString
        !inString && c == '{' -> depth++
        !inString && c == '}' -> {
          depth--
          if (depth == 0) return text.substring(start, i + 1)
        }
      }
    }
    return null
  }

  private fun JSONObject.optStringList(key: String): List<String> {
    val arr: JSONArray = optJSONArray(key) ?: return emptyList()
    return buildList {
      for (i in 0 until arr.length()) {
        val s = arr.optString(i).trim()
        if (s.isNotEmpty()) add(s)
      }
    }
  }
}
