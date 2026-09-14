param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.docker.test'),
    [string]$ProjectName = 'face-sc3-acceptance',
    [int]$BackendPort = 8690,
    [int]$AdminPort = 8681,
    [int]$ClientPort = 8682,
    [int]$TechnicianPort = 8683,
    [switch]$KeepRunning
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Net.Http

$Root = [System.IO.Path]::GetFullPath($Root)
$EnvFile = [System.IO.Path]::GetFullPath($EnvFile)
$composeFile = Join-Path $Root 'compose.yaml'
$baseVerifier = Join-Path $PSScriptRoot 'verify-docker-runtime.ps1'
$script:composeArgs = @(
    'compose', '--project-name', $ProjectName, '--project-directory', $Root,
    '--env-file', $EnvFile, '-f', $composeFile
)

function Invoke-MySqlQuery {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $result = $Sql | & docker @script:composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot face_salon'
    if ($LASTEXITCODE -ne 0) { throw 'SC3 MySQL verification query failed.' }
    return @($result)
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [object]$Body,
        [string]$Token
    )
    $request = New-ApiRequest $Method $Uri $Body $Token
    $response = $script:http.SendAsync($request).GetAwaiter().GetResult()
    $request.Dispose()
    return [pscustomobject]@{
        Status = [int]$response.StatusCode
        Text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    }
}

function New-ApiRequest {
    param([string]$Method, [string]$Uri, [object]$Body, [string]$Token)
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Uri)
    if ($Token) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $request.Content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, 'application/json')
    }
    return $request
}

function Require-Status {
    param($Response, [int]$Expected, [string]$Label)
    if ($Response.Status -ne $Expected) {
        throw "$Label expected HTTP $Expected but received $($Response.Status): $($Response.Text)"
    }
}

