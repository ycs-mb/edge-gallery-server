package com.smartscreenshot.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartscreenshot.domain.usecase.IndexScreenshotsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class IndexWorker
@AssistedInject
constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val indexScreenshots: IndexScreenshotsUseCase,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result =
    try {
      indexScreenshots()
      Result.success()
    } catch (t: Throwable) {
      if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

  companion object {
    const val UNIQUE_PERIODIC = "screenshot-index-periodic"
    const val UNIQUE_ONESHOT = "screenshot-index-oneshot"
    private const val MAX_ATTEMPTS = 3
  }
}
