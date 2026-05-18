package com.smartscreenshot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartscreenshot.domain.llm.LlmProviderType
import com.smartscreenshot.ui.common.ErrorBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val settings by viewModel.settingsState.collectAsStateWithLifecycle()
  val status by viewModel.status.collectAsStateWithLifecycle()
  var endpoint by remember(settings.httpEndpoint) { mutableStateOf(settings.httpEndpoint) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      status?.let { ErrorBanner(it, onDismiss = viewModel::clearStatus) }

      Text("Inference provider", style = MaterialTheme.typography.titleMedium)
      val modes = LlmProviderType.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { i, mode ->
          SegmentedButton(
            selected = settings.providerMode == mode,
            onClick = { viewModel.setProvider(mode) },
            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
          ) {
            Text(mode.name)
          }
        }
      }
      Text(
        "AUTO uses Gemini Nano (AICore) when available, otherwise the HTTP server.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Text("HTTP endpoint", style = MaterialTheme.typography.titleMedium)
      OutlinedTextField(
        value = endpoint,
        onValueChange = { endpoint = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("OpenAI-compatible /v1/chat/completions URL") },
      )
      Button(onClick = { viewModel.setEndpoint(endpoint) }) { Text("Save endpoint") }
      OutlinedButton(onClick = { viewModel.testConnection() }) { Text("Test connection") }

      Text("Maintenance", style = MaterialTheme.typography.titleMedium)
      OutlinedButton(onClick = { viewModel.reindexEmbeddings() }) {
        Text("Recompute embeddings")
      }
      OutlinedButton(onClick = { viewModel.clearCache() }) { Text("Clear local index / cache") }
    }
  }
}
