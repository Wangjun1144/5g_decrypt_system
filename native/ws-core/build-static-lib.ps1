param(
    [string]$ArPath = "D:\mingw64\bin\ar.exe",
    [string]$ObjectRoot = (Join-Path $PSScriptRoot "build-obj"),
    [string]$OutputRoot = (Join-Path $PSScriptRoot "build-lib"),
    [string]$LibraryName = "libws_core_seed.a",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

if ($Clean -and (Test-Path $OutputRoot)) {
    Remove-Item -Recurse -Force $OutputRoot
}

if (-not (Test-Path $ObjectRoot)) {
    throw "Object root does not exist: $ObjectRoot. Run build-objects.ps1 first."
}

if (-not (Test-Path $ArPath)) {
    throw "ar.exe not found at: $ArPath"
}

$objects = Get-ChildItem -Path $ObjectRoot -Recurse -Filter *.o | Sort-Object FullName
if (-not $objects -or $objects.Count -eq 0) {
    throw "No object files found under: $ObjectRoot"
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputLibrary = Join-Path $OutputRoot $LibraryName

if (Test-Path $outputLibrary) {
    Remove-Item -Force $outputLibrary
}

$relativeObjects = $objects | ForEach-Object {
    $_.FullName.Substring($ObjectRoot.Length).TrimStart('\')
}

Push-Location $ObjectRoot
try {
    & $ArPath rcs $outputLibrary @relativeObjects
    if ($LASTEXITCODE -ne 0) {
        throw "Static library creation failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Host "Created static library: $outputLibrary"
Write-Host "Packed objects: $($objects.Count)"
