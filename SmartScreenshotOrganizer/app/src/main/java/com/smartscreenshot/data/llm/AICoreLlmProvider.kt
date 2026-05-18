package com.smartscreenshot.data.llm

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import com.smartscreenshot.domain.PromptTemplates
import com.smartscreenshot.domain.llm.LlmProvider
import com.smartscreenshot.domain.llm.LlmProviderType
import com.smartscreenshot.domain.model.AnalysisResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gemini Nano via ML Kit GenAI Prompt. Heavyweight: the model is created and warmed
 * up lazily once, reused across calls, and released in [close]. Stream output is
 * accumulated into a single string. Patterns adapted from the Edge Gallery
 * `AICoreModelHelper` reference implementation.
 */
@Singleton
class AICoreLlmProvider @Inject constructor() : LlmProvider {

  override val type = LlmProviderType.AICORE

  private val mutex = Mutex()
  @Volatile private var model: GenerativeModel? = null
  @Volatile private var warmedUp = false

  private fun client(): GenerativeModel =
    model
      ?: Generation.getClient(
          generationConfig {
            modelConfig = modelConfig {
              releaseStage = ModelReleaseStage.STABLE
              preference = ModelPreference.FAST
            }
          }
        )
        .also { model = it }

  override suspend fun isAvailable(): Boolean =
    try {
      when (client().checkStatus()) {
        FeatureStatus.AVAILABLE,
        FeatureStatus.DOWNLOADABLE,
        FeatureStatus.DOWNLOADING -> true
        else -> false
      }
    } catch (t: Throwable) {
      Log.w(TAG, "checkStatus failed: ${t.message}")
      false
    }

  private suspend fun ensureReady(): Boolean =
    mutex.withLock {
      if (warmedUp) return@withLock true
      try {
        val m = client()
        when (m.checkStatus()) {
          FeatureStatus.AVAILABLE -> {
            m.warmup()
            warmedUp = true
          }
          FeatureStatus.DOWNLOADABLE,
          FeatureStatus.DOWNLOADING -> {
            m.download().collect { status ->
              if (status is DownloadStatus.DownloadCompleted) {
                m.warmup()
                warmedUp = true
              }
            }
          }
          else -> Unit
        }
      } catch (t: Throwable) {
        Log.w(TAG, "ensureReady failed: ${t.message}")
      }
      warmedUp
    }

  override suspend fun analyze(ocrText: String, bitmap: Bitmap?): AnalysisResult {
    if (ocrText.isBlank()) return AnalysisResult.fallback("Empty screenshot")
    if (!ensureReady()) return AnalysisResult.fallback("AICore unavailable")

    val basePrompt = PromptTemplates.SYSTEM + "\n\n" + PromptTemplates.userPrompt(ocrText)
    generate(basePrompt, bitmap)?.let { JsonExtraction.parse(it)?.let { r -> return r } }

    val repair = basePrompt + "\n\n" + PromptTemplates.repairPrompt()
    generate(repair, null)?.let { JsonExtraction.parse(it)?.let { r -> return r } }

    return AnalysisResult.fallback("Unanalyzed screenshot")
  }

  private suspend fun generate(prompt: String, bitmap: Bitmap?): String? =
    try {
      val request =
        if (bitmap != null) {
          generateContentRequest(ImagePart(bitmap), TextPart(prompt)) {
            temperature = 0.2f
            topK = 40
          }
        } else {
          generateContentRequest(TextPart(prompt)) {
            temperature = 0.2f
            topK = 40
          }
        }
      val sb = StringBuilder()
      client().generateContentStream(request).collect { response ->
        response.candidates.firstOrNull()?.let { sb.append(it.text ?: "") }
      }
      sb.toString().ifBlank { null }
    } catch (t: Throwable) {
      Log.w(TAG, "generate failed: ${t.message}")
      null
    }

  override suspend fun close() {
    mutex.withLock {
      try {
        model?.close()
      } catch (_: Throwable) {
        // best effort
      }
      model = null
      warmedUp = false
    }
  }

  companion object {
    private const val TAG = "AICoreLlmProvider"
  }
}
