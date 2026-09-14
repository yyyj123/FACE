param(
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = $env:FACE_ADMIN_PASSWORD,
    [long]$ShopId = 1
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "Set FACE_ADMIN_PASSWORD before running the member-center verification."
}

function Invoke-Utf8Json {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Body
    )

    $parameters = @{
        Method = $Method
        Uri = $Uri
        Headers = $Headers
        ContentType = "application/json; charset=utf-8"
    }
    if ($null -ne $Body) {
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes($Body)
    }
    Invoke-RestMethod @parameters
}

function Get-HttpStatus {
    param([scriptblock]$Action)
    try {
        & $Action | Out-Null
        return 200
    } catch {
        if ($null -ne $_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

$chainBase = "http://127.0.0.1:8090/face-next"
$phone = "13800009009"

$loginJson = '{{"username":"{0}","password":"{1}"}}' -f $AdminUsername, $AdminPassword
$login = Invoke-Utf8Json -Method "Post" -Uri "$chainBase/api/v2/auth/login" -Body $loginJson
if ($login.code -ne 0 -or [string]::IsNullOrWhiteSpace($login.data.token)) {
    throw "Login verification failed."
}
$headers = @{ Token = $login.data.token }

$createJson = '{{"shopId":{0},"name":"\u8fde\u9501\u9a8c\u6536\u4f1a\u5458","phone":"{1}","gender":"\u5973","birthday":"1995-06-18","source":"\u7cfb\u7edf\u9a8c\u6536","notes":"member center automated verification"}}' -f $ShopId, $phone
$created = Invoke-Utf8Json -Method "Post" -Uri "$chainBase/api/v2/members" -Headers $headers -Body $createJson
$memberId = [long]$created.data.id

$active = Invoke-RestMethod -Method Get -Uri "$chainBase/api/v2/members?shopId=$ShopId&keyword=$phone&status=ACTIVE&page=1&pageSize=10" -Headers $headers
$member = $active.data.records | Where-Object { $_.id -eq $memberId } | Select-Object -First 1
if ($null -eq $member) {
    throw "Created member was not returned by the scoped list."
}

$updateJson = '{{"shopId":{0},"version":{1},"name":"\u8fde\u9501\u9a8c\u6536\u4f1a\u5458","phone":"{2}","gender":"\u5973","birthday":"1995-06-18","source":"\u7cfb\u7edf\u9a8c\u6536","notes":"create list update deactivate restore verified"}}' -f $ShopId, $member.version, $phone
$updated = Invoke-Utf8Json -Method "Put" -Uri "$chainBase/api/v2/members/$memberId" -Headers $headers -Body $updateJson

$staleStatus = Get-HttpStatus {
    Invoke-Utf8Json -Method "Put" -Uri "$chainBase/api/v2/members/$memberId" -Headers $headers -Body $updateJson
}
if ($staleStatus -ne 409) {
    throw "Expected stale update to return 409, got $staleStatus."
}

$spoofStatus = Get-HttpStatus {
    Invoke-RestMethod -Method Get -Uri "$chainBase/api/v2/members?shopId=999&status=ACTIVE" -Headers $headers
}
if ($spoofStatus -ne 403) {
    throw "Expected inaccessible shop to return 403, got $spoofStatus."
}

$inactiveJson = '{{"shopId":{0},"status":"INACTIVE"}}' -f $ShopId
Invoke-Utf8Json -Method "Delete" -Uri "$chainBase/api/v2/members/$memberId" -Headers $headers -Body $inactiveJson | Out-Null
$inactive = Invoke-RestMethod -Method Get -Uri "$chainBase/api/v2/members?shopId=$ShopId&keyword=$phone&status=INACTIVE&page=1&pageSize=10" -Headers $headers
if ($inactive.data.total -lt 1) {
    throw "Deactivated member was not returned by the inactive filter."
}

$activeJson = '{{"shopId":{0},"status":"ACTIVE"}}' -f $ShopId
Invoke-Utf8Json -Method "Post" -Uri "$chainBase/api/v2/members/$memberId/restore" -Headers $headers -Body $activeJson | Out-Null
$restored = Invoke-RestMethod -Method Get -Uri "$chainBase/api/v2/members?shopId=$ShopId&keyword=$phone&status=ACTIVE&page=1&pageSize=10" -Headers $headers
if ($restored.data.total -lt 1) {
    throw "Restored member was not returned by the active filter."
}

[pscustomobject]@{
    Status = "PASS"
    MemberId = $memberId
    MemberNo = $restored.data.records[0].memberNo
    CreatedOrLinked = $created.code -eq 0
    UpdatedVersion = $updated.data.version
    StaleUpdateStatus = $staleStatus
    InaccessibleShopStatus = $spoofStatus
    DeactivateRestore = $true
}
