param(
    [string]$BuildDir = "build-mingw",
    [string]$GccPath = "D:\\mingw64\\bin\\gcc.exe",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildPath = Join-Path $root $BuildDir

if ($Clean -and (Test-Path $buildPath)) {
    Remove-Item -Recurse -Force $buildPath
}

if (-not (Test-Path $buildPath)) {
    New-Item -ItemType Directory -Path $buildPath | Out-Null
}

if (-not (Test-Path $GccPath)) {
    throw "gcc not found at $GccPath"
}

if (-not $env:JAVA_HOME -or [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw "JAVA_HOME must be set for JNI compilation"
}

$includeDir = Join-Path $root "include"
$srcDir = Join-Path $root "src"
$javaInclude = Join-Path $env:JAVA_HOME "include"
$javaWin32Include = Join-Path $javaInclude "win32"

$coreOut = Join-Path $buildPath "wireshark_native_bridge.dll"
$jniOut = Join-Path $buildPath "wireshark_native_bridge_jni.dll"

$commonFlags = @(
    "-shared",
    "-O2",
    "-std=c11",
    "-I$includeDir"
)

& $GccPath @commonFlags `
    "-o" $coreOut `
    (Join-Path $srcDir "ws_native_bridge_stub.c")

& $GccPath @commonFlags `
    "-I$javaInclude" `
    "-I$javaWin32Include" `
    "-o" $jniOut `
    (Join-Path $srcDir "ws_native_bridge_stub.c") `
    (Join-Path $srcDir "ws_native_bridge_jni_stub.c")
