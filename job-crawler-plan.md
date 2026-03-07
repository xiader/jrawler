# План: Job Vacancy Crawler System

## Контекст
- **Роль:** Java Software Engineer
- **Локация:** Люблин, Польша. Только remote или hybrid (max 1-2 дня/месяц в офисе)
- **Гео-фильтр:** Вакансии с возможностью работы из Европы (EU-remote, worldwide-remote, Poland-remote)
- **Список компаний:** частично есть, нужна возможность расширять

---

## Архитектура системы

```
┌──────────────────────────────────────────────────────────────┐
│                        ИСТОЧНИКИ (38)                        │
│  P0: LinkedIn · RemoteOK · WeWorkRemotely · Remotive         │
│      JustJoin.it · NoFluffJobs · TheProtocol · ATS-pages     │
│  P1: Wellfound · Arc.dev · Indeed · Glassdoor · Dice         │
│      Hired · StepStone · CWJobs · Reed · Pracuj + др.        │
│  P2: Otta · Landing.jobs · Xing · Monster · ZipRecruiter     │
│      YC Jobs · Talent.com + др.                              │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                  JAVA BACKEND (Spring Boot)                   │
│                                                              │
│  ┌────────────┐   ┌─────────────────────────────────────┐   │
│  │ Scheduler  │──▶│ AdapterRegistry                     │   │
│  │ (1ч/цикл) │   │ (38 JobSearchAdapter @Components)   │   │
│  └────────────┘   └──────────────┬──────────────────────┘   │
│                                  │ List<RawVacancy>          │
│                                  ▼                           │
│                   ┌──────────────────────────┐              │
│                   │ Processing Pipeline       │              │
│                   │  Normalizer               │              │
│                   │  → RelevanceScorer        │◀── Redis     │
│                   │     (per SearchProfile)   │    (ETag,    │
│                   │  → Deduplicator           │    rate lim) │
│                   └──────────────┬────────────┘              │
│                                  │ Vacancy                   │
│                                  ▼                           │
│  ┌────────────┐   ┌──────────────────────────┐              │
│  │ Company    │   │     PostgreSQL             │              │
│  │ Registry   │   │ vacancies · companies      │              │
│  └────────────┘   │ search_profiles · sources  │              │
│                   │ crawl_logs                 │              │
│  ┌────────────┐   └──────────────────────────┘              │
│  │ Telegram   │                                              │
│  │ Notifier   │   REST API (/api/v1/...)                     │
│  └────────────┘                                              │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│              FRONTEND (React + TypeScript + Vite)            │
│  /vacancies · /companies · /profiles · /dashboard · /settings│
└──────────────────────────────────────────────────────────────┘
```

---

## Хостинг

Выбор платформы — после имплементации MVP. Приложение упаковано в Docker Compose, что позволяет деплоить на любой VPS, Railway, Fly.io или аналоги без изменений кода.

---

## Схема базы данных

