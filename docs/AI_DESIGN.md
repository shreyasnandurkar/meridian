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

## Group fit — the numbers behind A2 and A3

All scoring is in `com.meridian.fit` and is plain code with unit tests.

**Place → interests (`PlaceInterests`).** OpenStreetMap tags are translated into the same 22-tag interest vocabulary as profiles, as strengths in [0, 1]. For example, `natural=beach` gives beaches 1.0 and nature 0.4; `craft=*` gives crafts 1.0 and local culture 0.5; a regional `cuisine` gives local food 1.0. A restaurant counts as local food (0.7) unless its cuisine tags, or failing those its name, say it serves food from elsewhere (pizza, sushi, Chinese and so on), in which case it gets only 0.2. Words in the name (and the title of a pasted link or pin) add weaker 0.5 signals. The category is the last resort.

**Member × place fit (`FitScorer`), 0–100.**

| Part | Rule |
|---|---|
| Interests | Strength-weighted average of the member's weights over what the place offers. An unrated interest counts as 0.25, and when a place's strengths add up to less than 1 the rest counts as unrated too, so a weak signal only gives a weak match. Base score `15 + 75 × match`. |
| Hidden gems | `TouristTrapScorer.gemAdjustment` with the member's slider (below). |
| Crowds | Crowd tolerance < 40 and fame ≥ 0.5 → up to −20. |
| Must-haves | A match by interest or by a name word adds +10. |
| Dealbreakers | A match **caps the score at 15**, whatever else matches. |
| Diet, access, stay budget | From OSM tags (`diet:*`, `wheelchair`, `stars`, hostel/guest house). `wheelchair=no` for a wheelchair user is a blocker. Missing data is shown as a note, not a penalty. |
| Reactions | What the member said beats the estimate: love → ≥ 90, OK → 50–80, no way → ≤ 10. |

Verdicts: ≥ 65 satisfies, < 40 misses, otherwise neutral. Every adjustment leaves a reason (plus, minus, blocker or note), and these reasons are what the "who it satisfies and why" breakdown shows.

**Group score per item** = `0.7 × mean + 0.3 × min` of the members who can be scored. Every member counts equally, and the least happy member pulls the score down so one person's dealbreaker is never averaged away.

**Matrix (`GroupFitCalculator`).** One row per attribute: pace, interests, the three budget bands, the private trip budget, crowds, hidden gems, must-haves vs dealbreakers, diet, access. Each row has a status:

- Pace and budget bands: one value → agree, adjacent values → trade-off, both extremes → conflict.
- Sliders: spread ≤ 25 → agree, ≤ 50 → trade-off, more → conflict.
- Interests: agree when at least two interests are liked (≥ 0.6) by everyone.
- Must-haves: conflict when one member's must-have (or a strongly loved interest, ≥ 0.75) maps to an interest named in someone else's dealbreaker.
- Diet and access: "plan around".
- Fewer than two profiles: "not enough data".

**Privacy.** Budget bands and trip budgets belong to their owner. Other members see "Private" unless the owner chose to share. The group sees only the band range and the shared trip-budget range (highest minimum to lowest maximum). The facts handed to A3 contain only anonymous counts and that shared range.

## Anti-tourist-trap scorer

`TouristTrapScorer` is an interface; `OsmTouristTrapScorer` is the implementation used today. It returns:

- `fame` = popularity proxy / 10. The proxy comes from Wikipedia (+4), Wikidata (+3), `tourism=attraction` (+2), a chain brand (+2) and many translated names (up to +3).
- `trap` = `min(1, 0.6 × fame + 0.35 × chain + 0.1 × attraction tag)`.
- `local` = regional cuisine (+0.4), a craft workshop (+0.4), a small guest house or camp site (+0.3), a market (+0.2), little-known heritage (+0.3), independent and little-known (+0.15), capped at 1.
- `signals`: the same evidence in words ("serves goan food", "part of a chain (Starbucks)"), used in "why" text.

The **hidden gems ↔ classics** slider `h` (0–100) scales its weight: `g = (h − 50) / 50`. If `g ≥ 0`, the adjustment is `g × (0.25 × local − 0.35 × trap)`. Otherwise it is `−g × 0.25 × fame`. At 50 it is neutral. Gem seekers see local places lifted and traps pushed down; classics fans see famous places lifted. **No commercial signal exists in the input**, so nothing paid can affect ranking. The `sponsored` field on suggestions is always false today and exists only so a flagged item could never appear without its label.

## A2 — Place recommendations

1. **Candidates (code).** Up to 60 places per category come from `PlacesClient` "nearby" (cached in Postgres). Places already on the shortlist are skipped. "Avoid crowds" drops chains and subtracts `30 × fame`.
2. **Score (code).** Each candidate gets the group score above, with the hidden-gems slider (default: the group's average setting) used for every member. The sort is score, then distance.
3. **Re-rank (model, `recommend-rerank-v1`).** The top 10 are listed as `c1…c10 | name | what it offers | local signals | code score`, together with a one-line group summary (shared interests, who likes what, dealbreakers, slider, crowds). The schema allows only those ids, and the validator requires an exact permutation. Code keeps the final say: the ranking is then repaired so no place moves more than 3 positions from its code rank, so a model that merely rotates the list cannot bury the best match.
4. **Explain (model, `recommend-explain-v2`).** In batches of 5, each place gets a ≤ 180-character "why this fits" written from its computed facts (what it offers, who it suits or not, local signals). The validator requires exactly the batch's ids, rejects any number not present in that place's facts or the group line, and the retry loop handles both. The explain step sees only the group's settings (traveler count, slider, crowds), not who likes what, and any sentence that still claims an interest the place's own facts don't mention ("a quiet beach with local food") is replaced by that place's template sentence while the rest of the batch is kept.
5. **Cache.** The key covers the category, slider, crowds toggle, group summary and the top-10 ids with their scores. `GET /recommendations` returns the cached AI set if one exists for exactly these inputs, otherwise the rule-based set. It never waits on the model. `POST /recommendations/ai` runs steps 3–4 as an async job.
6. **Fallback.** With AI off, a timeout or invalid output, the code ranking stands and template "why" text is used ("Offers history & heritage, which suits everyone. Heritage few visitors know."). The UI shows an AI or Rules badge, a "Why?" popover, Regenerate and Dismiss controls, and "AI moved it up from #4" when the order changed.

## A3 — Group trade-offs

Code computes every row of the matrix together with `facts` (for example "Asha is at 15: avoids crowds", "gap between lowest and highest: 75 points (a difference, not an average)", "stays budget levels (anonymous): 2 × Budget, 1 × Splurge") and a suggested compromise. The model (`group-tradeoffs-v2`) only rewrites the rows that are trade-offs, conflicts or plan-arounds, in batches of 5, as `{key, headline, explanation, suggestion}`.

- The `key` enum and the validator require exactly the given rows.
- **Any number in the output must appear in that row's facts**, otherwise the answer is rejected and retried.
- The model never sees a private budget amount, only anonymous counts and the shared range.
- Results are cached per facts hash, so the Group fit screen loads the cached text instantly. With no cache, or with AI off, the template sentences built from the same facts are shown.

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
