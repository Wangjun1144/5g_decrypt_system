# Compile Notes

`ws-core` should be grown with compiler-driven iterations.

## Current approach

1. Copy a small source batch into `third_party/wireshark-slice`.
2. Run `build-preflight.ps1`.
3. Let the compiler reveal the next missing headers or transitive modules.
4. Expand the slice in small, explainable batches.
5. After preflight is clean, run `build-objects.ps1` to surface implementation-level gaps.

## Current blockers already observed

### Internal slice gaps

- `wsutil/feature_list.h`
- `wsutil/wmem/*` transitive headers need to be brought in as a coherent set
- `wsutil/ws_getopt.h` is required by `wsutil/wslog.h`
- a temporary local `wireshark.h` shim is currently required for preflight

### External build dependencies

- `glib` development headers are not installed in the current environment
- `libgcrypt` development headers/libraries are not yet staged locally

Until `glib.h` and related include paths are available, preflight can keep
revealing internal slice issues only up to the first GLib include boundary.

After GLib was staged locally, the current preflight boundary moved deeper into
Wireshark's own utility layer. The next iterations should keep following
compiler-reported transitive dependencies instead of bulk-copying whole
directories.

## Temporary shims

- `native/ws-core/wireshark.h` exists only to keep early preflight moving.
- `native/ws-core/gcrypt.h` is a syntax-only shim so `epan.c` can reveal deeper
  internal dependencies before real libgcrypt headers are integrated.

These shims are not sufficient for a final ws-core library build.

## Current milestone

- The current seed source set now passes syntax-only preflight.
- The seed source set also passes initial object compilation.
- The seed source set also packages into a static library.
- The next active boundary is link-time symbol resolution.

## Link-time workflow

1. Run `build-link-probe.ps1` to force the full archive through the linker.
2. If the link fails, run `analyze-link-log.ps1`.
3. Group unresolved symbols by subsystem before deciding whether to:
   - add more upstream objects
   - replace a temporary shim with a real dependency
   - defer non-critical subsystems from the first runtime slice

## Local dependency strategy

For the current project, external C dependencies should be staged under:

- `native/ws-core/deps/`

The first bootstrap target is GLib, extracted from the official MSYS2 MinGW
package into:

- `native/ws-core/deps/msys2-glib/mingw64`

## Why this fits the current project

The Java side is already stable enough to consume a future native runtime. The
main uncertainty is the minimum Wireshark source subset. Compiler-driven
preflight gives a faster feedback loop than trying to plan every dependency up
front.

## Current runtime focus

The current object/link iterations are prioritizing `wsutil` runtime helpers
that directly unblock the NAS path before expanding more protocol-heavy
dependencies. The current high-value targets are:

- `wsutil/unicode-utils.c`
- `wsutil/time_util.c`
- `wsutil/inet_addr.c`
- `wsutil/strtoi.c`

These files close a meaningful portion of the current unresolved symbol set
without yet forcing the project into heavier Windows-specific file utility or
CLI support layers.
