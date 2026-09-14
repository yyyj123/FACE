# WEB-R 图片、演示数据与管理端响应修复验证

## 结论

2026-08-10 已完成并部署以下修复：

- 用户端不再按单一岗位名称过滤技师，当前公开技师会完整显示；桌面端技师区改为原生响应式卡片，避免 WebGL 画廊只呈现少量项目。
- 演示数据脚本会修复既有首页内容、技师头像和商城品牌/图片，不再只在首次插入时生效。
- 管理端的内容图片、护理项目图片、技师头像和商城商品图片均支持直接上传、更换、预览和移除。
- 商城创建接口会同时保存商品封面和 SKU 图片；已有商品可单独更新图片。
- 上传文件保存到 Docker 命名卷 `media_data`，容器重启后仍可读取。
- 管理端登录后的权限上下文与门店列表改为并行加载；积分/商城和护理项目保存后只刷新当前模块，减少无关接口请求。

验证基线 HEAD：`cd8477ac2c6a11383d7ddc9105fad0e1415dd626`。本报告记录的是该基线之上的当前工作区修复；未伪造历史提交。

## 根因

1. 技师接口有完整数据，但首页使用 `jobRole === '美容师'` 精确过滤；新演示技师的岗位为“美容护理师”，因此只剩旧的 2 条记录。
2. `showcase-seed.sql` 对已有内容只执行“缺失时插入”，曾经因终端编码产生的问号不会被后续启动修复；商城 upsert 也未更新品牌字段。
3. 原商城商品创建链路未接收或保存 `cover_url`，管理端只有业务字段，没有上传接口和持久化目录。
4. 多个管理端保存动作调用整页 `reload()`，会重复加载积分、商城、订单等无关数据；权限上下文和门店列表也按顺序请求。

## 自动化结果

| 检查 | 结果 |
| --- | --- |
| 管理端测试 | 5/5 通过 |
| 用户端测试 | 9/9 通过 |
| 后端测试 | 220/220 通过，0 失败、0 错误 |
| 管理端生产构建 | 通过 |
| 用户端生产构建 | 通过 |
| Docker 服务健康 | mysql、backend、admin、client、gateway 全部 healthy |
| 浏览器桌面/手机冒烟 | 通过，严重控制台或网络事件 0 |
| 图片真实文件校验 | JPG/PNG/WebP 魔数校验，最大 5 MB |
| 上传后读取 | 200，`image/webp`，37,554 bytes |
| 容器重启后读取 | 200，`image/webp`，37,554 bytes |
| 商品图片更新接口 | 通过 |
| 公网首页/管理端/用户端/健康检查 | 均为 HTTP 200 |

浏览器结果：`browser-smoke/browser-matrix-run-21.json`。

## 管理端响应时间

本机通过统一网关 `http://127.0.0.1:8290` 连续请求，结果如下（毫秒）：

| 请求 | 3 次结果 |
| --- | --- |
| 管理员登录 | 81 |
| 账号权限上下文 | 42 / 20 / 29 |
| 可访问门店 | 35 / 27 / 29 |
| 积分管理 | 28 / 15 / 14 |
| 商城管理 | 18 / 16 / 29 |
| 商品图片保存 | 42 |
| 图片上传 | 34 |

后端本机请求没有发现秒级阻塞。公网 Quick Tunnel 单次页面/健康请求约 493–520 ms，主要增加来自临时隧道链路；管理端代码已减少保存后的重复请求和登录时的串行等待。

## 部署与重启验证

实际使用以下显式配置完成构建和启动：

```powershell
docker compose --project-name face-sc8-demo --env-file .env.demo `
  -f compose.yaml -f docker-compose.demo.yml build backend admin

docker compose --project-name face-sc8-demo --env-file .env.demo `
  -f compose.yaml -f docker-compose.demo.yml up -d --wait `
  backend admin client gateway
```

重启验证：

```powershell
docker compose --project-name face-sc8-demo --env-file .env.demo `
  -f compose.yaml -f docker-compose.demo.yml restart backend admin client gateway

docker compose --project-name face-sc8-demo --env-file .env.demo `
  -f compose.yaml -f docker-compose.demo.yml up -d --wait --wait-timeout 180 `
  backend admin client gateway
```

当前临时公网地址：`https://identifies-motels-void-titanium.trycloudflare.com`。

## 风险与来源限制

- 当前公网地址是 Cloudflare Quick Tunnel，停止本机 Docker 或隧道容器后会失效，不等同于绑定正式域名的生产部署。
- 图片目前保存于本机 Docker 命名卷，适合本次演示；正式生产应迁移到对象存储、CDN、备份与生命周期管理。
- 上传后取消表单会留下未关联的 UUID 文件，后续生产化可增加定期清理未引用文件的任务。
- 视觉问题来源为用户提供的三张截图；本轮通过数据库、API、桌面/手机浏览器冒烟和生成截图复核，没有将截图中的文字当作数据库原始事实。
- 本轮未涉及微信小程序。
