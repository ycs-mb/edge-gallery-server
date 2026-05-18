package com.smartscreenshot.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.smartscreenshot.data.repo.ScreenshotRepository
import com.smartscreenshot.domain.model.Category
import com.smartscreenshot.domain.model.Screenshot
import com.smartscreenshot.domain.usecase.HybridSearchUseCase
import com.smartscreenshot.domain.usecase.IndexScreenshotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
  val total: Int = 0,
  val categories: List<Pair<Category, Int>> = emptyList(),
  val selectedCategory: Category? = null,
  val query: String = "",
  val searching: Boolean = false,
  val searchResults: List<Screenshot> = emptyList(),
  val scanning: Boolean = false,
  val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel
@Inject
constructor(
  private val repository: ScreenshotRepository,
  private val search: HybridSearchUseCase,
  private val indexScreenshots: IndexScreenshotsUseCase,
) : ViewModel() {

  private val _state = MutableStateFlow(HomeUiState())
  val state: StateFlow<HomeUiState> = _state.asStateFlow()

  private val selectedCategory = MutableStateFlow<Category?>(null)

  val paged: StateFlow<PagingData<Screenshot>> =
    selectedCategory
      .flatMapLatest { cat ->
        if (cat == null) repository.pagingAll() else repository.pagingByCategory(cat)
      }
      .cachedIn(viewModelScope)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PagingData.empty())

  private val queryFlow = MutableStateFlow("")

  init {
    repository.count().onEach { c -> _state.value = _state.value.copy(total = c) }.launchIn(viewModelScope)
    repository
      .categoryCounts()
      .onEach { c -> _state.value = _state.value.copy(categories = c) }
      .launchIn(viewModelScope)

    queryFlow
      .debounce(300)
      .distinctUntilChanged()
      .onEach { q ->
        if (q.isBlank()) {
          _state.value = _state.value.copy(searching = false, searchResults = emptyList())
        } else {
          _state.value = _state.value.copy(searching = true)
          val results = search(q).map { it.screenshot }
          _state.value = _state.value.copy(searching = false, searchResults = results)
        }
      }
      .launchIn(viewModelScope)
  }

  fun onQueryChange(q: String) {
    _state.value = _state.value.copy(query = q)
    queryFlow.value = q
  }

  fun onCategorySelected(category: Category?) {
    selectedCategory.value = category
    _state.value = _state.value.copy(selectedCategory = category)
  }

  fun scanNow() {
    if (_state.value.scanning) return
    viewModelScope.launch {
      _state.value = _state.value.copy(scanning = true, message = null)
      val count =
        runCatching { indexScreenshots() }
          .onFailure {
            _state.value = _state.value.copy(message = "Scan failed: ${it.message}")
          }
          .getOrDefault(0)
      _state.value =
        _state.value.copy(
          scanning = false,
          message = if (count > 0) "Indexed $count new screenshot(s)" else "No new screenshots",
        )
    }
  }

  fun clearMessage() {
    _state.value = _state.value.copy(message = null)
  }
}
