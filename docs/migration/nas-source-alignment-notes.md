# NAS Source Alignment Notes

Primary source of truth for ongoing NAS work:

- `wireshark/epan/dissectors/packet-nas_5gs.c`

This note exists to keep implementation work tied to Wireshark source logic
rather than only to already-exported sample JSON.

## Current source-backed coverage

### Authentication request (`0x56`)

Wireshark function:

- `nas_5gs_mm_authentication_req(...)`

Current native coverage:

- mandatory `ngKSI` half-octet
- mandatory `ABBA`
- optional `RAND` (`0x21`, TV)
- optional `AUTN` (`0x20`, TLV)

Still missing relative to source:

- deeper `EAP message` handoff beyond IE boundary preservation

### Authentication response (`0x57`)

Wireshark function:

- `nas_5gs_mm_authentication_resp(...)`

Current native coverage:

- optional authentication response parameter (`0x2d`, TLV)

Still missing relative to source:

- deeper `EAP message` handoff beyond IE boundary preservation

### Registration request (`0x41`)

Wireshark function:

- `nas_5gs_mm_registration_req(...)`

Current native coverage:

- mandatory `5GS registration type`
- mandatory `NAS key set identifier (H1)`
- mandatory `5GS mobile identity` for `5G-GUTI`
- optional `UE security capability` (`0x2e`, TLV)
- optional `5GMM capability` (`0x10`, TLV) first 6 octets source-aligned
- source-ordered optional scanner started for:
  - `MICO indication` (`0xb0`, TV)
  - `Payload container type` (`0x80`, TV)
  - `Additional GUTI` (`0x77`, TLV-E, tree-level decode path)

Still missing relative to source:

- non-current native NAS KSI (`0xc0`, TV)
- `5GMM capability` (`0x10`, TLV)
- `Requested NSSAI` (`0x2f`, TLV)
- `Last visited registered TAI` (`0x52`, TV)
- `S1 UE network capability` (`0x17`, TLV)
- `Uplink data status` (`0x40`, TLV)
- `PDU session status` (`0x50`, TLV)
- `MICO indication` (`0xb0`, TV)
- `UE status` (`0x2b`, TLV)
- `Additional GUTI` (`0x77`, TLV-E)
- `Allowed PDU session status` (`0x25`, TLV)
- `UE usage setting` (`0x18`, TLV)
- `Requested DRX parameters` (`0x51`, TLV)
- `EPS NAS message container` (`0x70`, TLV-E)
- `LADN indication` (`0x74`, TLV-E)
- payload container type and later optionals

### Identity response (`0x5c`)

Wireshark function:

- `nas_5gs_mm_id_resp(...)`

Current native coverage:

- mandatory `5GS mobile identity`
- `SUCI` / IMSI path fields currently aligned:
  - `supi_fmt`
  - `type_id`
  - `mcc`
  - `mnc`
  - `routing_indicator`
  - `scheme_id`
  - `pki`
  - `msin`
  - related spare bits

## Shared decoder behavior aligned from source

Wireshark common helper:

- `de_nas_5gs_mm_5gs_mobile_id(...)`

Current native shared helper:

- `Nas5gsMobileIdentityDecoder`

Implemented source-backed identity subpaths:

- `type_id = 1` (`SUCI`, IMSI path)
  - `NAI` branch
  - `scheme output` branch skeleton
- `type_id = 2` (`5G-GUTI`)
- `type_id = 3` (`IMEI`)
- `type_id = 4` (`5G-S-TMSI`)
- `type_id = 5` (`IMEISV`)
- `type_id = 6` (`MAC address`)
- `type_id = 7` (`EUI-64`)

Not yet implemented from Wireshark helper:

- source-specific NAI / scheme-output branches under `SUCI`

## Next source-driven priorities

1. Add `EAP message` handling skeleton for `0x56` and `0x57`.
2. Expand the source-ordered optional IE scanner for `Registration Request`.
3. Extend `Nas5gsMobileIdentityDecoder` with additional `type_id` branches from
   Wireshark.
