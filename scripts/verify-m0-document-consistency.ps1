$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = 'E:\face\docs\commercial-v1.1'
$prd = Join-Path $root '美容PRD_V1.1.pdf'
$database = Join-Path $root '美容数据库V1.1.docx'
$api = Join-Path $root '美容院店铺管理系统 API接口清单V1.1.docx'
$delivery = Join-Path $root '美容院店铺管理系统 分阶段开发实施与验收V1.1.docx'

function Read-DocxText {
    param([string]$Path)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $archive.GetEntry('word/document.xml')
        if ($null -eq $entry) {
            throw "word/document.xml missing: $Path"
        }
        $reader = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8)
        try {
            $xml = $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
        $matches = [regex]::Matches($xml, '<w:t[^>]*>(.*?)</w:t>')
        return ($matches | ForEach-Object {
            [System.Net.WebUtility]::HtmlDecode($_.Groups[1].Value)
        }) -join ''
    }
    finally {
        $archive.Dispose()
    }
}

function Read-PdfText {
    param([string]$Path)
    $pdfToText = (Get-Command 'pdftotext.exe' -ErrorAction Stop).Source
    $text = & $pdfToText -layout $Path -
    if ($LASTEXITCODE -ne 0) {
        throw "PDF text extraction failed: $Path"
    }
    return $text -join "`n"
}

function Require-Term {
    param(
        [string]$Document,
        [string]$Text,
        [string]$Term
    )
    if (-not $Text.Contains($Term)) {
        throw "$Document is missing required baseline term: $Term"
    }
}

$files = @($prd, $database, $api, $delivery)
foreach ($file in $files) {
    if (-not (Test-Path -LiteralPath $file)) {
        throw "Baseline file missing: $file"
    }
}

$texts = [ordered]@{
    PRD = Read-PdfText -Path $prd
    DATABASE = Read-DocxText -Path $database
    API = Read-DocxText -Path $api
    DELIVERY = Read-DocxText -Path $delivery
}

foreach ($entry in $texts.GetEnumerator()) {
    Require-Term -Document $entry.Key -Text $entry.Value -Term 'V1.1'
    Require-Term -Document $entry.Key -Text $entry.Value -Term '2026-07-26'
    Require-Term -Document $entry.Key -Text $entry.Value -Term 'M1 商用升级基线与安全闭环'
    Require-Term -Document $entry.Key -Text $entry.Value -Term 'Conditional Go'
    Require-Term -Document $entry.Key -Text $entry.Value -Term '不得伪造完成'
}

foreach ($term in @('/api/v3', 'M0 基线', 'M6 商用运营')) {
    Require-Term -Document 'PRD' -Text $texts.PRD -Term $term
}
foreach ($term in @('data_access_log', 'outbox_event', 'Expand')) {
    Require-Term -Document 'DATABASE' -Text $texts.DATABASE -Term $term
}
foreach ($term in @('/api/v3', 'Idempotency-Key', 'VERSION_CONFLICT')) {
    Require-Term -Document 'API' -Text $texts.API -Term $term
}
foreach ($term in @('M0 基线与保护', 'Definition of Done', 'Flyway')) {
    Require-Term -Document 'DELIVERY' -Text $texts.DELIVERY -Term $term
}

Write-Output 'BASELINE_FILES=4/4'
Write-Output 'BASELINE_VERSION=V1.1'
Write-Output 'BASELINE_DATE=2026-07-26'
Write-Output 'BASELINE_STAGE=M1'
Write-Output 'M0_DOCUMENT_CONSISTENCY=PASS'
