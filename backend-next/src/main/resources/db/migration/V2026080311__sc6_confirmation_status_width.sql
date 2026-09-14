-- SC6 follow-up: SYSTEM_AUTO_CONFIRMED is 21 characters and must fit the
-- customer-confirmation status projection allowed by the existing check.
ALTER TABLE `customer_confirmation`
  MODIFY COLUMN `status` varchar(30) NOT NULL DEFAULT 'PENDING';
