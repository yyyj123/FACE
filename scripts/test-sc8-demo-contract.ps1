param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = [System.IO.Path]::GetFullPath($Root)

function Require-File {
    param([string]$RelativePath)
    $path = Join-Path $Root $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "SC8 required file is missing: $RelativePath"
    }
    return $path
}

function Require-Pattern {
    param([string]$Path, [string]$Pattern, [string]$Message)
    $content = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    if ($content -notmatch $Pattern) { throw $Message }
}

function Require-NginxLocationPattern {
    param(
        [string]$Path,
        [string]$Location,
        [string]$Pattern,
        [string]$Message
    )

    $content = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    $start = $content.IndexOf($Location, [System.StringComparison]::Ordinal)
    if ($start -lt 0) { throw $Message }

    $open = $content.IndexOf('{', $start)
    if ($open -lt 0) { throw $Message }

    $depth = 0
    $close = -1
    for ($index = $open; $index -lt $content.Length; $index++) {
        if ($content[$index] -eq '{') { $depth++ }
        elseif ($content[$index] -eq '}') {
            $depth--
            if ($depth -eq 0) {
                $close = $index
                break
            }
        }
    }

    if ($close -lt 0) { throw $Message }
    $block = $content.Substring($open, $close - $open + 1)
    if ($block -notmatch $Pattern) { throw $Message }
}

$compose = Require-File 'docker-compose.demo.yml'
$gateway = Require-File 'demo/gateway/nginx.conf'
$indexFile = Require-File 'demo/gateway/html/index.html'
$start = Require-File 'scripts/demo-start.ps1'
$stop = Require-File 'scripts/demo-stop.ps1'
$backup = Require-File 'scripts/demo-backup.ps1'
$reset = Require-File 'scripts/demo-reset.ps1'

Require-Pattern $compose 'cloudflared:' 'SC8 demo compose must define cloudflared.'
Require-Pattern $compose 'http://gateway:8080' 'Quick Tunnel must target the Compose-network gateway.'
Require-Pattern $compose 'profiles:\s*\r?\n\s*- tunnel' 'Quick Tunnel must be explicitly enabled through the tunnel profile.'
Require-Pattern $compose 'SPRING_PROFILES_ACTIVE:\s*demo' 'Backend must run only with the demo profile in demo compose.'
Require-Pattern $compose 'SMS_MODE:\s*demo' 'Demo SMS mode must be explicit.'
Require-Pattern $compose 'FACE_PAYMENT_DEMO_MOCK_ENABLED:\s*"true"' 'Demo mock payment must be explicit.'
Require-Pattern $compose 'FACE_DEMO_ACCESS_PASSWORD' 'Demo access password must be injected.'
Require-Pattern $compose 'FACE_DEMO_COOKIE_SECRET' 'Demo cookie signing secret must be injected.'
Require-Pattern $compose 'FACE_DEMO_ADMIN_PASSWORD' 'Fixed account password must be injected.'

Require-Pattern $gateway 'location /admin/' 'Gateway must expose /admin/.'
Require-Pattern $gateway 'location /client/' 'Gateway must expose /client/.'
Require-Pattern $gateway 'location /api/' 'Gateway must expose /api/.'
Require-Pattern $gateway 'auth_request /_demo_auth' 'Protected routes must use the demo gate.'
Require-Pattern $gateway 'absolute_redirect off' 'Demo gate redirects must not expose the Compose container port.'
Require-NginxLocationPattern $gateway 'location /admin/assets/' 'Cache-Control "public, max-age=31536000, immutable"' 'Hashed admin assets must be immutable in the browser cache.'
Require-NginxLocationPattern $gateway 'location /client/assets/' 'Cache-Control "public, max-age=31536000, immutable"' 'Hashed client assets must be immutable in the browser cache.'
Require-NginxLocationPattern $gateway 'location = /demo/session' 'proxy_set_header Origin "";' 'Demo gate POST must strip the browser Origin before same-origin proxying.'
Require-NginxLocationPattern $gateway 'location = /_demo_auth' 'proxy_set_header Origin "";' 'Demo auth subrequest must strip the browser Origin before cookie validation.'
Require-NginxLocationPattern $gateway 'location /api/' 'proxy_set_header Origin "";' 'Demo API proxy must strip the browser Origin before same-origin proxying.'
Require-Pattern $gateway 'location /technician/' 'Gateway must explicitly reject /technician/.'
Require-Pattern $gateway 'return 404' 'Frozen technician route must not be public.'

Require-Pattern -Path $indexFile -Pattern '<title>FACE' -Message 'The custom FACE access page is missing.'
Require-Pattern -Path $indexFile -Pattern '888888' -Message 'The fixed demo SMS code must be disclosed on the access page.'
Require-Pattern -Path $indexFile -Pattern '/admin/' -Message 'The admin entry is missing.'
Require-Pattern -Path $indexFile -Pattern '/client/' -Message 'The client entry is missing.'
if ((Get-Content -LiteralPath $indexFile -Raw -Encoding UTF8) -match 'technician') {
    throw 'The public demo page must not expose the frozen technician portal.'
}

foreach ($path in @($start, $stop, $backup, $reset)) {
    Require-Pattern $path 'ProjectName' "Demo operation script must require an explicit project name: $path"
}
Require-Pattern $start 'FACE_DEMO_PORT' 'Demo start must resolve an explicit host port.'
Require-Pattern $start 'already occupied|已被占用' 'Demo start must preflight the requested host port.'
Require-Pattern $stop '--profile.*tunnel' 'Demo stop must include the tunnel profile.'
Require-Pattern $backup 'mysqldump' 'Demo backup must create a database dump.'
Require-Pattern $reset 'flyway:migrate' 'Demo reset must restore the schema through Flyway migrate.'
Require-Pattern $reset 'flyway:validate' 'Demo reset must validate Flyway history.'

Write-Output 'SC8_DEMO_STATIC_CONTRACT=PASS'
