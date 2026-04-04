param(
    [string]$GccPath = "D:\\mingw64\\bin\\gcc.exe",
    [string]$SliceRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\third_party\\wireshark-slice",
    [string]$WiresharkRoot = "D:\\ideaterm\\5g-decrypt-system\\wireshark",
    [string]$ConfigRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core",
    [string]$BridgeRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\wireshark-bridge",
    [string]$GlibRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\deps\\msys2-glib\\mingw64",
    [string]$OutputRoot = "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\build-mac-nr-chain-minimal",
    [string]$OutputName = "ws_core_mac_nr_chain_minimal.dll",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

function Copy-RuntimeDependency {
    param(
        [string]$SourcePath,
        [string]$DestinationDirectory
    )

    if (Test-Path $SourcePath) {
        Copy-Item -Force $SourcePath (Join-Path $DestinationDirectory (Split-Path -Leaf $SourcePath))
    }
}

if (-not (Test-Path $GccPath)) {
    throw "gcc not found at $GccPath"
}

if ($Clean -and (Test-Path $OutputRoot)) {
    Remove-Item -Recurse -Force $OutputRoot
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$objRoot = Join-Path $OutputRoot "obj"
New-Item -ItemType Directory -Force -Path $objRoot | Out-Null

$includeArgs = @(
    "-I$ConfigRoot",
    "-I$BridgeRoot\\include",
    "-I$SliceRoot",
    "-I$SliceRoot\\epan",
    "-I$SliceRoot\\epan\\dissectors",
    "-I$SliceRoot\\wsutil",
    "-I$SliceRoot\\wiretap",
    "-I$SliceRoot\\include",
    "-I$WiresharkRoot",
    "-I$WiresharkRoot\\epan",
    "-I$WiresharkRoot\\epan\\dissectors",
    "-I$WiresharkRoot\\epan\\ftypes"
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
    "-DWS_BUILD_DLL",
    "-DENABLE_STATIC",
    "-DWS_CORE_MAC_NR_CHAIN_MINIMAL_BUILD=1"
)

$sources = @(
    "$ConfigRoot\\bridge\\ws_core_mac_nr_bridge.c",
    "$ConfigRoot\\bridge\\ws_core_json_export_minimal.c",
    "$ConfigRoot\\bridge\\ws_core_epan_minimal.c",
    "$ConfigRoot\\bridge\\ws_core_nas_stubs.c",
    "$ConfigRoot\\bridge\\ws_nas_gsm_a_common_minimal.c",
    "$SliceRoot\\epan\\asn1.c",
    "$SliceRoot\\epan\\dissectors\\packet-e212.c",
    "$SliceRoot\\epan\\dissectors\\packet-nas_5gs.c",
    "$WiresharkRoot\\epan\\dissectors\\packet-nas_eps.c",
    "$WiresharkRoot\\epan\\dissectors\\packet-per.c",
    "$WiresharkRoot\\epan\\dissectors\\packet-nr-rrc.c",
    "$WiresharkRoot\\epan\\dissectors\\packet-pdcp-nr.c",
    "$WiresharkRoot\\epan\\dissectors\\packet-rlc-nr.c",
    "$WiresharkRoot\\epan\\dissectors\\packet-mac-nr.c",
    "$SliceRoot\\epan\\charsets.c",
    "$SliceRoot\\epan\\except.c",
    "$SliceRoot\\epan\\frame_data.c",
    "$WiresharkRoot\\epan\\guid-utils.c",
    "$SliceRoot\\epan\\iana-info.c",
    "$SliceRoot\\epan\\packet.c",
    "$SliceRoot\\epan\\proto.c",
    "$SliceRoot\\epan\\proto_data.c",
    "$SliceRoot\\epan\\show_exception.c",
    "$SliceRoot\\epan\\stat_tap_ui.c",
    "$SliceRoot\\epan\\strutil.c",
    "$SliceRoot\\epan\\tfs.c",
    "$SliceRoot\\epan\\timestamp.c",
    "$SliceRoot\\epan\\to_str.c",
    "$SliceRoot\\epan\\tvbuff.c",
    "$WiresharkRoot\\epan\\tvbuff_composite.c",
    "$SliceRoot\\epan\\tvbuff_real.c",
    "$SliceRoot\\epan\\tvbuff_subset.c",
    "$SliceRoot\\epan\\unit_strings.c",
    "$SliceRoot\\epan\\wmem_scopes.c",
    "$SliceRoot\\epan\\ftypes\\ftypes.c",
    "$WiresharkRoot\\epan\\dfilter\\drange.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-bytes.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-double.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-guid.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-ieee-11073-float.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-integer.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-ipv4.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-ipv6.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-none.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-protocol.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-string.c",
    "$WiresharkRoot\\epan\\ftypes\\ftype-time.c",
    "$SliceRoot\\wsutil\\dtoa.c",
    "$SliceRoot\\wsutil\\feature_list.c",
    "$SliceRoot\\wsutil\\file_util.c",
    "$SliceRoot\\wsutil\\inet_addr.c",
    "$WiresharkRoot\\wsutil\\jsmn.c",
    "$SliceRoot\\wsutil\\json_dumper.c",
    "$SliceRoot\\wsutil\\nstime.c",
    "$SliceRoot\\wsutil\\str_util.c",
    "$SliceRoot\\wsutil\\strtoi.c",
    "$SliceRoot\\wsutil\\time_util.c",
    "$SliceRoot\\wsutil\\to_str.c",
    "$SliceRoot\\wsutil\\unicode-utils.c",
    "$SliceRoot\\wsutil\\value_string.c",
    "$WiresharkRoot\\wsutil\\ws_strptime.c",
    "$SliceRoot\\wsutil\\ws_getopt.c",
    "$SliceRoot\\wsutil\\ws_mempbrk.c",
    "$SliceRoot\\wsutil\\wsjson.c",
    "$SliceRoot\\wsutil\\wslog.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_allocator_block.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_allocator_block_fast.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_allocator_simple.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_allocator_strict.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_array.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_core.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_interval_tree.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_list.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_map.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_miscutl.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_multimap.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_stack.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_strbuf.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_strutl.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_tree.c",
    "$SliceRoot\\wsutil\\wmem\\wmem_user_cb.c"
)

$objects = @()
foreach ($source in $sources) {
    if (-not (Test-Path $source)) {
        throw "Source not found: $source"
    }

    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($source)
    $parentName = Split-Path -Leaf (Split-Path -Parent $source)
    $objectPath = Join-Path $objRoot "$parentName-$baseName.o"
    $objects += $objectPath

    Write-Host "Compiling $source"
    & $GccPath @commonArgs @includeArgs $source "-o" $objectPath
    if ($LASTEXITCODE -ne 0) {
        throw "Compilation failed for $source with exit code $LASTEXITCODE"
    }
}

$outputBinary = Join-Path $OutputRoot $OutputName
$linkArgs = @(
    "-shared",
    "-o", $outputBinary
)
$linkArgs += $objects

$glibLibDir = Join-Path $GlibRoot "lib"
if (Test-Path $glibLibDir) {
    $linkArgs += "-L$glibLibDir"
    $linkArgs += "-lglib-2.0"
    if (Test-Path (Join-Path $glibLibDir "libgobject-2.0.dll.a")) {
        $linkArgs += "-lgobject-2.0"
    }
    if (Test-Path (Join-Path $glibLibDir "libgthread-2.0.dll.a")) {
        $linkArgs += "-lgthread-2.0"
    }
    if (Test-Path (Join-Path $glibLibDir "libgio-2.0.dll.a")) {
        $linkArgs += "-lgio-2.0"
    }
    if (Test-Path (Join-Path $glibLibDir "libgmodule-2.0.dll.a")) {
        $linkArgs += "-lgmodule-2.0"
    }
}

$linkArgs += "-lws2_32"

Write-Host "Linking $outputBinary"
& $GccPath @linkArgs 2>&1 | Tee-Object -FilePath (Join-Path $OutputRoot "link.log")
if ($LASTEXITCODE -ne 0) {
    throw "Link failed with exit code $LASTEXITCODE"
}

Write-Host "Created minimal MAC-NR chain bridge: $outputBinary"

$runtimeDlls = @(
    "D:\\mingw64\\bin\\libgcc_s_sjlj-1.dll",
    "D:\\mingw64\\bin\\libwinpthread-1.dll",
    "$GlibRoot\\bin\\libglib-2.0-0.dll",
    "$GlibRoot\\bin\\libgmodule-2.0-0.dll",
    "$GlibRoot\\bin\\libgobject-2.0-0.dll",
    "$GlibRoot\\bin\\libgthread-2.0-0.dll",
    "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\deps\\msys2-glib-extra\\mingw64\\bin\\libasprintf-0.dll",
    "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\deps\\msys2-glib-extra\\mingw64\\bin\\libcharset-1.dll",
    "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\deps\\msys2-glib-extra\\mingw64\\bin\\libiconv-2.dll",
    "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\deps\\msys2-glib-extra\\mingw64\\bin\\libintl-8.dll",
    "D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\deps\\msys2-glib-extra\\mingw64\\bin\\libpcre2-8-0.dll"
)

foreach ($runtimeDll in $runtimeDlls) {
    Copy-RuntimeDependency -SourcePath $runtimeDll -DestinationDirectory $OutputRoot
}

Write-Host "Copied runtime DLL dependencies into $OutputRoot"
