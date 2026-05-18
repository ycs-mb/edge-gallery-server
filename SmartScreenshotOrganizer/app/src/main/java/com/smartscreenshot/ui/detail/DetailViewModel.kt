package com.smartscreenshot.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartscreenshot.data.repo.ScreenshotRepository
import com.smartscreenshot.domain.model.Screenshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DetailViewModel
@Inject
constructor(private val repository: ScreenshotRepository) : ViewModel() {

  fun screenshot(id: Long): StateFlow<Screenshot?> =
    repository
      .observe(id)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
