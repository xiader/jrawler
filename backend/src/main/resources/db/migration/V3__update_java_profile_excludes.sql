-- Add QA/testing roles to Java Backend exclude list
UPDATE search_profiles
SET exclude_keywords = '["php", "clearance", "secret clearance", ".net", "c#", "ruby", "ios", "android", "react", "angular", "vue", "qa", "test automation", "quality assurance"]'
WHERE name = 'Java Backend';