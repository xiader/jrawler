-- ============================================================
-- V2 — Начальные данные: источники и профили поиска
-- ============================================================

-- ============================================================
-- Источники (sources)
-- ============================================================

-- P0 — API/RSS (минимальный риск бана)
INSERT INTO sources (id, name, priority, is_enabled) VALUES
    ('remoteok',       'RemoteOK',          0, TRUE),
    ('remotive',       'Remotive',          0, TRUE),
    ('weworkremotely', 'We Work Remotely',  0, TRUE),
    ('linkedin',       'LinkedIn',          0, TRUE),
    ('justjoinit',     'JustJoin.it',       0, FALSE),   -- /api/offers removed (404)
    ('nofluffjobs',    'NoFluffJobs',       0, TRUE),
    ('theprotocol',    'TheProtocol.it',    0, TRUE),
    ('company_ats',    'Company ATS Pages', 0, TRUE),

-- P1 — HTML parsing
    ('wellfound',      'Wellfound',         1, FALSE),
    ('arcdev',         'Arc.dev',           1, FALSE),
    ('glassdoor',      'Glassdoor',         1, FALSE),
    ('indeed',         'Indeed',            1, FALSE),
    ('dice',           'Dice.com',          1, FALSE),
    ('hired',          'Hired.com',         1, FALSE),
    ('stepstone',      'StepStone',         1, FALSE),
    ('cwjobs',         'CWJobs',            1, FALSE),
    ('reed',           'Reed.co.uk',        1, FALSE),
    ('relocateme',     'Relocate.me',       1, FALSE),
    ('bulldogjob',     'Bulldogjob.pl',     1, FALSE),
    ('rocketjobs',     'Rocketjobs.pl',     1, FALSE),
    ('pracuj',         'Pracuj.pl',         1, FALSE),

-- P2 — меньший объём или региональная специфика
    ('otta',           'Otta',              2, FALSE),
    ('landingjobs',    'Landing.jobs',      2, FALSE),
    ('jobspresso',     'Jobspresso',        2, FALSE),
    ('remoteco',       'Remote.co',         2, FALSE),
    ('careervault',    'CareerVault',       2, FALSE),
    ('workingnomads',  'WorkingNomads',     2, FALSE),
    ('4programmers',   '4programmers.net',  2, FALSE),
    ('xing',           'Xing Jobs',         2, FALSE),
    ('eurojobs',       'EuroJobs',          2, FALSE),
    ('jobscz',         'Jobs.cz',           2, FALSE),
    ('totaljobs',      'Totaljobs',         2, FALSE),
    ('ziprecruiter',   'ZipRecruiter',      2, FALSE),
    ('theladders',     'TheLadders',        2, FALSE),
    ('monster',        'Monster',           2, FALSE),
    ('ycjobs',         'YC Jobs',           2, FALSE),
    ('talent',         'Talent.com',        2, FALSE),
    ('workopolis',     'Workopolis',        2, FALSE);

-- ============================================================
-- Профили поиска (search_profiles)
-- ============================================================
INSERT INTO search_profiles (name, is_active, must_have_keywords, nice_to_have_keywords, exclude_keywords, locations, remote_types, min_relevance_score)
VALUES
    (
        'Java Backend',
        TRUE,
        '["java"]',
        '["spring", "spring boot", "kafka", "postgresql", "microservices", "kubernetes", "docker", "rest api", "hibernate"]',
        '["php", "clearance", "secret clearance", ".net", "c#", "ruby", "ios", "android", "react", "angular", "vue", "javascript", "qa", "test automation", "quality assurance"]',
        '["Poland", "Europe", "Remote", "EU", "Worldwide"]',
        '["REMOTE", "HYBRID"]',
        30
    ),
    (
        'Kotlin',
        TRUE,
        '["kotlin"]',
        '["spring", "spring boot", "coroutines", "ktor", "android", "jvm", "microservices", "docker"]',
        '["clearance", "secret clearance", "php", "ruby"]',
        '["Poland", "Europe", "Remote", "EU", "Worldwide"]',
        '["REMOTE", "HYBRID"]',
        30
    ),
    (
        'Golang',
        FALSE,
        '["golang", "go"]',
        '["kubernetes", "docker", "grpc", "microservices", "cloud", "aws", "gcp", "distributed systems"]',
        '["clearance", "secret clearance"]',
        '["Poland", "Europe", "Remote", "EU", "Worldwide"]',
        '["REMOTE", "HYBRID"]',
        30
    );