```sql
-- Вакансии
vacancies (
  id uuid PK,
  external_id varchar,          -- ID на стороне источника
  source_id varchar,            -- "remoteok", "linkedin", ...
  title varchar,
  company_name varchar,
  url varchar UNIQUE,
  location varchar,
  salary_raw varchar,           -- как есть: "70 000 - 90 000 PLN"
  remote_type varchar,          -- REMOTE | HYBRID | ON_SITE
  description text,
  description_hash varchar,     -- для дедупликации
  relevance_score int,          -- 0-100
  matched_keywords jsonb,       -- ["java", "spring", "kafka"]
  profile_id uuid FK,           -- какой SearchProfile матчнул
  status varchar DEFAULT 'NEW', -- NEW|INTERESTED|APPLIED|INTERVIEW|OFFER|REJECTED
  found_at timestamptz,
  created_at timestamptz
)

-- Источники (справочник)
sources (
  id varchar PK,                -- "remoteok", "linkedin"
  name varchar,
  priority int,                 -- 0=P0, 1=P1, 2=P2
  is_enabled bool,
  last_crawled_at timestamptz,
  last_etag varchar             -- для conditional requests
)

-- Компании (для прямого мониторинга карьерных страниц)
companies (
  id uuid PK,
  name varchar,
  career_page_url varchar,
  ats_type varchar,             -- greenhouse|lever|workday|smartrecruiters|bamboohr|custom
  ats_company_id varchar,       -- ID компании в ATS
  custom_selectors jsonb,       -- CSS-селекторы для кастомного парсинга
  is_active bool,
  last_crawled_at timestamptz
)

-- Профили поиска
search_profiles (
  id uuid PK,
  name varchar,                 -- "Java Backend", "Golang Engineer"
  is_active bool,
  must_have_keywords jsonb,     -- ["java"]
  nice_to_have_keywords jsonb,  -- ["spring", "kafka"]
  exclude_keywords jsonb,       -- ["php", "clearance"]
  locations jsonb,              -- ["Poland", "Europe", "Remote"]
  remote_types jsonb,           -- ["REMOTE", "HYBRID"]
  min_relevance_score int,      -- порог 0-100
  created_at timestamptz
)

-- Лог краулинга
crawl_logs (
  id uuid PK,
  source_id varchar FK,
  started_at timestamptz,
  finished_at timestamptz,
  vacancies_found int,
  vacancies_new int,
  error text
)
```

---

## Этапы реализации

### Этап 1: Фундамент

**1.1 — Spring Boot проект + инфраструктура**
- Spring Boot 3.x + Java 21
- PostgreSQL 18 + Flyway миграции (схема выше)
- Redis: ETag/If-Modified-Since кеш для адаптеров, rate limiting per domain
- Зависимости: Spring Web, Spring Data JPA, Spring Scheduler, Flyway, Redis

**1.2 — Локальная разработка**
- `docker-compose.yml` в корне — поднимает PostgreSQL + Redis
- Бэкенд запускается локально (`./mvnw spring-boot:run`), коннектится к Docker
- Конфигурация через `.env` (скопировать из `.env.example`)

```bash
docker compose up -d      # postgres + redis
./mvnw spring-boot:run    # бэкенд локально
```

**1.3 — Company Registry**
- CRUD: name, career_page_url, ats_type, ats_company_id, custom_selectors, is_active
- Импорт начального списка из CSV/JSON
- REST API: `GET/POST/PUT/DELETE /api/v1/companies`

---

### Этап 2: Adapter Engine

**2.1 — Интерфейсы и модели**

```java
public interface JobSearchAdapter {
    String getSourceId();                              // "remoteok", "linkedin"
    List<RawVacancy> fetchJobs(SearchCriteria criteria);
    boolean isEnabled();
}
```

```java
// Сырые данные из адаптера — до нормализации
public record RawVacancy(
    String sourceId,
    String externalId,
    String title,
    String companyName,
    String url,
    String location,
    String description,
    String salaryRaw,       // "70 000 - 90 000 PLN" — как есть
    String remoteTypeRaw,   // "full remote", "hybrid" — как есть
    Instant fetchedAt
) {}
```

```java
// Строится из union всех активных SearchProfile — адаптер делает широкий запрос
public record SearchCriteria(
    List<String> keywords,
    List<String> locations,
    List<RemoteType> remoteTypes
) {
    static SearchCriteria fromProfiles(List<SearchProfile> profiles) { ... }
}

public enum RemoteType { REMOTE, HYBRID, ON_SITE }
```

**2.2 — Абстрактные базовые классы**

