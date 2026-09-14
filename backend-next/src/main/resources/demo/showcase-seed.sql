-- Demo-only, idempotent showcase data. This script never deletes user-created rows.
SET @tenant_id = (SELECT tenant_id FROM shop WHERE id = 1 AND status = 'ACTIVE' LIMIT 1);
SET @shop_id = 1;
SET @admin_id = (SELECT id FROM account WHERE username = 'demo-admin' LIMIT 1);
SET @member_id = (SELECT id FROM member WHERE tenant_id = @tenant_id AND phone = '13900000001' LIMIT 1);
SET @member_account_id = (SELECT id FROM account WHERE tenant_id = @tenant_id AND phone = '13900000001' LIMIT 1);
SET @category_id = (SELECT id FROM service_category WHERE tenant_id = @tenant_id AND shop_id = @shop_id ORDER BY id LIMIT 1);

UPDATE account SET display_name = '演示运营管理员'
WHERE id = @admin_id;
UPDATE member SET name = '体验会员'
WHERE id = @member_id;
UPDATE account SET display_name = '体验会员'
WHERE id = @member_account_id;

-- Five additional management accounts, all using the configured demo account password.
INSERT INTO account (
    tenant_id, home_shop_id, shop_id, username, display_name,
    password_hash, role_code, status
)
SELECT @tenant_id, @shop_id, @shop_id,
       CONCAT('demo-operator-', LPAD(seed.n, 2, '0')),
       CONCAT('演示运营员', seed.n), base.password_hash, 'ADMIN', 'ACTIVE'
FROM account base
JOIN (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
) seed
WHERE base.id = @admin_id
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), password_hash = VALUES(password_hash), status = 'ACTIVE';

INSERT INTO account_shop_role (tenant_id, account_id, region_id, shop_id, role_id, status)
SELECT @tenant_id, account.id, NULL, @shop_id, role_definition.id, 'ACTIVE'
FROM account
JOIN role_definition
  ON role_definition.tenant_id = @tenant_id
 AND role_definition.role_code = 'ADMIN'
 AND role_definition.status = 'ACTIVE'
WHERE account.username LIKE 'demo-operator-%'
  AND NOT EXISTS (
      SELECT 1 FROM account_shop_role existing
      WHERE existing.account_id = account.id
        AND existing.shop_id = @shop_id
        AND existing.role_id = role_definition.id
        AND existing.status = 'ACTIVE'
  );

-- Five additional member records for lists, proxy booking and daily operations.
INSERT INTO member (
    tenant_id, home_shop_id, shop_id, member_no, global_member_no,
    name, phone, gender, source, points, notes, consent_privacy_at, status
) VALUES
(@tenant_id, @shop_id, @shop_id, 'DEMO-M-01', 'DEMO-GM-01', '林晓雨', '13900000011', 'FEMALE', 'DEMO', 320, '偏好舒缓护理', CURRENT_TIMESTAMP(3), 'ACTIVE'),
(@tenant_id, @shop_id, @shop_id, 'DEMO-M-02', 'DEMO-GM-02', '周安然', '13900000012', 'FEMALE', 'DEMO', 580, '关注补水保湿', CURRENT_TIMESTAMP(3), 'ACTIVE'),
(@tenant_id, @shop_id, @shop_id, 'DEMO-M-03', 'DEMO-GM-03', '陈嘉禾', '13900000013', 'MALE', 'DEMO', 260, '首次到店体验', CURRENT_TIMESTAMP(3), 'ACTIVE'),
(@tenant_id, @shop_id, @shop_id, 'DEMO-M-04', 'DEMO-GM-04', '许知夏', '13900000014', 'FEMALE', 'DEMO', 760, '定期进行清洁护理', CURRENT_TIMESTAMP(3), 'ACTIVE'),
(@tenant_id, @shop_id, @shop_id, 'DEMO-M-05', 'DEMO-GM-05', '顾明远', '13900000015', 'MALE', 'DEMO', 410, '偏好晚间预约', CURRENT_TIMESTAMP(3), 'ACTIVE')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), gender = VALUES(gender), notes = VALUES(notes), status = 'ACTIVE';

INSERT INTO member_shop_profile (
    tenant_id, member_id, shop_id, first_visit_at, last_visit_at,
    visit_count, source, shop_notes, status
)
SELECT @tenant_id, member.id, @shop_id,
       DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 120 DAY),
       DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 7 DAY),
       3, 'DEMO', '演示会员资料', 'ACTIVE'
FROM member
WHERE member.tenant_id = @tenant_id AND member.member_no LIKE 'DEMO-M-%'
ON DUPLICATE KEY UPDATE
    last_visit_at = VALUES(last_visit_at), source = 'DEMO', status = 'ACTIVE';

-- Five customer-facing service items.
INSERT INTO service_item (
    tenant_id, shop_id, category_id, service_code, name, subtitle, description,
    duration_minutes, cleanup_minutes, slot_interval_minutes,
    minimum_advance_minutes, same_day_booking_allowed,
    free_cancel_minutes, reschedule_cutoff_minutes, max_reschedules,
    late_cancel_policy, late_cancel_value, booking_terms_version,
    list_price, member_price, applicable_skin_types, contraindications,
    booking_notice, is_featured, status
) VALUES
(@tenant_id,@shop_id,@category_id,'DEMO-SVC-01','水润舒缓护理','温和补水，舒缓干燥紧绷','适合日常补水与换季舒缓。',60,10,60,30,1,1440,720,1,'FULL_REFUND',0,1,198,168,JSON_ARRAY('干性','中性'),'皮肤急性炎症期请暂缓','到店前请保持面部清洁。',1,'ACTIVE'),
(@tenant_id,@shop_id,@category_id,'DEMO-SVC-02','净澈清洁护理','清洁毛孔，改善油脂堆积','包含清洁、软化角质与补水收尾。',75,10,60,60,1,1440,720,1,'FULL_REFUND',0,1,258,218,JSON_ARRAY('油性','混合性'),'严重敏感或破损皮肤请先咨询','护理当天避免使用刺激性产品。',1,'ACTIVE'),
(@tenant_id,@shop_id,@category_id,'DEMO-SVC-03','亮采焕肤护理','改善暗沉，提升细腻光泽','温和焕肤配合修护精华导入。',90,15,60,120,1,1440,720,1,'FULL_REFUND',0,1,368,328,JSON_ARRAY('中性','混合性'),'近期进行医美项目请先咨询','护理后注意防晒和保湿。',1,'ACTIVE'),
(@tenant_id,@shop_id,@category_id,'DEMO-SVC-04','紧致轮廓护理','按摩提拉，放松面部肌肉','结合手法按摩与紧致护理产品。',80,10,60,60,1,1440,720,1,'FULL_REFUND',0,1,328,288,JSON_ARRAY('干性','中性'),'面部术后恢复期不建议体验','请提前告知过敏史。',1,'ACTIVE'),
(@tenant_id,@shop_id,@category_id,'DEMO-SVC-05','敏感修护护理','精简步骤，专注屏障修护','适合泛红、干痒与屏障脆弱时期。',70,15,60,60,1,1440,720,1,'FULL_REFUND',0,1,298,258,JSON_ARRAY('敏感性','干性'),'开放性伤口或感染期请就医','首次体验建议提前进行沟通。',1,'ACTIVE')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), subtitle = VALUES(subtitle), description = VALUES(description),
    booking_terms_version = booking_terms_version + IF(slot_interval_minutes <> 60, 1, 0),
    slot_interval_minutes = 60,
    list_price = VALUES(list_price), member_price = VALUES(member_price),
    booking_notice = VALUES(booking_notice), is_featured = 1, status = 'ACTIVE';

