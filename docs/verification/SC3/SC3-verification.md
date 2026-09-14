# SC3 最终验收报告

阶段：SC3（技师排班、预约规则、时间锁与候补）  
版本：SC3 1.0.0  
结论：通过  
状态：立即停止，等待人工回复“通过，进入 SC4”

## 1. 基线与可追溯性

| 项目 | 值 |
| --- | --- |
| 产品基线 | `FACE_用户端与运营管理平台增量升级PRD_V3.0.md`，方案一 |
| 首次可追溯快照 SHA | `ff635972a7d4feb4d5192b2f385ebd3703f36326` |
| SC3 起始基线 tag | `sc2/v1.0.0` |
| SC3 起始基线 commit SHA | `2410f0691ff78b0d70af511554d68552acf7a2aa` |
| SC3 已验证实现 SHA | `e8091c6b7d62eadfb3472620c96488ebc90a91df` |
| SC3 发布 tag | `sc3/v1.0.0`（本报告提交后创建） |
| 后端 artifact | `face-chain-platform:0.1.0-SNAPSHOT` |
| 用户端 package | `face-client-v2@0.2.0` |
| 运营端 package | `face-chain-admin@0.1.0` |

首次快照只代表收到代码后在 2026-08-03 建立的当前首次可追溯状态，不是原始开发历史。SC3 从人工批准的 SC2 tag 增量实施；没有重写既有 Flyway 文件、伪造历史提交或删除冻结兼容资产。

## 2. 实际完成范围

1. 项目级预约规则支持 15/20/30/60 分钟开始间隔、服务前后缓冲、最短提前量、当日预约、取消/改期截止、最多改期次数、临时取消策略、预约须知及条款版本。
2. 技师排班支持周固定工作/休息/停约规则和日期级工作/休息/请假/占用/停约事实；日期事实优先于周期规则。
3. 可预约时段仅由后端生成，每次严格返回连续 7 天，最远边界为未来 30 天；用户端不再在浏览器内推算时段。
4. 同时支持指定技师与不指定技师。不指定时按完整占用分钟低负载优先，并以持久游标处理同负载轮询。
5. 时间锁固定保留 15 分钟，覆盖服务时长和前后缓冲形成的完整占用区间；确认时保存规则快照、条款版本和确认时间。
6. 候补按先后顺序每个空位只匹配一位，保留 15 分钟；匹配不自动创建预约，会员重新确认条款后才转为预约；超时会完整释放并继续下一位。
7. 运营平台提供项目规则、周期/日期排班、代客预约和候补空位匹配，仍只面向 `ADMIN`/`SUPER_ADMIN`，未恢复历史复杂岗位菜单。
8. 用户端提供 7 天分页、指定/系统安排、锁倒计时、条款确认和候补状态；两端均完成 1440px 与 390px 响应式验证。
9. 独立技师端、旧多角色后台、采购、提成和培训代码/表均作为兼容资产保留，没有作为 SC3 新入口或新增交付。

运营入口最终采用独立的 `BookingOperationsView.vue`，没有重新激活历史复杂预约页面；这是为保持 SC3 单一运营角色和范围收口，不改变计划中的业务能力。

## 3. 数据库迁移

SC3 只追加：

- `V2026080303__sc3_scheduling_locks_waitlist.sql`：项目预约规则、周期排班、时间锁、预约完整占用/条款快照、候补/历史/游标以及两类管理员权限。

两轮均显式执行 Flyway `migrate` 与 `validate`，并以迁移目录和 `flyway_schema_history` 动态逐版本对账：

| 检查 | 第一轮 | 第二轮 |
| --- | --- | --- |
| 版本化迁移数 | 33 | 33 |
| 最新版本 | `2026080303` | `2026080303` |
| `flyway:validate` 迁移总数（含 callback） | 34 | 34 |
| 失败历史记录 | 0 | 0 |
| 目录/历史差异 | 0 | 0 |
| `migrate` / `validate` | PASS | PASS |

验收脚本没有写死版本号作为通过条件；`LATEST=2026080303` 是本次从迁移目录与历史表动态计算并输出的实际结果。

