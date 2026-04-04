#include "ws_native_bridge.h"

#include <jni.h>
#include <stdlib.h>

JNIEXPORT jstring JNICALL
Java_com_example_procedure_infrastructure_decode_nativews_NativeWiresharkJniBridgeClient_00024NativeMethods_decodeNas5gsJson(
    JNIEnv* env,
    jclass clazz,
    jbyteArray payload,
    jboolean include_field_tree,
    jboolean include_offsets
) {
    ws_native_nas_decode_request request;
    ws_native_decode_result result;
    jbyte* payload_bytes;
    jsize payload_length;
    (void) clazz;

    if (payload == NULL) {
        return (*env)->NewStringUTF(env, "{\"bridgeVersion\":\"phase1-jni-stub\",\"protocolName\":\"nas-5gs\",\"messageType\":-1,\"messageTypeName\":\"stub\",\"flatFields\":{},\"fieldTree\":[],\"diagnostics\":[\"payload was null\"]}");
    }

    payload_length = (*env)->GetArrayLength(env, payload);
    payload_bytes = (*env)->GetByteArrayElements(env, payload, NULL);

    request.payload = (const unsigned char*) payload_bytes;
    request.payload_length = (size_t) payload_length;
    request.include_field_tree = include_field_tree ? 1 : 0;
    request.include_offsets = include_offsets ? 1 : 0;

    if (ws_native_decode_nas_5gs(&request, &result) != 0 || result.json_utf8 == NULL) {
        (*env)->ReleaseByteArrayElements(env, payload, payload_bytes, JNI_ABORT);
        if (result.error_utf8 != NULL) {
            jstring error = (*env)->NewStringUTF(env, result.error_utf8);
            ws_native_free_result(&result);
            return error;
        }
        return (*env)->NewStringUTF(env, "{\"bridgeVersion\":\"phase1-jni-stub\",\"protocolName\":\"nas-5gs\",\"messageType\":-1,\"messageTypeName\":\"stub\",\"flatFields\":{},\"fieldTree\":[],\"diagnostics\":[\"core bridge returned no JSON\"]}");
    }

    (*env)->ReleaseByteArrayElements(env, payload, payload_bytes, JNI_ABORT);
    jstring json = (*env)->NewStringUTF(env, result.json_utf8);
    ws_native_free_result(&result);
    return json;
}
