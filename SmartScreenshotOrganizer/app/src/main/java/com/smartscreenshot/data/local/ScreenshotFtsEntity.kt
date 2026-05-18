package com.smartscreenshot.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * External-content FTS4 mirror of [ScreenshotEntity]. Room keeps it in sync via
 * generated triggers; `rowid` maps to the screenshot id.
 */
@Fts4(contentEntity = ScreenshotEntity::class)
@Entity(tableName = "screenshots_fts")
data class ScreenshotFtsEntity(
  val title: String,
  val summary: String,
  @ColumnInfo(name = "ocr_text") val ocrText: String,
  @ColumnInfo(name = "tags_json") val tagsJson: String,
)
