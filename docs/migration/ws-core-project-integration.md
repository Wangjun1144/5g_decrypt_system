# WS Core Integration With Current Project

This note maps the future `ws-core` native runtime onto the current Java
project, so the source extraction work stays aligned with real integration
points.

## Current decode boundaries

Production decode still goes through:

- `src/main/java/com/example/procedure/infrastructure/decode/bridge/pcap/TsharkPcapDecodeGateway.java`
- `src/main/java/com/example/procedure/infrastructure/decode/TsharkDecodeJsonTool.java`
- streaming tshark JSON parsing

The project also already has a native-oriented partial path:

- `src/main/java/com/example/procedure/infrastructure/decode/bridge/pcap/NativeNasPcapDecodeGateway.java`
- `src/main/java/com/example/procedure/infrastructure/dissection/FrameDissector.java`
- `src/main/java/com/example/procedure/infrastructure/dissection/assemble/NativeNasSignalingMessageAssembler.java`

## Intended replacement path

The future source-sliced native runtime should connect like this:

1. Java reads packets with `PcapFileReader`.
2. Java selects raw bytes for the target entry protocol.
3. JNI calls `ws-core`.
4. `ws-core` returns structured decode output.
5. Java maps it into:
   - `DissectionResult`
   - `NasInfo`
   - `SignalingMessage`
6. Existing message coordination keeps running unchanged.

## First production-use target

The first realistic production-use target is not full frame dissection.
It is:

- NAS-5GS decode from extracted bytes
- feeding `NativeNasSignalingMessageAssembler`

This is the smallest change that can remove the current need for:

- `text2pcap` as a tshark compatibility step
- tshark JSON streaming for NAS work
- repeated JSON-to-domain field extraction

## Bridge contract to preserve

The Java native bridge package should keep exposing:

- one request object for raw payload bytes
- one result object with:
  - flat fields
  - field tree
  - diagnostics

That lets the native implementation change from:

- stub
- installed-library probe
- source-sliced decode runtime

without changing the upper Java pipeline each time.