UPDATE service_item
SET cover_url = CASE service_code
    WHEN 'DEMO-SVC-01' THEN '/images/card-hydration.webp'
    WHEN 'DEMO-SVC-02' THEN '/images/card-facial.webp'
    WHEN 'DEMO-SVC-03' THEN '/images/hero-facial.webp'
    WHEN 'DEMO-SVC-04' THEN '/images/card-body.webp'
    WHEN 'DEMO-SVC-05' THEN '/images/path-care.webp'
    ELSE cover_url
END
WHERE tenant_id = @tenant_id AND service_code LIKE 'DEMO-SVC-%';

-- Five public staff profiles and their service skills.
INSERT INTO staff (
    tenant_id, home_shop_id, shop_id, staff_no, name, phone,
    job_role, level_name, bio, hire_date, status
) VALUES
(@tenant_id,@shop_id,@shop_id,'DEMO-ST-01','安然','13900000101','美容护理师','高级护理师','擅长补水修护与敏感肌沟通。',DATE_SUB(CURRENT_DATE,INTERVAL 5 YEAR),'ACTIVE'),
(@tenant_id,@shop_id,@shop_id,'DEMO-ST-02','可欣','13900000102','美容护理师','资深护理师','专注清洁管理与油性肌肤护理。',DATE_SUB(CURRENT_DATE,INTERVAL 4 YEAR),'ACTIVE'),
(@tenant_id,@shop_id,@shop_id,'DEMO-ST-03','若琳','13900000103','美容护理师','高级护理师','擅长亮肤护理与居家保养建议。',DATE_SUB(CURRENT_DATE,INTERVAL 6 YEAR),'ACTIVE'),
(@tenant_id,@shop_id,@shop_id,'DEMO-ST-04','书雅','13900000104','美容护理师','护理师','手法细致，擅长紧致按摩与放松。',DATE_SUB(CURRENT_DATE,INTERVAL 3 YEAR),'ACTIVE'),
(@tenant_id,@shop_id,@shop_id,'DEMO-ST-05','清禾','13900000105','美容护理师','资深护理师','专注屏障修护与敏感肌护理。',DATE_SUB(CURRENT_DATE,INTERVAL 5 YEAR),'ACTIVE')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), job_role = VALUES(job_role), level_name = VALUES(level_name),
    bio = VALUES(bio), status = 'ACTIVE';

UPDATE staff
SET avatar_url = CASE staff_no
    WHEN 'DEMO-ST-01' THEN '/images/staff-avatar.webp'
    WHEN 'DEMO-ST-02' THEN '/images/card-facial.webp'
    WHEN 'DEMO-ST-03' THEN '/images/card-hydration.webp'
    WHEN 'DEMO-ST-04' THEN '/images/path-care.webp'
    WHEN 'DEMO-ST-05' THEN '/images/hero-facial.webp'
    ELSE avatar_url
END
WHERE tenant_id = @tenant_id AND staff_no LIKE 'DEMO-ST-%';

INSERT INTO staff_shop_assignment (
    tenant_id, staff_id, shop_id, assignment_type, effective_from, status
)
SELECT @tenant_id, staff.id, @shop_id, 'PRIMARY', DATE_SUB(CURRENT_DATE, INTERVAL 1 YEAR), 'ACTIVE'
FROM staff
WHERE staff.tenant_id = @tenant_id AND staff.staff_no LIKE 'DEMO-ST-%'
  AND NOT EXISTS (
      SELECT 1 FROM staff_shop_assignment existing
      WHERE existing.staff_id = staff.id AND existing.shop_id = @shop_id
        AND existing.status = 'ACTIVE'
  );

INSERT INTO staff_service (staff_id, service_id, enabled)
SELECT staff.id, service_item.id, 1
FROM staff
JOIN service_item
  ON service_item.tenant_id = staff.tenant_id
 AND service_item.service_code LIKE 'DEMO-SVC-%'
WHERE staff.tenant_id = @tenant_id AND staff.staff_no LIKE 'DEMO-ST-%'
ON DUPLICATE KEY UPDATE enabled = 1;

INSERT INTO staff_schedule_rule (
    tenant_id, shop_id, staff_id, day_of_week, start_time, end_time,
    rule_type, effective_from, status, updated_by
)
SELECT @tenant_id, @shop_id, staff.id,
       CAST(RIGHT(staff.staff_no, 2) AS UNSIGNED), '09:00:00', '18:00:00',
       'WORK', CURRENT_DATE, 'ACTIVE', @admin_id
FROM staff
WHERE staff.tenant_id = @tenant_id AND staff.staff_no LIKE 'DEMO-ST-%'
ON DUPLICATE KEY UPDATE status = 'ACTIVE', updated_by = @admin_id;

INSERT INTO staff_schedule (
    tenant_id, shop_id, staff_id, schedule_date, start_time, end_time,
    schedule_type, remark, status, created_by, updated_by
)
SELECT @tenant_id, @shop_id, staff.id,
       DATE_ADD(CURRENT_DATE, INTERVAL (CAST(RIGHT(staff.staff_no, 2) AS UNSIGNED) - 1) DAY),
       '09:00:00', '18:00:00', 'WORK', '演示排班', 'ACTIVE', @admin_id, @admin_id
