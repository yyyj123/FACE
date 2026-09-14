# FACE 美容院店铺管理系统

> **历史文档状态（2026-08-03）：本 V2.1 范围已被 V3.0 取代。** 总部端、分店端、技师端和复杂多岗位后台的新增交付已经停止。本文保留用于来源追溯，不代表当前交付承诺；当前基线以 `FACE_用户端与运营管理平台增量升级PRD_V3.0.md` 为准。

## 项目组 分阶段生产升级 PRD 与实施约束 V2.1

> 适用代码基线：backend-next + admin-next + front-next + MySQL 8 + Flyway

> 执行方式：项目组 访问完整仓库；逐阶段开发；每阶段验收后等待人工批准

> 首个交付：Windows 11 + Docker Desktop + Cloudflare Quick Tunnel 的 1-3 天全端 DEMO 环境

> 最终目标：Linux 云服务器 + Docker Compose 的可审计、可回滚、可监控生产环境


## 文档控制

| 项目 | 内容 |
| --- | --- |
| 文档名称 | FACE 美容院店铺管理系统 项目组 分阶段生产升级 PRD |
| 版本 | V2.1 |
| 文档日期 | 2026-08-02 |
| 当前产品基线 | 商业基线 V1.3；M0-M6 已验收 |
| 代码主线 | backend-next、admin-next、front-next |
| 禁止新增能力的旧线 | 旧 Vue 2、旧后端、/api/v1 |
| 数据库 | MySQL 8；Flyway 只允许向前迁移与受控补偿 |
| 执行工具 | 项目组，完整仓库访问 |
| 交付策略 | 阶段化、TDD、频繁小提交、阶段门禁 |


## 1. 给 项目组 的最高优先级指令

> **重要：** 项目组 必须先阅读本章。未完成当前阶段全部验收并获得人工批准，不得进入下一阶段。

1. 开工前读取 PRODUCT.md、DESIGN.md、README.md、AGENTS.md（如存在）、docs/architecture、docs/baseline、docs/verification、docs/commercial-v1.3 以及 backend-next 的 Flyway 目录。
1. 先创建独立 feature branch 或 worktree，不得直接在 main/master 上开发。
1. 先完成 B0 基线审计，输出真实工程结构、构建工具、测试命令、端口、环境变量和模块路径；未通过 B0 不得写业务代码。
1. 每个阶段开始前，先在 docs/plans/ 中生成该阶段精确实施计划，包含真实文件路径、测试路径、命令和提交边界。
1. 每个任务遵循：先写失败测试、运行确认失败、最小实现、运行确认通过、提交。
1. 所有数据库结构或数据修复必须新增 Flyway 版本；禁止修改已执行迁移的 checksum，禁止人工直接改生产业务流水。
1. 资金、库存、套餐、提成、审批决定、通知投递等历史事实只能追加流水、版本或冲正，禁止覆盖和删除原事实。
1. 前端菜单隐藏不是权限边界；每个接口必须在后端校验权限点、tenant、region/shop、SELF 归属、状态机和职责分离。
1. 不得在旧 Vue 2、旧后端或 /api/v1 中新增商用能力；/api/v2 仅兼容，新增正式契约优先使用 /api/v3。
1. 任何阶段出现测试失败、迁移失败、权限越界、重复扣款、重复核销、负库存或不可回滚风险，立即停止并报告，不得伪造成功。
1. 每阶段结束必须输出验收报告、变更清单、测试证据、迁移说明、回滚说明、已知限制，并等待人工明确回复“通过，继续下一阶段”。

## 2. 当前基线与升级范围

当前系统已经形成会员、预约、到店护理、库存、订单收款、套餐、退款、提成、售后、经营分析、营销治理、培训和开放目录的可运行闭环。本次不重写核心业务，而是在现有模块化单体和三端工程上完成 DEMO 暴露、总部/分店管理边界收紧，以及生产部署、安全、支付、通知、监控、备份和灰度能力。

| 领域 | 保留并复用 | 本次升级重点 |
| --- | --- | --- |
| 后端 | 现有 security、member、appointment、servicecare、transaction、inventory、package、commission、marketing 等模块 | 生产配置隔离、DEMO 门禁、外部渠道、可观测性、部署门禁 |
| 管理端 | admin-next 现有概览、门店、会员、预约、交易、库存、采购、分析等页面 | 总部与分店菜单模板、数据范围、危险操作保护 |
| 客户端/技师端 | front-next 独立构建及 SELF 权限 | DEMO 注册、模拟验证码、电脑与手机响应式体验 |
| 数据库 | 101 张基础表、2 个视图和 Flyway 历史 | 仅新增必要配置、审计和生产运维表；不复制业务主表 |
| 部署 | 现有 Docker Compose 本地体验 | DEMO Compose、生产 Compose、Nginx、cloudflared、健康检查、备份和灰度 |