```java
// REST API адаптеры (RemoteOK, Remotive, Greenhouse, Lever...)
public abstract class AbstractRestApiAdapter implements JobSearchAdapter {
    protected abstract String buildRequestUrl(SearchCriteria criteria);
    protected abstract List<RawVacancy> parseResponse(String responseBody);

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        // retry (3 попытки), логирование, метрики
    }
}

// RSS адаптеры (WeWorkRemotely, NoFluffJobs...)
public abstract class AbstractRssAdapter implements JobSearchAdapter {
    protected abstract String getRssFeedUrl(SearchCriteria criteria);
    protected abstract RawVacancy mapEntry(SyndEntry entry);

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        // If-Modified-Since / ETag через Redis
    }
}

// Web crawling адаптеры (Indeed, Glassdoor, Pracuj.pl...)
public abstract class AbstractWebCrawlerAdapter implements JobSearchAdapter {
    protected abstract String buildSearchUrl(SearchCriteria criteria);
    protected abstract List<RawVacancy> parseJobList(Document document);
    protected abstract boolean requiresJavaScript(); // false=Jsoup, true=Playwright

    @Override
    public List<RawVacancy> fetchJobs(SearchCriteria criteria) {
        // rate limiting per domain (Redis), user-agent rotation, robots.txt
    }
}
```

```java
// Spring автоматически собирает все @Component-адаптеры
@Component
public class AdapterRegistry {
    private final List<JobSearchAdapter> adapters;

    public List<JobSearchAdapter> getEnabled() {
        return adapters.stream().filter(JobSearchAdapter::isEnabled).toList();
    }
}
```

**2.3 — Адаптеры по приоритету**

🔴 **P0 — API/RSS (минимальный риск бана):**

| Источник | Базовый класс | Регион |
|----------|---------------|--------|
| LinkedIn | AbstractRssAdapter | Global |
| RemoteOK | AbstractRestApiAdapter | Global/Remote |
| WeWorkRemotely | AbstractRssAdapter | Global/Remote |
| Remotive | AbstractRestApiAdapter | Global/Remote |
| JustJoin.it | AbstractRestApiAdapter | Польша |
| NoFluffJobs | AbstractRssAdapter | Польша/EU |
| TheProtocol.it | AbstractWebCrawlerAdapter | Польша |
| Карьерные страницы | CompanyCareerPageAdapter | Global |

🟡 **P1 — HTML parsing:**

| Источник | Базовый класс | Регион |
|----------|---------------|--------|
| Wellfound | AbstractWebCrawlerAdapter | Global/USA |
| Arc.dev | AbstractWebCrawlerAdapter | Global/Remote |
| Glassdoor | AbstractWebCrawlerAdapter | Global |
| Indeed | AbstractWebCrawlerAdapter | Global |
| Dice.com | AbstractWebCrawlerAdapter | USA |
| Hired.com | AbstractWebCrawlerAdapter | USA/EU |
| StepStone | AbstractWebCrawlerAdapter | DE/NL/BE/UK |
| CWJobs | AbstractWebCrawlerAdapter | UK |
| Reed.co.uk | AbstractWebCrawlerAdapter | UK |
| Relocate.me | AbstractWebCrawlerAdapter | Global/Remote |
| Bulldogjob.pl | AbstractWebCrawlerAdapter | Польша |
| Rocketjobs.pl | AbstractWebCrawlerAdapter | Польша |
| Pracuj.pl | AbstractWebCrawlerAdapter | Польша |

🟢 **P2 — меньший объём или региональная специфика:**

| Источник | Регион |
|----------|--------|
| Otta | UK/EU |
| Landing.jobs | EU |
| Jobspresso | Remote |
| Remote.co | Remote |
| CareerVault | Global |
| WorkingNomads | Remote |
| 4programmers.net | Польша |
| Xing Jobs | DACH |
| EuroJobs | EU |
| Jobs.cz | Чехия |
| Totaljobs | UK |
| ZipRecruiter | USA |
| TheLadders | USA (senior) |
| Monster | USA/EU |
| YC Jobs | USA/Remote |
| Talent.com | Канада/Global |
| Workopolis | Канада |

