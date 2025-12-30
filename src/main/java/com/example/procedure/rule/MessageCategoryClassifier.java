package com.example.procedure.rule;

import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 简单按 msgType 做分类：
 *  - PROCEDURE_DRIVING：驱动流程状态迁移的关键消息
 *  - PROCEDURE_AUX：辅助流程，但不决定边界
 *  - NON_PROCEDURE：和流程无关
 *
 * 注意：这里的字符串要和 SignalingMessage.msgType 完全一致。
 */
@Service
public class MessageCategoryClassifier {

    /**
     * 核心流程驱动信令：
     *  - 初始接入 / 注册 / 5G-AKA / NAS SMC / ICS / RRC SMC / RRC Reconfig
     *  - 以及 RRC 重建相关的关键点
     *
     * 命名风格：
     *  - RRC: RRCSetupRequest / RRCSecurityModeCommand / RRCReconfiguration...
     *  - NAS: Registration request / AuthenticationRequest / NASSecurityModeCommand...
     *  - NGAP: Initial UE Message / InitialContextSetupRequest...
     */
    private static final Set<String> DRIVING_MSGS = Set.of(
            "RRCSetupComplete",
            "Initial UE Message",
            "Nausf_UEAuthentication_Authenticate Response",
            "NAS SecurityModeCommand",
            "Initial Context Setup Request",
            "RRC SecurityModeCommand",
            "RRCReconfiguration",
            "Initial Context Setup Response",
            "Initial Context Setup Failure",
            "RRCReconfigurationComplete",
            "Registration Complete",
            "Registration reject"
    );

    /**
     * 辅助流程信令（仍然参与流程识别，但不作为边界）
     */
    private static final Set<String> AUX_MSGS = Set.of(
            "Nausf_UEAuthentication_Authenticate Request",
            "Nudm_UEAuthentication_Get Request",
            "Nudm_UEAuthentication_Get Response",
            "Nausf_UEAuthentication Response",
            "Authentication Request",
            "Authentication Response",
            "Authentication Failure",
            "Authentication reject",
            "NAS SecurityModeComplete",
            "NAS SecurityModeReject",
            "Nudm_UEAutentication_ResultConfirmation Request",
            "Nudm_UEAutentication_ResultConfirmation Response",
            "Identity Request",
            "Identity Response",
            "RRC SecurityModeComplete",
            "RRC SecurityModeFailure"
    );

    public MessageCategory classify(SignalingMessage msg) {
        if (msg == null) {
            return MessageCategory.NON_PROCEDURE;
        }
        String type = msg.getMsgType();
        if (type == null) {
            return MessageCategory.NON_PROCEDURE;
        }

        if (DRIVING_MSGS.contains(type)) {
            return MessageCategory.PROCEDURE_DRIVING;
        }
        if (AUX_MSGS.contains(type)) {
            return MessageCategory.PROCEDURE_AUX;
        }
        return MessageCategory.NON_PROCEDURE;
    }
}