FROM staff
WHERE staff.tenant_id = @tenant_id AND staff.staff_no LIKE 'DEMO-ST-%'
  AND NOT EXISTS (
      SELECT 1 FROM staff_schedule existing
      WHERE existing.staff_id = staff.id
        AND existing.schedule_date = DATE_ADD(CURRENT_DATE, INTERVAL (CAST(RIGHT(staff.staff_no, 2) AS UNSIGNED) - 1) DAY)
        AND existing.start_time = '09:00:00' AND existing.end_time = '18:00:00'
  );

-- Five published home/content cards shared by desktop and mobile layouts.
INSERT INTO banner (
    tenant_id, shop_id, content_type, title, summary, body_json,
    target_type, target_value, sort_order, status, published_at, updated_by
)
SELECT @tenant_id, @shop_id, seed.content_type, seed.title, seed.summary,
       JSON_OBJECT('body', seed.body, 'seedCode', seed.seed_code),
       seed.target_type, seed.target_value, seed.sort_order, 'PUBLISHED',
       CURRENT_TIMESTAMP(3), @admin_id
FROM (
    SELECT 'DEMO-CONTENT-01' seed_code, 'BANNER' content_type, '新客舒缓体验' title,
           '首次到店可先从温和补水护理开始。' summary,
           '专业护理师会先沟通肤况，再确认适合的护理步骤。' body,
           'SERVICE' target_type, 'DEMO-SVC-01' target_value, 10 sort_order
    UNION ALL SELECT 'DEMO-CONTENT-02','FEATURED_SERVICE','本周人气项目','清洁、补水与修护项目均可在线预约。','查看时长、价格和注意事项，选择适合自己的到店时间。','SERVICE','DEMO-SVC-02',20
    UNION ALL SELECT 'DEMO-CONTENT-03','FEATURED_PACKAGE','护理卡项推荐','常用护理可选择次数卡，权益和有效期清楚展示。','购买前可查看包含项目、次数与有效期。','NONE','DEMO-CONTENT-03',30
    UNION ALL SELECT 'DEMO-CONTENT-04','ACTIVITY','会员积分周','完成签到、护理和评价均可获得积分。','积分可在商城抵现或兑换精选护理商品。','NONE','DEMO-CONTENT-04',40
    UNION ALL SELECT 'DEMO-CONTENT-05','ANNOUNCEMENT','预约温馨提醒','如需改期，请尽量提前操作。','到店前保持面部清洁，并主动告知近期过敏或医美情况。','NONE','DEMO-CONTENT-05',50
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM banner existing
    WHERE existing.tenant_id = @tenant_id
      AND JSON_UNQUOTE(JSON_EXTRACT(existing.body_json, '$.seedCode')) = seed.seed_code
);

UPDATE banner
SET title = CASE JSON_UNQUOTE(JSON_EXTRACT(body_json, '$.seedCode'))
        WHEN 'DEMO-CONTENT-01' THEN '新客舒缓体验'
        WHEN 'DEMO-CONTENT-02' THEN '本周人气项目'
        WHEN 'DEMO-CONTENT-03' THEN '护理卡项推荐'
        WHEN 'DEMO-CONTENT-04' THEN '会员积分周'
        WHEN 'DEMO-CONTENT-05' THEN '预约温馨提醒'
        ELSE title
    END,
    summary = CASE JSON_UNQUOTE(JSON_EXTRACT(body_json, '$.seedCode'))
        WHEN 'DEMO-CONTENT-01' THEN '首次到店可先从温和补水护理开始。'
        WHEN 'DEMO-CONTENT-02' THEN '清洁、补水与修护项目均可在线预约。'
        WHEN 'DEMO-CONTENT-03' THEN '常用护理可选择次数卡，权益和有效期清楚展示。'
        WHEN 'DEMO-CONTENT-04' THEN '完成签到、护理和评价均可获得积分。'
        WHEN 'DEMO-CONTENT-05' THEN '如需改期，请尽量提前操作。'
        ELSE summary
    END,
    image_url = CASE JSON_UNQUOTE(JSON_EXTRACT(body_json, '$.seedCode'))
        WHEN 'DEMO-CONTENT-01' THEN '/images/hero-facial.webp'
        WHEN 'DEMO-CONTENT-02' THEN '/images/hero-hydration.webp'
        WHEN 'DEMO-CONTENT-03' THEN '/images/path-packages.webp'
        WHEN 'DEMO-CONTENT-04' THEN '/images/card-hydration.webp'
        WHEN 'DEMO-CONTENT-05' THEN '/images/path-care.webp'
        ELSE image_url
    END,
    status = 'PUBLISHED'
WHERE tenant_id = @tenant_id
  AND JSON_UNQUOTE(JSON_EXTRACT(body_json, '$.seedCode')) LIKE 'DEMO-CONTENT-%';

-- Five card products.
INSERT INTO package_product (
    tenant_id, shop_id, package_code, card_type, name, description,
    sale_price, principal_amount, gift_amount, usage_limit,
    scope_json, validity_days, status, created_by, updated_by
) VALUES
(@tenant_id,@shop_id,'DEMO-CARD-01','COMBO_TIMES','水润舒缓 5 次卡','适合定期补水与换季修护。',699,699,0,5,JSON_OBJECT('serviceCodes',JSON_ARRAY('DEMO-SVC-01')),180,'ACTIVE',@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-CARD-02','COMBO_TIMES','净澈清洁 5 次卡','适合油脂管理与定期清洁。',899,899,0,5,JSON_OBJECT('serviceCodes',JSON_ARRAY('DEMO-SVC-02')),180,'ACTIVE',@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-CARD-03','COMBO_TIMES','亮采焕肤 5 次卡','改善暗沉，按周期进行温和焕肤。',1399,1399,0,5,JSON_OBJECT('serviceCodes',JSON_ARRAY('DEMO-SVC-03')),240,'ACTIVE',@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-CARD-04','COMBO_TIMES','紧致轮廓 5 次卡','适合周期性按摩放松与紧致护理。',1199,1199,0,5,JSON_OBJECT('serviceCodes',JSON_ARRAY('DEMO-SVC-04')),240,'ACTIVE',@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-CARD-05','COMBO_TIMES','敏感修护 5 次卡','精简温和的屏障修护方案。',999,999,0,5,JSON_OBJECT('serviceCodes',JSON_ARRAY('DEMO-SVC-05')),180,'ACTIVE',@admin_id,@admin_id)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description), sale_price = VALUES(sale_price),
    scope_json = VALUES(scope_json), validity_days = VALUES(validity_days), status = 'ACTIVE';

