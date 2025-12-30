package com.example.procedure.rule;

public final class InitialAccessKeyBits {

    private InitialAccessKeyBits(){}

    public static final int BIT_RRC_SETUP_COMPLETE = 1 << 0;
    public static final int BIT_INITIAL_UE_MESSAGE = 1 << 1;
    public static final int BIT_NAUSF_AUTH_RESP    = 1 << 2;
    public static final int BIT_NAS_SMC            = 1 << 3;
    public static final int BIT_ICS_REQ            = 1 << 4;
    public static final int BIT_RRC_SMC            = 1 << 5;

    /** 强条件：全齐 */
    public static final int REQUIRED_MASK_STRONG =
            BIT_RRC_SETUP_COMPLETE |
                    BIT_INITIAL_UE_MESSAGE |
                    BIT_NAUSF_AUTH_RESP |
                    BIT_NAS_SMC |
                    BIT_ICS_REQ |
                    BIT_RRC_SMC;

    /** 弱条件（推荐先用）：抓包缺失时也能结束 */
    public static final int REQUIRED_MASK_WEAK =
            BIT_NAUSF_AUTH_RESP |
                    BIT_NAS_SMC |
                    BIT_ICS_REQ;

    public static int bitForMsgType(String msgType) {
        if (msgType == null) return 0;
        return switch (msgType) {
            case "RRCSetupComplete" -> BIT_RRC_SETUP_COMPLETE;
            case "Initial UE Message" -> BIT_INITIAL_UE_MESSAGE;
            case "Nausf_UEAuthentication_Authenticate Response" -> BIT_NAUSF_AUTH_RESP;
            case "NAS SecurityModeCommand" -> BIT_NAS_SMC;
            case "Initial Context Setup Request" -> BIT_ICS_REQ;
            case "RRC SecurityModeCommand" -> BIT_RRC_SMC;
            default -> 0;
        };
    }
}
