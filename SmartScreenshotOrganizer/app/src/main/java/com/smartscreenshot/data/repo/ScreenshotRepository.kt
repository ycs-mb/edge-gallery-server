package com.smartscreenshot.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.smartscreenshot.data.embedding.TextEmbedder
import com.smartscreenshot.data.llm.LlmProviderSelector
import com.smartscreenshot.data.local.ScreenshotDao
import com.smartscreenshot.data.local.ScreenshotEntity
import com.smartscreenshot.data.media.DocumentTreeImageSource
import com.smartscreenshot.data.media.MediaStoreScreenshotSource
import com.smartscreenshot.data.media.ScreenshotMediaItem
import com.smartscreenshot.data.ocr.OcrTextExtractor
import com.smartscreenshot.data.settings.SettingsRepository
import com.smartscreenshot.domain.llm.LlmProvider
import com.smartscreenshot.domain.model.AnalysisResult
import com.smartscreenshot.domain.model.Category
import com.smartscreenshot.domain.model.IndexStatus
import com.smartscreenshot.domain.model.Screenshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ScreenshotRepository
@Inject
constructor(
  private val dao: ScreenshotDao,
  private val media: MediaStoreScreenshotSource,
  private val folderSource: DocumentTreeImageSource,
  private val ocr: OcrTextExtractor,
  private val selector: LlmProviderSelector,
  private val embedder: TextEmbedder,
  private val settings: SettingsRepository,
) {

  fun pagingAll(): Flow<PagingData<Screenshot>> =
    Pager(PAGING) { dao.pagingAll() }.flow.map { p -> p.map { it.toDomain() } }

  fun pagingByCategory(category: Category): Flow<PagingData<Screenshot>> =
    Pager(PAGING) { dao.pagingByCategory(category.name) }.flow.map { p -> p.map { it.toDomain() } }

  fun observe(id: Long): Flow<Screenshot?> = dao.observe(id).map { it?.toDomain() }

  fun categoryCounts(): Flow<List<Pair<Category, Int>>> =
    dao.categoryCounts().map { list ->
      list.map { Category.fromString(it.category) to it.count }
    }

  fun count(): Flow<Int> = dao.count()

  /**
   * Indexes screenshots created since the last successful sweep. Idempotent: rows
   * that already exist are skipped. Returns the number newly indexed.
   */
  suspend fun indexNew(): Int {
    val current = settings.current()
    val since = current.lastIndexedDateAdded
    val mediaItems = media.queryNewScreenshots(since)
    // Folder items are gated only by dao.exists() (idempotent) — their SAF lastModified
    // timestamps live in a different namespace than MediaStore date_added, so they must
    // not advance or be filtered by the MediaStore watermark.
    val folderItems = folderSource.queryImages(current.pickedFolderUri)

    var maxDate = since
    var indexed = 0
    val provider = selector.select()
    try {
      for (item in mediaItems) {
        maxDate = maxOf(maxDate, item.dateAdded)
        if (indexItem(item, provider)) indexed++
      }
      for (item in folderItems) {
        if (indexItem(item, provider)) indexed++
      }
    } finally {
      runCatching { provider.close() }
      settings.setLastIndexedDateAdded(maxDate)
    }
    return indexed
  }

  /** Indexes one item if not already present. Returns true if a new row was written. */
  private suspend fun indexItem(item: ScreenshotMediaItem, provider: LlmProvider): Boolean {
    if (dao.exists(item.id)) return false

    val ocrOut = ocr.extract(item.uri)
    val analysis: AnalysisResult =
      if (ocrOut.text.isBlank()) AnalysisResult.fallback(item.displayName)
      else provider.analyze(ocrOut.text, ocrOut.bitmap)

    val embedding =
      embedder.embed(listOf(analysis.title, analysis.summary, ocrOut.text).joinToString("\n"))

    dao.upsert(
      ScreenshotEntity(
        id = item.id,
        contentUri = item.uri.toString(),
        dateAdded = item.dateAdded,
        ocrText = ocrOut.text,
        title = analysis.title,
        summary = analysis.summary,
        category = analysis.category.name,
        tagsJson = analysis.tags.encode(),
        detectedAppsJson = analysis.detectedApps.encode(),
        importantTextJson = analysis.importantText.encode(),
        priorityScore = analysis.priorityScore,
        sourceApp = analysis.detectedApps.firstOrNull(),
        embedding = embedding,
        embeddingModelVersion = if (embedding != null) embedder.modelVersion else null,
        status =
          if (analysis.needsReview) IndexStatus.NEEDS_REVIEW.name else IndexStatus.INDEXED.name,
      )
    )
    return true
  }

  /** Recomputes embeddings for rows missing them or built with an older model. */
  suspend fun reindexEmbeddings(): Int {
    if (!embedder.isReady) return 0
    val version = embedder.modelVersion
    val rows = dao.missingEmbeddingFor(version)
    var updated = 0
    for (row in rows) {
      val vec = embedder.embed(listOf(row.title, row.summary, row.ocrText).joinToString("\n")) ?: continue
      dao.update(row.copy(embedding = vec, embeddingModelVersion = version))
      updated++
    }
    return updated
  }

  suspend fun clearAll() {
    dao.clear()
    settings.setLastIndexedDateAdded(0L)
  }

  internal suspend fun ftsCandidates(ftsQuery: String, limit: Int) = dao.ftsSearch(ftsQuery, limit)

  internal suspend fun allWithEmbeddings() = dao.allWithEmbeddings()

  companion object {
    private val PAGING = PagingConfig(pageSize = 30, enablePlaceholders = false)
  }
}
