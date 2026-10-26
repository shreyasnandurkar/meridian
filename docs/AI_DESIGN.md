# AI design

Meridian uses a small local model (Ollama, OpenAI-compatible API) only where reading or writing natural language adds something. Numbers, constraints, ranking and validation are deterministic code, and every AI feature has a rule-based fallback, so the app works fully with `AI_ENABLED=false`.

## AiClient

`com.meridian.ai.AiClient` is the only path to a model. `OpenAiCompatibleAiClient` implements it:

- **Endpoint:** `POST {AI_BASE_URL}/chat/completions` with `model`, `messages` and `temperature`, plus `response_format: {type: "json_schema", strict: true, schema}`. Any OpenAI-compatible host works unchanged (`AI_BASE_URL`, `AI_MODEL`, `AI_API_KEY`).
- **Validation:** output is bound to a Java record and checked by a feature-specific validator. If it's invalid, the validator's messages go back to the model as a correction turn. That gives **3 attempts at most (2 retries)** before `AiException(INVALID_OUTPUT)`.
- **Small-model hygiene:** `<think>` blocks and markdown fences are stripped before parsing, in case a model emits them despite the schema. Prompts use temperature 0.2–0.4 and few-shot examples, and stay under 4k tokens of context.
- **Concurrency:** a fair semaphore caps model calls to `AI_MAX_CONCURRENCY` (default 1). Each call has a timeout of `AI_TIMEOUT_SECONDS`.
- **Logging:** every attempt writes a row to `ai_call_log` (feature, prompt version, model, input hash, attempt, status `VALID|INVALID|TIMEOUT|ERROR`, prompt and completion tokens, latency, error).
- **Async jobs:** `AiJobService` runs AI work on virtual threads and tracks status, percent and message per job in memory (one-hour retention). Jobs can be cancelled, which interrupts the in-flight HTTP call. The UI polls `GET /api/ai/jobs/{id}` (WebSocket push arrives in Milestone 6).
- **Result cache:** `ai_cache` stores validated results by `sha256(feature | prompt version | model | canonical input JSON)`. The same input never calls the model twice.
- **Prompts** live in `backend/src/main/resources/prompts/*.md`. Each file has front matter (`version`, `temperature`) and `## system|user|assistant` sections; `{{placeholders}}` are filled at render time, and an unknown placeholder is an error.

## A1 — Travel-style profile

| Step | Who does it |
|---|---|
| Sliders, pace, budget bands, crowd tolerance, hidden-gems slider, dietary and access needs, typed must-haves and dealbreakers | **Code**: copied straight from the quiz; interest rating `r ∈ 0..4` → weight `r / 4` |
| Reading the free-text "describe your ideal trip" | **Model** (`profile-extract-v1`) → `extraInterests[{tag, weight, evidence}]`, `mustHaves`, `dealbreakers`, `summary` |
| Merging | **Code** (`ProfileBuilder.build`) |

Guardrails:

- The model may only use tags from the fixed 22-tag vocabulary. The schema has an `enum`, and the validator re-checks it.
- **Explicit answers win.** The model cannot change a rated interest or any structured field; it can only add unrated interests and extra must-haves and dealbreakers.
- **Repair before retry.** Inferred weights are clamped to `[0.3, 1]` (a mentioned interest is at least a "mild mention"; the 4B model occasionally emits 0). Cheap deterministic fixes are preferred over a 6-second retry.
- **Grounding.** Every inferred interest must quote `evidence` that appears verbatim in the traveler's text, otherwise it is dropped. The quotes are shown in the "Why?" popover.
- **Fallback.** With AI off, the model unreachable, a timeout, or three invalid answers, keyword rules (`Vocabulary.INTERESTS[].keywords`, whole-word matching) read the text instead, at weight 0.6, with the matched snippet as evidence. A template writes the summary. `provenance.source = "rules"`, and `note` says why.
- The user sees and can edit every field. Edits set `edited = true`, and retaking the quiz regenerates the profile.

## Benchmark

<!-- ai-benchmark:start -->
_Last run 2026-10-26 by `scripts/ai-benchmark` (10 runs of the A1 `profile-extract-v1` prompt after one warm-up)._

| Metric | Value |
|---|---|
| Model | `richardyoung/qwen3-4b-instruct-2507-abliterated:latest` |
| Host | Windows_NT 10.0.26200, AMD Ryzen AI 7 350 w/ Radeon 860M, 23 GB RAM |
| JSON-validity rate (first attempt, schema + vocabulary checks) | 100% (10/10) |
| Transport errors | 0 |
| Median generation speed | 14.7 tokens/s |
| Median latency per call | 6542 ms (p90 8545 ms) |
| Median prompt / completion tokens | 501 / 98 |

Tokens/s is completion tokens divided by wall-clock latency, so it includes prompt processing — it is the speed a user actually waits for.
<!-- ai-benchmark:end -->
