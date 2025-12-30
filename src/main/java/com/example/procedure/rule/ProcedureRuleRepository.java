//package com.example.procedure.rule;
//
//import com.example.procedure.model.ProcedureTypeEnum;
//
//import java.util.Collection;
//import java.util.EnumMap;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Set;
//
//public class ProcedureRuleRepository {
//    private static final Map<ProcedureTypeEnum, ProcedureRule> RULES =
//            new EnumMap<>(ProcedureTypeEnum.class);
//
//    static {
//        // -------------------------------
//        // 1) 初始接入 / 初始注册 + 5G-AKA
//        // -------------------------------
//        Map<String, Set<String>> iaTransitions = new HashMap<>();
//
//        // -阶段一：确保成功建立流程，以RRCSetupComplete为基准
//        iaTransitions.put("RRCSetupRequest",
//                Set.of("RRCSetup", "RRCSetupComplete"));
//
//        iaTransitions.put("RRCSetup",
//                Set.of("RRCSetupComplete"));
//
//        // 阶段二
//        iaTransitions.put("RRCSetupComplete",
//                Set.of("Initial UE Message",
//                        "Registration request"));
//
//        // 阶段三
//        iaTransitions.put("Initial UE Message",
//                Set.of( "Nausf_UEAuthentication_Authenticate Request",
//                        "Nudm_UEAuthentication_Get Request",
//                        "Nudm_UEAuthentication_Get Response",
//                        "Nausf_UEAuthenticate Response",
//                        "Authentication Request"));
//
//        iaTransitions.put("Registration request",
//                Set.of( "Nausf_UEAuthentication_Authenticate Request",
//                        "Nudm_UEAuthentication_Get Request",
//                        "Nudm_UEAuthentication_Get Response",
//                        "Nausf_UEAuthenticate Response",
//                        "Authentication Request" ));
//
//        iaTransitions.put("Nausf_UEAuthentication_Authenticate Request",
//                Set.of( "Nudm_UEAuthentication_Get Request",
//                        "Nudm_UEAuthentication_Get Response",
//                        "Nausf_UEAuthenticate Response",
//                        "Authentication Request" ));
//
//        iaTransitions.put("Nudm_UEAuthentication_Get Request",
//                Set.of("Nudm_UEAuthentication_Get Response",
//                        "Nausf_UEAuthenticate Response",
//                        "Authentication Request"));
//
//        iaTransitions.put("Nudm_UEAuthentication_Get Response",
//                Set.of("Nausf_UEAuthenticate Response",
//                        "Authentication Request"));
//
//        iaTransitions.put("Nausf_UEAuthenticate Response",
//                Set.of("Authentication Request"));
//
//
//        // 阶段四
//        iaTransitions.put("Authentication Request",
//                Set.of( "Authentication Response",
//                        "Authentication Failure",
//                        "Authentication reject",
//                        "Nausf_UEAuthentication_Authenticate Request",
//                        "Nausf_UEAuthentication_Authenticate Response"));
//
//        iaTransitions.put("Authentication Response",
//                Set.of("Authentication Failure",
//                        "Authentication reject",
//                        "Nausf_UEAuthentication_Authenticate Request",
//                        "Nausf_UEAuthentication_Authenticate Response"));
//
//        iaTransitions.put("Authentication Failure",
//                Set.of("Authentication reject",
//                        "Nausf_UEAuthentication_Authenticate Request",
//                        "Nausf_UEAuthentication_Authenticate Response"));
//
//        iaTransitions.put("Authentication reject",
//                Set.of("Nausf_UEAuthentication_Authenticate Request",
//                        "Nausf_UEAuthentication_Authenticate Response"));
//
//        iaTransitions.put("Nausf_UEAuthentication_Authenticate Request",
//                Set.of("Nausf_UEAuthentication_Authenticate Response"));
//
//
//        //阶段五
//        iaTransitions.put("Nausf_UEAuthentication_Authenticate Response",
//                Set.of("NAS SecurityModeCommand"));
//
//        //阶段六
//        iaTransitions.put("NAS SecurityModeCommand",
//                Set.of( "NAS SecurityModeComplete",
//                        "NAS SecurityModeReject",
//                        "Initial Context Setup Request"));
//
//        iaTransitions.put("NAS SecurityModeComplete",
//                Set.of("NAS SecurityModeReject",
//                        "InitialContextSetupRequest"));
//
//        //阶段七
//        iaTransitions.put("NAS SecurityModeReject",
//                Set.of("InitialContextSetupRequest"));
//
//        //阶段八
//        iaTransitions.put("Initial Context Setup Request",
//                Set.of("RRC SecurityModeCommand"));
//
//        //阶段九
//        iaTransitions.put("RRC SecurityModeCommand",
//                Set.of(" RRC SecurityModeComplete",
//                        "SecuritymodeFailure",
//                        "RRCReconfiguration"));
//
//        iaTransitions.put("RRC SecurityModeComplete",
//                Set.of("RRC SecurityModeFailure",
//                        "RRCReconfiguration"));
//
//        iaTransitions.put("RRC SecurityModeFailure",
//                Set.of("RRCReconfiguration"));
//
//
//        //阶段十
//        iaTransitions.put("RRCReconfiguration",
//                Set.of( "RRCReconfigurationComplete",
//                        "Initial Context Setup Response",
//                        "Initial Context Setup Failure",
//                        "Registration Complete",
//                        "Registration reject"));
//
//
//        // ✅ 起点：
//        Set<String> iaStart = Set.of(
//                "RRCSetupRequest",
//                "RRCSetup",
//                "RRCSetupComplete",
//                "Registration request"
//        );
//
//        // 结束：成功完成 / 显式失败 / ICS 失败
//        Set<String> iaEnd =
//                Set.of( "RRCReconfigurationComplete",
//                        "Initial Context Setup Response",
//                        "Initial Context Setup Failure",
//                        "Registration Complete",
//                        "Registration reject");
//
//        Map<String, Integer> iaPhaseIndex = new HashMap<>();
//
//// 阶段一：RRC 建立
//        iaPhaseIndex.put("RRCSetupRequest", 1);
//        iaPhaseIndex.put("RRCSetup", 1);
//        iaPhaseIndex.put("RRCSetupComplete", 1);
//
//// 阶段二：Initial UE / Registration
//        iaPhaseIndex.put("Initial UE Message", 2);
//        iaPhaseIndex.put("Registration request", 2);
//
//// 阶段三：核心网鉴权前置
//        iaPhaseIndex.put("Nausf_UEAuthentication_Authenticate Request", 3);
//        iaPhaseIndex.put("Nudm_UEAuthentication_Get Request", 3);
//        iaPhaseIndex.put("Nudm_UEAuthentication_Get Response", 3);
//        iaPhaseIndex.put("Nausf_UEAuthentication_Authenticate Response", 4); // 这条也可以看成阶段 4 anchor
//
//// 阶段四：Auth Request/Response/Failure
//        iaPhaseIndex.put("Authentication Request", 4);
//        iaPhaseIndex.put("Authentication Response", 4);
//        iaPhaseIndex.put("Authentication Failure", 4);
//        iaPhaseIndex.put("Authentication reject", 4);
//
//// 阶段五：NASSecurityModeCommand/Complete/Reject
//        iaPhaseIndex.put("NAS SecurityModeCommand", 5);
//        iaPhaseIndex.put("NAS SecurityModeComplete", 5);
//        iaPhaseIndex.put("NAS SecurityModeReject", 5);
//
//// 阶段六：Initial Context Setup
//        iaPhaseIndex.put("Initial Context Setup Request", 6);
//        iaPhaseIndex.put("Initial Context Setup Response", 6);
//        iaPhaseIndex.put("Initial Context Setup Failure", 6);
//
//// 阶段七：RRC Security Mode
//        iaPhaseIndex.put("RRC SecurityModeCommand", 7);
//        iaPhaseIndex.put("RRC SecurityModeComplete", 7);
//        iaPhaseIndex.put("RRC SecurityModeFailure", 7);
//
//// 阶段八：RRCReconfiguration
//        iaPhaseIndex.put("RRCReconfiguration", 8);
//        iaPhaseIndex.put("RRCReconfigurationComplete", 8);
//
//// ……如果你后面还拆 Stage 9/10，可以继续补
//
//
//        RULES.put(
//                ProcedureTypeEnum.INITIAL_ACCESS,
//                new ProcedureRule(
//                        ProcedureTypeEnum.INITIAL_ACCESS,
//                        iaStart,
//                        iaEnd,
//                        iaTransitions,
//                        iaPhaseIndex,
//                        60_000L   // IA 最大允许空闲 60 秒，可根据实际再调
//                )
//        );
//
//        // -------------------------------
//        // 2) RRC 重建 / 恢复流程（统一改成驼峰 3GPP 风格）
//        // -------------------------------
////        RULES.put(
////                ProcedureTypeEnum.RRC_REESTABLISH,
////                new ProcedureRule(
////                        ProcedureTypeEnum.RRC_REESTABLISH,
////                        Set.of("RRCReestablishmentRequest", "RRCResumeRequest"),
////                        Set.of("RRCReestablishmentComplete", "RRCResumeComplete"),
////                        Map.of(
////                                "RRCReestablishmentRequest",
////                                Set.of("RRCReestablishment", "RRCReestablishmentComplete"),
////                                "RRCResumeRequest",
////                                Set.of("RRCResume", "RRCResumeComplete")
////                        ),
////                        30_000L
////                )
////        );
//
//        // TODO: Xn 切换、N2 切换、ServiceRequest、PduSessionEstablishment 等，
//        //       后面按同样的命名规范一条条补规则
//    }
//
//    public static ProcedureRule getRule(ProcedureTypeEnum type) {
//        return RULES.get(type);
//    }
//
//    public static Collection<ProcedureRule> getAllRules() {
//        return RULES.values();
//    }
//}
