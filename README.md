# Jrawler

Personal job aggregator that crawls 38+ sources — job boards, RSS feeds, company ATS systems and career pages — scores results against your search profiles, and surfaces the relevant ones in a React dashboard.

## Stack

| Layer | Tech |
|---|---|
| Backend | Java 21, Spring Boot 3.4.3 |
| Database | PostgreSQL 18, Redis 8.6 |
| Crawling | OkHttp 4.12, Rome 2.1 (RSS), Jsoup 1.18, Playwright 1.49 |
| Frontend | React 19, TypeScript, Vite 7, Tailwind 4 |
| Infra | Docker Compose, Flyway |

## Quick Start

### 1. Environment variables

```bash
cp .env.example .env
```

Edit `.env` — PostgreSQL and Redis already have working defaults.

```env
# PostgreSQL
POSTGRES_DB=jrawler_db
POSTGRES_USER=jrawler_user
POSTGRES_PASSWORD=supersecret
DB_HOST=localhost

# Redis
REDIS_HOST=localhost

# Telegram (optional, for notifications)
TELEGRAM_BOT_TOKEN=
TELEGRAM_BOT_USERNAME=
TELEGRAM_CHAT_ID=
```

### 2. Start infrastructure

```bash
docker compose up -d
```

Starts PostgreSQL on `:5432` and Redis on `:6379`. Flyway migrations run automatically on backend startup.

### 3. Start backend

```bash
cd backend
mvn spring-boot:run
```

Backend runs at `http://localhost:8080`.

### 4. Start frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at `http://localhost:5173` (proxies `/api` to `:8080`).

---

## Architecture

```
jrawler/
├── docker-compose.yml
├── .env.example
├── frontend/                     # React dashboard
└── backend/src/main/java/com/jrawler/
    ├── adapter/                  # Source adapters (RSS, REST API, Web, ATS)
    ├── company/                  # Companies and career pages
    ├── profile/                  # Search profiles (keywords, locations, remote)
    ├── vacancy/                  # Vacancies and statuses
    ├── processing/               # Pipeline: dedup -> normalize -> score
    ├── scheduler/                # Cron crawler (hourly)
    └── notification/             # Telegram bot
```

### Sources

- **Job boards:** RemoteOK, Remotive, JustJoinIt, WeWorkRemotely, LinkedIn, NoFluffJobs, TheProtocol
- **Company ATS:** Greenhouse, Lever, Workday, SmartRecruiters, BambooHR, Custom
- **Career pages:** arbitrary URLs via Jsoup/Playwright

### Processing pipeline

```
RawVacancy -> Deduplicator -> Normalizer -> RelevanceScorer -> DB
```

- **Deduplicator** — checks by URL and description hash (first 500 chars)
- **Normalizer** — normalizes fields to a unified format
- **RelevanceScorer** — matches against search profiles; scoring: +30 must-keyword in title, +10 in description, +5 nice-to-have, +15 remote match, +10 location match; exclude hit sets score to 0

---

## API

### Search profiles

```http
GET    /api/v1/profiles
POST   /api/v1/profiles
PUT    /api/v1/profiles/:id
DELETE /api/v1/profiles/:id
PATCH  /api/v1/profiles/:id/toggle
```

Example profile body:

```json
{
  "name": "Java Backend",
  "mustKeywords": ["Java", "Spring"],
  "niceKeywords": ["Kafka", "Docker"],
  "excludeKeywords": ["PHP", "1C"],
  "locations": ["remote", "Berlin"],
  "remoteTypes": ["FULL_REMOTE", "HYBRID"],
  "minScore": 30
}
```

### Sources

```http
GET   /api/v1/sources
PATCH /api/v1/sources/:id/toggle
```

### Companies

```http
GET    /api/v1/companies
POST   /api/v1/companies
PUT    /api/v1/companies/:id
DELETE /api/v1/companies/:id
```

### Vacancies

```http
GET   /api/v1/vacancies          # list with filters and pagination
GET   /api/v1/vacancies/:id
PATCH /api/v1/vacancies/:id/status
```

Query params for `GET /vacancies`: `sourceId`, `profileId`, `status`, `minScore`, `page`, `size`.

Vacancy statuses: `NEW` -> `INTERESTED` -> `APPLIED` -> `INTERVIEW` -> `OFFER` / `REJECTED`

### Crawler

```http
POST /api/v1/crawler/run          # trigger crawl of all active sources (202 Accepted, async)
GET  /api/v1/crawler/logs         # crawl history (?sourceId=&limit=)
```

---

## Configuration

All parameters via environment variables. `application.yml` reads them with defaults:

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `POSTGRES_DB` | `jrawler_db` | Database name |
| `POSTGRES_USER` | `jrawler_user` | Database user |
| `POSTGRES_PASSWORD` | `supersecret` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `SERVER_PORT` | `8080` | Backend port |
| `CRAWLER_ENABLED` | `true` | Enable scheduler |
| `TELEGRAM_BOT_TOKEN` | — | Telegram bot token |
| `TELEGRAM_BOT_USERNAME` | — | Bot username |
| `TELEGRAM_CHAT_ID` | — | Chat ID for notifications |

Crawler schedule in `application.yml`:

```yaml
crawler:
  schedule-cron: "0 0 * * * *"   # every hour
```

---

## Telegram notifications

1. Create a bot via [@BotFather](https://t.me/BotFather), get the token
2. Get your `chat_id` via [@userinfobot](https://t.me/userinfobot)
3. Fill in `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_CHAT_ID` in `.env`
4. Restart the backend

The bot sends new vacancies with score >= `minScore` from active profiles.

---

## Development

```bash
# Start infrastructure only
docker compose up -d

# Backend (hot reload via Spring DevTools)
cd backend
mvn spring-boot:run

# Build jar
mvn package -DskipTests

# Run jar
java -jar target/jrawler-*.jar

# Frontend dev server
cd frontend
npm run dev
```

Application logs — DEBUG for `com.jrawler`, WARN for Redis.
