package com.example.procedure.model;

/**
 * Canonical procedure type codes used across recognition, scoring, and state
 * tracking.
 */
public enum ProcedureTypeEnum {
    INITIAL_ACCESS("IA", "Initial access"),
    SERVICE_REQUEST("SR", "Service request"),
    XN_HANDOVER("XHO", "Xn handover"),
    N2_HANDOVER("N2H", "N2 handover"),
    RRC_REESTABLISH("RRE", "RRC re-establish or resume"),
    GNBCUINTERNAL_HANDOVER("GCI", "gNB-CU internal handover"),
    RRCSTATE_TRANSFER("RST", "RRC state transfer"),
    UNKNOWN("UNK", "Unknown procedure");

    private final String code;
    private final String desc;

    ProcedureTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static ProcedureTypeEnum fromCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        for (ProcedureTypeEnum e : ProcedureTypeEnum.values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return UNKNOWN;
    }
}
