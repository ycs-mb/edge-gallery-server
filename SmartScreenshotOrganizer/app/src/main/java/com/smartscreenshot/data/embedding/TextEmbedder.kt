package com.smartscreenshot.data.embedding

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder as MpTextEmbedder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * On-device text embeddings via MediaPipe. The model file
 * `assets/universal_sentence_encoder.tflite` must be present; if it is missing the
 * embedder reports [isReady] == false and search degrades to FTS-only instead of
 * crashing.
 */
@Singleton
class TextEmbedder @Inject constructor(private val context: Context) {

  @Volatile private var embedder: MpTextEmbedder? = null
  @Volatile private var initialized = false

  val modelVersion: String = MODEL_ASSET

  val isReady: Boolean
    get() {
      ensureInit()
      return embedder != null
    }

  @Synchronized
  private fun ensureInit() {
    if (initialized) return
    initialized = true
    embedder =
      try {
        val options =
          MpTextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .setL2Normalize(true)
            .build()
        MpTextEmbedder.createFromOptions(context, options)
      } catch (t: Throwable) {
        Log.w(TAG, "Embedding model unavailable ($MODEL_ASSET): ${t.message}")
        null
      }
  }

  /** Returns an L2-normalized embedding, or null when the model is unavailable. */
  fun embed(text: String): FloatArray? {
    ensureInit()
    val e = embedder ?: return null
    if (text.isBlank()) return null
    return try {
      val result = e.embed(text.take(MAX_CHARS))
      result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
    } catch (t: Throwable) {
      Log.w(TAG, "embed failed: ${t.message}")
      null
    }
  }

  companion object {
    private const val TAG = "TextEmbedder"
    private const val MODEL_ASSET = "universal_sentence_encoder.tflite"
    private const val MAX_CHARS = 4000

    /** Cosine similarity for already-L2-normalized vectors (falls back to full formula). */
    fun cosine(a: FloatArray, b: FloatArray): Float {
      if (a.size != b.size || a.isEmpty()) return 0f
      var dot = 0f
      var na = 0f
      var nb = 0f
      for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
      }
      val denom = sqrt(na) * sqrt(nb)
      return if (denom == 0f) 0f else dot / denom
    }
  }
}
