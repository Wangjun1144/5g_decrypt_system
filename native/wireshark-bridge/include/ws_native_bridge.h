#ifndef WS_NATIVE_BRIDGE_H
#define WS_NATIVE_BRIDGE_H

#include <stddef.h>

#ifdef _WIN32
#define WS_NATIVE_EXPORT __declspec(dllexport)
#else
#define WS_NATIVE_EXPORT
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ws_native_nas_decode_request {
    const unsigned char* payload;
    size_t payload_length;
    int include_field_tree;
    int include_offsets;
} ws_native_nas_decode_request;

typedef ws_native_nas_decode_request ws_native_ngap_decode_request;
typedef ws_native_nas_decode_request ws_native_nr_rrc_decode_request;
typedef ws_native_nas_decode_request ws_native_mac_nr_decode_request;

typedef struct ws_native_decode_result {
    int status_code;
    char* json_utf8;
    char* error_utf8;
} ws_native_decode_result;

WS_NATIVE_EXPORT int ws_native_decode_nas_5gs(
    const ws_native_nas_decode_request* request,
    ws_native_decode_result* result
);

WS_NATIVE_EXPORT int ws_native_decode_ngap(
    const ws_native_ngap_decode_request* request,
    ws_native_decode_result* result
);

WS_NATIVE_EXPORT int ws_native_decode_nr_rrc(
    const ws_native_nr_rrc_decode_request* request,
    ws_native_decode_result* result
);

WS_NATIVE_EXPORT int ws_native_decode_mac_nr(
    const ws_native_mac_nr_decode_request* request,
    ws_native_decode_result* result
);

WS_NATIVE_EXPORT void ws_native_free_result(ws_native_decode_result* result);

#ifdef __cplusplus
}
#endif

#endif
