package com.smartscreenshot.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screenshots")
data class ScreenshotEntity(
  /** MediaStore image _ID, stable across the device. */
  @PrimaryKey val id: Long,
  @ColumnInfo(name = "content_uri") val contentUri: String,
  @ColumnInfo(name = "date_added", index = true) val dateAdded: Long,
  @ColumnInfo(name = "ocr_text") val ocrText: String,
  val title: String,
  val summary: String,
  @ColumnInfo(index = true) val category: String,
  @ColumnInfo(name = "tags_json") val tagsJson: String,
  @ColumnInfo(name = "detected_apps_json") val detectedAppsJson: String,
  @ColumnInfo(name = "important_text_json") val importantTextJson: String,
  @ColumnInfo(name = "priority_score") val priorityScore: Int,
  @ColumnInfo(name = "source_app") val sourceApp: String?,
  val embedding: FloatArray?,
  @ColumnInfo(name = "embedding_model_version") val embeddingModelVersion: String?,
  @ColumnInfo(index = true) val status: String,
) {
  // Room entities with array fields should override equals/hashCode by identity-relevant
  // fields; we key on the stable primary id.
  override fun equals(other: Any?): Boolean = this === other || (other is ScreenshotEntity && other.id == id)

  override fun hashCode(): Int = id.hashCode()
}
