# NAS Source Slice Manifest

This manifest is tailored to the current project and its decode chain.

## Project integration target

The source slice is intended to feed these current project boundaries:

- `src/main/java/com/example/procedure/infrastructure/decode/nativews`
- `src/main/java/com/example/procedure/infrastructure/decode/bridge/pcap`
- `src/main/java/com/example/procedure/infrastructure/dissection/assemble`

The first native output target is NAS-5GS only, because that is where the
project already has:

- the most mature parity work
- existing `NativeNasSignalingMessageAssembler`
- the strongest need to replace tshark/json

## Phase-1 required Wireshark source roots

### Core runtime

- `wireshark/epan/epan.c`
- `wireshark/epan/epan.h`
- `wireshark/epan/epan_dissect.h`
- `wireshark/epan/packet.h`
- `wireshark/epan/proto*.c`
- `wireshark/epan/tvbuff*.c`
- `wireshark/epan/frame_data.*`
- `wireshark/epan/packet_info.h`
- `wireshark/epan/register.h`
- `wireshark/epan/expert.*`
- `wireshark/epan/prefs.*`
- `wireshark/epan/wmem*` via the required transitive sources

### NAS decode path

- `wireshark/epan/dissectors/packet-nas_5gs.c`
- `wireshark/epan/dissectors/packet-nas_eps.c`
- `wireshark/epan/dissectors/packet-gsm_a_common.*`

### Shared utility/runtime dependencies

- `wireshark/wsutil/`
- minimal required files under `wireshark/wiretap/`

## Not part of the first slice

- full UI
- tshark executable code
- generic capture-file pipeline
- unrelated dissectors
- plugin infrastructure beyond what NAS initialization strictly needs

## Extraction order

1. Build a project-owned bridge with the current stub ABI.
2. Copy only the EPAN/runtime files required to call `epan_init` safely in a
   non-JVM process.
3. Copy the NAS dissector path and compile it.
4. Add a native executable smoke probe that runs outside the JVM.
5. Once stable, move that same logic behind the JNI bridge.

## Why this order fits the current project

The project already has working Java-side packet reading and message assembly.
That means we do not need to reproduce Wireshark's full file-open path first.
We only need the decode runtime and dissector execution path.
