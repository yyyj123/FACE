-- Append-only repair for legacy ACTIVE banners migrated by V2026080301.
-- MySQL evaluates single-table UPDATE assignments from left to right, so the
-- status assignment can precede the published_at expression in that migration.
UPDATE `banner`
SET `published_at` = COALESCE(`start_at`, `created_at`)
WHERE `status` = 'PUBLISHED'
  AND `published_at` IS NULL;
