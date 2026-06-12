---
name: new-source-adapter
description: Use when adding a new job source adapter to Jrawler (new job board, RSS feed, ATS, or career page source). Covers adapter class, registration, sources seed migration, and status docs.
---

# Adding a New Source Adapter

## Checklist

1. **Pick the base class** in `backend/src/main/java/com/jrawler/adapter/base/`:
   - JSON/REST API source -> `AbstractRestApiAdapter`
   - RSS/Atom feed -> `AbstractRssAdapter`
   - HTML scraping (Jsoup/Playwright) -> `AbstractWebCrawlerAdapter`
   - Company ATS (Greenhouse-like, per-company config) -> implement `AtsAdapter` in `adapter/ats/` and register it in `AtsAdapterFactory`

2. **Create the adapter class**:
   - Job boards go in `adapter/p0/`, ATS adapters in `adapter/ats/`.
   - Study 1–2 existing siblings first (e.g. `RemoteOkAdapter` for REST, `WeWorkRemotelyAdapter` for RSS) and match their structure exactly.
   - `getSourceId()` returns a lowercase id, e.g. `"remoteok"`.
   - `fetchJobs(SearchCriteria)` must NEVER throw — catch, log, return empty list.
   - Map fields into `RawVacancy` (`adapter/model/`); set `RemoteType` when the source provides it.

3. **Seed the source row** — new Flyway migration in `backend/src/main/resources/db/migration/`:
   - Next version number: `V{n}__add_{source}_source.sql`
   - `INSERT INTO sources (id, name, priority, is_enabled) VALUES ('{id}', '{Name}', 0, TRUE);`
   - `id` must equal `getSourceId()`. Never edit already-applied migrations.

4. **Verify registration** — `AdapterRegistry` discovers adapters as Spring beans; confirm the class has the right annotation matching siblings.

5. **Test manually**:
   - `docker compose up -d`, then from `backend/`: `mvn spring-boot:run`
   - Trigger: `curl -X POST http://localhost:8080/api/v1/crawler/run`
   - Check: `curl "http://localhost:8080/api/v1/crawler/logs?sourceId={id}"` and the app log (DEBUG for `com.jrawler`).

6. **Update docs**: mark the source in `SOURCES_STATUS.md` and `job_providers_to_process.md`.

## Gotchas

- Rate limiting / politeness: copy timeout and user-agent handling from sibling adapters; don't hammer sources.
- Pagination: only fetch what `SearchCriteria` needs; cap pages like siblings do.
- Dedup happens downstream (URL + first 500 chars of description hash) — don't dedup inside the adapter.
