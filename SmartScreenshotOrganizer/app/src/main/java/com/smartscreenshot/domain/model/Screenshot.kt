package com.smartscreenshot.domain.model

/** Domain representation of an indexed screenshot. */
data class Screenshot(
  val id: Long,
  val contentUri: String,
  val dateAdded: Long,
  val ocrText: String,
  val title: String,
  val summary: String,
  val category: Category,
  val tags: List<String>,
  val detectedApps: List<String>,
  val importantText: List<String>,
  val priorityScore: Int,
  val status: IndexStatus,
)
