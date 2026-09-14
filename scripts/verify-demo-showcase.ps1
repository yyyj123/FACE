param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.demo'),
    [string]$ProjectName = 'face-sc8-demo'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = [IO.Path]::GetFullPath($Root)
$EnvFile = [IO.Path]::GetFullPath($EnvFile)
if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) { throw "Demo env file is missing: $EnvFile" }
if ($ProjectName -notmatch '^face-sc8-[a-z0-9_-]+$') { throw 'ProjectName must start with face-sc8-.' }

$sql = @'
SELECT 'content',COUNT(*) FROM banner WHERE JSON_UNQUOTE(JSON_EXTRACT(body_json,'$.seedCode')) LIKE 'DEMO-CONTENT-%'
UNION ALL SELECT 'members',COUNT(*) FROM member WHERE member_no LIKE 'DEMO-M-%'
UNION ALL SELECT 'services',COUNT(*) FROM service_item WHERE service_code LIKE 'DEMO-SVC-%'
UNION ALL SELECT 'staff',COUNT(*) FROM staff WHERE staff_no LIKE 'DEMO-ST-%'
UNION ALL SELECT 'schedule_rules',COUNT(*) FROM staff_schedule_rule r JOIN staff s ON s.id=r.staff_id WHERE s.staff_no LIKE 'DEMO-ST-%'
UNION ALL SELECT 'schedule_facts',COUNT(*) FROM staff_schedule s JOIN staff st ON st.id=s.staff_id WHERE st.staff_no LIKE 'DEMO-ST-%'
UNION ALL SELECT 'admin_accounts',COUNT(*) FROM account WHERE username LIKE 'demo-operator-%'
UNION ALL SELECT 'cards',COUNT(*) FROM package_product WHERE package_code LIKE 'DEMO-CARD-%'
UNION ALL SELECT 'owned_cards',COUNT(*) FROM package_instance WHERE instance_no LIKE 'DEMO-PI-%'
UNION ALL SELECT 'coupons',COUNT(*) FROM coupon_template WHERE template_code LIKE 'DEMO-COUPON-%'
UNION ALL SELECT 'owned_coupons',COUNT(*) FROM member_coupon WHERE coupon_no LIKE 'DEMO-MC-%'
UNION ALL SELECT 'points_tasks',COUNT(*) FROM points_task WHERE task_code LIKE 'DEMO-POINTS-%'
UNION ALL SELECT 'mall_products',COUNT(*) FROM mall_product WHERE product_code LIKE 'DEMO-PROD-%'
UNION ALL SELECT 'mall_inventory',COUNT(*) FROM mall_sku s JOIN mall_sku_inventory i ON i.sku_id=s.id WHERE s.sku_code LIKE 'DEMO-SKU-%'
UNION ALL SELECT 'appointments',COUNT(*) FROM appointment WHERE appointment_no LIKE 'DEMO-APT-%'
UNION ALL SELECT 'reviews',COUNT(*) FROM service_review r JOIN service_record sr ON sr.id=r.service_record_id WHERE sr.record_no LIKE 'DEMO-SR-%'
UNION ALL SELECT 'service_cases',COUNT(*) FROM after_sale_case WHERE case_no LIKE 'DEMO-AS-SVC-%'
UNION ALL SELECT 'mall_orders',COUNT(*) FROM mall_order WHERE order_no LIKE 'DEMO-MO-%'
UNION ALL SELECT 'return_cases',COUNT(*) FROM after_sale_case WHERE case_no LIKE 'DEMO-AS-RETURN-%'
UNION ALL SELECT 'returns',COUNT(*) FROM mall_return_request WHERE idempotency_key LIKE 'demo-return-%'
UNION ALL SELECT 'notifications',COUNT(*) FROM notification_message WHERE event_type='DemoNotificationSeeded'
UNION ALL SELECT 'import_batches',COUNT(*) FROM legacy_import_batch WHERE batch_no LIKE 'DEMO-IMPORT-%';
'@

$compose = @(
    'compose', '--project-name', $ProjectName, '--project-directory', $Root,
    '--env-file', $EnvFile, '-f', (Join-Path $Root 'compose.yaml'),
    '-f', (Join-Path $Root 'docker-compose.demo.yml')
)
$rows = $sql | & docker @compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B face_salon'
if ($LASTEXITCODE -ne 0) { throw 'Demo showcase count query failed.' }

$facts = [ordered]@{}
foreach ($row in $rows) {
    $parts = $row -split "`t"
    if ($parts.Count -ne 2) { throw "Unexpected showcase result: $row" }
    $facts[$parts[0]] = [int]$parts[1]
}
if ($facts.Count -ne 22) { throw "Expected 22 showcase groups, found $($facts.Count)." }
$invalid = @($facts.GetEnumerator() | Where-Object Value -ne 5)
if ($invalid.Count -gt 0) {
    $details = ($invalid | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', '
    throw "Showcase counts must all equal 5: $details"
}

$facts.GetEnumerator() | ForEach-Object { Write-Output "DEMO_SHOWCASE_$($_.Key.ToUpper())=$($_.Value)" }
Write-Output 'DEMO_SHOWCASE_VERIFY=PASS;GROUPS=22;ITEMS_PER_GROUP=5'
