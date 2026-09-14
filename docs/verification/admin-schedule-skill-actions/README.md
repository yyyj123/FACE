# 运营端周期排班恢复与技师技能增删改验收

验收日期：2026-08-18（Asia/Shanghai）
实施前基线 commit：`d0c65eb73743a896188f1192cdafd10b0cbeab37`
实现与验收 commit：`f56ae581e8a0f59ba6f3caa5c65364a3fb3b8b3c`
演示版本：`FACE_IMAGE_TAG=sc8-demo`
本地入口：`http://127.0.0.1:8290`
临时公网入口：`https://revisions-london-giving-broadcast.trycloudflare.com`

## 结论

PASS。

- 周期排班卡片明确展示“启用中/已停用”，按状态提供“停用/重新启用”。
- 重新启用使用版本号、幂等键、重叠检查和审计记录；有冲突时返回 409，不会绕过现有排班。
- 技师项目技能明确提供“新增、编辑、删除、恢复”。
- 删除技能采用停用版本，不物理删除历史；恢复和编辑都会创建下一版本。
- 新增技能时不再重复列出已经配置的项目，已删除技能通过列表中的“恢复”操作恢复。

## 验证结果

### 静态与自动化验证

- `npm run build`（`admin-next`）：PASS。
- `mvn -q test`（`backend-next`）：PASS。
- Surefire 汇总：121 个测试套件，224 个测试，0 failures，0 errors，0 skipped。
- `git diff --check`：PASS。
- Impeccable 检测指出旧 `.schedule-card.fact` 的 3px 单侧边框；已增加 1px 覆盖并沿用现有铜色状态表达。

### 真实接口闭环

- 临时周期规则：创建 PASS → 停用 `INACTIVE` → 重新启用 `ACTIVE` → 精确清理 PASS。
- 演示重复规则恢复：返回 409，冲突保护 PASS；测试前状态已恢复并记录 `SCHEDULE_RULE_TEST_RESTORE` 审计。
- 现有技能：修改 PASS → 删除 `enabled=false` → 恢复 `enabled=true`，原自定义时长恢复，最终版本 v4。
- 临时技能：新增 v1 → 删除 v2 → 精确清理 PASS。
- 所有测试操作均使用管理员令牌和不同的 `Idempotency-Key`。

### 运行状态

- MySQL、backend、admin、client、gateway：healthy。
- Cloudflare Quick Tunnel：运行中，公网入口继续指向当前 gateway。

## 数据与来源说明

- 用户提供的两张截图只用于确认缺失的操作入口，没有把截图文字作为数据库事实。
- 周期排班“停用”沿用 `staff_schedule_rule.status`；新增“重新启用”不会恢复被其他规则占用的冲突时段。
- 技能“删除”沿用既有 `staff_skill_version` 版本模型和 `staff_service.enabled=false`，不会删除历史预约或历史技能版本。
- 本轮未进入 SC3、微信小程序或独立技师端。
- 当前浏览器控制执行通道仍未向本任务开放，因此本轮未生成新的自动化浏览器截图；页面构建、真实接口和运行容器均已验证。
