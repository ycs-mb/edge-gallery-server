# Screenshot Analysis Prompts

The exact prompt strings live in
`app/src/main/java/com/smartscreenshot/domain/PromptTemplates.kt`. This document
shows them and sample I/O for both inference providers.

## System prompt

```
You analyze a phone screenshot's OCR text. Return ONLY one JSON object. No prose,
no explanations, no markdown code fences.
```

## User prompt (template)

```
Screenshot OCR text:
"""
<OCR text, clipped to 6000 chars>
"""

Return a JSON object with EXACTLY these keys:
{
  "title": string (<= 8 words),
  "summary": string (1-2 sentences),
  "category": one of [Shopping|Receipts|Chat|Social Media|Banking|Travel|Work|Coding|Memes|Documents|Other],
  "tags": array of 3-6 short lowercase strings,
  "detected_apps": array of app or website names visible in the text,
  "important_text": array of key snippets (codes, amounts, names, dates),
  "priority_score": integer 0-10 (10 = highly important to keep)
}
```

## Repair prompt (sent once if the first reply fails to parse)

```
Your previous answer was not valid JSON. Return ONLY the JSON object, no
markdown, no commentary.
```

## Sample — HTTP provider (OpenAI-compatible)

Request body to `POST /v1/chat/completions`:

```json
{
  "model": "local",
  "messages": [
    { "role": "system", "content": "You analyze a phone screenshot's OCR text. Return ONLY one JSON object. No prose, no explanations, no markdown code fences." },
    { "role": "user", "content": "Screenshot OCR text:\n\"\"\"\nStarbucks\nGrande Latte  $4.95\nVisa ****1234\n2024-05-01 08:31\n\"\"\"\n\nReturn a JSON object with EXACTLY these keys: ..." }
  ],
  "temperature": 0.2,
  "max_tokens": 512,
  "stream": false
}
```

Expected model content (`choices[0].message.content`):

```json
{
  "title": "Starbucks Latte Receipt",
  "summary": "Receipt for a grande latte paid with Visa ending 1234.",
  "category": "Receipts",
  "tags": ["coffee", "receipt", "starbucks"],
  "detected_apps": ["Starbucks"],
  "important_text": ["$4.95", "Visa ****1234", "2024-05-01 08:31"],
  "priority_score": 6
}
```

## Sample — AICore (Gemini Nano)

The same system + user text is concatenated into a single `TextPart`
(optionally with one `ImagePart` for the screenshot bitmap). Streamed tokens
are accumulated, then passed through the same `JsonExtraction` parser. A
well-behaved response is identical in shape to the HTTP sample above.

## Tolerant parsing

`JsonExtraction` handles:

- Markdown fences: ```` ```json … ``` ````
- Leading/trailing prose around the object
- Nested/escaped braces inside string values
- Unknown `category` → `Other`
- Out-of-range `priority_score` → clamped to 0–10
- Unparseable output → `AnalysisResult.fallback(...)` with `needsReview = true`
  (row marked `NEEDS_REVIEW`, never drops the screenshot)
