USE `face_salon`;

CREATE TABLE IF NOT EXISTS `chat` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `addtime` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `userid` BIGINT UNSIGNED NOT NULL COMMENT 'Member account id',
  `adminid` BIGINT UNSIGNED NULL COMMENT 'Staff account id',
  `ask` LONGTEXT NULL,
  `reply` LONGTEXT NULL,
  `isreply` TINYINT NOT NULL DEFAULT 1 COMMENT '1 awaiting reply, 0 historical',
  `isread` TINYINT NOT NULL DEFAULT 0,
  `uname` VARCHAR(200) NULL,
  `uimage` TEXT NULL,
  `type` TINYINT NOT NULL DEFAULT 1 COMMENT '1 text, 2 image, 3 video, 4 file',
  PRIMARY KEY (`id`),
  KEY `idx_chat_user_time` (`userid`, `addtime`),
  KEY `idx_chat_reply_state` (`isreply`, `addtime`),
  CONSTRAINT `fk_chat_user_account` FOREIGN KEY (`userid`) REFERENCES `account` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_chat_admin_account` FOREIGN KEY (`adminid`) REFERENCES `account` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Online customer-service conversation';
