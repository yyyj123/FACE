# SC2 最终验收报告

阶段：SC2（身份、内容、管理员与单店模式）  
版本：SC2 1.0.0  
结论：通过  
状态：立即停止，等待人工回复“通过，进入 SC3”

## 1. 基线与可追溯性

| 项目 | 值 |
| --- | --- |
| 产品基线 | `FACE_用户端与运营管理平台增量升级PRD_V3.0.md`，方案一 |
| 首次可追溯快照 SHA | `ff635972a7d4feb4d5192b2f385ebd3703f36326` |
| SC2 起始基线 tag | `sc1/v1.0.0` |
| SC2 起始基线 commit SHA | `00169ef116861310a3fd04d65156fc04e337170a` |
| SC2 已验证实现 SHA | `b32f8f368b2bce9c62e9cbd1dcb8646a9ef96d1e` |
| SC2 发布 tag | `sc2/v1.0.0`（本报告提交后创建） |
| 后端 artifact | `0.1.0-SNAPSHOT` |
| 用户端 package | `face-client-v2@0.2.0` |
| 管理端 package | `face-chain-admin@0.1.0` |

首次快照只代表收到代码后于 2026-08-03 建立的当前可追溯状态，不是、也不得表述为原始开发历史。SC2 从人工已批准的 SC1 tag 开始增量实施；没有重写既有 Flyway 文件或伪造历史提交。

## 2. 实际完成范围

1. 单店上下文统一由后端解析：`BUSINESS_MODE=SINGLE_SHOP`、`DEFAULT_SHOP_ID` 可配置；用户端不显示门店选择，当前正式前端不再写死 `shopId=1`；保留 `MULTI_SHOP` 扩展边界但未开启多店交互。
2. 用户身份支持手机号 + 密码登录、手机号 + 短信验证码注册/登录、短信验证码找回密码；同租户手机号唯一；注册优先关联手机号唯一匹配的历史会员。
3. 短信适配器按 Profile 隔离：`888888` 仅存在于 `demo` Profile；默认/生产路径为禁用或显式 HTTP 适配器，不自动降级到固定验证码。
4. 运营管理平台只展示 `ADMIN` 与 `SUPER_ADMIN`。普通管理员可维护 SC2 日常内容、项目和技师公开资料；超级管理员额外创建、停用和重置管理员账号，并在敏感账号操作后撤销会话。
5. 首页内容支持轮播图、推荐护理、推荐卡项、活动、积分商城、公告、门店介绍和联系方式，以及 `DRAFT → SCHEDULED → PUBLISHED → OFFLINE` 生命周期。
6. 访客可浏览首页内容、护理分类/列表/详情、技师公开资料和门店信息；公开技师资料不返回其他顾客信息，也未创建技师登录账号。
7. 用户端和运营管理平台完成响应式 SC2 页面；沿用既有白色/浅灰表面、玫瑰铜强调色和紧凑圆角设计，不进行范围外重设计。
8. 保留历史多岗位、技师端、采购、提成、培训等代码和数据表作为兼容资产，但不开放为本阶段新增入口或交付承诺。

## 3. 数据库迁移

SC2 只追加以下迁移：

- `V2026080301__sc2_identity_content_single_shop.sql`：账号手机号与版本、同租户手机号唯一约束、短信挑战、首页内容字段/状态、两个可见管理员角色和权限映射。
- `V2026080302__sc2_banner_publish_timestamp_backfill.sql`：以追加迁移修复旧 `ACTIVE` 横幅转为 `PUBLISHED` 后可能缺失的发布时间；未修改已提交的 `V2026080301`。

两轮均显式执行 Flyway `migrate` 和 `validate`，随后以迁移目录与 `flyway_schema_history` 动态逐版本对账：

| 检查 | 第一轮 | 第二轮 |
| --- | --- | --- |
| 版本化迁移数 | 32 | 32 |
| 最新迁移 | `2026080302` | `2026080302` |
| 失败历史记录 | 0 | 0 |
| 目录/历史差异 | 0 | 0 |
| `flyway:validate` | PASS | PASS |

## 4. 权限与关键业务验收

| 身份/场景 | 验收结果 |
| --- | --- |
| 游客 | 首页内容、护理项目、技师公开资料可读；管理员账号接口返回 401 |
| 会员 | 手机号/密码登录与本人资料可读；管理员账号接口返回 403 |
| `ADMIN` | 内容读取与内容状态流转可用；管理员账号管理返回 403 |
| `SUPER_ADMIN` | 管理员登录、列表和创建普通管理员均成功 |
| 生产短信 | 默认非 demo 环境请求返回明确 503；响应不包含 `888888` 或 `demo_code` |
| 历史会员匹配 | 将合成手机号绑定到已有历史会员后登录成功，同时返回真实 `account_id` 与既有 `member_id` |
| 手机号唯一性 | `uk_account_tenant_phone(tenant_id, phone)` 在干净 MySQL 8.4 数据库中存在且生效 |
| 非法门店预约 | 单店模式下携带 `shopId=999` 的预约请求在进入预约写入前返回 400 |
| 内容生命周期 | 同一条合成内容连续完成 `DRAFT → SCHEDULED → PUBLISHED → OFFLINE` |
| 管理员可见角色 | 运行时确认 `ADMIN,SUPER_ADMIN`；旧角色仅作兼容资产，不暴露于当前管理 UI |

容器验收最初暴露 `/api/v3/open/content/home` 被旧集成客户端过滤器误拦截为 401。已先增加失败回归测试，再把过滤范围收紧到真正的 `/api/v3/open/v1/catalog/**`，最终两轮访客矩阵均通过。

