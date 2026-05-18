package com.smartscreenshot.domain.model

/** Structured result of LLM analysis of a screenshot's OCR text. */
data class AnalysisResult(
  val title: String,
  val summary: String,
  val category: Category,
  val tags: List<String>,
  val detectedApps: List<String>,
  val importantText: List<String>,
  val priorityScore: Int,
  /** True when parsing failed or the model returned an unusable payload. */
  val needsReview: Boolean,
) {
  companion object {
    fun fallback(title: String): AnalysisResult =
      AnalysisResult(
        title = title,
        summary = "",
        category = Category.OTHER,
        tags = emptyList(),
        detectedApps = emptyList(),
        importantText = emptyList(),
        priorityScore = 0,
        needsReview = true,
      )
  }
}
