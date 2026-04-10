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

package com.google.ai.edge.gallery.http

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "AGLocalHttpBridge"
private const val SERVER_HOST = "127.0.0.1"
private const val SERVER_PORT = 8080
private const val NOTIFICATION_CHANNEL_ID = "edge_gallery_http_bridge"
private const val NOTIFICATION_ID = 8080
private const val COMPLETION_TIMEOUT_MINUTES = 10L

class LocalHttpBridgeService : Service() {
  private var server: EdgeGalleryHttpServer? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    startForeground(NOTIFICATION_ID, buildNotification())
    startServerIfNeeded()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startServerIfNeeded()
    return START_STICKY
  }

  override fun onDestroy() {
    server?.stop()
    server = null
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startServerIfNeeded() {
    if (server != null) {
      return
    }
    server =
      try {
        EdgeGalleryHttpServer().also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start local HTTP bridge", e)
        null
      }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(
      NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "Edge Gallery Local API",
        NotificationManager.IMPORTANCE_LOW,
      )
    )
  }

  private fun buildNotification() =
    NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Edge Gallery local API is running")
      .setContentText("Listening on http://127.0.0.1:$SERVER_PORT/v1/chat/completions")
      .setOngoing(true)
      .build()

  companion object {
    fun start(context: Context) {
      ContextCompat.startForegroundService(
        context,
        Intent(context, LocalHttpBridgeService::class.java),
      )
    }
  }
}

