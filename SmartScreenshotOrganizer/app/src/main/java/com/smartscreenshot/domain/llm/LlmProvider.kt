package com.smartscreenshot.domain.llm

import android.graphics.Bitmap
import com.smartscreenshot.domain.model.AnalysisResult

/**
 * Abstraction over an on-device or local-network LLM that turns a screenshot's OCR
 * text into structured metadata. OCR text is the canonical input; [bitmap] is an
 * optional enrichment that some providers (AICore) may use and others (HTTP) ignore.
 */
interface LlmProvider {
  val type: LlmProviderType

  /** True if this provider can currently service requests on this device. */
  suspend fun isAvailable(): Boolean

  /** Runs analysis. Implementations must never throw; return a needsReview result on failure. */
  suspend fun analyze(ocrText: String, bitmap: Bitmap?): AnalysisResult

  /** Releases any heavyweight resources (e.g. AICore warmup state). */
  suspend fun close() {}
}

enum class LlmProviderType {
  AUTO,
  AICORE,
  HTTP,
}
