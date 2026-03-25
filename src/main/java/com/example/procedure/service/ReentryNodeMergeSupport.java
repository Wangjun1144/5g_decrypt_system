package com.example.procedure.service;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.tree.MessageNode;
import com.example.procedure.parser.NasInfo;
import com.example.procedure.parser.PdcpInfo;
import com.example.procedure.parser.RrcInfo;

/**
 * @deprecated 旧的解密回流节点合并工具兼容层。
 *
 * 当前保留原因：
 * 1. 旧代码可能还依赖 service.ReentryNodeMergeSupport
 * 2. 新的正式实现已经迁到 support.reentry.ReentryNodeMergeSupport
 * 3. 这里收缩为兼容壳，避免旧引用立即失效
 */
@Deprecated
public final class ReentryNodeMergeSupport {

    /**
     * 工具类不允许实例化。
     */
    private ReentryNodeMergeSupport() {
    }

    /**
     * 兼容旧接口：判断字符串是否为空白。
     *
     * @param s 输入字符串
     * @return true 表示为空白
     */
    // REFACTOR STEP: SERVICE_PACKAGE_CLEANUP
    public static boolean isBlank(String s) {
        return com.example.procedure.support.reentry.ReentryNodeMergeSupport.isBlank(s);
    }

    /**
     * 兼容旧接口：查找原始目标节点。
     *
     * @param originalMsg 原始消息
     * @param sourceNodeId 源节点 ID
     * @return 找到的节点
     */
    public static MessageNode findOriginalTargetNode(SignalingMessage originalMsg, String sourceNodeId) {
        return com.example.procedure.support.reentry.ReentryNodeMergeSupport.findOriginalTargetNode(originalMsg, sourceNodeId);
    }

    /**
     * 兼容旧接口：查找回流根 payload 节点。
     *
     * @param reparsedMsg 回流消息
     * @return 根 payload 节点
     */
    public static MessageNode findReparsedRootPayloadNode(SignalingMessage reparsedMsg) {
        return com.example.procedure.support.reentry.ReentryNodeMergeSupport.findReparsedRootPayloadNode(reparsedMsg);
    }

    /**
     * 兼容旧接口：根据节点 ID 查找 NAS。
     *
     * @param msg 当前消息
     * @param nodeId 节点 ID
     * @return NAS payload
     */
    public static NasInfo findNasByNodeId(SignalingMessage msg, String nodeId) {
        return com.example.procedure.support.reentry.ReentryNodeMergeSupport.findNasByNodeId(msg, nodeId);
    }

    /**
     * 兼容旧接口：根据节点 ID 查找 RRC。
     *
     * @param msg 当前消息
     * @param nodeId 节点 ID
     * @return RRC payload
     */
    public static RrcInfo findRrcByNodeId(SignalingMessage msg, String nodeId) {
        return com.example.procedure.support.reentry.ReentryNodeMergeSupport.findRrcByNodeId(msg, nodeId);
    }

    /**
     * 兼容旧接口：根据节点 ID 查找 PDCP。
     *
     * @param msg 当前消息
     * @param nodeId 节点 ID
     * @return PDCP payload
     */
    public static PdcpInfo findPdcpByNodeId(SignalingMessage msg, String nodeId) {
        return com.example.procedure.support.reentry.ReentryNodeMergeSupport.findPdcpByNodeId(msg, nodeId);
    }

    /**
     * 兼容旧接口：保留原始 NAS 密文轨迹。
     *
     * @param target 目标 NAS
     */
    public static void preserveOriginalNasCipherTrace(NasInfo target) {
        com.example.procedure.support.reentry.ReentryNodeMergeSupport.preserveOriginalNasCipherTrace(target);
    }

    /**
     * 兼容旧接口：合并 NAS payload 字段。
     *
     * @param target 原始目标 NAS
     * @param decodedRoot 解密后 NAS 根节点
     * @param decryptPlainHex 解密明文
     */
    public static void mergeNasPayloadFields(NasInfo target, NasInfo decodedRoot, String decryptPlainHex) {
        com.example.procedure.support.reentry.ReentryNodeMergeSupport.mergeNasPayloadFields(target, decodedRoot, decryptPlainHex);
    }

    /**
     * 兼容旧接口：合并 RRC payload 字段。
     *
     * @param target 原始目标 RRC
     * @param decodedRoot 解密后 RRC 根节点
     */
    public static void mergeRrcPayloadFields(RrcInfo target, RrcInfo decodedRoot) {
        com.example.procedure.support.reentry.ReentryNodeMergeSupport.mergeRrcPayloadFields(target, decodedRoot);
    }

    /**
     * 兼容旧接口：保留原始 PDCP 密文轨迹。
     *
     * @param target 目标 PDCP
     */
    public static void preserveOriginalPdcpCipherTrace(PdcpInfo target) {
        com.example.procedure.support.reentry.ReentryNodeMergeSupport.preserveOriginalPdcpCipherTrace(target);
    }

    /**
     * 兼容旧接口：合并 PDCP 解密轨迹。
     *
     * @param target 原始目标 PDCP
     * @param decryptPlainHex 解密明文
     * @param decryptMacHex 解密 MAC
     */
    public static void mergePdcpDecryptTrace(PdcpInfo target, String decryptPlainHex, String decryptMacHex) {
        com.example.procedure.support.reentry.ReentryNodeMergeSupport.mergePdcpDecryptTrace(target, decryptPlainHex, decryptMacHex);
    }

    /**
     * 兼容旧接口：把回流子树 graft 回原始消息树。
     *
     * @param originalMsg 原始消息
     * @param reparsedMsg 回流消息
     * @param sourceNodeId 目标节点 ID
     * @param sameTypeAsTarget 是否同类型
     */
    public static void graftReparsedTreeIntoOriginal(
            SignalingMessage originalMsg,
            SignalingMessage reparsedMsg,
            String sourceNodeId,
            boolean sameTypeAsTarget
    ) {
        com.example.procedure.support.reentry.ReentryNodeMergeSupport.graftReparsedTreeIntoOriginal(
                originalMsg,
                reparsedMsg,
                sourceNodeId,
                sameTypeAsTarget
        );
    }

    /**
     * 兼容旧接口：判断两个节点是否同类型。
     *
     * @param target 原始目标节点
     * @param reparsedRoot 回流根节点
     * @return true 表示同类型
     */
    public static boolean isSameNodeType(MessageNode target, MessageNode reparsedRoot) {
        return com.example.procedure.support.reentry.ReentryNodeMergeSupport.isSameNodeType(target, reparsedRoot);
    }
}
