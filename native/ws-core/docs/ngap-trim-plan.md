# NGAP Phase 1 Trim Plan

## Goal

Build a minimal native NGAP decode library that can:

- accept a known NGAP PDU byte stream
- decode the NGAP message itself
- export usable structured fields

Phase 1 explicitly does not require:

- `SCTP -> NGAP` outer transport decode
- full JSON/media-type encapsulation support
- deep sub-dissector parity for NAS, NR RRC, LTE RRC, or NRPPa

## First Milestone Reached

The local ASN.1 generation path is working.

We successfully generated:

- [`packet-ngap.c`](D:/ideaterm/5g-decrypt-system/native/ws-core/build-ngap-gen/packet-ngap.c)
- [`packet-ngap.h`](D:/ideaterm/5g-decrypt-system/native/ws-core/build-ngap-gen/packet-ngap.h)

using:

- [`asn2wrs.py`](D:/ideaterm/5g-decrypt-system/wireshark/tools/asn2wrs.py)
- [`ngap.cnf`](D:/ideaterm/5g-decrypt-system/wireshark/epan/dissectors/asn1/ngap/ngap.cnf)
- [`packet-ngap-template.c`](D:/ideaterm/5g-decrypt-system/wireshark/epan/dissectors/asn1/ngap/packet-ngap-template.c)
- [`packet-ngap-template.h`](D:/ideaterm/5g-decrypt-system/wireshark/epan/dissectors/asn1/ngap/packet-ngap-template.h)

This avoids the missing split generated files problem (`packet-ngap-hf.c`, `packet-ngap-ett.c`, `packet-ngap-val.h`), because the generated output is a self-contained single `packet-ngap.c`.

## Must-Have Sources For Phase 1

### NGAP module

- generated [`packet-ngap.c`](D:/ideaterm/5g-decrypt-system/native/ws-core/build-ngap-gen/packet-ngap.c)
- generated [`packet-ngap.h`](D:/ideaterm/5g-decrypt-system/native/ws-core/build-ngap-gen/packet-ngap.h)

### ASN.1 / PER runtime

- [`epan/asn1.c`](D:/ideaterm/5g-decrypt-system/native/ws-core/third_party/wireshark-slice/epan/asn1.c)
- [`epan/asn1.h`](D:/ideaterm/5g-decrypt-system/native/ws-core/third_party/wireshark-slice/epan/asn1.h)
- [`packet-per.c`](D:/ideaterm/5g-decrypt-system/wireshark/epan/dissectors/packet-per.c)
- [`packet-per.h`](D:/ideaterm/5g-decrypt-system/wireshark/epan/dissectors/packet-per.h)

### Existing ws-core runtime base

- minimal runtime from [`ws_core_epan_minimal.c`](D:/ideaterm/5g-decrypt-system/native/ws-core/bridge/ws_core_epan_minimal.c)
- packet/proto/tvbuff/ftypes/wmem pieces already proven by NAS phase 1

## Likely Needed But Can Be Stubbed First

- `prefs`
- `expert`
- `tap`
- `stats_tree`
- `media_type`
- `http2`
- `sctp`
- `show_exception`
- `conversation`
- `proto_data`

The generated NGAP source registers and references these systems, but Phase 1 should prefer minimal stubs over full subsystem bring-up where possible.

## Deep Decode Dependencies Seen In NGAP

The generated NGAP source references these protocol handles or helper families:

- `nas-5gs`
- `nr-rrc.ue_radio_paging_info`
- `nr-rrc.ue_radio_access_cap_info`
- `lte-rrc.*`
- `nrppa`

For Phase 1, the plan is:

- keep the hooks
- allow missing handles to degrade gracefully
- postpone full nested decode parity until the NGAP top-level PDU is stable

## Sources That Are Good Candidates To Defer

- full `packet-sctp.c` if we feed raw NGAP PDU bytes directly
- outer media-type / JSON assisted dispatch path
- LTE RRC / NR RRC / NRPPa payload dissectors
- transport-chain registration beyond the minimum needed to call NGAP directly

## Immediate Next Steps

1. Add a dedicated NGAP build script similar to the NAS minimal build flow.
2. Compile generated `packet-ngap.c` with `packet-per.c` plus the existing ws-core runtime base.
3. Stub the first unresolved subsystem edges instead of pulling full Wireshark trees immediately.
4. Build a minimal NGAP bridge entrypoint:
   - input: raw NGAP PDU bytes
   - output: flat fields JSON
5. Validate against one known NGAP sample before attempting `SCTP -> NGAP`.

## Working Principle

For NGAP, prefer module-level trimming rather than ultra-fine trimming.

That means:

- keep the generated NGAP dissector mostly intact first
- trim around it at the runtime boundary
- only remove or rewrite internals after we have a working DLL