INSERT INTO package_product_item (
    tenant_id, package_product_id, service_id, service_name_snapshot,
    quantity_total, sort_order
)
SELECT @tenant_id, package_product.id, service_item.id, service_item.name, 5, 1
FROM package_product
JOIN service_item
  ON service_item.tenant_id = package_product.tenant_id
 AND RIGHT(service_item.service_code, 2) = RIGHT(package_product.package_code, 2)
WHERE package_product.tenant_id = @tenant_id
  AND package_product.package_code LIKE 'DEMO-CARD-%'
ON DUPLICATE KEY UPDATE
    service_name_snapshot = VALUES(service_name_snapshot), quantity_total = 5;

-- Five coupon templates and five coupons owned by the primary demo member.
INSERT INTO coupon_template (
    tenant_id, shop_id, template_code, name, coupon_type,
    threshold_amount, benefit_value, validity_days,
    return_on_full_refund, status, created_by, updated_by
) VALUES
(@tenant_id,@shop_id,'DEMO-COUPON-01','满 199 减 20','THRESHOLD_REDUCTION',199,20,30,1,'ACTIVE',@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-COUPON-02','满 299 减 40','THRESHOLD_REDUCTION',299,40,45,1,'ACTIVE',@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-COUPON-03','30 元护理券','CASH',0,30,30,1,'ACTIVE',@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-COUPON-04','九折护理券','DISCOUNT',0,90,60,1,'ACTIVE',@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-COUPON-05','舒缓护理体验券','SERVICE_EXPERIENCE',0,1,30,1,'ACTIVE',@admin_id,@admin_id)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), threshold_amount = VALUES(threshold_amount),
    benefit_value = VALUES(benefit_value), validity_days = VALUES(validity_days), status = 'ACTIVE';

INSERT INTO member_coupon (
    tenant_id, shop_id, member_id, template_id, coupon_no,
    source_type, source_reference, valid_from, valid_until,
    status, created_by
)
SELECT @tenant_id, @shop_id, @member_id, coupon_template.id,
       CONCAT('DEMO-MC-', RIGHT(coupon_template.template_code, 2)),
       'ADMIN_DIRECT', coupon_template.template_code,
       CURRENT_TIMESTAMP(3), DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL coupon_template.validity_days DAY),
       'AVAILABLE', @admin_id
FROM coupon_template
WHERE coupon_template.tenant_id = @tenant_id
  AND coupon_template.template_code LIKE 'DEMO-COUPON-%'
ON DUPLICATE KEY UPDATE
    valid_until = VALUES(valid_until), status = 'AVAILABLE';

-- Five card instances owned by the primary demo member.
INSERT INTO package_instance (
    tenant_id, shop_id, member_id, package_product_id, instance_no,
    card_type, source_type, source_reference, purchase_price,
    valid_from, valid_until, total_quantity, remaining_quantity,
    status, issue_idempotency_key, issue_request_hash, created_by, updated_by
)
SELECT @tenant_id, @shop_id, @member_id, package_product.id,
       CONCAT('DEMO-PI-', RIGHT(package_product.package_code, 2)),
       package_product.card_type, 'GIFT', package_product.package_code,
       package_product.sale_price, CURRENT_DATE,
       DATE_ADD(CURRENT_DATE, INTERVAL package_product.validity_days DAY),
       5, 5, 'ACTIVE', CONCAT('demo-package-', RIGHT(package_product.package_code, 2)),
       REPEAT(RIGHT(package_product.package_code, 1), 64), @admin_id, @admin_id
FROM package_product
WHERE package_product.tenant_id = @tenant_id
  AND package_product.package_code LIKE 'DEMO-CARD-%'
ON DUPLICATE KEY UPDATE
    valid_until = VALUES(valid_until), remaining_quantity = 5, status = 'ACTIVE';

INSERT INTO package_instance_item (
    tenant_id, package_instance_id, service_id, service_name_snapshot,
    total_quantity, remaining_quantity, frozen_quantity
)
SELECT @tenant_id, package_instance.id, package_product_item.service_id,
       package_product_item.service_name_snapshot, 5, 5, 0
FROM package_instance
JOIN package_product_item
  ON package_product_item.package_product_id = package_instance.package_product_id
WHERE package_instance.tenant_id = @tenant_id
  AND package_instance.instance_no LIKE 'DEMO-PI-%'
ON DUPLICATE KEY UPDATE
    service_name_snapshot = VALUES(service_name_snapshot), remaining_quantity = 5;

-- Five plain-language points tasks.
INSERT INTO points_task (
    tenant_id, shop_id, task_code, task_type, name, reward_points,
    cycle_days, daily_rewards_json, cycle_bonus_points,
    repeat_cycle, member_period_cap_points, status, created_by
) VALUES
(@tenant_id,@shop_id,'DEMO-POINTS-01','REGISTER','完成会员注册',50,1,JSON_ARRAY(50),0,0,50,'ACTIVE',@admin_id),
(@tenant_id,@shop_id,'DEMO-POINTS-02','PROFILE','完善个人资料',30,1,JSON_ARRAY(30),0,0,30,'ACTIVE',@admin_id),
(@tenant_id,@shop_id,'DEMO-POINTS-03','CHECKIN','每日到店签到',10,7,JSON_ARRAY(10,10,10,10,10,15,20),20,1,120,'ACTIVE',@admin_id),
(@tenant_id,@shop_id,'DEMO-POINTS-04','CARE_COMPLETE','完成一次护理',80,30,JSON_ARRAY(80),0,1,400,'ACTIVE',@admin_id),
(@tenant_id,@shop_id,'DEMO-POINTS-05','REVIEW','提交真实评价',20,30,JSON_ARRAY(20),0,1,100,'ACTIVE',@admin_id)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), reward_points = VALUES(reward_points),
    daily_rewards_json = VALUES(daily_rewards_json), status = 'ACTIVE';

