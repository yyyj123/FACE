param(
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = $env:FACE_ADMIN_PASSWORD,
    [long]$ShopId = 1
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "Set FACE_ADMIN_PASSWORD before running the inventory-center verification."
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

function New-Key {
    param([string]$Prefix)
    return "$Prefix-$([guid]::NewGuid().ToString('N'))"
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

$resources = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/inventory/resources?shopId=$ShopId" `
    -Headers $headers
$productOption = @($resources.data.products)[0]
$mainLocation = $resources.data.locations |
    Where-Object { $_.shopId -eq $ShopId -and $_.locationType -eq "SHOP" } |
    Select-Object -First 1
$careLocation = $resources.data.locations |
    Where-Object { $_.shopId -eq $ShopId -and $_.locationType -eq "ROOM" } |
    Select-Object -First 1
if ($null -eq $productOption -or $null -eq $mainLocation -or $null -eq $careLocation) {
    throw "Inventory verification requires one product, a main warehouse, and a care room."
}
$productId = [long]$productOption.id
$sku = [string]$productOption.sku

function Get-ProductState {
    $page = Invoke-RestMethod `
        -Method Get `
        -Uri "$chainBase/api/v2/inventory?shopId=$ShopId&keyword=$sku&page=1&pageSize=10" `
        -Headers $headers
    $product = $page.data.records |
        Where-Object { $_.id -eq $productId } |
        Select-Object -First 1
    if ($null -eq $product) {
        throw "Verification product disappeared from inventory."
    }
    return $product
}

function Get-LocationBalance {
    param(
        [object]$Product,
        [long]$LocationId
    )
    $balance = $Product.locations |
        Where-Object { $_.locationId -eq $LocationId } |
        Select-Object -First 1
    if ($null -eq $balance) {
        throw "Inventory balance is missing for location $LocationId."
    }
    return $balance
}

$before = Get-ProductState
$beforeMain = Get-LocationBalance -Product $before -LocationId ([long]$mainLocation.id)
$beforeCare = Get-LocationBalance -Product $before -LocationId ([long]$careLocation.id)
$beforeMainOnHand = [decimal]$beforeMain.quantityOnHand
$beforeCareOnHand = [decimal]$beforeCare.quantityOnHand
$beforeMainReserved = [decimal]$beforeMain.quantityReserved
$beforeCareReserved = [decimal]$beforeCare.quantityReserved

$inboundKey = New-Key "acceptance-inventory-in"
$inboundBody = @{
    shopId = $ShopId
    locationId = [long]$mainLocation.id
    productId = $productId
    quantityDelta = 2
    movementType = "MANUAL_IN"
    version = [int]$beforeMain.version
    idempotencyKey = $inboundKey
    referenceNo = "ACCEPTANCE-IN"
    remark = "inventory center automated inbound verification"
}
$inbound = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/inventory/adjustments" `
    -Headers $headers `
    -Body $inboundBody
$inboundReplay = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/inventory/adjustments" `
    -Headers $headers `
    -Body $inboundBody
if ([long]$inbound.data.id -ne [long]$inboundReplay.data.id) {
    throw "Inventory adjustment idempotency did not return the original movement."
}

$staleAdjustmentStatus = Get-HttpStatus {
    Invoke-Utf8Json `
        -Method Post `
        -Uri "$chainBase/api/v2/inventory/adjustments" `
        -Headers $headers `
        -Body @{
            shopId = $ShopId
            locationId = [long]$mainLocation.id
            productId = $productId
            quantityDelta = 1
            movementType = "MANUAL_IN"
            version = [int]$beforeMain.version
            idempotencyKey = (New-Key "acceptance-inventory-stale")
            remark = "stale version must be rejected"
        }
}
if ($staleAdjustmentStatus -ne 409) {
    throw "Expected stale inventory adjustment to return 409, got $staleAdjustmentStatus."
}

$afterInbound = Get-ProductState
$afterInboundMain = Get-LocationBalance -Product $afterInbound -LocationId ([long]$mainLocation.id)
if ([decimal]$afterInboundMain.quantityOnHand -ne ($beforeMainOnHand + 2)) {
    throw "Inbound adjustment did not add two units."
}

$insufficientStatus = Get-HttpStatus {
    Invoke-Utf8Json `
        -Method Post `
        -Uri "$chainBase/api/v2/inventory/adjustments" `
        -Headers $headers `
        -Body @{
            shopId = $ShopId
            locationId = [long]$mainLocation.id
            productId = $productId
            quantityDelta = -999999
            movementType = "MANUAL_OUT"
            version = [int]$afterInboundMain.version
            idempotencyKey = (New-Key "acceptance-inventory-insufficient")
            remark = "insufficient stock must be rejected"
        }
}
if ($insufficientStatus -ne 409) {
    throw "Expected insufficient stock to return 409, got $insufficientStatus."
}

$transferKey = New-Key "acceptance-transfer"
$transferBody = @{
    sourceShopId = $ShopId
    sourceLocationId = [long]$mainLocation.id
    destinationLocationId = [long]$careLocation.id
    productId = $productId
    quantity = 1
    version = [int]$afterInboundMain.version
    idempotencyKey = $transferKey
    remark = "automated approved transfer verification"
}
$transfer = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/inventory/transfers" `
    -Headers $headers `
    -Body $transferBody
$transferReplay = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/inventory/transfers" `
    -Headers $headers `
    -Body $transferBody
if ([long]$transfer.data.id -ne [long]$transferReplay.data.id) {
    throw "Transfer idempotency did not return the original transfer."
}

$afterReservation = Get-ProductState
$reservedMain = Get-LocationBalance -Product $afterReservation -LocationId ([long]$mainLocation.id)
if ([decimal]$reservedMain.quantityReserved -ne ($beforeMainReserved + 1)) {
    throw "Pending transfer did not reserve source stock."
}

$approved = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/inventory/transfers/$($transfer.data.id)/decision" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        action = "APPROVE"
        version = 0
        decisionNote = "automated approval"
    }
if ($approved.data.status -ne "APPROVED") {
    throw "Approved transfer did not reach APPROVED."
}

$staleDecisionStatus = Get-HttpStatus {
    Invoke-Utf8Json `
        -Method Post `
        -Uri "$chainBase/api/v2/inventory/transfers/$($transfer.data.id)/decision" `
        -Headers $headers `
        -Body @{
            shopId = $ShopId
            action = "APPROVE"
            version = 0
            decisionNote = "stale decision"
        }
}
if ($staleDecisionStatus -ne 409) {
    throw "Expected stale transfer decision to return 409, got $staleDecisionStatus."
}

$afterApproved = Get-ProductState
$approvedMain = Get-LocationBalance -Product $afterApproved -LocationId ([long]$mainLocation.id)
$approvedCare = Get-LocationBalance -Product $afterApproved -LocationId ([long]$careLocation.id)
if (
    [decimal]$approvedMain.quantityOnHand -ne ($beforeMainOnHand + 1) -or
    [decimal]$approvedCare.quantityOnHand -ne ($beforeCareOnHand + 1)
) {
    throw "Approved transfer did not move stock between locations."
}

$rejectTransfer = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/inventory/transfers" `
    -Headers $headers `
    -Body @{
        sourceShopId = $ShopId
        sourceLocationId = [long]$mainLocation.id
        destinationLocationId = [long]$careLocation.id
        productId = $productId
        quantity = 1
        version = [int]$approvedMain.version
        idempotencyKey = (New-Key "acceptance-transfer-reject")
        remark = "automated rejected transfer verification"
    }
$rejected = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/inventory/transfers/$($rejectTransfer.data.id)/decision" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        action = "REJECT"
        version = 0
        decisionNote = "automated rejection"
    }
if ($rejected.data.status -ne "REJECTED") {
    throw "Rejected transfer did not reach REJECTED."
}

$transactionResources = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/transactions/resources?shopId=$ShopId" `
    -Headers $headers
$member = $transactionResources.data.members |
    Where-Object { $_.memberNo -eq "M0001" } |
    Select-Object -First 1
$transactionProduct = $transactionResources.data.products |
    Where-Object { $_.id -eq $productId } |
    Select-Object -First 1
if ($null -eq $member -or $null -eq $transactionProduct) {
    throw "Order deduction verification resources are missing."
}
$order = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/transactions" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        memberId = [long]$member.id
        items = @(
            @{
                itemType = "PRODUCT"
                referenceId = $productId
                quantity = 1
                discountAmount = 0
            }
        )
        notes = "inventory deduction automated verification"
    }
$payment = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/transactions/$($order.data.id)/payments" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        paymentMethod = "CASH"
        amount = [decimal]$order.data.payableAmount
        version = 0
        idempotencyKey = (New-Key "acceptance-inventory-payment")
    }
if ($payment.data.status -ne "SUCCESS") {
    throw "Product order payment did not succeed."
}

$movements = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/inventory/movements?shopId=$ShopId&productId=$productId&movementType=SALE_OUT&page=1&pageSize=100" `
    -Headers $headers
$saleMovement = $movements.data.records |
    Where-Object { $_.orderId -eq [long]$order.data.id } |
    Select-Object -First 1
if ($null -eq $saleMovement -or [decimal]$saleMovement.quantityDelta -ne -1) {
    throw "Paid product order did not create a SALE_OUT movement."
}

$stockBeforeFailedPayment = Get-ProductState
$failedPaymentMain = Get-LocationBalance `
    -Product $stockBeforeFailedPayment `
    -LocationId ([long]$mainLocation.id)
$oversellQuantity = [int][Math]::Ceiling([double]$failedPaymentMain.quantityAvailable) + 10
$oversellOrder = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/transactions" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        memberId = [long]$member.id
        items = @(
            @{
                itemType = "PRODUCT"
                referenceId = $productId
                quantity = $oversellQuantity
                discountAmount = 0
            }
        )
        notes = "inventory oversell rollback verification"
    }
