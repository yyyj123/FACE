# M6-06 营销治理与合规触达验证记录

验证日期：2026-08-01  
阶段：M6 商用运营  
任务：M6-06

## 1. 交付范围

- 会员本人四通道同意水位、明确授权/撤回、服务端文本版本与哈希、不可变历史；
- 活动草稿、乐观修改、提交、异岗审批、取消、执行和不可变状态历史；
- 执行时按当前同意冻结最小受众、outbox、幂等站内通知和真实投递账本；
- 管理端“营销治理”白色主题页面和会员个人中心“营销信息偏好”；
- 短信、邮件、微信明确显示未配置，禁止伪造发送或送达。

## 2. 测试驱动与缺陷闭环

- 先新增状态机、职责分离、通道真实性、默认未同意、版本和迁移契约测试；缺少策略类/迁移时编译按预期失败，随后 7 项定向测试转绿；
- 全量测试 155 项全部通过；
- 隔离验收脚本先后暴露三个仅属于测试工具的问题：选择了错误的历史会员密码、中文写入 HTTP 幂等头、中文标题规范化后测试键碰撞；分别改为绑定最近售后会员、ASCII 规范化、活动序号，业务约束均保持不变；
- UI 专项首次使用不在 CORS 白名单的新端口而回到登录页，改为复用已清理的 8084/8085 验收端口后通过。

## 3. 自动验证结果

| 验证 | 命令 | 结果 |
|---|---|---|
| 后端全量测试 | `mvn.cmd clean verify` | 155 项通过，失败 0、错误 0、跳过 0 |
| 后端打包 | `mvn.cmd -DskipTests package` | 通过 |
| 管理端构建 | `npm.cmd run build` | 通过，含 `MarketingView` 产物 |
| 会员端构建 | `npm.cmd run build` | 通过，含营销偏好设置 |
| UI 规则扫描 | `detect.mjs --json MarketingView.vue AppShell.vue ProfileView.vue` | `[]` |
| 全新 MySQL/API/UI | `verify-m1-v3-security.ps1 -DatabasePort 3322 -BackendPort 8194 -VerifyM6Marketing` | 通过，自动清理 |

核心证据：

```text
M6_MARKETING_RUNTIME=PASS
CAMPAIGN_STATUS=COMPLETED;VERSION=4
AUDIENCE_ROWS=1
DELIVERY_ROWS=1;DELIVERED=1
MARKETING_NOTIFICATIONS=1;EXTERNAL_NOT_REQUESTED=1
CONSENT_HISTORY_ROWS=2
SELF_APPROVAL_ROWS=0
M6_MARKETING_UI=PASS
M6_MARKETING_TABLES=6
M6_MARKETING_UNIQUE_KEYS=6
M6_MARKETING_PERMISSIONS=4
M6_MARKETING_LATEST_FLYWAY=2026080102;SUCCESS=1
M6_MARKETING_DATABASE_CONTRACT=PASS
M1_V3_SECURITY=PASS
```

## 4. 浏览器与界面验收

实际无头 Edge 打开管理端 `/marketing` 和会员端 `/profile`：

- 管理端：标题、异岗审批、执行时冻结受众、外部通道未配置、3 条合成活动、白色主题、无横向溢出均通过；
- 会员端 390×844：默认关闭/撤回说明、4 个通道、3 个不可用通道、可访问 `role=switch`、无横向溢出均通过；
- 浏览器错误 0，网络失败 0；
- 截图：`E:\FACE\.artifacts\预览\M6-营销治理-管理端.png`、`E:\FACE\.artifacts\预览\M6-营销偏好-会员端.png`。

## 5. 自检结论

| 检查 | 结果 |
|---|---|
| 需求覆盖 | 同意/撤回、职责分离、状态机、受众冻结、幂等投递、真实通道状态、双端 UI 均覆盖 |
| 权限 | 管理四权限分离；会员仅本人；跨门店 403；对象查询带租户/门店 |
| 错误处理 | 参数 400；权限 403；对象范围 404；版本/状态/职责/无受众/通道 409 |
| 日志与隐私 | 审计和 outbox 只保存安全状态/标识；无手机号、证件、健康、护理和支付凭据 |
| 模块边界 | 会员候选和通知投递均通过公开端口；模块不直读彼此私有表 |
| 数据库 | expand-only；迁移、索引、约束、兼容、应用回退与物理回退条件齐全 |
| 文档 | M6 API、模块边界、数据库说明、影响方案和验收记录已同步 |

结论：M6-06 已通过代码、测试、构建、全新 MySQL 迁移、隔离 API 和真实浏览器验收，可以进入 M6-07 培训与开放平台。正式四份 WPS 商业文档仍按计划在 M6 总阶段收口时统一同步。
