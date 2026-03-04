# Job Sources Status

Last investigated: 2026-03-05

## Active Sources (working)

| Source | Type | Endpoint | Notes |
|--------|------|----------|-------|
| **TheProtocol** | REST API | Proprietary | ~48 results per run |
| **Remotive** | RSS | `https://remotive.com/remote-jobs/rss` | Works, small feed |
| **WeWorkRemotely** | RSS | `https://weworkremotely.com/categories/remote-programming-jobs.rss` | 25 results, ETag supported |
| **RemoteOK** | REST API | `https://remoteok.com/api?tag={keyword}` | Single tag only — `tags=` (plural) returns only legal notice |
| **NoFluffJobs** | REST API (POST) | `POST https://nofluffjobs.com/api/search/posting` | See below |

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

### JustJoin.it (`justjoinit`) — DISABLED
- **Problem**: Old `/api/offers` endpoint removed (HTTP 404)
- `public.justjoin.it` returns HTTP 403
- No other public API endpoints found
- Website is Next.js App Router (no `__NEXT_DATA__`)
- **Future option**: Playwright scraping of `https://justjoin.it/job-offers/java`

### NoFluffJobs RSS (`/feed.xml`) — REPLACED
- Old RSS feed `https://nofluffjobs.com/feed.xml` now redirects to Angular HTML page
- Replaced with POST API (see above)

---

## Never Implemented (P1/P2, disabled in DB)

- Wellfound, Arc.dev, Glassdoor, Indeed, Dice — HTML scraping, high bot-detection risk
- StepStone, CWJobs, Reed.co.uk — regional EU boards
- Pracuj.pl, Bulldogjob, Rocketjobs — Polish boards, need web scraping

---

## RemoteOK: Important Fix
API parameter is **`tag`** (singular), not `tags`:
- ✅ `https://remoteok.com/api?tag=java` → 56+ results
- ❌ `https://remoteok.com/api?tags=java,kotlin` → returns only legal notice (400 bytes)