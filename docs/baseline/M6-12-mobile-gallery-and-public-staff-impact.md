# M6-12 手机端图片画廊与公开技师数据影响说明

## 任务与验收

- 任务：修复 iPhone/微信内置浏览器中项目和技师画廊黑图，核对首页技师数量。
- 验收：手机端不创建 WebGL canvas，使用可横向滚动的原生图片卡片；首屏图片能在公网完成解码；技师数量仍由员工、门店分配和启停状态决定，不由前端伪造。

## 影响模块

- 顾客端：`CircularGallery.vue`、首页移动端画廊高度和展示策略。
- 媒体资源：`upload/service-catalog` 新增同名 WebP 缩略图，原 PNG 保留。
- 数据：`service_item.cover_url` 仅将受控路径 `upload/service-catalog/*.png` 改为同名 `.webp`。
- 不影响：预约状态机、权限、支付、套餐、库存、提成、API 字段契约。

## 迁移与兼容

- Flyway：`V2026080201__optimize_public_service_media.sql`。
- 表结构、索引、约束不变。
- 新旧图片同时保留，旧客户端仍可访问 PNG。
- 回滚 SQL：

```sql
UPDATE `service_item`
SET `cover_url` = CONCAT(LEFT(`cover_url`, LENGTH(`cover_url`) - 5), '.png')
WHERE `cover_url` LIKE 'upload/service-catalog/%.webp';
```

## 技师数据结论

门店 1 当前只有 2 条启用员工分配；其中 1 位配置了 20 个项目技能，另 1 位未配置项目技能。本次不用重复卡片或虚构员工扩大数量；新增技师应走员工档案、门店分配、技能版本和账号权限流程。

## 验证

- `npm --prefix front-next test`
- `npm --prefix front-next run build`
- `mvn -f backend-next/pom.xml clean verify`
- `docker compose --project-name face-demo --env-file .env.docker.test up -d --build --wait`
- `node scripts/verify-public-mobile-gallery.mjs`
