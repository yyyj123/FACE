-- M6-12: switch known demo service covers to the optimized WebP variants.
-- The source PNG files remain available, so rollback only requires restoring
-- the .png suffix for the same controlled path prefix.
UPDATE `service_item`
SET `cover_url` = CONCAT(LEFT(`cover_url`, LENGTH(`cover_url`) - 4), '.webp')
WHERE `cover_url` LIKE 'upload/service-catalog/%.png';
