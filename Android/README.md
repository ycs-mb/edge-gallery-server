# Google AI Edge Gallery (Android)

## Local OpenAI-compatible bridge

This fork starts a foreground service that listens on `http://127.0.0.1:8080`.

- `GET /health`
- `GET /v1/models`
- `POST /v1/chat/completions`

The bridge forwards prompts into the app's live on-device LiteRT LM chat runtime. To answer
requests, first open the app, initialize a local LLM such as Gemma 4 in **AI Chat**, and keep the
app running so the foreground service can stay alive while the screen is off.