## 5. 自动化、构建与容器结果

| 检查 | 第一轮 | 第二轮 |
| --- | --- | --- |
| 显式端口预检 | PASS（8590/8581/8582/8583） | PASS（8690/8681/8682/8683） |
| Docker/B0 静态契约 | PASS | PASS |
| 后端全量测试 | 182/182 PASS | 182/182 PASS |
| 用户端生产构建 | PASS | PASS |
| 管理端生产构建 | PASS | PASS |
| Flyway migrate/validate/历史对账 | PASS | PASS |
| 五容器初始健康/API/SPA | PASS | PASS |
| 五容器重启复检 | PASS | PASS |
| SC2 权限与业务矩阵 | PASS | PASS |
| 独立数据库 volume 清理 | PASS | PASS |
| 脚本退出码 | 0 | 0 |

补充 UI 自动化结果：用户端 7/7、运营端 1/1 静态测试通过；1440px 桌面与 390px 移动视口的用户登录页、运营登录页均无横向溢出。预览图：

- `E:\FACE\.artifacts\预览\SC2-用户端登录-桌面.png`
- `E:\FACE\.artifacts\预览\SC2-用户端登录-移动.png`
- `E:\FACE\.artifacts\预览\SC2-运营平台登录-桌面.png`
- `E:\FACE\.artifacts\预览\SC2-运营平台登录-移动.png`

## 6. 官方命令

第一轮：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc2-runtime.ps1 `
  -ProjectName face-sc2-evidence1 `
  -BackendPort 8590 -AdminPort 8581 -ClientPort 8582 -TechnicianPort 8583
```

第二轮：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc2-runtime.ps1 `
  -ProjectName face-sc2-evidence2 `
  -BackendPort 8690 -AdminPort 8681 -ClientPort 8682 -TechnicianPort 8683
```

脚本内部调用 B0-R 官方 `verify-docker-runtime.ps1 -KeepRunning`，完成端口预检、静态契约、后端测试、双前端构建、干净数据库迁移、Flyway 校验、目录/历史对账、镜像构建、健康检查和重启复检；随后执行 SC2 权限与业务矩阵，最后清理本轮容器、网络和 volume。

## 7. 证据文件完整性

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `round-1.log` | 136136 | `D9B64446B5767D5489EF52F01ED9CF0E568C13FA7BC97B3ADEED2C6E96705D1A` |
| `round-2.log` | 129074 | `67DE775D8EFBAB284D576F1D851950D61664D24C694188F8B60828049A21C21F` |
| `ui-check.log` | 5854 | `47D0447E9178FCE05FF2398B7A47525614F08B34EA32629577AAC338518FD20B` |

日志只包含构建、测试、迁移、容器状态和合成验收结果；不包含生产密钥、真实验证码或真实客户资料。

## 8. 工具与组件版本

| 组件 | 版本 |
| --- | --- |
| Windows | 10.0.26200 |
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

## 9. 失败测试与修复记录

1. 横幅发布时间修复和身份响应 `account_id` 先产生 2 个失败契约，随后通过追加迁移与响应字段实现转绿。
2. SC2 运行脚本初次加载时发现 Windows PowerShell 5 未自动加载 `System.Net.Http`；在任何容器启动前失败并修复。
3. 脚本第二次执行完成基础链后被只读变量名冲突拦截；容器由 `finally` 清理，变量改名后重跑。
4. 首次访客运行矩阵发现公开首页被集成客户端过滤器误拦截；新增 `IntegrationClientFilterTest` 先复现失败，最小修复公开路径边界后，两轮完整验收均通过。
5. UI 补充检查首次仅因 PowerShell 日志重定向位置错误返回 1；测试与浏览器检查实际已通过，随后以正确重定向重新执行并生成 `ui-check.log`。

## 10. 风险、限制与回滚

1. 本次只在 Windows 11、Docker Desktop、MySQL 8.4 和合成数据上验证；未连接生产数据库、正式域名或真实客户数据。
2. 生产短信仅验证“禁用时明确不可用且不会退化为 DEMO 固定码”。真实短信 HTTP 供应商仍需在后续生产准备阶段以真实 Sandbox/凭据单独验收。
3. 历史会员匹配使用干净库中的既有历史会员记录和合成手机号；没有导入真实历史卡项文件。大规模正式数据导入属于 SC7。
4. `MULTI_SHOP` 只保留后端解析和数据范围扩展边界；本阶段没有门店选择 UI，也没有执行多店业务验收。
5. 历史兼容 API、技师端和复杂业务模块仍在仓库中；本阶段仅收口当前入口，没有删除历史资产。
6. Maven 保留 Mockito 动态 agent 的未来 JDK 兼容警告；npm 保留 `sass_binary_site` 未来弃用和第三方 Rollup 注释位置警告，当前测试和构建均成功。
7. SC2 代码可用普通 `git revert` 按提交逆序回退；已执行的 Flyway 迁移不得修改或删除，只能通过新的补偿迁移回滚数据结构/数据影响。

## 11. 范围门禁

未实现或修改 SC3 的项目级预约间隔/缓冲、未来 30 天与 7 天分页、指定/不指定技师、低负载轮询、时间锁或候补 15 分钟确认。SC2 到此立即停止；只有收到人工明确回复“通过，进入 SC3”后才允许继续。

最终结论：SC2 验收通过，等待人工回复“通过，进入 SC3”。
