param(
    [string]$GccPath = "D:\\mingw64\\bin\\gcc.exe",
    [string]$SliceRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\third_party\\wireshark-slice",
    [string]$ConfigRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core",
    [string]$GlibRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\deps\\msys2-glib\\mingw64"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $GccPath)) {
    throw "gcc not found at $GccPath"
}

$includeArgs = @(
    "-I$ConfigRoot",
    "-I$SliceRoot",
    "-I$SliceRoot\\epan",
    "-I$SliceRoot\\epan\\dissectors",
    "-I$SliceRoot\\wsutil",
    "-I$SliceRoot\\wiretap",
    "-I$SliceRoot\\include"
)

if (Test-Path "$GlibRoot\\include\\glib-2.0\\glib.h") {
    $includeArgs += "-I$GlibRoot\\include\\glib-2.0"
}
if (Test-Path "$GlibRoot\\lib\\glib-2.0\\include\\glibconfig.h") {
    $includeArgs += "-I$GlibRoot\\lib\\glib-2.0\\include"
}

$sources = @(
    "$SliceRoot\\epan\\dissectors\\packet-nas_5gs.c",
    "$SliceRoot\\epan\\dissectors\\packet-nas_eps.c",
    "$SliceRoot\\epan\\dissectors\\packet-gsm_a_common.c",
    "$SliceRoot\\epan\\epan.c",
    "$SliceRoot\\epan\\proto.c",
    "$SliceRoot\\epan\\tvbuff.c"
)

foreach ($source in $sources) {
    Write-Output "Preflight checking $source"
    & $GccPath -fsyntax-only @includeArgs $source
}