private class EdgeGalleryHttpServer : NanoHTTPD(SERVER_HOST, SERVER_PORT) {
  override fun serve(session: IHTTPSession): Response {
    return try {
      when {
        session.method == Method.OPTIONS -> emptyResponse(Status.NO_CONTENT)

        session.method == Method.GET && session.uri == "/health" -> {
          jsonResponse(
            Status.OK,
            buildJsonObject {
              addProperty("status", "ok")
              addProperty("models_ready", ActiveLlmModelRegistry.list().size)
            }
          )
        }

        session.method == Method.GET && session.uri == "/v1/models" -> {
          val models =
            JsonArray().apply {
              for (model in ActiveLlmModelRegistry.list()) {
                add(
                  buildJsonObject {
                    addProperty("id", model.name)
                    addProperty("object", "model")
                    addProperty("display_name", model.displayName.ifEmpty { model.name })
                  }
                )
              }
            }
          jsonResponse(
            Status.OK,
            buildJsonObject {
              addProperty("object", "list")
              add("data", models)
            }
          )
        }

        session.method == Method.POST && session.uri == "/v1/chat/completions" -> {
          handleChatCompletion(session)
        }

        else -> jsonError(Status.NOT_FOUND, "Route not found.")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to serve request", e)
      jsonError(Status.INTERNAL_ERROR, e.message ?: "Unknown error")
    }
  }

  private fun handleChatCompletion(session: IHTTPSession): Response {
    val requestJson = parseRequestBody(session)
    val stream = requestJson.getBooleanOrDefault("stream", false)
    val requestedModel = requestJson.getStringOrNull("model")
    val model = ActiveLlmModelRegistry.get(requestedModel)
    if (model == null) {
      return jsonError(
        Status.CONFLICT,
        "No initialized Edge Gallery LLM is available. Open AI Chat, load Gemma 4, and keep the app running before calling the local API.",
      )
    }

    val prompt = buildPrompt(requestJson.getAsJsonArray("messages"))
    if (prompt.isBlank()) {
      return jsonError(Status.BAD_REQUEST, "Request must include at least one text message.")
    }

    val completionId = "chatcmpl-local-${System.currentTimeMillis()}"
    val created = System.currentTimeMillis() / 1000
    val enableThinking =
      requestJson.getJsonObjectOrNull("extra_body")?.getBooleanOrDefault("enable_thinking", false)
    val extraContext = if (enableThinking == true) mapOf("enable_thinking" to "true") else null

    return if (stream) {
      streamCompletion(
        modelName = model.name,
        completionId = completionId,
        created = created,
        prompt = prompt,
        extraContext = extraContext,
        model = model,
      )
    } else {
      completeOnce(
        modelName = model.name,
        completionId = completionId,
        created = created,
        prompt = prompt,
        extraContext = extraContext,
        model = model,
      )
    }
  }

  private fun completeOnce(
    modelName: String,
    completionId: String,
    created: Long,
    prompt: String,
    extraContext: Map<String, String>?,
    model: Model,
  ): Response {
    if (!REQUEST_SEMAPHORE.tryAcquire()) {
      return jsonError(Status.TOO_MANY_REQUESTS, "Another local inference request is still running.")
    }

    val text = StringBuilder()
    val failure = AtomicReference<String?>(null)
    val doneSignal = CountDownLatch(1)
    LlmChatModelHelper.runIsolatedTextInference(
      model = model,
      input = prompt,
      extraContext = extraContext,
      resultListener = { partialResult, done, _ ->
        if (partialResult.isNotEmpty()) {
          text.append(partialResult)
        }
        if (done) {
          doneSignal.countDown()
        }
      },
      onError = { message ->
        failure.set(message)
        doneSignal.countDown()
      },
    )

    val finished = doneSignal.await(COMPLETION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
    REQUEST_SEMAPHORE.release()
    if (!finished) {
      return jsonError(Status.REQUEST_TIMEOUT, "Timed out waiting for the on-device response.")
    }
    val error = failure.get()
    if (!error.isNullOrBlank()) {
      return jsonError(Status.INTERNAL_ERROR, error)
    }

    return jsonResponse(
      Status.OK,
      buildJsonObject {
        addProperty("id", completionId)
        addProperty("object", "chat.completion")
        addProperty("created", created)
        addProperty("model", modelName)
        add(
          "choices",
          JsonArray().apply {
            add(
              buildJsonObject {
                addProperty("index", 0)
                add(
                  "message",
                  buildJsonObject {
                    addProperty("role", "assistant")
                    addProperty("content", text.toString())
                  }
                )
                addProperty("finish_reason", "stop")
              }
            )
          },
        )
      },
    )
  }

  private fun streamCompletion(
    modelName: String,
    completionId: String,
    created: Long,
    prompt: String,
    extraContext: Map<String, String>?,
    model: Model,
  ): Response {
    if (!REQUEST_SEMAPHORE.tryAcquire()) {
      return jsonError(Status.TOO_MANY_REQUESTS, "Another local inference request is still running.")
    }

    val inputStream = PipedInputStream()
    val outputStream = PipedOutputStream(inputStream)
    fun writeChunk(payload: String) {
      outputStream.write(payload.toByteArray(Charsets.UTF_8))
      outputStream.flush()
    }

    LlmChatModelHelper.runIsolatedTextInference(
      model = model,
      input = prompt,
      extraContext = extraContext,
      resultListener = { partialResult, done, _ ->
        try {
          if (partialResult.isNotEmpty()) {
            writeChunk(
              "data: ${
                buildJsonObject {
                  addProperty("id", completionId)
                  addProperty("object", "chat.completion.chunk")
                  addProperty("created", created)
                  addProperty("model", modelName)
                  add(
                    "choices",
                    JsonArray().apply {
                      add(
                        buildJsonObject {
                          addProperty("index", 0)
                          add(
                            "delta",
                            buildJsonObject {
                              addProperty("content", partialResult)
                            }
                          )
                          add("finish_reason", JsonNull.INSTANCE)
                        }
                      )
                    },
                  )
                }
              }\n\n"
            )
          }
          if (done) {
            writeChunk(
              "data: ${
                buildJsonObject {
                  addProperty("id", completionId)
                  addProperty("object", "chat.completion.chunk")
                  addProperty("created", created)
                  addProperty("model", modelName)
                  add(
                    "choices",
                    JsonArray().apply {
                      add(
                        buildJsonObject {
                          addProperty("index", 0)
                          add("delta", JsonObject())
                          addProperty("finish_reason", "stop")
                        }
                      )
                    },
                  )
                }
              }\n\n"
            )
            writeChunk("data: [DONE]\n\n")
          }
        } finally {
          if (done) {
            REQUEST_SEMAPHORE.release()
            outputStream.close()
          }
        }
      },
      onError = { message ->
        try {
          writeChunk("data: ${buildJsonObject { addProperty("error", message) }}\n\n")
        } finally {
          REQUEST_SEMAPHORE.release()
          outputStream.close()
        }
      },
    )

    return newChunkedResponse(Status.OK, "text/event-stream", inputStream).apply {
      addHeader("Cache-Control", "no-cache")
      addHeader("Connection", "keep-alive")
      addCorsHeaders()
    }
  }

