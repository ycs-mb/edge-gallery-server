package com.smartscreenshot.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartscreenshot.data.repo.ScreenshotRepository
import com.smartscreenshot.data.settings.AppSettings
import com.smartscreenshot.data.settings.SettingsRepository
import com.smartscreenshot.domain.llm.LlmProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
  private val settings: SettingsRepository,
  private val repository: ScreenshotRepository,
  private val httpClient: HttpClient,
) : ViewModel() {

  val settingsState: StateFlow<AppSettings> =
    settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

  private val _status = MutableStateFlow<String?>(null)
  val status: StateFlow<String?> = _status.asStateFlow()

  fun setProvider(mode: LlmProviderType) = viewModelScope.launch { settings.setProviderMode(mode) }

  fun setEndpoint(url: String) = viewModelScope.launch { settings.setHttpEndpoint(url) }

  fun testConnection() =
    viewModelScope.launch {
      _status.value = "Testing…"
      val endpoint = settings.current().httpEndpoint
      _status.value =
        runCatching {
            val base = endpoint.substringBefore("/v1/").trimEnd('/')
            val resp = httpClient.get("$base/health")
            "Reachable (HTTP ${resp.status.value})"
          }
          .getOrElse { "Unreachable: ${it.message}" }
    }

  fun reindexEmbeddings() =
    viewModelScope.launch {
      _status.value = "Reindexing embeddings…"
      val n = runCatching { repository.reindexEmbeddings() }.getOrDefault(0)
      _status.value = "Reindexed $n screenshot(s)"
    }

  fun clearCache() =
    viewModelScope.launch {
      repository.clearAll()
      _status.value = "Cleared local index"
    }

  fun clearStatus() {
    _status.value = null
  }
}
