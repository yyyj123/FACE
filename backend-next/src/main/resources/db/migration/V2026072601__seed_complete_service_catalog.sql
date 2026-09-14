-- FACE client catalog: remove the unused catch-all category and keep five
-- active, image-backed services in every client-facing category for shop 1.

DELETE FROM service_category
WHERE shop_id = 1
  AND name = '未分类'
  AND NOT EXISTS (
      SELECT 1
      FROM service_item
      WHERE service_item.category_id = service_category.id
  );

UPDATE service_category
SET sort_order = CASE name
    WHEN '补水保湿' THEN 10
    WHEN '面部护理' THEN 20
    WHEN '身体护理' THEN 30
    WHEN '美甲美睫' THEN 40
    WHEN '护理套餐' THEN 50
    WHEN '敏感修护' THEN 60
    WHEN '清洁管理' THEN 70
    WHEN '紧致抗老' THEN 80
    WHEN '芳香舒压' THEN 90
    ELSE sort_order
END
WHERE shop_id = 1;

-- Preserve the identifiers of the eight existing services so historical
-- appointments and staff capabilities remain valid.
UPDATE service_item
SET category_id = (SELECT id FROM service_category WHERE shop_id = 1 AND name = '补水保湿'),
    name = '云感深层补水',
    subtitle = '密集补充水分，改善干燥紧绷',
    cover_url = 'upload/service-catalog/hydration-01-cloud-deep-hydration.png',
    description = '以分层补水与轻柔按摩帮助肌肤恢复柔润光泽，适合缺水、紧绷和换季干燥状态。',
    duration_minutes = 60,
    cleanup_minutes = 10,
    list_price = 298.00,
    member_price = 238.00,
    is_featured = 1,
    status = 'ACTIVE'
WHERE id = 2607230707;

UPDATE service_item
SET name = '肌底焕亮面护',
    subtitle = '温和焕亮，改善倦容与暗沉',
    cover_url = 'upload/service-catalog/facial-01-skin-glow.png',
    description = '通过温和清洁、柔润导入与面部放松护理，让肤色呈现自然通透感。',
    duration_minutes = 60,
    cleanup_minutes = 10,
    list_price = 328.00,
    member_price = 268.00,
    is_featured = 1,
    status = 'ACTIVE'
WHERE id = 1;

UPDATE service_item
SET name = '经络轻盈面护',
    subtitle = '舒展面部压力，找回轻盈状态',
    cover_url = 'upload/service-catalog/facial-02-meridian-relax.png',
    description = '以专业面部按摩手法舒缓紧张区域，配合滋养护理提升整体舒适度。',
    duration_minutes = 75,
    cleanup_minutes = 10,
    list_price = 368.00,
    member_price = 298.00,
    is_featured = 0,
    status = 'ACTIVE'
WHERE id = 2;

UPDATE service_item
SET name = '全身舒压护理',
    subtitle = '释放日常疲惫，恢复身心松弛',
    cover_url = 'upload/service-catalog/body-01-full-relax.png',
    description = '以舒缓节奏进行全身放松护理，重点照顾肩背与四肢的紧张感。',
    duration_minutes = 90,
    cleanup_minutes = 15,
    list_price = 498.00,
    member_price = 398.00,
    is_featured = 1,
    status = 'ACTIVE'
WHERE id = 3;

UPDATE service_item
SET name = '舒缓屏障修护',
    subtitle = '安抚脆弱不适，守护稳定状态',
    cover_url = 'upload/service-catalog/sensitive-01-barrier-repair.png',
    description = '采用温和洁护与屏障友好型产品，减少护理刺激，适合换季和易敏肌肤。',
    duration_minutes = 70,
    cleanup_minutes = 10,
    list_price = 368.00,
    member_price = 298.00,
    is_featured = 1,
    status = 'ACTIVE'
WHERE id = 2607230708;

UPDATE service_item
SET name = '净透毛孔管理',
    subtitle = '分区清洁，改善毛孔堵塞感',
    cover_url = 'upload/service-catalog/clean-01-pore-care.png',
    description = '依据不同区域的油脂与角质状态进行温和清洁，护理后同步补水舒缓。',
    duration_minutes = 70,
    cleanup_minutes = 10,
    list_price = 338.00,
    member_price = 278.00,
    is_featured = 1,
    status = 'ACTIVE'
WHERE id = 2607230709;

UPDATE service_item
SET name = '胶原紧致护理',
    subtitle = '充盈弹润，改善松弛疲态',
    cover_url = 'upload/service-catalog/firming-01-collagen-care.png',
    description = '结合紧致按摩与弹润护理产品，帮助肌肤呈现饱满、细腻的观感。',
    duration_minutes = 80,
    cleanup_minutes = 10,
    list_price = 428.00,
    member_price = 358.00,
    is_featured = 1,
    status = 'ACTIVE'
