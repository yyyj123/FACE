# M2-01 主数据与资源差异审计及影响冻结

状态：实施前冻结  
基线：商业文档 V1.1（2026-07-26）  
前置：M0、M1 已验收  
任务编号：M2-01

## 1. 当前阶段检查表

1. 当前阶段/任务：M2 / M2-01。
2. 输入：四份 V1.1 基线文档、M1 验收产物、当前代码和 Flyway。
3. 输出：M2 差异矩阵、影响模块/表/接口/权限/状态、迁移与回滚方案。
4. 历史数据：需要从 `staff_service` 受控回填首个技能版本；旧预约和项目快照不得改变。
5. 先新增测试：资源停用历史、技能版本生效区间、排班覆盖、资源冲突、技师并发双订。
6. 验证：Maven、两端构建、MySQL 8 Flyway、并发接口、管理端真实页面。
7. 文档：M2 API、数据库迁移、运行手册与验收记录。

## 2. V1.1 要求

M2 主要交付：

- 组织、员工、项目和任职主数据。
- 技师技能版本。
- 房间/设备资源。
- 排班。
- 预约冲突。
- 管理端配置能力。

M2 受控新增表：

- `service_resource`
- `resource_booking`
- `staff_skill_version`

验收：

- 同一技师或同一独占资源的并发双订最多一个成功。
- 资源停用不删除或破坏历史预约/资源占用快照。
- 技能历史可按业务发生时点复现。
- 后端测试、两端构建、Flyway、真实 MySQL 8 和管理端页面全部通过。

## 3. 当前能力与差异

| 能力 | 当前状态 | 结论 |
|---|---|---|
| tenant/region/shop | 表和授权范围已存在 | 复用；缺少完整管理配置入口 |
| staff | 主表存在 | 复用 |
| staff_shop_assignment | 有生效起止和状态 | 复用 |
| service_category/service_item | 主表存在，预约项已有快照 | 复用；缺少管理 CRUD |
| shop_service_price | 有生效期版本 | 复用；缺少管理入口 |
| staff_service | 仅当前 enabled/custom_duration | 不满足历史版本复现 |
| staff_skill_version | 不存在 | M2 新增并从 staff_service 回填首版 |
| staff_schedule | WORK/LEAVE/BLOCKED 已存在 | 冲突校验已消费；缺少受控管理入口和版本 |
| service_resource | 不存在 | M2 新增房间/设备 |
| resource_booking | 不存在 | M2 新增占用历史 |
| 技师预约冲突 | 事务内锁定 staff 行后检查重叠 | 已具备正确方向；缺少真实并发验收 |
| 房间/设备冲突 | 不存在 | M2 新增 |
| 资源停用历史保护 | 不存在 | M2 新增状态变更，不物理删除 |
| V3 services/staff | API 清单要求适配，尚未实现 | M2 新增适配层 |
| 管理端配置 | 当前“服务”页面是护理执行，不是主数据配置 | M2 新增独立配置任务区 |

## 4. 影响模块

| 模块 | 责任 | 边界 |
|---|---|---|
| masterdata | 项目、技师、任职、技能版本查询与命令 | 只写所属主数据表 |
| resource | 房间/设备、启停、资源占用 | 只写 service_resource/resource_booking |
| schedule | 排班命令和重叠校验 | 写 staff_schedule，通过应用服务协作 |
| appointment | 创建/改期时协调技师与资源锁 | 不直接维护资源主数据 |
| v3 | services/staff 与管理配置契约 | 复用应用服务，不直接拼跨域写 SQL |
| admin-next | 主数据与资源配置页面 | 与护理执行页面分离 |

## 5. 表、索引与状态设计

### service_resource

- 类型：`ROOM`、`EQUIPMENT`。
- 状态：`ACTIVE`、`INACTIVE`。
- 版本：乐观锁 `version`。
- 唯一：`tenant_id + shop_id + resource_code`。
- 查询索引：`tenant_id + shop_id + resource_type + status`。
- 停用只改变状态；历史 booking 保留。

### resource_booking

- 状态：`RESERVED`、`RELEASED`、`CANCELLED`。
- 保存 appointment、resource、起止时间和创建/释放证据。
- 查询索引：`tenant_id + resource_id + status + start_at + end_at`。
- 同一资源并发预订：先 `SELECT service_resource ... FOR UPDATE`，再检查重叠并插入。
- 取消/改期通过状态释放并新增/更新受控记录，不删除历史。

### staff_skill_version

- 保存 staff、service、是否可服务、自定义时长、生效起止、版本和创建人。
- 唯一：`tenant_id + staff_id + service_id + effective_from`。
- 当前版本查询：`effective_from <= now` 且 `effective_to > now/null`。
- 新版本在同一事务关闭旧版本再插入；不覆盖历史行。
- 从 `staff_service` 回填一条首版，仅使用现有虚构/业务主数据，不改预约历史快照。

### staff_schedule

- 保留现表，不在历史迁移上改 checksum。
- M2 评审新增 `version`、更新人和更新时间的 Expand 迁移。
- 同一技师同日排班重叠由事务锁和 SQL 校验拒绝。

## 6. 接口与权限影响

V1.1 已列出：

- `GET /api/v3/services`：门店/公开字段。
- `GET /api/v3/staff`：门店技师与当前技能。

管理配置需补充受控契约（将在 API 文档中明确）：

- 项目/门店价格查询与版本更新。
- 技师任职与技能版本查询/更新。
- 房间/设备查询、新增、版本更新和停用。
- 排班查询、新增、版本更新和停用。

权限建议复用现有权限点：

- 项目：`service:view`、`service:manage`。
- 员工/技能/排班：`staff:view`、`staff:manage`。
- 资源：新增 `resource:view`、`resource:manage`，由 Flyway 种子受控授予 OWNER/MANAGER。
- 所有命令同时校验权限点与同一门店范围。

## 7. 兼容与迁移

- Expand-only；不删、不重命名、不收窄现有列。
- V1/V2 继续读 `staff_service`；M2 新路径读 `staff_skill_version`。
- 过渡期技能命令双写由一个应用服务负责；稳定后再单独评审旧表 Contract。
- 预约和服务记录继续使用已有快照，资源/技能停用不回写历史。
- 真实库迁移前必须先在 M0 备份恢复出的影子库执行。

## 8. 回滚

1. 关闭 M2 功能开关或回退应用，V1/V2 继续使用现有主数据。
2. 停止新技能版本、资源和排班写入。
3. 保留新增表、索引和历史占用记录，不即时 DROP。
4. 若双写异常，以现有 `staff_service` 为旧链路读源，不自动覆盖新表历史。
5. 物理删除只允许独立维护窗口、备份、无依赖确认和审批后执行。

## 9. 冲突与保守决策

- 文档要求 M2 有技能版本，但当前只有 `staff_service` 当前态：新增版本表，禁止直接把旧表改造成历史表。
- 文档要求房间/设备，但当前无任何物理模型：新增独立资源领域，禁止借用库存地点 `ROOM` 冒充服务房间。
- 管理端“服务”现为护理执行工作区：新增独立配置页面，禁止混入同一页面造成职责混乱。
- 预约已有 staff 行锁：保留并增加真实并发测试，不用单纯“先查再插”替代。
