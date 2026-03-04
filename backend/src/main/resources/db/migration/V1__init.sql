-- ============================================================
-- V1 — Начальная схема базы данных
-- ============================================================

-- Расширения
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- Источники (справочник)
-- ============================================================
CREATE TABLE sources (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    priority        SMALLINT    NOT NULL DEFAULT 1,  -- 0=P0, 1=P1, 2=P2
    is_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    last_crawled_at TIMESTAMPTZ,
    last_etag       VARCHAR(255)
);

-- ============================================================
-- Компании (для прямого мониторинга карьерных страниц)
-- ============================================================
CREATE TABLE companies (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(255) NOT NULL,
    career_page_url  VARCHAR(512),
    ats_type         VARCHAR(64),          -- greenhouse|lever|workday|smartrecruiters|bamboohr|custom
    ats_company_id   VARCHAR(128),
    custom_selectors JSONB,               -- CSS-селекторы для кастомного парсинга
    is_active        BOOLEAN     NOT NULL DEFAULT TRUE,
    last_crawled_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Профили поиска
-- ============================================================
CREATE TABLE search_profiles (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 VARCHAR(255) NOT NULL,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    must_have_keywords   JSONB       NOT NULL DEFAULT '[]',
    nice_to_have_keywords JSONB      NOT NULL DEFAULT '[]',
    exclude_keywords     JSONB       NOT NULL DEFAULT '[]',
    locations            JSONB       NOT NULL DEFAULT '[]',
    remote_types         JSONB       NOT NULL DEFAULT '[]',
    min_relevance_score  SMALLINT    NOT NULL DEFAULT 30,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Вакансии
-- ============================================================
CREATE TABLE vacancies (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id      VARCHAR(255),
    source_id        VARCHAR(64) NOT NULL REFERENCES sources(id),
    title            VARCHAR(512) NOT NULL,
    company_name     VARCHAR(255),
    url              VARCHAR(1024) NOT NULL,
    location         VARCHAR(255),
    salary_raw       VARCHAR(255),
    remote_type      VARCHAR(32),          -- REMOTE|HYBRID|ON_SITE
    description      TEXT,
    description_hash VARCHAR(64),
    relevance_score  SMALLINT    NOT NULL DEFAULT 0,
    matched_keywords JSONB       NOT NULL DEFAULT '[]',
    profile_id       UUID        REFERENCES search_profiles(id),
    status           VARCHAR(32) NOT NULL DEFAULT 'NEW',
    found_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT vacancies_url_unique UNIQUE (url),
    CONSTRAINT vacancies_status_check CHECK (
        status IN ('NEW', 'INTERESTED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED')
    ),
    CONSTRAINT vacancies_remote_type_check CHECK (
        remote_type IS NULL OR remote_type IN ('REMOTE', 'HYBRID', 'ON_SITE')
    )
);

-- ============================================================
-- Лог краулинга
-- ============================================================
CREATE TABLE crawl_logs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       VARCHAR(64) NOT NULL REFERENCES sources(id),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMPTZ,
    vacancies_found INT         NOT NULL DEFAULT 0,
    vacancies_new   INT         NOT NULL DEFAULT 0,
    error           TEXT
);

-- ============================================================
-- Индексы
-- ============================================================
CREATE INDEX idx_vacancies_source_id        ON vacancies (source_id);
CREATE INDEX idx_vacancies_profile_id       ON vacancies (profile_id);
CREATE INDEX idx_vacancies_status           ON vacancies (status);
CREATE INDEX idx_vacancies_relevance_score  ON vacancies (relevance_score DESC);
CREATE INDEX idx_vacancies_found_at         ON vacancies (found_at DESC);
CREATE INDEX idx_vacancies_description_hash ON vacancies (description_hash);
CREATE INDEX idx_crawl_logs_source_id       ON crawl_logs (source_id);
CREATE INDEX idx_crawl_logs_started_at      ON crawl_logs (started_at DESC);
