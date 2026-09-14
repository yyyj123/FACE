# 汽车服务系统到美容院系统迁移说明

## 原则

`E:\face` 是独立改造副本，`C:\project` 保持不变。前端现阶段保留旧模块英文目录和 `tableName`，仅作为兼容层；新后端完成后，应逐步切换到 `api-contract.md` 中的领域 API，避免数据库继续使用汽车字段。

## 领域映射

| 旧模块 | 过渡显示名 | 新数据库 |
|---|---|---|
| `chezhu` | 会员/顾客 | `member`、`account` |
| `weixiujishi` | 美容师 | `staff`、`staff_service`、`staff_schedule` |
| `fuwufenlei` | 项目分类 | `service_category` |
| `shouhoufuwu` | 美容项目 | `service_item` |
| `xinnengyuanqiche` | 护理套餐（过渡） | 后续增加套餐表；MVP 不作为核心项目表 |
| `fuwuyuyue` | 项目预约 | `appointment`、`appointment_item` |
| `weixiujilu` | 到店服务/消费记录 | `service_record`、`sales_order` |
| `pingjiafankui` | 服务评价 | `review` |
| `peijianxinxi` | 产品与耗材 | `product` |
| `peijianchuku` | 库存流水 | `inventory_movement` |
| `pinpaixinxi` | 产品品牌 | `product.brand_name` 或品牌字典 |
| `guzhangfenlei` | 肌肤问题分类 | 二期字典 |
| `guzhangpaicha` | 护理建议 | `care_record`，二期扩展规则库 |
| `weixiuziliao` | 护理知识 | 二期内容表 |
| `config` | 轮播图 | `banner` |
| `chat` | 在线咨询 | `consultation` + 消息表（后端实现） |

## 实施阶段

1. 建立新数据库，录入门店、项目分类、项目、美容师与班次。
2. 实现认证、会员、项目、排班和预约 API；预约写入使用事务与行锁。
3. 前端从旧表接口切到 `/api/v1`，先打通“项目详情 → 时段 → 预约 → 我的预约”。
4. 管理端切换会员、预约日历、签到、服务记录和结算。
5. 上线库存流水和经营工作台；旧汽车表转只读后归档。

## 禁止直接迁移的旧字段

- 车牌号、车辆问题、汽车型号、座位数、车身颜色。
- 维修状态、开始维修、维修账号、配件销售额。

这些字段与美容院业务语义不同，不能仅改中文标签后继续入库。

## 验证门槛

- 同一美容师重叠时段只能成功创建一条有效预约。
- 取消预约后时段重新可用。
- 完成服务才能生成护理记录和待支付订单。
- 支付成功与库存扣减处于同一事务，并能通过幂等键防止重复执行。
- 退款产生反向库存流水，不覆盖历史流水。
- 会员、美容师、前台和店长的数据权限分别验证。
