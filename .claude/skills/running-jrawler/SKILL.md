---
name: running-jrawler
description: Use when you need to run, start, restart, or verify the Jrawler app locally (backend, frontend, or full stack) — including checking that a change works end to end.
---

# Running Jrawler Locally

## Order matters

1. **Infra** (PostgreSQL :5432, Redis :6379):
   ```bash
   docker compose up -d
   docker compose ps        # both must be healthy/running
   ```
   Requires `.env` (copy from `.env.example` if missing — defaults work).

2. **Backend** (from `backend/`):
   ```bash
   mvn spring-boot:run
   ```
   Runs at http://localhost:8080. Flyway migrations apply on startup. This blocks — run in background. Startup is ready when the log shows `Started JrawlerApplication`.

3. **Frontend** (from `frontend/`), only if the change touches UI:
   ```bash
   npm run dev
   ```
   Runs at http://localhost:5173, proxies `/api` to :8080. Also blocks — run in background.

## Quick health checks

```bash
curl http://localhost:8080/api/v1/sources         # backend up + DB reachable
curl -X POST http://localhost:8080/api/v1/crawler/run   # trigger crawl (202, async)
curl "http://localhost:8080/api/v1/crawler/logs?limit=10"
curl "http://localhost:8080/api/v1/vacancies?page=0&size=5"
```

## Notes

- App logs: console + `logs/` dir; `com.jrawler` at DEBUG.
- Crawler cron is hourly; for testing always trigger via the POST endpoint instead of waiting.
- Disable scheduler with `CRAWLER_ENABLED=false` if it interferes with debugging.
- Schema errors on startup usually mean entity/migration mismatch (`ddl-auto: validate`) — fix via a new Flyway migration, not by editing entities to match.
