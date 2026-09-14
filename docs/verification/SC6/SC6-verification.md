# SC6 最终验收报告

阶段：SC6（履约确认、评价与售后）  
版本：SC6 1.0.0  
结论：通过  
状态：立即停止，等待人工回复“通过，进入 SC7”

## 1. 基线与可追溯性

| 项目 | 值 |
| --- | --- |
| 产品基线 | `docs/specs/FACE_用户端与运营管理平台增量升级PRD_V3.0.md`，方案一 |
| 首次可追溯快照 SHA | `ff635972a7d4feb4d5192b2f385ebd3703f36326` |
| SC6 起始 tag | `sc5/v1.0.0` |
| SC6 起始 commit SHA | `c2f62937750bd1c2c0dac4290f8fad76ef6e0388` |
| SC6 已验证实现 SHA | `f731b5abed15b5e766bacd96bdbe7b8417504f56` |
| SC6 发布 tag | `sc6/v1.0.0`（本报告提交后创建） |
| 后端 artifact | `face-chain-platform:0.1.0-SNAPSHOT` |
| 用户端 package | `face-client-v2@0.2.0` |
| 运营端 package | `face-chain-admin@0.1.0` |

首次快照只代表 2026-08-03 收到代码后建立的当前首次可追溯状态，不代表或伪造原始开发历史。SC6 从人工批准的 SC5 tag 增量实施；未重写既有迁移，未删除历史技师端、复杂多角色后台、采购、提成或培训代码/表。

## 2. 实际完成范围

1. 结束护理后创建 24 小时顾客确认任务；确认完成、提出异议和超时自动完成分别记录 `CONFIRMED`、`REJECTED`、`SYSTEM_AUTO_CONFIRMED`，服务与预约履约投影同步变化。
2. 卡项/储值/体验券和护理项目积分延迟到顾客确认或系统自动确认后正式核销/到账；`service_fulfillment_fact` 唯一事实防止重复完成造成重复核销。
3. 顾客评价包含技师服务、项目效果和门店环境三项必填评分，综合分由后端计算并保留一位小数；支持公开或仅门店可见。
4. 公开评价进入审核；运营人员只能通过、回复或隐藏，不能修改顾客原文和评分。公开评价修改后重新待审，每次修改形成不可变版本。
5. 评价删除使用逻辑删除；历史版本、审核日志、审计日志和已创建售后不删除。任一分项或综合分低于 3，或顾客要求联系，自动创建并关联售后工单。
6. 服务最终完成后 7 天内可进入售后；处理方案支持退款、重做服务、恢复权益、补偿优惠券和驳回。退款成功前保持处理中，成功后才进入 48 小时顾客确认。
7. 普通管理员资产风险阈值为 500 元，超过阈值由超级管理员处理；方案、原因、凭证、幂等业务键和资产动作均留流水。
8. 顾客在 48 小时内接受或在原有效期内重开一次；超时任务自动关单并记录 `SYSTEM_AUTO_CLOSED`，第二次重开由后端拒绝。
9. 实物订单支持退换货申请、审核、寄回、待检、验货通过/不通过、恢复/报损库存；验货通过前不返还积分、现金或运费。
10. 用户端新增响应式“护理确认、评价与售后”页面；运营端新增响应式履约、评价审核、售后方案和退货验货工作台。公开会员导航没有新增独立技师端入口。

## 3. 未完成与范围外事项

- SC7 的 Excel 模板、预检、冲突处理、正式导入、历史卡独立导入、移动高频管理页扩展和基础统计未实现。
- 真实微信/支付宝/聚合退款、真实短信、真实物流轨迹、对象存储凭证和生产密钥未接入；现金退款状态衔接复用现有退款抽象，本阶段未宣称真实渠道认证。
- 历史卡仍只允许提交售后并由管理员核实后线下处理；没有开放用户端直接线上退款。
- 本阶段验证同键重复完成和退货申请不重复建单；其他 SC6 更新写接口由 `version`、唯一业务键和事务阻止重复资产动作，但未逐个保存并回放历史响应体。
- 未执行浏览器像素级截图回归、压力测试、Linux 生产部署或真实客户数据验证。

## 4. 修改文件与提交序列

SC6 相对 `sc5/v1.0.0` 共修改/新增 31 个受版本控制文件，集中于：

