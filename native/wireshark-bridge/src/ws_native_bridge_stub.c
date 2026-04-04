#include "ws_native_bridge.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <windows.h>
#endif

static char* ws_native_strdup(const char* value) {
    size_t length;
    char* copy;
    if (value == NULL) {
        return NULL;
    }
    length = strlen(value);
    copy = (char*) malloc(length + 1);
    if (copy == NULL) {
        return NULL;
    }
    memcpy(copy, value, length + 1);
    return copy;
}

static const char* ws_native_nas_message_name(int message_type) {
    switch (message_type) {
        case 0x41:
            return "Registration request";
        case 0x56:
            return "Authentication request";
        case 0x57:
            return "Authentication response";
        case 0x5d:
            return "Security mode command";
        case 0x5e:
            return "Security mode complete";
        case 0x5c:
            return "Identity response";
        case 0xd6:
            return "5GSM status";
        case 0xc1:
            return "PDU session establishment request";
        case 0xc2:
            return "PDU session establishment accept";
        case 0xc3:
            return "PDU session establishment reject";
        default:
            return "stub";
    }
}

static int ws_native_guess_message_type(const unsigned char* payload, size_t payload_length) {
    if (payload == NULL || payload_length == 0) {
        return -1;
    }
    if (payload_length >= 3 && payload[0] == 0x7e) {
        return (int) payload[2];
    }
    if (payload_length >= 4 && payload[0] == 0x2e) {
        return (int) payload[3];
    }
    if (payload_length >= 1) {
        return (int) payload[0];
    }
    return -1;
}

typedef struct ws_runtime_probe_result {
    int available;
    int epan_init_present;
    int epan_cleanup_present;
    int epan_new_present;
    int epan_dissect_new_present;
    int epan_dissect_run_present;
    int epan_init_call_succeeded;
    const char* diagnostics;
} ws_runtime_probe_result;

static ws_runtime_probe_result ws_native_probe_wireshark_runtime(void) {
    ws_runtime_probe_result probe;
    probe.available = 0;
    probe.epan_init_present = 0;
    probe.epan_cleanup_present = 0;
    probe.epan_new_present = 0;
    probe.epan_dissect_new_present = 0;
    probe.epan_dissect_run_present = 0;
    probe.epan_init_call_succeeded = 0;
    probe.diagnostics = "wireshark runtime probe not attempted";

#ifdef _WIN32
    {
        typedef void (*ws_register_entity_func)(void* cb, void* client_data);
        typedef struct ws_epan_app_data {
            const char* env_var_prefix;
            const char** col_fmt;
            int num_cols;
            ws_register_entity_func register_func;
            ws_register_entity_func handoff_func;
            const void* tap_reg_listeners;
        } ws_epan_app_data;
        typedef int (*ws_epan_init_func)(void* cb, void* client_data, int load_plugins, ws_epan_app_data* app_data);
        typedef void (*ws_epan_cleanup_func)(void);

        const char* root = getenv("WS_NATIVE_WIRESHARK_DIR");
        char libwireshark_path[MAX_PATH];
        char libwiretap_path[MAX_PATH];
        char libwsutil_path[MAX_PATH];
        HMODULE libwiretap;
        HMODULE libwsutil;
        HMODULE libwireshark;
        ws_epan_init_func epan_init_func;
        ws_epan_cleanup_func epan_cleanup_func;
        ws_epan_app_data app_data;

        if (root == NULL || root[0] == '\0') {
            root = "D:\\wireshark";
        }

        snprintf(libwireshark_path, sizeof(libwireshark_path), "%s\\libwireshark.dll", root);
        snprintf(libwiretap_path, sizeof(libwiretap_path), "%s\\libwiretap.dll", root);
        snprintf(libwsutil_path, sizeof(libwsutil_path), "%s\\libwsutil.dll", root);

        libwiretap = LoadLibraryA(libwiretap_path);
        if (libwiretap == NULL) {
            probe.diagnostics = "failed to load libwiretap.dll";
            return probe;
        }

        libwsutil = LoadLibraryA(libwsutil_path);
        if (libwsutil == NULL) {
            probe.diagnostics = "failed to load libwsutil.dll";
            return probe;
        }

        libwireshark = LoadLibraryA(libwireshark_path);
        if (libwireshark == NULL) {
            probe.diagnostics = "failed to load libwireshark.dll";
            return probe;
        }

        probe.available = 1;
        epan_init_func = (ws_epan_init_func) GetProcAddress(libwireshark, "epan_init");
        epan_cleanup_func = (ws_epan_cleanup_func) GetProcAddress(libwireshark, "epan_cleanup");
        probe.epan_init_present = epan_init_func != NULL ? 1 : 0;
        probe.epan_cleanup_present = epan_cleanup_func != NULL ? 1 : 0;
        probe.epan_new_present = GetProcAddress(libwireshark, "epan_new") != NULL ? 1 : 0;
        probe.epan_dissect_new_present = GetProcAddress(libwireshark, "epan_dissect_new") != NULL ? 1 : 0;
        probe.epan_dissect_run_present = GetProcAddress(libwireshark, "epan_dissect_run") != NULL ? 1 : 0;
        if (epan_init_func != NULL && epan_cleanup_func != NULL) {
            memset(&app_data, 0, sizeof(app_data));
            app_data.env_var_prefix = "WIRESHARK";
            app_data.col_fmt = NULL;
            app_data.num_cols = 0;
            app_data.register_func = NULL;
            app_data.handoff_func = NULL;
            app_data.tap_reg_listeners = NULL;
            if (getenv("WS_NATIVE_TRY_EPAN_INIT") != NULL) {
                if (epan_init_func(NULL, NULL, 0, &app_data)) {
                    probe.epan_init_call_succeeded = 1;
                    epan_cleanup_func();
                    probe.diagnostics = "wireshark runtime probe loaded installed DLLs and epan_init succeeded";
                } else {
                    probe.diagnostics = "wireshark runtime probe loaded installed DLLs but epan_init returned false";
                }
            } else {
                probe.diagnostics = "wireshark runtime probe loaded installed DLLs; epan_init probe skipped";
            }
        } else {
            probe.diagnostics = "wireshark runtime probe loaded installed DLLs";
        }
        return probe;
    }
#else
    probe.diagnostics = "wireshark runtime probe is only implemented for Windows";
    return probe;
#endif
}

