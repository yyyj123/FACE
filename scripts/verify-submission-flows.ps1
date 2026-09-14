$ErrorActionPreference = 'Stop'

$baseUrl = 'http://127.0.0.1:8080/face/'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$marker = 'FACE-SUBMIT-20260722'
$ownerToken = 'face-owner-regression-20260722'
$memberToken = 'face-member-regression-20260722'
$results = New-Object System.Collections.Generic.List[object]

function Invoke-Sql([string]$sql) {
    $output = & $mysql '--protocol=TCP' '--host=127.0.0.1' '--port=3308' '--user=face_app' '--password=FaceDb@2026!' '--database=face_salon' '--batch' '--raw' '--skip-column-names' "--execute=$sql"
    if ($LASTEXITCODE -ne 0) { throw "SQL failed: $sql" }
    return $output
}

function Invoke-Face([string]$method, [string]$path, [object]$body, [string]$token = $ownerToken) {
    $parameters = @{
        Uri = $baseUrl + $path
        Method = $method
        Headers = @{ Token = $token }
        TimeoutSec = 10
    }
    if ($null -ne $body) {
        $json = if ($body -is [string] -and $body.TrimStart().StartsWith('[')) { $body } else { $body | ConvertTo-Json -Depth 12 -Compress }
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }
    try {
        $response = Invoke-RestMethod @parameters
    } catch {
        $details = $_.Exception.Message
        if ($_.Exception.Response) {
            $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
            $details = $reader.ReadToEnd()
        }
        throw "$method $path returned HTTP error: $details"
    }
    if ($response.code -ne 0) { throw "$method $path failed: $($response.msg)" }
    return $response
}

function Add-Result([string]$area, [string]$flow) {
    $results.Add([pscustomobject]@{ Area = $area; Flow = $flow; Status = 'PASS' })
}

$cases = @(
    @{ table='config'; key='name'; path='config'; body=@{name=$marker; value='initial'}; update=@{value='updated'} },
    @{ table='fuwufenlei'; key='fuwufenlei'; path='fuwufenlei'; body=@{fuwufenlei=$marker}; update=@{fuwufenlei="$marker-U"} },
    @{ table='guzhangfenlei'; key='guzhangfenlei'; path='guzhangfenlei'; body=@{guzhangfenlei=$marker}; update=@{guzhangfenlei="$marker-U"} },
    @{ table='pinpaixinxi'; key='pinpai'; path='pinpaixinxi'; body=@{pinpai=$marker}; update=@{pinpai="$marker-U"} },
    @{ table='xinnengyuanqiche'; key='qichexinghao'; path='xinnengyuanqiche'; body=@{qichexinghao=$marker; qicheleixing='package'; jiage=99}; update=@{jiage=109} },
    @{ table='shouhoufuwu'; key='fuwumingcheng'; path='shouhoufuwu'; body=@{fuwumingcheng=$marker; weixiuzhanghao='audit-tech'; jiage=88}; update=@{jiage=98} },
    @{ table='peijianxinxi'; key='peijianbianhao'; path='peijianxinxi'; body=@{peijianbianhao=$marker; peijianmingcheng='audit-product'; peijianzhonglei='supply'; pinpai='FACE'; shuliang=20; shoujia=10}; update=@{shuliang=19} },
    @{ table='peijianchuku'; key='chukubianhao'; path='peijianchuku'; body=@{chukubianhao=$marker; peijianmingcheng='audit-product'; peijianzhonglei='supply'; shuliang=1; shoujia=10; zongjia=10}; update=@{beizhu='updated'} },
    @{ table='guzhangpaicha'; key='guzhangmingcheng'; path='guzhangpaicha'; body=@{guzhangmingcheng=$marker; guzhangfenlei='care'}; update=@{paichabujian='face'} },
    @{ table='weixiuziliao'; key='ziliaomingcheng'; path='weixiuziliao'; body=@{ziliaomingcheng=$marker}; update=@{ziliaoneirong='updated'} },
    @{ table='fuwuyuyue'; key='yuyuebianhao'; path='fuwuyuyue'; body=@{yuyuebianhao=$marker; chepaihao='visit'; fuwumingcheng='audit-appointment'}; update=@{shhf='updated'} },
    @{ table='weixiujilu'; key='weixiubianhao'; path='weixiujilu'; body=@{weixiubianhao=$marker; fuwumingcheng='audit-service'}; update=@{weixiushuoming='updated'} },
    @{ table='pingjiafankui'; key='pingjiabianhao'; path='pingjiafankui'; body=@{pingjiabianhao=$marker; manyichengdu='satisfied'}; update=@{pingjiafankui='updated'} }
)

