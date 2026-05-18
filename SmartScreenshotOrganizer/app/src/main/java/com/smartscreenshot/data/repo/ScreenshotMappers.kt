package com.smartscreenshot.data.repo

import com.smartscreenshot.data.local.ScreenshotEntity
import com.smartscreenshot.domain.model.Category
import com.smartscreenshot.domain.model.IndexStatus
import com.smartscreenshot.domain.model.Screenshot
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val stringListSerializer = ListSerializer(String.serializer())

fun List<String>.encode(): String = json.encodeToString(stringListSerializer, this)

fun String?.decodeStringList(): List<String> =
  if (this.isNullOrBlank()) emptyList()
  else runCatching { json.decodeFromString(stringListSerializer, this) }.getOrDefault(emptyList())

fun ScreenshotEntity.toDomain(): Screenshot =
  Screenshot(
    id = id,
    contentUri = contentUri,
    dateAdded = dateAdded,
    ocrText = ocrText,
    title = title,
    summary = summary,
    category = Category.fromString(category),
    tags = tagsJson.decodeStringList(),
    detectedApps = detectedAppsJson.decodeStringList(),
    importantText = importantTextJson.decodeStringList(),
    priorityScore = priorityScore,
    status = runCatching { IndexStatus.valueOf(status) }.getOrDefault(IndexStatus.INDEXED),
  )
