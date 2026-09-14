# FACE 网页端公网演示部署教程

本文适用于当前 V3.0 范围：响应式 H5/Web 用户端、响应式运营管理平台和统一后端。微信小程序与独立技师端不在本次部署范围。

## 1. 本教程部署出的地址

启动脚本会同时提供：

- 本机入口：`http://127.0.0.1:<FACE_DEMO_PORT>/`
- 临时公网入口：`https://随机名称.trycloudflare.com/`
- 用户端：入口页验证通过后进入 `/client/`
- 运营管理平台：入口页验证通过后进入 `/admin/`

Cloudflare Quick Tunnel 适合 1～3 天的演示和验收，不是长期生产域名。运行它的电脑必须开机，Docker Desktop 必须保持运行；停止服务或重建通道后，公网地址会失效或改变。

## 2. 准备环境

Windows 电脑需要安装并启动：

1. Docker Desktop（使用 Linux 容器）。
2. Git。
3. PowerShell 5.1 或更高版本。
4. 可访问 Docker Hub 和 `trycloudflare.com` 的网络。

在仓库根目录执行：

```powershell
cd E:\face
docker version
docker compose version
```

两个命令都应正常显示版本号。

## 3. 创建演示环境配置

首次部署时复制示例配置：

```powershell
Copy-Item .env.demo.example .env.demo
```

编辑 `.env.demo`，替换所有 `replace-with-...` 示例值。至少应设置：

```dotenv
FACE_DB_PASSWORD=应用数据库强密码
FACE_DB_ROOT_PASSWORD=不同的数据库管理员强密码
FACE_IMAGE_TAG=sc8-demo
FACE_DEMO_PORT=8290
FACE_DB_POOL_MAX=10
FACE_DB_POOL_MIN=2
FACE_DEMO_ACCESS_PASSWORD=不少于12位的网页访问密码
FACE_DEMO_COOKIE_SECRET=不少于32位的随机Cookie签名密钥
FACE_DEMO_ADMIN_PASSWORD=不少于12位的演示账号密码
FACE_PAYMENT_DEMO_MOCK_SECRET=仅演示使用的支付签名密钥
FACE_SC6_FULFILLMENT_SCAN_MS=1000
```

注意：

- `FACE_DEMO_ACCESS_PASSWORD` 是打开公网入口时填写的“演示访问密码”。
- `FACE_DEMO_ADMIN_PASSWORD` 是 `demo-admin` 和演示会员账号使用的登录密码，两者不是同一个概念。
- 每个密码应不同，不要把示例配置或真实密码提交到 Git。
- 端口必须显式填写；脚本不会随机换端口。

## 4. 从干净状态启动并发布公网

确认 `FACE_DEMO_PORT` 没有被其他程序占用后执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-start.ps1
```

脚本会依次完成：

1. 检查宿主机端口。
2. 启动 MySQL。
3. 执行 Flyway `migrate`。
4. 执行 Flyway `validate`。
5. 构建并启动后端、用户端、运营端和统一网关。
6. 等待所有容器健康。
7. 启动 Cloudflare 临时公网通道。

成功时会看到类似输出：

```text
DEMO_PORT_PREFLIGHT=PASS;PORT=8290
DEMO_FLYWAY_MIGRATE_VALIDATE=PASS
DEMO_GATEWAY_HEALTH=PASS
DEMO_QUICK_TUNNEL_URL=https://随机名称.trycloudflare.com
DEMO_START=PASS
```

复制 `DEMO_QUICK_TUNNEL_URL` 后面的完整地址即可分享给体验者。

如果只想本机使用，不发布公网：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-start.ps1 -NoTunnel
```

## 5. 登录和体验

1. 打开脚本输出的公网根地址。
2. 输入 `.env.demo` 中的 `FACE_DEMO_ACCESS_PASSWORD`。
3. 选择“用户端”或“运营管理平台”。

固定演示账号：

- 运营管理平台账号：`demo-admin`
- 运营管理平台密码：`.env.demo` 中的 `FACE_DEMO_ADMIN_PASSWORD`
- 会员手机号：`13900000001`
- 会员账号密码：`.env.demo` 中的 `FACE_DEMO_ADMIN_PASSWORD`
- 演示短信验证码：`888888`（不会发送真实短信）

演示环境使用合成数据、模拟短信和模拟支付，不得录入真实客户资料、支付信息或正式业务密码。

## 6. 验证部署结果

查看容器状态：

```powershell
docker compose --project-name face-sc8-demo --env-file .env.demo `
  -f compose.yaml -f docker-compose.demo.yml --profile tunnel ps
```

所有业务容器应为 `healthy`，`cloudflared` 应为 `Up`。

检查本机网关：

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8290/healthz
```

检查每组演示数据是否都有 5 条：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-demo-showcase.ps1
```

成功时最后一行是：

```text
DEMO_SHOWCASE_VERIFY=PASS;GROUPS=22;ITEMS_PER_GROUP=5
```

该检查覆盖首页内容、会员、项目、员工、排班、管理员、卡项、优惠券、积分任务、商城、预约、评价、售后、通知和导入历史。演示播种使用固定编码，重复启动不会不断增加重复数据，也不会删除商家自己创建的记录。

## 7. 更新代码后重新部署

保留数据库数据的更新方式：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-stop.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-start.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-demo-showcase.ps1
```

普通 `down` 不会删除 MySQL 数据卷。不要随意执行 `down -v`，因为 `-v` 会删除数据库卷。

## 8. 停止公网演示

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-stop.ps1
```

脚本会停止容器，并检查临时公网地址已经不可访问。数据库卷仍保留，后续可以重新启动。

## 9. 常见问题

### 端口已被占用

错误信息包含 `Requested FACE_DEMO_PORT ... is already occupied` 时，先停止占用该端口的程序，或在 `.env.demo` 中显式改成另一个固定端口，例如 `8291`，然后重试。脚本不会自动随机选择端口。

### 演示访问密码不正确

入口页要填写 `FACE_DEMO_ACCESS_PASSWORD`，不是 `FACE_DEMO_ADMIN_PASSWORD`。修改 `.env.demo` 后必须重建后端容器或重新执行停止、启动流程，旧容器不会自动读取新值。

### 没有输出公网地址

查看通道日志：

```powershell
docker compose --project-name face-sc8-demo --env-file .env.demo `
  -f compose.yaml -f docker-compose.demo.yml --profile tunnel logs cloudflared
```

确认网络没有拦截 Cloudflare，并重启演示。不要手工编造 `trycloudflare.com` 地址。

### 页面仍是旧版本

执行完整停止、启动流程以重新构建镜像，然后在浏览器使用 `Ctrl+F5` 强制刷新。

## 10. 长期正式域名部署

长期上线不要使用 Quick Tunnel，应准备 Linux 服务器、正式域名、DNS、CA 证书、外部 Secret 托管、异地加密备份和监控告警。仓库的生产编排入口是：

```sh
FACE_ENV_FILE=/absolute/path/.env.prod \
FACE_PROJECT_NAME=face-production \
./scripts/prod-start.sh
```

生产环境必须保持 `payment=disabled`、`SMS_MODE=disabled`、`logistics=disabled`，直到真实渠道适配包和验收证据齐全；生产环境不得启用 `demo` profile、固定验证码或模拟支付。正式部署前还应完成域名和证书配置、数据库及对象存储备份、恢复演练和 Go/No-Go 检查。
