/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.serveronly

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.server.AICoreModelFactory
import com.google.ai.edge.gallery.server.ImportedModelStore
import com.google.ai.edge.gallery.server.LlmServerService
import com.google.ai.edge.gallery.server.ServerModelHolder
import com.google.ai.edge.gallery.runtime.aicore.AICoreModelHelper
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ServerLauncher"

class ServerLauncherActivity : ComponentActivity() {

  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) { ServerLauncherScreen() }
      }
    }
  }
}

enum class ModelStatus { CHECKING, AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerLauncherScreen() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var serverRunning by remember { mutableStateOf(LlmServerService.isRunning) }
  var localIp by remember { mutableStateOf(findLocalIpv4() ?: "<unknown>") }
  var activeModel by remember { mutableStateOf(ServerModelHolder.activeModel?.name) }

  val modelDefs = remember { AICoreModelFactory.AVAILABLE_MODELS }
  val modelStatuses = remember { mutableStateMapOf<String, ModelStatus>() }
  var downloadProgress by remember { mutableStateOf<Pair<String, Float>?>(null) }

  val prefs = remember {
    context.getSharedPreferences(ImportedModelStore.PREFS_NAME, Context.MODE_PRIVATE)
  }
  var defaultModelName by remember {
    mutableStateOf(prefs.getString(ImportedModelStore.PREF_DEFAULT_MODEL_NAME, null))
  }

  LaunchedEffect(Unit) {
    modelDefs.forEach { modelStatuses[it.name] = ModelStatus.CHECKING }
    withContext(Dispatchers.IO) {
      modelDefs.forEach { def ->
        val model = AICoreModelFactory.createModel(def.name) ?: return@forEach
        try {
          val available = AICoreModelHelper.isModelDownloaded(model)
          modelStatuses[def.name] =
            if (available) ModelStatus.AVAILABLE else ModelStatus.DOWNLOADABLE
        } catch (e: Exception) {
          Log.w(TAG, "Failed to check status for ${def.name}", e)
          modelStatuses[def.name] = ModelStatus.UNAVAILABLE
        }
      }
    }
  }

  LaunchedEffect(Unit) {
    while (true) {
      serverRunning = LlmServerService.isRunning
      activeModel = ServerModelHolder.activeModel?.name
      delay(750)
    }
  }

  Scaffold(
    topBar = { TopAppBar(title = { Text("Edge Gallery Server") }) },
  ) { padding ->
    Column(
      modifier =
        Modifier
          .padding(padding)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      StatusCard(
        running = serverRunning,
        localIp = localIp,
        port = LlmServerService.DEFAULT_PORT,
        activeModel = activeModel,
      )

      ModelListCard(
        modelDefs = modelDefs,
        modelStatuses = modelStatuses,
        downloadProgress = downloadProgress,
        onDownload = { def ->
          val model = AICoreModelFactory.createModel(def.name) ?: return@ModelListCard
          modelStatuses[def.name] = ModelStatus.DOWNLOADING
          downloadProgress = def.name to 0f
          scope.launch {
            AICoreModelHelper.downloadModel(
              context = context,
              coroutineScope = this,
              model = model,
              onProgress = { downloaded, total ->
                val pct = if (total > 0) downloaded.toFloat() / total else 0f
                downloadProgress = def.name to pct
              },
              onDone = {
                modelStatuses[def.name] = ModelStatus.AVAILABLE
                downloadProgress = null
              },
              onError = { msg ->
                Log.e(TAG, "Download failed for ${def.name}: $msg")
                modelStatuses[def.name] = ModelStatus.ERROR
                downloadProgress = null
              },
            )
          }
        },
      )

      DefaultModelPicker(
        modelDefs = modelDefs,
        modelStatuses = modelStatuses,
        defaultModelName = defaultModelName,
        onDefaultModelChange = { name ->
          prefs.edit().putString(ImportedModelStore.PREF_DEFAULT_MODEL_NAME, name).apply()
          defaultModelName = name
        },
      )

      ServerControls(
        running = serverRunning,
        canStart = defaultModelName != null &&
          modelStatuses[defaultModelName] == ModelStatus.AVAILABLE,
        onStart = {
          LlmServerService.start(context, LlmServerService.DEFAULT_PORT)
          serverRunning = true
        },
        onStop = {
          LlmServerService.stop(context)
          serverRunning = false
        },
        onRefreshIp = { localIp = findLocalIpv4() ?: "<unknown>" },
      )
    }
  }
}

