# FACE 美容院店铺系统后端

这是从原汽车服务项目复制出的独立后端副本。原始目录不会被修改。旧 `com.controller`、`com.entity` 等生成代码暂时保留，便于按页面逐步迁移；新的美容院核心接口位于 `com.face`，只访问规范化的 `face_salon` 数据表。

## 启动

1. 使用 MySQL 8 执行 `db/face_salon_mysql8.sql`。
2. 按需设置环境变量 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。
3. 执行 `mvn spring-boot:run`。
4. 健康检查：`GET http://localhost:8080/face/api/v1/health`。

开发初始化账号为 `admin / Face@123`，首次登录后必须修改。管理端登录接口为 `POST /face/api/v1/auth/login`。

## 已实现的核心能力

- BCrypt 账号登录和一小时令牌。
- 项目分类、项目、美容师、轮播和可用时段查询。
- 预约创建事务：校验门店主体、美容师项目资质、工作排班、请假/锁定时段和预约重叠。
- 预约状态机与乐观版本控制。
- 管理端会员分页/新建、员工排班和 ECharts 工作台聚合数据。

## 迁移边界

旧拼音路径是兼容源码，不应再作为新页面的数据契约。新页面应只调用 `/api/v1`。护理档案、结算、库存和评价的数据表已经设计，业务接口按 `docs/architecture/api-contract.md` 的后续阶段继续实现。