## 3. 明确不做的事情

- 不重写预约、护理、库存、套餐、退款、提成等已验收核心领域。
- 不把旧 Vue 2 或旧后端恢复为商用主线。
- 不在 DEMO 环境使用真实客户、真实支付、真实短信、真实邮件或生产密钥。
- 不将 Cloudflare Quick Tunnel 当成生产入口或长期稳定服务。
- 不在本次首期引入 Kubernetes、服务网格或微服务拆分。
- 不开放会员、健康、支付、库存等敏感领域的开放平台写权限。

## 4. 目标部署架构

系统采用同一套代码、不同环境配置。DEMO 用于 1-3 天客户试用；生产环境最终部署到 Linux 云服务器和 Docker Compose。

```text
DEMO（Windows 11 + Docker Desktop）
客户浏览器/手机
  -> Cloudflare Quick Tunnel（cloudflared 容器）
  -> Nginx Demo Gateway
       /               FACE 演示访问页与入口导航
       /admin/         admin-next
       /client/        客户端
       /technician/    技师端
       /api/           backend-next
  -> MySQL Demo 数据库

PROD（Linux 云服务器 + Docker Compose）
正式域名 + DNS + TLS
  -> Nginx
       -> admin/client/technician 静态站点
       -> backend-next
  -> MySQL 8（Compose 或外部托管，通过环境变量切换）
  -> 对象存储/持久卷
  -> 监控、日志、备份与告警
```


## 5. 环境矩阵

| 环境 | 用途 | 数据 | 支付/通知 | 公网入口 | 允许破坏性操作 |
| --- | --- | --- | --- | --- | --- |
| dev | 开发人员本地开发 | 合成开发数据 | Stub/Sandbox | 默认无 | 按开发权限 |
| test | 自动化测试 | 自动创建和销毁 | Fake | 无 | 仅测试进程 |
| demo | 客户 1-3 天试用 | 独立 DEMO 数据库 | 模拟支付/退款/短信 | Quick Tunnel | 删除、导出、密钥管理禁用 |
| staging | 生产前验收 | 脱敏或合成数据 | Sandbox/测试商户 | 固定测试域名 | 严格审批 |
| prod | 正式经营 | 真实业务数据 | 真实渠道 | 正式域名 + TLS | 按权限和审批 |


## 6. 全局技术与数据约束

- 所有环境变量使用 .env.example 提供键名和说明，真实值不得提交 Git。
- DEMO 与 PROD 必须使用不同数据库、不同密钥、不同 Cookie 名称、不同对象存储前缀。
- 所有写接口继续遵守 Idempotency-Key、version/If-Match、稳定错误码和 request_id。
- 金额使用 DECIMAL/十进制字符串，时间使用带时区 ISO-8601，数据库和应用显式配置时区。
- 日志、错误堆栈、通知和 outbox 中不得出现完整手机号、证件号、健康信息、支付凭据或演示访问密码。
- DEMO 模拟能力必须由后端 Profile 和功能开关双重控制，生产环境不存在自动降级到模拟模式。
- 前端可以隐藏或禁用按钮，但最终禁止必须在后端实现并测试。
- 每个新增配置项必须有默认安全值；未配置外部依赖时明确返回 DEPENDENCY_UNAVAILABLE。

## 7. 阶段总览与门禁

| 阶段 | 目标 | 可独立验收交付 | 进入下一阶段条件 |
| --- | --- | --- | --- |
| B0 | 仓库基线审计与保护 | 真实路径图、命令清单、基线报告、分支/回滚点 | 所有现有测试和构建保持通过 |
| D0 | 免费客户试用环境 | Windows 一键启动、统一网址、四类入口、模拟业务、手动重置 | 1-3 天试用验收通过 |
| P1 | 总部/区域/分店权限收口 | 菜单模板、数据范围、职责分离、专项越权测试 | 权限矩阵全部通过 |
| P2 | Linux 生产部署基础 | prod Compose、Nginx、TLS、配置与密钥隔离、staging | 在干净 Linux 主机可重复部署 |
| P3 | 真实渠道与资金闭环 | 至少一个真实支付渠道、退款、对账、外部通知 | 专项资金和通知验收通过 |
| P4 | 可观测性与灾备 | 指标、日志、告警、加密备份、恢复演练 | 满足 SLO、RPO、RTO |
| P5 | CI/CD 与逐门店灰度 | 流水线、灰度开关、回滚、试点门店 | 试点稳定运行并无重大数据差异 |
| P6 | 生产 Go/No-Go | 最终安全、性能、运维和业务验收 | 全部门禁签字通过 |


