# SC4 最终验收报告

阶段：SC4（订单、支付抽象、卡项与护理优惠）  
版本：SC4 1.0.0  
结论：通过  
状态：立即停止，等待人工回复“通过，进入 SC5”

## 1. 基线与可追溯性

| 项目 | 值 |
| --- | --- |
| 产品基线 | `FACE_用户端与运营管理平台增量升级PRD_V3.0.md`，方案一 |
| 首次可追溯快照 SHA | `ff635972a7d4feb4d5192b2f385ebd3703f36326` |
| SC4 起始基线 tag | `sc3/v1.0.0` |
| SC4 起始基线 commit SHA | `005dc1ac2eb65013bd3acca2406704559d520ac0` |
| SC4 已验证实现 SHA | `5ef1ae3674e02ddf7eb53fe9463195424401e8df` |
| SC4 发布 tag | `sc4/v1.0.0`（本报告提交后创建） |
| 后端 artifact | `face-chain-platform:0.1.0-SNAPSHOT` |
| 用户端 package | `face-client-v2@0.2.0` |
| 运营端 package | `face-chain-admin@0.1.0` |

首次快照只代表收到代码后在 2026-08-03 建立的当前首次可追溯状态，不是原始开发历史。SC4 从人工批准的 SC3 tag 增量实施；没有重写既有 Flyway 迁移、伪造历史提交或删除冻结兼容资产。

SC4 计划中曾把 `005dc1ac...` 记为“已验证 SC3 基线”；最终核对确认它同时是 `sc3/v1.0.0` 所指向的 commit。`git rev-parse sc3/v1.0.0` 输出的 `e891d13d...` 是 annotated tag 对象 SHA，不是另一份代码基线。

## 2. 实际完成范围

1. 单次护理预约和卡项在线购买统一进入服务端结算；创建订单、订单行、定价决策、支付记录和履约记录，所有关键写操作具备幂等键。
2. 支付层提供统一适配器注册、渠道配置状态、签名回调和回调事件去重；真实渠道未配置时明确返回不可用，不创建订单或改变预约/权益。
3. `DEMO_MOCK` 仅在显式环境开关启用时注册，密钥只来自环境变量；运营接口只返回脱敏状态，不返回密钥。
4. 支持 `ZERO_AMOUNT` 零元支付记录，不调用外部渠道；零元订单仍创建订单、预约、优惠券流水、履约和幂等记录。
5. 支持次数组合卡、储值卡、折扣卡，以及在线购买、线下销售、赠送、补发来源；人工入口拒绝伪造在线购买和历史导入来源。
6. 次卡在预约结算时按项目校验并冻结 1 次，服务完成命令核销 1 次，正常释放路径返还冻结数；冻结、余额、状态和不可变核销流水受数据库事务保护。
7. 储值卡按本金与赠送金两个资产余额和冻结额分开记录；一次订单只选一张储值卡，冻结、核销和释放均留流水。
8. 护理优惠券支持满减、现金、折扣、项目体验四类；活动价、优惠券、折扣卡与“不使用优惠”由服务端报价，默认不使用，用户主动选择且不自动勾选推荐项。积分明确留到 SC5。
9. 用户端新增响应式结算页、卡项/优惠券资产页和次卡结算选择；运营端新增卡产品、券模板、发卡、发券和支付渠道状态管理，延续现有玫瑰/铜色设计系统。
10. 未恢复独立技师端入口、总部端、分店端或复杂多岗位后台；采购、提成、培训及历史技师端代码/表继续只作兼容资产保留。

## 3. 数据库迁移

SC4 只追加：

- `V2026080304__sc4_order_cards_coupon_benefits.sql`

该迁移扩展卡产品/实例来源和冻结余额，新增储值资产批次与流水、券模板/会员券/券流水、活动、订单定价决策、权益冻结、履约去重等结构，并给 `ADMIN`/`SUPER_ADMIN` 增加本阶段所需权限。

两轮均显式执行 Flyway `migrate`、`validate`，并动态比对迁移目录与 `flyway_schema_history`：

