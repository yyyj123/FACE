# WEB-R 演示数据与公网部署验证记录

## 结论

2026-08-10 已完成响应式网页端与手机端共用演示数据扩充，并通过 Cloudflare Quick Tunnel 发布公网。微信小程序和独立技师端未启用。

- 当前临时公网地址：`https://identifies-motels-void-titanium.trycloudflare.com`
- 本机入口：`http://127.0.0.1:8290`
- 源码起始基线：`ef7e8ea1083ddcb37ad5a8f1c004881687c8fdfb`
- Compose 项目：`face-sc8-demo`
- 镜像标签：`sc8-demo`

公网地址属于临时演示通道；电脑关机、Docker 停止或通道重建后会失效或改变。

## 演示数据结果

`scripts/verify-demo-showcase.ps1` 连续两次运行均返回：

```text
DEMO_SHOWCASE_VERIFY=PASS;GROUPS=22;ITEMS_PER_GROUP=5
```

覆盖结果：

| 数据组 | 数量 |
|---|---:|
| 首页/内容运营 | 5 |
| 演示会员 | 5 |
| 护理项目 | 5 |
| 员工公开资料 | 5 |
| 周期排班 | 5 |
| 日期排班 | 5 |
| 演示运营账号 | 5 |
| 卡项模板 | 5 |
| 会员已拥有卡项 | 5 |
| 优惠券模板 | 5 |
| 会员已拥有优惠券 | 5 |
| 积分任务 | 5 |
| 商城商品 | 5 |
| 商城库存 | 5 |
| 预约 | 5 |
| 评价 | 5 |
| 服务售后工单 | 5 |
| 商城订单 | 5 |
| 退货售后工单 | 5 |
| 退货申请 | 5 |
| 会员通知 | 5 |
| 导入历史批次 | 5 |

所有演示记录使用 `DEMO-*` 固定编码或固定幂等键。脚本重复执行不会增加重复记录，也不会删除用户自行创建的商品或业务记录。

## 构建与运行验证

执行命令：

```powershell
cd E:\face\backend-next
mvn test
```

结果：

```text
Tests run: 218, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

随后重建并重启后端容器，再次运行演示数据检查。结果仍为 22 组、每组 5 条，证明应用 `demo` profile 会自动执行幂等播种。

本机检查：

```text
/healthz = 200
/client/ = 200
/admin/ = 200
```

公网检查：

```text
访问密码会话创建 = PASS
/demo/check = 204
/client/ = 200，返回真实用户端应用壳
/admin/ = 200，返回真实运营端应用壳
/healthz = 200
```

使用公网访问会话和 `demo-admin` 登录后，进一步验证了实际业务接口：内容 5 条、卡项 5 条、优惠券 5 条、评价 5 条、退货 5 条、商城订单 5 条。积分任务和商城商品列表还保留了原有数据，因此页面总数分别大于 5；其中本次新增的 `DEMO-*` 数据仍严格为 5 条。

Compose 状态：MySQL、后端、用户端、运营端和网关均为 `healthy`；`cloudflared` 为 `Up`。

## 部署与停止

完整中文教程：`docs/operations/demo-public-deployment.md`。

从停止状态发布公网：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-start.ps1
```

停止并使临时公网地址失效：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-stop.ps1
```

## 风险与限制

1. Quick Tunnel 没有固定域名和可用性承诺，只适合短期演示。
2. 宿主机必须持续联网并保持 Docker Desktop 运行。
3. 演示环境使用模拟短信、模拟支付和合成数据，不得承载真实客户或真实交易。
4. 运营端和会员端的账号密码来自 `.env.demo`，不得写入文档或提交 Git。
5. 长期正式上线仍需正式域名、TLS、Secret 托管、真实渠道适配、备份恢复与监控告警。