Все источники: интервал **1 час**. Параллельный запуск через Virtual Threads (Java 21).

**2.4 — ATS-адаптеры для карьерных страниц компаний**

`CompanyCareerPageAdapter` определяет ATS компании и делегирует в нужный адаптер:

| ATS | Метод | Пример URL |
|-----|-------|------------|
| Greenhouse | REST API | `boards.greenhouse.io/{company}` |
| Lever | REST API | `jobs.lever.co/{company}` |
| Workday | JSON API | `{company}.wd5.myworkdayjobs.com` |
| SmartRecruiters | REST API | `jobs.smartrecruiters.com/{company}` |
| BambooHR | HTML | `{company}.bamboohr.com/careers` |
| Custom | Jsoup/Playwright | CSS-селекторы из таблицы `companies` |

---

### Этап 3: Search Profiles + Processing Pipeline

**3.1 — SearchProfile**

```java
// Хранится в БД, управляется через UI — не требует перезапуска
public record SearchProfile(
    String id,
    String name,                      // "Java Backend", "Golang Engineer"
    boolean active,
    List<String> mustHaveKeywords,    // хотя бы одно — в title/description
    List<String> niceToHaveKeywords,  // повышают score
    List<String> excludeKeywords,     // "php", "clearance" → score = 0
    List<String> locations,
    List<RemoteType> remoteTypes,
    int minRelevanceScore             // 0-100, вакансии ниже не сохраняются
) {}
```

Профили из коробки (seeded при старте):
- **Java Backend** — `must: [java]`, `nice: [spring, kafka, postgresql, microservices]`
- **Kotlin** — `must: [kotlin]`, `nice: [spring, android, coroutines]`
- **Golang** — `must: [golang, go]`, `nice: [kubernetes, docker, grpc]`

**3.2 — Pipeline**

```
RawVacancy
  → Normalizer          (зарплата, remote type, локация — в стандартные форматы)
  → [для каждого активного SearchProfile]
      → RelevanceScorer (score 0-100 + matchedKeywords)
      → фильтр: score >= minRelevanceScore
  → Deduplicator        (hash + fuzzy matching)
  → Vacancy (DB)
  → TelegramNotifier    (push для новых вакансий)
```

**3.3 — RelevanceScorer**

```java
public interface RelevanceScorer {
    ScoredVacancy score(RawVacancy vacancy, SearchProfile profile);
}

public record ScoredVacancy(
    RawVacancy vacancy,
    int score,
    List<String> matchedKeywords,  // сохраняются в БД, используются для подсветки
    String profileId
) {}
```

Логика score:
| Условие | Баллы |
|---------|-------|
| `mustHaveKeyword` в title | +30 за каждое |
| `mustHaveKeyword` в description | +10 за каждое |
| `niceToHaveKeyword` найдено | +5 за каждое |
| remote type совпадает | +15 |
| локация совпадает | +10 |
| `excludeKeyword` найдено | score = 0 |

**3.4 — Дедупликация**
- Точные дубли: hash по `(company + normalized_title + location)`
- Кросс-платформенные: fuzzy matching по description (similarity > 0.85)
- Одна вакансия на нескольких платформах = одна запись + несколько `source_id`

---

### Этап 4: Telegram бот

**4.1 — Уведомления**
- TelegramBots (rubenlagus/TelegramBots)
- Push при появлении новой вакансии (score в уведомлении)
- Формат:
  ```
  🆕 Java Backend Engineer  [score: 87]
  🏢 Spotify | 💰 €70-90k
  📍 Remote (EU) | 🔗 Ссылка

  Matched: java · spring · kafka
  ⏰ Найдено: 2 мин назад

  [👍 Интересно] [👎 Пропустить] [📌 Потом]
  ```
- Inline-кнопки меняют `status` вакансии напрямую из Telegram