$oversellPaymentStatus = Get-HttpStatus {
    Invoke-Utf8Json `
        -Method Post `
        -Uri "$chainBase/api/v2/transactions/$($oversellOrder.data.id)/payments" `
        -Headers $headers `
        -Body @{
            shopId = $ShopId
            paymentMethod = "CASH"
            amount = [decimal]$oversellOrder.data.payableAmount
            version = 0
            idempotencyKey = (New-Key "acceptance-inventory-oversell")
        }
}
if ($oversellPaymentStatus -ne 409) {
    throw "Expected out-of-stock order payment to return 409, got $oversellPaymentStatus."
}
$today = (Get-Date).ToString("yyyy-MM-dd")
$oversellListed = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/transactions?shopId=$ShopId&fromDate=$today&toDate=$today&status=ALL&keyword=$($oversellOrder.data.orderNo)" `
    -Headers $headers
$oversellListedOrder = $oversellListed.data.records |
    Where-Object { $_.id -eq [long]$oversellOrder.data.id } |
    Select-Object -First 1
if (
    $null -eq $oversellListedOrder -or
    $oversellListedOrder.status -ne "UNPAID" -or
    @($oversellListedOrder.payments).Count -ne 0
) {
    throw "Failed out-of-stock payment did not roll back the order and payment transaction."
}
Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/transactions/$($oversellOrder.data.id)/void" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        version = 0
        reason = "cleanup after out-of-stock rollback verification"
    } | Out-Null

