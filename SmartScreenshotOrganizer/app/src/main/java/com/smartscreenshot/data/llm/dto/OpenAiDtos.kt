package com.smartscreenshot.data.llm.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatCompletionRequest(
  val model: String = "local",
  val messages: List<ChatMessage>,
  val temperature: Double = 0.2,
  @SerialName("max_tokens") val maxTokens: Int = 512,
  val stream: Boolean = false,
)

@Serializable data class ChatChoiceMessage(val role: String = "assistant", val content: String = "")

@Serializable data class ChatChoice(val index: Int = 0, val message: ChatChoiceMessage = ChatChoiceMessage())

@Serializable
data class ChatCompletionResponse(
  val id: String = "",
  val model: String = "",
  val choices: List<ChatChoice> = emptyList(),
) {
  fun firstContent(): String = choices.firstOrNull()?.message?.content.orEmpty()
}
