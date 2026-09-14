# SC2 验收证据

本目录保存 SC2 两轮干净状态运行日志、UI 检查日志与最终验收报告。所有运行使用独立 Compose project、全新数据库 volume、合成测试凭据和显式固定端口，不连接生产环境或真实客户数据。

- `SC2-verification.md`：最终结论、范围、版本、命令、结果、风险与来源限制。
- `round-1.log`：第一轮完整官方基础链与 SC2 业务验收输出。
- `round-2.log`：第二轮独立 Compose project 的重复验收输出。
- `ui-check.log`：用户端/运营端静态测试与桌面、移动浏览器布局指标。
