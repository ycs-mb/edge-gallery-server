package com.smartscreenshot.work

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Near-real-time optimization: when MediaStore images change while the app process
 * is alive, enqueue a deduplicated indexing pass. The periodic sweep remains the
 * source of truth for events missed while the process was dead.
 */
@Singleton
class ScreenshotObserver
@Inject
constructor(private val context: Context, private val scheduler: WorkScheduler) {

  private val handler = Handler(Looper.getMainLooper())

  private val observer =
    object : ContentObserver(handler) {
      override fun onChange(selfChange: Boolean) {
        scheduler.enqueueOneShot()
      }
    }

  private var registered = false

  fun register() {
    if (registered) return
    context.contentResolver.registerContentObserver(
      MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
      true,
      observer,
    )
    registered = true
  }

  fun unregister() {
    if (!registered) return
    context.contentResolver.unregisterContentObserver(observer)
    registered = false
  }
}