## 8. B0：仓库基线审计与保护

> **重要：** B0 只允许读取、运行、记录和创建保护性文档/分支；禁止修改业务行为。

- 读取全部基线文档，确认代码事实和文档冲突时采用“代码与物理数据库描述现状、商业文档定义需求”的规则。
- 识别 backend-next 的构建系统、Java 版本、Spring Boot 版本、测试框架、包根路径、Flyway 路径和配置文件。
- 识别 admin-next、front-next 的包管理器、Node 版本、构建脚本、路由 base、API base 和输出目录。
- 输出 docs/baseline/production-upgrade-repo-map.md，列出真实文件路径、端口、健康端点、容器名和持久卷。
- 输出 docs/verification/B0-baseline-verification.md，记录所有命令、耗时、测试数量和结果。
- 建立 Git tag 或明确 commit SHA 作为升级前回滚点。
| 验收项 | 通过标准 |
| --- | --- |
| 后端测试 | 与当前基线一致，失败、错误、跳过均为 0；若数量变化需解释 |
| 前端测试 | 管理端、客户端、技师端现有测试全部通过 |
| 生产构建 | backend/admin/client/technician 全部构建成功 |
| Flyway | validate 和 migrate 在全新测试库成功 |
| Docker | 现有 mysql、backend、admin、client、technician 均 healthy |
| 基线差异 | 没有未解释的本地修改和未提交生成文件 |


## 9. D0：1-3 天免费客户试用环境

D0 是首个实际开发阶段。部署机器为 Windows 11 + Docker Desktop；电脑在试用期间持续开机联网。客户通过一个 Cloudflare Quick Tunnel 临时 HTTPS 地址访问全部入口。


### 9.1 D0 交付文件

| 类型 | 必须创建或修改 |
| --- | --- |
| Compose | docker-compose.demo.yml；必要时复用基础 docker-compose.yml |
| 配置 | .env.demo.example、backend-next 的 demo Profile、各前端 demo 构建配置 |
| Nginx | deploy/nginx/demo.conf 及相关静态挂载配置 |
| 演示门户 | 独立轻量 demo-portal，或在现有工程中按既有模式创建独立构建；不得混入生产入口 |
| 脚本 | scripts/demo-start.ps1、demo-stop.ps1、demo-backup.ps1、demo-reset.ps1、demo-health-check.ps1 |
| 数据 | 可重复的 DEMO 种子与重置逻辑；只写 DEMO 数据库 |
| 文档 | docs/demo/README.md、账号说明、操作手册、故障排查、试用结束清理说明 |
| 测试 | 后端 Profile/权限/模拟渠道测试，前端门户与响应式测试，Compose 集成检查 |


### 9.2 Cloudflare Quick Tunnel 容器

- cloudflared 必须作为 docker-compose.demo.yml 的容器运行，不要求 Windows 额外安装 cloudflared.exe。
- 容器仅连接内部 Nginx，例如 http://nginx-demo:80，不直接暴露 backend、MySQL 或各前端端口。
- 使用 Quick Tunnel，不提交固定 token；启动日志中自动识别 trycloudflare.com URL。
- demo-start.ps1 启动后等待 URL 生成，将 URL 打印并保存到 .runtime/demo-url.txt；该目录加入 .gitignore。
- demo-stop.ps1 必须停止 cloudflared 和全部 DEMO 服务，使公网入口立即失效。
- Quick Tunnel 只标记为演示用途，不提供可用性承诺，不允许复用到 PROD。

### 9.3 统一路径与 Nginx 规则

```text
/                FACE 演示访问页/入口导航
/admin/          总部与分店管理端
/client/         客户端
/technician/     技师端
/api/            后端 API
/demo-access/    DEMO 访问认证接口
/_internal/      仅容器网络可访问的健康与 auth_request 端点
```

- 所有前端必须正确设置 base path，刷新深层路由时由 Nginx 回退到对应 index.html。
- 外部 /api/ 与三个业务入口全部经过 DEMO 访问门禁；内部健康检查不经过浏览器门禁。
- Nginx 必须设置合理的请求体大小、超时、安全头和静态资源缓存；客户照片上传仍受后端类型和大小校验。
- MySQL 不映射到公网接口；如为本机调试映射端口，只绑定 127.0.0.1。

### 9.4 自定义 FACE 演示访问页

