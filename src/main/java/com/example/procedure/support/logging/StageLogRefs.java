package com.example.procedure.support.logging;

import com.example.procedure.model.SignalingMessage;

import java.nio.file.Path;
import java.util.Set;

/**
 * 阶段日志引用信息工具。
 *
 * 设计目的：
 * 1. 统一 application 层和 processing 层的日志引用信息格式
 * 2. 避免在多个类中重复拼装 msgId / ueId / msgType
 * 3. 为后续继续补 trace / metrics / 审计日志提供统一引用格式
 *
 * 当前阶段只处理最常见的引用对象：
 * - SignalingMessage
 * - MessageProcessingContext
 * - pcap Path
 * - Set 的 size 输出
 *
 * 当前保持简单静态工具类，不引入额外状态。
 */
public final class StageLogRefs {

    private StageLogRefs() {
    }

    /**
     * 生成单条消息的统一引用文本。
     *
     * 当前统一格式：
     * msgId={...},ueId={...},msgType={...}
     */
    public static String message(SignalingMessage msg) {
        if (msg == null) {
            return "msg:null";
        }

        return "msgId=" + safe(msg.getMsgId())
                + ",ueId=" + safe(msg.getUeId())
                + ",msgType=" + safe(msg.getMsgType());
    }

    /**
     * 基于处理上下文生成消息引用文本。
     */
    /**
     * 安全输出 pcap 路径。
     */
    public static String pcap(Path pcap) {
        return pcap == null ? "UNKNOWN" : pcap.toString();
    }

    /**
     * 安全输出集合大小。
     */
    public static int size(Set<?> values) {
        return values == null ? 0 : values.size();
    }

    /**
     * 安全输出通用字符串值。
     */
    public static String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
