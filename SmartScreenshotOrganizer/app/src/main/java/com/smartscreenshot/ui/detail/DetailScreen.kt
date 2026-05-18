package com.smartscreenshot.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.smartscreenshot.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
  screenshotId: Long,
  onBack: () -> Unit,
  viewModel: DetailViewModel = hiltViewModel(),
) {
  val screenshot by viewModel.screenshot(screenshotId).collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(screenshot?.title ?: "Detail") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    val s = screenshot
    if (s == null) {
      EmptyState("Loading…", modifier = Modifier.padding(padding))
      return@Scaffold
    }
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AsyncImage(
        model = s.contentUri,
        contentDescription = s.title,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth(),
      )
      Section("Category", s.category.label)
      if (s.summary.isNotBlank()) Section("Summary", s.summary)
      if (s.tags.isNotEmpty()) ChipSection("Tags", s.tags)
      if (s.detectedApps.isNotEmpty()) ChipSection("Detected apps", s.detectedApps)
      if (s.importantText.isNotEmpty()) Section("Important", s.importantText.joinToString("\n"))
      Section("Priority", s.priorityScore.toString())
      if (s.ocrText.isNotBlank()) Section("Extracted text", s.ocrText)
    }
  }
}

@Composable
private fun Section(title: String, body: String) {
  Column {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(body, style = MaterialTheme.typography.bodyMedium)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(title: String, items: List<String>) {
  Column {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
    }
  }
}
