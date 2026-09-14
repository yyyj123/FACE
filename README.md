# FACE 用户端与运营管理平台

当前有效产品基线为 **V3.0 方案一：在现有系统上收缩范围并增量升级**。仓库保留既有实现作为迁移和兼容资产，但当前只交付两个可见产品与一个统一后端。

## 当前交付范围

| 交付面 | 代码目录 | 对外入口 | 说明 |
|---|---|---|---|
| 响应式 H5/Web 用户端 | `front-next` | `/client/` | 面向顾客，后续微信小程序复用同一后端 |
| 响应式运营管理平台 | `admin-next` | `/admin/` | 面向运营管理员，兼容桌面和移动浏览器 |
| 统一后端 | `backend-next` | `/api/` | 统一承载身份、领域能力、审计与数据访问 |
| DEMO 入口 | 部署网关 | `/` | 只展示用户端、运营管理平台和 API 状态入口 |

未来微信小程序是用户端的另一种交付外壳，不新建平行后端。

## 冻结范围

以下既有能力不属于 V3.0 当前新增交付：

- 独立技师端；
- 总部端、分店端和复杂多岗位后台；
- 采购、供应商、调拨、复杂盘点；
- 自动提成、提成结算；
- 培训与考试。

相关源码、接口、数据表、迁移和 Docker 兼容服务继续保留，不删除、不扩展，也不出现在公开 DEMO 导航中。历史 V2.1 PRD 和既往验收材料仅作为来源记录，不代表当前交付承诺。完整处置矩阵见 `docs/architecture/v3-scope-module-matrix.md`。

## 代码结构

- `front-next`：当前 Vue 3 用户端；内部仍含冻结的技师兼容代码。
- `admin-next`：当前 Vue 3 运营管理平台。
- `backend-next`：当前 Spring Boot 统一后端与 Flyway 迁移。
- `front`、`admin`、`backend`：历史迁移参考与回退资产，不是当前交付主线。
- `docs`：规格、架构、运维与逐阶段验收证据。

## 本地验证

后端测试与前端构建：

```powershell
mvn -B -ntp -f .\backend-next\pom.xml test
npm --prefix .\admin-next run build
npm --prefix .\front-next run test
npm --prefix .\front-next run build
```

Docker 配置、迁移和运行时验收以仓库脚本为准：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docker-contract.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docker-runtime.ps1 -EnvFile .env.docker.test
```

端口必须通过环境文件显式配置；脚本会在启动前检查占用，不会自动随机选择端口。部署细节见 `docs/operations/docker-deployment.md`。

## SC8 受控公网 DEMO

SC8 使用 Cloudflare Quick Tunnel 暂时暴露 Compose 内部网关。公开导航仅包含 `/client/` 与 `/admin/`，`/api/` 复用统一后端，独立技师端不会启动或公开。Quick Tunnel 仅用于短期验收，不是生产部署方式。

先复制环境模板并替换全部占位密钥；端口必须显式填写，脚本不会随机选择：

```powershell
Copy-Item .\.env.demo.example .\.env.demo
# 编辑 .env.demo 后启动
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-start.ps1
```

固定账号为运营端 `admin`、`demo-admin` 和用户端手机号 `13900000001`；三者使用 `FACE_DEMO_ADMIN_PASSWORD`。演示短信验证码固定为 `888888`，支付、退款与通知均为模拟通道，不会发送真实短信或产生真实资金交易。

运维操作：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-backup.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-reset.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-stop.ps1
```

完整可重复验收：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc8-runtime.ps1 -Run 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc8-runtime.ps1 -Run 2
```

重置会重新创建数据卷，通过 Flyway `migrate`、`validate` 与迁移目录/`flyway_schema_history` 动态比对恢复固定数据；停止后当前公网地址必须失效。

## SC9 生产部署准备

生产部署使用独立的 `docker-compose.prod.yml`，只公开 TLS 网关的 `/client/`、`/admin/`、`/api/` 和对象下载路径。数据库、后端、对象存储、Alertmanager 与 Pushgateway 均不映射公网端口；Prometheus 只绑定宿主机回环地址。独立技师端保持冻结且不会启动。

在 Linux 主机上复制 `.env.prod.example` 为 `.env.prod`，显式设置域名与端口，并在 `FACE_SECRET_DIR` 指向的目录创建所有 Secret 文件。生产启动、备份、恢复演练和 Go/No-Go 命令见 `docs/operations/docker-deployment.md`。默认支付、短信和物流通道均为 `disabled`；没有通过 Sandbox、小额支付、退款、对账或对应物流验收并提交 SHA-256 证据时，生产保护器会拒绝启动，绝不会回退到 DEMO 通道。

SC9 两轮验收命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc9-runtime.ps1 -Run 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc9-runtime.ps1 -Run 2
```

## WEB-R 响应式 Web 稳定化

WEB-R 只完善 `front-next` 用户端和 `admin-next` 运营管理平台的桌面/手机浏览器体验，不修改业务规则，也不进入微信小程序。两端现已具备路由级页面标题与焦点恢复、跳到主要内容、可见焦点、44px 触控目标、安全区适配、减少动态效果支持；用户端页面按路由分包，运营端移动抽屉支持 Escape、遮罩和导航关闭并在关闭后归还焦点。

正式验收使用显式端口，从两个独立干净 Compose project 连续执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-web-r-runtime.ps1 -Run 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-web-r-runtime.ps1 -Run 2
```

验收覆盖 390×844、768×1024、1440×960 和 844×390 四种视口，以及数据库动态迁移校验、后端全量测试、双前端测试/构建、SC8 核心业务回归、容器健康和重启恢复。完整证据见 `docs/verification/WEB-R/WEB-R-verification.md`。

## 可追溯基线

- 首次可追溯快照：`baseline/first-traceable-v3.0`。该标签是对收到代码的首次快照，不代表或伪造原始开发历史。
- B0-R 可重复验证：`b0-r/v1.0.0`。
- WEB-R 响应式 Web 稳定化：`web-r/v1.0.0`。
- 当前阶段：WEB-R 已完成并停止；微信小程序与 SC10 保持延期，不自动恢复 `stash@{0}`，等待人工下一步明确指令。
