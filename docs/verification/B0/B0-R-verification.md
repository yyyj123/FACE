# B0-R 最终验收报告

阶段：B0-R（基线整改）  
版本：B0-R 1.0.0  
结论：通过  
状态：立即停止，等待人工回复“通过，进入 SC0”

## 1. 实际完成范围

1. 已确认原始交付目录在本轮开始前不是 Git 工作树，不存在可读取的 `.git`、commit 或 tag。
2. 已建立“当前首次可追溯快照”，并在提交正文和注释标签中明确声明它不是原始开发历史。
3. Docker 验收路径已显式执行 Flyway `migrate` 和 `validate`；同时从迁移目录发现版本，并与 `flyway_schema_history` 中成功的 SQL 版本逐项对账，不再把某个历史版本写死为预期值。
4. 后端、管理端、用户端和既有技师端容器的宿主机端口均来自环境文件或显式命令参数。官方脚本在构建/启动前检查端口占用；发现占用后失败退出，不自动随机选择端口。
5. 官方脚本在两个独立 Compose project 和全新 volume 上连续完成两轮：后端测试、管理端构建、用户端构建、数据库迁移、Flyway 校验、迁移历史对账、镜像构建、容器健康、反向代理登录、SPA 检查、全容器重启复检和清理。
6. 没有修改业务功能、业务 API、权限、状态机或 Flyway 迁移内容，没有进入 SC0。

## 2. Git 基线与来源限制

| 项目 | 值 |
| --- | --- |
| 产品范围版本 | PRD V3.0 |
| 首次可追溯快照 SHA | `ff635972a7d4feb4d5192b2f385ebd3703f36326` |
| 注释标签 | `baseline/first-traceable-v3.0` |
| 提交时间 | `2026-08-03T11:10:24+08:00` |
| 提交主题 | `chore(baseline): record first traceable delivered snapshot` |
| 来源声明 | 仅代表 2026-08-03 当时磁盘上的交付源码状态；不是、也不得表述为原始开发历史 |

原目录没有 Git 元数据，也没有配置可用于追溯原始历史的 Git remote。因此无法提供真实的升级前开发 commit、原始作者链、分支或历史 tag。首次快照排除了已有 `node_modules`、`target`、`dist`、运行时缓存、临时数据库、备份等生成/运行产物；交付源码、脚本和文档纳入快照。

## 3. 修改文件清单

- `.env.docker.example`：补齐既有技师端宿主机端口变量示例。
- `.gitignore`：排除生成物、运行时缓存、临时数据库和备份目录。
- `backend-next/pom.xml`：加入 Flyway 12.4.0 Maven 运维执行器及 MySQL 驱动，仅供显式 `migrate`/`validate` 使用。
- `compose.yaml`：加入 `tools` profile 下的隔离 migration runner；沿用应用既有 baseline-on-migrate 配置，不对外暴露 MySQL。
- `scripts/verify-docker-runtime.ps1`：端口参数/预检、显式测试与构建、Flyway 动态校验、健康及重启检查。
- `scripts/verify-docker-contract.ps1`：校验端口变量、migration runner 和动态 Flyway 契约。
- `scripts/test-b0-runtime-contract.ps1`：B0-R 静态契约回归测试。
- `scripts/verify-m1-v3-security.ps1`、`scripts/verify-m6-static-security.ps1`：旧验收断言改为从迁移目录动态推导最新版本。
- `docs/plans/2026-08-03-b0-r.md`：B0-R 真实路径执行计划。
- `docs/verification/B0/*`：本报告和两轮完整日志。

## 4. Flyway 与数据影响

- 新增/修改 Flyway SQL：无。
- 迁移目录发现：30 个版本迁移 + 1 个 beforeMigrate callback。
- 两轮 `flyway:migrate`：均成功应用 30 个版本迁移。
- 两轮独立 `flyway:validate`：均成功校验 31 个迁移资源。
- 两轮目录/历史表对账：`COUNT=30`、`LATEST=2026080204`、失败记录 0、差异 0。
- 验证数据库：仅使用合成测试凭据和每轮新建的 Compose volume；每轮结束均执行 `down --volumes --remove-orphans`。
- 真实/生产数据库影响：无。

## 5. API、权限与状态机变化

- 新增/修改业务 API：无。
- 权限变化：无。
- 状态机变化：无。
- SC0 入口收口：未执行。既有技师容器仅作为当前运行拓扑的回归对象保留，未新增技师端交付能力。

## 6. 官方命令

静态契约：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-b0-runtime-contract.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docker-contract.ps1
```

端口占用拒绝测试使用预先监听的 `8399`，官方脚本按预期报错，并输出：

```text
PORT_OCCUPIED_REJECTION=PASS
PORT_RANDOM_FALLBACK=DISABLED
```

第一轮完整证据命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docker-runtime.ps1 `
  -ProjectName face-b0r-evidence1 `
  -BackendPort 8390 -AdminPort 8381 -ClientPort 8382 -TechnicianPort 8383
```

