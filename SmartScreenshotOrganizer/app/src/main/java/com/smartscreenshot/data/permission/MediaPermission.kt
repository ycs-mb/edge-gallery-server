package com.smartscreenshot.data.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccess {
  /** Full read access to images — auto-organize works fully. */
  FULL,
  /** Android 14+ "selected photos only" — background sweeps silently miss screenshots. */
  PARTIAL,
  /** No access. */
  DENIED,
}

object MediaPermission {

  /** The runtime permissions to request for this API level. */
  val requestPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
      )
    } else {
      arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    }

  fun current(context: Context): MediaAccess {
    val full =
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
        PackageManager.PERMISSION_GRANTED
    if (full) return MediaAccess.FULL

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val selected =
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ) == PackageManager.PERMISSION_GRANTED
      if (selected) return MediaAccess.PARTIAL
    }
    return MediaAccess.DENIED
  }
}
