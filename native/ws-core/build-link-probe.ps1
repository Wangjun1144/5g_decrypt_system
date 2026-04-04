param(
    [string]$GccPath = "D:\\mingw64\\bin\\gcc.exe",
    [string]$LibraryPath = (Join-Path $PSScriptRoot "build-lib\\libws_core_seed.a"),
    [string]$ProbeSource = (Join-Path $PSScriptRoot "bridge\\seed_link_probe.c"),
    [string]$GlibRoot = (Join-Path $PSScriptRoot "deps\\msys2-glib\\mingw64"),
    [string]$OutputRoot = (Join-Path $PSScriptRoot "build-link"),
    [string]$OutputName = "ws_core_seed_probe.dll",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $GccPath)) {
    throw "gcc not found at $GccPath"
}

if (-not (Test-Path $LibraryPath)) {
    throw "Seed static library not found: $LibraryPath. Run build-static-lib.ps1 first."
}

if (-not (Test-Path $ProbeSource)) {
    throw "Probe source not found: $ProbeSource"
}

if ($Clean -and (Test-Path $OutputRoot)) {
    Remove-Item -Recurse -Force $OutputRoot
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$outputBinary = Join-Path $OutputRoot $OutputName

$args = @(
    "-shared",
    "-o", $outputBinary,
    $ProbeSource,
    "-Wl,--whole-archive",
    $LibraryPath,
    "-Wl,--no-whole-archive"
)

$glibLibDir = Join-Path $GlibRoot "lib"
if (Test-Path $glibLibDir) {
    $args += "-L$glibLibDir"
    $args += "-lglib-2.0"
    if (Test-Path (Join-Path $glibLibDir "libgobject-2.0.dll.a")) {
        $args += "-lgobject-2.0"
    }
    if (Test-Path (Join-Path $glibLibDir "libgthread-2.0.dll.a")) {
        $args += "-lgthread-2.0"
    }
    if (Test-Path (Join-Path $glibLibDir "libgio-2.0.dll.a")) {
        $args += "-lgio-2.0"
    }
}

$args += "-lws2_32"

Write-Host "Link probing $LibraryPath"
& $GccPath @args 2>&1 | Tee-Object -FilePath (Join-Path $OutputRoot "link.log")

if ($LASTEXITCODE -ne 0) {
    throw "Link probe failed with exit code $LASTEXITCODE"
}

Write-Host "Created link probe binary: $outputBinary"