## 4. 关键业务与并发验收

| 场景 | 两轮结果 |
| --- | --- |
| 服务端 7 天时段与未来 30 天边界 | PASS |
| 同技师同完整占用区间并发抢锁 | 恰好一个 HTTP 200、一个 HTTP 409 |
| 指定技师和系统安排均复用完整冲突校验 | PASS |
| `PAYMENT_TIMEOUT` 释放完整占用 | `RELEASED:PAYMENT_TIMEOUT` |
| 两位候补按 FIFO 加入，同一空位只匹配一位 | PASS |
| 候补匹配不自动创建预约 | PASS |
| 第一位超时，锁与候补均过期并推进下一位 | `EXPIRED:EXPIRED`，PASS |
| 第二位重新确认条款后创建预约 | `CONFIRMED:CONVERTED:PENDING` |
| 预约保留规则、条款和完整占用快照 | PASS |

并发实测最初暴露出“两个请求在空表上均通过查询”的竞争窗口。最终实现按租户/门店/技师使用 MySQL 命名互斥锁串行化临界区，并在互斥区内以 `FOR UPDATE` 重新读取当前预约与有效锁；最终连续验收只允许一个请求成功。

## 5. 自动化、构建与容器结果

| 检查 | 第一轮 | 第二轮 |
| --- | --- | --- |
| 显式端口预检 | PASS：9290/9281/9282/9283 | PASS：9390/9381/9382/9383 |
| Docker/B0 静态契约 | PASS | PASS |
| 后端全量测试 | 192/192 PASS | 192/192 PASS |
| 用户端生产构建 | PASS | PASS |
| 运营端生产构建 | PASS | PASS |
| Flyway migrate/validate/动态对账 | PASS | PASS |
| 五容器初始健康、API 与 SPA | PASS | PASS |
| 五容器重启复检 | PASS | PASS |
| SC3 业务与并发矩阵 | PASS | PASS |
| 独立数据库 volume 清理 | PASS | PASS |
| 官方脚本退出码 | 0 | 0 |

正式命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc3-runtime.ps1 `
  -ProjectName face-sc3-evidence1b `
  -BackendPort 9290 -AdminPort 9281 -ClientPort 9282 -TechnicianPort 9283

powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc3-runtime.ps1 `
  -ProjectName face-sc3-evidence2 `
  -BackendPort 9390 -AdminPort 9381 -ClientPort 9382 -TechnicianPort 9383
```

脚本内部调用 B0-R 官方 `verify-docker-runtime.ps1 -KeepRunning`，完成端口预检、静态契约、后端全量测试、双前端构建、干净库迁移、Flyway 校验、迁移目录/历史对账、镜像构建、健康检查和重启复检；随后运行 SC3 合成矩阵，默认在 `finally` 清理容器、网络和 volume。`-KeepRunning` 仅供明确的 UI 诊断使用，默认不会保留运行环境。

## 6. UI 与响应式证据

运行 `node .\scripts\sc3-ui-check.mjs --client=http://127.0.0.1:8882 --admin=http://127.0.0.1:8881 --debugPort=9337` 后：

| 页面 | 1440px | 390px | 横向溢出 | 运行时错误 |
| --- | --- | --- | --- | --- |
| 用户预约 | PASS | PASS（`innerWidth=scrollWidth=390`） | 无 | 0 |
| 运营排班 | PASS | PASS（`innerWidth=scrollWidth=390`） | 无 | 0 |

视觉检查曾发现候补完整时间范围撑宽移动布局，以及排班日期直接显示 UTC ISO 导致东八区前移一天。最终分别通过可收缩/任意断行和 `Asia/Shanghai` 日期格式化修复；重建后再次通过四视口检查。

截图：

- `E:\FACE\.artifacts\预览\SC3-用户预约-桌面.png`
- `E:\FACE\.artifacts\预览\SC3-用户预约-移动.png`
- `E:\FACE\.artifacts\预览\SC3-运营排班-桌面.png`
- `E:\FACE\.artifacts\预览\SC3-运营排班-移动.png`

