-- SC6 follow-up: keep the immutable fact outcome wide enough for the
-- SYSTEM_AUTO_CONFIRMED state recorded by the scheduled finalizer.
ALTER TABLE `service_fulfillment_fact`
  MODIFY COLUMN `outcome` varchar(30) NOT NULL;
