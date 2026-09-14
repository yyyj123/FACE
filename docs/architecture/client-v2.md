# FACE Client V2 兼容架构说明

> V3.0 状态：`front-next` 作为响应式 H5/Web 用户端继续交付；原技师模式已冻结，仅保留兼容代码，不提供公开产品入口。

## Current runtime

- 用户端：内部开发地址 `http://127.0.0.1:8082`，外部部署路径 `/client/`。
- 运营管理平台：内部开发地址 `http://127.0.0.1:8081`，外部部署路径 `/admin/`。
- 统一后端：内部 readiness `http://127.0.0.1:8090/face-next/actuator/health/readiness`，外部部署路径 `/api/`。
- 旧 Vue 2 用户端：仅迁移参考和回退资产，不是当前运行时依赖。

`front-next` 使用 Vue 3、TypeScript、Vite、Pinia 和 Vue Router。V3.0 对外只呈现顾客体验；后续微信小程序复用 `backend-next` 的账户、领域服务与数据模型。

## Active role boundary

- `MEMBER` 可以浏览公开目录、注册、管理本人资料、创建预约、查看本人预约，并在服务开始前取消预约。
- 运营账户使用 `admin-next`，不通过用户端登录入口承载复杂岗位导航。
- 服务端从 token 关联账户推导会员/员工范围；客户端不能通过修改 `memberId` 读取或创建他人预约。

## Frozen compatibility boundary

- `BEAUTICIAN` 技师模式、`TechnicianShell.vue`、技师相关接口和 Docker `technician` 服务保留，用于历史兼容与追溯。
- 冻结资产不在 `/` 或用户端登录页展示入口，不作为 V3.0 当前验收对象。
- SC0 不删除冻结代码、不修改其权限或接口行为；是否进一步处置必须经过后续阶段单独批准。

## Data sources

| 用户端区域 | 数据来源 |
|---|---|
| 首页轮播 | `banner` / `GET /api/v2/client/public/banners` |
| 服务目录 | `service_category`、`service_item` |
| 可预约技师展示 | `staff`、有效 `staff_shop_assignment`、`staff_service` |
| 可用时间 | `staff_schedule` 减去有效 `appointment` 冲突 |
| 会员中心 | token 关联的 `member` 和范围内 `appointment` |

公开目录位于 `/api/v2/client/public`；登录和注册位于 `/api/v2/client/auth`；个人数据和预约变更需要 bearer token。现有 `/api/v2` 契约在 SC0 保持不变，部署时由统一 `/api/` 前缀代理。

## Verification

```powershell
cd E:\face
npm --prefix .\front-next run test
npm --prefix .\front-next run build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-client-v2.ps1
```

`verify-client-v2.ps1` 仍覆盖历史兼容行为，其中技师账户相关断言是回归保护，不代表技师端重新进入 V3.0 交付范围。