  private fun parseRequestBody(session: IHTTPSession): JsonObject {
    val files = mutableMapOf<String, String>()
    session.parseBody(files)
    return JsonParser.parseString(files["postData"].orEmpty()).asJsonObject
  }

  private fun buildPrompt(messages: JsonArray?): String {
    if (messages == null || messages.size() == 0) {
      return ""
    }
    return buildString {
      for (item in messages) {
        val message = item.asJsonObject
        val content = readMessageContent(message.get("content"))
        if (content.isBlank()) {
          continue
        }
        val role = message.getStringOrNull("role") ?: "user"
        append(role.uppercase())
        append(": ")
        append(content.trim())
        append("\n\n")
      }
      append("ASSISTANT:")
    }
  }

  private fun readMessageContent(content: JsonElement?): String {
    if (content == null || content.isJsonNull) {
      return ""
    }
    if (content.isJsonPrimitive) {
      return content.asString
    }
    if (content.isJsonArray) {
      return content.asJsonArray.joinToString(separator = "\n") { part ->
        val partObject = part.asJsonObject
        if (partObject.getStringOrNull("type") == "text") {
          partObject.getStringOrNull("text").orEmpty()
        } else {
          ""
        }
      }
    }
    return ""
  }

  private fun jsonResponse(status: Status, body: JsonObject): Response {
    return newFixedLengthResponse(status, "application/json", body.toString()).apply {
      addHeader("Content-Type", "application/json; charset=utf-8")
      addCorsHeaders()
    }
  }

  private fun emptyResponse(status: Status): Response {
    return newFixedLengthResponse(status, "text/plain", "").apply { addCorsHeaders() }
  }

  private fun jsonError(status: Status, message: String): Response {
    return jsonResponse(
      status,
      buildJsonObject {
        add(
          "error",
          buildJsonObject {
            addProperty("message", message)
            addProperty("type", "invalid_request_error")
          }
        )
      },
    )
  }
}

private val REQUEST_SEMAPHORE = Semaphore(1)

private fun buildJsonObject(block: JsonObject.() -> Unit): JsonObject {
  return JsonObject().apply(block)
}

private fun JsonObject.getStringOrNull(key: String): String? {
  if (!has(key)) {
    return null
  }
  val element = get(key)
  return if (element != null && element.isJsonPrimitive) element.asString else null
}

private fun JsonObject.getBooleanOrDefault(key: String, defaultValue: Boolean): Boolean {
  if (!has(key)) {
    return defaultValue
  }
  val element = get(key)
  return if (element != null && element.isJsonPrimitive) element.asBoolean else defaultValue
}

private fun JsonObject.getJsonObjectOrNull(key: String): JsonObject? {
  if (!has(key)) {
    return null
  }
  val element = get(key)
  return if (element != null && element.isJsonObject) element.asJsonObject else null
}

private fun NanoHTTPD.Response.addCorsHeaders() {
  addHeader("Access-Control-Allow-Origin", "*")
  addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
  addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
}
