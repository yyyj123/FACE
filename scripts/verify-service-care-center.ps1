param(
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = $env:FACE_ADMIN_PASSWORD,
    [long]$ShopId = 1
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "Set FACE_ADMIN_PASSWORD before running the service-care verification."
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
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 10 -Compress }
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

function New-Key {
    param([string]$Prefix)
    return "$Prefix-$([guid]::NewGuid().ToString('N'))"
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
            $staff = $resources.data.staff |
                Where-Object { $_.id -eq $schedule.staffId } |
                Select-Object -First 1
            if ($null -eq $staff -or [string]::IsNullOrWhiteSpace([string]$staff.serviceIds)) {
                continue
            }

            foreach ($serviceIdText in ([string]$staff.serviceIds).Split(",")) {
                $serviceId = [long]$serviceIdText
                $service = $resources.data.services |
                    Where-Object { $_.id -eq $serviceId } |
                    Select-Object -First 1
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
                            ServiceId = $serviceId
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
    -Body @{ username = $AdminUsername; password = $AdminPassword }
if ($login.code -ne 0 -or [string]::IsNullOrWhiteSpace($login.data.token)) {
    throw "Login verification failed."
}
$headers = @{ Token = $login.data.token }

$slot = Get-FirstBookableSlot `
    -BaseUrl $chainBase `
    -Headers $headers `
    -ShopId $ShopId `
    -FromDate (Get-Date).Date.AddDays(1)

$created = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/appointments" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        memberId = $slot.MemberId
        staffId = $slot.StaffId
        serviceIds = @($slot.ServiceId)
        startAt = $slot.StartAt
        source = "FRONT_DESK"
        memberNote = "service-care automated verification"
        internalNote = "service lifecycle, care profile and consumption verification"
    }
$appointmentId = [long]$created.data.id
$appointmentVersion = 0

foreach ($targetStatus in @("CONFIRMED", "CHECKED_IN")) {
    $changed = Invoke-Utf8Json `
        -Method "Post" `
        -Uri "$chainBase/api/v2/appointments/$appointmentId/status" `
        -Headers $headers `
        -Body @{
            shopId = $ShopId
            status = $targetStatus
            version = $appointmentVersion
            reason = $null
        }
    $appointmentVersion = [int]$changed.data.version
}

$serviceResources = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/service-records/resources?shopId=$ShopId" `
    -Headers $headers
$ready = $serviceResources.data.readyAppointments |
    Where-Object { $_.id -eq $appointmentId } |
    Select-Object -First 1
if ($null -eq $ready) {
    throw "Checked-in appointment was not exposed to the service center."
}

$started = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/service-records/start" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        appointmentId = $appointmentId
        appointmentVersion = $appointmentVersion
    }
$serviceRecordId = [long]$started.data.id

$serviceResources = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/service-records/resources?shopId=$ShopId" `
    -Headers $headers
$consumable = $serviceResources.data.consumables |
    Where-Object { [decimal]$_.quantityAvailable -ge 1 } |
    Select-Object -First 1
if ($null -eq $consumable) {
    throw "No consumable with available stock was found."
}
$beforeQuantity = [decimal]$consumable.quantityAvailable
$completionKey = New-Key "service-complete"
$completionBody = @{
    shopId = $ShopId
    version = [int]$started.data.version
    serviceSummary = "Automated acceptance: service completed and member stable"
    nextVisitRecommendation = "Review care results in four weeks"
    skinType = "COMBINATION"
    concerns = @("HYDRATION", "BARRIER")
    observations = "Automated care observation: no irritation and stable condition"
    homeCareAdvice = "Use gentle cleansing, hydration and daytime protection"
    nextRecommendedAt = (Get-Date).Date.AddDays(28).ToString("yyyy-MM-dd")
    consumptions = @(
        @{
            locationId = [long]$consumable.locationId
            productId = [long]$consumable.productId
            quantity = 1
            balanceVersion = [int]$consumable.balanceVersion
        }
    )
    idempotencyKey = $completionKey
}
$completed = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/service-records/$serviceRecordId/complete" `
    -Headers $headers `
    -Body $completionBody
if ($completed.data.status -ne "COMPLETED" -or @($completed.data.consumptions).Count -ne 1) {
    throw "Service completion did not persist the care and consumption snapshots."
}

$replayed = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/service-records/$serviceRecordId/complete" `
    -Headers $headers `
    -Body $completionBody
if ([long]$replayed.data.id -ne $serviceRecordId) {
    throw "Completion idempotency replay returned another service record."
}

$afterResources = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/service-records/resources?shopId=$ShopId" `
    -Headers $headers
