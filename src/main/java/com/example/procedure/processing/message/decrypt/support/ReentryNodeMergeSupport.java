package com.example.procedure.processing.message.decrypt.support;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.tree.MessageNode;
import com.example.procedure.model.message.tree.MessageNodeType;
import com.example.procedure.model.message.tree.MessageTree;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.model.message.info.RrcInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 鐟欙絽鐦戦崶鐐寸ウ閸氬海娈戦懞鍌滃仯閸氬牆鑻熷銉ュ徔閵?
 *
 * 瑜版挸澧犵€规矮缍呴敍?
 * 1. 鏉╂瑦妲哥憴锝呯槕閸ョ偞绁﹂惄绋垮彠閻ㄥ嫰鈧氨鏁ら弨顖涘瘮瀹搞儱鍙?
 * 2. 鐎瑰啩绗夌仦鐐扮艾 service 鐏炲偊绱濋懓灞界潣娴?support 鐏炲倻娈戦柅姘辨暏閺嶆垵鎮庨獮鑸垫暜閹?
 * 3. 瑜版挸澧犳潻浣盒╅崚?support.reentry 閸栧拑绱濋弰顖欒礋娴滃棜顔€閸栧懐绮ㄩ弸鍕纯濞撳懏娅?
 */
public final class ReentryNodeMergeSupport {

    /**
     * 瀹搞儱鍙跨猾璁崇瑝閸忎浇顔忕€圭偘绶ラ崠鏍モ偓?
     */
    private ReentryNodeMergeSupport() {
    }

    /**
     * 閸掋倖鏌囩€涙顑佹稉鍙夋Ц閸氾缚璐熺粚铏规閵?
     *
     * @param s 鏉堟挸鍙嗙€涙顑佹稉?
     * @return true 鐞涖劎銇氭稉铏光敄閻?
     */
    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 閸︺劌甯慨瀣Х閹垱鐖叉稉顓熺叀閹靛墽娲伴弽鍥Ν閻愬箍鈧?
     *
     * @param originalMsg 閸樼喎顫愬☉鍫熶紖
     * @param sourceNodeId 濠ф劘濡悙?ID
     * @return 閹垫儳鍩岄惃鍕斧婵娲伴弽鍥Ν閻愮櫢绱遍幍鍙ョ瑝閸掓澘鍨潻鏂挎礀 null
     */
    public static MessageNode findOriginalTargetNode(SignalingMessage originalMsg, String sourceNodeId) {
        if (originalMsg == null || originalMsg.getMessageTree() == null || isBlank(sourceNodeId)) {
            return null;
        }
        return originalMsg.getMessageTree().getNode(sourceNodeId);
    }

