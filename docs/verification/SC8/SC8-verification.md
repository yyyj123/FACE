# SC8 双端 DEMO 与客户验收报告

## 最终结论

**PASS。** SC8 已按 V3.0 方案一完成，最终代码连续两轮从干净数据卷通过正式验收。两轮均验证了动态数据库迁移、后端测试、双前端构建、容器健康、受控公网访问、桌面/移动端渲染、固定账号、模拟短信/支付、SC4-SC6 全业务链路、备份、重置恢复和停机失效。

所有验收用 Quick Tunnel 均已停止；日志中的 `trycloudflare.com` 地址是已失效的历史证据，不是当前可访问地址。当前停止在 SC8，不进入 SC9。

## 范围与结果

| 验收项 | 结果 | 证据摘要 |
|---|---|---|
| 受控入口 | PASS | 自定义 FACE 门禁；12 小时 `HttpOnly; Secure; SameSite=Lax` 签名 Cookie；未授权双端相对跳转，不暴露容器端口 |
| 可见产品 | PASS | 仅 `/client/`、`/admin/`、`/api/`；`/technician/` 明确返回 404 |
| 固定演示身份 | PASS | `admin`、`demo-admin`、`13900000001` 使用环境注入的统一演示密码 |
| 模拟渠道 | PASS | 短信固定码 `888888`；支付、退款、通知均使用 DEMO 模式，无真实外发或真实资金动作 |
| 完整业务链路 | PASS | 注册、预约、卡项、积分、商城、发货、评价、售后；复用 SC4、SC5、SC6 正式运行检查 |
| 数据库 | PASS | Flyway `migrate`、`validate`、迁移目录与 `flyway_schema_history` 动态比对一致：43 条，最新 `2026080313` |
| 响应式验收 | PASS | Edge Headless，1440×900 桌面与 390×844 移动视口；用户端、运营端均渲染且无横向溢出 |
| 运维脚本 | PASS | 显式端口和占用预检；启动、停止、备份、重置均完成；未随机选择端口 |
| 重置与停机 | PASS | 两轮固定种子指纹均为 `40000000`；重置后恢复一致；旧 URL 和最终 URL 均失效 |

## 可追溯基线

- 上一阶段基线 commit：`47fdec75e8ebdfcb9092a6b762c18aa4739c86ff`，tag：`sc7/v1.0.0`。
- SC8 计划 commit：`02e31547e01cfb84ebb50c2160a48f5e334c7e99`。
- SC8 实现候选 commit：`5ae2e02e49ffec48011cd46b5e48b72d3b84eb40`。
- SC8 最终证据基线：tag `sc8/v1.0.0`；可用 `git rev-list -n 1 sc8/v1.0.0` 解析最终证据 commit。
- 首次可追溯标签仍仅表示收到代码后的首次快照，不代表或伪造原始开发历史。

## 环境与版本

| 组件 | 版本/标识 |
|---|---|
| Windows | Windows 11 10.0 amd64 |
| Git | 2.45.1.windows.1 |
| Docker Engine | 29.6.1 |
| Docker Compose | v5.1.4 |
| Java | Eclipse Temurin 21.0.11 LTS |
| Maven | 3.9.16 |
| Node.js | v24.18.0 |
| npm | 11.16.0 |
| MySQL | 8.4.11 |
| Flyway Maven plugin | 12.4.0 |
| cloudflared | 2026.7.3 |
| cloudflared image | `cloudflare/cloudflared@sha256:e39ee8da81ad5e05d77f38d2f51c60ca51bf2a8450ac3abab50c17fdb91d91bf` |

