package com.smartscreenshot.domain.usecase

import com.smartscreenshot.data.embedding.TextEmbedder
import com.smartscreenshot.data.local.ScreenshotEntity
import com.smartscreenshot.data.repo.ScreenshotRepository
import com.smartscreenshot.data.repo.toDomain
import com.smartscreenshot.domain.model.Screenshot
import javax.inject.Inject

/**
 * Hybrid retrieval: FTS4 keyword candidates re-ranked by embedding cosine
 * similarity. Falls back to a full embedding scan when the keyword set is thin so
 * conceptual queries still return results.
 */
class HybridSearchUseCase
@Inject
constructor(
  private val repository: ScreenshotRepository,
  private val embedder: TextEmbedder,
) {

  data class Ranked(val screenshot: Screenshot, val score: Float)

  suspend operator fun invoke(rawQuery: String, limit: Int = 100): List<Ranked> {
    val query = rawQuery.trim()
    if (query.isEmpty()) return emptyList()

    val ftsExpr = toFtsMatch(query)
    val candidates: List<ScreenshotEntity> =
      if (ftsExpr != null) repository.ftsCandidates(ftsExpr, FTS_LIMIT) else emptyList()

    val queryVec = embedder.embed(query)

    val pool: List<ScreenshotEntity> =
      if (candidates.size >= MIN_CANDIDATES || queryVec == null) candidates
      else (candidates + repository.allWithEmbeddings()).distinctBy { it.id }

    if (pool.isEmpty()) return emptyList()

    val ftsIds = candidates.mapIndexed { idx, e -> e.id to idx }.toMap()
    val ranked =
      pool.map { entity ->
        val ftsRank =
          ftsIds[entity.id]?.let { 1f - (it.toFloat() / candidates.size.coerceAtLeast(1)) } ?: 0f
        val cosine =
          if (queryVec != null && entity.embedding != null)
            TextEmbedder.cosine(queryVec, entity.embedding).coerceIn(0f, 1f)
          else 0f
        Ranked(entity.toDomain(), W_FTS * ftsRank + W_VEC * cosine)
      }

    return ranked.filter { it.score > 0f }.sortedByDescending { it.score }.take(limit)
  }

  /** Builds a safe FTS4 MATCH expression with prefix matching; null if no usable token. */
  private fun toFtsMatch(query: String): String? {
    val tokens =
      query
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 2 }
        .map { "${it.lowercase()}*" }
    return if (tokens.isEmpty()) null else tokens.joinToString(" ")
  }

  companion object {
    private const val FTS_LIMIT = 200
    private const val MIN_CANDIDATES = 10
    private const val W_FTS = 0.4f
    private const val W_VEC = 0.6f
  }
}