- 访问根路径时展示 FACE 品牌、客户试用环境标识、隐私提示和访问密码输入框。
- 密码在服务端校验，来源为 .env.demo；前端不得包含正确密码或密码哈希。
- 验证成功后签发 12 小时有效的签名 Cookie；Cookie 使用 HttpOnly、Secure、SameSite=Lax 和明确 Path。
- Cookie 不存访问密码；载荷至少包含 demo 标识、签发时间、失效时间和密钥版本。
- 修改访问密码或 DEMO_SIGNING_SECRET_VERSION 后，旧 Cookie 必须失效。
- 连续失败需限流：按 IP 和浏览器标识记录，达到阈值后增加延迟并短时封禁；日志不得记录密码。
- 入口导航显示总部管理端、分店管理端、客户端、技师端四张卡片，并为客户端和技师端显示当前临时地址二维码。
- 提供“退出演示访问”按钮，清除 Cookie 并返回访问页。
- demo Profile 关闭时，演示访问 Controller、Filter、元数据接口和门户服务不得注册。

### 9.5 演示账号与登录页

| 角色 | 建议账号标识 | 入口 | 数据范围 |
| --- | --- | --- | --- |
| 总部所有者 | demo.owner | /admin/ | TENANT |
| 区域经理 | demo.region | /admin/ | REGION |
| 分店店长 | demo.manager | /admin/ | SHOP |
| 前台 | demo.frontdesk | /admin/ | SHOP |
| 仓库 | demo.warehouse | /admin/ | SHOP |
| 财务 | demo.finance | /admin/ | 授权范围 |
| 技师 | demo.beautician | /technician/ | SELF |
| 固定客户 | demo.member | /client/ | SELF |

- 所有固定演示账号使用同一个密码，密码通过 DEMO_COMMON_PASSWORD 环境变量注入。
- 演示账号和角色由种子脚本创建；前端不得硬编码账号和密码。
- 只有通过外层访问门禁后，登录页才能从受保护的 DEMO 元数据接口读取账号说明和公共演示密码。
- 登录页必须提示“演示账号共用密码，请勿录入真实客户信息”。
- 演示账号禁止修改密码、绑定真实手机号、执行找回密码或提升自身权限。
- 生产环境不提供演示账号元数据接口，也不创建这些账号。

### 9.6 客户注册与模拟短信

- 客户端既支持固定演示客户登录，也允许创建新的 DEMO 客户账号。
- 注册页必须提示不得填写真实姓名、真实手机号、健康信息和支付信息。
- DEMO 固定验证码为 888888，并在验证码输入框附近显示“演示验证码，未发送真实短信”。
- 验证码仍由后端校验；有效期、发送间隔、错误次数和频率限制继续执行。
- 手机号可使用演示格式或虚拟号段；日志和审计中必须脱敏。
- prod Profile 不注册固定验证码实现；真实短信未配置时返回 DEPENDENCY_UNAVAILABLE，不得回退 888888。
- 自动化测试必须证明 demo 可使用 888888，prod/staging 不可使用。

### 9.7 模拟支付、退款和通知

| 能力 | DEMO 行为 | 必须保留的真实规则 |
| --- | --- | --- |
| 支付 | 显示“模拟支付”，创建模拟渠道交易并完成现有订单状态流转 | 幂等键、金额校验、订单快照、重复请求不重复入账 |
| 退款 | 显示“模拟退款”，走正式申请、审批、执行和资产冲正流程 | 退款是独立交易，不写负支付；套餐/提成按现有规则冲正 |
| 短信/邮件 | 不调用供应商，记录“未实际发送”的模拟投递结果 | 同意、模板、频率限制、投递账本和审计仍执行 |
| 站内通知 | 真实写入 DEMO 站内通知 | outbox 幂等和本人可见规则保持不变 |

- 所有模拟记录必须带 channel=DEMO_* 或等价明确标识，报表和页面不得误报为真实渠道成功。
- 模拟能力不得绕过权限、审批、状态机、库存、套餐或提成规则。
- 生产配置没有 DEMO_* 渠道的默认启用值；错误配置时启动失败或明确不可用。

### 9.8 DEMO 危险操作保护

| 操作 | DEMO 处理 |
| --- | --- |
| 物理删除核心业务记录 | 后端拒绝，返回稳定错误码 DEMO_OPERATION_DISABLED |
| 客户/订单/报表批量导出 | 后端拒绝；前端隐藏并显示演示限制说明 |
| 开放平台密钥创建、轮换、查看 | 禁用 |
| 真实退款执行 | 强制使用 DEMO_REFUND |
| 真实短信/邮件/企业微信 | 禁用外部发送 |
| 修改系统级安全配置 | 禁用 |
| 修改演示账号密码/权限 | 禁用 |
| 数据库清库 | 仅允许 demo-reset.ps1 在本机交互执行 |

