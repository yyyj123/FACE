param(
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = $env:FACE_ADMIN_PASSWORD,
    [long]$ShopId = 1
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "Set FACE_ADMIN_PASSWORD before running the transaction-center verification."
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

$chainBase = "http://127.0.0.1:8090/face-next"
$today = (Get-Date).ToString("yyyy-MM-dd")

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
    -Uri "$chainBase/api/v2/transactions/resources?shopId=$ShopId" `
    -Headers $headers
$member = $resources.data.members | Where-Object { $_.memberNo -eq "M0001" } | Select-Object -First 1
$service = @($resources.data.services)[0]
if ($null -eq $member -or $null -eq $service) {
    throw "Transaction verification member or service is missing."
}

$beforeActivity = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/transactions/members/$($member.id)/accounts?shopId=$ShopId" `
    -Headers $headers
$beforeBalance = [decimal](
    $beforeActivity.data.accounts |
        Where-Object { $_.accountType -eq "BALANCE" } |
        Select-Object -ExpandProperty balance
)
$beforeLedgerCount = @($beforeActivity.data.ledger).Count

$createBody = @{
    shopId = $ShopId
    memberId = [long]$member.id
    items = @(
        @{
            itemType = "SERVICE"
            referenceId = [long]$service.id
            quantity = 1
            discountAmount = 0
        }
    )
    notes = "transaction center automated verification"
}
$created = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/transactions" `
    -Headers $headers `
    -Body $createBody
$orderId = [long]$created.data.id
$payable = [decimal]$created.data.payableAmount

$paymentKey = "acceptance-payment-$orderId"
$paymentBody = @{
    shopId = $ShopId
    paymentMethod = "BALANCE"
    amount = $payable
    version = 0
    idempotencyKey = $paymentKey
}
$payment = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/transactions/$orderId/payments" `
    -Headers $headers `
    -Body $paymentBody
$paymentReplay = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/transactions/$orderId/payments" `
    -Headers $headers `
    -Body $paymentBody
if ([long]$payment.data.id -ne [long]$paymentReplay.data.id) {
    throw "Payment idempotency did not return the original transaction."
}

$stalePaymentStatus = Get-HttpStatus {
    Invoke-Utf8Json `
        -Method "Post" `
        -Uri "$chainBase/api/v2/transactions/$orderId/payments" `
        -Headers $headers `
        -Body @{
            shopId = $ShopId
            paymentMethod = "CASH"
            amount = $payable
            version = 0
            idempotencyKey = "stale-payment-$orderId"
        }
}
if ($stalePaymentStatus -ne 409) {
    throw "Expected stale payment to return 409, got $stalePaymentStatus."
}

$afterPaymentActivity = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/transactions/members/$($member.id)/accounts?shopId=$ShopId" `
    -Headers $headers
$afterPaymentBalance = [decimal](
    $afterPaymentActivity.data.accounts |
        Where-Object { $_.accountType -eq "BALANCE" } |
        Select-Object -ExpandProperty balance
)
if ($afterPaymentBalance -ne ($beforeBalance - $payable)) {
    throw "Balance payment did not debit the member account correctly."
}

$refundKey = "acceptance-refund-$orderId"
$refundBody = @{
    shopId = $ShopId
    paymentId = [long]$payment.data.id
    amount = $payable
    reason = "automated full refund verification"
    idempotencyKey = $refundKey
}
$refund = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/transactions/$orderId/refunds" `
    -Headers $headers `
    -Body $refundBody
$refundReplay = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/transactions/$orderId/refunds" `
    -Headers $headers `
    -Body $refundBody
if ([long]$refund.data.id -ne [long]$refundReplay.data.id) {
    throw "Refund idempotency did not return the original request."
}

$decisionBody = @{
    shopId = $ShopId
    action = "APPROVE"
    version = 0
    decisionNote = "automated approval"
}
$approved = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/transactions/refunds/$($refund.data.id)/decision" `
    -Headers $headers `
    -Body $decisionBody
if ($approved.data.status -ne "SUCCESS") {
    throw "Approved refund did not reach SUCCESS."
}

$staleDecisionStatus = Get-HttpStatus {
    Invoke-Utf8Json `
        -Method "Post" `
        -Uri "$chainBase/api/v2/transactions/refunds/$($refund.data.id)/decision" `
        -Headers $headers `
        -Body $decisionBody
}
if ($staleDecisionStatus -ne 409) {
    throw "Expected stale refund decision to return 409, got $staleDecisionStatus."
}

$afterRefundActivity = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/transactions/members/$($member.id)/accounts?shopId=$ShopId" `
    -Headers $headers
$afterRefundBalance = [decimal](
    $afterRefundActivity.data.accounts |
        Where-Object { $_.accountType -eq "BALANCE" } |
        Select-Object -ExpandProperty balance
)
if ($afterRefundBalance -ne $beforeBalance) {
    throw "Balance refund did not restore the member account."
}
$afterLedgerCount = @($afterRefundActivity.data.ledger).Count
if ($afterLedgerCount -lt ($beforeLedgerCount + 2)) {
    throw "Payment and refund ledger entries were not both recorded."
}

$listed = Invoke-RestMethod `
    -Method Get `
    -Uri "$chainBase/api/v2/transactions?shopId=$ShopId&fromDate=$today&toDate=$today&status=ALL&keyword=$($created.data.orderNo)" `
    -Headers $headers
$listedOrder = $listed.data.records | Where-Object { $_.id -eq $orderId } | Select-Object -First 1
if ($null -eq $listedOrder -or $listedOrder.status -ne "REFUNDED") {
    throw "Refunded order was not returned with the expected status."
}

$voidCreated = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/transactions" `
    -Headers $headers `
    -Body $createBody
$voided = Invoke-Utf8Json `
    -Method "Post" `
    -Uri "$chainBase/api/v2/transactions/$($voidCreated.data.id)/void" `
    -Headers $headers `
    -Body @{
        shopId = $ShopId
        version = 0
        reason = "automated void verification"
    }
if ($voided.data.status -ne "VOID") {
    throw "Unpaid order did not become VOID."
}

$spoofStatus = Get-HttpStatus {
    Invoke-RestMethod `
        -Method Get `
        -Uri "$chainBase/api/v2/transactions?shopId=999&status=ALL" `
        -Headers $headers
}
if ($spoofStatus -ne 403) {
    throw "Expected inaccessible shop to return 403, got $spoofStatus."
}

[pscustomobject]@{
    Status = "PASS"
    OrderId = $orderId
    OrderNo = $created.data.orderNo
    PaymentId = $payment.data.id
    RefundId = $refund.data.id
    PayableAmount = $payable
    BalanceBefore = $beforeBalance
    BalanceAfterRefund = $afterRefundBalance
    LedgerEntriesAdded = $afterLedgerCount - $beforeLedgerCount
    PaymentIdempotent = $true
    RefundIdempotent = $true
    StalePaymentStatus = $stalePaymentStatus
    StaleDecisionStatus = $staleDecisionStatus
    InaccessibleShopStatus = $spoofStatus
    FinalOrderStatus = $listedOrder.status
    VoidOrderStatus = $voided.data.status
}
