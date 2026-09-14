# SC7 最终验收报告

阶段：SC7（数据迁移与运营平台完善）  
版本：SC7 1.0.0  
结论：通过  
状态：立即停止，等待人工回复“通过，进入 SC8”

## 1. 基线与可追溯性

| 项目 | 值 |
| --- | --- |
| 产品基线 | `docs/specs/FACE_用户端与运营管理平台增量升级PRD_V3.0.md`，方案一 |
| 首次可追溯快照 SHA | `ff635972a7d4feb4d5192b2f385ebd3703f36326` |
| SC7 起始 tag | `sc6/v1.0.0` |
| SC7 起始 commit SHA | `0daa7be5431728cb54a09fde4e975803a0f6981f` |
| SC7 已验证实现 SHA | `7a0b8ac0bd7ee347fea06902c6f1bc062703e35c` |
| SC7 发布 tag | `sc7/v1.0.0`（本报告提交后创建） |
| 后端 artifact | `face-chain-platform:0.1.0-SNAPSHOT` |
| 用户端 package | `face-client-v2@0.2.0` |
| 运营端 package | `face-chain-admin@0.1.0` |

首次快照只代表 2026-08-03 收到无 Git 历史目录后建立的当前首次可追溯状态，不代表或伪造原始开发历史。SC7 从人工批准的 SC6 tag 增量实施；未重写既有已发布迁移，未删除历史技师端、复杂多角色后台、采购、提成、培训相关代码或表。

## 2. 实际完成范围

1. 提供系统生成的 `.xlsx` 模板，包含“客户资料、组合卡、组合卡护理明细、储值卡、折扣卡”五个工作表；限制 10 MB、最多 10,000 行并校验必填字段、日期、金额、次数、门店、卡产品和护理项目。
2. 上传后先创建预检批次，展示总数、可导入、冲突和错误行，提供问题 CSV；正式导入不会直接接受未预检的文件。
3. 会员按“来源系统 + 原会员编号 → 标准化手机号 → 人工处理”匹配。系统已有姓名或手机号不一致时进入冲突，不覆盖；匹配资料一致时只补充系统为空的性别、生日和来源。
4. 历史组合卡、储值卡、折扣卡按“来源系统 + 原卡号”独立入账，保留原会员编号、原卡号、购买日期、到期日和剩余权益；组合卡护理明细按来源卡号关联。
5. 历史卡建立 `LEGACY_IMPORT` 套餐/储值台账；文件哈希、来源会员键、来源卡键和执行请求哈希形成多层幂等边界，重复文件和同键执行不重复建会员、卡或台账。
6. 普通管理员可下载模板、预检、看结果和历史，但不能正式导入；正式执行同时要求 `SUPER_ADMIN`、`import:execute`、门店范围、幂等键和桌面端声明。
7. 响应式运营端新增“数据迁移”桌面工作区和“今日工作台”移动高频页。移动页只提供今日预约、技师日程、候补/履约提醒、订单/发货查询、售后沟通和基础数量统计。
8. 服务端统一拦截移动端或未声明端访问批量导入、管理员/权限账号、集成密钥，以及预留的支付设置、系统初始化、批量资产路径；桌面端管理 API 保持可用，移动工作台保持可用。
9. 管理端依据 900px 响应式断点声明 `MOBILE` 或 `DESKTOP`；服务端对敏感路径采用缺省拒绝。公开运营壳没有独立技师端入口。
10. 新增统一后端工作台 API，只返回基础计数和高频任务列表，没有另建移动后端或恢复已停止的多岗位后台。

## 3. 未完成与范围外事项

- 未进入 SC8；未配置 Cloudflare Quick Tunnel、`trycloudflare.com`、公网临时域名、远端设备演示或任何公网暴露。
- 未接入真实历史 Excel、真实客户资料、真实支付密钥、真实短信或生产对象存储；验收全部使用每轮临时生成的合成数据。
- 未新增复杂经营分析、BI 报表、总部/分店/技师独立后台、采购/提成/培训交付面或微信小程序页面。
- 本阶段只支持系统模板 `.xlsx`，不接受自由格式 Excel、CSV、旧数据库直连、公式计算结果推断或图片 OCR。
- 没有执行浏览器像素级截图回归、10,000 行容量压测、并发大批次压测、Linux 生产部署或真实来源系统对账。

## 4. 修改文件与提交序列

已验证实现相对 `sc6/v1.0.0` 修改/新增 25 个受版本控制文件；本报告和门禁更新再增加 2 个治理/证据文件。主要集中于：

