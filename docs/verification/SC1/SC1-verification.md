# SC1 阶段验收报告

阶段：SC1 现状审计与增量领域设计

结论：通过（仅表示 SC1 设计门禁通过，不表示 SC2-SC10 业务能力已实现）

执行日期：2026-08-03（Asia/Shanghai）

产品基线：V3.0 方案一

Git 基线：`6a80b8d07723bed488b7f315bab4a527305fdadb`（`sc0/v1.0.0`）

SC1 设计提交：

- `385c2dea3b1e0a7e953f4839cbd9787922cf1cd7` — `docs(sc1): audit current domain gaps`
- `5b855575969f93f51c4686d713b4af64d52187a3` — `docs(sc1): define incremental domain boundaries`

SC1 版本标签：`sc1/v1.0.0`（本报告提交后创建，指向 SC1 最终证据提交）

## 1. 实际完成范围

- 基于真实 Flyway、Spring JDBC SQL、服务、控制器、安全策略、前端路由与页面，对 MEMBER、APPOINTMENT、ORDER、PACKAGE、AFTERSALE、MARKETING、NOTIFICATION、INVENTORY 八个领域完成逐项审计。
- 输出真实表/记录模型、服务、API、权限、页面和证据路径清单，明确“已实现、部分实现、缺失、冻结”边界。
- 输出数据复用和追加式迁移计划，解决积分、库存、套餐三个重复事实源风险，并给出 SC2-SC10 计划迁移位。
- 输出当前/目标状态机、事务拥有者、幂等键、锁顺序、outbox 和跨模块端口约束。
- 接受 ADR-001，确定继续复用 `backend-next`、不复制 V3 业务表、不做 JPA 前置重写、不允许共享数据库自由写入。
- 为 SC2-SC10 输出文件级实施计划；65 个标为“现有”的路径已逐一验证存在，尚不存在的路径均标为“计划新增”。
- 新增 SC1 自动设计契约，同时检查必需文档、八领域覆盖、ADR、阶段覆盖、Flyway 零变更及 SC1 文件边界。

## 2. 未完成与范围外事项

- 未进入 SC2，未实现短信验证、密码找回、角色收口、单店内容管理或任何新接口。
- 未实现预约时间锁/候补、优惠券、权益预占、积分批次、电商、履约、评价、售后 SLA、数据导入或微信小程序。
- 未修复现有跨模块直接 SQL 写入；本阶段只识别具体位置并冻结后续收口规则。
- 未调整前端页面、路由、菜单、响应式样式或业务交互。
- 未连接、抽样或修改生产/客户数据。

## 3. 修改文件清单

阶段计划与门禁：

- `docs/plans/2026-08-03-sc1.md`
- `scripts/verify-sc1-design-contract.ps1`
- `CONTRIBUTING.md`
- `README.md`
- `PRODUCT.md`

审计与设计：

- `docs/architecture/sc1-current-state-gap-audit.md`
- `docs/architecture/sc1-data-reuse-migration-plan.md`
- `docs/architecture/sc1-state-machine-transaction-boundaries.md`
- `docs/architecture/sc1-follow-up-stage-file-plan.md`
- `docs/adr/ADR-001-v3-incremental-domain-reuse.md`

证据：

- `docs/verification/SC1/SC1-verification.md`

## 4. 新增 Flyway 及数据影响

- 新增 Flyway：无。
- 修改、删除或重排历史迁移：无。
- SC0 基线至 SC1 的迁移目录差异：空。
- 动态盘点迁移文件：30 个；当前排序最后一个为 `V2026080204__backfill_completed_service_orders.sql`。
- 文档中的 `V2026080301` 至 `V2026080307` 只是后续阶段计划位，不是已创建或已保留的版本；每阶段实施前必须重新动态确定版本。
- 表、索引、约束、种子和业务数据写入：无。

## 5. 新增/修改 API

无。`backend-next/src` 相对 `sc0/v1.0.0` 无差异，现有 `/api/v1`、`/api/v2`、`/api/v3` 行为保持不变。后续计划中的控制器路径均标为“计划新增”，不能视为当前 API。

## 6. 权限与状态机变化

无运行时变化。未修改角色、权限判断、路由守卫、领域状态或错误码。

SC1 文档只定义后续目标：统一运营端角色映射、状态机所有者、事务边界和跨模块写入门禁；必须在获批的后续阶段以迁移、实现和测试落地。

## 7. 测试命令、数量、结果