-- Five products and SKUs for the points store and admin inventory.
INSERT INTO mall_category (tenant_id, category_code, name, sort_order, status)
VALUES (@tenant_id, 'DEMO-CARE', '居家护理', 10, 'ACTIVE')
ON DUPLICATE KEY UPDATE name = '居家护理', status = 'ACTIVE';
SET @mall_category_id = (SELECT id FROM mall_category WHERE tenant_id = @tenant_id AND category_code = 'DEMO-CARE');

INSERT INTO mall_product (
    tenant_id, shop_id, category_id, product_code, product_type,
    name, brand_name, description, delivery_mode,
    separate_shipping, after_sale_policy, status, created_by
) VALUES
(@tenant_id,@shop_id,@mall_category_id,'DEMO-PROD-01','PHYSICAL','舒缓补水面膜 5 片装','FACE 精选','日常补水与换季舒缓。','BOTH',0,'未拆封商品支持 7 天售后。','ON_SALE',@admin_id),
(@tenant_id,@shop_id,@mall_category_id,'DEMO-PROD-02','PHYSICAL','温和洁面泡沫','FACE 精选','清洁后不紧绷，适合每日使用。','BOTH',0,'未拆封商品支持 7 天售后。','ON_SALE',@admin_id),
(@tenant_id,@shop_id,@mall_category_id,'DEMO-PROD-03','PHYSICAL','修护保湿喷雾','FACE 精选','随时补水，缓解空调环境干燥。','BOTH',0,'未拆封商品支持 7 天售后。','ON_SALE',@admin_id),
(@tenant_id,@shop_id,@mall_category_id,'DEMO-PROD-04','PHYSICAL','轻润修护乳','FACE 精选','轻盈保湿，适合日常屏障护理。','BOTH',0,'未拆封商品支持 7 天售后。','ON_SALE',@admin_id),
(@tenant_id,@shop_id,@mall_category_id,'DEMO-PROD-05','PHYSICAL','柔软洁面巾 3 包','FACE 精选','一次性洁面巾，柔软亲肤。','BOTH',0,'未拆封商品支持 7 天售后。','ON_SALE',@admin_id)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), brand_name = VALUES(brand_name), description = VALUES(description),
    delivery_mode = VALUES(delivery_mode), status = 'ON_SALE';

UPDATE mall_product
SET brand_name = 'FACE 精选',
    cover_url = CASE product_code
        WHEN 'DEMO-PROD-01' THEN '/images/card-hydration.webp'
        WHEN 'DEMO-PROD-02' THEN '/images/card-facial.webp'
        WHEN 'DEMO-PROD-03' THEN '/images/hero-hydration.webp'
        WHEN 'DEMO-PROD-04' THEN '/images/path-care.webp'
        WHEN 'DEMO-PROD-05' THEN '/images/path-services.webp'
        ELSE cover_url
    END
WHERE tenant_id = @tenant_id AND product_code LIKE 'DEMO-PROD-%';

INSERT INTO mall_sku (
    tenant_id, product_id, sku_code, spec_json,
    cash_price, points_price, combo_cash_price, combo_points_price,
    cash_enabled, points_enabled, combo_enabled, purchase_limit, status
)
SELECT @tenant_id, mall_product.id,
       CONCAT('DEMO-SKU-', RIGHT(mall_product.product_code, 2)),
       JSON_OBJECT('规格', '标准装'),
       39 + CAST(RIGHT(mall_product.product_code, 2) AS UNSIGNED) * 10,
       500 + CAST(RIGHT(mall_product.product_code, 2) AS UNSIGNED) * 100,
       19 + CAST(RIGHT(mall_product.product_code, 2) AS UNSIGNED) * 5,
       250 + CAST(RIGHT(mall_product.product_code, 2) AS UNSIGNED) * 50,
       1, 1, 1, 5, 'ACTIVE'
FROM mall_product
WHERE mall_product.tenant_id = @tenant_id AND mall_product.product_code LIKE 'DEMO-PROD-%'
ON DUPLICATE KEY UPDATE
    cash_price = VALUES(cash_price), points_price = VALUES(points_price),
    cash_enabled = 1, points_enabled = 1, combo_enabled = 1, status = 'ACTIVE';

INSERT INTO mall_sku_inventory (
    tenant_id, shop_id, sku_id, available_quantity, reserved_quantity,
    sold_quantity, warning_threshold
)
SELECT @tenant_id, @shop_id, mall_sku.id, 20, 0, 0, 5
FROM mall_sku
WHERE mall_sku.tenant_id = @tenant_id AND mall_sku.sku_code LIKE 'DEMO-SKU-%'
ON DUPLICATE KEY UPDATE
    available_quantity = GREATEST(available_quantity, 20), warning_threshold = 5;

-- Five completed appointments, service records and customer confirmations.
INSERT INTO appointment (
    tenant_id, shop_id, appointment_no, member_id, staff_id,
    start_at, end_at, occupied_start_at, occupied_end_at,
    status, fulfillment_status, source, member_note,
    terms_version, terms_confirmed_at, completed_at
)
SELECT @tenant_id, @shop_id,
       CONCAT('DEMO-APT-', RIGHT(staff.staff_no, 2)),
       @member_id, staff.id,
       TIMESTAMP(DATE_SUB(CURRENT_DATE, INTERVAL CAST(RIGHT(staff.staff_no, 2) AS UNSIGNED) DAY), '10:00:00'),
       TIMESTAMP(DATE_SUB(CURRENT_DATE, INTERVAL CAST(RIGHT(staff.staff_no, 2) AS UNSIGNED) DAY), '11:00:00'),
       TIMESTAMP(DATE_SUB(CURRENT_DATE, INTERVAL CAST(RIGHT(staff.staff_no, 2) AS UNSIGNED) DAY), '09:50:00'),
       TIMESTAMP(DATE_SUB(CURRENT_DATE, INTERVAL CAST(RIGHT(staff.staff_no, 2) AS UNSIGNED) DAY), '11:10:00'),
       'COMPLETED', 'COMPLETED', 'ONLINE', '演示预约记录',
       1,
       TIMESTAMP(DATE_SUB(CURRENT_DATE, INTERVAL (CAST(RIGHT(staff.staff_no, 2) AS UNSIGNED) + 2) DAY), '09:00:00'),
       TIMESTAMP(DATE_SUB(CURRENT_DATE, INTERVAL CAST(RIGHT(staff.staff_no, 2) AS UNSIGNED) DAY), '11:00:00')
