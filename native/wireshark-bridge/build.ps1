param(
    [string]$BuildDir = "build",
    [string]$Generator = "Unix Makefiles",
    [string]$CCompiler = "",
    [switch]$Fresh
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildPath = Join-Path $root $BuildDir

if (-not (Test-Path $buildPath)) {
    New-Item -ItemType Directory -Path $buildPath | Out-Null
}

Push-Location $root
try {
    if ($Fresh -and (Test-Path $buildPath)) {
        Remove-Item -Recurse -Force $buildPath
        New-Item -ItemType Directory -Path $buildPath | Out-Null
    }
    $configureArgs = @("-S", ".", "-B", $BuildDir, "-G", $Generator)
    if ((-not $CCompiler) -or [string]::IsNullOrWhiteSpace($CCompiler)) {
        $mingwGcc = "D:\\mingw64\\bin\\gcc.exe"
        if (Test-Path $mingwGcc) {
            $CCompiler = $mingwGcc
        }
    }
    if ($CCompiler -and -not [string]::IsNullOrWhiteSpace($CCompiler)) {
        $compilerPath = $CCompiler
        $cygpath = Get-Command cygpath -ErrorAction SilentlyContinue
        if ($cygpath -and (Test-Path $compilerPath)) {
            $compilerPath = (& $cygpath.Source -u $compilerPath).Trim()
        }
        $configureArgs += "-DCMAKE_C_COMPILER=$compilerPath"
    }
    if ($env:JAVA_HOME -and -not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $javaHome = $env:JAVA_HOME
        $cygpath = Get-Command cygpath -ErrorAction SilentlyContinue
        if ($cygpath) {
            $javaHome = (& $cygpath.Source -u $javaHome).Trim()
        }
        $configureArgs += "-DWS_NATIVE_JAVA_HOME=$javaHome"
    }
    cmake @configureArgs
    cmake --build $BuildDir
} finally {
    Pop-Location
}
