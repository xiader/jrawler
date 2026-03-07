# LinkedIn Adapter — план реализации

## Решения

| Вопрос | Решение |
|---|---|
| Описание вакансии | Да, fetch для каждой вакансии |
| Период | Последняя неделя (`f_TPR=r604800`) |
| Запросы по keywords | Один запрос (union из профиля) |
| Авторизация | Опциональная через `li_at` cookie |

---

## Проблема с текущей реализацией

`LinkedInAdapter` extends `AbstractRssAdapter` — нерабочая заглушка.
LinkedIn убил публичные RSS-ленты вакансий. Адаптер возвращает 0 вакансий.

---

## Стратегия: два режима работы

### Режим 1: Guest API (без авторизации, дефолт)

LinkedIn предоставляет публичный guest API, используемый самим сайтом:

**Листинг вакансий:**
```
GET https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search
    ?keywords=java+backend+spring
    &location=Europe
    &f_WT=2        (remote: 1=onsite, 2=remote, 3=hybrid)
    &f_TPR=r604800 (последняя неделя = 604800 сек)
    &start=0       (пагинация)
```

**Детальная страница (описание):**
```
GET https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/{jobId}
```
Возвращает HTML с описанием без авторизации для публичных вакансий.
`jobId` извлекается из `data-entity-urn="urn:li:jobPosting:JOBID"`.

### Режим 2: Авторизованный (li_at cookie, опционально)

**Что даёт авторизация:**
- Доступ к вакансиям, закрытым для гостей (с required login)
- Меньше 429/редиректов (LinkedIn лояльнее к авторизованным запросам)
- Доступ к "Easy Apply" вакансиям
- Стабильнее работает при высокой нагрузке

**Что НЕ даёт авторизация:**
- Принципиально другое качество данных (те же поля)
- API-лимиты не снимаются — LinkedIn всё равно режет scraping

**Риски:**
- LinkedIn может заблокировать аккаунт за scraping
- `li_at` cookie протухает (раз в несколько недель)

**Вывод:** Авторизацию предусмотреть как опцию. Если `LINKEDIN_LI_AT` задан в env —
использовать cookie на всех запросах. Если не задан — работать в guest режиме.

При авторизации endpoint листинга меняется на:
```
GET https://www.linkedin.com/jobs/search/
    ?keywords=java+backend
    &location=Europe
    &f_WT=2
    &f_TPR=r604800
    &start=0
```
(стандартный search с cookie-сессией, парсим HTML)

---

## Архитектура

### Класс

```
LinkedInAdapter extends AbstractWebCrawlerAdapter
```

- `requiresJavaScript()` → `false` (Jsoup достаточно)
- Переопределяем `fetchJobs()` для поддержки пагинации + fetch описаний
- Базовые методы `buildSearchUrl()` и `parseJobList()` реализованы как вспомогательные

### Пагинация

- Шаг: 25 вакансий за запрос (лимит LinkedIn)
- Максимум: 3 страницы (0, 25, 50) = до 75 вакансий
- Стоп-условие: ответ пустой ИЛИ достигнут лимит страниц

### Fetch описания

Для каждой вакансии из листинга:
1. GET на `jobs-guest/jobs/api/jobPosting/{jobId}`
2. Jsoup парсинг `.show-more-less-html__markup` или `.description__text`
3. Rate limit между запросами (существующий Redis-механизм)
4. Если fetch упал (429/error) — description = null, вакансию всё равно сохраняем

**Ожидаемое время:** 75 вакансий × (2–5 сек задержка) ≈ 2.5–6 мин.
Т.к. CrawlScheduler ограничивает 5 мин на адаптер — реально ~50–60 вакансий с описанием.

---

## HTML-структура листинга (guest API)

```html
<li>
  <div class="base-card" data-entity-urn="urn:li:jobPosting:3876543210">
    <a class="base-card__full-link" href="https://www.linkedin.com/jobs/view/3876543210/">
    <h3 class="base-search-card__title">Senior Java Developer</h3>
    <h4 class="base-search-card__subtitle">
      <a class="hidden-nested-link">Company Name</a>
    </h4>
    <span class="job-search-card__location">Warsaw, Poland</span>
    <time class="job-search-card__listdate" datetime="2024-01-15">2 weeks ago</time>
  </div>
</li>
```

### HTML-структура детальной страницы

```html
<div class="show-more-less-html__markup">
  <!-- полное описание -->
</div>
```
Fallback: `.description__text`

---

## Маппинг полей → RawVacancy

| Источник | RawVacancy поле |
|---|---|
| `data-entity-urn` → jobId из URN | `externalId` |
| `.base-card__full-link[href]` | `url` |
| `.base-search-card__title` | `title` |
| `.hidden-nested-link` (внутри h4) | `companyName` |
| `.job-search-card__location` | `location` |
| `time[datetime]` | `fetchedAt` |
| `"remote"` (hardcoded, f_WT=2) | `remoteTypeRaw` |
| Fetch детальной страницы | `description` |

---

## Конфигурация (application.yml / env)

```yaml
linkedin:
  li-at: ${LINKEDIN_LI_AT:}  # пусто = guest режим
```

Или просто через `@Value("${linkedin.li-at:}")` в адаптере.

---

## HTTP headers для requests

Базовые (все запросы):
```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ...
Accept: text/html,application/xhtml+xml,*/*;q=0.8
Accept-Language: en-US,en;q=0.5
Referer: https://www.linkedin.com/
```

Если авторизован:
```
Cookie: li_at=<value>
```

---

## Обработка ошибок

| Ситуация | Действие |
|---|---|
| HTTP 429 (rate limit) | log.warn, вернуть накопленные вакансии |
| HTTP 302 → login (без авторизации) | log.warn, пропустить страницу |
| Пустой `<ul>` (нет вакансий) | завершить пагинацию |
| Description fetch упал | description = null, вакансию сохранить |
| Исключение при парсинге | log.error, пропустить элемент |

---

## Порядок реализации

1. [ ] Переписать `LinkedInAdapter`:
   - `extends AbstractWebCrawlerAdapter`
   - `buildSearchUrl()` — формирует URL с keywords из criteria
   - `parseJobList()` — парсит `<li>` в RawVacancy (без description)
   - `fetchJobs()` — пагинация + вызов fetchDescription для каждой

2. [ ] Метод `fetchDescription(String jobId)`:
   - GET на `jobs-guest/jobs/api/jobPosting/{jobId}`
   - Парсинг `.show-more-less-html__markup`
   - Возвращает String или null

3. [ ] Конфигурация `li_at` через `@Value`

4. [ ] Добавить `LINKEDIN_LI_AT=` в `.env.example`

5. [ ] Верификация: запустить один раз, проверить логи, убедиться что вакансии
   со скором > 0 попадают в базу
