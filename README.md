# Meridian

AI-powered collaborative trip planning. A group plans one trip in a shared space; an AI learns each traveler's style, reconciles the group's preferences and proposes ad-free, non-touristy itineraries that the group votes on and edits.

> Status: **Milestone 5 (itinerary)**: a day-by-day draft from the shortlist, grouped by area with travel times, ordered by AI one day at a time and checked by code, with a map of each day's route, organizer edits, change proposals from members and comments. Earlier: group fit, place suggestions and price comparison; places and a shared shortlist with a map; trips and invite links; onboarding quiz with AI travel-style profile.

## Run with Docker

```bash
cp .env.example .env        # optional; compose has working defaults
docker compose up --build
```

- Frontend: http://localhost:3000
- API: http://localhost:8080/api/health

## Run locally (dev)

Requirements: JDK 21, Maven 3.9+, Node 22+, Docker (for Postgres and the tests).

```bash
docker compose up -d postgres
mvn -f backend spring-boot:run          # API on :8080
npm --prefix frontend install
npm --prefix frontend run dev           # UI on :3000
```

## AI model

AI runs locally via [Ollama](https://ollama.com) (OpenAI-compatible API). Set `AI_MODEL` in `.env` to a model you have pulled, e.g.

```bash
ollama pull qwen3:4b-instruct-2507
node scripts/ai-benchmark.mjs      # tokens/s + JSON validity → docs/AI_DESIGN.md
```

With `AI_ENABLED=false` (or Ollama stopped) every AI feature falls back to deterministic rules.

## Places and maps

Place data comes from OpenStreetMap, with no API keys. Nominatim answers searches. Overpass answers "nearby" lookups, falling back to Nominatim category searches when the public Overpass servers are busy. Every answer is stored in Postgres and reused for 7 days. If OpenStreetMap is unreachable, the stored results are shown and marked as stale. `OVERPASS_URL` takes a comma-separated list of mirrors, tried in order, and `NOMINATIM_URL` can point at a self-hosted instance. Map tiles are OpenStreetMap raster tiles rendered with MapLibre GL.

Pasted links are fetched server-side only to read the page title. Private and local addresses are refused, and every redirect is re-checked; at most 3 redirects, 5 s and 512 KB are allowed.

## Group fit, suggestions and compare

- **Group fit** shows who has filled in preferences and reacted, a matrix of everyone's pace, interests, budgets, crowd tolerance, must-haves and needs (icon, word and colour per status), and a fit score for every shortlisted place with who it works for and why. Budget amounts and levels are only visible to their owner unless shared; the group sees the shared range.
- **Suggestions** (Shortlist tab) ranks OpenStreetMap places for the whole group. The hidden-gems slider and "avoid crowds" re-rank instantly; "Re-rank with AI" re-orders the top ten and explains each in a background job. Nothing commercial is used for ranking.
- **Compare** lists options per provider with price, currency, when it was checked (flagged after 24 hours), the source link and a "Sponsored" label when a member marks one. Pasting a booking link prefills provider, title and, when the page title shows one, the price. Booking always happens on the provider's site.

All scoring rules are in `docs/AI_DESIGN.md`. With `AI_ENABLED=false` every number and ranking is identical; only the AI-written sentences are replaced by templates.

## Itinerary

The organizer generates a draft for the trip's dates. Code groups shortlisted places into days by area, picks the group's pace (the median of members' profiles, or the organizer's choice), works out travel times and leaves out what doesn't fit, with a reason. The AI then orders each day and suggests start times and a short note per stop. Code checks every time against travel and the day's hours, keeps meals at lunch or dinner time, and fixes anything that doesn't fit. Each day's "Why?" popover lists what was fixed. Days can be regenerated one at a time.

The organizer can reorder stops, move them between days, remove them or add unscheduled places; times are recomputed. Members propose the same changes, with a reason, and anyone can comment on a stop or a proposal. Voting on proposals and locking the plan come in the next milestone.


```bash
mvn -f backend verify        # unit + Testcontainers integration tests (needs Docker)
npm --prefix frontend run lint
```

## Layout

```
backend/    Spring Boot API (Java 21, Flyway, JWT)
frontend/   Next.js App Router + Tailwind + shadcn/ui
docs/       AI_DESIGN.md
scripts/    seed + ai-benchmark (later milestones)
```