    /**
     * 閺屻儲澹橀崶鐐寸ウ濞戝牊浼呴弽鎴犳埂濮濓絿娈戦弽?payload 閼哄倻鍋ｉ妴?
     *
     * 瑜版挸澧犵粵鏍殣閿?
     * - 閸?ROOT 閻ㄥ嫮顑囨稉鈧稉?child
     *
     * @param reparsedMsg 閸ョ偞绁﹂崥搴ㄥ櫢閺傛媽袙閺嬫劗娈戝☉鍫熶紖
     * @return 閺?payload 閼哄倻鍋ｉ敍娑欏娑撳秴鍩岄崚娆掔箲閸?null
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
     * 閺嶈宓侀懞鍌滃仯 ID 閺屻儲澹?NAS payload閵?
     *
     * @param msg 瑜版挸澧犲☉鍫熶紖
     * @param nodeId 閼哄倻鍋?ID
     * @return 閹垫儳鍩岄惃?NAS payload閿涙稒澹樻稉宥呭煂閸掓瑨绻戦崶?null
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
     * 閺嶈宓侀懞鍌滃仯 ID 閺屻儲澹?RRC payload閵?
     *
     * @param msg 瑜版挸澧犲☉鍫熶紖
     * @param nodeId 閼哄倻鍋?ID
     * @return 閹垫儳鍩岄惃?RRC payload閿涙稒澹樻稉宥呭煂閸掓瑨绻戦崶?null
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
     * 閺嶈宓侀懞鍌滃仯 ID 閺屻儲澹?PDCP payload閵?
     *
     * @param msg 瑜版挸澧犲☉鍫熶紖
     * @param nodeId 閼哄倻鍋?ID
     * @return 閹垫儳鍩岄惃?PDCP payload閿涙稒澹樻稉宥呭煂閸掓瑨绻戦崶?null
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
     * 娣囨繄鏆€閸樼喎顫?NAS 鐎靛棙鏋冩潪銊ㄦ姉閵?
     *
     * @param target 閻╊喗鐖?NAS payload
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
     * 閸氬牆鑻?NAS 鐟欙絽鐦戦崥搴ｆ畱 payload 鐎涙顔岄妴?
     *
     * @param target 閸樼喎顫愰惄顔界垼 NAS
     * @param decodedRoot 鐟欙絽鐦戦崥搴ㄥ櫢閺傛媽袙閺嬫劕鍤惃?NAS 閺嶇濡悙?
     * @param decryptPlainHex 鐟欙絽鐦戝妤€鍩岄惃鍕閺?hex
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
     * 閸氬牆鑻?RRC 鐟欙絽鐦戦崥搴ｆ畱 payload 鐎涙顔岄妴?
     *
     * @param target 閸樼喎顫愰惄顔界垼 RRC
     * @param decodedRoot 鐟欙絽鐦戦崥搴ㄥ櫢閺傛媽袙閺嬫劕鍤惃?RRC 閺嶇濡悙?
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
     * 娣囨繄鏆€閸樼喎顫?PDCP 鐎靛棙鏋冩潪銊ㄦ姉閵?
     *
     * @param target 閻╊喗鐖?PDCP payload
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
     * 閸氬牆鑻?PDCP 鐟欙絽鐦戞潪銊ㄦ姉閵?
     *
     * @param target 閸樼喎顫愰惄顔界垼 PDCP
     * @param decryptPlainHex 鐟欙絽鐦戝妤€鍩岄惃鍕閺?hex
     * @param decryptMacHex 鐟欙絽鐦戝妤€鍩岄惃鍕閺?MAC
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
     * 閹跺﹤娲栧ù浣告倵閻ㄥ嫬鐡欓弽?graft 閸ョ偛甯慨瀣Х閹垱鐖查妴?
     *
     * @param originalMsg 閸樼喎顫愬☉鍫熶紖
     * @param reparsedMsg 閸ョ偞绁︾憴锝嗙€界紒鎾寸亯
     * @param sourceNodeId 閸樼喎顫愰惄顔界垼閼哄倻鍋?ID
     * @param sameTypeAsTarget 閺勵垰鎯佹稉搴ｆ窗閺嶅洩濡悙鐟版倱缁鐎?
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
     * 闁帒缍婃径宥呭煑閸ョ偞绁︾€涙劖鐖查崚鏉垮斧婵绉烽幁顖涚埐閵?
     *
     * @param reparsedNode 瑜版挸澧犻崶鐐寸ウ閼哄倻鍋?
     * @param reparsedTree 閸ョ偞绁﹀☉鍫熶紖閺?
     * @param originalTree 閸樼喎顫愬☉鍫熶紖閺?
     * @param newParentNodeId 閺傛壆鍩楅懞鍌滃仯 ID
     * @return 閺傛媽濡悙?ID
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
     * 婢跺秴鍩楁稉鈧稉顏呯Х閹垱鐖查懞鍌滃仯閵?
     *
     * @param src 閸樼喎顫愰懞鍌滃仯
     * @return 閸忓娈曢懞鍌滃仯
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
     * 閸掋倖鏌囨稉銈勯嚋閼哄倻鍋ｉ弰顖氭儊娑撳搫鎮撶猾璇茬€烽妴?
     *
     * @param target 閸樼喎顫愰惄顔界垼閼哄倻鍋?
     * @param reparsedRoot 閸ョ偞绁﹂弽纭呭Ν閻?
     * @return true 鐞涖劎銇氶崥宀€琚崹?
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
