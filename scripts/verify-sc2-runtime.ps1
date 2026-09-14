param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.docker.test'),
    [string]$ProjectName = 'face-sc2-acceptance',
    [int]$BackendPort = 8590,
    [int]$AdminPort = 8581,
    [int]$ClientPort = 8582,
    [int]$TechnicianPort = 8583
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

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & docker @script:composeArgs @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose command failed: $($Arguments -join ' ')"
    }
}

function Invoke-MySqlQuery {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $result = $Sql | & docker @script:composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot face_salon'
    if ($LASTEXITCODE -ne 0) { throw 'SC2 MySQL verification query failed.' }
    return @($result)
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [object]$Body,
        [string]$Token
    )
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method), $Uri
    )
    if ($Token) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $request.Content = [System.Net.Http.StringContent]::new(
            $json, [System.Text.Encoding]::UTF8, 'application/json'
        )
    }
    $response = $script:http.SendAsync($request).GetAwaiter().GetResult()
    return [pscustomobject]@{
        Status = [int]$response.StatusCode
        Text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    }
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

    $homeResponse = Invoke-Api GET "$clientBase/api/v3/open/content/home"
    Require-Status $homeResponse 200 'Visitor home content'
    $services = Invoke-Api GET "$clientBase/api/v2/client/public/services"
    Require-Status $services 200 'Visitor service catalog'
    $staff = Invoke-Api GET "$clientBase/api/v2/client/public/staff"
    Require-Status $staff 200 'Visitor staff profiles'
    $visitorAdmin = Invoke-Api GET "$adminBase/api/v3/admin-accounts"
    Require-Status $visitorAdmin 401 'Visitor administrator boundary'
    Write-Output 'SC2_VISITOR_MATRIX=PASS'

    $sms = Invoke-Api POST "$clientBase/api/v3/client/identity/sms/request" @{
        phone = '13900002026'; purpose = 'REGISTER_LOGIN'
    }
    Require-Status $sms 503 'Production SMS adapter'
    if ($sms.Text -match '888888|demo_code') {
        throw 'Production SMS response exposed the DEMO verification code.'
    }
    Write-Output 'SC2_PRODUCTION_DEMO_CODE_REJECTION=PASS'

    $superLogin = Invoke-Api POST "$adminBase/api/v3/auth/admin-login" @{
        username = 'admin'; password = 'Face@123'
    }
    Require-Status $superLogin 200 'SUPER_ADMIN login'
    $superToken = (($superLogin.Text | ConvertFrom-Json).data.access_token)
    if (-not $superToken) { throw 'SUPER_ADMIN login returned no access token.' }
    $superList = Invoke-Api GET "$adminBase/api/v3/admin-accounts" $null $superToken
    Require-Status $superList 200 'SUPER_ADMIN account management'

    $adminName = "sc2_admin_$($ProjectName -replace '[^a-z0-9]', '_')"
    $createAdmin = Invoke-Api POST "$adminBase/api/v3/admin-accounts" @{
        username = $adminName; display_name = 'SC2 Admin'; password = 'Face@123'; role = 'ADMIN'
    } $superToken
    Require-Status $createAdmin 200 'Create ADMIN account'
    $adminLogin = Invoke-Api POST "$adminBase/api/v3/auth/admin-login" @{
        username = $adminName; password = 'Face@123'
    }
    Require-Status $adminLogin 200 'ADMIN login'
    $adminToken = (($adminLogin.Text | ConvertFrom-Json).data.access_token)
    $adminContent = Invoke-Api GET "$adminBase/api/v3/content" $null $adminToken
    Require-Status $adminContent 200 'ADMIN content access'
    $adminAccounts = Invoke-Api GET "$adminBase/api/v3/admin-accounts" $null $adminToken
    Require-Status $adminAccounts 403 'ADMIN account-management boundary'
    Write-Output 'SC2_ADMIN_SUPER_ADMIN_MATRIX=PASS'

    $draft = Invoke-Api POST "$adminBase/api/v3/content" @{
        content_type = 'ANNOUNCEMENT'; title = 'SC2 runtime lifecycle'; summary = 'synthetic acceptance content'
        body = '{}'; image_url = ''; target_type = ''; target_value = ''; sort_order = 99
    } $adminToken
    Require-Status $draft 200 'Content draft creation'
    $content = ($draft.Text | ConvertFrom-Json).data
    $scheduled = Invoke-Api POST "$adminBase/api/v3/content/$($content.id)/status" @{
        version = $content.version; status = 'SCHEDULED'; scheduled_at = '2030-01-01T00:00:00Z'
    } $adminToken
    Require-Status $scheduled 200 'Content schedule transition'
    $content = ($scheduled.Text | ConvertFrom-Json).data
    $published = Invoke-Api POST "$adminBase/api/v3/content/$($content.id)/status" @{
        version = $content.version; status = 'PUBLISHED'; scheduled_at = $null
    } $adminToken
    Require-Status $published 200 'Content publish transition'
    $content = ($published.Text | ConvertFrom-Json).data
    $offline = Invoke-Api POST "$adminBase/api/v3/content/$($content.id)/status" @{
        version = $content.version; status = 'OFFLINE'; scheduled_at = $null
    } $adminToken
    Require-Status $offline 200 'Content offline transition'
    Write-Output 'SC2_CONTENT_LIFECYCLE_DRAFT_SCHEDULED_PUBLISHED_OFFLINE=PASS'

    $memberSeedSql = @"
