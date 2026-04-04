param(
    [string]$GccPath = "D:\\mingw64\\bin\\gcc.exe",
    [string]$SliceRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\third_party\\wireshark-slice",
    [string]$ConfigRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core",
    [string]$GlibRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\deps\\msys2-glib\\mingw64",
    [string]$OutputRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\build-obj",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $GccPath)) {
    throw "gcc not found at $GccPath"
}

if ($Clean -and (Test-Path $OutputRoot)) {
    Remove-Item -Recurse -Force $OutputRoot
}

if (-not (Test-Path $OutputRoot)) {
    New-Item -ItemType Directory -Path $OutputRoot | Out-Null
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

$commonArgs = @(
    "-c",
    "-std=gnu11",
    "-Wall",
    "-Wno-unused-function",
    "-Wno-unused-variable",
    "-Wno-unused-but-set-variable",
    "-Wno-sign-compare",
    "-Wno-format",
    "-DWS_CORE_PREFLIGHT_OBJECT_COMPILE=1",
    "-DWS_CORE_FORCE_STATIC_RUNTIME=1"
)

$sources = @(
    "$ConfigRoot\\bridge\\ws_nas_gsm_a_common_minimal.c",
    "$SliceRoot\\epan\\dissectors\\packet-nas_5gs.c",
    "$SliceRoot\\epan\\dissectors\\packet-nas_eps.c",
    "$SliceRoot\\epan\\dissectors\\packet-e212.c",
    "$SliceRoot\\epan\\charsets.c",
    "$SliceRoot\\epan\\column-utils.c",
    "$SliceRoot\\epan\\epan.c",
    "$SliceRoot\\epan\\except.c",
    "$SliceRoot\\epan\\expert.c",
    "$SliceRoot\\epan\\frame_data.c",
    "$SliceRoot\\epan\\iana-info.c",
    "$SliceRoot\\epan\\packet.c",
    "$SliceRoot\\epan\\proto.c",
    "$SliceRoot\\epan\\proto_data.c",
    "$SliceRoot\\epan\\show_exception.c",
    "$SliceRoot\\epan\\stat_tap_ui.c",
    "$SliceRoot\\epan\\stream.c",
    "$SliceRoot\\epan\\strutil.c",
    "$SliceRoot\\epan\\tap.c",
    "$SliceRoot\\epan\\tfs.c",
    "$SliceRoot\\epan\\timestamp.c",
    "$SliceRoot\\epan\\to_str.c",
    "$SliceRoot\\epan\\tvbuff.c",
    "$SliceRoot\\epan\\tvbuff_real.c",
    "$SliceRoot\\epan\\tvbuff_subset.c",
    "$SliceRoot\\epan\\unit_strings.c",
    "$SliceRoot\\epan\\wmem_scopes.c",
    "$SliceRoot\\epan\\ftypes\\ftypes.c",
    "$SliceRoot\\wsutil\\console_win32.c",
    "$SliceRoot\\wsutil\\dtoa.c",
    "$SliceRoot\\wsutil\\feature_list.c",
    "$SliceRoot\\wsutil\\file_util.c",
    "$SliceRoot\\wsutil\\nstime.c",
    "$SliceRoot\\wsutil\\inet_addr.c",
    "$SliceRoot\\wsutil\\json_dumper.c",
    "$SliceRoot\\wsutil\\str_util.c",
    "$SliceRoot\\wsutil\\strtoi.c",
    "$SliceRoot\\wsutil\\time_util.c",
    "$SliceRoot\\wsutil\\to_str.c",
    "$SliceRoot\\wsutil\\unicode-utils.c",
    "$SliceRoot\\wsutil\\value_string.c",
    "$SliceRoot\\wsutil\\ws_getopt.c",
    "$SliceRoot\\wsutil\\wsjson.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_core.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_array.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_list.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_map.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_miscutl.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_multimap.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_stack.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_strbuf.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_strutl.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_tree.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_interval_tree.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_user_cb.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_allocator_block.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_allocator_block_fast.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_allocator_simple.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_allocator_strict.c",
    "$SliceRoot\\wsutil\\ws_mempbrk.c",
    "$SliceRoot\\wsutil\\wslog.c"
)

foreach ($source in $sources) {
    $relative = $source.Substring($SliceRoot.Length).TrimStart('\')
    $objectPath = Join-Path $OutputRoot (($relative -replace '\.c$', '.o') -replace '\\', '\')
    $objectDir = Split-Path -Parent $objectPath
    if (-not (Test-Path $objectDir)) {
        New-Item -ItemType Directory -Path $objectDir -Force | Out-Null
    }

    Write-Output "Object compiling $source"
    & $GccPath @commonArgs @includeArgs $source "-o" $objectPath
    if ($LASTEXITCODE -ne 0) {
        throw "Object compilation failed for $source with exit code $LASTEXITCODE"
    }
}