FROM staff
WHERE staff.tenant_id = @tenant_id AND staff.staff_no LIKE 'DEMO-ST-%'
ON DUPLICATE KEY UPDATE
    staff_id = VALUES(staff_id), start_at = VALUES(start_at), end_at = VALUES(end_at),
    occupied_start_at = VALUES(occupied_start_at), occupied_end_at = VALUES(occupied_end_at),
    status = 'COMPLETED', fulfillment_status = 'COMPLETED', completed_at = VALUES(completed_at);

INSERT INTO appointment_item (
    appointment_id, service_id, service_name_snapshot,
    duration_minutes_snapshot, price_snapshot, sort_order
)
SELECT appointment.id, service_item.id, service_item.name,
       service_item.duration_minutes, service_item.member_price, 1
FROM appointment
JOIN service_item
  ON service_item.tenant_id = appointment.tenant_id
 AND RIGHT(service_item.service_code, 2) = RIGHT(appointment.appointment_no, 2)
WHERE appointment.tenant_id = @tenant_id AND appointment.appointment_no LIKE 'DEMO-APT-%'
  AND NOT EXISTS (
      SELECT 1 FROM appointment_item existing WHERE existing.appointment_id = appointment.id
  );

INSERT INTO service_record (
    tenant_id, record_no, completion_idempotency_key, completion_request_hash,
    shop_id, appointment_id, member_id, staff_id,
    actual_start_at, actual_end_at, service_summary,
    next_visit_recommendation, status, fulfillment_status,
    customer_confirmed_at, created_by, updated_by
)
SELECT @tenant_id, CONCAT('DEMO-SR-', RIGHT(appointment.appointment_no, 2)),
       CONCAT('demo-service-complete-', RIGHT(appointment.appointment_no, 2)),
       REPEAT(RIGHT(appointment.appointment_no, 1), 64),
       @shop_id, appointment.id, @member_id, appointment.staff_id,
       appointment.start_at, appointment.end_at,
       '已按预约完成护理，过程顺利。', '建议 2 至 4 周后根据肤况复访。',
       'COMPLETED', 'COMPLETED', appointment.end_at, @admin_id, @admin_id
FROM appointment
WHERE appointment.tenant_id = @tenant_id AND appointment.appointment_no LIKE 'DEMO-APT-%'
ON DUPLICATE KEY UPDATE
    actual_start_at = VALUES(actual_start_at), actual_end_at = VALUES(actual_end_at),
    service_summary = VALUES(service_summary), status = 'COMPLETED', fulfillment_status = 'COMPLETED';

INSERT INTO customer_confirmation (
    tenant_id, shop_id, appointment_id, service_record_id, member_id,
    confirmation_type, status, idempotency_key, request_hash,
    acted_by, acted_at, finalization_source, finalized_at,
    created_by, updated_by
)
SELECT @tenant_id, @shop_id, service_record.appointment_id, service_record.id, @member_id,
       'SERVICE_RESULT', 'CONFIRMED', CONCAT('demo-confirm-', RIGHT(service_record.record_no, 2)),
       REPEAT(RIGHT(service_record.record_no, 1), 64), @member_account_id,
       service_record.actual_end_at, 'MEMBER', service_record.actual_end_at,
       @admin_id, @admin_id
FROM service_record
WHERE service_record.tenant_id = @tenant_id AND service_record.record_no LIKE 'DEMO-SR-%'
ON DUPLICATE KEY UPDATE
    status = 'CONFIRMED', acted_at = VALUES(acted_at), finalized_at = VALUES(finalized_at);

-- Five customer reviews, including two low-score examples for follow-up practice.
INSERT INTO service_review (
    tenant_id, shop_id, member_id, service_record_id, confirmation_id,
    staff_rating, effect_rating, environment_rating, average_rating,
    visibility, moderation_status, current_version_no, wants_contact
)
SELECT @tenant_id, @shop_id, @member_id, service_record.id, customer_confirmation.id,
       CASE WHEN RIGHT(service_record.record_no, 2) IN ('01','02') THEN 3 ELSE 5 END,
       CASE WHEN RIGHT(service_record.record_no, 2) = '01' THEN 2 WHEN RIGHT(service_record.record_no, 2) = '02' THEN 3 ELSE 5 END,
       CASE WHEN RIGHT(service_record.record_no, 2) = '01' THEN 3 ELSE 5 END,
       CASE WHEN RIGHT(service_record.record_no, 2) = '01' THEN 2.7 WHEN RIGHT(service_record.record_no, 2) = '02' THEN 3.7 ELSE 5.0 END,
       'PUBLIC', 'PENDING', 1,
       CASE WHEN RIGHT(service_record.record_no, 2) IN ('01','02') THEN 1 ELSE 0 END
FROM service_record
JOIN customer_confirmation ON customer_confirmation.service_record_id = service_record.id
WHERE service_record.tenant_id = @tenant_id AND service_record.record_no LIKE 'DEMO-SR-%'
ON DUPLICATE KEY UPDATE
    visibility = 'PUBLIC', moderation_status = 'PENDING', deleted_at = NULL;

INSERT INTO service_review_version (
    tenant_id, review_id, version_no, staff_rating, effect_rating,
    environment_rating, average_rating, visibility, content,
    wants_contact, change_type, created_by
)
SELECT @tenant_id, service_review.id, 1,
       service_review.staff_rating, service_review.effect_rating,
       service_review.environment_rating, service_review.average_rating,
       service_review.visibility,
       CASE RIGHT(service_record.record_no, 2)
         WHEN '01' THEN '护理过程很细致，但这次保湿效果没有达到预期，希望门店联系。'
         WHEN '02' THEN '环境舒适，清洁后局部略有紧绷，希望获得居家护理建议。'
         WHEN '03' THEN '服务讲解清楚，护理后肤色看起来更均匀。'
         WHEN '04' THEN '按摩手法很舒服，整个过程放松安心。'
         ELSE '步骤温和，护理后泛红明显缓解。'
       END,
       service_review.wants_contact, 'CREATED', @member_account_id
FROM service_review
JOIN service_record ON service_record.id = service_review.service_record_id
WHERE service_review.tenant_id = @tenant_id AND service_record.record_no LIKE 'DEMO-SR-%'
ON DUPLICATE KEY UPDATE content = VALUES(content), wants_contact = VALUES(wants_contact);

