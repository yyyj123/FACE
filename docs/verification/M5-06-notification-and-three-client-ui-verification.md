# M5-06 站内通知与三端界面验收记录

## 1. 输入、输出与验收标准

输入为四份 V1.2 商业基线、M5 生命周期/API 契约、M5-02 至 M5-05 已验收能力和现有三端白色主题规范。

输出：

- outbox 到站内通知的可重试、幂等投影；
- 本人通知查询、单条版本化已读和水位线批量已读；
- 管理端提成/结算/售后/审批/通知经营协同页；
- 技师端本人提成、审批申请和通知工作台；
- 会员端售后工单和消息中心；
- 数据库迁移、权限、审计、外部通道真实状态和三端响应式验证。

验收标准：不跨接收人泄露；事件重放不重复；并发新消息不被批量已读吞掉；未配置外部通道不得伪造成功；三端无权限越界、无横向溢出、无浏览器异常或失败请求；测试、构建和全新数据库历史链通过。

## 2. 测试驱动证据

实现前新增并确认失败：

```text
NotificationPolicyTest
M5NotificationMigrationContractTest
M5NotificationApiContractTest
NotificationProjectionContractTest
```

失败证明通知状态、SELF、投影幂等、迁移和 API 契约尚未实现；完成实现后定向测试及全量测试均转绿。

## 3. MySQL 8 全历史链与运行时

执行：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass \
  -File E:\face\scripts\verify-m1-v3-security.ps1 \
  -DatabasePort 3407 -BackendPort 8267 \
  -VerifyM5Notification
```

关键证据：

```text
M5_NOTIFICATION_SELF_SCOPE=PASS
M5_NOTIFICATION_OPTIMISTIC_READ=PASS
M5_NOTIFICATION_READ_ALL_WATERMARK=PASS
M5_NOTIFICATION_EXTERNAL_CHANNEL_TRUTH=PASS
M5_NOTIFICATION_PROJECTION_IDEMPOTENCY=PASS
M5_NOTIFICATION_PROJECTED=21
M5_NOTIFICATION_FAILED=0
M5_NOTIFICATION_DUPLICATES=0
M5_NOTIFICATION_TABLES=2
M5_NOTIFICATION_PERMISSIONS=1
M5_NOTIFICATION_CONSTRAINTS=4
M5_NOTIFICATION_THREE_CLIENT_UI=PASS
LATEST=2026073007;SUCCESS=1
M1_V3_SECURITY=PASS
```

命令退出码为 0，隔离数据库、后端端口、临时浏览器配置和临时测试角色已清理。

## 4. 三端白色主题实机验证

验证使用隔离数据库、有效登录会话、Edge Headless 和白名单内独立端口完成：

```text
管理端：6 个经营协同页签，通知 14 条，白色主题，无溢出
技师端：4 项本人提成摘要，仅本人范围，无会员售后导航
会员端：售后表单、敏感信息提示、追加式历史说明，移动端无溢出
消息中心：全部/未读/已读 3 个筛选，安全摘要或空状态正常
BROWSER_ERRORS=0
NETWORK_FAILURES=0
```

截图：

- `E:\FACE\.artifacts\预览\M5-经营协同与通知-管理端.png`
- `E:\FACE\.artifacts\预览\M5-技师提成协同工作台.png`
- `E:\FACE\.artifacts\预览\M5-会员售后服务-移动端.png`
- `E:\FACE\.artifacts\预览\M5-会员消息中心-移动端.png`

## 5. 全量测试与构建

```text
mvn.cmd -q test
TESTS=140;FAILURES=0;ERRORS=0;SKIPPED=0;FILES=74

mvn.cmd -q -DskipTests package
exit code 0

admin-next: npm.cmd run build
1776 modules transformed; built successfully

front-next: npm.cmd run build
199 modules transformed; built successfully
```

## 6. 自检

1. 需求：站内通知、管理端、技师端、会员端和角色导航已覆盖。
2. 权限：所有通知接口强制 `notification:view:self` 和接收人 SELF；管理动作仍按原 M5 权限校验。
3. 状态：已读使用版本；批量已读使用水位线；外部通道状态不伪造。
4. 幂等：投影、单条已读和批量已读均有稳定幂等保护。
5. 错误处理：投影失败保留检查点和错误码；前端展示可恢复错误，不吞异常。
6. 日志与敏感数据：通知只保存安全摘要，不记录客户敏感信息或支付凭据。
7. 文档：API、数据库、影响迁移和验收记录已同步。

## 7. 源码快照

```text
SOURCE_SNAPSHOT=E:\face\backups\m5\m5-06\face-m5-source-20260730-162420.zip
SOURCE_SNAPSHOT_BYTES=95753839
SOURCE_SNAPSHOT_SHA256=97BFFC92AE4CC8FC514998B626B67D011F6BD25AABC97E6C57F83E66F95B79F8
M5_SOURCE_SNAPSHOT=PASS
```

## 8. 结论

M5-06 验收通过。下一阶段为 M5-07：使用 WPS 将 M5 最终实现、迁移、API 和验收事实同步到四份商业 V1.2 文档。
