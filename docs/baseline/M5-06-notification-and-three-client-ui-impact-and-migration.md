# M5-06 站内通知与三端界面影响及迁移方案

## 1. 阶段与任务

- 阶段：M5-06。
- 输入：V1.2 PRD、数据库设计、API 清单、M5 实施与验收文档、M5-01～M5-05 已验收代码。
- 输出：站内通知投影、本人通知接口、管理端经营协同页、技师工作台、会员售后与通知页。
- 验收：通知去重、本人范围、已读并发控制、外部通道真实状态、三端权限路由、后端测试、前后端构建、迁移与运行态验证全部通过。

## 2. 影响模块

| 类别 | 影响 |
| --- | --- |
| 通知模块 | 新增通知消息、outbox 投影游标、站内送达与已读状态 |
| 审批 | `ApprovalRequested` 通知候选审批人；`ApprovalDecided` 通知申请人 |
| 售后 | 工单创建和状态变化通知经办人、创建人和关联会员账号 |
| 提成/结算 | 提成入账、冲正、调整和结算状态通知关联技师账号 |
| 权限 | 新增 `notification:view:self`；所有查询与写入只允许当前账号本人 |
| API | 新增 `/api/v3/notifications`、单条已读和批量已读接口 |
| 管理端 | 新增 M5 经营协同页，整合提成、结算、售后、审批和通知 |
| 技师端 | 新增本人提成摘要、本人售后、本人审批和通知工作台 |
| 会员端 | 新增本人售后工单、售后详情/进度和通知中心 |

## 3. 状态与幂等

- 站内消息状态：`UNREAD -> READ`，终态读取不得回退。
- 投影状态：`PENDING -> PROJECTED`；失败保留错误摘要和重试次数，不能跳过未成功事件。
- 站内投影通过 `(tenant_id, event_id, recipient_account_id, channel)` 唯一键去重。
- 单条已读通过 `version` 乐观锁控制；重复读取已是 `READ` 时返回当前结果。
- 批量已读以服务端时间水位线和本人账号为边界，不影响水位线后的新消息。
- 外部短信、邮件或微信通道本阶段未配置，响应必须返回 `external_status=UNAVAILABLE`，不得写成已发送。

## 4. 数据库迁移

- 新建 `notification_message`：仅保存安全标题、安全摘要、业务标识、动作路径和本人读取状态。
- 新建 `notification_projection_checkpoint`：记录 outbox 事件投影状态、重试次数和安全错误码。
- 索引：
  - 本人未读列表：`(tenant_id, recipient_account_id, status, created_at, id)`；
  - 门店事件审计：`(tenant_id, shop_id, event_type, created_at)`；
  - 投影轮询：`(status, next_retry_at, id)`。
- 兼容：仅新增表、权限和路由，不修改 M5-01～M5-05 状态机与历史数据。
- 回滚：
  1. 停止通知投影任务；
  2. 下线三端通知入口与 API；
  3. 保留 `notification_message` 和游标表只读以供审计；
  4. 如需物理删除，必须先导出审计快照并在维护窗口单独执行，不在自动回滚脚本中 `DROP` 或 `DELETE`。

## 5. 模块边界

- 业务模块仍只负责写业务状态和 outbox 事件。
- 通知模块消费 outbox，通过显式的事件描述/收件人解析应用服务获得安全摘要和账号标识，不读取健康信息、支付凭据或完整业务正文。
- 三端只能通过 `/api/v3` 应用服务访问，不能直接查询其他模块私有表。

## 6. 测试先行

1. `NotificationPolicyTest`：状态、版本和批量水位线规则。
2. `M5NotificationMigrationContractTest`：表、唯一键、索引、约束、权限、无破坏迁移。
3. `M5NotificationApiContractTest`：列表、单条已读、批量已读和 SELF 契约。
4. `NotificationProjectionContractTest`：去重、失败重试、敏感正文禁入和外部通道不可用。
5. 运行态脚本：审批、售后和提成事件投影；越权不可见；重复投影不增行；并发版本冲突；批量水位线不误读新消息。

## 7. 验证命令

```text
mvn.cmd -q test
mvn.cmd -q -DskipTests package
npm.cmd run build       # admin-next
npm.cmd run build       # front-next
scripts/verify-m1-v3-security.ps1 -VerifyM5Notification
```

浏览器验收覆盖管理端、技师端、会员端的加载、空态、错误态、响应式导航和权限隐藏。

## 8. 文档同步

- 新增数据库迁移说明、M5 API 契约、阶段验收记录。
- M5-07 再将最终契约同步到四份 WPS 商用文档。
