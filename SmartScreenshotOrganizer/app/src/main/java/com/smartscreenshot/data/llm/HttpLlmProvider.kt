package com.smartscreenshot.data.llm

import android.graphics.Bitmap
import android.util.Log
import com.smartscreenshot.data.llm.dto.ChatCompletionRequest
import com.smartscreenshot.data.llm.dto.ChatCompletionResponse
import com.smartscreenshot.data.llm.dto.ChatMessage
import com.smartscreenshot.data.settings.SettingsRepository
import com.smartscreenshot.domain.PromptTemplates
import com.smartscreenshot.domain.llm.LlmProvider
import com.smartscreenshot.domain.llm.LlmProviderType
import com.smartscreenshot.domain.model.AnalysisResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to any OpenAI-compatible `/v1/chat/completions` endpoint (e.g. the Edge
 * Gallery `LlmHttpServer`, which is non-streaming and text-only). Images are not
 * sent — the server discards them — so OCR text is the sole input.
 */
@Singleton
class HttpLlmProvider
@Inject
constructor(private val client: HttpClient, private val settings: SettingsRepository) :
  LlmProvider {

  override val type = LlmProviderType.HTTP

  override suspend fun isAvailable(): Boolean = settings.current().httpEndpoint.isNotBlank()

  override suspend fun analyze(ocrText: String, bitmap: Bitmap?): AnalysisResult {
    if (ocrText.isBlank()) return AnalysisResult.fallback("Empty screenshot")
    val endpoint = settings.current().httpEndpoint

    val firstRaw = request(endpoint, buildMessages(ocrText, repair = false)) ?: return failure()
    JsonExtraction.parse(firstRaw)?.let {
      return it
    }

    // One repair attempt.
    val repaired = request(endpoint, buildMessages(ocrText, repair = true, prior = firstRaw))
    repaired?.let { JsonExtraction.parse(it)?.let { parsed -> return parsed } }

    return failure()
  }

  private fun buildMessages(
    ocrText: String,
    repair: Boolean,
    prior: String? = null,
  ): List<ChatMessage> = buildList {
    add(ChatMessage("system", PromptTemplates.SYSTEM))
    add(ChatMessage("user", PromptTemplates.userPrompt(ocrText)))
    if (repair) {
      if (prior != null) add(ChatMessage("assistant", prior.take(2000)))
      add(ChatMessage("user", PromptTemplates.repairPrompt()))
    }
  }

  private suspend fun request(endpoint: String, messages: List<ChatMessage>): String? =
    try {
      val response: ChatCompletionResponse =
        client
          .post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(ChatCompletionRequest(messages = messages))
            timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
          }
          .body()
      response.firstContent().ifBlank { null }
    } catch (t: Throwable) {
      Log.w(TAG, "HTTP inference failed: ${t.message}")
      null
    }

  private fun failure() = AnalysisResult.fallback("Unanalyzed screenshot")

  companion object {
    private const val TAG = "HttpLlmProvider"
    private const val REQUEST_TIMEOUT_MS = 120_000L
  }
}
