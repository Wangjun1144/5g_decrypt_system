package com.example.procedure.rule;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 描述“初始接入流程”的一个阶段：
 *  - index: 阶段号（0,1,2,...）
 *  - messages: 本阶段所有可能出现的信令类型，按时间顺序排列
 *  - startMessages: 能作为进入本阶段的“开始信令”
 *  - keyMessages: 本阶段对解密很重要的信令（用于 UEContext 更新）
 */
public class PhaseDef {

    private final int index;
    private final String[] messages;
    private final Set<String> startMessages;
    private final Set<String> keyMessages;

    public PhaseDef(int index,
                    String[] messages,
                    Set<String> startMessages,
                    Set<String> keyMessages) {
        this.index = index;
        this.messages = messages;
        this.startMessages = startMessages != null ? startMessages : Set.of();
        this.keyMessages = keyMessages != null ? keyMessages : Set.of();
    }

    public int getIndex() {
        return index;
    }

    public String[] getMessages() {
        return messages;
    }

    /** 在本阶段中的顺序号，找不到返回 -1 */
    public int indexOf(String msgType) {
        for (int i = 0; i < messages.length; i++) {
            if (messages[i].equals(msgType)) {
                return i;
            }
        }
        return -1;
    }

    public boolean isPhaseStart(String msgType) {
        return startMessages.contains(msgType);
    }

    public boolean isKeyMessage(String msgType) {
        return keyMessages.contains(msgType);
    }

    @Override
    public String toString() {
        return "PhaseDef{" +
                "index=" + index +
                ", messages=" + Arrays.toString(messages) +
                ", startMessages=" + startMessages +
                ", keyMessages=" + keyMessages +
                '}';
    }

    // 小工具：表示“某条消息在流程中的位置”
    public static class PhaseLocation {
        private final int phaseIndex;
        private final int orderIndex;
        private final boolean phaseStart;
        private final boolean key;

        public PhaseLocation(int phaseIndex, int orderIndex, boolean phaseStart, boolean key) {
            this.phaseIndex = phaseIndex;
            this.orderIndex = orderIndex;
            this.phaseStart = phaseStart;
            this.key = key;
        }

        public int getPhaseIndex() {
            return phaseIndex;
        }

        public int getOrderIndex() {
            return orderIndex;
        }

        public boolean isPhaseStart() {
            return phaseStart;
        }

        public boolean isKey() {
            return key;
        }
    }
}