-- Five review/service after-sale cases.
INSERT INTO after_sale_case (
    tenant_id, shop_id, case_no, member_id, service_record_id,
    category, origin_type, priority, summary, entry_deadline_at,
    status, risk_amount, evidence_json, assignee_account_id,
    create_idempotency_key, create_request_hash, created_by
)
SELECT @tenant_id, @shop_id, CONCAT('DEMO-AS-SVC-', RIGHT(service_record.record_no, 2)),
       @member_id, service_record.id, 'SERVICE_QUALITY',
       CASE WHEN RIGHT(service_record.record_no, 2) IN ('01','02') THEN 'LOW_SCORE_REVIEW' ELSE 'SERVICE_DISPUTE' END,
       CASE WHEN RIGHT(service_record.record_no, 2) IN ('01','02') THEN 'HIGH' ELSE 'NORMAL' END,
       CASE RIGHT(service_record.record_no, 2)
         WHEN '01' THEN '顾客反馈保湿效果未达到预期'
         WHEN '02' THEN '顾客护理后局部感觉紧绷'
         WHEN '03' THEN '顾客咨询护理后的防晒建议'
         WHEN '04' THEN '顾客希望调整下次护理手法力度'
         ELSE '顾客希望确认敏感期居家护理步骤'
       END,
       DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR),
       CASE WHEN RIGHT(service_record.record_no, 2) = '03' THEN 'WAITING_CUSTOMER' ELSE 'OPEN' END,
       0, JSON_OBJECT('source','DEMO_SEED'), @admin_id,
       CONCAT('demo-aftersale-service-', RIGHT(service_record.record_no, 2)),
       REPEAT(RIGHT(service_record.record_no, 1), 64), @admin_id
FROM service_record
WHERE service_record.tenant_id = @tenant_id AND service_record.record_no LIKE 'DEMO-SR-%'
ON DUPLICATE KEY UPDATE
    summary = VALUES(summary), assignee_account_id = @admin_id;

UPDATE service_review
JOIN service_record ON service_record.id = service_review.service_record_id
JOIN after_sale_case
  ON after_sale_case.tenant_id = service_review.tenant_id
 AND after_sale_case.case_no = CONCAT('DEMO-AS-SVC-', RIGHT(service_record.record_no, 2))
SET service_review.after_sale_case_id = after_sale_case.id
WHERE service_review.tenant_id = @tenant_id
  AND service_record.record_no LIKE 'DEMO-SR-%';

-- Five mall orders.
INSERT INTO mall_order (
    tenant_id, shop_id, member_id, order_no, cash_amount, points_amount,
    status, address_snapshot_json, create_idempotency_key,
    create_request_hash, created_by, created_at, paid_at
) VALUES
(@tenant_id,@shop_id,@member_id,'DEMO-MO-01',49,0,'COMPLETED',JSON_OBJECT('deliveryMode','PICKUP'),'demo-mall-order-01',REPEAT('1',64),@member_account_id,DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 10 DAY),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 10 DAY)),
(@tenant_id,@shop_id,@member_id,'DEMO-MO-02',0,700,'COMPLETED',JSON_OBJECT('deliveryMode','PICKUP'),'demo-mall-order-02',REPEAT('2',64),@member_account_id,DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 9 DAY),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 9 DAY)),
(@tenant_id,@shop_id,@member_id,'DEMO-MO-03',34,400,'COMPLETED',JSON_OBJECT('deliveryMode','DELIVERY'),'demo-mall-order-03',REPEAT('3',64),@member_account_id,DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 8 DAY),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 8 DAY)),
(@tenant_id,@shop_id,@member_id,'DEMO-MO-04',79,0,'COMPLETED',JSON_OBJECT('deliveryMode','DELIVERY'),'demo-mall-order-04',REPEAT('4',64),@member_account_id,DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 7 DAY),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 7 DAY)),
(@tenant_id,@shop_id,@member_id,'DEMO-MO-05',0,1000,'COMPLETED',JSON_OBJECT('deliveryMode','PICKUP'),'demo-mall-order-05',REPEAT('5',64),@member_account_id,DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 DAY),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 DAY))
ON DUPLICATE KEY UPDATE
    cash_amount = VALUES(cash_amount), points_amount = VALUES(points_amount), status = VALUES(status);

-- Five product return cases and five return requests.
INSERT INTO after_sale_case (
    tenant_id, shop_id, case_no, member_id, category, origin_type,
    priority, summary, status, risk_amount, evidence_json,
    assignee_account_id, create_idempotency_key, create_request_hash, created_by
)
SELECT @tenant_id, @shop_id, CONCAT('DEMO-AS-RETURN-', RIGHT(mall_order.order_no, 2)),
       @member_id, 'PRODUCT', 'MALL_RETURN', 'NORMAL',
       CONCAT('商城订单 ', mall_order.order_no, ' 申请退货'),
       'OPEN', mall_order.cash_amount, JSON_OBJECT('source','DEMO_SEED'), @admin_id,
       CONCAT('demo-aftersale-return-', RIGHT(mall_order.order_no, 2)),
       REPEAT(RIGHT(mall_order.order_no, 1), 64), @admin_id
FROM mall_order
WHERE mall_order.tenant_id = @tenant_id AND mall_order.order_no LIKE 'DEMO-MO-%'
ON DUPLICATE KEY UPDATE summary = VALUES(summary), risk_amount = VALUES(risk_amount);

INSERT INTO mall_return_request (
    tenant_id, shop_id, member_id, mall_order_id, after_sale_case_id,
    reason_code, reason_detail, status, return_tracking_no,
    evidence_json, idempotency_key, submitted_at
)
SELECT @tenant_id, @shop_id, @member_id, mall_order.id, after_sale_case.id,
       'NOT_SUITABLE',
       CASE RIGHT(mall_order.order_no, 2)
         WHEN '01' THEN '商品未拆封，购买后发现不适合当前肤况。'
         WHEN '02' THEN '希望更换为更温和的居家护理商品。'
         WHEN '03' THEN '收到后发现规格与预期不符。'
         WHEN '04' THEN '重复购买，申请退回未拆封商品。'
         ELSE '暂时不需要该商品，申请按规则退货。'
       END,
       CASE WHEN RIGHT(mall_order.order_no, 2) IN ('04','05') THEN 'PENDING_INSPECTION' ELSE 'SUBMITTED' END,
       CASE WHEN RIGHT(mall_order.order_no, 2) IN ('04','05') THEN CONCAT('SF-DEMO-', RIGHT(mall_order.order_no, 2)) ELSE NULL END,
       JSON_OBJECT('source','DEMO_SEED'),
       CONCAT('demo-return-', RIGHT(mall_order.order_no, 2)),
       DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL CAST(RIGHT(mall_order.order_no, 2) AS UNSIGNED) DAY)
