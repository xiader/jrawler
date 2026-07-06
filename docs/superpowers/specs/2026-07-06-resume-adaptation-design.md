# Resume Adaptation — Design

Date: 2026-07-06
Status: approved

## Summary

New dashboard feature: user provides a vacancy (URL or pasted text) and uploads their
resume (`.docx`); an LLM (Claude API) proposes paragraph-level rewrites that tailor the
resume to the vacancy; the user reviews a diff, accepts/rejects individual edits, and
downloads the adapted `.docx`. Ephemeral — nothing is stored in PostgreSQL; in-flight
state lives in Redis with a 1-hour TTL.

## Decisions (agreed during brainstorming)

- Separate dashboard page (`/adapt`), not a button on vacancy cards.
- Vacancy input: URL fetch (Jsoup, Playwright fallback) with editable textarea fallback
  for manual paste.
- Resume format: DOCX in, DOCX out. No PDF.
- Adaptation engine: Claude API via the official Java SDK (`com.anthropic:anthropic-java`).
- LLM may only rephrase existing content — no invented facts. Separately it returns
  suggestions ("vacancy asks for X, not mentioned in resume — add it if true").
- Diff page with per-edit accept/reject checkboxes.
- Resume is uploaded each time; no stored master resume.
- No adaptation history; state is ephemeral (Redis, TTL 1h).
- Single provider (Anthropic), no provider abstraction.
- Edit mapping strategy: **paragraph-indexed edits** — POI numbers the non-empty
  paragraphs, the LLM returns `{paragraphIndex, newText}` pairs, accepted edits are
  substituted back into the original document. The LLM cannot add, remove, or reorder
  paragraphs.

## Flow and API

All endpoints under `/api/v1/resume-adaptation`.

1. `POST /fetch-vacancy` `{url}` → `{text}`. Jsoup first; empty/suspiciously short
   result → Playwright render. Extracted text goes into an editable textarea.
2. `POST /` (multipart: `resume` file + `vacancyText`) →
   - POI parses non-empty paragraphs `[{index, text}]`
   - Claude API call (structured output)
   - edits validated, original docx bytes + edits stored in Redis
     (`adaptation:{uuid}`, TTL 1h)
   - response:
     ```json
     {
       "adaptationId": "uuid",
       "edits": [{"paragraphIndex": 12, "original": "...", "proposed": "..."}],
       "suggestions": ["..."]
     }
     ```
3. `POST /{id}/download` `{"acceptedIndexes": [12, 15]}` → applies accepted edits to
   the stored docx, streams `resume-adapted.docx`.

## Backend components

New package `com.jrawler.adaptation`:

| Class | Responsibility |
|---|---|
| `ResumeAdaptationController` (in `api/`) | The three endpoints above |
| `VacancyTextExtractor` | URL → clean text. Jsoup, Playwright fallback (pattern from `AbstractWebCrawlerAdapter`) |
| `DocxService` | POI `XWPFDocument`: extract non-empty paragraphs; apply edits preserving the style of the paragraph's first run |
| `ClaudeClient` | Thin wrapper over the official Anthropic Java SDK. Structured output via `outputConfig(Class)` |
| `AdaptationService` | Orchestration + validation of LLM edits (index in range, non-empty `proposed`, drop no-op edits) |
| `AdaptationStore` | RedisTemplate: docx bytes (base64) + edits JSON, key `adaptation:{uuid}`, TTL 1h |

New dependencies in `pom.xml`: `org.apache.poi:poi-ooxml`, `com.anthropic:anthropic-java`.

Config (`application.yml`):

```yaml
adaptation:
  api-key: ${ANTHROPIC_API_KEY:}
  model: ${ADAPTATION_MODEL:claude-opus-4-8}
  fetch-timeout-seconds: 30
```

No API key → the endpoints answer 503 with a clear message; the rest of the app is
unaffected. No Flyway migrations.

## LLM contract

**Input** (user message):

```
<vacancy>
{vacancy text}
</vacancy>

<resume>
[0] Ivan Ivanov
[1] Senior Java Developer
[5] Built microservices with Spring Boot...
</resume>
```

Numbers are the indexes of non-empty docx paragraphs.

**System prompt (essence):** you help adapt a resume to a vacancy. Rephrase only — never
invent experience, technologies, dates, or numbers. Only rewrite paragraphs genuinely
worth strengthening for this vacancy. Answer in the resume's language. Separately list
vacancy requirements not mentioned in the resume.

**Output** — structured output (JSON schema via `output_config.format`; in the Java SDK,
typed `outputConfig(Class)`):

```json
{
  "edits": [{"paragraphIndex": 5, "newText": "..."}],
  "suggestions": ["..."]
}
```

Request: `maxTokens` 16000, non-streaming. Backend validation: index in range, non-empty
`newText`, edits identical to the original are dropped.

## Frontend

New route `/adapt`, nav item "Адаптация резюме". One page, three states:

1. **Form** (`pages/AdaptPage.tsx`): URL field + fetch button → textarea (editable;
   fetch failure shows "couldn't download, paste manually" and leaves the textarea);
   `.docx` file input; "Адаптировать" button enabled when both inputs present; spinner
   during the LLM call (~10–30 s).
2. **Diff** (`AdaptationDiff.tsx`): one card per edit — original vs proposed side by
   side (stacked on mobile), word-level highlight via a small in-house diff helper
   (~30 lines, no new dependency); checkbox per card, all checked by default;
   "accepted N of M" counter; suggestions list below; "Скачать docx" and "Начать
   заново" buttons.
3. **Download:** POST with accepted indexes, response blob saved as
   `resume-adapted.docx` via a temporary `<a download>`.

API client lives in `frontend/src/api/`; mutations via TanStack Query; Tailwind styling
consistent with the rest of the dashboard.

## Error handling

| Situation | Response |
|---|---|
| Missing `ANTHROPIC_API_KEY` | 503 "Adaptation not configured: set ANTHROPIC_API_KEY" |
| URL fetch failed / empty text | 422 with reason → frontend suggests manual paste |
| File not docx / POI parse failure | 400 "Upload a .docx file" |
| File too large | 413, limit ~2 MB |
| Claude API failure / garbage response | 502 "LLM service unavailable, try again" |
| `adaptationId` expired (TTL 1h) | 410 "Session expired, start over" |

All errors logged; the feature is isolated from the crawler pipeline.

## Testing

- `DocxServiceTest` (the important one): paragraph extraction from a fixture docx,
  edit application, style preservation, round-trip (parse → apply → parse → assert).
- `AdaptationServiceTest`: LLM edit validation with a mocked `ClaudeClient` — index out
  of range, empty text, no-op edit.
- `VacancyTextExtractorTest`: extraction from a saved HTML fixture.
- No live Claude API calls in tests (cost + flakiness); end-to-end verified manually
  via the `running-jrawler` skill.
