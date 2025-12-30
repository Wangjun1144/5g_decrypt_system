package com.example.procedure.rule;


import com.example.procedure.initial_acess.NausfUeAuthRespPayload;
import com.example.procedure.initial_acess.RrcSetupCompletePayload;
import com.example.procedure.initial_acess.NgapInitialUeMessagePayload;
import com.example.procedure.model.SignalingMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.example.procedure.rule.PhaseDef.PhaseLocation;

/**
 * 初始接入 / 初始注册 + 5G-AKA 的阶段配置
 */
public class InitialAccessPhases {

    // 起始类型：不是起始 / 待定起始 / 确认起始
    public enum StartType {
        NOT_START,     // 跟初始接入流程无关的起始
        PENDING_START, // RRCSetupRequest / RRCSetup 这种：有可能是 IA，还需要后续确认
        CONFIRMED_START // RRCSetupComplete 或其他明确起始
    }

    private static final List<PhaseDef> PHASES = new ArrayList<>();

    static {
        // ========= Phase 0: RRC 建链 =========
        PHASES.add(new PhaseDef(
                0,
                new String[]{
                        "RRCSetupComplete"
                },
                Set.of("RRCSetupComplete"), // 允许从任一条起流程
                Set.of("RRCSetupComplete")                                 // 关键：RRC 建链完成
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

    /** 在所有阶段中查找某个 msgType 所在的位置 */
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
     * 方法一：判断一条信令是否可以作为“IA 流程的起始信令”
     *
     *  - RRCSetupRequest / RRCSetup         -> PENDING_START（待定）
     *  - RRCSetupComplete                   -> CONFIRMED_START（当前阶段起始关键信令）
     *  - 其他消息：
     *      - 如果在阶段表中是 phaseStart（比如直接从 Initial UE Message 开始） -> CONFIRMED_START
     *      - 否则 -> NOT_START
     */
    public static StartType checkStartType(SignalingMessage msg) {
        String msgType = msg.getMsgType();

        // RRCSetupRequest / RRCSetup -> PENDING_START
        if ("RRCSetupRequest".equals(msgType) || "RRCSetup".equals(msgType)) {
            return StartType.PENDING_START;
        }
        // RRCSetupComplete 要检查 payload
        if ("RRCSetupComplete".equals(msgType)) {
            if (msg.getNasList()!=null) {
                return StartType.CONFIRMED_START;
            } else {
                return StartType.NOT_START;
            }
        }

         return StartType.NOT_START;
    }

    // ⭐ IA 流程结束信令：任意一个出现就可以认为流程结束
    private static final Set<String> END_MESSAGES = Set.of(
            "Initial Context Setup Response",
            "Initial Context Setup Failure",
            "RRCReconfigurationComplete",
            "Registration Complete",
            "Registration reject"
    );

    /**
     * 方法二：判断一条信令是否是“IA 流程的结束信令”
     *  只要在 END_MESSAGES 里，就认为这个 IA 流程可以结束并归档
     */
    public static boolean isEndMessage(String msgType) {
        return END_MESSAGES.contains(msgType);
    }

    public static boolean hasValidPayloadForPhaseStart(SignalingMessage msg, int phaseIndex) {
        String msgType = msg.getMsgType();

        // 如果根本没有 payload，直接认为“不满足”
//        if (msg.getPayload() == null) {
//            return false;
//        }

        // 针对不同阶段 / 不同起始信令，做精细判断
        // 你可以根据 InitialAccessPhases 的定义来写

        // ========== Phase 0 -> 1: Initial UE Message ==========
        if (phaseIndex == 1 && "Initial UE Message".equals(msgType)) {
            // 假设你有一个 NgapInitialUeMessagePayload，里面有 amfUeNgapId / ranUeNgapId
//            if (msg.getPayload() instanceof NgapInitialUeMessagePayload p && p.isStartMsg()) {
//                return true;
//            }
            // payload 类型不对 or 字段为空 → 不满足
            return true;
        }

        // ========== Phase 1 -> 2: Nausf_UEAuthentication_Authenticate Response ==========
        if (phaseIndex == 2 && "Nausf_UEAuthentication_Authenticate Response".equals(msgType)) {
            // 举例：你有一个 NausfAuthRespPayload，里面有 Kseaf
//            if (msg.getPayload() instanceof NausfUeAuthRespPayload p && p.isStartMsg()) {
//                return true;
//            }
            return true;
        }

        // ========== 其他阶段：暂时只要有 payload 就放行 ==========
        return true;
    }

}
