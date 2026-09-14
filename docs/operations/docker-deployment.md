# Docker Compose 部署

## V3.0 对外入口

| 产品 | 默认宿主机端口 | 目标部署路径 |
|---|---:|---|
| 运营管理平台 | `8081` | `/admin/` |
| H5/Web 用户端 | `8082` | `/client/` |
| 统一后端 readiness | `8090` | `/api/` |

Compose 中的 `technician` 服务和 `FACE_TECHNICIAN_PORT` 属于冻结兼容资产，不进入公开 DEMO 导航，也不属于 V3.0 当前新增交付。SC0 保留该服务以避免改变既有运行行为。

## 本地或单机部署

1. 确认 Docker Desktop 的 Linux 容器引擎已启动。
2. 将 `.env.docker.example` 复制为 `.env.docker`，显式设置所有宿主机端口，并用不同的强随机密码替换示例值。
3. 执行端口预检和配置契约：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docker-contract.ps1 -EnvFile .env.docker`。
4. 解析配置：`docker compose --env-file .env.docker config --quiet`。
5. 构建并启动：`docker compose --env-file .env.docker up -d --build --wait`。
6. 查看状态：`docker compose --env-file .env.docker ps`。
7. 停止但保留数据：`docker compose --env-file .env.docker down --remove-orphans`。

验收脚本不会在端口冲突时自动随机选择替代端口；请显式修改环境文件后重试。

## SC9 Linux 生产部署

SC9 生产编排使用 `docker-compose.prod.yml`，本地/DEMO 编排不作为生产入口。准备步骤：

1. 在 Linux 主机安装 Docker Engine、Compose plugin、OpenSSL、`ss` 和 POSIX `sh`。
2. 将 `.env.prod.example` 复制为 `.env.prod`，显式设置 `FACE_DOMAIN`、`FACE_PUBLIC_ORIGIN`、HTTP/HTTPS/Prometheus 端口及所有目录；脚本不会随机选择端口。
3. 在 `FACE_SECRET_DIR` 下创建无换行的 `db_password`、`db_root_password`、`object_store_access_key`、`object_store_secret_key`、`sms_http_bearer_token`、`alert_webhook_url`，并放入 PEM 格式的 `tls_certificate.pem`、`tls_private_key.pem`。非验收环境的敏感文件权限必须是 `600` 或 `400`。
4. 将真实渠道 JAR 放到 `FACE_ADAPTER_DIR`，将验收证据放到 `FACE_CHANNEL_EVIDENCE_DIR`；未启用真实渠道时保持 `payment=disabled`、`SMS_MODE=disabled`、`logistics=disabled`。
5. 执行 `FACE_ENV_FILE=/path/to/.env.prod FACE_PROJECT_NAME=face-production ./scripts/prod-start.sh`。脚本依次完成端口预检、证书有效期检查、`migrate`、`validate`、构建、健康等待和 Go/No-Go。

停止但保留生产数据：

```sh
FACE_ENV_FILE=/path/to/.env.prod FACE_PROJECT_NAME=face-production ./scripts/prod-stop.sh
```

生产网关将 HTTP 308 跳转至 HTTPS，并添加 HSTS、CSP、X-Content-Type-Options、Referrer-Policy 与 Permissions-Policy。仅网关监听公网；Prometheus 默认只绑定 `127.0.0.1`。Alertmanager 从 `alert_webhook_url` Secret 读取值，接收后端不可用、JVM 堆高水位和备份过期告警。

## 备份与恢复演练

备份包含 MySQL 一致性转储、对象存储镜像、数据库指纹、manifest 和相对路径 SHA-256 清单：

```sh
FACE_ENV_FILE=/path/to/.env.prod FACE_PROJECT_NAME=face-production ./scripts/prod-backup.sh
```

恢复演练必须使用独立且以 `-restore-drill` 结尾的 Compose 项目；脚本校验哈希、创建隔离卷、恢复数据库与对象并比对指纹，退出时删除演练卷，不触碰生产卷：

```sh
FACE_ENV_FILE=/path/to/.env.prod \
FACE_PROJECT_NAME=face-prod-20260810-restore-drill \
./scripts/prod-restore-drill.sh /absolute/path/to/backups/production/<timestamp>
```

## 真实渠道安装门禁

- 支付：安装包必须命名为 `payment-<CHANNEL>.jar`，`payment.properties` 必须记录匹配的 `channel`、90 天内的 `checked-at`、安装包 `adapter.sha256`，且 `sandbox`、`small-payment`、`refund`、`reconciliation` 全部为 `PASS`。
- 短信：内置 HTTP 通道使用 `adapter.sha256=BUILTIN`，`sms.properties` 必须包含 `sandbox=PASS` 与 `delivery-receipt=PASS`；其他通道还必须提供 `sms-<CHANNEL>.jar`。
- 物流：安装包必须命名为 `logistics-<CHANNEL>.jar`，`logistics.properties` 必须包含匹配 SHA-256，以及 `sandbox`、`create-shipment`、`tracking`、`cancel-shipment` 全部为 `PASS`。
- `prod` profile 禁止 `demo` profile、固定验证码 `888888`、`DEMO_MOCK` 和支付 Sandbox Secret。配置缺失时明确不可用，不模拟成功。

安装格式与证据示例见 `prod/adapters/README.md`。切换前再次运行：

```sh
FACE_ENV_FILE=/path/to/.env.prod FACE_PROJECT_NAME=face-production ./scripts/prod-go-no-go.sh
```

## 数据迁移与回滚

- MySQL 数据保存在 `mysql_data` 命名卷中，普通 `down` 不删除该卷。
- 只有隔离的合成测试环境允许使用 `down -v`；生产环境执行破坏性操作前必须完成备份、哈希验证和恢复演练。
- 干净数据库由 Flyway `migrate` 执行受控迁移，再由 `validate` 验证迁移目录与 `flyway_schema_history` 一致；不得写死某个迁移版本，也不得手工修改历史表。
- SC9 已提供 TLS、Docker Secret、对象存储、备份恢复和监控告警机制。生产组织仍需在 SC10 前提供正式域名/CA 证书、外部 Secret 托管、异地加密备份、实际告警接收地址与可信代理边界。

完整 B0-R 可重复验证证据见 `docs/verification/B0/B0-R-verification.md`。
