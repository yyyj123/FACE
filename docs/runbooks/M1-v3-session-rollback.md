# M1 V3 会话发布与回滚 Runbook

## 发布前

1. 确认 M0 恢复演练仍有可用备份和 SHA-256。
2. 执行 `mvn.cmd clean verify`。
3. 在隔离 MySQL 8 执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File E:\face\scripts\verify-m1-v3-security.ps1
```

4. 确认输出包含：

```text
LATEST=2026072802;SUCCESS=1
RAW_TOKEN_PERSISTENCE=0
M1_V3_SECURITY=PASS
```

5. 先部署兼容应用但不开启外部 V3 流量，验证 V2 仍可登录。
6. 灰度放开 V3 登录，观察 401/429、数据库连接、锁等待和会话表增长。

## 异常处置

| 异常 | 动作 |
|---|---|
| Flyway 失败/checksum 漂移 | 停止发布，不手工改 history；从备份重建影子库 |
| V3 登录大量 500 | 关闭 V3 流量并回退应用；V2 保持服务 |
| 刷新冲突异常升高 | 检查重复请求和 `version` 更新，不放宽并发条件 |
| 会话撤销失效 | 立即关闭 V3 登录/刷新，保留表证据并回退应用 |
| 数据库连接/锁等待异常 | 停止切流，检查 Hikari 和 InnoDB；不执行表删除 |

## 回滚

1. 停止新的 V3 登录和刷新请求。
2. 回退到 M1 前应用版本。
3. 不删除 `auth_session`；旧 V1/V2 使用 `token`，不依赖该表。
4. 保留会话表用于审计和故障分析。
5. 只有在备份完成、确认无 V3 实例和无依赖、维护窗口获批后，才可另行评审物理删除。

## 敏感信息

- 不在终端、日志或工单中输出密码及原始访问/刷新令牌。
- 验证脚本只输出计数和 PASS/FAIL。
- 测试账号与数据必须是虚构数据。
