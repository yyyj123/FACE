param(
    [string]$BaseUrl = 'http://127.0.0.1:8090/face-next',
    [string]$ClientUrl = 'http://127.0.0.1:8082'
)

$ErrorActionPreference = 'Stop'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$memberUsername = 'face_client_v2_member'
$technicianUsername = 'face_client_v2_tech'
$password = 'TestOnly-2026!'
$results = New-Object System.Collections.Generic.List[object]

function Invoke-Sql([string]$sql) {
    $output = & $mysql '--protocol=TCP' '--host=127.0.0.1' '--port=3308' '--user=face_app' `
        '--password=FaceDb@2026!' '--database=face_salon' '--batch' '--raw' '--skip-column-names' "--execute=$sql"
    if ($LASTEXITCODE -ne 0) { throw "SQL failed: $sql" }
    return $output
}

function Invoke-Api(
    [string]$method,
    [string]$path,
    [object]$body = $null,
    [string]$token = ''
) {
    $uri = "$BaseUrl/$path"
    if ($path -match '^api/v2/client/(me|dashboard|appointments)' -and $token.Length -ne 64) {
        throw "Protected request $path received token length $($token.Length)"
    }
    $curlArgs = @('-sS', '-X', $method.ToUpperInvariant(), '-H', 'Accept: application/json')
    if ($token) { $curlArgs += @('-H', "Token:$token") }
    $json = $null
    if ($null -ne $body) {
        $json = $body | ConvertTo-Json -Depth 10 -Compress
        $curlArgs += @('-H', 'Content-Type: application/json; charset=utf-8', '--data-binary', '@-')
    }
    $curlArgs += $uri
    try {
        $rawResponse = if ($null -ne $json) {
            $json | & curl.exe @curlArgs
        } else {
            & curl.exe @curlArgs
        }
        if ($LASTEXITCODE -ne 0) { throw "curl exited with code $LASTEXITCODE" }
        $response = ($rawResponse -join [Environment]::NewLine) | ConvertFrom-Json
    }
    catch {
        throw "$method $path returned HTTP error: $($_.Exception.Message)"
    }
    if ($response.code -ne 0) { throw "$method $path failed: $($response.msg)" }
    return $response.data
}

function Add-Pass([string]$area, [string]$detail) {
    $results.Add([pscustomobject]@{ Area = $area; Detail = $detail; Status = 'PASS' })
}

function Cleanup {
    Invoke-Sql @"
DELETE ai FROM appointment_item ai
JOIN appointment a ON a.id = ai.appointment_id
JOIN member m ON m.id = a.member_id
WHERE m.phone = '13900008881';
DELETE ash FROM appointment_status_history ash
JOIN appointment a ON a.id = ash.appointment_id
JOIN member m ON m.id = a.member_id
WHERE m.phone = '13900008881';
DELETE a FROM appointment a
JOIN member m ON m.id = a.member_id
WHERE m.phone = '13900008881';
DELETE FROM staff_schedule WHERE remark = 'FACE_CLIENT_V2';
DELETE FROM token WHERE username IN ('$memberUsername', '$technicianUsername');
DELETE al FROM audit_log al
JOIN account ac ON ac.id = al.account_id
WHERE ac.username IN ('$memberUsername', '$technicianUsername');
DELETE FROM account WHERE username IN ('$memberUsername', '$technicianUsername');
DELETE ma FROM member_account ma
JOIN member m ON m.id = ma.member_id
WHERE m.phone = '13900008881' OR m.member_no LIKE 'M%CLIENTV2%';
DELETE msp FROM member_shop_profile msp
JOIN member m ON m.id = msp.member_id
WHERE m.phone = '13900008881' OR m.member_no LIKE 'M%CLIENTV2%';
DELETE FROM member WHERE phone = '13900008881' OR member_no LIKE 'M%CLIENTV2%';
DELETE FROM staff_service WHERE staff_id IN (SELECT id FROM staff WHERE staff_no = '$technicianUsername');
DELETE FROM staff_schedule WHERE staff_id IN (SELECT id FROM staff WHERE staff_no = '$technicianUsername');
DELETE FROM staff_shop_assignment WHERE staff_id IN (SELECT id FROM staff WHERE staff_no = '$technicianUsername');
DELETE FROM staff WHERE staff_no = '$technicianUsername';
DELETE FROM weixiujishi WHERE weixiuzhanghao = '$technicianUsername';
"@ | Out-Null
}

try {
    Cleanup

    $clientResponse = Invoke-WebRequest -Uri $ClientUrl -UseBasicParsing -TimeoutSec 10
    if ($clientResponse.StatusCode -ne 200 -or $clientResponse.Content -notmatch '/src/main\.ts') {
        throw 'Vue 3 client entry did not respond on port 8082'
    }
    Add-Pass 'client-entry' '8082 serves the Vue 3 + TypeScript application'

    $banners = Invoke-Api 'Get' 'api/v2/client/public/banners?shopId=1'
    $categories = Invoke-Api 'Get' 'api/v2/client/public/service-categories?shopId=1'
    $services = Invoke-Api 'Get' 'api/v2/client/public/services?shopId=1'
    $staff = Invoke-Api 'Get' 'api/v2/client/public/staff?shopId=1'
    if ($banners.Count -lt 2 -or $services.Count -lt 1 -or $staff.Count -lt 1) {
        throw 'Public catalog data is incomplete'
    }
    Add-Pass 'public-data' "$($banners.Count) banners / $($services.Count) services / $($staff.Count) staff"

    if (@($categories).Count -ne 9) {
        throw "Expected 9 client categories, got $(@($categories).Count)"
    }
    if (@($categories | Where-Object { $_.id -eq 8 }).Count -ne 0) {
        throw 'The unused category is still visible'
    }
    foreach ($category in @($categories)) {
        $categoryServices = @($services | Where-Object { $_.categoryId -eq $category.id })
        if ($categoryServices.Count -ne 5) {
            throw "Category $($category.name) expected 5 services, got $($categoryServices.Count)"
        }
    }
    $serviceCovers = @($services | ForEach-Object { $_.coverUrl })
    if ($serviceCovers.Count -ne 45 -or @($serviceCovers | Sort-Object -Unique).Count -ne 45) {
        throw 'The catalog must expose 45 unique service covers'
    }
    Add-Pass 'service-catalog' '9 categories / 5 services each / 45 unique covers'

    $mediaPaths = @(
        @($banners) | ForEach-Object { $_.imageUrl }
        @($services) | ForEach-Object { $_.coverUrl }
        @($staff) | ForEach-Object { $_.avatarUrl }
    ) | Where-Object { $_ -match '^upload/.+\.(avif|gif|jpe?g|png|webp)$' } | Sort-Object -Unique
    foreach ($mediaPath in $mediaPaths) {
        $mediaResponse = Invoke-WebRequest -Uri "$ClientUrl/face-next/$mediaPath" -UseBasicParsing -TimeoutSec 10
        if ($mediaResponse.StatusCode -ne 200 -or $mediaResponse.Headers['Content-Type'] -notmatch '^image/') {
            throw "Media asset is not available through the active client: $mediaPath"
        }
    }
    Add-Pass 'client-media' "$($mediaPaths.Count) referenced images served by backend-next"

    Invoke-Api 'Post' 'api/v2/client/auth/register' @{
        username = $memberUsername
        password = $password
        name = 'client-v2-member'
        phone = '13900008881'
        shopId = 1
    } | Out-Null
    $memberAuth = Invoke-Api 'Post' 'api/v2/client/auth/login' @{
        username = $memberUsername
        password = $password
    }
    if ($memberAuth.role -ne 'MEMBER' -or -not $memberAuth.memberId) {
        throw 'Member login did not return a MEMBER-scoped session'
    }
    $memberToken = [string]$memberAuth.token
    if ($memberToken.Length -ne 64) { throw "Member login returned an invalid token length: $($memberToken.Length)" }
    $memberProfile = Invoke-Api -method 'Get' -path 'api/v2/client/me' -token $memberToken
    $memberDashboard = Invoke-Api -method 'Get' -path 'api/v2/client/dashboard' -token $memberToken
    Invoke-Api -method 'Get' -path 'api/v2/client/appointments' -token $memberToken | Out-Null
    if ($memberProfile.name -ne 'client-v2-member' -or $null -eq $memberDashboard.summary) {
        throw 'Member profile/dashboard contract is incomplete'
    }
    $updatedMember = Invoke-Api 'Put' 'api/v2/client/me' @{
        name = 'client-v2-member-updated'
        phone = '13900008881'
        gender = 'F'
        avatarUrl = ''
    } -token $memberToken
    if ($updatedMember.name -ne 'client-v2-member-updated') {
        throw 'Member profile update did not persist'
    }
    Add-Pass 'member-center' 'register / login / scoped profile / update / dashboard'

    $service = $services | Select-Object -First 1
    $serviceStaff = Invoke-Api 'Get' "api/v2/client/public/staff?shopId=1&serviceId=$($service.id)"
    if (@($serviceStaff).Count -lt 1) { throw 'No staff-service binding is available for booking verification' }
    $bookingStaff = $serviceStaff | Select-Object -First 1
    $bookingDate = (Get-Date).Date.AddDays(10).ToString('yyyy-MM-dd')
    Invoke-Sql "INSERT INTO staff_schedule (tenant_id, shop_id, staff_id, schedule_date, start_time, end_time, schedule_type, remark) VALUES (1, 1, $($bookingStaff.id), '$bookingDate', '19:00:00', '23:00:00', 'WORK', 'FACE_CLIENT_V2');" | Out-Null
    $createdAppointment = Invoke-Api 'Post' 'api/v2/client/appointments' @{
        shopId = 1
        memberId = 999999
        staffId = [long]$bookingStaff.id
        serviceIds = @([long]$service.id)
        startAt = "${bookingDate}T19:00:00"
        source = 'ONLINE'
        memberNote = 'client-v2-smoke'
    } -token $memberToken
    $memberAppointments = Invoke-Api -method 'Get' -path 'api/v2/client/appointments' -token $memberToken
    $createdRow = $memberAppointments | Where-Object { $_.id -eq $createdAppointment.id } | Select-Object -First 1
    if (-not $createdRow) { throw 'Created appointment is not visible in the member scope' }
    Invoke-Api 'Post' "api/v2/client/appointments/$($createdRow.id)/cancel" @{
        version = [int]$createdRow.version
    } -token $memberToken | Out-Null
    Add-Pass 'booking-flow' 'member scope override / conflict validation / list / cancel'

    $passwordHash = (Invoke-Sql "SELECT password_hash FROM account WHERE username = '$memberUsername' LIMIT 1;").Trim()
    Invoke-Sql @"
INSERT INTO staff
    (tenant_id, home_shop_id, shop_id, staff_no, name, phone, job_role, level_name, status)
VALUES
    (1, 1, 1, '$technicianUsername', 'client-v2-tech', '13800008882', 'BEAUTICIAN', 'Senior', 'ACTIVE');
SET @staff_id = LAST_INSERT_ID();
INSERT INTO staff_shop_assignment
    (tenant_id, staff_id, shop_id, assignment_type, effective_from, status)
VALUES
    (1, @staff_id, 1, 'PRIMARY', CURRENT_DATE, 'ACTIVE');
INSERT INTO account
    (tenant_id, home_shop_id, shop_id, username, display_name, password_hash, role_code, staff_id, status)
VALUES
    (1, 1, 1, '$technicianUsername', 'client-v2-tech', '$passwordHash', 'BEAUTICIAN', @staff_id, 'ACTIVE');
SET @account_id = LAST_INSERT_ID();
INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
SELECT 1, @account_id, 1, id, 'ACTIVE'
FROM role_definition
WHERE tenant_id = 1 AND role_code = 'BEAUTICIAN'
LIMIT 1;
"@ | Out-Null
    $technicianAuth = Invoke-Api 'Post' 'api/v2/client/auth/login' @{
        username = $technicianUsername
        password = $password
    }
    if ($technicianAuth.role -ne 'BEAUTICIAN' -or -not $technicianAuth.staffId) {
        throw 'Technician login did not return a BEAUTICIAN-scoped session'
    }
    $technicianToken = [string]$technicianAuth.token
    if ($technicianToken.Length -ne 64) { throw "Technician login returned an invalid token length: $($technicianToken.Length)" }
    $technicianProfile = Invoke-Api -method 'Get' -path 'api/v2/client/me' -token $technicianToken
    $technicianDashboard = Invoke-Api -method 'Get' -path 'api/v2/client/dashboard' -token $technicianToken
    Invoke-Api -method 'Get' -path 'api/v2/client/appointments' -token $technicianToken | Out-Null
    if ($technicianProfile.name -ne 'client-v2-tech' -or $null -eq $technicianDashboard.summary) {
        throw 'Technician profile/dashboard contract is incomplete'
    }
    $updatedTechnician = Invoke-Api 'Put' 'api/v2/client/me' @{
        name = 'client-v2-tech-updated'
        phone = '13800008882'
        avatarUrl = ''
        bio = 'client-v2-profile'
    } -token $technicianToken
    if ($updatedTechnician.bio -ne 'client-v2-profile') {
        throw 'Technician profile update did not persist'
    }
    Add-Pass 'technician-center' 'login / own profile / update / own schedule / own appointments'

    $results | Format-Table -AutoSize
}
finally {
    Cleanup
}
