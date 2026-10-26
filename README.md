# Meridian

AI-powered collaborative trip planning. A group plans one trip in a shared space; an AI learns each traveler's style, reconciles the group's preferences and proposes ad-free, non-touristy itineraries that the group votes on and edits.

> Status: **Milestone 1 (foundation)**: auth, health check, app skeletons.

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
