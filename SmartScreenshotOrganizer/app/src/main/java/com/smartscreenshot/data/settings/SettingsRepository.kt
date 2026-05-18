package com.smartscreenshot.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartscreenshot.domain.llm.LlmProviderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
  val providerMode: LlmProviderType = LlmProviderType.AUTO,
  val httpEndpoint: String = DEFAULT_ENDPOINT,
  val lastIndexedDateAdded: Long = 0L,
  val embeddingModelVersion: String = "",
) {
  companion object {
    const val DEFAULT_ENDPOINT = "http://10.0.2.2:8080/v1/chat/completions"
  }
}

@Singleton
class SettingsRepository @Inject constructor(private val context: Context) {

  private object Keys {
    val PROVIDER = stringPreferencesKey("provider_mode")
    val ENDPOINT = stringPreferencesKey("http_endpoint")
    val LAST_INDEXED = longPreferencesKey("last_indexed_date_added")
    val EMBED_VERSION = stringPreferencesKey("embedding_model_version")
  }

  val settings: Flow<AppSettings> =
    context.dataStore.data.map { p ->
      AppSettings(
        providerMode =
          runCatching { LlmProviderType.valueOf(p[Keys.PROVIDER] ?: "AUTO") }
            .getOrDefault(LlmProviderType.AUTO),
        httpEndpoint = p[Keys.ENDPOINT] ?: AppSettings.DEFAULT_ENDPOINT,
        lastIndexedDateAdded = p[Keys.LAST_INDEXED] ?: 0L,
        embeddingModelVersion = p[Keys.EMBED_VERSION] ?: "",
      )
    }

  suspend fun current(): AppSettings = settings.first()

  suspend fun setProviderMode(mode: LlmProviderType) =
    context.dataStore.edit { it[Keys.PROVIDER] = mode.name }

  suspend fun setHttpEndpoint(endpoint: String) =
    context.dataStore.edit { it[Keys.ENDPOINT] = endpoint.trim() }

  suspend fun setLastIndexedDateAdded(value: Long) =
    context.dataStore.edit { it[Keys.LAST_INDEXED] = value }

  suspend fun setEmbeddingModelVersion(version: String) =
    context.dataStore.edit { it[Keys.EMBED_VERSION] = version }
}
