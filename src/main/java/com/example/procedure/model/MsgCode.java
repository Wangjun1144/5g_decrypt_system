package com.example.procedure.model;

public enum MsgCode {
    UNKNOWN(0),

    // === RRC ===
    RRC_SETUP_COMPLETE(1001),
    RRC_SECURITY_MODE_COMMAND(1002),

    // === NGAP ===
    NGAP_INITIAL_UE_MESSAGE(2001),
    NGAP_INITIAL_CONTEXT_SETUP_REQUEST(2002),

    // === NAS ===
    NAS_SECURITY_MODE_COMMAND(3001),

    // === NUAR (N12) ===
    NUAR_AUTHENTICATE_RESPONSE(4001);

    public final int code;

    MsgCode(int code) {
        this.code = code;
    }
}
