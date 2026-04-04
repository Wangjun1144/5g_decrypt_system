#include <stdint.h>

#if defined(_WIN32)
#define WS_CORE_EXPORT __declspec(dllexport)
#else
#define WS_CORE_EXPORT
#endif

WS_CORE_EXPORT int32_t ws_core_seed_link_probe(void) {
    return 0;
}
