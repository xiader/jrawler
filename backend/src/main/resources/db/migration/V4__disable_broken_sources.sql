-- LinkedIn: public RSS/API removed years ago; adapter receives HTML instead of XML
-- JustJoinIt: /api/offers endpoint removed (HTTP 404); new API requires auth
UPDATE sources SET is_enabled = FALSE
WHERE id IN ('linkedin', 'justjoinit');
