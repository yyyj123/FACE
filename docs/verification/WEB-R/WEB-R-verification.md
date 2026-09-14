# WEB-R 最终验收结论

- 日期：2026-08-10
- 结论：**PASS**
- 版本标签：`web-r/v1.0.0`
- 上一正式基线：`sc9/v1.0.0` / `f9a2a7316192fbda6a73ef801ba86f48080633ed`
- WEB-R 计划：`a17b3cb60a0f726b2311cec970a9f74de19e061a`
- WEB-R 实现与原始证据：`07f23d102be4df42d0c021f310414d2017f2ee46`

## 范围结论

本阶段只稳定化 `front-next` 响应式用户端和 `admin-next` 响应式运营管理平台。没有修改预约、支付、卡项、积分、商城、履约、评价、售后、权限或通知业务规则；没有进入微信小程序；没有恢复已暂停的 SC10 stash；没有删除冻结的技师端、复杂多岗位后台或历史数据结构；公开用户端没有独立技师工作台入口。

用户端完成路由级懒加载、顾客导航收口、跳过导航、页面标题/主内容焦点恢复、安全区底部导航、横屏/短视口适配、共享骨架加载态和减少动态效果。运营端完成页面标题/焦点恢复、移动抽屉语义、Escape/遮罩/路由关闭、背景滚动锁定、焦点归还、移动安全区和窄屏布局收口。

## 两轮正式结果

| 项目 | Run 1 | Run 2 |
|---|---:|---:|
| WEB-R 静态 UI 契约 | PASS | PASS |
| 用户端测试 | 9/9 PASS | 9/9 PASS |
| 运营端测试 | 3/3 PASS | 3/3 PASS |
| 后端全量测试 | 218/218 PASS | 218/218 PASS |
| 用户端生产构建/路由分包 | PASS | PASS |
| 运营端生产构建 | PASS | PASS |
| Flyway migrate/validate/目录与历史动态比对 | 43 / `2026080313` PASS | 43 / `2026080313` PASS |
| MySQL、后端、用户端、运营端、网关健康 | PASS | PASS |
| SC8 核心业务回归 | PASS | PASS |
| 四视口浏览器矩阵 | 48 项 PASS，FACE 严重事件 0 | 48 项 PASS，FACE 严重事件 0 |
| 容器重启后浏览器冒烟 | 4 项 PASS，FACE 严重事件 0 | 4 项 PASS，FACE 严重事件 0 |
| 公网临时地址停止后失效 | PASS | PASS |
| 最终标记 | `WEB_R_REPEATABLE_RUN_1=PASS` | `WEB_R_REPEATABLE_RUN_2=PASS` |

浏览器矩阵覆盖 390×844、768×1024、1440×960、844×390；检查文档级横向溢出、路由标题、主内容焦点、跳过导航、正确导航形态、44px 触控目标、冻结范围入口和运营端抽屉键盘闭环。Run 1 与 Run 2 均从独立 Compose project 和干净 MySQL 数据卷开始；最后一次脚本修正后两轮从 Run 1 重新执行，中间未修改运行代码或验收脚本。

## 证据索引

- Run 1：`run-1/verification.log`、`run-1/browser-matrix-run-1.json`、`run-1/after-restart/browser-matrix-run-11.json`
- Run 2：`run-2/verification.log`、`run-2/browser-matrix-run-2.json`、`run-2/after-restart/browser-matrix-run-12.json`
- 命令：`commands.md`
- 工具版本：`versions.md`
- RED 与迭代记录：`red-tests.md`
- 截图：`screenshots.md`

## 风险与来源限制

- 浏览器自动化在 Windows 11 / Microsoft Edge 151 上执行，覆盖仿真视口，不等同于 iOS Safari、Android WebView 和实体设备专项认证。
- 两次全矩阵各观察到 4 个由企业托管 Edge 扩展 `chrome-extension://pdff...` 产生的消息端口异常；验收器按 URL 来源隔离并计为 `ignoredExtensionEventCount`，FACE 页面异常、本站 5xx 与关键请求失败仍为严重事件且均为 0。重启冒烟未出现该扩展事件。
- Quick Tunnel 只用于 SC8/WEB-R 受控验收，不是生产部署。浏览器矩阵通过同一 Compose 网关的显式本机端口执行，以避免临时隧道抖动；门禁、Secure/HttpOnly Cookie、隧道启动和停止失效仍由 SC8 业务回归验证。
- DEMO 支付、短信、退款和物流为模拟通道，不能替代真实渠道 Sandbox/小额交易验收。
- MySQL 8.4 对历史迁移中的整数显示宽度和 `VALUES()` 写法给出弃用警告；迁移、validate 与历史比对均通过，但后续数据库版本升级前应治理。
- npm 报告用户级 `sass_binary_site` 配置将在下一主版本失效；运营端构建还包含第三方 `@vueuse/core` PURE 注释位置警告，两者均未导致构建失败。
- 正式截图按全局媒体规则保存在仓库外的 `E:\FACE\.artifacts\预览\FACE-WEB-R`，仓库只保存索引与机器可读 JSON。

## 停止条件

WEB-R 已完成并停止。微信小程序与 SC10 继续延期，`stash@{0}` 保持未恢复；等待人工下一步明确指令。
