#ifndef WS_CORE_CONFIG_H
#define WS_CORE_CONFIG_H

/*
 * Minimal config shim for the early ws-core slice preflight stage.
 *
 * This is not a full Wireshark-generated config header. It only exists so the
 * compiler can start walking the copied source slice and report the next layer
 * of missing dependencies.
 */

#define ENABLE_STATIC 1

#define VERSION "ws-core-preflight"
#define VERSION_MAJOR 0
#define VERSION_MINOR 0
#define VERSION_MICRO 0

#ifndef UNICODE
#define UNICODE 1
#endif
#ifndef _UNICODE
#define _UNICODE 1
#endif

/*
 * Leave optional feature macros undefined unless we intentionally want their
 * include trees to participate in preflight. Many Wireshark sources use
 * `#ifdef HAVE_FOO` rather than `#if HAVE_FOO`.
 */

#endif