static char* ws_native_build_stub_json(
    const ws_native_nas_decode_request* request,
    int message_type,
    const ws_runtime_probe_result* probe
) {
    const char* message_name = ws_native_nas_message_name(message_type);
    const char* field_tree = request->include_field_tree ? "[]" : "[]";
    const char* protocol_name = "nas-5gs";
    const char* diagnostics = probe->diagnostics;
    int needed = snprintf(
        NULL,
        0,
        "{"
        "\"bridgeVersion\":\"phase1-stub\","
        "\"protocolName\":\"%s\","
        "\"messageType\":%d,"
        "\"messageTypeName\":\"%s\","
        "\"flatFields\":{"
        "\"bridge.stub_payload_length\":\"%zu\","
        "\"bridge.runtime_available\":\"%d\","
        "\"bridge.runtime.epan_init\":\"%d\","
        "\"bridge.runtime.epan_cleanup\":\"%d\","
        "\"bridge.runtime.epan_new\":\"%d\","
        "\"bridge.runtime.epan_dissect_new\":\"%d\","
        "\"bridge.runtime.epan_dissect_run\":\"%d\","
        "\"bridge.runtime.epan_init_call_succeeded\":\"%d\""
        "},"
        "\"fieldTree\":%s,"
        "\"diagnostics\":[\"%s\"]"
        "}",
        protocol_name,
        message_type,
        message_name,
        request->payload_length,
        probe->available,
        probe->epan_init_present,
        probe->epan_cleanup_present,
        probe->epan_new_present,
        probe->epan_dissect_new_present,
        probe->epan_dissect_run_present,
        probe->epan_init_call_succeeded,
        field_tree,
        diagnostics
    );
    char* json;
    if (needed < 0) {
        return NULL;
    }
    json = (char*) malloc((size_t) needed + 1);
    if (json == NULL) {
        return NULL;
    }
    snprintf(
        json,
        (size_t) needed + 1,
        "{"
        "\"bridgeVersion\":\"phase1-stub\","
        "\"protocolName\":\"%s\","
        "\"messageType\":%d,"
        "\"messageTypeName\":\"%s\","
        "\"flatFields\":{"
        "\"bridge.stub_payload_length\":\"%zu\","
        "\"bridge.runtime_available\":\"%d\","
        "\"bridge.runtime.epan_init\":\"%d\","
        "\"bridge.runtime.epan_cleanup\":\"%d\","
        "\"bridge.runtime.epan_new\":\"%d\","
        "\"bridge.runtime.epan_dissect_new\":\"%d\","
        "\"bridge.runtime.epan_dissect_run\":\"%d\","
        "\"bridge.runtime.epan_init_call_succeeded\":\"%d\""
        "},"
        "\"fieldTree\":%s,"
        "\"diagnostics\":[\"%s\"]"
        "}",
        protocol_name,
        message_type,
        message_name,
        request->payload_length,
        probe->available,
        probe->epan_init_present,
        probe->epan_cleanup_present,
        probe->epan_new_present,
        probe->epan_dissect_new_present,
        probe->epan_dissect_run_present,
        probe->epan_init_call_succeeded,
        field_tree,
        diagnostics
    );
    return json;
}

int ws_native_decode_nas_5gs(
    const ws_native_nas_decode_request* request,
    ws_native_decode_result* result
) {
    int message_type;
    ws_runtime_probe_result probe;
    if (result == NULL) {
        return -1;
    }

    result->status_code = -1;
    result->json_utf8 = NULL;
    result->error_utf8 = NULL;

    if (request == NULL || request->payload == NULL || request->payload_length == 0) {
        result->error_utf8 = ws_native_strdup("payload must not be empty");
        return -1;
    }

    message_type = ws_native_guess_message_type(request->payload, request->payload_length);
    probe = ws_native_probe_wireshark_runtime();
    result->status_code = 0;
    result->json_utf8 = ws_native_build_stub_json(request, message_type, &probe);
    if (result->json_utf8 == NULL) {
        result->status_code = -1;
        result->error_utf8 = ws_native_strdup("failed to allocate stub json payload");
        return -1;
    }

    return 0;
}

void ws_native_free_result(ws_native_decode_result* result) {
    if (result == NULL) {
        return;
    }
    free(result->json_utf8);
    free(result->error_utf8);
    result->json_utf8 = NULL;
    result->error_utf8 = NULL;
    result->status_code = 0;
}
