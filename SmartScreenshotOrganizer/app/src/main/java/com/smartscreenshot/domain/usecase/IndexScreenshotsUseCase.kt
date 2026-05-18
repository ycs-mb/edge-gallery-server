package com.smartscreenshot.domain.usecase

import com.smartscreenshot.data.repo.ScreenshotRepository
import javax.inject.Inject

class IndexScreenshotsUseCase @Inject constructor(private val repository: ScreenshotRepository) {
  /** Returns the number of newly indexed screenshots. */
  suspend operator fun invoke(): Int = repository.indexNew()
}