try {
    Invoke-Sql "UPDATE token SET token='$ownerToken', expiratedtime=DATE_ADD(NOW(), INTERVAL 2 HOUR) WHERE userid=1 AND role='OWNER'; UPDATE token SET token='$memberToken', expiratedtime=DATE_ADD(NOW(), INTERVAL 2 HOUR) WHERE userid=2 AND role='MEMBER';" | Out-Null

    foreach ($case in $cases) {
        Write-Output "Testing $($case.path)..."
        Invoke-Face 'Post' ($case.path + '/save') $case.body | Out-Null
        $escaped = $case.body[$case.key].Replace("'", "''")
        $id = Invoke-Sql "SELECT id FROM $($case.table) WHERE $($case.key)='$escaped' ORDER BY id DESC LIMIT 1;"
        if (-not $id) { throw "$($case.path) save returned success but no row was stored" }
        $update = @{} + $case.update
        $update.id = [long]$id
        Invoke-Face 'Post' ($case.path + '/update') $update | Out-Null
        Invoke-Face 'Post' ($case.path + '/delete') "[$id]" | Out-Null
        $remaining = Invoke-Sql "SELECT COUNT(*) FROM $($case.table) WHERE id=$id;"
        if ([int]$remaining -ne 0) { throw "$($case.path) delete did not remove row $id" }
        Add-Result $case.path 'create / update / delete'
    }

    $managed = Invoke-Face 'Post' 'api/v1/management/members' @{
        memberNo='FACE-MANAGED-MEMBER';name='audit-managed-member';phone='13900009991';
        username='face_managed_member';password='TestOnly-2026!'
    }
    $managedId = [long]$managed.data.id
    Invoke-Face 'Post' 'api/v1/management/members/update' @{
        id=$managedId;memberNo='FACE-MANAGED-MEMBER';name='audit-managed-member-updated';
        phone='13900009991';username='face_managed_member'
    } | Out-Null
    Invoke-Face 'Post' 'api/v1/management/members/delete' "[$managedId]" | Out-Null
    Add-Result 'member-management' 'create / update / deactivate'

    Invoke-Face 'Post' 'api/v1/auth/register' @{
        zhanghao='face_front_member';mima='TestOnly-2026!';xingming='audit-front-member';
        xingbie='F';shouji='13900009992'
    } '' | Out-Null
    $frontAuth = Invoke-Face 'Post' 'api/v1/auth/login' @{username='face_front_member';password='TestOnly-2026!'} ''
    $frontToken = $frontAuth.data.token
    $profile = Invoke-Face 'Get' 'account/session' $null $frontToken
    if ($profile.data.zhanghao -ne 'face_front_member') { throw 'Front member session compatibility fields are missing' }
    Invoke-Face 'Post' 'account/update' @{xingming='audit-front-member-updated';xingbie='F';shouji='13900009992'} $frontToken | Out-Null
    Invoke-Face 'Post' 'account/password' @{oldPassword='TestOnly-2026!';newPassword='TestOnly-2026-Changed!'} $frontToken | Out-Null
    Invoke-Face 'Post' 'api/v1/auth/login' @{username='face_front_member';password='TestOnly-2026-Changed!'} '' | Out-Null
    Add-Result 'member-client' 'register / login / profile update / password update'

    $techBody = @{weixiuzhanghao='face_beautician';mima='TestOnly-2026!';weixiuxingming='audit-beautician';xingbie='F';lianxidianhua='13800009999'}
    Invoke-Face 'Post' 'weixiujishi/save' $techBody | Out-Null
    $techId = Invoke-Sql "SELECT id FROM weixiujishi WHERE weixiuzhanghao='face_beautician' LIMIT 1;"
    $auth = Invoke-Face 'Post' 'api/v1/auth/login' @{username='face_beautician';password='TestOnly-2026!'} ''
    if ($auth.data.role -ne 'BEAUTICIAN') { throw 'Technician account did not receive BEAUTICIAN role' }
    Invoke-Face 'Get' 'api/v1/management/technician-dashboard' $null $auth.data.token | Out-Null
    Invoke-Face 'Post' 'weixiujishi/delete' "[$techId]" | Out-Null
    Add-Result 'technician' 'create / login / scoped dashboard / deactivate'

    $ask = "$marker-chat"
    $sent = Invoke-Face 'Post' 'chat/add' @{ask=$ask;type=1;uname='audit-member'} $memberToken
    $chatId = [long]$sent.data
    $queue = Invoke-Face 'Get' 'chat/page?page=1&limit=50&sort=addtime&order=desc&isreply=1' $null $ownerToken
    if (-not ($queue.data.list | Where-Object { $_.id -eq $chatId })) { throw 'Admin customer-service queue did not receive member message' }
    Invoke-Face 'Post' 'chat/save' @{userid=2;reply="$marker-reply";type=1;uname='support'} $ownerToken | Out-Null
    $thread = Invoke-Face 'Get' 'chat/list?page=1&limit=100&sort=addtime&order=asc&userid=2' $null $memberToken
    if (-not ($thread.data.list | Where-Object { $_.reply -eq "$marker-reply" })) { throw 'Member did not receive customer-service reply' }
    Add-Result 'customer-service' 'member send / admin receive / reply / member receive'
}
finally {
    foreach ($case in $cases) {
        $values = @($case.body[$case.key], $case.update[$case.key]) | Where-Object { $_ }
        foreach ($value in $values) {
            $escaped = $value.ToString().Replace("'", "''")
            Invoke-Sql "DELETE FROM $($case.table) WHERE $($case.key)='$escaped';" | Out-Null
        }
    }
    Invoke-Sql "DELETE FROM chat WHERE ask LIKE '$marker%' OR reply LIKE '$marker%'; DELETE FROM account WHERE username IN ('face_beautician','face_managed_member','face_front_member'); DELETE FROM member WHERE member_no='FACE-MANAGED-MEMBER' OR phone IN ('13900009991','13900009992'); DELETE FROM staff WHERE staff_no='face_beautician'; DELETE FROM weixiujishi WHERE weixiuzhanghao='face_beautician';" | Out-Null
}

$results | Format-Table -AutoSize
Write-Output "Submission regression passed: $($results.Count) flows."
