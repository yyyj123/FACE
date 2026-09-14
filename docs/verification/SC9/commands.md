# SC9 楠屾敹鍛戒护

## 姝ｅ紡涓よ疆

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "& '.\scripts\verify-sc9-runtime.ps1' -Run 1 | Tee-Object -FilePath '.\docs\verification\SC9\run-1.log'"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "& '.\scripts\verify-sc9-runtime.ps1' -Run 2 | Tee-Object -FilePath '.\docs\verification\SC9\run-2.log'"
```

鑴氭湰姣忚疆鍐呴儴鎸夐『搴忔墽琛岋細鍥哄畾绔彛棰勬銆侀潤鎬佸绾︺€丩inux `sh -n`銆丮aven 鍏ㄦ祴璇曘€佷袱濂楀墠绔瀯寤恒€佺┖鍗峰垱寤恒€丗lyway `migrate`銆乣validate`銆佽縼绉荤洰褰?鍘嗗彶鍔ㄦ€佹瘮瀵广€佸叏鏍堟瀯寤轰笌鍋ュ悍绛夊緟銆乀LS 璺敱銆佺洃鎺с€佸璞¤鍐欍€佹暟鎹簱涓庡璞″浠姐€侀殧绂婚」鐩仮澶嶃€佸叏鏍堥噸鍚€丼ecret 娉勬紡鎵弿鍜屽甫鍗锋竻鐞嗐€?
## 灏佹澘澶嶆牳

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-sc9-production-contract.ps1
docker compose --project-name face-prod-postcheck --env-file .env.prod.test -f docker-compose.prod.yml config --quiet
mvn.cmd -B -ntp -f backend-next\pom.xml "-Dtest=ProductionSafetyPolicyTest,ObjectStorageHealthIndicatorTest" test
git diff --check
```

姣忎釜 `scripts/prod-*.sh` 杩樹娇鐢?`maven:3.9.11-eclipse-temurin-21-alpine` 鎵ц `/bin/sh -n`锛屾渶缁堢粨鏋?`SC9_POST_SEAL_VERIFICATION=PASS`銆?
