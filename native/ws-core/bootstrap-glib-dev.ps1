param(
    [string]$PackageUrl = "https://mirror.msys2.org/mingw/mingw64/mingw-w64-x86_64-glib2-2.86.4-1-any.pkg.tar.zst",
    [string]$DownloadDir = "downloads",
    [string]$DepsDir = "deps",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$downloadPath = Join-Path $root $DownloadDir
$depsPath = Join-Path $root $DepsDir
$archivePath = Join-Path $downloadPath "mingw-w64-x86_64-glib2.pkg.tar.zst"
$tarPath = Join-Path $downloadPath "mingw-w64-x86_64-glib2.pkg.tar"
$extractRoot = Join-Path $depsPath "msys2-glib"

if ($Clean) {
    if (Test-Path $archivePath) { Remove-Item -Force $archivePath }
    if (Test-Path $tarPath) { Remove-Item -Force $tarPath }
    if (Test-Path $extractRoot) { Remove-Item -Recurse -Force $extractRoot }
}

if (-not (Test-Path $downloadPath)) {
    New-Item -ItemType Directory -Path $downloadPath | Out-Null
}

if (-not (Test-Path $depsPath)) {
    New-Item -ItemType Directory -Path $depsPath | Out-Null
}

Invoke-WebRequest -Uri $PackageUrl -OutFile $archivePath

& "D:\Cygwin\bin\zstd.exe" -d -f $archivePath -o $tarPath

if (Test-Path $extractRoot) {
    Remove-Item -Recurse -Force $extractRoot
}
New-Item -ItemType Directory -Path $extractRoot | Out-Null

& "C:\Windows\System32\tar.exe" -xf $tarPath -C $extractRoot

Write-Output "Extracted GLib dev package into $extractRoot"
