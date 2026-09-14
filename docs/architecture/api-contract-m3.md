# M3 API 契约补充

## 通用约定

- V3 基础路径：`/api/v3`
- 鉴权：`Authorization: Bearer <access_token>`
- 关键写操作：`Idempotency-Key` 必填，最长 80 字符
- 并发写入：请求体携带当前 `version`
- 成功响应：`code`、`message`、`data`、`request_id`、UTC `timestamp`
- 同键异载荷、版本冲突、非法状态跳转或库存不足返回 HTTP 409

## 预约与服务

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/appointments` | 按门店、日期、状态、关键词分页查询；技师自动收敛为本人范围 |
| POST | `/appointments` | 创建预约，支持项目和房间/设备资源 |
| POST | `/appointments/{id}/confirm` | `PENDING → CONFIRMED` |
| POST | `/appointments/{id}/check-in` | `CONFIRMED → CHECKED_IN` |
| POST | `/appointments/{id}/cancel` | 取消预约，原因必填 |
| POST | `/appointments/{id}/start` | `CHECKED_IN → IN_SERVICE` 并创建服务记录 |
| GET | `/service-records/{id}?shop_id=` | 获取授权范围内护理记录 |
| POST | `/service-records/{id}/complete` | 原子完成护理、耗材、库存、预约、确认、审计和 outbox |

完成服务请求示例：

```json
{
  "shop_id": 1,
  "version": 1,
  "service_summary": "完成清洁与舒缓护理",
  "skin_type": "混合性",
  "concerns": ["补水"],
  "observations": "护理过程稳定",
  "home_care_advice": "晚间加强保湿",
  "next_recommended_at": "2026-08-28",
  "consumptions": [
    {
      "locationId": 1,
      "productId": 1001,
      "quantity": 1,
      "balanceVersion": 3
    }
  ]
}
```

## 顾客确认与护理更正

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| GET | `/customer-confirmations` | 会员本人 | 查询本人的护理结果确认 |
| POST | `/customer-confirmations/{id}/action` | 会员本人 | `CONFIRMED` 或 `REJECTED`；拒绝原因必填 |
| GET | `/service-records/{id}/corrections?shop_id=` | 授权岗位 | 按时间读取追加更正 |
| POST | `/service-records/{id}/corrections` | `service_record:correct` | 追加更正，不覆盖原始护理事实 |

追加更正请求示例：

```json
{
  "shop_id": 1,
  "service_record_version": 2,
  "reason": "补充技师复核后的居家建议",
  "corrected_fields": {
    "homeCareAdvice": "晚间使用温和保湿产品"
  }
}
```

## 订单与支付

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/orders` | 创建订单；订单创建幂等键在租户内唯一 |
| POST | `/orders/{id}/payments` | 支付订单；校验订单版本、金额、支付方式、幂等键和请求哈希 |

支付请求不在日志中输出外部支付凭据；相同幂等键同载荷安全重放，相同键不同载荷返回冲突。

## V2 三端兼容路由

顾客/技师端继续使用 `/api/v2/client`：

- `GET /appointments`
- `POST /appointments/{id}/status`
- `GET /service-records/resources`
- `GET /service-records/{id}`
- `POST /service-records/start`
- `POST /service-records/{id}/complete`
- `GET /confirmations`
- `POST /confirmations/{id}/action`

V2 仅为协议适配，状态机、权限、幂等和事务规则与 V3 使用同一应用服务。
