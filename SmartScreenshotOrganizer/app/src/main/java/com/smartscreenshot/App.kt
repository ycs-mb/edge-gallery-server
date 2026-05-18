package com.smartscreenshot

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.smartscreenshot.work.ScreenshotObserver
import com.smartscreenshot.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory
  @Inject lateinit var screenshotObserver: ScreenshotObserver
  @Inject lateinit var workScheduler: WorkScheduler

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

  override fun onCreate() {
    super.onCreate()
    workScheduler.ensurePeriodicSweep()

    ProcessLifecycleOwner.get()
      .lifecycle
      .addObserver(
        object : DefaultLifecycleObserver {
          override fun onStart(owner: LifecycleOwner) {
            screenshotObserver.register()
            workScheduler.enqueueOneShot() // cold-start / foreground sweep
          }

          override fun onStop(owner: LifecycleOwner) {
            screenshotObserver.unregister()
          }
        }
      )
  }
}