## 正式命令与连续两轮结果

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc8-runtime.ps1 -Run 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc8-runtime.ps1 -Run 2
```

两轮均从独立 Compose project 和干净 MySQL 数据卷开始，执行顺序一致：

1. SC8 静态契约；
2. Maven 全量后端测试；
3. `admin-next`、`front-next` 生产构建；
4. Flyway `migrate`、`validate` 及目录/历史动态比对；
5. 容器健康、Quick Tunnel 和门禁；
6. 固定账号、固定短信码、SC4-SC6 全业务链路；
7. 公网桌面/移动浏览器检查；
8. `mysqldump` 备份；
9. 销毁数据卷并重置，比较固定种子指纹；
10. 重置后公网复验，停止并确认公网立即失效。

| 结果 | Run 1 | Run 2 |
|---|---:|---:|
| 后端测试 | 214/214，0 failure，0 error | 214/214，0 failure，0 error |
| Flyway 动态历史 | 43/43，PASS | 43/43，PASS |
| 固定种子指纹 | `40000000` | `40000000` |
| 备份 | 504,138 bytes | 504,137 bytes |
| 重置恢复 | PASS | PASS |
| 公网双视口 | PASS | PASS |
| 停机失效 | PASS | PASS |
| 最终标记 | `SC8_REPEATABLE_RUN_1=PASS` | `SC8_REPEATABLE_RUN_2=PASS` |

完整日志：

- [Run 1 日志](./run-1.log)，SHA-256 `9574418FD29E53A967FBDD7C24EA53068EBE30C3FAAB506470EDC7638F7D268F`
- [Run 2 日志](./run-2.log)，SHA-256 `2661D475A4275C3EA39E2A72345F346A14C5B36C5DDF2A4C2681052521ED36D8`

两份日志对六个验收密钥的逐值扫描结果均为 0；测试环境文件只包含明确标注的合成、一次性验收值，真实 Secret 未进入日志。

## 浏览器证据

Run 2 最终代码截图：

- [桌面用户端](./run-2/desktop-client.png)，SHA-256 `3215BF78A9A4B5F0C1354BDE50653D1C8DBA29637202C068C39E830C06965930`
- [桌面运营端](./run-2/desktop-admin.png)，SHA-256 `29DF87A6FC0BCA95DDAE46DEB8B468465E685D8D3811530D9CA74A84057A356E`
- [移动用户端](./run-2/mobile-client.png)，SHA-256 `B64BB8C257F99B96CE5EE24CD5823F151AEEE02671DF4AA78A2606367E9F952B`
- [移动运营端](./run-2/mobile-admin.png)，SHA-256 `B0A70B0F17790413EA1EE83F63EA42B57402EFB64CB31CB9F8C757BCE2A2CDEF`
- [Run 2 浏览器结构化结果](./run-2/browser-results.json)
- [Run 2 重置后浏览器结构化结果](./run-2/after-reset/browser-results.json)

截图已经人工目检：桌面与移动布局清晰，无横向溢出；公开页面没有独立技师端入口。

## 来源与限制

- Cloudflare 官方将 Quick Tunnel 定位为测试用途，启动后生成随机 `trycloudflare.com` 子域；本阶段据此只用于短期演示，不视为稳定域名或生产入口：[Quick Tunnels](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/)。
- `cloudflared` 使用 `latest` 拉取，但本次实际验收版本和镜像 digest 已在上表固定留证。后续若重跑拉取到新 digest，必须重新验收，不能沿用本报告结论。
- Quick Tunnel 依赖外部网络和 Cloudflare 服务，不提供本项目控制范围内的可用性保证；本阶段不覆盖 TLS 自定义域、生产 Secret、监控告警或 Go/No-Go，这些属于 SC9。
- 浏览器证据来自本机 Edge Headless 的桌面视口和移动仿真，不等同于全部真实机型测试。
- 演示数据、账号和渠道均为合成或模拟；未接入真实会员数据、真实短信、真实支付、真实退款或真实物流。
- MySQL 8.4 对部分历史迁移中的 `VALUES()` 和整数显示宽度给出弃用警告；迁移与校验均成功，但应在未来迁移维护中逐步消除。
- 访问门禁为短期演示保护，不替代生产级身份认证、WAF、限流或审计。SC9 前不得把当前 Quick Tunnel 配置当作生产部署。

## 阶段门禁

SC8 完成后立即停止。必须等待人工明确回复 **“通过，进入 SC9”** 后，才允许开展 Linux Compose、TLS、Secret 隔离、对象存储、备份恢复、监控告警、真实渠道适配器安装机制或 Go/No-Go 工作。
