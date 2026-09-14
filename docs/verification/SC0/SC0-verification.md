# SC0 阶段验收报告

阶段：SC0 范围冻结与旧入口收口

结论：通过

执行日期：2026-08-03（Asia/Shanghai）

产品基线：V3.0 方案一

Git 基线：`0adbf9d50419bce6ebdf89df6c448a51ef8573a7`（`b0-r/v1.0.0`）

SC0 提交：

- `8eeba94d60cf351996e8dad3f5afa54e2ea240e7` — `docs(sc0): freeze V3.0 two-surface scope`
- `8382c1517dae9f1cf4146e40e4bcd2d8d0866cdf` — `fix(sc0): remove technician demo entry`

SC0 版本标签：`sc0/v1.0.0`（本报告提交后创建，指向 SC0 最终报告提交）

## 1. 实际完成范围

- 将根产品、设计、仓库说明和执行约束统一到 V3.0 两端范围。
- 明确当前只交付响应式 H5/Web 用户端、响应式运营管理平台和统一后端；后续微信小程序复用同一后端。
- 将独立技师端、复杂多角色后台、采购、调拨/复杂盘点、提成和培训标记为冻结。
- 为 V2.1 历史 PRD 增加“已被 V3.0 取代”声明，原始正文继续保留。
- 输出保留/复用/冻结/新增模块矩阵。
- 删除会员登录页中指向 `8283` 的独立技师端导航，并把管理入口文案收口为运营管理平台。
- 新增自动范围契约，验证当前文档口径、入口删除和冻结资产保留。

## 2. 未完成与范围外事项

- 未进入 SC1，未执行现有领域能力差距分析。
- 未调整管理平台菜单、角色权限或冻结功能的直接 URL；这些不属于 SC0。
- 未实现 `/`、`/client/`、`/admin/`、`/api/` 的最终网关映射；矩阵只冻结目标入口，具体部署由后续阶段实施。
- 未开发微信小程序或新增任何业务功能。

## 3. 修改文件清单

范围与文档：

- `README.md`
- `PRODUCT.md`
- `DESIGN.md`
- `CONTRIBUTING.md`
- `docs/architecture/client-v2.md`
- `docs/architecture/v3-scope-module-matrix.md`
- `docs/operations/docker-deployment.md`
- `docs/specs/FACE_分阶段生产升级PRD_V2.1.md`
- `docs/plans/2026-08-03-sc0.md`

入口与验证：

- `front-next/src/views/LoginView.vue`
- `scripts/verify-sc0-scope.ps1`
- `docs/verification/SC0/SC0-verification.md`

相对 B0-R 基线无删除文件；技师端组件、迁移和 Compose 兼容服务仍存在。

## 4. 新增 Flyway 及数据影响

- 新增 Flyway：无。
- 修改历史迁移：无。
- 表、视图、索引和种子数据变化：无。
- 数据写入或清理：无。

## 5. 新增/修改 API

无。`backend-next` 未发生文件变化，现有 `/api/v1`、`/api/v2`、`/api/v3` 行为保持不变。

## 6. 权限与状态机变化

无。未修改角色、权限判断、路由守卫、领域状态机或错误码。登录页只删除一个导航链接并调整范围文案。

## 7. 测试命令、数量、结果

| 命令 | 数量 | 结果 |
|---|---:|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc0-scope.ps1` | 1 个 SC0 复合契约 | PASS |
| `mvn -B -ntp -f .\backend-next\pom.xml test` | 165 | 165 通过，0 失败，0 错误，0 跳过 |
| `npm --prefix .\front-next run test` | 6 | 6 通过，0 失败 |
| `git diff --check` | 全部 SC0 差异 | PASS |

范围契约按 TDD 执行：首次在未改动产品文档和入口时失败于 `README.md` 缺少 V3.0 基线；最小实现后通过。

## 8. 构建和容器结果

| 命令 | 结果 |
|---|---|
| `npm --prefix .\admin-next run build` | PASS，Vite 生产构建完成 |
| `npm --prefix .\front-next run build` | PASS，Vite 生产构建完成 |
| 构建产物搜索 `8283` | 用户端生产 bundle 中不存在该技师入口 |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docker-contract.ps1 -EnvFile .env.docker.test` | `DOCKER_CONTRACT=PASS` |

SC0 未修改 Compose、镜像、后端或数据库迁移，因此没有重复执行容器生命周期和数据卷清理。最近一次干净状态、连续两轮迁移/健康/重启证据仍为 `docs/verification/B0/B0-R-verification.md`。

## 9. 数据核对结果

- `git diff 0adbf9d50419bce6ebdf89df6c448a51ef8573a7..HEAD -- backend-next docs/database`：无差异。
- `front-next/src/components/TechnicianShell.vue`：存在。
- `backend-next/src/main/resources/db/migration/V2026072602__seed_beautician_demo_account.sql`：存在。
- `compose.yaml` 中 `technician` 兼容服务：存在。
- 未连接、查询或修改客户数据。

## 10. 安全、隐私和审计检查

- 未新增外部访问、密钥、账号或真实数据。
- 未改变鉴权、租户/门店范围、审计或日志行为。
- 移除公开入口不等于安全禁用：冻结技师兼容服务若由运维显式启动，仍可能通过直接端口访问；该事实已在架构矩阵和风险中记录。

## 11. 回滚方式

- 文档范围提交可回滚：`git revert 8eeba94d60cf351996e8dad3f5afa54e2ea240e7`。
- DEMO 入口提交可回滚：`git revert 8382c1517dae9f1cf4146e40e4bcd2d8d0866cdf`。
- 两个回滚均不涉及数据库恢复或迁移降级。
- 回滚会重新暴露已停止交付的技师入口，只有在明确撤销 V3.0 决策后才可执行。

## 12. 已知风险与来源限制

- 原始交付目录没有 Git 历史；当前 Git 仅从 `baseline/first-traceable-v3.0` 开始，不能证明此前开发时间线或作者归属。
- 历史规格和验收文档数量较多，仍会描述技师端、采购、提成和培训；它们作为历史证据保留。根文档、V3.0 PRD 和模块矩阵是当前范围依据。
- 冻结代码和容器未删除，知道直接地址的人员仍可能访问兼容界面；SC0 仅移除公开导航，不实施新的权限封禁。
- 现有管理平台仍物理包含部分冻结菜单和模块。按 SC0“无业务行为变化”门禁，本阶段未隐藏或删除它们；SC1 必须先审计再决定后续处置。
- SC0 没有复跑 Docker 容器生命周期；该风险由未修改运行配置、Docker 契约通过及 B0-R 两轮完整证据共同约束。
- Maven 输出包含 Mockito 动态 agent 的未来兼容性警告；本次 Java 21 测试全部通过，不影响 SC0 结论。

## 13. 下一阶段前置条件

- 人工审阅本报告、模块矩阵与 DEMO 入口差异。
- 人工明确回复：`通过，进入 SC1`。
- 未收到上述回复前，不得开展 SC1 差距分析、领域设计或任何业务功能修改。

## 工具版本

- Git `2.45.1.windows.1`
- Node.js `v24.18.0`
- npm `11.16.0`
- Maven `3.9.16`
- Java `21.0.11`（Eclipse Adoptium）
- Docker `29.6.1`
