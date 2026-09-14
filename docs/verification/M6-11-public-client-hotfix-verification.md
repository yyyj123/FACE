# M6-11 公网客户门户热修复验收记录

## 结论

M6-11 通过。临时公网客户门户已恢复项目与技师内容，注册请求不再被 CORS 拒绝；无数据库结构变更和历史数据迁移。

## 测试驱动记录

实现前新增并确认失败：

- `ClientCatalogCompatibilityContractTest`：检测到 `FROM storeup`。
- `ClientAuthServiceValidationTest`：非法手机号继续进入门店查询并返回 404。
- `front-next/tests/error-message.test.ts`：错误转换模块不存在。
- `verify-docker-contract.ps1`：未启用可信转发头处理。

实现后结果：

```text
后端定向测试：PASS
前端错误提示测试：3/3 PASS
Docker 部署契约：PASS
```

## 完整验证

```text
mvn -f backend-next/pom.xml clean verify
Tests run: 163, Failures: 0, Errors: 0, Skipped: 0

npm --prefix front-next run build
PASS

npm --prefix admin-next run build
PASS
```

## Docker 与公网验证

现有 `face-demo` 数据卷保持不变，后端、管理端、客户端镜像重新构建后四个容器均为 Healthy。

```text
PUBLIC_HOME_STATUS=200
PUBLIC_SERVICES_CODE=0
PUBLIC_SERVICES_COUNT=9
PUBLIC_STAFF_CODE=0
PUBLIC_STAFF_COUNT=2
PUBLIC_INVALID_REGISTER_STATUS=400
```

带临时公网 `Origin` 的非法注册已进入业务校验并返回 400，证明原 403 CORS 阻断已消除；该请求未产生会员数据。

## 浏览器验收

```text
PUBLIC_BROWSER_HOME={"hasGenericError":false,"notices":[],"galleryCount":2}
PUBLIC_BROWSER_REGISTER_VALIDATION={"usernameMinLength":3,"phoneValid":false,"phonePattern":"1[3-9][0-9]{9}"}
PUBLIC_BROWSER_ACCEPTANCE=PASS
```

预览证据位于 `E:/FACE/.artifacts/预览/FACE-公网修复验收`。

## 数据库结论

- Flyway 版本保持 `2026080103`。
- 未新增迁移、表或索引。
- 未修改预约、支付、套餐、库存、退款、提成和审计数据。
