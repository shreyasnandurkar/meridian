# Meridian

AI-powered collaborative trip planning. A group plans one trip in a shared space; an AI learns each traveler's style, reconciles the group's preferences and proposes ad-free, non-touristy itineraries that the group votes on and edits.

> Status: **Milestone 3 (places & shortlist)**: OpenStreetMap place search and nearby browsing (cached in Postgres), a shared shortlist built by search, pasted link or map pin, with attribution, reactions and a MapLibre map. Earlier: trips, invite links, roles, onboarding quiz with AI travel-style profile (A1).

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

## Tests

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