$beforeReverse = Get-ProductState
$reverseCare = Get-LocationBalance -Product $beforeReverse -LocationId ([long]$careLocation.id)
$reverseTransfer = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/inventory/transfers" `
    -Headers $headers `
    -Body @{
        sourceShopId = $ShopId
        sourceLocationId = [long]$careLocation.id
        destinationLocationId = [long]$mainLocation.id
        productId = $productId
        quantity = 1
        version = [int]$reverseCare.version
        idempotencyKey = (New-Key "acceptance-transfer-cleanup")
        remark = "restore location distribution after verification"
    }
$reverseApproved = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/inventory/transfers/$($reverseTransfer.data.id)/decision" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        action = "APPROVE"
        version = 0
        decisionNote = "automated cleanup"
    }
if ($reverseApproved.data.status -ne "APPROVED") {
    throw "Reverse transfer cleanup failed."
}

$beforeCleanup = Get-ProductState
$cleanupMain = Get-LocationBalance -Product $beforeCleanup -LocationId ([long]$mainLocation.id)
$cleanup = Invoke-Utf8Json `
    -Method Post `
    -Uri "$chainBase/api/v2/inventory/adjustments" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        locationId = [long]$mainLocation.id
        productId = $productId
        quantityDelta = -1
        movementType = "MANUAL_OUT"
        version = [int]$cleanupMain.version
        idempotencyKey = (New-Key "acceptance-inventory-cleanup")
        referenceNo = "ACCEPTANCE-CLEANUP"
        remark = "restore total stock after automated verification"
    }

$final = Get-ProductState
$finalMain = Get-LocationBalance -Product $final -LocationId ([long]$mainLocation.id)
$finalCare = Get-LocationBalance -Product $final -LocationId ([long]$careLocation.id)
if (
    [decimal]$finalMain.quantityOnHand -ne $beforeMainOnHand -or
    [decimal]$finalCare.quantityOnHand -ne $beforeCareOnHand -or
    [decimal]$finalMain.quantityReserved -ne $beforeMainReserved -or
    [decimal]$finalCare.quantityReserved -ne $beforeCareReserved
) {
    throw "Verification cleanup did not restore the original stock balances."
}

$spoofStatus = Get-HttpStatus {
    Invoke-RestMethod `
        -Method Get `
        -Uri "$chainBase/api/v2/inventory?shopId=999" `
        -Headers $headers
}
if ($spoofStatus -ne 403) {
    throw "Expected inaccessible shop to return 403, got $spoofStatus."
}

[pscustomobject]@{
    Status = "PASS"
    ProductId = $productId
    Sku = $sku
    AdjustmentMovementId = $inbound.data.id
    AdjustmentIdempotent = $true
    StaleAdjustmentStatus = $staleAdjustmentStatus
    InsufficientStockStatus = $insufficientStatus
    ApprovedTransferId = $transfer.data.id
    RejectedTransferId = $rejectTransfer.data.id
    TransferIdempotent = $true
    StaleDecisionStatus = $staleDecisionStatus
    OrderId = $order.data.id
    SaleMovementId = $saleMovement.id
    OversellPaymentStatus = $oversellPaymentStatus
    OversellPaymentRolledBack = $true
    InaccessibleShopStatus = $spoofStatus
    FinalMainStock = $finalMain.quantityOnHand
    FinalCareStock = $finalCare.quantityOnHand
    BalancesRestored = $true
}
