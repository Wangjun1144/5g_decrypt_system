package com.example.procedure.model;

public enum ProcedureTypeEnum {
    INITIAL_ACCESS("IA", "初始接入流程"),
    SERVICE_REQUEST("SR", "业务请求流程"),
    XN_HANDOVER("XHO", "Xn 切换"),
    N2_HANDOVER("N2H", "N2 切换"),
    RRC_REESTABLISH("RRE", "RRC 重建/恢复"),
    GNBCUINTERNAL_HANDOVER("GCI", "gNB-CU 内部切换"),
    RRCSTATE_TRANSFER("RST", "RRC 状态迁移"),
    UNKNOWN("UNK", "未知流程");

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
            if (e.code.equals(code)) {   // 直接比较内部字段 code
                return e;
            }
        }
        return UNKNOWN;
    }
}