$afterConsumable = $afterResources.data.consumables |
    Where-Object {
        $_.productId -eq $consumable.productId -and
        $_.locationId -eq $consumable.locationId
    } |
    Select-Object -First 1
if ([decimal]$afterConsumable.quantityAvailable -ne ($beforeQuantity - 1)) {
    throw "Service consumption did not deduct exactly one stock unit."
}

$staleCareStatus = Get-HttpStatus {
    Invoke-Utf8Json `
        -Method "Put" `
        -Uri "$chainBase/api/v2/service-records/$serviceRecordId/care" `
        -Headers $headers `
        -Body @{
            shopId = $ShopId
            careVersion = 999
            serviceSummary = "stale care update"
            concerns = @()
            observations = "stale care update"
        }
}
if ($staleCareStatus -ne 409) {
    throw "Expected stale care update to return 409, got $staleCareStatus."
}

$updated = Invoke-Utf8Json `
    -Method "Put" `
    -Uri "$chainBase/api/v2/service-records/$serviceRecordId/care" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        careVersion = [int]$completed.data.careVersion
        serviceSummary = "Automated acceptance: care record edited"
        nextVisitRecommendation = "Adjust the plan in four weeks"
        skinType = "COMBINATION"
        concerns = @("HYDRATION", "BARRIER", "PROTECTION")
        observations = "Automated care observation: second edit saved"
        homeCareAdvice = "Continue gentle cleansing, hydration and protection"
        nextRecommendedAt = (Get-Date).Date.AddDays(28).ToString("yyyy-MM-dd")
    }
if ($updated.data.observations -notlike "*second edit*") {
    throw "Care record update was not persisted."
}

$history = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/service-records/members/$($slot.MemberId)/history?shopId=$ShopId" `
    -Headers $headers
if ($null -eq ($history.data | Where-Object { $_.id -eq $serviceRecordId } | Select-Object -First 1)) {
    throw "Completed service did not appear in member care history."
}

$order = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/transactions" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        memberId = $slot.MemberId
        appointmentId = $appointmentId
        items = @()
        notes = "service-care linkage automated verification"
    }
$businessDate = (Get-Date).ToString("yyyy-MM-dd")
$orders = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/transactions?shopId=$ShopId&fromDate=$businessDate&toDate=$businessDate&keyword=$($order.data.orderNo)&page=1&pageSize=20" `
    -Headers $headers
$linkedOrder = $orders.data.records |
    Where-Object { $_.id -eq $order.data.id } |
    Select-Object -First 1
if ($null -eq $linkedOrder -or [long]$linkedOrder.serviceRecordId -ne $serviceRecordId) {
    throw "Checkout order was not linked to the completed service record."
}
Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/transactions/$($order.data.id)/void" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        version = [int]$order.data.version
        reason = "automated verification cleanup"
    } | Out-Null

Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/inventory/adjustments" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        locationId = [long]$afterConsumable.locationId
        productId = [long]$afterConsumable.productId
        quantityDelta = 1
        movementType = "ADJUSTMENT"
        version = [int]$afterConsumable.balanceVersion
        idempotencyKey = New-Key "service-stock-restore"
        referenceNo = [string]$started.data.recordNo
        remark = "service-care automated verification stock restore"
    } | Out-Null

$restoredResources = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/service-records/resources?shopId=$ShopId" `
    -Headers $headers
$restored = $restoredResources.data.consumables |
    Where-Object {
        $_.productId -eq $consumable.productId -and
        $_.locationId -eq $consumable.locationId
    } |
    Select-Object -First 1
if ([decimal]$restored.quantityAvailable -ne $beforeQuantity) {
    throw "Verification stock cleanup did not restore the original quantity."
}

$spoofStatus = Get-HttpStatus {
    Invoke-RestMethod `
        -Method Get `
        -Uri "$chainBase/api/v2/service-records?shopId=999&page=1&pageSize=10" `
        -Headers $headers
}
if ($spoofStatus -ne 403) {
    throw "Expected inaccessible shop to return 403, got $spoofStatus."
}

[pscustomobject]@{
    Status = "PASS"
    AppointmentId = $appointmentId
    ServiceRecordId = $serviceRecordId
    ServiceRecordNo = $started.data.recordNo
    CareRecordId = $updated.data.careRecordId
    ConsumptionMovementId = $completed.data.consumptions[0].inventoryMovementId
    LinkedOrderId = $order.data.id
    CompletionReplayId = $replayed.data.id
    StaleCareStatus = $staleCareStatus
    InaccessibleShopStatus = $spoofStatus
    StockBefore = $beforeQuantity
    StockAfterService = $afterConsumable.quantityAvailable
    StockRestored = $restored.quantityAvailable
}
