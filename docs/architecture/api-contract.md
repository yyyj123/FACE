# FACE 美容院系统 API 契约（MVP）

## 约定

- 服务地址：`http://localhost:8080/face`
- API 基础路径：`/api/v1`
- 鉴权：请求头 `Token: <token>`；公开目录接口除外。
- 时间：请求使用 ISO 8601 本地时间，例如 `2026-07-23T10:00:00`；数据库按门店时区保存。
- 成功响应：`{ "code": 0, "msg": "success", "data": ... }`
- 失败响应：`{ "code": 400|401|403|409, "msg": "面向用户的说明" }`

## 会员与员工

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/management/members` | 会员检索与分页 |
| POST | `/management/members` | 前台建立会员档案 |
| GET | `/members/{id}` | 会员详情与消费摘要 |
| PATCH | `/members/{id}` | 更新会员资料 |
| GET | `/staff` | 美容师/员工列表 |
| GET | `/staff/{id}/services` | 可服务项目 |
| GET | `/availability?staffId=&date=YYYY-MM-DD` | 排班和已占用时段 |
| POST | `/management/schedules` | 设置班次或请假 |

## 项目与预约

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/service-categories` | 项目分类 |
| GET | `/services` | 项目列表，支持分类和推荐筛选 |
| GET | `/services/{id}` | 项目详情 |
| POST | `/appointments` | 创建预约并执行冲突校验 |
| GET | `/appointments` | 按日期、员工、会员、状态查询 |
| GET | `/appointments/{id}` | 预约详情 |
| POST | `/appointments/{id}/status` | 按状态机确认、签到、开始、完成或取消 |

创建预约请求：

```json
{
	"shopId": 1,
  "memberId": 1001,
  "staffId": 2001,
  "serviceIds": [3001],
  "startAt": "2026-07-23T10:00:00",
  "memberNote": "首次到店，皮肤容易泛红",
  "source": "ONLINE"
}
```

## 护理、结算与库存

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/service-records/{id}/care-record` | 保存敏感护理档案 |
| GET | `/members/{id}/care-records` | 查看授权范围内护理历史 |
| POST | `/orders` | 创建消费单 |
| POST | `/orders/{id}/pay` | 确认支付，幂等执行库存扣减 |
| POST | `/orders/{id}/refund` | 退款并生成反向库存流水 |
| GET | `/products` | 产品/耗材及库存 |
| POST | `/inventory/movements` | 入库、领用或盘点调整 |
| GET | `/inventory/low-stock` | 低库存列表 |
| POST | `/appointments/{id}/review` | 完成预约后的唯一评价 |

## 工作台

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/management/dashboard` | 会员数、今日预约、完成数、月营收、状态分布、14 日趋势、热门项目 |

## 关键错误码

| 错误码 | HTTP | 用户提示 |
|---|---:|---|
| `VALIDATION_FAILED` | 400 | 请检查标记的必填信息 |
| `AUTH_REQUIRED` | 401 | 登录状态已失效，请重新登录 |
| `FORBIDDEN` | 403 | 当前账号没有此操作权限 |
| `MEMBER_NOT_FOUND` | 404 | 未找到该会员 |
| `SERVICE_INACTIVE` | 409 | 该项目已暂停预约，请选择其他项目 |
| `STAFF_NOT_AVAILABLE` | 409 | 美容师当前不在班，请重新选择时间 |
| `APPOINTMENT_SLOT_CONFLICT` | 409 | 该时段刚被预约，请选择其他时间 |
| `INVALID_STATUS_TRANSITION` | 409 | 当前状态不能执行此操作，请刷新后重试 |
| `INSUFFICIENT_STOCK` | 409 | 库存不足，无法完成扣减 |
| `DUPLICATE_REQUEST` | 409 | 请求已处理，请勿重复提交 |

## 权限边界

- `OWNER/MANAGER`：经营数据、员工、项目、库存和全部预约。
- `FRONT_DESK`：会员、预约、签到、订单；不能查看非必要的护理隐私。
- `BEAUTICIAN`：本人排班、本人预约和被授权的护理记录。
- `MEMBER`：本人预约、消费、护理记录和评价。
- 护理档案、退款、库存调整和账号权限变更必须写入 `audit_log`。

## V3 M2 基础资料与资源契约

V3 使用 `Authorization: Bearer <access_token>`，写请求必须携带
`Idempotency-Key: <UUID>`。成功和失败均使用 V3 统一响应，包含稳定
`code`、`message`、`data`、`request_id` 和 UTC `timestamp`。旧
`/api/v1`、`/api/v2` 路由保持兼容。

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| GET | `/api/v3/services?shop_id=` | `service:view` | 门店项目目录 |
| PUT | `/api/v3/services/{serviceId}` | `service:manage` | 乐观锁更新项目资料 |
| GET | `/api/v3/staff?shop_id=` | `staff:view` | 门店员工目录 |
| GET | `/api/v3/service-resources?shop_id=` | `resource:view` | 房间与设备目录 |
| POST | `/api/v3/service-resources` | `resource:manage` | 新增房间或设备 |
| PUT | `/api/v3/service-resources/{resourceId}` | `resource:manage` | 乐观锁更新资源 |
| POST | `/api/v3/service-resources/{resourceId}/deactivate` | `resource:manage` | 停用资源，保留历史占用 |
| GET | `/api/v3/staff/{staffId}/skills?shop_id=` | `staff:view` | 当前技能版本 |
| PUT | `/api/v3/staff/{staffId}/skills/{serviceId}` | `staff:manage` | 关闭旧版本并发布新版本 |
| GET | `/api/v3/staff/{staffId}/schedules?shop_id=` | `staff:view` | 排班、休假与锁定 |
| POST | `/api/v3/staff/{staffId}/schedules` | `staff:manage` | 新增排班规则 |
| POST | `/api/v3/staff/{staffId}/schedules/{scheduleId}/deactivate` | `staff:manage` | 停用排班，保留历史 |

预约创建和改期请求可传 `resource_ids`。应用在同一数据库事务内按资源
编号稳定排序并锁定 `service_resource`，再按 `capacity` 判断重叠占用数；
任一资源达到容量即返回 HTTP 409，整个预约事务回滚。取消、爽约、完成和
改期通过状态更新释放占用，不删除 `resource_booking` 历史。

所有 M2 写接口使用请求体哈希配合 `idempotency_record`：相同键和相同负载
重放已完成结果；相同键但负载不同返回冲突。项目、资源、技能和排班变更写入
`audit_log`，日志不得保存手机号、健康信息、支付凭据或响应正文。
