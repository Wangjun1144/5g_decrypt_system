# WS Core

`ws-core` is the project-owned native decode runtime that will eventually
replace the current dependency on:

- `pcap -> tshark.exe -> json -> Java re-parse`
- the temporary installed-library probe path

## Why this module exists

The current Java pipeline already has the right high-level shape:

- read capture packets
- decode signaling
- assemble `SignalingMessage`
- run message coordination and procedure recognition

What is still suboptimal is the decode boundary. Today it is split between:

- tshark-based production decode
- project-native Java dissectors for partial parity work
- a JNI/native bridge scaffold that can already load bridge DLLs

`ws-core` is the next step: extract only the Wireshark source needed for
decode, build it as a project-owned native library, and feed the existing Java
pipeline through JNI.

## How it connects to the current project

Target path inside this repository:

1. `PcapFileReader` keeps reading capture packets.
2. A future `WsCorePcapDecodeGateway` replaces the tshark gateway.
3. `ws-core` receives either:
   - NAS bytes directly
   - full frame bytes plus link type
4. `ws-core` returns:
   - message type / message name
   - flat fields
   - field tree
   - diagnostics
5. Java maps that into `DissectionResult` / `SignalingMessage`.

The goal is to let these current project pieces survive mostly unchanged:

- `src/main/java/com/example/procedure/processing/message`
- `src/main/java/com/example/procedure/application/message`
- `src/main/java/com/example/procedure/infrastructure/dissection/assemble`

## Scope of the first slice

The first source slice is intentionally narrow:

- EPAN runtime needed to initialize dissectors
- NAS-5GS dissector path
- the smallest common helper set required by NAS-5GS
- bridge code that exports a stable C ABI for JNI

This module should not try to read arbitrary capture files yet. The project
already has packet readers on the Java side.

## Layout

- `third_party/wireshark-slice/`
  project-owned copy of the minimum Wireshark source subset
- `bridge/`
  project bridge code that wraps the slice into a stable exported ABI
- `docs/`
  slice manifest and integration notes

## Current transition strategy

Near term:

- keep using installed `D:\\wireshark` runtime only for probing and reference
- start copying the minimum NAS decode source subset into this module
- compile a project-owned DLL from the copied slice

## First extraction command

The first seed batch can be copied with:

```powershell
powershell -ExecutionPolicy Bypass -File native\ws-core\extract-first-batch.ps1
```

The copied file list is tracked in:

- `native/ws-core/docs/first-batch-files.txt`

## Current preflight command

```powershell
powershell -ExecutionPolicy Bypass -File native\ws-core\build-preflight.ps1
```

At the moment this is expected to stop at external GLib development headers
unless those headers are installed locally.

## Local GLib bootstrap

If the machine does not already have GLib development headers, the project can
seed a local copy for `ws-core` with:

```powershell
powershell -ExecutionPolicy Bypass -File native\ws-core\bootstrap-glib-dev.ps1
```

This extracts the MSYS2 MinGW package into `native/ws-core/deps/msys2-glib/`.

Later:

- remove dependency on installed Wireshark DLLs from the default JNI path
- switch bridge output from stub/runtime-probe JSON to real source-backed decode

## Stage 2 object compile

Once preflight is clean, the next step is to compile the same seed sources into
object files:

```powershell
powershell -ExecutionPolicy Bypass -File native\ws-core\build-objects.ps1
```

This is expected to reveal implementation-level gaps that syntax-only preflight
cannot surface.

## Stage 3 static library packaging

After seed object compilation succeeds, the next step is to package the objects
into a project-owned static library:

```powershell
powershell -ExecutionPolicy Bypass -File native\ws-core\build-static-lib.ps1
```

This does not resolve link-time symbols yet. It establishes the first internal
library artifact that later bridge and link stages can consume.

## Stage 4 link probe

Once the seed static library exists, the next step is to force a link of the
entire archive and let the linker reveal unresolved runtime symbols:

```powershell
powershell -ExecutionPolicy Bypass -File native\ws-core\build-link-probe.ps1
```

This produces a small probe DLL or, when the link fails, a concrete unresolved
symbol log under `native/ws-core/build-link/`.

To summarize unresolved symbols after a failed link probe:

```powershell
powershell -ExecutionPolicy Bypass -File native\ws-core\analyze-link-log.ps1
```
