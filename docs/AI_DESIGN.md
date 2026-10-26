# AI design

Meridian uses a small local model (Ollama, OpenAI-compatible API) only where reading or writing natural language adds something. Numbers, constraints, ranking and validation are deterministic code, and every AI feature has a rule-based fallback, so the app works fully with `AI_ENABLED=false`.

## AiClient

`com.meridian.ai.AiClient` is the only path to a model. `OpenAiCompatibleAiClient` implements it:

- **Endpoint:** `POST {AI_BASE_URL}/chat/completions` with `model`, `messages` and `temperature`, plus `response_format: {type: "json_schema", strict: true, schema}`. Any OpenAI-compatible host works unchanged (`AI_BASE_URL`, `AI_MODEL`, `AI_API_KEY`).
- **Validation:** output is bound to a Java record and checked by a feature-specific validator. If it's invalid, the validator's messages go back to the model as a correction turn. That gives **3 attempts at most (2 retries)** before `AiException(INVALID_OUTPUT)`.
- **Small-model hygiene:** `<think>` blocks and markdown fences are stripped before parsing, in case a model emits them despite the schema. Prompts use temperature 0.2–0.4 and few-shot examples, and stay under 4k tokens of context.
- **Concurrency:** a fair semaphore caps model calls to `AI_MAX_CONCURRENCY` (default 1). Each call has a timeout of `AI_TIMEOUT_SECONDS`.
- **Logging:** every attempt writes a row to `ai_call_log` (feature, prompt version, model, input hash, attempt, status `VALID|INVALID|TIMEOUT|ERROR`, prompt and completion tokens, latency, error).
- **Async jobs:** `AiJobService` runs AI work on virtual threads and tracks status, percent and message per job in memory (one-hour retention). Jobs can be cancelled, which interrupts the in-flight HTTP call. The owner's UI polls `GET /api/ai/jobs/{id}` for the result; trip-scoped jobs also publish every progress step to the trip's WebSocket topic, so the whole group sees the AI working.
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

## A4 — Draft itinerary

Code does almost all of the work. The model only orders one day and suggests times and short notes.

1. **Stops.** Every shortlisted place or pin with a location becomes a stop: 90 min for sights, 75 for food, 150 for activities, 60 otherwise. Stays and links without a location are listed under "Not on the plan", with the reason.
2. **Pace.** The group's pace is the median of members' profile paces (an even split of different paces gives balanced). It sets the day window and the number of stops: slow 10:00–19:00 and 3 stops, balanced 09:30–20:00 and 4, packed 08:30–21:30 and 6. The organizer can override it.
3. **Days.** If there are more stops than `days × stops per day`, the lowest group-fit scores are left out, each with a reason. The rest are clustered by **size-capped complete-linkage agglomerative clustering**, which keeps merging the two groups whose farthest-apart members are closest while the merge still fits in a day. Days are then ordered west to east. Places far apart never share a day just to even out day sizes, and the same shortlist always gives the same days.
4. **Rule plan.** A nearest-neighbour route from the trip's destination (or the middle of all stops when the destination has no coordinates) runs through the non-meal stops; meals are inserted nearest to 13:00 or 19:30. If the day runs past its end, the stop with the lowest fit is dropped and recorded.
5. **Model (`itinerary-day-v2`, one call per day).** The model sees short ids `s1…sN` in rule order, each stop's category, duration and what it offers, all pairwise travel times, and the day window. It returns `{stops:[{id,start,note}], summary}`. JSON-schema output plus a validator: every id exactly once, `HH:MM` times, the day ends in time with travel, no invented numbers, length limits. Invalid output is retried at most twice, then the rule plan is used.
6. **Repair, not trust.** The model's order is kept, but code recomputes every time:
   - a start that leaves too little travel time from the previous stop is moved later and recorded ("Moved X from 10:00 to 11:15 to allow 15 min from Y");
   - meals start no earlier than 12:00, and a meal reached between 15:30 and 19:00 becomes dinner at 19:00;
   - a note that names an interest the place doesn't offer, or a time of day (morning, sunset, evening…) that the stop's scheduled midpoint doesn't fall in, is dropped;
   - a summary that does either is replaced by a template ("Starts at A, with a meal at M and ends at B.").
   Every repair is listed under "Fixed by code" in the day's "Why?" popover.
7. **Travel model.** Straight-line distance; ≤ 1.2 km is a walk (×1.25 detour at 4.5 km/h + 3 min), anything longer a ride (×1.35 detour at 24 km/h + 10 min). Legs round up to 5 min.
8. **Cache.** The raw model answer is cached per (day line, pace, stop ids). Repairs run when the cache is read, so tightened rules also fix cached plans. Regenerating a day bypasses the cache.
9. **Edits.** The organizer moves, swaps, removes and adds stops directly; members propose the same edits. Code re-times only the changed days, and a stop keeps its time only while the stop before it is unchanged. Regenerating the whole draft marks open proposals as out of date.

With `AI_ENABLED=false`, or after a timeout or invalid output, the rule plan is used with template summaries. Days, travel times and repairs are identical.

Found in the live check (qwen3 4B): the model asked for "evening photos" at an 11:15 stop, and it put a seafood dinner place at 09:30 on a day of its own. Both are now repaired by code, as described in step 6.

## Voting and the consensus gate (no AI)

Everything about a decision is code; the model only writes the log sentence afterwards (A6).

- **One vote per member, equal weight.** A vote can be changed or taken back while the proposal is open. Proposing a change counts as the proposer's approval.
- **Quorum.** Simple majority (`n/2 + 1`) unless the organizer sets a number of approvals, clamped to the group size.
- **Outcome as soon as it is certain.** Approved when approvals reach the quorum; rejected when so many members rejected that the rest can no longer reach it. An approved change is applied by code at once (same re-timing as an organizer edit). All of this runs under a row lock on the itinerary, so two last votes arriving together can't apply a change twice.
- **The gate.** Members approve the plan as it is now (`plan_approvals`, tied to the itinerary version). Any change makes earlier verdicts out of date, except that a change approved by vote carries forward the plan approval of members who voted for it. "Lock itinerary" is enabled only when the current version has quorum approval; the organizer can lock without it only by giving a reason, which is logged as an override. Locking closes open proposals.
- **Live.** Every change is published after commit to `/topic/trips/{id}` over STOMP, so the other windows refetch what changed; AI-job progress is published on the same topic.

## A6 — Decision summary

When a vote closes (a change approved or rejected) or the plan is locked, code appends an entry to `decision_log` with the alternatives, the votes as they stood, who had not voted, the counts and the reason, plus a **template summary** ("Chose to move X over keeping the plan as it was: 3 of 4 approved (3 needed). Voted against: Kabir."). The log is append-only: a database trigger rejects deletes and any update except to the summary columns.

After the commit an AI job (`decision-summary-v1`, temperature 0.2, two few-shot examples) gets the entry as fact lines: decision, change, alternatives, proposer and reason, quorum, `approved (3 of 4): …`, `rejected (1 of 4): …`, `did not vote: …`, and up to four comments from the proposal's discussion. It returns `{summary}` (≤ 280 characters).

- The summary must state the count exactly as given ("3 of 4" or "3/4"), and **any number not in the facts is rejected**; invalid output is retried at most twice, then the template stays.
- Each person appears once with their vote in the facts, so the model has nothing to mix up (the A3 lesson).
- Results are cached by facts hash. Any member can regenerate (skips the cache) or dismiss (puts the template back); the entry shows an AI or Rules badge and a "Why?" popover. Unlock entries are template-only.
- With `AI_ENABLED=false` the template summary is the entry; nothing else changes.

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
