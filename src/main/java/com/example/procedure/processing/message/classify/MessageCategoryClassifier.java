package com.example.procedure.processing.message.classify;

import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.SignalingMessage;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 按消息类型执行静态分类。
 *
 * 当前定位：
 * 1. 这是消息处理子域中的分类组件
 * 2. 虽然内部仍是静态规则集合，但它服务于 processing.message 主链
 * 3. 不再放在 rule 包中，避免把运行期消息分类和静态流程定义混在一起
 */
@Service
public class MessageCategoryClassifier {
    // REFACTOR STEP: MESSAGE_CLASSIFY_SUBPACKAGE_REORG

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

    /**
     * 根据消息类型执行分类。
     *
     * @param msg 当前消息
     * @return 消息分类
     */
    // REFACTOR STEP: RULE_PACKAGE_PRUNE
    public MessageCategory classify(SignalingMessage msg) {
        if (msg == null || msg.getMsgType() == null) {
            return MessageCategory.NON_PROCEDURE;
        }

        String type = msg.getMsgType();
        if (DRIVING_MSGS.contains(type)) {
            return MessageCategory.PROCEDURE_DRIVING;
        }
        if (AUX_MSGS.contains(type)) {
            return MessageCategory.PROCEDURE_AUX;
        }
        return MessageCategory.NON_PROCEDURE;
    }
}
