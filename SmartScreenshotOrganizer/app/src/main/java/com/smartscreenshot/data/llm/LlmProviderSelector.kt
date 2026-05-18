package com.smartscreenshot.data.llm

import com.smartscreenshot.data.settings.SettingsRepository
import com.smartscreenshot.domain.llm.LlmProvider
import com.smartscreenshot.domain.llm.LlmProviderType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the active [LlmProvider] from user settings: an explicit override wins,
 * otherwise AUTO prefers AICore when available and falls back to HTTP.
 */
@Singleton
class LlmProviderSelector
@Inject
constructor(
  private val settings: SettingsRepository,
  private val aiCore: AICoreLlmProvider,
  private val http: HttpLlmProvider,
) {

  suspend fun select(): LlmProvider =
    when (settings.current().providerMode) {
      LlmProviderType.AICORE -> aiCore
      LlmProviderType.HTTP -> http
      LlmProviderType.AUTO -> if (aiCore.isAvailable()) aiCore else http
    }
}
