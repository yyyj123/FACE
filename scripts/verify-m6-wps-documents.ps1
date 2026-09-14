$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath('E:\face')
$documentRoot = Join-Path $repoRoot 'docs\commercial-v1.3'
$renderRoot = Join-Path $repoRoot 'tmp\m6-wps-render'
$allowedRenderRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'tmp')) +
    [System.IO.Path]::DirectorySeparatorChar
$resolvedRenderRoot = [System.IO.Path]::GetFullPath($renderRoot)
if (-not $resolvedRenderRoot.StartsWith($allowedRenderRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe WPS render directory: $resolvedRenderRoot"
}
New-Item -ItemType Directory -Path $resolvedRenderRoot -Force | Out-Null

$docxFiles = @(Get-ChildItem -LiteralPath $documentRoot -Filter '*.docx' -File | Sort-Object Name)
$pdfFiles = @(Get-ChildItem -LiteralPath $documentRoot -Filter '*.pdf' -File)
if ($docxFiles.Count -ne 3) { throw "Expected 3 commercial DOCX files, found $($docxFiles.Count)." }
if ($pdfFiles.Count -ne 1) { throw "Expected 1 commercial PDF file, found $($pdfFiles.Count)." }
$pdfFile = $pdfFiles[0].FullName

$wps = $null
try {
    $wps = New-Object -ComObject KWPS.Application
    $wps.Visible = $false
    $wps.DisplayAlerts = 0
    Write-Output "WPS_VERSION=$($wps.Version)"
    foreach ($file in $docxFiles) {
        $name = $file.Name
        $path = $file.FullName
        $export = Join-Path $resolvedRenderRoot ($file.BaseName + '.pdf')
        $document = $null
        try {
            $document = $wps.Documents.Open($path, $false, $false)
            if ($null -eq $document) { throw "WPS could not open $name" }
            foreach ($field in @($document.Fields)) {
                try { [void]$field.Update() } catch { }
            }
            $pages = [int]$document.ComputeStatistics(2)
            $paragraphs = [int]$document.Paragraphs.Count
            $tables = [int]$document.Tables.Count
            if ($pages -lt 2 -or $paragraphs -lt 10) {
                throw "WPS structure check failed for $name"
            }
            $document.Save()
            $document.ExportAsFixedFormat($export, 17)
            if (-not (Test-Path -LiteralPath $export) -or (Get-Item -LiteralPath $export).Length -le 0) {
                throw "WPS PDF export failed for $name"
            }
            Write-Output "WPS_DOCX=$name;PAGES=$pages;PARAGRAPHS=$paragraphs;TABLES=$tables"
            Write-Output "WPS_EXPORT=$export"
        }
        finally {
            if ($null -ne $document) {
                try { $document.Close($false) } catch { }
                [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($document)
            }
        }
    }
}
finally {
    if ($null -ne $wps) {
        try { $wps.Quit() } catch { }
        [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($wps)
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}

$wpsPdfExe = 'C:\Program Files (x86)\Kingsoft Office Software\WPS Office\12.1.0.28043\office6\wpspdf.exe'
if (-not (Test-Path -LiteralPath $wpsPdfExe)) { throw 'WPS PDF executable is missing.' }
$before = @(Get-Process -Name 'wpspdf' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
$launcher = Start-Process -FilePath $wpsPdfExe -ArgumentList @($pdfFile) -WindowStyle Hidden -PassThru
Start-Sleep -Seconds 5
$after = @(Get-Process -Name 'wpspdf' -ErrorAction SilentlyContinue)
$newProcesses = @($after | Where-Object { $_.Id -notin $before })
if ($launcher.HasExited -and $newProcesses.Count -eq 0 -and $launcher.ExitCode -ne 0) {
    throw "WPS PDF open failed with exit code $($launcher.ExitCode)."
}
Write-Output "WPS_PDF_OPENED=$([System.IO.Path]::GetFileName($pdfFile))"
foreach ($process in $newProcesses) {
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
}
if (-not $launcher.HasExited) {
    Stop-Process -Id $launcher.Id -Force -ErrorAction SilentlyContinue
}

Write-Output 'M6_WPS_DOCUMENTS=PASS'
