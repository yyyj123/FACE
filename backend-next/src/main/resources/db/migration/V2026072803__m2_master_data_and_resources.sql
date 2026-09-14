-- M2 master data and resource foundation.
-- Expand-only migration: legacy staff_service remains the compatibility read model.

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'service_item'
      AND column_name = 'version'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE service_item ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 1 AFTER status',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'service_item'
      AND column_name = 'updated_by'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE service_item ADD COLUMN updated_by BIGINT UNSIGNED NULL AFTER version',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'staff_schedule'
      AND column_name = 'status'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE staff_schedule ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'' AFTER remark',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'staff_schedule'
      AND column_name = 'version'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE staff_schedule ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 1 AFTER status',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'staff_schedule'
      AND column_name = 'created_by'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE staff_schedule ADD COLUMN created_by BIGINT UNSIGNED NULL AFTER version',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'staff_schedule'
      AND column_name = 'updated_by'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE staff_schedule ADD COLUMN updated_by BIGINT UNSIGNED NULL AFTER created_by',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'staff_schedule'
      AND index_name = 'idx_staff_schedule_active_window'
);
SET @ddl = IF(
    @index_exists = 0,
    'ALTER TABLE staff_schedule ADD KEY idx_staff_schedule_active_window (tenant_id, shop_id, staff_id, schedule_date, status, start_time, end_time)',
    'SELECT 1'
);
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

CREATE TABLE IF NOT EXISTS staff_skill_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    shop_id BIGINT UNSIGNED NOT NULL,
    staff_id BIGINT UNSIGNED NOT NULL,
    service_id BIGINT UNSIGNED NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    custom_duration_minutes INT UNSIGNED NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL,
    created_by BIGINT UNSIGNED NULL,
    updated_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    current_marker TINYINT
        GENERATED ALWAYS AS (CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_staff_skill_version (tenant_id, staff_id, service_id, version),
    UNIQUE KEY uk_staff_skill_current (tenant_id, staff_id, service_id, current_marker),
    KEY idx_staff_skill_effective (tenant_id, shop_id, staff_id, effective_from, effective_to),
    KEY idx_staff_skill_service (tenant_id, shop_id, service_id, enabled),
    CONSTRAINT fk_staff_skill_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_staff_skill_shop
        FOREIGN KEY (shop_id) REFERENCES shop(id),
    CONSTRAINT fk_staff_skill_staff
        FOREIGN KEY (staff_id) REFERENCES staff(id),
    CONSTRAINT fk_staff_skill_service
        FOREIGN KEY (service_id) REFERENCES service_item(id),
    CONSTRAINT fk_staff_skill_created_by
        FOREIGN KEY (created_by) REFERENCES account(id),
    CONSTRAINT fk_staff_skill_updated_by
        FOREIGN KEY (updated_by) REFERENCES account(id),
    CONSTRAINT ck_staff_skill_duration
        CHECK (custom_duration_minutes IS NULL OR custom_duration_minutes > 0),
    CONSTRAINT ck_staff_skill_effective
        CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_staff_skill_version
        CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='技师项目技能生效版本';

CREATE TABLE IF NOT EXISTS service_resource (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    shop_id BIGINT UNSIGNED NOT NULL,
    resource_code VARCHAR(50) NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    capacity INT UNSIGNED NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NULL,
    updated_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_resource_code (tenant_id, shop_id, resource_code),
    KEY idx_service_resource_listing (tenant_id, shop_id, resource_type, status, resource_name),
    CONSTRAINT fk_service_resource_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_service_resource_shop
        FOREIGN KEY (shop_id) REFERENCES shop(id),
    CONSTRAINT fk_service_resource_created_by
        FOREIGN KEY (created_by) REFERENCES account(id),
    CONSTRAINT fk_service_resource_updated_by
        FOREIGN KEY (updated_by) REFERENCES account(id),
    CONSTRAINT ck_service_resource_type
        CHECK (resource_type IN ('ROOM', 'EQUIPMENT')),
    CONSTRAINT ck_service_resource_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_service_resource_capacity
        CHECK (capacity > 0),
    CONSTRAINT ck_service_resource_version
        CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='门店护理房间与设备资源';

CREATE TABLE IF NOT EXISTS resource_booking (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    shop_id BIGINT UNSIGNED NOT NULL,
    appointment_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL,
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    release_reason VARCHAR(200) NULL,
    created_by BIGINT UNSIGNED NULL,
    released_by BIGINT UNSIGNED NULL,
    released_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_resource_booking_overlap (tenant_id, resource_id, status, start_at, end_at),
    KEY idx_resource_booking_appointment (tenant_id, shop_id, appointment_id, status),
    CONSTRAINT fk_resource_booking_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_resource_booking_shop
        FOREIGN KEY (shop_id) REFERENCES shop(id),
    CONSTRAINT fk_resource_booking_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    CONSTRAINT fk_resource_booking_resource
        FOREIGN KEY (resource_id) REFERENCES service_resource(id),
    CONSTRAINT fk_resource_booking_created_by
        FOREIGN KEY (created_by) REFERENCES account(id),
    CONSTRAINT fk_resource_booking_released_by
        FOREIGN KEY (released_by) REFERENCES account(id),
    CONSTRAINT ck_resource_booking_status
        CHECK (status IN ('RESERVED', 'RELEASED', 'CANCELLED')),
    CONSTRAINT ck_resource_booking_interval
        CHECK (end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='预约房间与设备占用历史';

INSERT IGNORE INTO staff_skill_version (
    tenant_id,
    shop_id,
    staff_id,
    service_id,
    enabled,
    custom_duration_minutes,
    effective_from,
    version
)
SELECT
    s.tenant_id,
    s.shop_id,
    ss.staff_id,
    ss.service_id,
    ss.enabled,
    ss.custom_duration_minutes,
    COALESCE(s.created_at, CURRENT_TIMESTAMP(3)),
    1
FROM staff_service ss
JOIN staff s ON s.id = ss.staff_id
JOIN service_item si
  ON si.id = ss.service_id
 AND si.tenant_id = s.tenant_id;

INSERT IGNORE INTO permission_definition (
    permission_code,
    permission_name,
    module_code,
    risk_level
) VALUES
    ('service:view', '查看服务项目', 'service', 'NORMAL'),
    ('resource:view', '查看房间与设备资源', 'resource', 'NORMAL'),
    ('resource:manage', '管理房间与设备资源', 'resource', 'SENSITIVE');

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_definition r
JOIN permission_definition p
  ON p.permission_code IN ('service:view', 'resource:view', 'resource:manage')
WHERE r.role_code IN ('OWNER', 'REGIONAL_MANAGER', 'MANAGER');

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_definition r
JOIN permission_definition p
  ON p.permission_code IN ('service:view', 'resource:view')
WHERE r.role_code IN ('FRONT_DESK', 'BEAUTICIAN');
