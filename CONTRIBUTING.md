# FACE 仓库执行约束

## 当前产品基线

本仓库当前执行 `FACE_用户端与运营管理平台增量升级PRD_V3.0.md` 的“方案一”。可见交付仅包括：

- 响应式 H5/Web 用户端（`front-next`，部署路径 `/client/`）；
- 响应式运营管理平台（`admin-next`，部署路径 `/admin/`）；
- 统一后端（`backend-next`，部署路径 `/api/`）；
- 后续复用同一后端的微信小程序。

## WEB-R 阶段门禁

- 人工已明确要求“WEB-R 执行”，并明确“微信小程序先不做，先完善网页端和手机端”；WEB-R 两端响应式稳定化和两轮可重复运行验收已完成并留证。
- 当前必须停止，不得进入微信小程序、恢复已暂停的 SC10 实现或执行正式域名生产切换/真实渠道开通。
- 必须等待人工下一步明确指令后才允许继续；不得自动恢复 `stash@{0}`。
- 不得删除历史技师端、复杂多角色后台、采购、提成、培训相关代码或表。
- 公开 DEMO 不得展示独立技师端入口。

## 文档与历史

- V3.0 PRD 是当前范围的唯一产品基线。
- V2.1 及更早规格、历史验证报告和截图只作来源记录，不得据此恢复已停止的交付范围。
- 首次 Git 标签是收到代码后的首次可追溯快照，不得描述为原始开发历史。
- 对历史材料只增加清晰的状态声明，不重写其原始结论或伪造时间线。

## 实施与验证

- 新工作优先落在 `front-next`、`admin-next` 和 `backend-next`；旧 `front`、`admin`、`backend` 只作迁移参考。
- 所有端口必须显式参数化并在启动前预检，禁止自动随机选端口。
- 数据库迁移验证必须使用 Flyway `migrate`、`validate`、`flyway_schema_history` 和迁移目录动态结果，不得写死某个迁移版本。
- 每个阶段先写 `docs/plans/YYYY-MM-DD-<stage>.md`，再按阶段门禁实现、验证、留证并提交。
- SC0 范围契约：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc0-scope.ps1`。
- SC1 设计契约：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc1-design-contract.ps1`。
- SC2 运行验收：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc2-runtime.ps1`。
- SC3 运行验收：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc3-runtime.ps1`。
- SC4 运行验收：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc4-runtime.ps1`。
- SC5 运行验收：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc5-runtime.ps1`。
- SC6 运行验收：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc6-runtime.ps1`。
- SC7 运行验收：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc7-runtime.ps1`。
- SC8 运行验收：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc8-runtime.ps1 -Run 1` 和 `-Run 2`。
- SC9 运行验收：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc9-runtime.ps1 -Run 1` 和 `-Run 2`。
- WEB-R 运行验收：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-web-r-runtime.ps1 -Run 1` 和 `-Run 2`。
