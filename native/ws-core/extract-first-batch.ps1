param(
    [string]$SourceRoot = "D:\ideaterm\5g-decrypt-system\wireshark",
    [string]$TargetRoot = "D:\ideaterm\5g-decrypt-system\native\ws-core\third_party\wireshark-slice",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$manifestPath = Join-Path $PSScriptRoot "docs\first-batch-files.txt"
if (-not (Test-Path $manifestPath)) {
    throw "Manifest not found: $manifestPath"
}

if ($Clean -and (Test-Path $TargetRoot)) {
    Remove-Item -Recurse -Force $TargetRoot
}

if (-not (Test-Path $TargetRoot)) {
    New-Item -ItemType Directory -Path $TargetRoot | Out-Null
}

$files = Get-Content $manifestPath | Where-Object { $_ -and -not $_.StartsWith("#") }
$copied = @()

foreach ($relative in $files) {
    $sourcePath = Join-Path $SourceRoot $relative
    if (-not (Test-Path $sourcePath)) {
        throw "Missing source file: $sourcePath"
    }
    $targetPath = Join-Path $TargetRoot $relative
    $targetDir = Split-Path -Parent $targetPath
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }
    Copy-Item -LiteralPath $sourcePath -Destination $targetPath -Force
    $copied += $relative
}

Write-Output ("Copied {0} files into {1}" -f $copied.Count, $TargetRoot)
