package com.smartscreenshot.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: ScreenshotEntity)

  @Update suspend fun update(entity: ScreenshotEntity)

  @Query("SELECT * FROM screenshots WHERE id = :id") fun observe(id: Long): Flow<ScreenshotEntity?>

  @Query("SELECT * FROM screenshots WHERE id = :id") suspend fun getById(id: Long): ScreenshotEntity?

  @Query("SELECT EXISTS(SELECT 1 FROM screenshots WHERE id = :id)")
  suspend fun exists(id: Long): Boolean

  @Query("SELECT * FROM screenshots ORDER BY date_added DESC")
  fun pagingAll(): PagingSource<Int, ScreenshotEntity>

  @Query("SELECT * FROM screenshots WHERE category = :category ORDER BY date_added DESC")
  fun pagingByCategory(category: String): PagingSource<Int, ScreenshotEntity>

  @Query("SELECT category, COUNT(*) AS count FROM screenshots GROUP BY category ORDER BY count DESC")
  fun categoryCounts(): Flow<List<CategoryCount>>

  @Query("SELECT COUNT(*) FROM screenshots") fun count(): Flow<Int>

  /** Keyword candidates via FTS4 MATCH, newest first. */
  @Query(
    "SELECT s.* FROM screenshots s JOIN screenshots_fts f ON s.id = f.rowid " +
      "WHERE screenshots_fts MATCH :ftsQuery ORDER BY s.date_added DESC LIMIT :limit"
  )
  suspend fun ftsSearch(ftsQuery: String, limit: Int): List<ScreenshotEntity>

  /** Full set carrying embeddings, for the semantic full-scan fallback. */
  @Query("SELECT * FROM screenshots WHERE embedding IS NOT NULL")
  suspend fun allWithEmbeddings(): List<ScreenshotEntity>

  @Query("SELECT * FROM screenshots WHERE embedding IS NULL OR embedding_model_version != :version")
  suspend fun missingEmbeddingFor(version: String): List<ScreenshotEntity>

  @Query("SELECT MAX(date_added) FROM screenshots") suspend fun maxDateAdded(): Long?

  @Query("DELETE FROM screenshots") suspend fun clear()
}

data class CategoryCount(val category: String, val count: Int)
