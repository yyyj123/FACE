# M0 数据库备份与恢复 Runbook

## 1. 适用范围

本 Runbook 用于开发/影子环境的 MySQL 8 备份恢复演练。脚本默认创建独立数据目录、独立端口和独立数据库，不连接现有 `3308` 开发库。

生产或真实门店数据库必须由授权人员提供连接凭据、维护窗口和恢复目标；不得复用本脚本中的临时凭据。

## 2. 备份前检查

1. 记录数据库版本、字符集、时区和 Flyway 最新版本。
2. 确认迁移全部成功且 checksum 无漂移。
3. 记录核心表行数和关键金额/库存汇总。
4. 确认测试数据为虚构数据。
5. 确认目标备份目录位于 `E:\face\backups\m0`。

## 3. 备份策略

- 使用 `mysqldump --single-transaction`，避免非必要全局锁。
- 包含 routines、triggers、events 和十六进制二进制值。
- 备份后计算 SHA-256。
- 备份文件和源码快照写入同一 M0 资产目录。
- 不把数据库密码、手机号、健康信息或支付凭据写入日志。

## 4. 恢复演练

1. 在隔离 MySQL 8 实例创建全新恢复库。
2. 导入完整 dump。
3. 比对源库和恢复库的表数、Flyway 最新版本及核心表精确行数。
4. 比对 M0 虚构标记记录。
5. 将后端连接到恢复库，确认 readiness 为 `UP`。
6. 停止隔离后端与数据库，确认任务端口无残留。

## 5. 回滚

- 迁移失败：停止发布，不修改 checksum，不手工标记 success。
- 恢复失败：保留失败日志和 dump 哈希，重新创建隔离库排查。
- 应用异常：回退应用并关闭新写入口，保留 Expand 结构。
- 数据不一致：停止结算和写入，通过冲正或重新恢复处理，不直接覆盖流水。

## 6. 执行

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File E:\face\scripts\verify-m0-backup-restore.ps1
```

脚本输出包含 `M0_BACKUP_RESTORE=PASS` 才表示开发/影子环境恢复演练通过。

