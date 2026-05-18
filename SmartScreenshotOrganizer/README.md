# Smart Screenshot Organizer

Offline-first Android app that automatically organizes screenshots using
on-device LLM inference. All screenshot analysis happens locally — no cloud
APIs, no telemetry.

## Architecture

Single-module app, Clean Architecture by package boundary, MVVM UI.

```
ui/        Compose screens + ViewModels (Home, Detail, Settings)
domain/    Models, LlmProvider abstraction, use cases (index, hybrid search)
data/      Room, DataStore settings, MediaStore source, OCR, LLM providers,
           embeddings, repository
work/      WorkManager indexing (periodic sweep + ContentObserver)
di/        Hilt modules
```

### Inference (dual mode)

`LlmProvider` is the abstraction. `LlmProviderSelector` resolves the active
provider from settings:

- **AICore** (`AICoreLlmProvider`) — Gemini Nano via ML Kit GenAI Prompt.
  Preferred in AUTO mode when the device supports it.
- **HTTP fallback** (`HttpLlmProvider`) — POSTs OpenAI-compatible
  `/v1/chat/completions` to a configurable endpoint (default
  `http://10.0.2.2:8080/v1/chat/completions`, which reaches the host loopback
  from an emulator; point it at the Edge Gallery `LlmHttpServer` or any
  OpenAI-compatible server).

OCR text is the canonical LLM input for **both** providers (the reference HTTP
server is non-streaming and discards images). Neither path guarantees JSON, so
`JsonExtraction` tolerantly parses fenced/prefixed output, with one repair
retry and a `NEEDS_REVIEW` fallback record.

### Pipeline

MediaStore screenshot → ML Kit OCR (offline) → LLM analysis (strict JSON:
title, summary, category, tags, detected_apps, important_text, priority_score)
→ MediaPipe text embedding → Room.

### Search (hybrid)

Room FTS4 keyword candidates re-ranked by embedding cosine similarity
(`0.4 * ftsRank + 0.6 * cosine`). When the keyword set is thin, a full
embedding scan keeps conceptual queries working. Browse lists use Paging 3.

### Background indexing

`WorkScheduler` enqueues a periodic 6h MediaStore sweep (source of truth,
survives process death) plus a cold-start sweep. `ScreenshotObserver`
(ContentObserver) enqueues a deduplicated one-shot pass while the app is alive.

## Setup

1. Open `SmartScreenshotOrganizer/` in Android Studio (JDK 21, minSdk 33).
2. **Embedding model**: download a MediaPipe-compatible Universal Sentence
   Encoder model and place it at
   `app/src/main/assets/universal_sentence_encoder.tflite`. If absent, the app
   still runs — semantic search degrades to FTS-only (no crash).
3. **HTTP fallback (optional)**: run an OpenAI-compatible server and set its
   URL in Settings. To use the in-repo Edge Gallery server, start its
   `LlmServerService` and point the endpoint at `http://<device-ip>:8080/v1/chat/completions`.
4. Build: `./gradlew :app:assembleDebug`.

CI (`.github/workflows/build_screenshot_organizer.yaml`) runs unit tests and
builds debug + release APKs on every push to `feature/screenshot-organizer`.

## Permissions

| API | Permissions |
|-----|-------------|
| 33  | `READ_MEDIA_IMAGES` |
| 34+ | `READ_MEDIA_IMAGES` + `READ_MEDIA_VISUAL_USER_SELECTED` |

"Selected photos only" (partial access) is detected and surfaced with a banner,
since background sweeps silently miss screenshots in that mode.

## Testing

- Unit: `./gradlew testDebugUnitTest` — JSON extraction tolerance, cosine ranking.
- Instrumented: `./gradlew connectedDebugAndroidTest` — Room + FTS4 DAO.

## Privacy

On-device OCR (ML Kit bundled), on-device embeddings (MediaPipe), on-device or
local-network LLM only. No analytics, no third-party network calls except the
user-configured HTTP endpoint.
