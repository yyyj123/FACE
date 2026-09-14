# M6-09 最终发布验收

## 1. 结论

M0 至 M6-09 已按阶段顺序完成。本地商用发布基线结论为 **Go**；所有代码、迁移、业务、权限、浏览器、容量、安全、依赖、备份恢复和 WPS 文档门禁均有真实通过输出。真实生产仍受生产凭据、TLS、支付/通知通道、监控告警、加密备份和逐门店灰度等外部配置约束。

## 2. 最终质量门禁

- 后端：161 个测试、85 个套件；失败 0、错误 0、跳过 0；`mvn -q clean verify` 和 `mvn -q -DskipTests package` 均通过。
- 管理端：1782 个模块，TypeScript 与 Vite 生产构建通过。
- 技师/会员端：199 个模块，TypeScript 与 Vite 生产构建通过。
- 静态安全：217 个生产源文件；嵌入凭据 0、敏感日志命中 0。
- 供应链：75 个 Maven、88 个管理端 npm、69 个技师/会员端 npm 生产依赖；OSV/npm 漏洞均为 0。
- Jackson：`jackson-bom.version=3.1.5`，`GHSA-5gvw-p9qm-jgwh` 契约测试与 OSV 复核通过。

## 3. 最终隔离业务与容量验收

命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File E:\face\scripts\verify-m1-v3-security.ps1 `
  -DatabasePort 3330 -BackendPort 8202 -VerifyM6Release
```

结果：最新 Flyway `2026080103;SUCCESS=1`；M1 安全、M2 资源、M3 服务、M4 资产资金、M5 激励售后通知、M6 报表营销培训开放平台及数据库契约全部 PASS。浏览器错误 0、失败请求 0、横向溢出 0。

容量门禁：20 并发、500 次混合请求，错误 0；P50=7.81 ms，P95=18.05 ms，P99=22.52 ms，吞吐 2170.47 请求/秒。

## 4. 最终备份恢复

- 备份：`E:\face\backups\m6\m6-09\face-salon-m0-synthetic-20260802-012154.sql`
- SHA-256：`0AD8194069D4FA1F501B9431DB833EA245AC3881D058D5F74707EB3F5921221A`
- 101 张表和关键汇总在源库/恢复库完全一致；合成标记保留；恢复库 readiness=`UP`。
- RPO=0，RTO=68.73 秒。

## 5. 最终 WPS 商用文档

V1.2 原件保留，V1.3 位于 `E:\face\docs\commercial-v1.3`。WPS Office 12.0 实际保存三份 DOCX、导出 PDF，WPS PDF 实际打开 PRD；内容、结构和版本页眉验证通过。

| 文件 | SHA-256 |
|---|---|
| 美容PRD_V1.3.pdf | `0636EE771EF0127E48C02A5C09ED53137F6B9A5ED5BC6D5C20F902700AAD4F4F` |
| 美容数据库V1.3.docx | `6A9AB4D05CB8C9B48482ED23A0BB95432967AD8CD92022EA9BE63BACC6899E71` |
| 美容院店铺管理系统 API接口清单V1.3.docx | `D7B57979D0BD78D435EA7F3C14E50D63DBBC26201884E8873CC19A56B17626EC` |
| 美容院店铺管理系统 分阶段开发实施与验收V1.3.docx | `CD9103E494DF8A962FCC1FE96B5964DF8437975D969E759A9B112E29454B4C80` |

## 6. 交付与回滚

最终源码归档由 `create-stage-source-snapshot.ps1` 生成，旁置 SHA-256 清单为权威校验值。生产异常时先关闭新入口和外部通道、回退应用、保留新增表及不可变历史；不得直接改库或删除审计/流水。生产外部配置未完成前，相关通道继续保持明确不可用。