| 命令 | 数量 | 结果 |
|---|---:|---|
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc1-design-contract.ps1` | 1 个 SC1 复合契约 | PASS |
| `mvn -B -ntp -f .\backend-next\pom.xml test` | 165 | 165 通过，0 失败，0 错误，0 跳过 |
| `npm --prefix .\front-next run test` | 6 | 6 通过，0 失败 |
| 解析 `backend-next/target/surefire-reports/TEST-*.xml` | 165 | 与 Maven 汇总一致 |
| SC2-SC10 文件计划中 `[现有]` 路径逐项 `Test-Path` | 65 | 全部存在 |
| `git diff --check` | 全部 SC1 差异 | PASS |

设计契约先失败后通过：首次在必需审计产物尚未建立时失败；产物补齐后又暴露了 PowerShell 严格模式下空管道不是数组的问题。脚本显式数组化结果后通过，并继续覆盖已提交、暂存、未暂存和未跟踪迁移文件。

## 8. 构建和容器结果

| 命令 | 结果 |
|---|---|
| `npm --prefix .\admin-next run build` | PASS，Vite 生产构建完成（1782 modules） |
| `npm --prefix .\front-next run build` | PASS，Vite 生产构建完成（205 modules） |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docker-contract.ps1 -EnvFile .env.docker.test` | `DOCKER_CONTRACT=PASS` |

SC1 没有修改 Compose、镜像、运行配置、后端或迁移，因此未重复执行容器销毁和全生命周期验证；B0-R 两轮干净状态证据仍由 `docs/verification/B0/B0-R-verification.md` 提供。

## 9. 数据核对结果

- `git diff --name-only sc0/v1.0.0..HEAD -- backend-next/src/main/resources/db/migration`：空。
- `git diff --name-only sc0/v1.0.0..HEAD -- backend-next/src admin-next/src front-next/src`：空。
- 当前后端持久化事实以 Flyway 表和 Spring JDBC SQL 为准，不把请求 record、Map 投影或文档概念误报为 JPA 实体。
- 已确认需后续治理的候选重复事实：`member.points`/积分账户、`product.stock_quantity`/库存账本、`care_package`/套餐账户家族。
- 未执行数据回填、对账、删除、修复或迁移。

## 10. 安全、隐私和审计检查

- 未新增外部访问、密钥、账号、真实手机号或客户数据。
- 未改变鉴权、租户/门店范围、审计、支付回调或日志行为。
- 后续设计要求所有新命令有幂等键、固定锁顺序、事务内 outbox 和模块拥有者；这些是实施门禁，不是当前运行时保证。
- 历史冻结模块和复杂角色代码继续保留，SC1 未扩大其公开入口或交付范围。

## 11. 回滚方式

- 审计提交可回滚：`git revert 385c2dea3b1e0a7e953f4839cbd9787922cf1cd7`。
- 设计提交可回滚：`git revert 5b855575969f93f51c4686d713b4af64d52187a3`。
- 最终证据提交可在标签创建后通过 `git log -1 sc1/v1.0.0` 定位并单独回滚。
- 所有回滚均只影响文档和验证脚本，不需要数据库恢复或 Flyway 降级。

## 12. 已知风险与来源限制

- 原始交付目录没有更早 Git 历史；当前事实只能追溯到首次可追溯快照，不能证明此前开发时间线、作者或历史上线顺序。
- 本次审计基于仓库静态代码、迁移与自动测试，没有读取生产数据、访问日志、实际权限分配或第三方支付/短信控制台；数据质量和线上使用频率仍需后续环境验证。
- 当前代码中的跨模块直接 SQL 和兼容投影仍然存在。SC1 解决的是目标所有权和实施顺序，只有后续阶段测试通过后才能宣称风险已消除。
- 计划迁移文件名不是版本预约；并行开发或新增迁移可能改变实际下一个版本号。
- 现有角色多于 V3.0 交付所需角色。如何无损映射历史账号必须在 SC2 结合真实账户分布验证，不能只按角色名批量覆盖。
- 营销现有能力偏触达治理，不等同于定价活动/优惠券；通知当前主要为 IN_APP 投影；库存当前以单 SKU 商品为主。文档已明确这些差距，但均未实现。
- npm 输出包含 `sass_binary_site` 配置弃用警告及 Rollup 注释清理警告；Maven 仍有 Mockito 动态 agent 的未来兼容性提示。测试和构建均通过，但依赖治理仍需后续维护。
- SC1 没有重跑 Docker 全生命周期，风险由运行文件零变更、Docker 契约通过以及 B0-R 的连续两轮证据共同约束。

## 13. 下一阶段前置条件

- 人工审阅现状差距审计、数据复用/迁移计划、状态机/事务边界、ADR-001 和 SC2-SC10 文件级计划。
- 人工确认接受三项单一事实源决策和“跨模块写入必须走应用端口”的门禁。
- 人工明确回复：`通过，进入 SC2`。
- 未收到上述回复前，不得实现 SC2，不得新增业务源码或 Flyway 迁移。

## 工具版本

- Git `2.45.1.windows.1`
- Node.js `v24.18.0`
- npm `11.16.0`
- Maven `3.9.16`
- Java `21.0.11`（Eclipse Adoptium）
- Docker `29.6.1`