$portEnvironment = [ordered]@{
    FACE_BACKEND_PORT = $BackendPort
    FACE_ADMIN_PORT = $AdminPort
    FACE_CLIENT_PORT = $ClientPort
    FACE_TECHNICIAN_PORT = $TechnicianPort
}
$originalEnvironment = @{}
foreach ($entry in $portEnvironment.GetEnumerator()) {
    $originalEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
    [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
}

$script:http = [System.Net.Http.HttpClient]::new()
$started = $false
try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $baseVerifier `
        -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName `
        -BackendPort $BackendPort -AdminPort $AdminPort -ClientPort $ClientPort `
        -TechnicianPort $TechnicianPort -KeepRunning
    if ($LASTEXITCODE -ne 0) { throw 'Base Docker runtime verification failed.' }
    $started = $true

    $clientBase = "http://127.0.0.1:$ClientPort/face-next"
    $adminBase = "http://127.0.0.1:$AdminPort/face-next"
    $tomorrow = [DateTime]::Today.AddDays(1).ToString('yyyy-MM-dd')
    $slot = "${tomorrow}T10:00"

    $seed = @"
SET @service_id := (SELECT ss.service_id FROM staff_service ss JOIN service_item si ON si.id = ss.service_id AND si.status = 'ACTIVE' JOIN staff st ON st.id = ss.staff_id AND st.status = 'ACTIVE' WHERE ss.enabled = 1 ORDER BY ss.service_id, ss.staff_id LIMIT 1);
SET @staff_id := (SELECT ss.staff_id FROM staff_service ss JOIN staff st ON st.id = ss.staff_id AND st.status = 'ACTIVE' WHERE ss.service_id = @service_id AND ss.enabled = 1 ORDER BY ss.staff_id LIMIT 1);
SET @admin_id := (SELECT id FROM account WHERE username = 'admin' AND status = 'ACTIVE' LIMIT 1);
UPDATE service_item SET slot_interval_minutes = 60, buffer_before_minutes = 10, buffer_after_minutes = 15, minimum_advance_minutes = 0, same_day_booking_allowed = 1, free_cancel_minutes = 1440, reschedule_cutoff_minutes = 720, max_reschedules = 2, booking_notice = 'SC3 synthetic terms', booking_terms_version = booking_terms_version + 1, booking_policy_updated_by = @admin_id WHERE id = @service_id;
INSERT IGNORE INTO staff_schedule (tenant_id, shop_id, staff_id, schedule_date, start_time, end_time, schedule_type, remark)
SELECT st.tenant_id, 1, st.id, '$tomorrow', '09:00:00', '18:00:00', 'WORK', 'SC3 runtime' FROM staff st WHERE st.id = @staff_id;
INSERT INTO member (tenant_id, home_shop_id, shop_id, member_no, global_member_no, name, phone, source, status)
VALUES (1, 1, 1, 'SC3-RUNTIME-1', 'SC3-GLOBAL-1', 'SC3 Member One', '13900003031', 'SC3_RUNTIME', 'ACTIVE'),
       (1, 1, 1, 'SC3-RUNTIME-2', 'SC3-GLOBAL-2', 'SC3 Member Two', '13900003032', 'SC3_RUNTIME', 'ACTIVE');
SET @m1 := (SELECT id FROM member WHERE phone = '13900003031' LIMIT 1);
SET @m2 := (SELECT id FROM member WHERE phone = '13900003032' LIMIT 1);
INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, phone, password_hash, role_code, member_id, status)
SELECT m.tenant_id, 1, 1, 'sc3-member-1', 'SC3 Member One', '13900003031', admin.password_hash, 'MEMBER', m.id, 'ACTIVE' FROM member m JOIN account admin ON admin.username = 'admin' WHERE m.id = @m1;
INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, phone, password_hash, role_code, member_id, status)
SELECT m.tenant_id, 1, 1, 'sc3-member-2', 'SC3 Member Two', '13900003032', admin.password_hash, 'MEMBER', m.id, 'ACTIVE' FROM member m JOIN account admin ON admin.username = 'admin' WHERE m.id = @m2;
INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
SELECT a.tenant_id, a.id, 1, r.id, 'ACTIVE' FROM account a JOIN role_definition r ON r.tenant_id = a.tenant_id AND r.role_code = 'MEMBER' WHERE a.phone IN ('13900003031','13900003032');
"@
    Invoke-MySqlQuery $seed | Out-Null
    $idsQuery = "SELECT CONCAT(ss.service_id, ',', ss.staff_id) FROM staff_service ss JOIN service_item si ON si.id = ss.service_id AND si.status = 'ACTIVE' JOIN staff st ON st.id = ss.staff_id AND st.status = 'ACTIVE' WHERE ss.enabled = 1 ORDER BY ss.service_id, ss.staff_id LIMIT 1;"
    $ids = (Invoke-MySqlQuery $idsQuery | Select-Object -Last 1).Trim().Split(',')
    $serviceId = [long]$ids[0]
    $staffId = [long]$ids[1]

    $adminLogin = Invoke-Api POST "$adminBase/api/v3/auth/admin-login" @{ username = 'admin'; password = 'Face@123' }
    Require-Status $adminLogin 200 'ADMIN login'
    $adminToken = (($adminLogin.Text | ConvertFrom-Json).data.access_token)
    $member1Login = Invoke-Api POST "$clientBase/api/v3/client/identity/password-login" @{ phone = '13900003031'; password = 'Face@123' }
    Require-Status $member1Login 200 'MEMBER 1 login'
    $member1 = ($member1Login.Text | ConvertFrom-Json).data
    $member2Login = Invoke-Api POST "$clientBase/api/v3/client/identity/password-login" @{ phone = '13900003032'; password = 'Face@123' }
    Require-Status $member2Login 200 'MEMBER 2 login'
    $member2 = ($member2Login.Text | ConvertFrom-Json).data

    $availability = Invoke-Api GET "$clientBase/api/v3/open/booking/availability?service_id=$serviceId&staff_id=$staffId&from_date=$tomorrow"
    Require-Status $availability 200 'Seven-day server availability'
    $availabilityData = ($availability.Text | ConvertFrom-Json).data
    if ($availabilityData.days.Count -ne 7 -or $availabilityData.max_booking_date -ne [DateTime]::Today.AddDays(30).ToString('yyyy-MM-dd')) {
        throw 'SC3 availability did not return exactly seven days inside the 30-day horizon.'
    }
    $serverSlot = @($availabilityData.days[0].slots | Where-Object start_at -eq $slot)
    if ($serverSlot.Count -ne 1) { throw "Expected server-generated slot is missing: $slot" }
    Write-Output 'SC3_SERVER_GENERATED_7_DAY_AVAILABILITY=PASS'

    $holdBody = @{ shop_id = 1; service_id = $serviceId; staff_id = $staffId; assignment_mode = 'SPECIFIED'; start_at = $slot; bypass_minimum_advance = $false }
    $request1 = New-ApiRequest POST "$clientBase/api/v3/booking/locks" $holdBody $member1.access_token
    $request2 = New-ApiRequest POST "$clientBase/api/v3/booking/locks" $holdBody $member2.access_token
    $task1 = $script:http.SendAsync($request1)
    $task2 = $script:http.SendAsync($request2)
    $response1 = $task1.GetAwaiter().GetResult()
    $response2 = $task2.GetAwaiter().GetResult()
    $statuses = @([int]$response1.StatusCode, [int]$response2.StatusCode) | Sort-Object
    if (($statuses -join ',') -ne '200,409') {
        $body1 = $response1.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $body2 = $response2.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        & docker @script:composeArgs logs --tail 120 backend
        throw "Concurrent lock statuses mismatch: $($statuses -join ','); body1=$body1; body2=$body2"
    }
    $winnerResponse = if ([int]$response1.StatusCode -eq 200) { $response1 } else { $response2 }
    $winner = ($winnerResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json).data
    $winnerToken = if ([int]$response1.StatusCode -eq 200) { $member1.access_token } else { $member2.access_token }
    $request1.Dispose(); $request2.Dispose(); $response1.Dispose(); $response2.Dispose()
    Write-Output 'SC3_CONCURRENT_SAME_STAFF_SLOT_ONE_SUCCESS=PASS'

    $release = Invoke-Api DELETE "$clientBase/api/v3/booking/locks/$($winner.lock_token)?reason=PAYMENT_TIMEOUT" $null $winnerToken
    Require-Status $release 200 'Payment timeout release'
    $releaseFact = (Invoke-MySqlQuery "SELECT CONCAT(status, ':', release_reason) FROM booking_time_lock WHERE lock_token = '$($winner.lock_token)';" | Select-Object -Last 1).Trim()
    if ($releaseFact -ne 'RELEASED:PAYMENT_TIMEOUT') { throw "Payment timeout did not fully release: $releaseFact" }
    Write-Output 'SC3_PAYMENT_TIMEOUT_FULL_RELEASE=PASS'

    $joinBody = @{ shop_id = 1; service_id = $serviceId; requested_staff_id = $staffId; date_from = $tomorrow; date_to = $tomorrow; time_from = '09:00:00'; time_to = '12:00:00'; flexibility_minutes = 0; accept_other_staff = $false }
    $join1 = Invoke-Api POST "$clientBase/api/v3/booking/waitlist" $joinBody $member1.access_token
    Require-Status $join1 200 'Waitlist member 1 join'
    Start-Sleep -Milliseconds 25
    $join2 = Invoke-Api POST "$clientBase/api/v3/booking/waitlist" $joinBody $member2.access_token
    Require-Status $join2 200 'Waitlist member 2 join'
    $appointmentsBefore = [int]((Invoke-MySqlQuery 'SELECT COUNT(*) FROM appointment;' | Select-Object -Last 1).Trim())
    $match1 = Invoke-Api POST "$adminBase/api/v3/booking/waitlist/process-vacancy?shop_id=1" @{ service_id = $serviceId; staff_id = $staffId; start_at = $slot } $adminToken
    Require-Status $match1 200 'First waitlist vacancy match'
    $match1Data = ($match1.Text | ConvertFrom-Json).data
    if (-not $match1Data.matched -or $match1Data.appointment_created -ne $false) { throw 'First waitlist match contract failed.' }
    $appointmentsAfterMatch = [int]((Invoke-MySqlQuery 'SELECT COUNT(*) FROM appointment;' | Select-Object -Last 1).Trim())
    if ($appointmentsAfterMatch -ne $appointmentsBefore) { throw 'Waitlist matching auto-created an appointment.' }
    Write-Output 'SC3_WAITLIST_ONE_CANDIDATE_NO_AUTO_APPOINTMENT=PASS'

    Invoke-MySqlQuery "UPDATE booking_waitlist SET confirmation_expires_at = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE id = $($match1Data.waitlist_id); UPDATE booking_time_lock SET expires_at = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE waitlist_id = $($match1Data.waitlist_id);" | Out-Null
    $match2 = Invoke-Api POST "$adminBase/api/v3/booking/waitlist/process-vacancy?shop_id=1" @{ service_id = $serviceId; staff_id = $staffId; start_at = $slot } $adminToken
    Require-Status $match2 200 'Second waitlist vacancy match after timeout'
    $match2Data = ($match2.Text | ConvertFrom-Json).data
    if (-not $match2Data.matched -or $match2Data.waitlist_id -eq $match1Data.waitlist_id) { throw 'Waitlist timeout did not proceed to the next candidate.' }
    $expiredFacts = (Invoke-MySqlQuery "SELECT CONCAT(w.status, ':', l.status) FROM booking_waitlist w JOIN booking_time_lock l ON l.id = w.time_lock_id WHERE w.id = $($match1Data.waitlist_id);" | Select-Object -Last 1).Trim()
    if ($expiredFacts -ne 'EXPIRED:EXPIRED') { throw "Waitlist timeout release mismatch: $expiredFacts" }
    Write-Output 'SC3_WAITLIST_TIMEOUT_RELEASE_AND_NEXT_CANDIDATE=PASS'

    $mine2 = Invoke-Api GET "$clientBase/api/v3/booking/waitlist/mine" $null $member2.access_token
    Require-Status $mine2 200 'Member 2 waitlist list'
    $candidate2 = @(($mine2.Text | ConvertFrom-Json).data | Where-Object id -eq $match2Data.waitlist_id)[0]
    $confirm2 = Invoke-Api POST "$clientBase/api/v3/booking/waitlist/$($candidate2.id)/confirm" @{ terms_version = $candidate2.termsVersion; terms_confirmed = $true; member_note = 'SC3 acceptance' } $member2.access_token
    Require-Status $confirm2 200 'Waitlist terms reconfirmation'
    $confirmedFacts = (Invoke-MySqlQuery "SELECT CONCAT(w.status, ':', l.status, ':', a.status) FROM booking_waitlist w JOIN booking_time_lock l ON l.id = w.time_lock_id JOIN appointment a ON a.id = l.appointment_id WHERE w.id = $($candidate2.id);" | Select-Object -Last 1).Trim()
    if ($confirmedFacts -ne 'CONFIRMED:CONVERTED:PENDING') { throw "Waitlist confirmation facts mismatch: $confirmedFacts" }
    Write-Output 'SC3_WAITLIST_RECONFIRM_CREATES_APPOINTMENT=PASS'

    $snapshotCount = [int]((Invoke-MySqlQuery "SELECT COUNT(*) FROM appointment WHERE rule_snapshot_json IS NOT NULL AND terms_confirmed_at IS NOT NULL AND occupied_start_at < start_at AND occupied_end_at > end_at;" | Select-Object -Last 1).Trim())
    if ($snapshotCount -lt 1) { throw 'Confirmed appointment omitted rule/terms/full-occupancy snapshots.' }
    Write-Output 'SC3_TERMS_AND_FULL_OCCUPANCY_SNAPSHOT=PASS'
    Write-Output 'SC3_RUNTIME_ACCEPTANCE=PASS'
}
finally {
    $script:http.Dispose()
    if ($started -and -not $KeepRunning) {
        & docker @script:composeArgs down --volumes --remove-orphans
        if ($LASTEXITCODE -ne 0) { Write-Warning 'SC3 Docker acceptance cleanup failed.' }
        else { Write-Output 'SC3_DOCKER_ACCEPTANCE_CLEANUP=PASS' }
    } elseif ($started) {
        Write-Output "SC3_DOCKER_ACCEPTANCE_KEEP_RUNNING=PASS;PROJECT=$ProjectName"
    }
    foreach ($entry in $originalEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}