第二轮完整证据命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docker-runtime.ps1 `
  -ProjectName face-b0r-evidence2 `
  -BackendPort 8490 -AdminPort 8481 -ClientPort 8482 -TechnicianPort 8483
```

脚本内部的正式步骤为：

```text
compose down --volumes --remove-orphans
端口占用预检
Docker/B0 静态契约
mvn test
npm --prefix admin-next run build
npm --prefix front-next run build
compose up mysql --wait
Flyway Maven runner: flyway:migrate
Flyway Maven runner: flyway:validate
flyway_schema_history 与迁移目录逐版本对账
compose up --build --wait
健康/API/SPA/登录检查
compose restart + compose up --wait
重启后重复健康/API/SPA/登录检查
compose down --volumes --remove-orphans
```

## 7. 两轮结果

| 检查项 | 第一轮 | 第二轮 |
| --- | --- | --- |
| 显式固定端口预检 | PASS | PASS |
| B0/Docker 静态契约 | PASS | PASS |
| 后端测试 | 165/165 PASS | 165/165 PASS |
| 管理端生产构建 | PASS | PASS |
| 用户端生产构建 | PASS | PASS |
| Flyway migrate | PASS | PASS |
| Flyway validate | PASS | PASS |
| 迁移目录/历史表 | 30，最新 2026080204，差异 0 | 30，最新 2026080204，差异 0 |
| 初次容器健康与入口 | PASS | PASS |
| 全容器重启复检 | PASS | PASS |
| 测试容器与 volume 清理 | PASS | PASS |
| 官方脚本退出码 | 0 | 0 |

证据文件完整性：

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `round-1.log` | 130906 | `FFEDF1F277A1E0A28E837849ACDCDFF7B3CE2D995DC1ECCF861D292D7EDC1763` |
| `round-2.log` | 130446 | `90E2CD05B47BBDCD651FC4B453E3DDE0DFE4EA77B805C2B8BF44ABF1C6AE26BB` |

## 8. 工具与组件版本

| 组件 | 版本 |
| --- | --- |
| Windows | 10.0.26200.0 |
| Windows PowerShell | 5.1.26100.7462 |
| Git | 2.45.1.windows.1 |
| Docker Engine | 29.6.1 |
| Docker Compose | v5.1.4 |
| MySQL 镜像 | 8.4 |
| Apache Maven | 3.9.16 |
| Java | Eclipse Adoptium 21.0.11 LTS |
| Flyway | 12.4.0 |
| MySQL Connector/J | 9.7.0 |
| Node.js | v24.18.0 |
| npm | 11.16.0 |
| Spring Boot | 4.1.0 |
| 后端 artifact | 0.1.0-SNAPSHOT |
| workspace package | 1.0.0 |

## 9. 安全、隐私与审计检查

- 使用 `.env.docker.test` 中声明为 synthetic 的本地测试凭据；日志未输出数据库密码。
- 未使用真实客户数据、生产密钥、真实支付或外部发送能力。
- MySQL 仍仅在 Compose 内部网络可访问，没有为迁移校验新增宿主机数据库端口。
- Compose 密码继续由环境变量注入，契约检查禁止内嵌数据库密码。
- 日志中没有生产个人信息；完整日志只包含构建、测试、迁移和容器状态。

## 10. 回滚方式

- 首次快照可通过注释标签 `baseline/first-traceable-v3.0` 在新的分支/工作树中只读复现。
- B0-R 基础设施变更可用 Git 对本次 B0-R 提交执行普通 `git revert`；禁止修改既有 Flyway SQL 或数据库历史。
- B0-R 测试数据库无需业务回滚：两轮 volume 已删除，容器/网络已清理。

## 11. 已知风险与警告

1. 无法恢复原始开发历史、原作者链或升级前真实 tag；首次快照只能解决从当前时点向后的可追溯性。
2. 历史迁移在 MySQL 8.4 下产生 `VALUES()` 弃用警告，但迁移和校验均成功；本阶段禁止修改已执行迁移，后续只能在获批阶段用新增迁移/代码消除。
3. Maven 测试提示 Mockito 动态 agent 未来 JDK 兼容警告；当前 165 个测试全部通过。
4. npm 提示旧 `sass_binary_site` 配置将在未来主版本失效，Rollup 还报告第三方依赖纯函数注释位置警告；当前双前端生产构建成功。
5. `.env.docker.test` 的历史镜像标签仍名为 `m6-10-acceptance`，但其凭据明确为 synthetic，且本轮用独立 project 名和 volume 隔离；未在 B0-R 擅自重命名历史验收资产。

## 12. 最终门禁

B0-R 五项要求均已满足，结论为“通过”。本阶段到此立即停止；在人工明确回复“通过，进入 SC0”之前，不执行 SC0 或任何业务修改。