- 危险操作限制由后端统一策略实现，不能只依赖前端按钮隐藏。
- 所有拒绝行为写审计日志，包含用户、角色、request_id 和动作，不记录敏感载荷。

### 9.9 演示数据与重置

- 种子数据至少包含 1 个租户、2 个门店、8 类账号、20-30 个模拟会员、项目、技师技能、排班、房间、设备、产品、库存、套餐、预约、护理、支付、退款、提成、售后和报表样例。
- 演示数据必须通过现有领域服务、受控迁移或明确的 DEMO 初始化工具创建，禁止前端伪造权限或直接拼装不一致数据。
- 试用期间保留客户操作，不做每天自动重置，不随 Docker 重启自动清库。
- demo-reset.ps1 执行前自动调用 demo-backup.ps1，显示目标数据库名、容器和数据卷，并要求输入明确确认词。
- 重置脚本必须验证环境标识为 DEMO、数据库名包含允许的 demo 标识、生产安全开关未开启；任一不满足立即退出。
- 重置保留 Flyway schema history，清理试用业务数据后重新执行受控 DEMO 初始化。
- 重置完成自动运行健康检查和核心业务烟雾测试。
- 试用数据默认不迁入生产；确需保留时另立脱敏、同意与迁移任务。

### 9.10 PowerShell 一键脚本契约

| 脚本 | 必须行为 |
| --- | --- |
| demo-start.ps1 | 检查 Docker Desktop；检查 .env.demo；构建并启动；等待健康；提取 Tunnel URL；打印账号和入口 |
| demo-health-check.ps1 | 检查容器、Flyway、readiness、四入口、门禁、模拟短信和受保护 API |
| demo-backup.ps1 | 生成带时间戳的数据库备份和配置清单；不得包含明文密钥 |
| demo-reset.ps1 | 备份、二次确认、环境防误删、重置数据、初始化、健康检查 |
| demo-stop.ps1 | 停止 Tunnel 和 DEMO 栈；验证公网 URL 不再可达；保留数据卷，除非显式传入清理参数 |


### 9.11 响应式与设备验收

| 端 | 主要设备 | 必须验收尺寸 |
| --- | --- | --- |
| 管理端 | 电脑 | 1366x768、1920x1080 |
| 客户端 | 手机优先，电脑可用 | 375x667、390x844、430x932、1280x720、1920x1080 |
| 技师端 | 手机优先，电脑可用 | 375x667、390x844、430x932、1280x720、1920x1080 |
| 演示门户 | 电脑和手机 | 与上述全部尺寸兼容 |

- 客户端和技师端在电脑上不能只显示过窄的手机模拟框；应采用合理最大宽度、居中布局和可用导航。
- 不要求管理端完整适配手机，但访问门户和登录页必须在手机可读。

### 9.12 D0 必须测试的场景

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| D0-T01 | 未输入访问密码访问 /admin/ | 返回访问页或 401，不暴露登录页 |
| D0-T02 | 正确密码登录后访问四入口 | 12 小时 Cookie 生效 |
| D0-T03 | 连续错误访问密码 | 限流并产生脱敏审计 |
| D0-T04 | 关闭浏览器后重新访问 | 12 小时内仍有效 |
| D0-T05 | 修改密钥版本 | 旧 Cookie 立即无效 |
| D0-T06 | DEMO 固定账号登录 | 各角色只能看到自身菜单和数据范围 |
| D0-T07 | 客户用 888888 注册 | 成功，显示未发送真实短信 |
| D0-T08 | 在 prod Profile 使用 888888 | 失败 |
| D0-T09 | 模拟支付重复提交 | 只生成一笔有效交易 |
| D0-T10 | 模拟退款 | 形成独立退款及资产/提成冲正 |
| D0-T11 | 尝试批量导出或密钥创建 | 后端拒绝 |
| D0-T12 | 重启 Docker | 试用数据仍保留 |
| D0-T13 | 执行 reset | 先备份，重置后固定账号和初始数据恢复 |
| D0-T14 | 停止 DEMO | Tunnel 地址不可继续访问 |
| D0-T15 | 手机扫码进入客户端/技师端 | 同一临时域名和正确路径可用 |


### 9.13 D0 完成定义

