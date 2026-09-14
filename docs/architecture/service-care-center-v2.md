# 到店服务与护理档案中心 V2

## 目标

把“预约已到店”之后的执行过程升级为可用于连锁经营的业务闭环：

1. 已到店预约开始服务；
2. 生成不可重复的实际服务单；
3. 完成服务时填写护理档案；
4. 同一事务内扣减护理耗材；
5. 服务单关联后续结算单；
6. 会员护理历史可按门店权限追溯。

旧系统的 `weixiujilu` 代表已经发生的服务记录，迁入实际服务单和护理档案。
`guzhangpaicha` 是通用护理知识，不混入会员个人档案，只在填写档案时作为参考。

## 核心数据结构

### `service_record`

实际到店服务主单。新增租户、业务编号、旧数据来源、完成幂等键、版本、创建人和更新人。

- `appointment_id`：可空；旧服务记录没有可验证的预约关系。
- `record_no`：租户内唯一。
- `status`：`IN_PROGRESS`、`COMPLETED`、`VOID`。
- `version`：乐观锁版本。
- `completion_idempotency_key`：阻止重复完成与重复扣库。

### `service_record_item`

服务项目快照。保存服务名称、时长和价格，后续项目改名或调价不影响历史。

### `care_record`

会员护理档案。保存肤质、关注点、护理观察、居家建议和下次建议日期。

- 一个服务单最多一条护理档案；
- 使用独立版本号控制并发编辑；
- 已完成但没有档案的历史记录允许后续补录。

### `service_record_consumption`

服务耗材不可变快照，关联：

- 服务单；
- 商品；
- 库位；
- 实际库存流水；
- 领用数量与操作人。

完成服务后只能通过库存调整纠错，不能直接改写原领用记录。

### `sales_order.service_record_id`

结算单通过服务单与实际护理过程关联。预约结算时自动回填对应服务单。

## 状态和事务

```text
CHECKED_IN appointment
        |
        v
IN_SERVICE appointment + IN_PROGRESS service_record
        |
        | complete service (one database transaction)
        v
COMPLETED appointment
COMPLETED service_record
care_record
inventory_movement(SERVICE_USE)
service_record_consumption
```

完成服务事务中的任一环节失败，护理档案、服务状态和耗材扣减都会整体回滚。

## 权限

| 权限 | OWNER | MANAGER | BEAUTICIAN | FRONT_DESK | REGIONAL_MANAGER |
| --- | --- | --- | --- | --- | --- |
| `service_record:view` | 是 | 是 | 是 | 是 | 是 |
| `service_record:manage` | 是 | 是 | 是 | 是 | 否 |
| `care_record:view` | 是 | 是 | 是 | 否 | 否 |
| `care_record:manage` | 是 | 是 | 是 | 否 | 否 |

前台可以推进到店服务状态，但不能读取会员敏感护理内容。

## API

- `GET /api/v2/service-records`
- `GET /api/v2/service-records/resources`
- `GET /api/v2/service-records/{id}`
- `GET /api/v2/service-records/members/{memberId}/history`
- `POST /api/v2/service-records/start`
- `POST /api/v2/service-records/{id}/complete`
- `PUT /api/v2/service-records/{id}/care`

完成服务请求必须携带：

- 服务单版本；
- 完成幂等键；
- 护理档案内容；
- 每条耗材的商品、库位、数量和库存版本。

## 管理端

`/services` 页面提供：

- 服务中、已完成、档案待补统计；
- 已到店预约开始服务；
- 服务记录检索；
- 服务完成与耗材领用；
- 护理档案补录、二次编辑；
- 护理知识引用；
- 会员历史护理时间线。

## 验收

运行：

```powershell
$env:FACE_ADMIN_PASSWORD = '<admin password>'
.\scripts\verify-service-care-center.ps1
```

脚本覆盖：

- 预约到店与开始服务；
- 服务完成幂等；
- 护理档案乐观锁；
- 耗材扣库和库存恢复；
- 会员护理历史；
- 服务单与结算单关联；
- 跨门店访问拒绝。