界面延续白色/浅灰表面和玫瑰铜强调色，保持紧凑运营信息密度；没有引入无关重设计。该选择受 `impeccable` UI 约束影响。

## 7. 证据完整性

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `round-1.log` | 65906 | `567449996EEC31F75477F2C94B99B5EC20A700251844630DEFA16F8BA6229C2A` |
| `round-2.log` | 65917 | `EDA659A646ECD74FA18BA76E686AFD712AE45DEE52661371672E9458776DBD55` |
| `ui-check.log` | 1518 | `B599D9B5B18858ADCC69101459046BEA83AD7414B04858530C66A4F2DC2F92CB` |

日志只包含构建、测试、迁移、容器状态和显式合成数据，不含生产密钥、真实验证码或真实客户资料。

## 8. 工具与组件版本

| 组件 | 版本 |
| --- | --- |
| Windows | 10.0.26200 |
| Windows PowerShell | 5.1.26100.7462 |
| Git | 2.45.1.windows.1 |
| Docker Engine | 29.6.1 |
| Docker Compose | v5.1.4 |
| MySQL 镜像 | 8.4 |
| Maven | 3.9.16 |
| Java | Eclipse Adoptium 21.0.11 |
| Flyway | 12.4.0 |
| Node.js | v24.18.0 |
| npm | 11.16.0 |

## 9. 失败测试与修复记录

1. 首版验收脚本假定存在两条可复用历史会员；改为显式建立两个合成会员，不依赖历史数据顺序。
2. Java `LocalDateTime` JSON 可能省略 `:00` 秒；验收统一按合法时间语义比较，不再误把等价格式判为失败。
3. 可选布尔字段最初使用原始 `boolean`，请求省略时被 Jackson 3 拒绝；改为可空包装类型并显式归一化为 `false`。
4. 首次真实并发验收发现空表查询无法仅靠行锁阻止双成功；增加按技师的 MySQL 互斥临界区和当前占用复读后转绿。
5. UI 检查发现候补时间文本撑宽 390px 页面，以及排班日期 UTC 展示偏移；修复后重新构建、截图，并以最终代码重新完成两轮正式验收。
6. 首次用 `Tee-Object` 采集日志时，外层 Windows PowerShell 把 Maven/JDK 的预期 stderr 警告包装为 `NativeCommandError`，虽然日志中所有终态标记均已 PASS。为消除退出码歧义，随后使用保留子进程退出码的方式重采本报告中的两份正式日志，两轮均为 0。

## 10. 风险、来源限制与回滚

1. 本次只在 Windows 11、Docker Desktop、MySQL 8.4 和合成数据上验证；未连接生产数据库、正式域名、真实客户资料或第三方渠道。
2. V3.0 PRD 是唯一产品范围来源；V2.1 及更早规格、历史报告和截图只作来源记录，没有据此恢复总部端、分店端、独立技师端或复杂多岗位后台。
3. 命名互斥锁依赖当前已选定的 MySQL 后端；若未来更换数据库，需要以等价的数据库级 advisory lock 或唯一占用模型替换并重新做真实并发验收。
4. 时间锁和候补超时目前在相关查询/处理请求中释放；生产规模下的主动定时扫描、通知投递和多实例可观测告警应在相应后续阶段单独验收。
5. 运营端上海时区展示与当前单店中国业务一致；未来启用多时区门店时应改为读取门店时区而非固定展示时区。
6. Maven 保留 Mockito 动态 agent 的未来 JDK 兼容警告；npm 保留 `sass_binary_site` 未来弃用及第三方 Rollup 注释位置警告，当前测试和构建均成功。
7. SC3 代码可按提交逆序使用普通 `git revert` 回退；已执行的 Flyway 迁移不得修改或删除，只能通过新的补偿迁移回滚结构/数据影响。

## 11. 范围门禁

SC3 没有创建订单、接入支付渠道、实现优惠决策、卡项购买或权益冻结扩展；候补确认只创建 `PENDING` 预约。没有删除或公开历史技师端及其他冻结模块。

最终结论：SC3 验收通过。立即停止，等待人工回复“通过，进入 SC4”。
