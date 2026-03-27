package com.example.procedure.processing.procedure.ruledef;

import com.example.procedure.model.initialaccess.NausfUeAuthRespPayload;
import com.example.procedure.model.initialaccess.NgapInitialUeMessagePayload;
import com.example.procedure.model.initialaccess.RrcSetupCompletePayload;
import com.example.procedure.model.SignalingMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.example.procedure.processing.procedure.ruledef.PhaseDef.PhaseLocation;


/**
 * 鍒濆鎺ュ叆 / 鍒濆娉ㄥ唽 + 5G-AKA 鐨勯樁娈甸厤缃?
 */
public class InitialAccessPhases {

    // 璧峰绫诲瀷锛氫笉鏄捣濮?/ 寰呭畾璧峰 / 纭璧峰
    public enum StartType {
        NOT_START,     // 璺熷垵濮嬫帴鍏ユ祦绋嬫棤鍏崇殑璧峰
        PENDING_START, // RRCSetupRequest / RRCSetup 杩欑锛氭湁鍙兘鏄?IA锛岃繕闇€瑕佸悗缁‘璁?
        CONFIRMED_START // RRCSetupComplete 鎴栧叾浠栨槑纭捣濮?
    }

    private static final List<PhaseDef> PHASES = new ArrayList<>();

    static {
        // ========= Phase 0: RRC 寤洪摼 =========
        PHASES.add(new PhaseDef(
                0,
                new String[]{
                        "RRCSetupComplete"
                },
                Set.of("RRCSetupComplete"), // 鍏佽浠庝换涓€鏉¤捣娴佺▼
                Set.of("RRCSetupComplete")                                 // 鍏抽敭锛歊RC 寤洪摼瀹屾垚
        ));

        // ========= Phase 1: InitialUE / Registration =========
        PHASES.add(new PhaseDef(
                1,
                new String[]{
                        "Initial UE Message",
                        "Nausf_UEAuthentication_Authenticate Request",
                        "Nudm_UEAuthentication_Get Request",
                        "Nudm_UEAuthentication_Get Response",
                        "Nausf_UEAuthentication Response",
                        "Authentication Request",
                        "Authentication Response",
                        "Authentication Failure",
                        "Authentication reject",
                        "Nausf_UEAuthentication_Authenticate Request"
                },
                Set.of("Initial UE Message"),
                Set.of("Initial UE Message")
        ));

        // ========= Phase 2:
        PHASES.add(new PhaseDef(
                2,
                new String[]{
                        "Nausf_UEAuthentication_Authenticate Response",
                },
                Set.of("Nausf_UEAuthentication_Authenticate Response"),
                Set.of("Nausf_UEAuthentication_Authenticate Response") // Kseaf
        ));

        // ========= Phase 3: Authentication =========
        PHASES.add(new PhaseDef(
                3,
                new String[]{
                        "NAS SecurityModeCommand",
                        "NAS SecurityModeComplete",
                        "NAS SecurityModeReject",
                        "Nudm_UEAutentication_ResultConfirmation Request",
                        "Nudm_UEAutentication_ResultConfirmation Response",
                        "Identity Request",
                        "Identity Response"
                },
                Set.of("NAS SecurityModeCommand"),
                Set.of("NAS SecurityModeCommand")
        ));

        // ========= Phase 4: NAS Security Mode =========
        PHASES.add(new PhaseDef(
                4,
                new String[]{
                        "Initial Context Setup Request"
                },
                Set.of("Initial Context Setup Request"),
                Set.of("Initial Context Setup Request")
        ));

        // ========= Phase 5:
        PHASES.add(new PhaseDef(
                5,
                new String[]{
                        "RRC SecurityModeCommand",
                        "RRC SecurityModeComplete",
                        "RRC SecurityModeFailure"
                },
                Set.of("RRC SecurityModeCommand"),
                Set.of("RRC SecurityModeCommand")
        ));

        // ========= Phase 6: =========
        PHASES.add(new PhaseDef(
                6,
                new String[]{
                        "RRCReconfiguration"
                },
                Set.of("RRCReconfiguration"),
                Set.of("RRCReconfiguration")
        ));

    }

    public static List<PhaseDef> getPhases() {
        return PHASES;
    }

    /** 鍦ㄦ墍鏈夐樁娈典腑鏌ユ壘鏌愪釜 msgType 鎵€鍦ㄧ殑浣嶇疆 */
    public static PhaseLocation locate(String msgType) {
        for (PhaseDef phase : PHASES) {
            int idx = phase.indexOf(msgType);
            if (idx >= 0) {
                return new PhaseLocation(
                        phase.getIndex(),
                        idx,
                        phase.isPhaseStart(msgType),
                        phase.isKeyMessage(msgType)
                );
            }
        }
        return null;
    }