- `backend-next/.../legacyimport/`：模板解析、预检、冲突、执行、历史卡和幂等；
- `backend-next/.../operations/`：移动高频工作台和基础统计；
- `backend-next/.../security/AdminSurfaceGuardFilter.java`：敏感路径桌面端门禁；
- `backend-next/src/main/resources/db/migration/V2026080313__sc7_legacy_import.sql`：SC7 追加迁移；
- `admin-next/src/views/DataMigrationView.vue`、`MobileOperationsView.vue` 及 API/路由/导航；
- `scripts/verify-sc7-runtime.ps1`、`sc7-runtime-check.mjs`、`sc7-ui-check.mjs`；
- `docs/plans/2026-08-10-sc7.md`、`CONTRIBUTING.md` 与本报告。

提交序列：

```text
7996eda docs(sc7): define data migration and operations stage plan
ed77a49 feat(sc7): implement idempotent legacy data import
5ef2e7d feat(sc7): add responsive migration and operations workspace
0a29a0b test(sc7): add repeatable data import acceptance
7a0b8ac fix(sc7): enforce desktop-only administration gates
```

## 5. Flyway 与数据影响

SC7 只追加 `V2026080313__sc7_legacy_import.sql`。开发期首次 MySQL 8.4 运行发现 `ROW_NUMBER` 关键字边界后，在该迁移发布前将新列命名为 `source_row_number`；没有修改任何既有已执行迁移。两轮均动态执行 `migrate`、`validate`，并将迁移目录与 `flyway_schema_history` 对账；验收脚本没有写死最终版本。

| 检查 | 第 1 轮 | 第 2 轮 |
| --- | ---: | ---: |
| 版本化迁移数 | 43 | 43 |
| 动态最新版本 | `2026080313` | `2026080313` |
| Flyway validate 数（含 callback） | 44 | 44 |
| 目录/历史差异 | 0 | 0 |
| migrate / validate | PASS | PASS |

新增核心表为 `legacy_import_batch`、`legacy_import_row`、`legacy_member_source_map`；`package_instance` 增加历史来源/原卡号/原购买日字段与唯一键，套餐和储值台账枚举增量允许 `LEGACY_IMPORT`。批次行保留结构化原始载荷、状态、匹配方式、问题和结果实体，便于审计与问题报告。

## 6. 新增 API

- 模板：`GET /api/v3/legacy-import/template`。
- 预检：`POST /api/v3/legacy-import/preflight?shopId={id}`。
- 批次：列表、详情、问题 CSV 和 `POST /api/v3/legacy-import/batches/{id}/execute`。
- 移动工作台：`GET /api/v3/operations/workbench?shopId={id}`。
- 既有会员、套餐实例、储值批次和台账服务被统一后端复用，没有复制业务后端。

## 7. 权限、冲突、幂等和移动边界

- `ADMIN` 和 `SUPER_ADMIN` 具备 `import:view`、`import:preflight`；只有 `SUPER_ADMIN` 具备 `import:execute`，执行仍校验门店范围和幂等键。
- 文件 SHA-256 在租户/门店范围唯一；会员来源键、卡来源键和执行请求哈希防止文件、批次、行和卡重复落账。
- 来源键或手机号匹配存在歧义、系统姓名/手机号不一致、卡产品/项目缺失、日期金额非法时进入冲突或错误；冲突会员及关联卡不会覆盖现有资料。
- 管理端根据当前视口发送 `X-FACE-Admin-Surface`。批量导入、管理员账号/权限、集成密钥、支付设置、系统初始化和批量资产路径只接受 `DESKTOP`；`MOBILE` 与缺失声明均返回 403。
- 端声明是产品表面门禁而非设备身份认证；任意客户端都可能伪造 header，因此真实安全仍由登录、角色、权限、租户/门店范围、幂等和审计共同承担。
- 运行验收确认后端日志不包含合成导入手机号全文。

## 8. 两轮最终命令

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc7-runtime.ps1 `
  -ProjectName face-sc7-final-1 `
  -BackendPort 9290 -AdminPort 9281 -ClientPort 9282 -TechnicianPort 9283

powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc7-runtime.ps1 `
  -ProjectName face-sc7-final-2 `
  -BackendPort 9390 -AdminPort 9381 -ClientPort 9382 -TechnicianPort 9383