- `backend-next/.../servicecare/`、`appointment/`、`points/`：履约确认、延迟核销与积分到账；
- `backend-next/.../sc6/`、`v3/sc6/`、`aftersale/`：评价、售后阈值、48 小时任务和实物退货验货；
- `backend-next/src/main/resources/db/migration/V2026080310...V2026080312`：只追加迁移；
- `front-next/src/views/CareFeedbackView.vue` 与用户端 API/路由/导航；
- `admin-next/src/views/FulfillmentOperationsView.vue` 与运营端 API/路由/导航；
- `scripts/verify-sc6-runtime.ps1`、`scripts/sc6-runtime-check.mjs`、`scripts/sc6-ui-check.mjs`；
- `docs/plans/2026-08-03-sc6.md`、`CONTRIBUTING.md` 与本报告。

提交序列：

```text
acf1c48 docs(sc6): define fulfillment review and aftersale stage plan
a5b279e feat(sc6): implement fulfillment review and aftersale domain
4efe7db feat(sc6): add responsive fulfillment and aftersale workspaces
f731b5a test(sc6): add repeatable runtime acceptance
```

## 5. Flyway 与数据影响

SC6 只追加：

- `V2026080310__sc6_fulfillment_reviews_aftersale.sql`
- `V2026080311__sc6_confirmation_status_width.sql`
- `V2026080312__sc6_fulfillment_outcome_width.sql`

`V2026080311` 与 `V2026080312` 是开发期运行验收发现状态值长度边界后新增的补偿迁移；没有修改已执行的 `V2026080310` checksum。两轮均动态执行 `migrate`、`validate`，并将迁移目录与 `flyway_schema_history` 对账；脚本没有写死最终迁移版本。

| 检查 | 第 1 轮 | 第 2 轮 |
| --- | ---: | ---: |
| 版本化迁移数 | 42 | 42 |
| 动态最新版本 | `2026080312` | `2026080312` |
| Flyway validate 数（含 callback） | 43 | 43 |
| 目录/历史差异 | 0 | 0 |
| migrate / validate | PASS | PASS |

新增核心事实包括 `service_fulfillment_fact`、`service_review`、`service_review_version`、`service_review_moderation_log`、`after_sale_asset_ledger`、`mall_return_request` 和 `mall_return_item`；既有 `customer_confirmation`、`service_record`、`appointment`、`after_sale_case` 仅做增量扩展。

## 6. 新增/修改 API

- 履约确认：`GET /api/v3/customer-confirmations`、`POST /api/v3/customer-confirmations/{id}/action`。
- 用户评价：`GET /api/v3/reviews/mine`、创建、修改和逻辑删除评价。
- 评价运营：`GET /api/v3/admin/reviews`、`POST /api/v3/admin/reviews/{id}/moderation`。
- 售后方案：`POST /api/v3/after-sales/cases/{id}/solution`、`POST /api/v3/after-sales/cases/{id}/customer-response`。
- 实物退换货：查询/创建、运营审核、会员登记寄回和运营验货。
- 既有完成护理、积分到账和退款链路增量接入最终履约边界，没有另建第二套后端。

## 7. 权限、状态机与资产边界

- 会员接口以 tenant/member/shop 范围限制，只能确认、评价和处理自己的售后/退货。
- 公开评价审核要求 `review:moderate`；售后方案要求 `aftersale:manage`；验货要求 `mall:return:inspect`；超 500 元资产影响额外要求 `SUPER_ADMIN`。
- 结束护理先进入 `PENDING_CUSTOMER_CONFIRMATION`，只有会员确认或 24 小时自动确认才进入 `COMPLETED` 并产生唯一履约事实。
- 异议进入 `AFTER_SALES_PROCESSING`；售后方案进入 `WAITING_CUSTOMER`，退款在渠道成功前保持 `PROCESSING`。
- 退货按 `SUBMITTED → APPROVED → PENDING_INSPECTION → INSPECTION_PASSED/FAILED`；积分、现金、运费和库存终态只在验货通过事务中处理。
- 资产和库存变化采用追加流水/原记录冲正，不覆盖历史事实；唯一业务键防止同一资产动作重复入账。

## 8. 两轮正式命令

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc6-runtime.ps1 `
  -ProjectName face-sc6-final1 `
  -BackendPort 9790 -AdminPort 9781 -ClientPort 9782 -TechnicianPort 9783

powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc6-runtime.ps1 `
  -ProjectName face-sc6-final2 `
  -BackendPort 9890 -AdminPort 9881 -ClientPort 9882 -TechnicianPort 9883