- 在一台未配置项目的 Windows 11 + Docker Desktop 机器上，按 docs/demo/README.md 可以完成从配置到公网访问。
- 一条 PowerShell 命令可启动全部容器，自动输出 Quick Tunnel 地址和四类入口。
- 总部、分店、技师、客户四类入口可在电脑访问；客户端和技师端可手机扫码访问。
- 预约、到店、护理、耗材、模拟支付、套餐核销、库存扣减、提成、模拟退款和售后闭环可演示。
- 真实支付、真实短信、导出、删除和密钥管理不会被误调用。
- 全部测试、构建、Flyway、Compose 健康检查通过，验收报告提交后 项目组 停止等待批准。

## 10. P1：总部、区域与分店管理边界升级

P1 不创建第二套后台，而是在 admin-next 和后端权限上下文中形成总部、区域、分店和岗位菜单模板。

| 角色/模板 | 范围 | 主要能力 | 必须限制 |
| --- | --- | --- | --- |
| OWNER/HQ_ADMIN | TENANT | 组织、门店、规则、财务、采购、经营和系统治理 | 高风险动作仍需职责分离与审计 |
| REGIONAL_MANAGER | REGION | 区域分析、预约、库存、订单、营销、培训 | 不得越区域 |
| MANAGER | SHOP | 本店会员、预约、护理监督、库存、采购、运营 | 不得越门店，不得自批本人申请 |
| FRONT_DESK | SHOP | 会员、预约、订单、套餐、售后发起 | 不得完成护理、执行退款、结案对账 |
| WAREHOUSE | SHOP | 库存查询、采购、收货、调拨申请、盘点录入、报损申请 | 无会员、支付、提成权限 |
| FINANCE | 授权范围 | 退款、对账、提成结算、审批 | 发起与确认分离 |

- 补充 HQ_ADMIN、HQ_OPERATION、HQ_FINANCE、HQ_INVENTORY、TRAINING_MANAGER、CUSTOMER_SERVICE 等职能角色时，优先复用现有角色与权限表，不新增第二套授权模型。
- MANAGER 当前能力必须经过职责分离审查，避免同一账号同时创建、审批、执行和结案高风险业务。
- 菜单模板由后端返回授权结果或基于权限定义生成；不能以硬编码角色名作为唯一授权依据。
- 为总部、区域、本店、SELF 和范围外资源建立接口级集成测试。

## 11. P2：Linux Docker Compose 生产部署基础

- 新增 docker-compose.prod.yml 和可选 docker-compose.staging.yml；不得复用 DEMO 模拟服务。
- 生产仅暴露 Nginx 的 80/443，backend、MySQL 和内部服务位于私有 Docker 网络。
- Nginx 配置正式域名、TLS、HTTP 到 HTTPS 跳转、安全头、上传限制、超时、静态缓存和反向代理。
- 支持 MySQL 容器和外部 MySQL 两种模式，通过环境变量切换；生产禁止使用默认密码。
- 文件上传和报表不得只依赖临时容器文件系统；使用持久卷或对象存储适配器。
- 提供 scripts/prod-preflight.sh、prod-deploy.sh、prod-health-check.sh、prod-backup.sh、prod-rollback.sh。
- 部署必须可重复执行；同一版本重复部署不得破坏数据或生成重复迁移。
- 创建 staging 环境先完成真实域名/TLS/回调测试，再允许 prod。

## 12. P3：生产安全、密钥与会话

- 生产密钥从云密钥服务、Docker secrets 或受权限保护的环境文件注入；不进入镜像、Git、构建日志和前端产物。
- 访问令牌、会话撤销、Cookie、CORS、CSRF、上传文件、密码策略和登录限流按生产标准复核。
- 总部高权限账号启用强密码和二次认证能力；至少为 OWNER/HQ_ADMIN/FINANCE 提供 MFA 接口或实现。
- 敏感字段按业务需要加密或令牌化，密钥支持版本和轮换。
- 输出权限专项报告，覆盖对象级越权、字段级越权、批量导出和范围外防枚举。
- 依赖漏洞、镜像漏洞、密钥扫描和 SAST 进入 CI 门禁。

## 13. P4：真实支付、退款、对账和外部通知

> **重要：** 本阶段只有在客户提供真实商户和通知渠道凭据后实施。没有凭据时保持明确不可用，不允许伪装上线。

- 至少接入一个真实支付渠道，复用现有订单、支付回调、签名、Nonce、防重放和幂等模型。
- 回调校验渠道签名、商户号、订单号、金额、币种、时间戳和事件唯一性；重复或乱序回调不重复入账。
- 退款走独立交易和审批，成功后受控触发套餐、会员资产和提成冲正。
- 建立日对账任务、自动匹配、差异处理和结案；差异未解释不得结案。
- 接入至少一个外部通知渠道，完成客户同意、退订、模板、频率限制、供应商回执和投递账本。
- 真实渠道全部在 staging 先验收；生产凭据不进入 DEMO。

