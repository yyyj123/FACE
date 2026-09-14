# M1-02 V3 安全闭环验收记录

验收日期：2026-07-28  
阶段：M1 安全闭环  
状态：通过

## 1. 需求覆盖

| M1 要求 | 证据 |
|---|---|
| 权限与门店范围绑定 | `TenantAccessService` 同一授权范围 SQL；跨门店回归测试 |
| SELF 过滤 | `AppointmentSelfScopeTest`；SELF 权限只暴露权限码，不进入管理范围 |
| 预约完成收口 | 原 `AppointmentCompletionGuardTest` 保持通过 |
| V3 契约基线 | 统一响应、稳定错误码、request_id、UTC timestamp |
| 可撤销会话 | 哈希令牌、刷新轮换、乐观版本、退出撤销 |
| 数据访问审计 | context/shops 各写一条无敏感正文的审计记录 |
| 数据库 readiness | 必需表增加 `auth_session`，影子库返回 UP |
| V1/V2 兼容 | 未修改旧路由、旧响应和旧 `token` 结构 |

## 2. 测试先行记录

首轮目标测试在实现前运行，因以下符号/行为尚不存在而失败：

- `V3ApiResponse`
- `RequestIdFilter`
- `SessionTokenCodec`
- `V3AuthSessionService/Repository`
- `TenantAccessService.resolveV3`
- readiness 的第 8 张必需表

SELF 权限码测试先失败，证实查询只包含 TENANT/REGION/SHOP；随后只将 `permissionCodes` 扩展为包含 SELF，管理权限 SQL保持不变。

隔离链路第一次加入登录锁定验证时发现 JDBC `DATETIME` 映射差异会让应用忽略已设置的 `locked_until`。先增加仓储映射测试，再改为由 MySQL 计算是否处于锁定期。第二次链路又发现 MySQL `SET` 从左到右赋值造成阈值提前一次，随后调整赋值顺序；最终真实第 5 次失败锁定并返回 429。

## 3. 后端验证

命令：

```powershell
cd E:\face\backend-next
mvn.cmd clean verify
```

结果：

```text
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

产物：

```text
E:\face\backend-next\target\face-chain-platform-0.1.0-SNAPSHOT.jar
```

## 4. 隔离 MySQL 8 与真实接口链路

命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File E:\face\scripts\verify-m1-v3-security.ps1
```

环境：

- 独立临时 MySQL 8
- 数据库端口 3321
- 后端端口 8193
- 虚构技师账号 `jishi01`
- 不访问开发库 `127.0.0.1:3308`

结果：

```text
LATEST=2026072802;SUCCESS=1
AUTH_SESSION_ROWS=1
REVOKED_ROWS=1
HASH_ONLY_ROWS=1
AUDIT_ROWS=2
REGIONAL_MANAGER_ROLES=1
LOGIN_GUARD=5;LOCKED=1
LOGIN_RATE_LIMIT=PASS
LOGIN=PASS
TENANT_CONTEXT_AND_SHOP_SCOPE=PASS
REFRESH_ROTATION=PASS
REFRESH_REPLAY_REJECTED=PASS
OLD_ACCESS_REJECTED=PASS
LOGOUT_REVOCATION=PASS
RAW_TOKEN_PERSISTENCE=0
M1_V3_SECURITY=PASS
```

验证结束后端口 3321/8193 均关闭，临时任务目录数量为 0。

## 5. 前端构建

管理端：

```text
face-chain-admin: vue-tsc -b && vite build
1743 modules transformed
built in 6.92s
exit code 0
```

顾客/技师端：

```text
face-client-v2: vue-tsc -b && vite build
187 modules transformed
built in 1.79s
exit code 0
```

管理端仍有既有 npm `sass_binary_site` 和第三方 `@vueuse/core` PURE 注释警告，不影响构建，本阶段未修改前端依赖。

## 6. 数据与日志自检

- 数据库只保存 64 位 token 哈希，原始 token 持久化计数为 0。
- 验证输出不包含密码或原始 token。
- 审计记录不含手机号、健康信息、支付凭据或响应正文。
- 未修改历史 Flyway 文件和 checksum。
- 当前目录不是 Git 仓库，因此未擅自初始化或伪造小步提交；使用 M0 源码快照机制创建阶段回滚点。

阶段源码回滚点由 `create-stage-source-snapshot.ps1 -Stage M1` 生成，并使用同名 `.sha256` 清单校验。

## 7. 阶段结论

M1 验收门禁全部通过，可以进入 M2。尚未实现的主数据、资源、技能版本、房间设备与排班属于 M2，不在本记录中冒充完成。
