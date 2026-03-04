# Job Crawler

Персональный агрегатор вакансий: автоматически обходит 38+ источников (job-борды, RSS, ATS компаний), фильтрует по профилям поиска и отправляет релевантные вакансии в Telegram.

## Стек

| Слой | Технологии |
|---|---|
| Backend | Java 21, Spring Boot 3.4.3 |
| БД | PostgreSQL 18, Redis 8.6 |
| Парсинг | OkHttp 4.12, Rome 2.1 (RSS), Jsoup 1.18, Playwright 1.49 |
| Уведомления | Telegram Bot (longpolling) |
| Инфра | Docker Compose, Flyway |

## Быстрый старт

### 1. Переменные окружения

```bash
cp .env.example .env
```

Откройте `.env` и заполните нужные значения. Минимум для запуска — PostgreSQL/Redis уже настроены дефолтами.

```env
# PostgreSQL
POSTGRES_DB=job_crawler_db
POSTGRES_USER=job_crawler_user
POSTGRES_PASSWORD=supersecret
DB_HOST=localhost

# Redis
REDIS_HOST=localhost

# Telegram (опционально, для уведомлений)
TELEGRAM_BOT_TOKEN=
TELEGRAM_BOT_USERNAME=
TELEGRAM_CHAT_ID=
```

### 2. Запуск инфраструктуры

```bash
docker compose up -d
```

Поднимет PostgreSQL на `:5432` и Redis на `:6379`. Flyway автоматически накатит миграции при старте бэкенда.

### 3. Запуск бэкенда

```bash
cd backend
mvn spring-boot:run
```

Бэкенд запустится на `http://localhost:8080`.

---

## Архитектура

```
job-crawler/
├── docker-compose.yml
├── .env.example
└── backend/src/main/java/com/jobcrawler/
    ├── adapter/          # Адаптеры источников (RSS, REST API, Web, ATS)
    ├── company/          # Управление компаниями и карьерными страницами
    ├── profile/          # Профили поиска (ключевые слова, локации, remote)
    ├── vacancy/          # Вакансии и их статусы
    ├── processing/       # Пайплайн: дедупликация → нормализация → скоринг
    ├── scheduler/        # Планировщик обходов (cron, каждый час)
    └── notification/     # Telegram-бот
```

### Источники вакансий

- **P0 (job-борды):** RemoteOK, Remotive, JustJoinIt, WeWorkRemotely, LinkedIn, NoFluffJobs, TheProtocol
- **ATS компаний:** Greenhouse, Lever, Workday, SmartRecruiters, BambooHR, Custom
- **Карьерные страницы:** произвольные URL через Jsoup/Playwright

### Пайплайн обработки

```
RawVacancy → Deduplicator → Normalizer → RelevanceScorer → DB
```

- **Deduplicator** — проверяет по URL и хешу описания (первые 500 символов)
- **Normalizer** — приводит поля к единому формату
- **RelevanceScorer** — сравнивает с профилями поиска; формула: +30 must-keyword в заголовке, +10 в описании, +5 nice-to-have, +15 remote match, +10 location match; если hit по exclude — score=0

---

## API

### Профили поиска

```http
GET    /api/v1/profiles
POST   /api/v1/profiles
PUT    /api/v1/profiles/:id
DELETE /api/v1/profiles/:id
PATCH  /api/v1/profiles/:id/toggle   # вкл/выкл профиль
```

Пример тела профиля:

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

### Источники

```http
GET   /api/v1/sources
PATCH /api/v1/sources/:id/toggle    # вкл/выкл источник
```

### Компании (ATS/карьерные страницы)

```http
GET    /api/v1/companies
POST   /api/v1/companies
PUT    /api/v1/companies/:id
DELETE /api/v1/companies/:id
```

### Вакансии

```http
GET  /api/v1/vacancies          # список с фильтрами и пагинацией
GET  /api/v1/vacancies/:id
PATCH /api/v1/vacancies/:id/status
```

Query-параметры для GET `/vacancies`: `sourceId`, `profileId`, `status`, `minScore`, `page`, `size`.

Статусы вакансии: `NEW` → `INTERESTED` → `APPLIED` → `INTERVIEW` → `OFFER` / `REJECTED`

### Краулер (ручной запуск)

```http
POST /api/v1/crawler/run          # запускает обход всех активных источников (202 Accepted, async)
GET  /api/v1/crawler/logs         # история запусков (?sourceId=&limit=)
```

---

## Конфигурация

Все параметры задаются через переменные окружения (`.env`). `application.yml` читает их с дефолтами:

| Переменная | Дефолт | Описание |
|---|---|---|
| `DB_HOST` | `localhost` | Хост PostgreSQL |
| `POSTGRES_DB` | `job_crawler_db` | Имя БД |
| `POSTGRES_USER` | `job_crawler_user` | Пользователь БД |
| `POSTGRES_PASSWORD` | `supersecret` | Пароль БД |
| `REDIS_HOST` | `localhost` | Хост Redis |
| `REDIS_PORT` | `6379` | Порт Redis |
| `SERVER_PORT` | `8080` | Порт бэкенда |
| `CRAWLER_ENABLED` | `true` | Включить планировщик |
| `TELEGRAM_BOT_TOKEN` | — | Токен Telegram-бота |
| `TELEGRAM_BOT_USERNAME` | — | Username бота |
| `TELEGRAM_CHAT_ID` | — | ID чата для уведомлений |

Расписание краулера задаётся в `application.yml`:

```yaml
crawler:
  schedule-cron: "0 0 * * * *"   # каждый час
```

---

## Telegram-бот

Для получения уведомлений о новых вакансиях:

1. Создайте бота через [@BotFather](https://t.me/BotFather), получите токен
2. Узнайте свой `chat_id` (например, через [@userinfobot](https://t.me/userinfobot))
3. Заполните `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_CHAT_ID` в `.env`
4. Перезапустите бэкенд

Бот будет присылать новые вакансии с оценкой >= `minScore` из активных профилей.

---

## Разработка

```bash
# Только инфраструктура (БД + Redis)
docker compose up -d

# Запуск с перезагрузкой при изменениях
cd backend
mvn spring-boot:run

# Сборка jar
mvn package -DskipTests

# Запуск jar
java -jar target/job-crawler-*.jar
```

Логи приложения — уровень DEBUG для `com.jobcrawler`, WARNING для Redis.
