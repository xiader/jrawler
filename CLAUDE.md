# Jrawler

Personal job aggregator: crawls 38+ sources (job boards, RSS, company ATS, career pages), scores results against search profiles, shows them in a React dashboard. Single-user, self-hosted.

## Commands

```bash
docker compose up -d              # infra: PostgreSQL :5432, Redis :6379

# Backend (from backend/)
mvn spring-boot:run               # run with hot reload, http://localhost:8080
mvn package -DskipTests           # build jar
mvn test                          # run tests

# Frontend (from frontend/)
npm run dev                       # dev server, http://localhost:5173 (proxies /api -> :8080)
npm run build                     # tsc -b && vite build
npm run lint                      # eslint
```

Flyway migrations run automatically on backend startup. `.env` is read by docker-compose and the backend (see `.env.example`).

## Stack

- Backend: Java 21, Spring Boot 3.4.3, JPA (ddl-auto: validate — schema changes go through Flyway), PostgreSQL 18, Redis 8.6
- Crawling: OkHttp 4.12, Rome 2.1 (RSS), Jsoup 1.18, Playwright 1.49
- Frontend: React 19, TypeScript, Vite 7, Tailwind 4, TanStack Query 5, axios, react-router 7

## Architecture

Backend packages under `backend/src/main/java/com/jrawler/`:

| Package | Responsibility |
|---|---|
| `adapter/` | Source adapters. `JobSearchAdapter` interface, `AdapterRegistry`, base classes in `adapter/base/` (`AbstractRestApiAdapter`, `AbstractRssAdapter`, `AbstractWebCrawlerAdapter`), ATS adapters in `adapter/ats/` (Greenhouse, Lever, Workday, ...), job boards in `adapter/p0/` |
| `crawl/` | Crawl orchestration and crawl logs |
| `processing/` | Pipeline: `RawVacancy -> Deduplicator -> Normalizer -> RelevanceScorer -> DB` |
| `profile/` | Search profiles (must/nice/exclude keywords, locations, remote types, minScore) |
| `vacancy/` | Vacancies, status flow: NEW -> INTERESTED -> APPLIED -> INTERVIEW -> OFFER / REJECTED |
| `company/` | Companies and their career pages / ATS configs |
| `scheduler/` | Hourly cron crawl (`crawler.schedule-cron`, toggle via `CRAWLER_ENABLED`) |
| `notification/` | Telegram bot (optional, env-driven) |
| `source/` | Source entities; each adapter's `getSourceId()` must match a `sources.id` row |
| `api/` | REST controllers under `/api/v1/` (profiles, sources, companies, vacancies, crawler) |

Scoring: +30 must-keyword in title, +10 in description, +5 nice-to-have, +15 remote match, +10 location match; any exclude hit -> score 0.

## Conventions

- Adapters never throw from `fetchJobs` — log and return empty list.
- New adapter requires a seed row in `sources` via a new Flyway migration (`V{n}__*.sql` in `backend/src/main/resources/db/migration/`). Never edit applied migrations.
- Dedup: by URL and by hash of first 500 chars of description.
- All config via env vars with defaults in `application.yml`.
- Frontend talks to backend only through the Vite `/api` proxy; API client code lives in `frontend/src/`.
- `SOURCES_STATUS.md` and `job_providers_to_process.md` track which sources are done/pending — update them when adding or fixing a source.
