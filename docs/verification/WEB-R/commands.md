# WEB-R 验收命令

工作目录：`E:\face`

```powershell
# 定向契约
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-web-r-ui-contract.ps1
npm.cmd --prefix .\front-next test
npm.cmd --prefix .\admin-next test

# 全量测试与生产构建
mvn.cmd -B -ntp -f .\backend-next\pom.xml test
npm.cmd --prefix .\front-next run build
npm.cmd --prefix .\admin-next run build

# 两个独立干净环境的正式验收
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-web-r-runtime.ps1 -Run 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-web-r-runtime.ps1 -Run 2

# 证据与版本核对
git diff --check
git status --short
git rev-parse HEAD
git rev-parse "sc9/v1.0.0^{commit}"
git tag --points-at HEAD
```

脚本要求 `FACE_DEMO_PORT` 或 `-DemoPort` 显式提供有效端口并在启动前预检，不自动随机选择端口。数据库从干净卷执行 Flyway `migrate`、`validate`，再动态比较迁移目录与 `flyway_schema_history`。