**4.2 — Команды бота**
- `/stats` — статистика за день/неделю
- `/companies` — список отслеживаемых компаний
- `/add company_url` — добавить компанию
- `/pause` / `/resume` — пауза уведомлений
- `/profiles` — список активных SearchProfile

---

### Этап 5: Web Dashboard

**5.1 — React + TypeScript + Vite + Tailwind**

Страницы:
- `/vacancies` — таблица с фильтрами (по score, источнику, профилю, статусу), подсветка `matchedKeywords`
- `/companies` — управление списком компаний + ATS конфиг
- `/profiles` — CRUD для SearchProfile, тест профиля на последних вакансиях
- `/dashboard` — аналитика: вакансий в день, по источникам, по профилям, воронка статусов
- `/settings` — Telegram, интервалы, общие настройки

**5.2 — Vacancy статусы (kanban)**
```
NEW → INTERESTED → APPLIED → INTERVIEW → OFFER
                                       → REJECTED
```

---

### Этап 6: Оптимизация

**6.1 — Anti-ban меры** (реализуется в базовых классах адаптеров)
- Rate limiting per domain через Redis (токен-бакет)
- Random delay 1-5 сек между запросами к одному домену
- User-Agent ротация (пул из 10+ реальных UA)
- `If-Modified-Since` / `ETag` — не парсить неизменённые страницы
- Respect `robots.txt`
- Proxy pool — опционально, при необходимости

**6.2 — Производительность**
- Virtual Threads (Java 21) — все 38 адаптеров параллельно
- Webhook где поддерживается (некоторые ATS)
- Incremental crawling: `last_crawled_at` + `description_hash` — пропускать неизменённые

---

## REST API

```
# Вакансии
GET    /api/v1/vacancies              # список с фильтрами и пагинацией
GET    /api/v1/vacancies/{id}         # детали вакансии
PATCH  /api/v1/vacancies/{id}/status  # смена статуса

# Компании
GET    /api/v1/companies
POST   /api/v1/companies
PUT    /api/v1/companies/{id}
DELETE /api/v1/companies/{id}

# Search Profiles
GET    /api/v1/profiles
POST   /api/v1/profiles
PUT    /api/v1/profiles/{id}
DELETE /api/v1/profiles/{id}
PATCH  /api/v1/profiles/{id}/toggle   # включить/выключить

# Источники
GET    /api/v1/sources                # список с last_crawled_at
PATCH  /api/v1/sources/{id}/toggle    # включить/выключить адаптер

# Аналитика
GET    /api/v1/stats/daily
GET    /api/v1/stats/by-source
GET    /api/v1/stats/by-profile

# Краулер
POST   /api/v1/crawler/run            # ручной запуск
GET    /api/v1/crawler/logs           # последние crawl_logs
```

---

## Стек технологий

| Компонент | Технология |
|-----------|-----------|
| Backend | Java 21, Spring Boot 3.x, Spring Scheduler |
| HTTP клиент | OkHttp |
| RSS парсинг | Rome (com.rometools) |
| HTML парсинг | Jsoup |
| JS-heavy сайты | Playwright4J (headless Chromium) |
| База данных | PostgreSQL 18 |
| Кеш / Rate limiting | Redis 8.6 |
| Миграции | Flyway |
| Telegram | TelegramBots (rubenlagus/TelegramBots) |
| Frontend | React 19, TypeScript 5, Vite 7, Tailwind 4 |
| Локальная разработка | docker-compose.yml (PostgreSQL + Redis), бэкенд нативно |
| Деплой | Docker Compose (backend + DB + Redis + frontend) |
| CI | GitHub Actions → Docker build → deploy |

---

## Структура проекта

