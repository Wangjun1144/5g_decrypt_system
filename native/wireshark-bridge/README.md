# Wireshark Native Bridge

This module is the phase-1 landing zone for replacing the current
`pcap -> tshark -> json -> java re-parse` flow with a direct native bridge.

## Phase-1 goal

Phase 1 does not decode full frames yet. It focuses on one narrow contract:

- input: NAS-5GS bytes
- engine: Wireshark-native dissector logic
- output: structured decode payload for the Java application

This lets the Java side stop depending on tshark JSON for NAS work while
keeping the scope small enough to prove out the bridge.

## Why this exists

The current project still carries several steps that only exist to make
`tshark.exe` usable:

- wrap bytes into a pcap
- spawn tshark
- stream JSON
- parse tshark JSON back into internal fields

For a native bridge, those steps are unnecessary. The bridge should eventually
accept decoded byte slices directly and return the structured fields required by
the existing signaling pipeline.

## Target contract

The native side will expose a small C ABI:

- `ws_native_decode_nas_5gs`
- `ws_native_free_result`

The JVM-facing side will eventually load a JNI shim library:

- `wireshark_native_bridge_jni`

That shim should stay thin and delegate to the C ABI above.

The Java side will call that ABI through JNI or JNA and receive a single JSON
document shaped like:

```json
{
  "bridgeVersion": "phase1",
  "protocolName": "nas-5gs",
  "messageType": 65,
  "messageTypeName": "Registration request",
  "flatFields": {
    "nas-5gs.mm.5gs_reg_type": "1"
  },
  "fieldTree": [
    {
      "name": "Registration request",
      "value": "",
      "offset": 0,
      "length": 10,
      "children": []
    }
  ],
  "diagnostics": []
}
```

## Implementation plan

1. Keep this module as a standalone native build root.
2. First ship a stub implementation so Java integration can stabilize.
3. Add the JNI shim that forwards to the C ABI.
4. Replace the stub with a real Wireshark-backed decoder.
5. Once NAS byte decode is stable, add frame-level decode entrypoints.

## Current build note

The current workspace can build the stub bridge with the available CMake tool.
For JVM loading, prefer a MinGW toolchain over a Cygwin-linked one, otherwise
the produced DLL may carry runtime dependencies that the JVM cannot resolve
cleanly.

For that reason this folder now includes two build paths:

- `build.ps1`
  for the CMake-based workspace build
- `build-mingw.ps1`
  for a direct MinGW DLL build aimed at JNI smoke testing on Windows

## Notes

- This folder intentionally does not try to build Wireshark yet.
- When we switch from stub to real implementation, we should keep the exported
  ABI stable so the Java side does not need another redesign.
