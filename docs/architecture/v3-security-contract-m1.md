# V3 安全与平台基础契约（M1）

版本：V1.1  
实现阶段：M1  
基路径：`/api/v3`

## 1. 通用响应

成功：

```json
{
  "code": "SUCCESS",
  "message": "ok",
  "data": {},
  "request_id": "req-...",
  "timestamp": "2026-07-28T06:30:00Z"
}
```

失败：

```json
{
  "code": "UNAUTHENTICATED",
  "message": "登录状态已失效，请重新登录",
  "data": null,
  "request_id": "req-...",
  "timestamp": "2026-07-28T06:30:00Z"
}
```

- `X-Request-Id` 只接受 1–64 位字母、数字、`.`、`_`、`:`、`-`。
- 缺失或非法请求 ID 由服务端生成 UUID。
- 响应头回传 `X-Request-Id`。
- V1/V2 保持原响应结构，不受本契约影响。

## 2. 认证与会话

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/auth/login` | 公开/数据库限流 | 登录并创建可撤销会话 |
| POST | `/auth/refresh` | 有效刷新令牌 | 同事务轮换访问和刷新令牌 |
| POST | `/auth/logout` | Bearer 访问令牌 | 撤销当前会话 |

登录请求：

```json
{"username":"jishi01","password":"***"}
```

刷新请求：

```json
{"refresh_token":"opaque-token"}
```

登录/刷新成功的 `data`：

```json
{
  "session_id": "uuid",
  "access_token": "opaque-token",
  "token_type": "Bearer",
  "access_expires_at": "2026-07-28T06:45:00Z",
  "refresh_token": "opaque-token",
  "refresh_expires_at": "2026-08-27T06:30:00Z"
}
```

规则：

- 访问令牌默认 15 分钟，刷新令牌默认 30 天，可通过环境变量调整。
- 数据库只保存两个令牌的 SHA-256 哈希。
- 刷新使用行锁和 `version` 乐观条件；并发刷新最多一个成功。
- 刷新成功后旧访问令牌和旧刷新令牌同时失效。
- 退出只撤销当前会话，保留审计记录。
- 连续 5 次密码错误后账号临时锁定 15 分钟；错误提示不暴露账号是否存在。

## 3. 平台接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/me/context` | Bearer | 服务端解析租户、账号、角色、权限和范围 |
| GET | `/shops` | Bearer | 返回当前授权覆盖的门店 |
| GET | `/health/readiness` | 公开运维探针 | 数据库、Flyway、必需表和权限索引就绪 |

`/me/context` 不接受客户端传入的 `tenant_id`。SELF 角色可以看到自身角色权限码，但不会因此获得管理门店或其他主体数据的权限。

## 4. 稳定错误码

| HTTP | code | 场景 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 请求字段不合法 |
| 401 | `UNAUTHENTICATED` | 凭据错误、令牌过期、会话撤销或刷新重放 |
| 403 | `PERMISSION_DENIED` | 无权限或超出授权范围 |
| 409 | `VERSION_CONFLICT` | 唯一约束或版本冲突 |
| 429 | `RATE_LIMITED` | 登录失败次数达到限制 |
| 500 | `INTERNAL_ERROR` | 未分类内部错误，不返回堆栈 |
| 503 | `DEPENDENCY_UNAVAILABLE` | 数据库或迁移契约未就绪 |

## 5. 审计与敏感数据

- `/me/context` 与 `/shops` 的访问写入 `data_access_log`。
- 审计只保存租户、账号、资源标识、动作、结果和 request_id。
- 不保存响应正文、密码、原始令牌、手机号、健康信息或支付凭据。