```

两轮均从独立 Compose project 和全新 MySQL volume 开始，先对显式端口做占用预检；没有自动随机选择端口。每轮依次执行范围/静态契约、后端全量测试、双前端生产构建、Flyway migrate/validate/动态对账、镜像构建、首次健康、五容器整体重启、API/SPA 和 SC7 业务矩阵，最后删除容器、网络和 volume。

## 9. 测试、构建与容器结果

| 检查 | 第 1 轮 | 第 2 轮 |
| --- | --- | --- |
| 官方脚本退出码 | 0 | 0 |
| 固定端口预检 | PASS | PASS |
| Docker/B0/SC7 静态契约 | PASS | PASS |
| 后端全量测试 | 212/212 PASS | 212/212 PASS |
| 用户端/运营端生产构建 | PASS | PASS |
| Flyway migrate/validate/动态对账 | PASS | PASS |
| 五容器首次健康/API/SPA | PASS | PASS |
| 五容器整体重启复检 | PASS | PASS |
| SC7 导入、冲突、幂等、权限与移动门禁矩阵 | PASS | PASS |
| 独立数据库 volume 清理 | PASS | PASS |

额外独立执行用户端测试 7/7、运营端测试 2/2，均通过。两轮一致的关键终态标记：

```text
SC7_RESPONSIVE_IMPORT_AND_OPERATIONS_CONTRACT=PASS
PORT_PREFLIGHT=PASS
B0_VERSIONED_MIGRATIONS=43
MIGRATION_DIRECTORY_HISTORY_MATCH=PASS;COUNT=43;LATEST=2026080313
DOCKER_INITIAL_HEALTH=PASS
DOCKER_RESTART_HEALTH=PASS
DOCKER_RUNTIME_ACCEPTANCE=PASS
SC7_RUNTIME=PASS;BATCH=LIB001000000000001;MEMBERS=1;CARDS=3;CONFLICTS_ISOLATED=1
SC7_DOCKER_ACCEPTANCE_CLEANUP=PASS
```

每轮真实验证一名会员、三种历史卡、三条套餐 `LEGACY_IMPORT` 台账和一条储值 `LEGACY_IMPORT` 台账；重复文件返回原批次，同执行键重放不新增数据；冲突资料保持原姓名。普通管理员正式执行、移动端导入、未声明端导入、移动端管理员账号/权限和未声明端管理员账号/权限均返回 403，桌面端账号查询和移动工作台返回成功。

## 10. 数据、安全、隐私与审计核对

- 模板夹具、手机号、会员、卡和台账只存在于每轮临时文件/数据库；脚本在业务检查后删除临时 `.xlsx`，Compose 清理删除数据库 volume。
- 服务日志验收明确搜索完整合成手机号且未发现；错误报告只输出工作表、行号、记录类型、来源记录号、状态和问题，不输出完整手机号。
- 原始文件本体不在服务端持久保存，但解析后的行载荷保存在导入表 JSON 中，属于敏感业务数据；生产环境仍需数据库加密、备份访问控制和保留期策略。
- 文件名经过净化，文件类型/大小/行数受限；模板解析不调用宏或外部链接。未将 Excel 公式求值当作可信输入。
- 正式执行保留操作者、开始/完成时间、文件哈希、请求哈希、来源映射、结果实体和审计日志。
- 页面沿用现有玫瑰/铜色、紧凑运营信息密度、移动单列和 900px 响应式规则；`impeccable` 约束用于保持现有视觉系统、层级和触控可用性。本阶段没有保存像素级浏览器截图，因此不宣称视觉回归已认证。

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

## 12. 来源限制、回滚与已知风险

1. V3.0 PRD 是唯一产品基线；V2.1、旧报告、旧代码和截图只作来源记录，没有据此恢复已停止范围。
2. 匹配/冲突结论只用系统生成的合成模板验证。真实来源系统的编码、空值、重复手机号、日期格式和脏数据分布仍需上线前抽样演练与业务签字。
3. 当前预检/执行为同步请求，且工作簿在内存解析；10 MB/10,000 行是安全上限，不代表已完成该上限的容量认证。生产大批次应监控请求超时、内存和事务时长。
4. `X-FACE-Admin-Surface` 可被客户端伪造，不可替代身份认证或授权；本阶段证明官方响应式客户端和服务端路径均执行门禁，不声称完成设备证明。
5. 当前只在 Windows 11、Docker Desktop、MySQL 8.4 和合成数据上验证；未认证 Linux 生产环境、多实例并发导入、灾备恢复或真实数据对账。
6. Maven 保留 Mockito 动态 agent 的未来 JDK 警告；npm 保留 `sass_binary_site` 弃用、第三方 Rollup PURE 注释和 lockfile 审计告警。当前测试、构建和运行成功，但依赖告警需在后续安全维护窗口评估。
7. 已执行 Flyway 不得修改或删除；结构/数据回退必须追加补偿迁移。应用代码可按提交逆序 `git revert`，但已导入业务数据必须通过审计后的补偿流程处理，不能直接删台账。

## 13. 阶段门禁

SC7 未进入 SC8 的临时公网演示范围。最终结论：SC7 验收通过。立即停止，等待人工回复“通过，进入 SC8”。
