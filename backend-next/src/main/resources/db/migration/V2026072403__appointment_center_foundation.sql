SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'staff_schedule' AND column_name = 'tenant_id'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE staff_schedule ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

UPDATE staff_schedule ss
JOIN shop s ON s.id = ss.shop_id
SET ss.tenant_id = s.tenant_id
WHERE ss.tenant_id IS NULL;

ALTER TABLE staff_schedule
    MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment' AND column_name = 'rescheduled_from_at'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE appointment ADD COLUMN rescheduled_from_at DATETIME(3) NULL AFTER checked_in_at',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment' AND column_name = 'started_at'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE appointment ADD COLUMN started_at DATETIME(3) NULL AFTER rescheduled_from_at',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment' AND column_name = 'completed_at'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE appointment ADD COLUMN completed_at DATETIME(3) NULL AFTER started_at',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment' AND column_name = 'cancelled_at'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE appointment ADD COLUMN cancelled_at DATETIME(3) NULL AFTER completed_at',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment' AND column_name = 'no_show_at'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE appointment ADD COLUMN no_show_at DATETIME(3) NULL AFTER cancelled_at',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'appointment' AND column_name = 'updated_by'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE appointment ADD COLUMN updated_by BIGINT UNSIGNED NULL AFTER version',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