## 14. P5：监控、日志、备份与灾备

| 领域 | 最低要求 |
| --- | --- |
| 指标 | 请求量、错误率、P50/P95/P99、JVM、连接池、MySQL、支付回调、outbox、库存/套餐/提成异常 |
| 日志 | 结构化 JSON、request_id、tenant/shop、脱敏、等级和保留策略 |
| 告警 | 服务不可用、错误率、慢请求、磁盘、备份失败、回调积压、对账差异、库存负值尝试 |
| 备份 | 数据库和文件/对象存储备份，加密、校验、异地副本、明确保留周期 |
| 恢复 | 定期在隔离环境恢复，核对订单金额、库存、套餐、提成和 Flyway 历史 |
| 目标 | 试点期建议 RPO <= 24h、RTO <= 4h；正式签约前按客户 SLA 调整 |


## 15. P6：CI/CD、灰度和生产发布

- CI 顺序至少包括：后端测试、前端测试、生产构建、Flyway validate、镜像构建、依赖/密钥扫描和 Compose 配置校验。
- 发布采用 Expand -> Migrate -> Contract；先部署向后兼容代码，再迁移和回填。
- 按门店功能开关逐步开放新写路径；先内部门店或试点门店，再扩大范围。
- 发布前备份 schema、Flyway history、核心表计数和订单/库存/套餐/提成汇总。
- 异常时关闭新入口并回退应用，保留新增表、审计和历史事实；禁止直接 DROP 或人工改业务流水。
- Contract 删除和改名必须在稳定周期后单独审批。

## 16. 生产 Go/No-Go 门禁

| 编号 | 门禁 | Go 标准 |
| --- | --- | --- |
| G01 | 功能回归 | 全部 P0 核心闭环和角色流程通过 |
| G02 | 权限 | TENANT/REGION/SHOP/SELF 越权测试全部通过 |
| G03 | 数据迁移 | 影子库 migrate/validate 和数据核对通过 |
| G04 | 资金 | 支付、退款、回调、对账无未解释差异 |
| G05 | 资产 | 库存、套餐、会员余额、提成无负值或重复流水 |
| G06 | 安全 | 无高危未处置漏洞和明文密钥 |
| G07 | 性能 | 按生产容量模型完成压力测试且满足商定 SLO |
| G08 | 监控 | 核心指标、日志和告警真实触发验证通过 |
| G09 | 备份恢复 | 完成一次加密备份和隔离恢复演练 |
| G10 | 回滚 | 应用回滚和功能开关关闭演练通过 |
| G11 | 运维 | Runbook、值班联系人和故障升级路径齐全 |
| G12 | 业务 | 试点门店负责人、财务和数据负责人签字 |


## 17. API 与错误语义新增要求

| 能力 | 建议契约 |
| --- | --- |
| DEMO 访问登录 | POST /demo-access/session；仅 demo Profile |
| DEMO 访问校验 | GET /_internal/demo-access/verify；仅容器网络或 Nginx auth_request |
| DEMO 访问退出 | DELETE /demo-access/session |
| DEMO 元数据 | GET /api/v3/demo/metadata；需外层访问 Cookie；仅 demo |
| 模拟短信 | 复用正式验证码契约，由 demo provider 返回模拟结果 |
| 模拟支付/退款 | 复用正式业务接口，通过渠道代码 DEMO_* 选择 provider |

- 新增稳定错误码：DEMO_ACCESS_DENIED、DEMO_RATE_LIMITED、DEMO_OPERATION_DISABLED；其余沿用现有错误语义。
- DEMO 接口不得出现在 production OpenAPI 文档或运行路由中。
- Nginx 内部校验端点不得直接暴露业务数据。

## 18. 数据库与 Flyway 要求

- 优先复用现有 account、role、permission、notification、payment、refund 和 audit 模型。
- DEMO 固定账号和业务样例优先使用独立 DEMO 初始化脚本或 demo-only 迁移机制；生产迁移不得自动创建公网演示账号。
- 如新增演示访问失败记录、密钥版本或功能开关表，必须包含 tenant/environment 范围和清理策略；更推荐使用短期缓存或现有审计能力，避免无必要新表。
- 任何新增索引必须有查询依据和影子库执行时间记录。
- 所有回填可重入、有水位、有核对；禁止无条件全表更新。
- 生产数据修复通过新的补偿迁移或应用服务完成，禁止直接编辑既有 Flyway 文件。

## 19. 项目组 每阶段输出格式