@Composable
private fun StatusCard(running: Boolean, localIp: String, port: Int, activeModel: String?) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("Server status", fontWeight = FontWeight.SemiBold)
      Text(if (running) "Running" else "Stopped", style = MaterialTheme.typography.bodyLarge)
      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
      Text("Listening on http://$localIp:$port", style = MaterialTheme.typography.bodyMedium)
      Text(
        "POST to /v1/chat/completions from any client on the same network.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (activeModel != null) {
        Text(
          "Active model: $activeModel",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      } else {
        Text(
          "No model loaded yet.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun ModelListCard(
  modelDefs: List<AICoreModelFactory.AICoreModelDef>,
  modelStatuses: Map<String, ModelStatus>,
  downloadProgress: Pair<String, Float>?,
  onDownload: (AICoreModelFactory.AICoreModelDef) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("AICore Models (Gemini Nano)", fontWeight = FontWeight.SemiBold)
      Text(
        "On-device models running on NPU via Google AI Core.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      modelDefs.forEach { def ->
        val status = modelStatuses[def.name] ?: ModelStatus.CHECKING
        HorizontalDivider()
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(def.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
              statusLabel(status),
              style = MaterialTheme.typography.bodySmall,
              color = when (status) {
                ModelStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
                ModelStatus.ERROR, ModelStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
              },
            )
          }
          if (status == ModelStatus.DOWNLOADABLE) {
            Button(onClick = { onDownload(def) }) { Text("Download") }
          }
        }
        if (status == ModelStatus.DOWNLOADING && downloadProgress?.first == def.name) {
          LinearProgressIndicator(
            progress = { downloadProgress.second },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultModelPicker(
  modelDefs: List<AICoreModelFactory.AICoreModelDef>,
  modelStatuses: Map<String, ModelStatus>,
  defaultModelName: String?,
  onDefaultModelChange: (String) -> Unit,
) {
  val availableModels = modelDefs.filter { modelStatuses[it.name] == ModelStatus.AVAILABLE }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Default model", fontWeight = FontWeight.SemiBold)
      Text(
        "Loaded automatically when the server starts.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (availableModels.isEmpty()) {
        Text(
          "No models available. Download a model first.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
        return@Card
      }
      var expanded by remember { mutableStateOf(false) }
      val displayName = modelDefs.find { it.name == defaultModelName }?.displayName
        ?: defaultModelName ?: "(none)"
      ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        TextField(
          value = displayName,
          onValueChange = {},
          readOnly = true,
          label = { Text("Pick a model") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
          modifier = Modifier
            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            .fillMaxWidth(),
        )
        ExposedDropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = false },
        ) {
          availableModels.forEach { def ->
            DropdownMenuItem(
              text = { Text(def.displayName) },
              onClick = {
                onDefaultModelChange(def.name)
                expanded = false
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ServerControls(
  running: Boolean,
  canStart: Boolean,
  onStart: () -> Unit,
  onStop: () -> Unit,
  onRefreshIp: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Server controls", fontWeight = FontWeight.SemiBold)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (running) {
          OutlinedButton(onClick = onStop) { Text("Stop server") }
        } else {
          Button(onClick = onStart, enabled = canStart) { Text("Start server") }
        }
        OutlinedButton(onClick = onRefreshIp) { Text("Refresh IP") }
      }
      if (!canStart && !running) {
        Text(
          "Download and select a model before starting.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

private fun statusLabel(status: ModelStatus): String = when (status) {
  ModelStatus.CHECKING -> "Checking..."
  ModelStatus.AVAILABLE -> "Available"
  ModelStatus.DOWNLOADABLE -> "Ready to download"
  ModelStatus.DOWNLOADING -> "Downloading..."
  ModelStatus.UNAVAILABLE -> "Unavailable on this device"
  ModelStatus.ERROR -> "Error"
}

private fun findLocalIpv4(): String? {
  return try {
    val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
    for (iface in ifaces) {
      if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
      for (addr in iface.inetAddresses) {
        if (addr is Inet4Address && !addr.isLoopbackAddress) {
          return addr.hostAddress
        }
      }
    }
    null
  } catch (_: Throwable) {
    null
  }
}