CREATE TABLE IF NOT EXISTS appointment_status_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    shop_id BIGINT UNSIGNED NOT NULL,
    appointment_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NULL,
    changed_by BIGINT UNSIGNED NULL,
    appointment_version INT UNSIGNED NOT NULL,
    changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_appointment_history_appointment (appointment_id, changed_at),
    KEY idx_appointment_history_tenant_time (tenant_id, changed_at),
    CONSTRAINT fk_appointment_history_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_appointment_history_shop
        FOREIGN KEY (shop_id) REFERENCES shop(id),
    CONSTRAINT fk_appointment_history_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointment(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_history_account
        FOREIGN KEY (changed_by) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='预约状态变更历史';

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_definition r
JOIN permission_definition p
  ON p.permission_code IN ('appointment:view', 'appointment:manage')
WHERE r.role_code = 'REGIONAL_MANAGER';

INSERT INTO member (
    tenant_id, home_shop_id, shop_id, member_no, global_member_no,
    name, phone, gender, avatar_url, source, status, created_at, updated_at
)
SELECT
    s.tenant_id,
    s.id,
    s.id,
    LEFT(c.zhanghao, 32),
    CONCAT('LEGACY-', c.id),
    c.xingming,
    c.shouji,
    c.xingbie,
    c.touxiang,
    '旧系统迁移',
    'ACTIVE',
    c.addtime,
    c.addtime
FROM chezhu c
JOIN shop s ON s.id = 1
WHERE c.shouji IS NOT NULL
  AND c.shouji <> ''
  AND NOT EXISTS (
      SELECT 1 FROM member m
      WHERE m.tenant_id = s.tenant_id
        AND m.phone COLLATE utf8mb4_unicode_ci = c.shouji
  );

INSERT IGNORE INTO member_shop_profile (
    tenant_id, member_id, shop_id, first_visit_at, source, status
)
SELECT
    m.tenant_id,
    m.id,
    1,
    c.addtime,
    '旧系统迁移',
    'ACTIVE'
FROM chezhu c
JOIN member m
  ON m.tenant_id = 1
 AND m.phone COLLATE utf8mb4_unicode_ci = c.shouji;

INSERT IGNORE INTO member_account (
    tenant_id, member_id, account_type, currency_code, balance, status
)
SELECT
    m.tenant_id,
    m.id,
    account_types.account_type,
    'CNY',
    0,
    'ACTIVE'
FROM member m
JOIN chezhu c
  ON c.shouji = m.phone COLLATE utf8mb4_unicode_ci
JOIN (
    SELECT 'BALANCE' AS account_type
    UNION ALL SELECT 'GIFT_BALANCE'
    UNION ALL SELECT 'POINTS'
) account_types
WHERE m.tenant_id = 1;

INSERT INTO service_category (
    tenant_id, shop_id, name, is_chain_standard, sort_order, status
)
SELECT
    s.tenant_id,
    s.id,
    legacy_categories.name,
    0,
    80,
    'ACTIVE'
FROM shop s
JOIN (
    SELECT DISTINCT COALESCE(NULLIF(TRIM(fuwufenlei), ''), '未分类') AS name
    FROM fuwuyuyue
) legacy_categories
WHERE s.id = 1
  AND NOT EXISTS (
      SELECT 1 FROM service_category sc
      WHERE sc.shop_id = s.id
        AND sc.name COLLATE utf8mb4_unicode_ci = legacy_categories.name
  );

INSERT INTO service_item (
    tenant_id, shop_id, category_id, service_code, name, cover_url,
    description, duration_minutes, cleanup_minutes, list_price, member_price,
    is_featured, is_chain_standard, status, created_at, updated_at
)
SELECT
    s.tenant_id,
    s.id,
    sc.id,
    CONCAT('LEGACY-', MIN(f.id)),
    f.fuwumingcheng,
    MIN(f.fengmian),
    CONCAT('由旧预约数据迁移：', COALESCE(MIN(f.cheliangwenti), '')),
    CASE
        WHEN f.fuwumingcheng LIKE '%舒压%' THEN 90
        WHEN f.fuwumingcheng LIKE '%修护%' OR f.fuwumingcheng LIKE '%紧致%' THEN 75
        ELSE 60
    END,
    15,
    COALESCE(MAX(f.jiage), 0),
    COALESCE(MAX(f.jiage), 0),
    0,
    0,
    'ACTIVE',
    MIN(f.addtime),
    MIN(f.addtime)
FROM fuwuyuyue f
JOIN shop s ON s.id = 1
JOIN service_category sc
  ON sc.shop_id = s.id
 AND sc.name COLLATE utf8mb4_unicode_ci = COALESCE(NULLIF(TRIM(f.fuwufenlei), ''), '未分类')
WHERE f.fuwumingcheng IS NOT NULL
  AND f.fuwumingcheng <> ''
  AND NOT EXISTS (
      SELECT 1 FROM service_item si
      WHERE si.shop_id = s.id
        AND si.name COLLATE utf8mb4_unicode_ci = f.fuwumingcheng
  )
GROUP BY s.tenant_id, s.id, sc.id, f.fuwumingcheng;

INSERT IGNORE INTO staff_service (staff_id, service_id, enabled)
SELECT DISTINCT
    st.id,
    si.id,
    1
FROM fuwuyuyue f
JOIN staff st
  ON st.tenant_id = 1
 AND st.staff_no COLLATE utf8mb4_unicode_ci = f.weixiuzhanghao
JOIN service_item si
  ON si.shop_id = 1
 AND si.name COLLATE utf8mb4_unicode_ci = f.fuwumingcheng;

INSERT IGNORE INTO staff_schedule (
    tenant_id, shop_id, staff_id, schedule_date,
    start_time, end_time, schedule_type, remark
)
SELECT DISTINCT
    st.tenant_id,
    1,
    st.id,
    DATE(f.yuyueshijian),
    '09:00:00',
    '23:59:00',
    'WORK',
    '由旧预约数据补齐'
FROM fuwuyuyue f
JOIN staff st
  ON st.tenant_id = 1
 AND st.staff_no COLLATE utf8mb4_unicode_ci = f.weixiuzhanghao
WHERE f.yuyueshijian IS NOT NULL;

INSERT INTO appointment (
    tenant_id, shop_id, appointment_no, member_id, staff_id,
    start_at, end_at, status, source, member_note, internal_note,
    confirmed_at, version, created_at, updated_at
)
SELECT
    1,
    1,
    f.yuyuebianhao,
    m.id,
    st.id,
    f.yuyueshijian,
    TIMESTAMPADD(MINUTE, si.duration_minutes + si.cleanup_minutes, f.yuyueshijian),
    CASE
        WHEN f.weixiuzhuangtai IN ('已确认', '待服务') OR f.sfsh = '是' THEN 'CONFIRMED'
        ELSE 'PENDING'
    END,
    'ONLINE',
    f.cheliangwenti,
    f.shhf,
    CASE
        WHEN f.weixiuzhuangtai IN ('已确认', '待服务') OR f.sfsh = '是' THEN f.addtime
        ELSE NULL
    END,
    0,
    f.addtime,
    f.addtime
FROM fuwuyuyue f
JOIN member m
  ON m.tenant_id = 1
 AND m.phone COLLATE utf8mb4_unicode_ci = f.shouji
JOIN staff st
  ON st.tenant_id = 1
 AND st.staff_no COLLATE utf8mb4_unicode_ci = f.weixiuzhanghao
JOIN service_item si
  ON si.shop_id = 1
 AND si.name COLLATE utf8mb4_unicode_ci = f.fuwumingcheng
WHERE f.yuyuebianhao IS NOT NULL
  AND f.yuyuebianhao <> ''
  AND f.yuyueshijian IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM appointment a
      WHERE a.appointment_no COLLATE utf8mb4_unicode_ci = f.yuyuebianhao
  );

INSERT IGNORE INTO appointment_item (
    appointment_id, service_id, service_name_snapshot,
    duration_minutes_snapshot, price_snapshot, sort_order
)
SELECT
    a.id,
    si.id,
    si.name,
    si.duration_minutes,
    COALESCE(f.jiage, si.member_price, si.list_price),
    0
FROM fuwuyuyue f
JOIN appointment a
  ON a.appointment_no COLLATE utf8mb4_unicode_ci = f.yuyuebianhao
JOIN service_item si
  ON si.shop_id = a.shop_id
 AND si.name COLLATE utf8mb4_unicode_ci = f.fuwumingcheng;

INSERT INTO appointment_status_history (
    tenant_id, shop_id, appointment_id, from_status, to_status,
    reason, changed_by, appointment_version, changed_at
)
SELECT
    a.tenant_id,
    a.shop_id,
    a.id,
    NULL,
    a.status,
    '旧系统预约迁移',
    NULL,
    a.version,
    a.created_at
FROM appointment a
WHERE a.appointment_no LIKE 'YY%'
  AND NOT EXISTS (
      SELECT 1 FROM appointment_status_history h
      WHERE h.appointment_id = a.id
  );