    /**
     * 鏂规硶涓€锛氬垽鏂竴鏉′俊浠ゆ槸鍚﹀彲浠ヤ綔涓衡€淚A 娴佺▼鐨勮捣濮嬩俊浠も€?
     *
     *  - RRCSetupRequest / RRCSetup         -> PENDING_START锛堝緟瀹氾級
     *  - RRCSetupComplete                   -> CONFIRMED_START锛堝綋鍓嶉樁娈佃捣濮嬪叧閿俊浠わ級
     *  - 鍏朵粬娑堟伅锛?
     *      - 濡傛灉鍦ㄩ樁娈佃〃涓槸 phaseStart锛堟瘮濡傜洿鎺ヤ粠 Initial UE Message 寮€濮嬶級 -> CONFIRMED_START
     *      - 鍚﹀垯 -> NOT_START
     */
    public static StartType checkStartType(SignalingMessage msg) {
        String msgType = msg.getMsgType();

        // RRCSetupRequest / RRCSetup -> PENDING_START
        if ("RRCSetupRequest".equals(msgType) || "RRCSetup".equals(msgType)) {
            return StartType.PENDING_START;
        }
        // RRCSetupComplete 瑕佹鏌?payload
        if ("RRCSetupComplete".equals(msgType)) {
            if (msg.getNasList()!=null) {
                return StartType.CONFIRMED_START;
            } else {
                return StartType.NOT_START;
            }
        }

         return StartType.NOT_START;
    }

    // 猸?IA 娴佺▼缁撴潫淇′护锛氫换鎰忎竴涓嚭鐜板氨鍙互璁や负娴佺▼缁撴潫
    private static final Set<String> END_MESSAGES = Set.of(
            "Initial Context Setup Response",
            "Initial Context Setup Failure",
            "RRCReconfigurationComplete",
            "Registration Complete",
            "Registration reject"
    );

    /**
     * 鏂规硶浜岋細鍒ゆ柇涓€鏉′俊浠ゆ槸鍚︽槸鈥淚A 娴佺▼鐨勭粨鏉熶俊浠も€?
     *  鍙鍦?END_MESSAGES 閲岋紝灏辫涓鸿繖涓?IA 娴佺▼鍙互缁撴潫骞跺綊妗?
     */
    public static boolean isEndMessage(String msgType) {
        return END_MESSAGES.contains(msgType);
    }

    public static boolean hasValidPayloadForPhaseStart(SignalingMessage msg, int phaseIndex) {
        String msgType = msg.getMsgType();

        // 濡傛灉鏍规湰娌℃湁 payload锛岀洿鎺ヨ涓衡€滀笉婊¤冻鈥?
//        if (msg.getPayload() == null) {
//            return false;
//        }

        // 閽堝涓嶅悓闃舵 / 涓嶅悓璧峰淇′护锛屽仛绮剧粏鍒ゆ柇
        // 浣犲彲浠ユ牴鎹?InitialAccessPhases 鐨勫畾涔夋潵鍐?

        // ========== Phase 0 -> 1: Initial UE Message ==========
        if (phaseIndex == 1 && "Initial UE Message".equals(msgType)) {
            // 鍋囪浣犳湁涓€涓?NgapInitialUeMessagePayload锛岄噷闈㈡湁 amfUeNgapId / ranUeNgapId
//            if (msg.getPayload() instanceof NgapInitialUeMessagePayload p && p.isStartMsg()) {
//                return true;
//            }
            // payload 绫诲瀷涓嶅 or 瀛楁涓虹┖ 鈫?涓嶆弧瓒?
            return true;
        }

        // ========== Phase 1 -> 2: Nausf_UEAuthentication_Authenticate Response ==========
        if (phaseIndex == 2 && "Nausf_UEAuthentication_Authenticate Response".equals(msgType)) {
            // 涓句緥锛氫綘鏈変竴涓?NausfAuthRespPayload锛岄噷闈㈡湁 Kseaf
//            if (msg.getPayload() instanceof NausfUeAuthRespPayload p && p.isStartMsg()) {
//                return true;
//            }
            return true;
        }

        // ========== 鍏朵粬闃舵锛氭殏鏃跺彧瑕佹湁 payload 灏辨斁琛?==========
        return true;
    }

}
