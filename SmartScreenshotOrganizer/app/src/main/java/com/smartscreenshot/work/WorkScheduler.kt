package com.smartscreenshot.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(private val workManager: WorkManager) {

  /** Source-of-truth periodic sweep; survives process death. */
  fun ensurePeriodicSweep() {
    val request =
      PeriodicWorkRequestBuilder<IndexWorker>(6, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    workManager.enqueueUniquePeriodicWork(
      IndexWorker.UNIQUE_PERIODIC,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }

  /** One-shot sweep (cold start or ContentObserver trigger), de-duplicated. */
  fun enqueueOneShot() {
    val request =
      OneTimeWorkRequestBuilder<IndexWorker>()
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .build()
    workManager.enqueueUniqueWork(
      IndexWorker.UNIQUE_ONESHOT,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request,
    )
  }
}
