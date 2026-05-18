package com.smartscreenshot.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.smartscreenshot.data.permission.MediaAccess
import com.smartscreenshot.data.permission.MediaPermission
import com.smartscreenshot.domain.model.Category
import com.smartscreenshot.domain.model.Screenshot
import com.smartscreenshot.ui.common.EmptyState
import com.smartscreenshot.ui.common.ErrorBanner
import com.smartscreenshot.ui.common.PermissionRationale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  onOpenSettings: () -> Unit,
  onOpenDetail: (Long) -> Unit,
  viewModel: HomeViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val paged = viewModel.paged.collectAsLazyPagingItems()
  val context = LocalContext.current
  var access by remember { mutableStateOf(MediaPermission.current(context)) }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      access = MediaPermission.current(context)
      if (access != MediaAccess.DENIED) viewModel.scanNow()
    }
  val folderLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        viewModel.onFolderPicked(uri.toString())
      }
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Screenshots (${state.total})") },
        actions = {
          IconButton(onClick = { folderLauncher.launch(null) }) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = "Add folder")
          }
          IconButton(onClick = { viewModel.scanNow() }, enabled = !state.scanning) {
            if (state.scanning)
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, contentDescription = "Scan now")
          }
          IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
          }
        },
      )
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      if (access == MediaAccess.DENIED) {
        PermissionRationale(
          message =
            "Screenshot Organizer needs photo access to detect and organize your screenshots. " +
              "All analysis happens on-device.",
          actionLabel = "Grant access",
          onAction = { permissionLauncher.launch(MediaPermission.requestPermissions) },
        )
        return@Column
      }
      if (access == MediaAccess.PARTIAL) {
        ErrorBanner(
          "Limited photo access granted — new screenshots may be missed. Tap to grant full access.",
          onDismiss = { permissionLauncher.launch(MediaPermission.requestPermissions) },
        )
      }
      state.message?.let { ErrorBanner(it, onDismiss = viewModel::clearMessage) }

      OutlinedTextField(
        value = state.query,
        onValueChange = viewModel::onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        singleLine = true,
        label = { Text("Search screenshots") },
      )

      CategoryFilters(
        categories = state.categories,
        selected = state.selectedCategory,
        onSelected = viewModel::onCategorySelected,
      )

      when {
        state.query.isNotBlank() -> {
          if (state.searching) {
            EmptyState("Searching…")
          } else if (state.searchResults.isEmpty()) {
            EmptyState("No matches for \"${state.query}\"")
          } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              items(state.searchResults, key = { it.id }) { s ->
                ScreenshotRow(s) { onOpenDetail(s.id) }
              }
            }
          }
        }
        paged.itemCount == 0 ->
          EmptyState(
            "No screenshots indexed yet.\nTap refresh to scan, or use the folder icon " +
              "to pick the folder your screenshots are stored in."
          )
        else ->
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(paged.itemCount, key = paged.itemKey { it.id }) { index ->
              paged[index]?.let { s -> ScreenshotRow(s) { onOpenDetail(s.id) } }
            }
          }
      }
    }
  }
}

@Composable
private fun CategoryFilters(
  categories: List<Pair<Category, Int>>,
  selected: Category?,
  onSelected: (Category?) -> Unit,
) {
  if (categories.isEmpty()) return
  LazyRow(
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item {
      FilterChip(
        selected = selected == null,
        onClick = { onSelected(null) },
        label = { Text("All") },
      )
    }
    items(categories) { (cat, count) ->
      FilterChip(
        selected = selected == cat,
        onClick = { onSelected(if (selected == cat) null else cat) },
        label = { Text("${cat.label} ($count)") },
      )
    }
  }
}

@Composable
private fun ScreenshotRow(screenshot: Screenshot, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AsyncImage(
      model = screenshot.contentUri,
      contentDescription = screenshot.title,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
    )
    Column(modifier = Modifier.padding(start = 16.dp)) {
      Text(
        text = screenshot.title.ifBlank { "Untitled" },
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${screenshot.category.label} · ${screenshot.summary}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