FROM mall_order
JOIN after_sale_case
  ON after_sale_case.tenant_id = mall_order.tenant_id
 AND after_sale_case.case_no = CONCAT('DEMO-AS-RETURN-', RIGHT(mall_order.order_no, 2))
WHERE mall_order.tenant_id = @tenant_id AND mall_order.order_no LIKE 'DEMO-MO-%'
ON DUPLICATE KEY UPDATE
    reason_detail = VALUES(reason_detail), return_tracking_no = VALUES(return_tracking_no);

-- Five unread customer notifications.
INSERT INTO outbox_event (
    event_id, tenant_id, shop_id, aggregate_type, aggregate_id,
    event_type, payload, status, published_at
) VALUES
('10000000-0000-0000-0000-000000000001',@tenant_id,@shop_id,'DEMO','1','DemoNotificationSeeded',JSON_OBJECT('seed',1),'PUBLISHED',CURRENT_TIMESTAMP(3)),
('10000000-0000-0000-0000-000000000002',@tenant_id,@shop_id,'DEMO','2','DemoNotificationSeeded',JSON_OBJECT('seed',2),'PUBLISHED',CURRENT_TIMESTAMP(3)),
('10000000-0000-0000-0000-000000000003',@tenant_id,@shop_id,'DEMO','3','DemoNotificationSeeded',JSON_OBJECT('seed',3),'PUBLISHED',CURRENT_TIMESTAMP(3)),
('10000000-0000-0000-0000-000000000004',@tenant_id,@shop_id,'DEMO','4','DemoNotificationSeeded',JSON_OBJECT('seed',4),'PUBLISHED',CURRENT_TIMESTAMP(3)),
('10000000-0000-0000-0000-000000000005',@tenant_id,@shop_id,'DEMO','5','DemoNotificationSeeded',JSON_OBJECT('seed',5),'PUBLISHED',CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE status = 'PUBLISHED', published_at = VALUES(published_at);

INSERT INTO notification_message (
    tenant_id, shop_id, recipient_account_id, event_id,
    event_type, business_type, business_id, category,
    channel, delivery_status, external_status,
    title, safe_summary, action_path, status
) VALUES
(@tenant_id,@shop_id,@member_account_id,'10000000-0000-0000-0000-000000000001','DemoNotificationSeeded','APPOINTMENT','DEMO-APT-01','SYSTEM','IN_APP','DELIVERED','NOT_REQUESTED','预约已完成','水润舒缓护理已完成，可查看护理记录。','/client/appointments','UNREAD'),
(@tenant_id,@shop_id,@member_account_id,'10000000-0000-0000-0000-000000000002','DemoNotificationSeeded','POINTS','DEMO-POINTS-03','SYSTEM','IN_APP','DELIVERED','NOT_REQUESTED','今日签到有积分','完成签到可获得积分奖励。','/client/points-store','UNREAD'),
(@tenant_id,@shop_id,@member_account_id,'10000000-0000-0000-0000-000000000003','DemoNotificationSeeded','BENEFIT','DEMO-CARD-01','SYSTEM','IN_APP','DELIVERED','NOT_REQUESTED','护理卡已到账','水润舒缓 5 次卡已放入你的权益账户。','/client/benefits','UNREAD'),
(@tenant_id,@shop_id,@member_account_id,'10000000-0000-0000-0000-000000000004','DemoNotificationSeeded','MALL','DEMO-MO-04','SYSTEM','IN_APP','DELIVERED','NOT_REQUESTED','退货等待验货','商品寄回后，门店会完成验货并更新结果。','/client/after-sales','UNREAD'),
(@tenant_id,@shop_id,@member_account_id,'10000000-0000-0000-0000-000000000005','DemoNotificationSeeded','CONTENT','DEMO-CONTENT-05','SYSTEM','IN_APP','DELIVERED','NOT_REQUESTED','护理前温馨提醒','到店前请保持面部清洁，并主动告知近期过敏情况。','/client/notifications','UNREAD')
ON DUPLICATE KEY UPDATE
    title = VALUES(title), safe_summary = VALUES(safe_summary), action_path = VALUES(action_path);

-- Five traceable completed import-history examples (no real customer data is imported).
INSERT INTO legacy_import_batch (
    tenant_id, shop_id, batch_no, file_name, file_sha256, status,
    total_rows, ready_rows, conflict_rows, error_rows,
    imported_members, imported_cards, request_hash,
    started_at, completed_at, created_by, executed_by
) VALUES
(@tenant_id,@shop_id,'DEMO-IMPORT-01','演示-老会员基础资料.xlsx',REPEAT('1',64),'COMPLETED',5,5,0,0,5,0,REPEAT('a',64),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 5 DAY),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 5 DAY),@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-IMPORT-02','演示-历史卡项.xlsx',REPEAT('2',64),'COMPLETED',5,5,0,0,0,5,REPEAT('b',64),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 4 DAY),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 4 DAY),@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-IMPORT-03','演示-会员补充资料.xlsx',REPEAT('3',64),'COMPLETED',5,5,0,0,5,0,REPEAT('c',64),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 3 DAY),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 3 DAY),@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-IMPORT-04','演示-历史权益复核.xlsx',REPEAT('4',64),'COMPLETED',5,5,0,0,0,5,REPEAT('d',64),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 2 DAY),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 2 DAY),@admin_id,@admin_id),
(@tenant_id,@shop_id,'DEMO-IMPORT-05','演示-开业客户清单.xlsx',REPEAT('5',64),'COMPLETED',5,5,0,0,5,0,REPEAT('e',64),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 DAY),DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 DAY),@admin_id,@admin_id)
ON DUPLICATE KEY UPDATE
    file_name = VALUES(file_name), status = 'COMPLETED', total_rows = 5,
    ready_rows = 5, conflict_rows = 0, error_rows = 0,
    completed_at = VALUES(completed_at);
