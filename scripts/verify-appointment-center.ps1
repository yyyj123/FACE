param(
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = $env:FACE_ADMIN_PASSWORD,
    [long]$ShopId = 1
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "Set FACE_ADMIN_PASSWORD before running the appointment-center verification."
}

function Invoke-Utf8Json {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [object]$Body
    )

    $parameters = @{
        Method = $Method
        Uri = $Uri
        Headers = $Headers
        ContentType = "application/json; charset=utf-8"
    }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 8 -Compress }
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes($json)
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

function Get-FirstBookableSlot {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [long]$ShopId,
        [datetime]$FromDate,
        [int]$DaysToScan = 30
    )

    for ($dayOffset = 0; $dayOffset -lt $DaysToScan; $dayOffset++) {
        $date = $FromDate.AddDays($dayOffset).ToString("yyyy-MM-dd")
        $resources = Invoke-RestMethod `
            -Method Get `
            -Uri "$BaseUrl/api/v2/appointments/resources?shopId=$ShopId&date=$date" `
            -Headers $Headers

        foreach ($schedule in @($resources.data.schedules | Where-Object { $_.scheduleType -eq "WORK" })) {
            $staff = $resources.data.staff | Where-Object { $_.id -eq $schedule.staffId } | Select-Object -First 1
            if ($null -eq $staff -or [string]::IsNullOrWhiteSpace([string]$staff.serviceIds)) {
                continue
            }

            foreach ($serviceIdText in ([string]$staff.serviceIds).Split(",")) {
                $serviceId = [long]$serviceIdText
                $service = $resources.data.services | Where-Object { $_.id -eq $serviceId } | Select-Object -First 1
                if ($null -eq $service) {
                    continue
                }

                try {
                    $availability = Invoke-RestMethod `
                        -Method Get `
                        -Uri "$BaseUrl/api/v2/appointments/availability?shopId=$ShopId&staffId=$($staff.id)&date=$date&serviceIds=$serviceId" `
                        -Headers $Headers
                    $slot = @($availability.data.slots)[0]
                    if ($null -ne $slot) {
                        return [pscustomobject]@{
                            Date = $date
                            MemberId = [long]$resources.data.members[0].id
                            StaffId = [long]$staff.id
                            StaffName = [string]$staff.name
                            ServiceId = $serviceId
                            ServiceName = [string]$service.name
                            StartAt = [string]$slot.startAt
                        }
                    }
                } catch {
                    if ($null -eq $_.Exception.Response) {
                        throw
                    }
                }
            }
        }
    }

    throw "No bookable appointment slot was found in the next $DaysToScan days."
}

$chainBase = "http://127.0.0.1:8090/face-next"

$login = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/auth/login" `
    -Body @{
        username = $AdminUsername
        password = $AdminPassword
    }
if ($login.code -ne 0 -or [string]::IsNullOrWhiteSpace($login.data.token)) {
    throw "Login verification failed."
}
$headers = @{ Token = $login.data.token }

$firstSlot = Get-FirstBookableSlot `
    -BaseUrl $chainBase `
    -Headers $headers `
    -ShopId $ShopId `
    -FromDate (Get-Date).Date.AddDays(1)

$createBody = @{
    shopId = $ShopId
    memberId = $firstSlot.MemberId
    staffId = $firstSlot.StaffId
    serviceIds = @($firstSlot.ServiceId)
    startAt = $firstSlot.StartAt
    source = "FRONT_DESK"
    memberNote = "appointment center automated verification"
    internalNote = "conflict, reschedule and status flow verification"
}
$created = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/appointments" `
    -Headers $headers `
    -Body $createBody
$appointmentId = [long]$created.data.id

$duplicateStatus = Get-HttpStatus {
    Invoke-Utf8Json `
        -Method "Post" `
        -Uri "$chainBase/api/v2/appointments" `
        -Headers $headers `
        -Body $createBody
}
if ($duplicateStatus -ne 409) {
    throw "Expected duplicate booking to return 409, got $duplicateStatus."
}

$listed = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/appointments?shopId=$ShopId&fromDate=$($firstSlot.Date)&toDate=$($firstSlot.Date)&status=ALL&page=1&pageSize=50" `
    -Headers $headers
$listedAppointment = $listed.data.records | Where-Object { $_.id -eq $appointmentId } | Select-Object -First 1
if ($null -eq $listedAppointment) {
    throw "Created appointment was not returned by the scoped daily list."
}

$secondAvailability = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/appointments/availability?shopId=$ShopId&staffId=$($firstSlot.StaffId)&date=$($firstSlot.Date)&serviceIds=$($firstSlot.ServiceId)" `
    -Headers $headers
$secondSlot = @($secondAvailability.data.slots)[0]
if ($null -eq $secondSlot) {
    throw "No second slot remained for reschedule verification."
}

$rescheduleBody = @{
    shopId = $ShopId
    staffId = $firstSlot.StaffId
    startAt = [string]$secondSlot.startAt
    version = 0
    reason = "automated acceptance reschedule"
}
$rescheduled = Invoke-Utf8Json `
    -Method "Put" `
    -Uri "$chainBase/api/v2/appointments/$appointmentId/reschedule" `
    -Headers $headers `
    -Body $rescheduleBody

$staleStatus = Get-HttpStatus {
    Invoke-Utf8Json `
        -Method "Put" `
        -Uri "$chainBase/api/v2/appointments/$appointmentId/reschedule" `
        -Headers $headers `
        -Body $rescheduleBody
}
if ($staleStatus -ne 409) {
    throw "Expected stale reschedule to return 409, got $staleStatus."
}

$version = [int]$rescheduled.data.version
$statusFlow = @("CONFIRMED", "CHECKED_IN", "IN_SERVICE", "COMPLETED")
foreach ($targetStatus in $statusFlow) {
    $changed = Invoke-Utf8Json `
        -Method "Post" `
        -Uri "$chainBase/api/v2/appointments/$appointmentId/status" `
        -Headers $headers `
        -Body @{
            shopId = $ShopId
            status = $targetStatus
            version = $version
            reason = $null
        }
    $version = [int]$changed.data.version
}

$invalidTransitionStatus = Get-HttpStatus {
    Invoke-Utf8Json `
        -Method "Post" `
        -Uri "$chainBase/api/v2/appointments/$appointmentId/status" `
        -Headers $headers `
        -Body @{
            shopId = $ShopId
            status = "CANCELLED"
            version = $version
            reason = "terminal state must reject cancellation"
        }
}
if ($invalidTransitionStatus -ne 409) {
    throw "Expected terminal-state transition to return 409, got $invalidTransitionStatus."
}

$spoofStatus = Get-HttpStatus {
    Invoke-RestMethod `
        -Method Get `
        -Uri "$chainBase/api/v2/appointments?shopId=999&status=ALL" `
        -Headers $headers
}
if ($spoofStatus -ne 403) {
    throw "Expected inaccessible shop to return 403, got $spoofStatus."
}

[pscustomobject]@{
    Status = "PASS"
    AppointmentId = $appointmentId
    AppointmentNo = $created.data.appointmentNo
    StaffId = $firstSlot.StaffId
    ServiceId = $firstSlot.ServiceId
    OriginalStartAt = $firstSlot.StartAt
    RescheduledStartAt = $rescheduled.data.startAt
    DuplicateBookingStatus = $duplicateStatus
    StaleRescheduleStatus = $staleStatus
    InvalidTransitionStatus = $invalidTransitionStatus
    InaccessibleShopStatus = $spoofStatus
    FinalStatus = "COMPLETED"
    FinalVersion = $version
}