| 检查 | 第 1 轮 | 第 2 轮 |
| --- | ---: | ---: |
| 版本化迁移数 | 34 | 34 |
| 最新动态版本 | `2026080304` | `2026080304` |
| `flyway:validate` 总数（含 callback） | 35 | 35 |
| 失败历史记录 | 0 | 0 |
| 目录/历史差异 | 0 | 0 |
| `migrate` / `validate` | PASS | PASS |

验收条件没有写死 `2026080304`；该值是脚本从当前迁移目录和历史表计算后输出的实际结果。

## 4. 两轮正式命令

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc4-runtime.ps1 `
  -ProjectName face-sc4-final1 `
  -BackendPort 8990 -AdminPort 8981 -ClientPort 8982 -TechnicianPort 8983

powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc4-runtime.ps1 `
  -ProjectName face-sc4-final2 `
  -BackendPort 9090 -AdminPort 9081 -ClientPort 9082 -TechnicianPort 9083
```

两轮都从独立空 MySQL volume 开始，先做固定端口占用预检，再运行静态契约、后端全量测试、两个前端测试/生产构建、Flyway migrate/validate/历史动态对账、镜像构建、首次健康检查、五容器重启复检、API/SPA 代理检查和 SC4 合成业务矩阵；结束后自动删除容器、网络和 volume。未自动随机选择端口。

## 5. 自动化、构建与容器结果

| 检查 | 第 1 轮 | 第 2 轮 |
| --- | --- | --- |
| 官方脚本退出码 | 0 | 0 |
| 固定端口预检 | PASS | PASS |
| Docker/B0 契约 | PASS | PASS |
| 后端全量测试 | 198/198 PASS | 198/198 PASS |
| 用户端测试 | 7/7 PASS | 7/7 PASS |
| 用户端生产构建 | PASS | PASS |
| 运营端测试/生产构建 | PASS | PASS |
| Flyway migrate/validate/动态对账 | PASS | PASS |
| 五容器首次健康、API 与 SPA | PASS | PASS |
| 五容器重启复检 | PASS | PASS |
| SC4 业务矩阵 | PASS | PASS |
| 独立数据库 volume 清理 | PASS | PASS |

核心终态标记两轮一致：

```text
SC4_RESPONSIVE_UI_CONTRACT=PASS
DOCKER_FLYWAY_MIGRATE=PASS
DOCKER_FLYWAY_VALIDATE=PASS
MIGRATION_DIRECTORY_HISTORY_MATCH=PASS;COUNT=34;LATEST=2026080304
DOCKER_INITIAL_HEALTH=PASS
DOCKER_RESTART_HEALTH=PASS
DOCKER_RUNTIME_ACCEPTANCE=PASS
SC4_RUNTIME_ACCEPTANCE=PASS
SC4_DOCKER_ACCEPTANCE_CLEANUP=PASS
```

## 6. 关键业务验收矩阵

| 场景 | 两轮结果 |
| --- | --- |
| 支付配置可查询且密钥不泄露 | `SC4_MASKED_PAYMENT_CONFIGURATION=PASS` |
| 三类卡项可创建 | `SC4_THREE_CARD_TYPES=PASS` |
| 线下、赠送、补发来源与发卡幂等 | `SC4_CARD_SOURCES_AND_IDEMPOTENT_ISSUE=PASS` |
| 四类护理券 | `SC4_FOUR_COUPON_TYPES=PASS` |
| 默认不使用优惠、SC5 积分未越界 | PASS |
| 全额券生成 `ZERO_AMOUNT`、预约确认、券置为已用 | `SC4_ZERO_AMOUNT_ORDER_AND_COUPON_LEDGER=PASS` |
| 相同成功回调重复提交，只产生一次履约/发卡/回调事件 | `SC4_DUPLICATE_CALLBACK_SINGLE_FULFILLMENT=PASS` |
| 未配置微信渠道返回不可用且订单数不变 | `SC4_UNCONFIGURED_REAL_CHANNEL_NO_SIDE_EFFECT=PASS` |
| 储值本金/赠送金冻结、核销、冻结归零与流水一致 | `SC4_STORED_VALUE_FREEZE_CONSUME_LEDGER=PASS` |
| 次卡 5 次预约后冻结 1 次，核销后剩余 4、冻结 0、核销流水恰好 1 条 | `SC4_COMBO_TIME_FREEZE_CONSUME_LEDGER=PASS` |
| 模拟支付失败后订单作废、时间锁释放、折扣卡占用归零 | `SC4_FAILED_PAYMENT_FULL_RELEASE=PASS` |

正式验收前发现次卡只有核销/释放能力但没有预约冻结入口。该缺口修复后，旧的两轮输出作废；本报告只采用 commit `5ef1ae3...` 上重新执行的 `face-sc4-final1` 与 `face-sc4-final2` 两轮结果。

## 7. UI 与响应式证据

- `front-next` 与 `admin-next` 均通过 TypeScript 检查和生产构建。
- `scripts/sc4-ui-check.mjs` 两轮均输出 `SC4_RESPONSIVE_UI_CONTRACT=PASS`，校验结算、权益和运营页面具有明确的 900px/640px 响应式断点、移动端单列布局与减少动态效果规则。
- 本阶段未另存浏览器截图，因此不把像素级视觉回归列为已执行证据；结论依据可重复的源代码响应式契约和生产构建。界面设计决策受 `impeccable` 约束影响，保持既有紧凑信息密度、玫瑰/铜色强调和移动端可操作布局。

## 8. 工具与组件版本

| 组件 | 版本 |
| --- | --- |
| Windows | 11 / 10.0.26200 |
| Windows PowerShell | 5.1.26100.7462 |
| Git | 2.45.1.windows.1 |
| Docker Engine | 29.6.1 |
| Docker Compose | v5.1.4 |
| MySQL 镜像 | 8.4 |
| Maven | 3.9.16 |
| Java | Eclipse Adoptium 21.0.11 |
| Flyway | 12.4.0 |
| Node.js | v24.18.0 |
| npm | 11.16.0 |

## 9. 风险与来源限制

1. 验证环境仅为 Windows 11、Docker Desktop、MySQL 8.4 和合成数据；未连接生产数据库、正式域名、真实客户资料、微信/支付宝/聚合支付沙箱或生产渠道。
2. `DEMO_MOCK` 仅在验收脚本显式设置开关与合成密钥时启用；生产配置默认关闭。真实支付接入仍需后续取得渠道参数并执行官方沙箱、签名轮换、对账和告警验收。
3. 本阶段验证了退款相关结构的兼容性，但没有把真实渠道退款、资金对账或售后策略扩大为 SC4 新交付；不得据此宣称生产退款链路已经过真实渠道认证。
4. `LEGACY_IMPORT` 只保留为未来迁移来源枚举，人工发卡接口明确拒绝该来源；历史数据导入属于 SC7，不在 SC4 执行。
5. 积分账户、积分抵扣与商城属于 SC5，当前报价明确返回不可用原因，未提前创建积分业务能力。
6. V3.0 PRD 是唯一产品范围来源；V2.1 及更早规格、历史报告和截图只作来源记录，没有据此恢复总部端、分店端、独立技师端或复杂多岗位后台。
7. Maven 保留 Mockito 动态 agent 的未来 JDK 兼容警告；npm 保留 `sass_binary_site` 未来弃用警告和第三方 Rollup PURE 注释位置警告。当前测试、构建和运行验收均成功，但这些依赖告警应在后续技术维护中处理。
8. 已执行的 Flyway 迁移不得修改或删除；结构/数据回退必须追加补偿迁移。代码可按提交逆序使用普通 `git revert` 回退。

## 10. 阶段门禁

SC4 没有实现积分账户/任务、积分商城、实物商品/SKU/库存、购物车拆单/合包、积分退款恢复或 SC5 其他能力，也没有修改公开 DEMO 的独立技师端入口策略。

最终结论：SC4 验收通过。立即停止，等待人工回复“通过，进入 SC5”。