```text
阶段：D0
状态：等待验收

1. 已完成任务
2. 实际修改文件
3. 新增/修改的 API、表、权限点和状态
4. 执行的测试与完整结果
5. Docker/Flyway/构建验证
6. 安全与数据检查
7. 演示或验收步骤
8. 回滚方法
9. 已知限制
10. 下一阶段建议（不得自动开始）
```

- 报告必须给出真实命令和结果摘要，不能只写“测试通过”。
- 失败、跳过或未执行项必须明确列出原因。
- 阶段报告保存到 docs/verification/，文件名包含阶段和日期。

## 20. 项目组 提交和评审规则

- 一个提交只包含一个可独立解释的行为变化；配置、测试和文档随对应功能一起提交。
- 提交信息使用清晰前缀，例如 feat(demo)、fix(auth)、test(permission)、docs(runbook)、chore(deploy)。
- 每个阶段至少进行一次需求符合性评审和一次代码质量评审。
- 不得为了让测试通过而删除测试、降低断言、关闭权限校验或吞掉异常。
- 不得提交 .env.demo、.env.prod、商户证书、访问密码、统一演示密码、数据库备份和 .runtime 文件。

## 21. D0 客户演示操作脚本

1. 在 Windows 11 启动 Docker Desktop，并确认 WSL2/Docker Engine 正常。
1. 复制 .env.demo.example 为 .env.demo，填写 DEMO_ACCESS_PASSWORD、DEMO_COMMON_PASSWORD 和 DEMO_SIGNING_SECRET。
1. 运行 PowerShell：scripts/demo-start.ps1。
1. 等待脚本输出 trycloudflare.com 地址、总部/分店/客户/技师入口和演示账号。
1. 客户先输入统一访问密码，再选择入口；手机扫描客户端或技师端二维码。
1. 客户使用固定账号或在客户端以 888888 注册新的演示账号。
1. 演示结束运行 scripts/demo-backup.ps1，再运行 scripts/demo-stop.ps1。
1. 需要恢复初始数据时运行 scripts/demo-reset.ps1，确认备份和目标数据库后执行。

## 22. 首次交给 项目组 时的执行边界

> **重要：** 第一次只执行 B0。B0 报告通过后，再执行 D0。项目组 不得一次性实现 P1-P6。

- B0 完成后，项目组 输出 D0 的精确文件级实施计划，等待批准。
- D0 完成并通过客户演示环境验收后，才开始 P1。
- 真实支付、短信、生产域名和商户凭据未提供前，不开始 P3。
- 生产发布前必须建立 staging，并完成 P2-P6 门禁。

## 附录 A：建议环境变量

| 变量 | 环境 | 用途/安全要求 |
| --- | --- | --- |
| SPRING_PROFILES_ACTIVE | demo/prod | 必须显式设置，不允许生产默认 demo |
| FACE_ENVIRONMENT | 全部 | dev/test/demo/staging/prod 枚举 |
| DEMO_ACCESS_PASSWORD | demo | 外层访问密码，不提交 Git |
| DEMO_COMMON_PASSWORD | demo | 固定账号共用密码，不提交 Git |
| DEMO_SIGNING_SECRET | demo | 签名 Cookie，至少 32 字节随机值 |
| DEMO_SIGNING_SECRET_VERSION | demo | 修改后使旧 Cookie 失效 |
| DEMO_COOKIE_TTL_SECONDS | demo | 固定 43200 |
| DEMO_SMS_CODE | demo | 固定 888888；生产不得定义 |
| DEMO_DISABLE_EXPORT | demo | 固定 true |
| DEMO_DISABLE_DESTRUCTIVE | demo | 固定 true |
| PAYMENT_PROVIDER | demo/prod | demo 使用 DEMO；prod 使用真实 provider |
| NOTIFICATION_PROVIDER | demo/prod | demo 使用 DEMO/IN_APP；prod 使用真实 provider |
| DB_HOST/DB_NAME/DB_USER/DB_PASSWORD | 全部 | 环境隔离，prod 无默认弱密码 |
| PUBLIC_BASE_URL | demo/prod | Quick Tunnel 或正式域名 |


## 附录 B：D0 验收结论模板

```text
D0 验收结论：PASS / FAIL

- Windows 11 + Docker Desktop：
- Quick Tunnel 地址：
- 四端入口：
- 访问门禁与 12 小时 Cookie：
- 固定账号与统一密码：
- 客户注册与 888888：
- 模拟支付/退款/通知：
- 删除/导出/密钥禁用：
- 数据保留、备份和手动重置：
- 手机/电脑响应式：
- 后端测试：
- 前端测试与构建：
- Flyway：
- Docker 健康：
- 安全扫描：
- 已知限制：

人工决定：通过，允许进入 P1 / 不通过，返回 D0 修复
```
