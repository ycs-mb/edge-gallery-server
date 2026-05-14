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

package com.google.ai.edge.gallery.server

import com.google.ai.edge.gallery.data.AICoreModelPreference
import com.google.ai.edge.gallery.data.AICoreModelReleaseStage
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.data.createAICoreConfigs

object AICoreModelFactory {

  data class AICoreModelDef(
    val name: String,
    val displayName: String,
    val releaseStage: AICoreModelReleaseStage,
    val preference: AICoreModelPreference,
  )

  val AVAILABLE_MODELS = listOf(
    AICoreModelDef(
      "gemini-nano-fast-preview",
      "Gemini Nano 4 Fast [Preview, TPU]",
      AICoreModelReleaseStage.PREVIEW,
      AICoreModelPreference.FAST,
    ),
    AICoreModelDef(
      "gemini-nano-full-preview",
      "Gemini Nano 4 Full [Preview, TPU]",
      AICoreModelReleaseStage.PREVIEW,
      AICoreModelPreference.FULL,
    ),
    AICoreModelDef(
      "gemini-nano-fast-stable",
      "Gemini Nano Fast [Stable, TPU]",
      AICoreModelReleaseStage.STABLE,
      AICoreModelPreference.FAST,
    ),
    AICoreModelDef(
      "gemini-nano-full-stable",
      "Gemini Nano Full [Stable, TPU]",
      AICoreModelReleaseStage.STABLE,
      AICoreModelPreference.FULL,
    ),
  )

  fun createModel(name: String): Model? {
    val def = AVAILABLE_MODELS.find { it.name == name } ?: return null
    return Model(
      name = def.name,
      displayName = def.displayName,
      isLlm = true,
      runtimeType = RuntimeType.AICORE,
      aicoreReleaseStage = def.releaseStage,
      aicorePreference = def.preference,
      configs = createAICoreConfigs(),
    ).also { it.preProcess() }
  }

  fun createAllModels(): List<Model> = AVAILABLE_MODELS.map { createModel(it.name)!! }
}
