package com.smartscreenshot.data.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enumerates image files inside a user-picked Storage Access Framework folder.
 *
 * This is a fallback to [MediaStoreScreenshotSource]: some devices/OEMs store screenshots in
 * folders the MediaStore heuristic doesn't match, and "selected photos only" access hides them
 * entirely. Pointing the app at an explicit folder via ACTION_OPEN_DOCUMENT_TREE bypasses both
 * problems — the persisted tree-URI grant gives durable read access regardless of MediaStore.
 *
 * IDs are derived as a negative 63-bit hash of the document URI so they never collide with
 * MediaStore `_ID`s (always positive) in the shared `screenshots` table.
 */
@Singleton
class DocumentTreeImageSource @Inject constructor(private val context: Context) {

  /** Walks [treeUriString] recursively (depth-guarded) and returns every image document. */
  fun queryImages(treeUriString: String, maxItems: Int = 2_000): List<ScreenshotMediaItem> {
    if (treeUriString.isBlank()) return emptyList()
    val treeUri =
      try {
        Uri.parse(treeUriString)
      } catch (t: Throwable) {
        Log.w(TAG, "Bad tree URI: $treeUriString", t)
        return emptyList()
      }
    val rootDocId =
      try {
        DocumentsContract.getTreeDocumentId(treeUri)
      } catch (t: Throwable) {
        Log.w(TAG, "Not a tree URI: $treeUriString", t)
        return emptyList()
      }

    val out = mutableListOf<ScreenshotMediaItem>()
    walk(treeUri, rootDocId, depth = 0, out = out, maxItems = maxItems)
    return out
  }

  private fun walk(
    treeUri: Uri,
    parentDocId: String,
    depth: Int,
    out: MutableList<ScreenshotMediaItem>,
    maxItems: Int,
  ) {
    if (depth > MAX_DEPTH || out.size >= maxItems) return
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
      )
    try {
      context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        while (cursor.moveToNext()) {
          if (out.size >= maxItems) return
          val docId = cursor.getString(idCol) ?: continue
          val mime = cursor.getString(mimeCol) ?: ""
          if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            walk(treeUri, docId, depth + 1, out, maxItems)
            continue
          }
          if (!mime.startsWith("image/")) continue
          val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
          out +=
            ScreenshotMediaItem(
              id = stableNegativeId(docUri.toString()),
              uri = docUri,
              dateAdded = cursor.getLong(modCol) / 1000L,
              displayName = cursor.getString(nameCol) ?: "image",
            )
        }
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to enumerate $parentDocId", t)
    }
  }

  private fun stableNegativeId(s: String): Long {
    // 64-bit FNV-1a, masked to 63 bits then negated so folder IDs occupy the negative
    // half of the keyspace, keeping them disjoint from positive MediaStore _IDs.
    var hash = -0x340d631b7bdddcdbL // FNV offset basis
    for (c in s) {
      hash = hash xor c.code.toLong()
      hash *= 0x100000001b3L // FNV prime
    }
    return -((hash and Long.MAX_VALUE) or 1L)
  }

  companion object {
    private const val TAG = "DocumentTreeImageSource"
    private const val MAX_DEPTH = 6
  }
}
