#ifndef WS_CORE_ARES_H
#define WS_CORE_ARES_H

/*
 * Temporary preflight shim for c-ares.
 *
 * This is only used to keep ws-core dependency discovery moving until real
 * development headers are staged.
 */

const char *ares_version(int *version);

#endif
