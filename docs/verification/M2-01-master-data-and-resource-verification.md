# M2 基础资料与服务资源验收记录

验收日期：2026-07-28  
阶段：M2 基础资料与服务资源  
状态：通过

## 1. 需求覆盖

| M2 要求 | 实现与证据 |
|---|---|
| 项目主数据维护 | V3 项目查询/更新、乐观锁、权限、幂等和审计 |
| 技师技能版本 | `staff_skill_version` 有效期版本；当前版本唯一；双写旧 `staff_service` |
| 排班/休假/锁定 | 员工行锁、重叠检查、乐观版本、停用留痕 |
| 房间与设备 | `service_resource` 独立于库存库位；编码唯一、容量、状态与版本约束 |
| 预约资源冲突 | 稳定锁顺序、`FOR UPDATE`、容量判定、预约与占用同事务 |
| 历史保护 | 技能关闭有效期；资源/排班/占用只更新状态，不删除 |
| 管理端配置 | 独立“基础资料与资源”页面，项目/资源/技能/排班四个标签 |
| 兼容与安全 | V1/V2 保持；V3 Bearer 会话、门店范围、统一响应和稳定错误码 |

## 2. 测试先行记录

以下测试在实现对应能力前先运行并按预期失败：

- `StaffSkillVersionServiceTest`
- `ResourceBookingServiceTest`
- `ServiceResourceServiceTest`
- `StaffScheduleServiceTest`
- `M2MigrationContractTest`
- `CommandIdempotencyServiceTest`
- `ServiceCatalogServiceTest`

封存前又增加资源容量测试：容量为 2 且已有一个重叠占用时应允许第二个
预约。测试先因旧实现按“任何重叠即冲突”失败，随后实现按
`overlap_count >= capacity` 拒绝并通过。

## 3. 后端全量验证

命令：

```powershell
cd E:\face\backend-next
mvn.cmd clean verify
```

结果：

```text
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

产物：

```text
E:\face\backend-next\target\face-chain-platform-0.1.0-SNAPSHOT.jar
```

## 4. 前端生产构建

管理端：

```text
face-chain-admin: vue-tsc -b && vite build
1749 modules transformed
BUILD SUCCESS
```

顾客/技师端：

```text
face-client-v2: vue-tsc -b && vite build
187 modules transformed
BUILD SUCCESS
```

管理端存在既有 npm `sass_binary_site` 和第三方 `@vueuse/core` PURE 注释
警告，不影响构建，本阶段未修改依赖或关闭检查。

## 5. 隔离 MySQL 8 与真实接口

命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File E:\face\scripts\verify-m1-v3-security.ps1 -VerifyM2Ui
```

环境为独立临时 MySQL 8（3321）和后端（8193），不访问开发库。并发创建
两个不同技师、同一时间、同一容量为 1 的房间预约，结果必须恰好一个 200、
一个 409。

结果：

```text
M2_TABLES=3
M2_RESOURCE_ROWS=1
M2_RESERVED_ROWS=1
M2_APPOINTMENT_ROWS=1
M2_IDEMPOTENCY_ROWS=4
M2_AUDIT_ROWS=4
M2_MASTER_DATA_AND_RESOURCE_CONCURRENCY=PASS
LATEST=2026072803;SUCCESS=1
M1_V3_SECURITY=PASS
```

M1 安全回归同时验证登录限流、刷新轮换、重放拒绝、旧访问令牌拒绝、退出
撤销和原始令牌零持久化。

## 6. 管理端运行时与视觉验证

- 桌面：白色主题、导航激活、20 条项目记录、表格与编辑入口正常。
- 390px：编辑器位于结果列表之前；页面主体无未受控横向溢出；表格局部
  横向滚动可用；门店控件可见。
- 控制台页面未出现 M2 数据加载错误。

证据：

- `E:\FACE\.artifacts\预览\M2-基础资料与资源-桌面.png`
- `E:\FACE\.artifacts\预览\M2-基础资料与资源-窄屏.png`

## 7. 数据、权限与日志自检

- M2 新表、索引、外键和 `CHECK` 约束在真实 MySQL 8 成功迁移。
- 项目、资源、技能和排班写接口要求权限与 `Idempotency-Key`。
- 相同幂等键不同负载返回冲突，完成请求可安全重放。
- 审计和验证输出不包含手机号、证件、健康信息、密码、令牌或支付凭据。
- 验证结束后 8081、9341、3321、8193 端口均关闭，M1/M2 临时目录为 0。
- 当前目录不是 Git 仓库，未擅自初始化；使用阶段源码快照封存。

## 8. 阶段结论

M2 门禁全部通过，可以进入 M3。套餐、支付、库存、退款和提成仍按 V1.1
阶段边界留在后续阶段，不在本记录中冒充完成。
