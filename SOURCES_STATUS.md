# Job Sources Status

Last investigated: 2026-06-12

## Active Sources (working)

| Source | Type | Endpoint | Notes |
|--------|------|----------|-------|
| **TheProtocol** | REST API | Proprietary | ~48 results per run |
| **Remotive** | RSS | `https://remotive.com/remote-jobs/rss` | Works, small feed |
| **WeWorkRemotely** | RSS | `https://weworkremotely.com/categories/remote-programming-jobs.rss` | 25 results, ETag supported |
| **RemoteOK** | REST API | `https://remoteok.com/api?tag={keyword}` | Single tag only — `tags=` (plural) returns only legal notice |
| **NoFluffJobs** | REST API (POST) | `POST https://nofluffjobs.com/api/search/posting` | See below |
| **JustJoin.it** | REST API | `https://api.justjoin.it/v2/user-panel/offers` | Needs `Version: 2` header, see below |
| **RocketJobs.pl** | REST API | `https://api.rocketjobs.pl/v2/user-panel/offers` | Same platform/API as JustJoin.it (v2, `Version: 2` header). Offer URL: `rocketjobs.pl/oferta-pracy/{slug}`. First run: 93 found |
| **Bulldogjob.pl** | GraphQL | `POST https://bulldogjob.pl/graphql` | `searchJobs(page, perPage, filters:{skills:[...]})`. Introspection off — schema discovered via error messages. No free-text search, keyword → skills filter. Job URL: `bulldogjob.pl/companies/jobs/{id}` (id is slug). First run: 50 found, 4 saved |
| **Dice.com** | REST API | `https://job-search-api.svc.dhigroupinc.com/v1/dice/jobs/search` | Frontend's public `x-api-key` (override: `crawler.dice.api-key`). `q`, `page`, `pageSize` (max 100), `filters.postedDate=SEVEN`. Don't pass `fields=` — returns empty objects. First run: 620 found, 3 saved |
| **Relocate.me** | HTML (Jsoup) | `https://relocate.me/international-jobs?page=N` | Static HTML, 20 jobs/page, no keyword search (filter in pipeline). Cards: `div.jobs-list__job`, title in `.job__title a b`, info cells `.job__info .job__company p` = [country, company]. Mostly onsite-with-relocation → score 0 with REMOTE/HYBRID-only profiles. First run: 32 found |

### NoFluffJobs POST API
```
POST https://nofluffjobs.com/api/search/posting?limit=100&offset=0&salaryCurrency=PLN&salaryPeriod=month&region=pl
Content-Type: application/json

{"page": 1, "criteriaSearch": {"requirement": ["java"]}}
```
- Returns up to 3636+ results for Java
- Response: `{postings: [...], totalCount: N}`
- Job URL: `https://nofluffjobs.com/pl/job/{posting.url}`
- Remote: `posting.fullyRemote` (boolean)
- Skills in: `posting.tiles.values[].value`
- No auth required, but requires all 4 query params

---

## Disabled Sources (broken)

### LinkedIn (`linkedin`) — DISABLED
- **Problem**: LinkedIn removed all public RSS/API access years ago
- The adapter URL `https://www.linkedin.com/jobs/search/?...&format=json` returns 181KB of HTML
- Rome RSS parser gets a parse exception → 0 results
- **Future options**: LinkedIn API requires official OAuth approval (restricted program)

### JustJoin.it (`justjoinit`) — WORKING (fixed 2026-06-12)
- **Fixed**: migrated to API v2 — `GET https://api.justjoin.it/v2/user-panel/offers`
- Requires `Version: 2` header; `perPage` max 100; `page` starts at 1
- `keywords[]` params combine with AND — adapter runs one paginated query per keyword (max 3 keywords × 5 pages), dedups by `guid`
- Response: `{data: [...], meta: {totalItems, totalPages, nextPage}}`
- Job URL: `https://justjoin.it/job-offer/{slug}`
- Skills in `requiredSkills`/`niceToHaveSkills` arrays; salary in `employmentTypes[].from/to/currency`; remote via `workplaceType`
- First run: fetched 767, saved 36 Java matches

### NoFluffJobs RSS (`/feed.xml`) — REPLACED
- Old RSS feed `https://nofluffjobs.com/feed.xml` now redirects to Angular HTML page
- Replaced with POST API (see above)

---

## Never Implemented (P1/P2, disabled in DB)

Re-investigated 2026-06-12:

- **Reed.co.uk** — has official Jobseeker API (`https://www.reed.co.uk/api/1.0/search`) but requires free API key (401 without). Easy win if key registered.
- **Pracuj.pl** — no accessible public API found (`massachusetts.pracuj.pl/api/offers` and `/graphql` both 404); needs Playwright scraping.
- **Arc.dev** — no public API found (`/api/v1.1/public/remote-jobs` 404); JS-rendered listing.
- **Wellfound, Glassdoor, Indeed, Hired, StepStone, CWJobs** — bot-protected / JS-heavy HTML, high detection risk, no public API.

---

## RemoteOK: Important Fix
API parameter is **`tag`** (singular), not `tags`:
- ✅ `https://remoteok.com/api?tag=java` → 56+ results
- ❌ `https://remoteok.com/api?tags=java,kotlin` → returns only legal notice (400 bytes)