```
jrawler/
├── docker-compose.yml            # локальная разработка: postgres + redis
├── .env.example
├── backend/
│   ├── src/main/java/com/jrawler/
│   │   ├── config/               # Spring конфигурация, Redis, OkHttp бины
│   │   ├── adapter/
│   │   │   ├── JobSearchAdapter.java
│   │   │   ├── AdapterRegistry.java
│   │   │   ├── base/
│   │   │   │   ├── AbstractRestApiAdapter.java
│   │   │   │   ├── AbstractRssAdapter.java
│   │   │   │   └── AbstractWebCrawlerAdapter.java
│   │   │   ├── model/
│   │   │   │   ├── RawVacancy.java
│   │   │   │   └── SearchCriteria.java
│   │   │   ├── p0/               # LinkedIn, RemoteOK, WeWorkRemotely, Remotive,
│   │   │   │                     # JustJoinIt, NoFluffJobs, TheProtocol
│   │   │   ├── p1/               # Wellfound, ArcDev, Glassdoor, Indeed, Dice,
│   │   │   │                     # Hired, StepStone, CWJobs, Reed, Relocate,
│   │   │   │                     # Bulldogjob, Rocketjobs, Pracuj
│   │   │   ├── p2/               # Otta, LandingJobs, Jobspresso, RemoteCo,
│   │   │   │                     # CareerVault, WorkingNomads, 4programmers,
│   │   │   │                     # Xing, EuroJobs, JobsCz, Totaljobs,
│   │   │   │                     # ZipRecruiter, TheLadders, Monster,
│   │   │   │                     # YCJobs, Talent, Workopolis
│   │   │   └── ats/              # Greenhouse, Lever, Workday,
│   │   │                         # SmartRecruiters, BambooHR, Custom
│   │   ├── processing/
│   │   │   ├── ProcessingPipeline.java
│   │   │   ├── Normalizer.java
│   │   │   ├── RelevanceScorer.java
│   │   │   ├── KeywordMatcher.java
│   │   │   └── Deduplicator.java
│   │   ├── profile/              # SearchProfile entity, repo, service
│   │   ├── company/              # Company entity, repo, service
│   │   ├── vacancy/              # Vacancy entity, repo, service
│   │   ├── notification/         # Telegram bot
│   │   ├── api/                  # REST контроллеры
│   │   └── scheduler/            # CrawlScheduler — запускает AdapterRegistry
│   └── src/main/resources/
│       └── db/migration/         # Flyway: V1__init.sql, V2__seed_profiles.sql
├── frontend/
│   ├── src/
│   │   ├── pages/                # Vacancies, Companies, Profiles, Dashboard, Settings
│   │   ├── components/           # Table, Filters, KanbanBoard, KeywordHighlight
│   │   └── api/                  # REST client (axios/fetch)
│   └── vite.config.ts
└── deploy/
    ├── Dockerfile.backend
    ├── Dockerfile.frontend
    └── docker-compose.prod.yml
```

---

## MVP: быстрый старт

Минимальная рабочая версия за ~5 дней:

1. **Этап 1** — Spring Boot + DB + docker-compose
2. **Этап 2 (частично)** — только P0 адаптеры с API/RSS: RemoteOK, Remotive, WeWorkRemotely, JustJoin.it, Greenhouse, Lever
3. **Этап 3** — один SearchProfile "Java Backend" + RelevanceScorer
4. **Этап 4** — Telegram уведомления

Дашборд, P1/P2 адаптеры, anti-ban оптимизации — после MVP.

---

## Ключевые решения

1. **Adapter pattern**: единый интерфейс для 38 источников + 5 ATS — добавить новый борд = один новый класс
2. **SearchProfile в БД**: смена профиля поиска (Java → Golang) без перезапуска, через UI
3. **RelevanceScorer**: score 0-100 + matchedKeywords для подсветки — не нужен ручной просмотр сотен вакансий
4. **Virtual Threads (Java 21)**: 38 адаптеров параллельно без thread pool bottleneck
5. **Incremental crawling**: ETag/Last-Modified в Redis — не парсить неизменённые страницы
6. **ATS-покрытие**: 5 парсеров для стандартных ATS покрывают ~70% карьерных страниц компаний
