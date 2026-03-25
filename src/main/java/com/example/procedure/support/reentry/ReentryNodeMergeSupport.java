package com.example.procedure.support.reentry;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.tree.MessageNode;
import com.example.procedure.model.tree.MessageNodeType;
import com.example.procedure.model.tree.MessageTree;
import com.example.procedure.parser.NasInfo;
import com.example.procedure.parser.PdcpInfo;
import com.example.procedure.parser.RrcInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解密回流后的节点合并工具。
 *
 * 当前定位：
 * 1. 这是解密回流相关的通用支持工具
 * 2. 它不属于 service 层，而属于 support 层的通用树合并支持
 * 3. 当前迁移到 support.reentry 包，是为了让包结构更清晰
 */
public final class ReentryNodeMergeSupport {

    /**
     * 工具类不允许实例化。
     */
    private ReentryNodeMergeSupport() {
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param s 输入字符串
     * @return true 表示为空白
     */
    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 在原始消息树中查找目标节点。
     *
     * @param originalMsg 原始消息
     * @param sourceNodeId 源节点 ID
     * @return 找到的原始目标节点；找不到则返回 null
     */
    public static MessageNode findOriginalTargetNode(SignalingMessage originalMsg, String sourceNodeId) {
        if (originalMsg == null || originalMsg.getMessageTree() == null || isBlank(sourceNodeId)) {
            return null;
        }
        return originalMsg.getMessageTree().getNode(sourceNodeId);
    }

    /**
     * 查找回流消息树真正的根 payload 节点。
     *
     * 当前策略：
     * - 取 ROOT 的第一个 child
     *
     * @param reparsedMsg 回流后重新解析的消息
     * @return 根 payload 节点；找不到则返回 null
     */
    public static MessageNode findReparsedRootPayloadNode(SignalingMessage reparsedMsg) {
        if (reparsedMsg == null || reparsedMsg.getMessageTree() == null) {
            return null;
        }

        MessageTree tree = reparsedMsg.getMessageTree();
        if (isBlank(tree.getRootNodeId())) {
            return null;
        }

        List<MessageNode> children = tree.getChildren(tree.getRootNodeId());
        if (children == null || children.isEmpty()) {
            return null;
        }

        return children.get(0);
    }

    /**
     * 根据节点 ID 查找 NAS payload。
     *
     * @param msg 当前消息
     * @param nodeId 节点 ID
     * @return 找到的 NAS payload；找不到则返回 null
     */
    public static NasInfo findNasByNodeId(SignalingMessage msg, String nodeId) {
        if (msg == null || isBlank(nodeId) || msg.getNasList() == null) {
            return null;
        }
        for (NasInfo nas : msg.getNasList()) {
            if (nas != null && nodeId.equals(nas.getNodeId())) {
                return nas;
            }
        }
        return null;
    }

    /**
     * 根据节点 ID 查找 RRC payload。
     *
     * @param msg 当前消息
     * @param nodeId 节点 ID
     * @return 找到的 RRC payload；找不到则返回 null
     */
    public static RrcInfo findRrcByNodeId(SignalingMessage msg, String nodeId) {
        if (msg == null || isBlank(nodeId)) {
            return null;
        }
        RrcInfo rrc = msg.getRrcInfo();
        if (rrc != null && nodeId.equals(rrc.getNodeId())) {
            return rrc;
        }
        return null;
    }

    /**
     * 根据节点 ID 查找 PDCP payload。
     *
     * @param msg 当前消息
     * @param nodeId 节点 ID
     * @return 找到的 PDCP payload；找不到则返回 null
     */
    public static PdcpInfo findPdcpByNodeId(SignalingMessage msg, String nodeId) {
        if (msg == null || isBlank(nodeId)) {
            return null;
        }
        PdcpInfo pdcp = msg.getPdcpInfo();
        if (pdcp != null && nodeId.equals(pdcp.getNodeId())) {
            return pdcp;
        }
        return null;
    }

    /**
     * 保留原始 NAS 密文轨迹。
     *
     * @param target 目标 NAS payload
     */
    public static void preserveOriginalNasCipherTrace(NasInfo target) {
        if (target == null) {
            return;
        }

        if (isBlank(target.getOriginalFullNasPduHex())) {
            target.setOriginalFullNasPduHex(target.getFullNasPduHex());
        }
        if (isBlank(target.getOriginalCipherTextHex())) {
            target.setOriginalCipherTextHex(target.getCipherTextHex());
        }
    }

    /**
     * 合并 NAS 解密后的 payload 字段。
     *
     * @param target 原始目标 NAS
     * @param decodedRoot 解密后重新解析出的 NAS 根节点
     * @param decryptPlainHex 解密得到的明文 hex
     */
    public static void mergeNasPayloadFields(NasInfo target, NasInfo decodedRoot, String decryptPlainHex) {
        if (target == null || decodedRoot == null) {
            return;
        }

        preserveOriginalNasCipherTrace(target);

        if (!isBlank(decryptPlainHex)) {
            target.setDecryptedTexHex(decryptPlainHex);
        }

        target.setEncrypted(false);
        target.setCipherTextHex(null);

        target.setNasNode(decodedRoot.getNasNode());
        target.setFullNasPduHex(decodedRoot.getFullNasPduHex());

        target.setEpd(decodedRoot.getEpd());
        target.setSpareHalfOctet(decodedRoot.getSpareHalfOctet());
        target.setSecurityHeaderType(decodedRoot.getSecurityHeaderType());
        target.setMsgAuthCodeHex(decodedRoot.getMsgAuthCodeHex());
        target.setSeqNo(decodedRoot.getSeqNo());

        target.setMmMessageType(decodedRoot.getMmMessageType());
        target.setNas_cipheringAlgorithm(decodedRoot.getNas_cipheringAlgorithm());
        target.setNas_integrityProtAlgorithm(decodedRoot.getNas_integrityProtAlgorithm());

        target.setGuamiMcc(decodedRoot.getGuamiMcc());
        target.setGuamiMnc(decodedRoot.getGuamiMnc());
        target.setTmsi(decodedRoot.getTmsi());
        target.setRegType5gs(decodedRoot.getRegType5gs());

        if (decodedRoot.getFieldPaths() != null && !decodedRoot.getFieldPaths().isEmpty()) {
            Map<String, String> merged = new LinkedHashMap<>();
            if (target.getFieldPaths() != null) {
                merged.putAll(target.getFieldPaths());
            }
            merged.putAll(decodedRoot.getFieldPaths());
            target.setFieldPaths(merged);
        }
    }

    /**
     * 合并 RRC 解密后的 payload 字段。
     *
     * @param target 原始目标 RRC
     * @param decodedRoot 解密后重新解析出的 RRC 根节点
     */
    public static void mergeRrcPayloadFields(RrcInfo target, RrcInfo decodedRoot) {
        if (target == null || decodedRoot == null) {
            return;
        }

        target.setDirection(decodedRoot.getDirection());
        target.setMsgName(decodedRoot.getMsgName());

        target.setRandomValueHex(decodedRoot.getRandomValueHex());
        target.setEstablishmentCause(decodedRoot.getEstablishmentCause());
        target.setCrnti(decodedRoot.getCrnti());

        target.setIntegrityProtAlgorithm(decodedRoot.getIntegrityProtAlgorithm());
        target.setCipheringAlgorithm(decodedRoot.getCipheringAlgorithm());
        target.setHasDedicatedNas(decodedRoot.isHasDedicatedNas());

        if (decodedRoot.getFieldPaths() != null && !decodedRoot.getFieldPaths().isEmpty()) {
            Map<String, String> merged = new LinkedHashMap<>();
            if (target.getFieldPaths() != null) {
                merged.putAll(target.getFieldPaths());
            }
            merged.putAll(decodedRoot.getFieldPaths());
            target.setFieldPaths(merged);
        }
    }

    /**
     * 保留原始 PDCP 密文轨迹。
     *
     * @param target 目标 PDCP payload
     */
    public static void preserveOriginalPdcpCipherTrace(PdcpInfo target) {
        if (target == null) {
            return;
        }
        if (isBlank(target.getOriginalSignallingDataHex())) {
            target.setOriginalSignallingDataHex(target.getSignallingDataHex());
        }
    }

    /**
     * 合并 PDCP 解密轨迹。
     *
     * @param target 原始目标 PDCP
     * @param decryptPlainHex 解密得到的明文 hex
     * @param decryptMacHex 解密得到的明文 MAC
     */
    public static void mergePdcpDecryptTrace(PdcpInfo target, String decryptPlainHex, String decryptMacHex) {
        if (target == null) {
            return;
        }

        preserveOriginalPdcpCipherTrace(target);

        if (!isBlank(decryptPlainHex)) {
            target.setDecyptedTexHex(decryptPlainHex);
        }
        if (!isBlank(decryptMacHex)) {
            target.setMacHex(decryptMacHex);
        }

        target.setPdcpencrypted(false);
        target.setSignallingDataHex(null);
    }

    /**
     * 把回流后的子树 graft 回原始消息树。
     *
     * @param originalMsg 原始消息
     * @param reparsedMsg 回流解析结果
     * @param sourceNodeId 原始目标节点 ID
     * @param sameTypeAsTarget 是否与目标节点同类型
     */
    public static void graftReparsedTreeIntoOriginal(
            SignalingMessage originalMsg,
            SignalingMessage reparsedMsg,
            String sourceNodeId,
            boolean sameTypeAsTarget
    ) {
        if (originalMsg == null || reparsedMsg == null || isBlank(sourceNodeId)) {
            return;
        }

        MessageTree originalTree = originalMsg.getMessageTree();
        MessageTree reparsedTree = reparsedMsg.getMessageTree();
        if (originalTree == null || reparsedTree == null) {
            return;
        }

        MessageNode originalTarget = originalTree.getNode(sourceNodeId);
        if (originalTarget == null) {
            return;
        }

        MessageNode reparsedRoot = findReparsedRootPayloadNode(reparsedMsg);
        if (reparsedRoot == null) {
            return;
        }

        List<MessageNode> nodesToAttach;
        if (sameTypeAsTarget) {
            nodesToAttach = reparsedTree.getChildren(reparsedRoot.getNodeId());
        } else {
            nodesToAttach = List.of(reparsedRoot);
        }

        if (nodesToAttach == null || nodesToAttach.isEmpty()) {
            return;
        }

        List<String> childIds = originalTarget.getChildNodeIds();
        if (childIds == null) {
            childIds = new ArrayList<>();
            originalTarget.setChildNodeIds(childIds);
        }
        Set<String> dedup = new LinkedHashSet<>(childIds);

        for (MessageNode node : nodesToAttach) {
            String graftedId = cloneSubtreeIntoOriginalTree(
                    node,
                    reparsedTree,
                    originalTree,
                    sourceNodeId
            );
            if (!isBlank(graftedId)) {
                dedup.add(graftedId);
            }
        }

        originalTarget.setChildNodeIds(new ArrayList<>(dedup));
    }

    /**
     * 递归复制回流子树到原始消息树。
     *
     * @param reparsedNode 当前回流节点
     * @param reparsedTree 回流消息树
     * @param originalTree 原始消息树
     * @param newParentNodeId 新父节点 ID
     * @return 新节点 ID
     */
    private static String cloneSubtreeIntoOriginalTree(
            MessageNode reparsedNode,
            MessageTree reparsedTree,
            MessageTree originalTree,
            String newParentNodeId
    ) {
        if (reparsedNode == null) {
            return null;
        }

        MessageNode cloned = cloneMessageNode(reparsedNode);
        cloned.setParentNodeId(newParentNodeId);
        cloned.setChildNodeIds(new ArrayList<>());

        originalTree.getNodesById().put(cloned.getNodeId(), cloned);

        List<MessageNode> reparsedChildren = reparsedTree.getChildren(reparsedNode.getNodeId());
        if (reparsedChildren != null && !reparsedChildren.isEmpty()) {
            List<String> clonedChildIds = new ArrayList<>();
            for (MessageNode child : reparsedChildren) {
                String childId = cloneSubtreeIntoOriginalTree(
                        child,
                        reparsedTree,
                        originalTree,
                        cloned.getNodeId()
                );
                if (!isBlank(childId)) {
                    clonedChildIds.add(childId);
                }
            }
            cloned.setChildNodeIds(clonedChildIds);
        }

        return cloned.getNodeId();
    }

    /**
     * 复制一个消息树节点。
     *
     * @param src 原始节点
     * @return 克隆节点
     */
    private static MessageNode cloneMessageNode(MessageNode src) {
        MessageNode node = new MessageNode();
        node.setNodeId(src.getNodeId());
        node.setParentNodeId(src.getParentNodeId());
        node.setPath(src.getPath());
        node.setNodeType(src.getNodeType());
        node.setPayloadIndex(src.getPayloadIndex());
        node.setPayloadSequence(src.getPayloadSequence());
        node.setChildNodeIds(
                src.getChildNodeIds() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(src.getChildNodeIds())
        );
        return node;
    }

    /**
     * 判断两个节点是否为同类型。
     *
     * @param target 原始目标节点
     * @param reparsedRoot 回流根节点
     * @return true 表示同类型
     */
    public static boolean isSameNodeType(MessageNode target, MessageNode reparsedRoot) {
        if (target == null || reparsedRoot == null) {
            return false;
        }
        MessageNodeType a = target.getNodeType();
        MessageNodeType b = reparsedRoot.getNodeType();
        return a != null && a == b;
    }
}