```

两轮都使用显式固定端口和独立 Compose project/全新 MySQL volume；先预检端口，再执行静态契约、全量测试、双前端生产构建、Flyway、镜像、首次健康、五容器整体重启、API/SPA 和 SC6 合成业务矩阵；结束后删除容器、网络和 volume。未自动随机选择端口。

## 9. 测试、构建与容器结果

| 检查 | 第 1 轮 | 第 2 轮 |
| --- | --- | --- |
| 官方脚本退出码 | 0 | 0 |
| 固定端口预检 | PASS | PASS |
| Docker/B0 契约 | PASS | PASS |
| 后端全量测试 | 206/206 PASS | 206/206 PASS |
| 用户端测试 | 7/7 PASS | 7/7 PASS |
| 运营端测试 | 1/1 PASS | 1/1 PASS |
| 用户端/运营端生产构建 | PASS | PASS |
| Flyway migrate/validate/动态对账 | PASS | PASS |
| 五容器首次健康/API/SPA | PASS | PASS |
| 五容器整体重启复检 | PASS | PASS |
| SC6 业务矩阵 | PASS | PASS |
| 独立数据库 volume 清理 | PASS | PASS |

两轮一致的关键终态标记：

```text
SC6_RESPONSIVE_UI_AND_DOMAIN_CONTRACT=PASS
MIGRATION_DIRECTORY_HISTORY_MATCH=PASS;COUNT=42;LATEST=2026080312
DOCKER_INITIAL_HEALTH=PASS
DOCKER_RESTART_HEALTH=PASS
SC6_CUSTOMER_CONFIRMATION_IDEMPOTENCY=PASS
SC6_24H_AUTO_CONFIRMATION=PASS
SC6_REVIEW_VERSION_MODERATION_AUDIT=PASS
SC6_AFTERSALE_THRESHOLD_48H_REOPEN=PASS
SC6_48H_AUTO_CLOSE=PASS
SC6_RETURN_INSPECTION_ASSET_GATE=PASS
SC6_RUNTIME_ACCEPTANCE=PASS
SC6_DOCKER_ACCEPTANCE_CLEANUP=PASS
```

## 10. 数据、安全、隐私和审计核对

- 合成会员、手机号、积分、商城订单、物流号和售后凭证只存在于每轮临时数据库，清理后不保留真实客户数据或生产密钥。
- 顾客原文与评分没有运营修改 API；公开审核、回复、隐藏和删除均保留独立审计/版本事实。
- 普通管理员 501 元方案在运行验收中返回 403 且工单状态不变；超级管理员执行后产生追加资产流水。
- 第二次重开在运行验收中返回 409；48 小时到期工单由计划任务自动关闭并生成日志。
- 实物退货在待验货阶段的积分/现金/运费流水计数为 0；验货通过后原积分冻结记录恢复、库存回到可用并各自产生可追溯流水。
- 页面沿用现有玫瑰/铜色、紧凑运营信息密度和移动端单列响应式规则；`impeccable` 约束用于保持既有视觉系统、清晰层级、触控尺寸和状态说明。本阶段未保存浏览器截图，因此不把像素级视觉回归列为已执行证据。

## 11. 工具与组件版本

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

## 12. 回滚与已知风险

1. 已执行 Flyway 不得修改或删除；数据/结构回退必须追加补偿迁移。应用代码可按提交逆序 `git revert`。
2. 当前只在 Windows 11、Docker Desktop、MySQL 8.4 和合成数据上验证；不能据此声明 Linux 生产部署、真实支付退款、真实物流或真实大并发已经认证。
3. Maven 保留 Mockito 动态 agent 的未来 JDK 警告；npm 保留 `sass_binary_site` 未来弃用和第三方 Rollup PURE 注释警告。当前测试、构建和运行均成功。
4. 本阶段无压力测试；重复完成结论来自事务、条件更新、唯一履约事实和实际同键重放，不代表已完成容量基准。
5. 真实渠道异步退款只有在适配器报告成功后才进入顾客确认；本阶段验证状态门禁和本地合成资产链路，不替代 SC9 的渠道 Sandbox、小额退款与对账验收。
6. V3.0 PRD 是唯一产品基线；V2.1、旧报告和历史截图只作来源记录，没有据此恢复已停止范围。

## 13. 阶段门禁

SC6 未进入 SC7 的数据导入和运营平台完善范围。最终结论：SC6 验收通过。立即停止，等待人工回复“通过，进入 SC7”。