SET @member_id := (SELECT m.id FROM member m LEFT JOIN account existing ON existing.member_id = m.id WHERE existing.id IS NULL ORDER BY m.id LIMIT 1);
INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, phone, password_hash, role_code, member_id, status)
SELECT m.tenant_id, COALESCE(m.home_shop_id, 1), COALESCE(m.home_shop_id, 1), CONCAT('sc2-member-', m.id), 'SC2 Member', '13900002026', admin.password_hash, 'MEMBER', m.id, 'ACTIVE'
FROM member m JOIN account admin ON admin.tenant_id = m.tenant_id AND admin.username = 'admin'
WHERE m.id = @member_id;
INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
SELECT a.tenant_id, a.id, a.home_shop_id, r.id, 'ACTIVE'
FROM account a JOIN role_definition r ON r.tenant_id = a.tenant_id AND r.role_code = 'MEMBER'
WHERE a.phone = '13900002026';
"@
    Invoke-MySqlQuery $memberSeedSql | Out-Null
    $memberCount = (Invoke-MySqlQuery "SELECT COUNT(*) FROM account WHERE tenant_id = 1 AND phone = '13900002026' AND role_code = 'MEMBER';" | Select-Object -Last 1).Trim()
    if ($memberCount -ne '1') { throw 'Historical member identity attachment failed.' }
    $memberLogin = Invoke-Api POST "$clientBase/api/v3/client/identity/password-login" @{
        phone = '13900002026'; password = 'Face@123'
    }
    Require-Status $memberLogin 200 'MEMBER phone/password login'
    $memberData = ($memberLogin.Text | ConvertFrom-Json).data
    if (-not $memberData.account_id -or -not $memberData.member_id) {
        throw 'MEMBER identity response omitted account_id or member_id.'
    }
    $memberMe = Invoke-Api GET "$clientBase/api/v2/client/me" $null $memberData.access_token
    Require-Status $memberMe 200 'MEMBER self profile'
    $memberAdmin = Invoke-Api GET "$adminBase/api/v3/admin-accounts" $null $memberData.access_token
    Require-Status $memberAdmin 403 'MEMBER administrator boundary'
    $invalidAppointment = Invoke-Api POST "$clientBase/api/v2/client/appointments" @{
        shopId = 999; memberId = $memberData.member_id; staffId = 1; serviceIds = @(1)
        resourceIds = @(); startAt = '2030-01-01T10:00:00'; source = 'ONLINE'; memberNote = 'SC2 boundary'; internalNote = ''
    } $memberData.access_token
    Require-Status $invalidAppointment 400 'Invalid single-shop appointment'
    Write-Output 'SC2_MEMBER_MATRIX_AND_HISTORICAL_MATCH=PASS'
    Write-Output 'SC2_INVALID_DEFAULT_SHOP_APPOINTMENT_REJECTION=PASS'

    $visibleRoles = (Invoke-MySqlQuery "SELECT GROUP_CONCAT(DISTINCT role_code ORDER BY role_code) FROM role_definition WHERE role_code IN ('ADMIN','SUPER_ADMIN');" | Select-Object -Last 1).Trim()
    if ($visibleRoles -ne 'ADMIN,SUPER_ADMIN') { throw "Visible administrator roles mismatch: $visibleRoles" }
    $phoneIndex = (Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'account' AND index_name = 'uk_account_tenant_phone';" | Select-Object -Last 1).Trim()
    if ($phoneIndex -ne '2') { throw "Tenant-phone unique index column count mismatch: $phoneIndex" }
    Write-Output 'SC2_VISIBLE_ADMIN_ROLES=ADMIN,SUPER_ADMIN'
    Write-Output 'SC2_TENANT_PHONE_UNIQUENESS=PASS'
    Write-Output 'SC2_RUNTIME_ACCEPTANCE=PASS'
}
finally {
    $script:http.Dispose()
    if ($started) {
        & docker @script:composeArgs down --volumes --remove-orphans
        if ($LASTEXITCODE -ne 0) {
            Write-Warning 'SC2 Docker acceptance cleanup failed.'
        } else {
            Write-Output 'SC2_DOCKER_ACCEPTANCE_CLEANUP=PASS'
        }
    }
    foreach ($entry in $originalEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}
