package com.smartscreenshot.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import javax.inject.Inject
import javax.inject.Singleton

data class ScreenshotMediaItem(
  val id: Long,
  val uri: Uri,
  val dateAdded: Long,
  val displayName: String,
)

@Singleton
class MediaStoreScreenshotSource @Inject constructor(private val context: Context) {

  /**
   * Returns screenshot images with `date_added` strictly greater than [sinceEpochSeconds],
   * oldest first so indexing progresses monotonically.
   */
  fun queryNewScreenshots(sinceEpochSeconds: Long, limit: Int = 200): List<ScreenshotMediaItem> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection =
      arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
      )
    // Primary signal: RELATIVE_PATH contains "Screenshots". Fallbacks: bucket name,
    // or a "Screenshot" filename prefix used by some OEMs.
    val selection =
      "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? " +
        "OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ? " +
        "OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?) " +
        "AND ${MediaStore.Images.Media.DATE_ADDED} > ?"
    val args = arrayOf("%Screenshots%", "Screenshots", "Screenshot%", sinceEpochSeconds.toString())
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC LIMIT $limit"

    val items = mutableListOf<ScreenshotMediaItem>()
    context.contentResolver
      .query(collection, projection, selection, args, sortOrder)
      ?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        while (cursor.moveToNext()) {
          val id = cursor.getLong(idCol)
          items +=
            ScreenshotMediaItem(
              id = id,
              uri = ContentUris.withAppendedId(collection, id),
              dateAdded = cursor.getLong(dateCol),
              displayName = cursor.getString(nameCol) ?: "screenshot_$id",
            )
        }
      }
    return items
  }
}
