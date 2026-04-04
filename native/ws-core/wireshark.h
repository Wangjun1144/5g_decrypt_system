#ifndef WS_CORE_WIRESHARK_H
#define WS_CORE_WIRESHARK_H

/*
 * Temporary shim used only for source-slice preflight.
 *
 * Upstream Wireshark builds generate or provide a broader application header
 * surface. For the early ws-core compile walk we only need enough shape for
 * files such as proto.c to continue revealing transitive dependencies.
 */

#include <stdbool.h>
#include <stdint.h>
#include <ws_attributes.h>
#include <ws_diag_control.h>
#include <jtckdint.h>
#include <ws_symbol_export.h>
#include <wsutil/ws_assert.h>

#endif
