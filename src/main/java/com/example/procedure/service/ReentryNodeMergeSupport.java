package com.example.procedure.service;

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

final class ReentryNodeMergeSupport {

    private ReentryNodeMergeSupport() {}

    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // =========================
    // 通用树查询
    // =========================

    static MessageNode findOriginalTargetNode(SignalingMessage originalMsg, String sourceNodeId) {
        if (originalMsg == null || originalMsg.getMessageTree() == null || isBlank(sourceNodeId)) {
            return null;
        }
        return originalMsg.getMessageTree().getNode(sourceNodeId);
    }

    /**
     * 回流树真正的“根 payload 节点”：
     * 取 ROOT 的第一个 child。
     * 这比“默认第一个 NAS”更通用，适用于 NAS / RRC / PDCP 回流。
     */
    static MessageNode findReparsedRootPayloadNode(SignalingMessage reparsedMsg) {
        if (reparsedMsg == null || reparsedMsg.getMessageTree() == null) return null;

        MessageTree t = reparsedMsg.getMessageTree();
        if (isBlank(t.getRootNodeId())) return null;

        List<MessageNode> children = t.getChildren(t.getRootNodeId());
        if (children == null || children.isEmpty()) return null;

        return children.get(0);
    }

    // =========================
    // payload 查找
    // =========================

    static NasInfo findNasByNodeId(SignalingMessage msg, String nodeId) {
        if (msg == null || isBlank(nodeId) || msg.getNasList() == null) return null;
        for (NasInfo nas : msg.getNasList()) {
            if (nas != null && nodeId.equals(nas.getNodeId())) {
                return nas;
            }
        }
        return null;
    }

    static RrcInfo findRrcByNodeId(SignalingMessage msg, String nodeId) {
        if (msg == null || isBlank(nodeId)) return null;
        RrcInfo rrc = msg.getRrcInfo();
        if (rrc != null && nodeId.equals(rrc.getNodeId())) {
            return rrc;
        }
        return null;
    }

    static PdcpInfo findPdcpByNodeId(SignalingMessage msg, String nodeId) {
        if (msg == null || isBlank(nodeId)) return null;
        PdcpInfo pdcp = msg.getPdcpInfo();
        if (pdcp != null && nodeId.equals(pdcp.getNodeId())) {
            return pdcp;
        }
        return null;
    }

    // =========================
    // NAS merge
    // =========================

    static void preserveOriginalNasCipherTrace(NasInfo target) {
        if (target == null) return;

        if (isBlank(target.getOriginalFullNasPduHex())) {
            target.setOriginalFullNasPduHex(target.getFullNasPduHex());
        }
        if (isBlank(target.getOriginalCipherTextHex())) {
            target.setOriginalCipherTextHex(target.getCipherTextHex());
        }
    }

    static void mergeNasPayloadFields(NasInfo target, NasInfo decodedRoot, String decryptPlainHex) {
        if (target == null || decodedRoot == null) return;

        preserveOriginalNasCipherTrace(target);

        if (!isBlank(decryptPlainHex)) {
            target.setDecryptedTexHex(decryptPlainHex);
        }

        target.setEncrypted(false);
        target.setCipherTextHex(null);

        // 直接把明文语义 merge 到原节点
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

    // =========================
    // RRC merge
    // =========================

    static void mergeRrcPayloadFields(RrcInfo target, RrcInfo decodedRoot) {
        if (target == null || decodedRoot == null) return;

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

    // =========================
    // PDCP merge
    // =========================

    static void preserveOriginalPdcpCipherTrace(PdcpInfo target) {
        if (target == null) return;
        if (isBlank(target.getOriginalSignallingDataHex())) {
            target.setOriginalSignallingDataHex(target.getSignallingDataHex());
        }
    }

    /**
     * AS/PDCP 解密后的目标节点仍然是原 PDCP 节点。
     * 这里不把它替换成 RRC，而是：
     * - 保留原 nodeId / 原树位置
     * - 标记为已解密
     * - 记住原始密文和明文
     * - 语义子树（RRC/NAS）用 graftChildren 接回原 PDCP 节点下面
     */
    static void mergePdcpDecryptTrace(PdcpInfo target, String decryptPlainHex, String decryptMacHex) {
        if (target == null) return;

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

    // =========================
    // 子树 graft
    // =========================

    /**
     * 把回流根节点本身 merge 到 sourceNodeId 后，决定是否 graft root or graft children：
     *
     * - sameTypeAsTarget = true：
     *   回流根和原目标是同类型（例如 NAS -> NAS）。
     *   此时只 graft 回流根的 children。
     *
     * - sameTypeAsTarget = false：
     *   回流根和原目标不是同类型（例如 PDCP 解密后得到 RRC 根）。
     *   此时要把“回流根整棵树” graft 到原目标下面。
     */
    static void graftReparsedTreeIntoOriginal(
            SignalingMessage originalMsg,
            SignalingMessage reparsedMsg,
            String sourceNodeId,
            boolean sameTypeAsTarget
    ) {
        if (originalMsg == null || reparsedMsg == null || isBlank(sourceNodeId)) return;

        MessageTree originalTree = originalMsg.getMessageTree();
        MessageTree reparsedTree = reparsedMsg.getMessageTree();
        if (originalTree == null || reparsedTree == null) return;

        MessageNode originalTarget = originalTree.getNode(sourceNodeId);
        if (originalTarget == null) return;

        MessageNode reparsedRoot = findReparsedRootPayloadNode(reparsedMsg);
        if (reparsedRoot == null) return;

        List<MessageNode> nodesToAttach;
        if (sameTypeAsTarget) {
            nodesToAttach = reparsedTree.getChildren(reparsedRoot.getNodeId());
        } else {
            nodesToAttach = List.of(reparsedRoot);
        }

        if (nodesToAttach == null || nodesToAttach.isEmpty()) return;

        List<String> childIds = originalTarget.getChildNodeIds();
        if (childIds == null) {
            childIds = new ArrayList<>();
            originalTarget.setChildNodeIds(childIds);
        }
        Set<String> dedup = new LinkedHashSet<>(childIds);

        for (MessageNode n : nodesToAttach) {
            String graftedId = cloneSubtreeIntoOriginalTree(
                    n,
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

    private static String cloneSubtreeIntoOriginalTree(
            MessageNode reparsedNode,
            MessageTree reparsedTree,
            MessageTree originalTree,
            String newParentNodeId
    ) {
        if (reparsedNode == null) return null;

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

    private static MessageNode cloneMessageNode(MessageNode src) {
        MessageNode n = new MessageNode();
        n.setNodeId(src.getNodeId());
        n.setParentNodeId(src.getParentNodeId());
        n.setPath(src.getPath());
        n.setNodeType(src.getNodeType());
        n.setPayloadIndex(src.getPayloadIndex());
        n.setPayloadSequence(src.getPayloadSequence());
        n.setChildNodeIds(
                src.getChildNodeIds() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(src.getChildNodeIds())
        );
        return n;
    }

    static boolean isSameNodeType(MessageNode target, MessageNode reparsedRoot) {
        if (target == null || reparsedRoot == null) return false;
        MessageNodeType a = target.getNodeType();
        MessageNodeType b = reparsedRoot.getNodeType();
        return a != null && a == b;
    }
}