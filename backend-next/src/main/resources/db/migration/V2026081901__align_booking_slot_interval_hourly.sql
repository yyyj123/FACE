-- Keep customer booking starts aligned with the hourly admin schedule while
-- preserving each service's independent duration and existing occupied ranges.

UPDATE `service_item`
SET `slot_interval_minutes` = 60,
    `booking_terms_version` = `booking_terms_version` + 1
WHERE `slot_interval_minutes` <> 60;

ALTER TABLE `service_item`
  MODIFY COLUMN `slot_interval_minutes` smallint unsigned NOT NULL DEFAULT 60;