WHERE id = 2607230710;

UPDATE service_item
SET name = '芳香肩颈舒压',
    subtitle = '植物香气相伴，舒展肩颈压力',
    cover_url = 'upload/service-catalog/aroma-01-shoulder-neck.png',
    description = '以植物精油香气与温和手法放松肩颈，适合久坐、伏案后的日常舒缓。',
    duration_minutes = 60,
    cleanup_minutes = 10,
    list_price = 298.00,
    member_price = 238.00,
    is_featured = 1,
    status = 'ACTIVE'
WHERE id = 2607230711;

INSERT INTO service_item
    (tenant_id, shop_id, category_id, service_code, name, subtitle, cover_url,
     description, duration_minutes, cleanup_minutes, list_price, member_price,
     is_featured, is_chain_standard, status)
SELECT sc.tenant_id, sc.shop_id, sc.id, seed.service_code, seed.name, seed.subtitle,
       seed.cover_url, seed.description, seed.duration_minutes, seed.cleanup_minutes,
       seed.list_price, seed.member_price, seed.is_featured, 1, 'ACTIVE'
FROM service_category sc
JOIN (
    SELECT '补水保湿' category_name, 'HYD-002' service_code, '玻尿酸水光导入' name,
           '清透导入，补充肌肤水润感' subtitle,
           'upload/service-catalog/hydration-02-hyaluronic-infusion.png' cover_url,
           '通过非侵入式水润导入与舒缓护理，改善干燥粗糙，让肌肤触感更细腻。' description,
           70 duration_minutes, 10 cleanup_minutes, 368.00 list_price, 298.00 member_price, 1 is_featured
    UNION ALL SELECT '补水保湿','HYD-003','海藻矿物补水','清凉水润，舒缓晒后干燥',
           'upload/service-catalog/hydration-03-seaweed-mineral.png',
           '以海藻矿物凝胶与湿敷护理补充水分，带来清凉舒适的护理体验。',60,10,328.00,268.00,0
    UNION ALL SELECT '补水保湿','HYD-004','玫瑰沁润护理','花植沁润，恢复柔软光泽',
           'upload/service-catalog/hydration-04-rose-moisture.png',
           '使用玫瑰花水湿敷与柔润按摩，适合干燥、缺乏光泽的日常护理。',60,10,318.00,258.00,0
    UNION ALL SELECT '补水保湿','HYD-005','夜间锁水修护','深度滋养，减少夜间干燥',
           'upload/service-catalog/hydration-05-night-lock.png',
           '以锁水乳霜和温和按摩完成晚间滋养，帮助肌肤维持柔润舒适。',75,10,398.00,328.00,0

    UNION ALL SELECT '面部护理','FAC-003','小颜轮廓按摩','精细手法，舒展下颌与面部压力',
           'upload/service-catalog/facial-03-contour-massage.png',
           '结合天然按摩石与手部护理，重点舒缓下颌、面颊和太阳穴区域。',60,10,358.00,288.00,1
    UNION ALL SELECT '面部护理','FAC-004','活氧焕肤护理','轻盈净肤，呈现清新通透感',
           'upload/service-catalog/facial-04-oxygen-renewal.png',
           '通过柔和泡泡洁护和补水步骤带走表面污垢，护理过程温和舒适。',70,10,388.00,318.00,0
    UNION ALL SELECT '面部护理','FAC-005','黑金奢养面护','矿物奢养，细腻柔润肌肤',
           'upload/service-catalog/facial-05-black-gold.png',
           '使用矿物泥膜与滋养精华完成层次护理，适合重要场合前的精致管理。',90,15,528.00,438.00,1

    UNION ALL SELECT '身体护理','BODY-002','背部净肤护理','温和清洁背部，改善粗糙触感',
           'upload/service-catalog/body-02-back-clarifying.png',
           '分步骤完成背部清洁、矿物泥护理与舒缓保湿，恢复洁净触感。',70,15,368.00,298.00,0
    UNION ALL SELECT '身体护理','BODY-003','腿部轻盈护理','温热舒缓，放松久站疲惫',
           'upload/service-catalog/body-03-leg-lightness.png',
           '以温热敷护与舒缓手法照顾小腿和足部，适合久站久坐后的放松。',60,15,328.00,268.00,0
    UNION ALL SELECT '身体护理','BODY-004','腹部暖养护理','温润暖护，享受安静放松',
           'upload/service-catalog/body-04-abdominal-warmth.png',
           '运用温热护理包和轻柔手法，为腹部带来温暖、舒适的护理体验。',60,15,358.00,288.00,0
    UNION ALL SELECT '身体护理','BODY-005','手臂柔润护理','细致去角质，柔润双臂',
           'upload/service-catalog/body-05-arm-smoothing.png',
           '从温和清洁到身体乳按摩，改善手臂干燥和粗糙触感。',50,10,268.00,218.00,0

    UNION ALL SELECT '美甲美睫','NAIL-001','法式裸粉美甲','清透裸粉，日常百搭',
           'upload/service-catalog/nail-01-french-nude.png',
           '完成基础修型、甲缘护理与裸粉法式造型，风格自然利落。',90,15,298.00,238.00,1
    UNION ALL SELECT '美甲美睫','NAIL-002','琥珀猫眼美甲','流光琥珀，精致显白',
           'upload/service-catalog/nail-02-amber-cat-eye.png',
           '以琥珀色系和细腻猫眼光泽打造层次感，适合轻奢日常造型。',100,15,368.00,298.00,0
    UNION ALL SELECT '美甲美睫','NAIL-003','手部精致护理','柔嫩双手，修护甲缘',
           'upload/service-catalog/nail-03-hand-care.png',
           '包含手部清洁、温和去角质、甲缘整理与滋养手膜。',60,10,228.00,188.00,0
    UNION ALL SELECT '美甲美睫','LASH-001','自然轻盈美睫','根根轻盈，自然放大双眼',
           'upload/service-catalog/nail-04-natural-lash.png',
           '根据眼型设计自然款睫毛，强调轻盈、舒适与日常精致感。',120,20,398.00,328.00,1
    UNION ALL SELECT '美甲美睫','LASH-002','山茶花浓密美睫','层次丰盈，柔和有神',
           'upload/service-catalog/nail-05-camellia-lash.png',
           '使用分层山茶花造型打造柔和浓密感，适合希望增强存在感的顾客。',150,20,498.00,418.00,0

    UNION ALL SELECT '护理套餐','PKG-001','水润焕亮组合','补水与焕亮，一次完整护理',
           'upload/service-catalog/package-01-hydration-glow.png',
           '组合深层补水与温和焕亮步骤，适合重要约会和周期护理。',120,20,598.00,468.00,1
    UNION ALL SELECT '护理套餐','PKG-002','面颈同养套餐','面部与颈部同步精细护理',
           'upload/service-catalog/package-02-face-neck.png',
           '覆盖面部、下颌与颈部的清洁、滋养和放松步骤，护理更完整。',120,20,668.00,528.00,0
    UNION ALL SELECT '护理套餐','PKG-003','头肩面舒压套餐','从头肩到面部，集中释放疲惫',
           'upload/service-catalog/package-03-head-shoulder-face.png',
           '结合头部、肩颈与面部舒压护理，为高强度工作后的顾客提供放松体验。',150,20,728.00,588.00,1
    UNION ALL SELECT '护理套餐','PKG-004','新娘焕采套餐','重要时刻前的精致焕采管理',
           'upload/service-catalog/package-04-bridal-glow.png',
           '包含面部焕采、颈部护理和手部精护，建议在重要日期前预约。',180,30,998.00,798.00,1
    UNION ALL SELECT '护理套餐','PKG-005','月度肌肤管理','四次分阶护理，持续管理状态',
           'upload/service-catalog/package-05-monthly-care.png',
           '按肌肤状态规划四次到店护理，包含记录、复盘与阶段性方案调整。',90,15,1288.00,988.00,0

    UNION ALL SELECT '敏感修护','SENS-002','冷敷褪红护理','清凉安抚，缓解灼热紧绷',
           'upload/service-catalog/sensitive-02-cooling-care.png',
           '使用清凉湿敷和温和保湿步骤安抚不适，减少额外摩擦。',60,10,338.00,278.00,1
    UNION ALL SELECT '敏感修护','SENS-003','燕麦安心护理','燕麦柔护，减少干痒不适',
           'upload/service-catalog/sensitive-03-oat-calm.png',
           '采用燕麦舒缓配方和低刺激护理流程，为脆弱肌肤补充柔润感。',65,10,348.00,288.00,0
    UNION ALL SELECT '敏感修护','SENS-004','角鲨烷修护','补充脂质，改善干燥起皮',
           'upload/service-catalog/sensitive-04-squalane-repair.png',
           '通过角鲨烷精华与封层保湿护理，帮助干燥肌肤保持稳定舒适。',70,10,388.00,318.00,0
    UNION ALL SELECT '敏感修护','SENS-005','季节敏感急护','换季专护，精简温和流程',
           'upload/service-catalog/sensitive-05-seasonal-rescue.png',
           '针对换季脆弱状态精简清洁与护理步骤，优先舒缓和基础保湿。',75,10,418.00,348.00,1

    UNION ALL SELECT '清洁管理','CLEAN-002','温和酵素净肤','软化老废角质，清洁不紧绷',
           'upload/service-catalog/clean-02-enzyme.png',
           '使用温和酵素与湿敷方式整理表层角质，适合常规周期清洁。',60,10,298.00,238.00,0
    UNION ALL SELECT '清洁管理','CLEAN-003','T区黑头管理','聚焦T区，细致分区清洁',
           'upload/service-catalog/clean-03-t-zone.png',
           '重点照顾额头、鼻翼和下巴区域，清洁后完成补水舒缓。',65,10,328.00,268.00,1
    UNION ALL SELECT '清洁管理','CLEAN-004','背部清洁管理','清洁背部油脂与粗糙角质',
           'upload/service-catalog/clean-04-back-clean.png',
           '采用背部分区清洁、温和角质护理与保湿步骤，改善黏腻粗糙感。',75,15,378.00,308.00,0
    UNION ALL SELECT '清洁管理','CLEAN-005','城市净尘护理','卸除污染残留，恢复清爽',
           'upload/service-catalog/clean-05-urban-detox.png',
           '针对通勤环境设计的双重清洁与抗氧化护理，适合城市日常管理。',70,10,358.00,288.00,0

    UNION ALL SELECT '紧致抗老','FIRM-002','小颜提拉护理','精细提拉，塑造利落轮廓',
           'upload/service-catalog/firming-02-contour-lift.png',
           '通过面部与下颌的紧致按摩手法，改善疲惫和松弛观感。',80,10,438.00,368.00,1
    UNION ALL SELECT '紧致抗老','FIRM-003','眼周焕活护理','舒缓眼周疲惫，补充细腻滋养',
           'upload/service-catalog/firming-03-eye-revive.png',
           '以温和眼膜和眼周按摩照顾干燥疲惫，护理过程轻柔克制。',50,10,328.00,268.00,0
    UNION ALL SELECT '紧致抗老','FIRM-004','颈纹淡化护理','专注颈部，柔润紧致肌肤',
           'upload/service-catalog/firming-04-neck-care.png',
           '针对颈部干燥和纹理进行滋养、按摩与保湿封层护理。',60,10,368.00,298.00,0
    UNION ALL SELECT '紧致抗老','FIRM-005','多肽弹润护理','多肽奢养，提升弹润观感',
           'upload/service-catalog/firming-05-peptide.png',
           '结合多肽精华与弹润面膜，为成熟肌肤提供层次滋养。',90,15,528.00,438.00,1

    UNION ALL SELECT '芳香舒压','AROMA-002','薰衣草深眠护理','柔和薰衣草香，进入安静节奏',
           'upload/service-catalog/aroma-02-lavender-sleep.png',
           '运用薰衣草香氛、温热敷护与轻柔手法，帮助身心放慢节奏。',90,15,458.00,368.00,1
    UNION ALL SELECT '芳香舒压','AROMA-003','柑橘活力舒压','明亮柑橘香，唤醒轻快状态',
           'upload/service-catalog/aroma-03-citrus-energy.png',
           '以清新柑橘香气搭配节奏舒缓手法，适合午后恢复精力。',75,15,398.00,328.00,0
    UNION ALL SELECT '芳香舒压','AROMA-004','岩兰草背部舒压','沉稳木质香，深度放松背部',
           'upload/service-catalog/aroma-04-vetiver-back.png',
           '通过岩兰草木质香调与背部放松护理，舒展久坐后的紧张感。',90,15,468.00,388.00,0
    UNION ALL SELECT '芳香舒压','AROMA-005','玫瑰全身芳疗','玫瑰香气相伴，享受完整芳疗',
           'upload/service-catalog/aroma-05-rose-ritual.png',
           '以玫瑰复方精油和舒缓节奏完成全身芳香护理，带来仪式感体验。',120,20,628.00,518.00,1
) seed ON seed.category_name = sc.name
WHERE sc.shop_id = 1
  AND sc.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM service_item existing
      WHERE existing.shop_id = sc.shop_id
        AND existing.service_code = seed.service_code
  );

-- Ensure every seeded client project can enter the booking flow. Keep the
-- assignment scoped to the verified shop beauticians and the senior legacy
-- beautician already used by existing appointments.
INSERT IGNORE INTO staff_service (staff_id, service_id, enabled)
SELECT st.id, si.id, 1
FROM staff st
JOIN service_item si
  ON si.tenant_id = st.tenant_id
 AND si.shop_id = st.shop_id
WHERE st.shop_id = 1
  AND st.status = 'ACTIVE'
  AND si.status = 'ACTIVE'
  AND (st.staff_no LIKE 'FACE_JS%' OR st.id = 2);
