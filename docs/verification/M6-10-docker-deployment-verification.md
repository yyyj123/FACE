# M6-10 Docker 部署验收记录

## 结论

Docker 单机部署能力已完成并通过真实运行验收。验收使用隔离项目 `face-m6-10-acceptance` 和纯合成密码；结束后容器、网络和测试卷全部删除，不影响现有系统及历史数据。

## 红灯与修复记录

1. 部署契约首次运行因缺少 `compose.yaml` 失败，证明测试能够拦截未实现状态。
2. 首次真实启动发现 Flyway 不能直接从绝对空库执行：历史迁移 `V2026072401` 的契约是基于已验收的单店基础库升级，缺少 `shop` 表时正确失败。
3. 采用不改历史迁移、不改校验和的保守修复：MySQL 新卷首次启动加载 `docs/database/face_salon_mysql8.sql`，健康检查确认 `shop` 已就绪后再启动后端，随后由 Flyway 升级。
4. 第二次启动 4 个容器均已 Healthy，但 Windows PowerShell 将 readiness 正文作为字节数组返回；验收脚本增加 UTF-8 解码后复测。
5. 同时修复失败路径清理：即使 `compose up --wait` 失败，也会删除隔离容器、网络和测试卷。

## 最终命令

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docker-contract.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docker-runtime.ps1
```

## 最终结果

```text
DOCKER_CONTRACT=PASS
DOCKER_IMAGE_BUILD=PASS
DOCKER_MYSQL_HEALTH=PASS
DOCKER_FLYWAY_VERSION=2026080103
DOCKER_BACKEND_READINESS=UP
DOCKER_ADMIN_PROXY_LOGIN=PASS
DOCKER_ADMIN_SPA=PASS
DOCKER_CLIENT_TECHNICIAN_SPA=PASS
DOCKER_COMPOSE_HEALTH=PASS
DOCKER_RUNTIME_ACCEPTANCE=PASS
DOCKER_ACCEPTANCE_CLEANUP=PASS
```

## 交付资产

- `compose.yaml`
- `.env.docker.example`
- `backend-next/Dockerfile`
- `admin-next/Dockerfile` 与 `admin-next/nginx.conf`
- `front-next/Dockerfile` 与 `front-next/nginx.conf`
- `scripts/verify-docker-contract.ps1`
- `scripts/verify-docker-runtime.ps1`
- `docs/operations/docker-deployment.md`

## 上生产前外部配置门

本记录证明本地商用基线可容器化部署，不代表互联网生产环境已完成。生产仍需真实域名与 TLS、密钥托管、支付/通知凭据、加密备份、监控告警、容量规划和灰度回滚演练。

