package com.example.procedure.processing.message;

import com.example.procedure.decodebridge.DecryptResultReentryService;
import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.decrypt.DecryptClient;
import com.example.procedure.decrypt.DecryptResponse;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.parser.NasInfo;
import com.example.procedure.parser.PdcpInfo;
import com.example.procedure.service.ReentryNodeMergeSupport;
import com.example.procedure.util.SignalingMessagePrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;

/**
 * 解密协同器。
 *
 * 职责：
 * 1. 判断当前消息是否需要解密
 * 2. 根据加密类型选择 NAS / PDCP 解密策略
 * 3. 处理解密成功后的回流重解析
 * 4. 控制递归解密深度
 *
 * 说明：
 * - 阶段 1 先把 MsgProcessing_Service 中的“解密复杂度”单独收口
 * - 不改变原有对外行为，只做职责拆分
 */
@Service
public class DecryptCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DecryptCoordinator.class);

    /** 防止回流后仍加密导致无限递归 */
    private static final int MAX_DECRYPT_DEPTH = 4;

    /** 当前仍沿用原来的解密服务地址，阶段 2 再抽配置 */
    private static final String DECRYPT_URL = "http://127.0.0.1:8004/decrypt";

    private final ObjectMapper objectMapper;
    private final DecryptResultReentryService decryptResultReentryService;

    public DecryptCoordinator(
            ObjectMapper objectMapper,
            DecryptResultReentryService decryptResultReentryService
    ) {
        this.objectMapper = objectMapper;
        this.decryptResultReentryService = decryptResultReentryService;
    }

    /**
     * 统一处理“加密消息”的入口。
     *
     * 返回值约定：
     * - 返回 null：表示后续流程继续执行
     * - 返回非 null：表示当前消息已经完成阶段性处理，应提前返回
     *
     * 之所以在阶段 1 就保留这个约定，是为了尽量少改外部主流程结构，
     * 让重构的风险更可控。
     */
    public DecryptAttemptResult handleEncryptedMessageIfNeeded(MessageProcessingContext context) {
        if (!context.isEncrypted()) {
            return null;
        }

        SignalingMessage msg = context.getMessage();
        String encType = context.getEncryptedType();
        UEContext ueContext = context.getUeContext();

        DecryptAttemptResult decryptResult = tryDecryptByType(msg, encType, ueContext);
        context.setDecryptResult(decryptResult);

        // 记录日志时保留原消息信息，便于和旧日志对齐
        if (decryptResult.getStatus() == DecryptAttemptResult.Status.WAITING) {
            log.info("Decrypt waiting: ueId={}, reason={}, msgId={}, encType={}",
                    msg.getUeId(), decryptResult.getReason(), msg.getMsgId(), encType);
        } else if (decryptResult.getStatus() == DecryptAttemptResult.Status.FAILED) {
            log.warn("Decrypt failed: ueId={}, msgId={}, encType={}, err={}",
                    msg.getUeId(), msg.getMsgId(), encType, decryptResult.getError());
        }

        return decryptResult;
    }

    /**
     * 解密成功后的回流处理。
     *
     * 返回值：
     * - true  : 已进行了回流，且消息可能还需要外层再次递归处理
     * - false : 没有发生有效回流
     */
    public boolean handleDecryptSuccess(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();
        String encType = context.getEncryptedType();

        try {
            reenterDecryptedMessage(msg, encType);
            return true;
        } catch (Exception e) {
            log.error("Decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                    msg.getUeId(), msg.getMsgId(), encType, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 对某条消息执行一次“按类型分派的解密尝试”。
     */
    public DecryptAttemptResult tryDecryptByType(SignalingMessage msg, String encType, UEContext ctx) {
        String normalizedEncType = normalizeEncType(encType);

        if ("NONE".equals(normalizedEncType)) {
            return DecryptAttemptResult.skip();
        }

        int depth = safeDecryptDepth(msg);
        if (depth >= MAX_DECRYPT_DEPTH) {
            return DecryptAttemptResult.failed(
                    "decrypt max depth reached: " + depth + ", encType=" + normalizedEncType
            );
        }

        if ("NAS".equals(normalizedEncType)) {
            return decryptNasLayers(msg, ctx);
        }

        if ("PDCP".equals(normalizedEncType)) {
            return decryptAs(msg, ctx);
        }

        if ("NAS+PDCP".equals(normalizedEncType)) {
            // 保持旧行为：优先尝试 NAS，NAS 不行再尝试 PDCP
            DecryptAttemptResult nasResult = decryptNasLayers(msg, ctx);

            if (nasResult.getStatus() == DecryptAttemptResult.Status.OK) {
                return nasResult;
            }

            if (nasResult.getStatus() == DecryptAttemptResult.Status.WAITING) {
                return nasResult;
            }

            return decryptAs(msg, ctx);
        }

        return DecryptAttemptResult.skip();
    }

    /**
     * NAS 层解密。
     *
     * 说明：
     * - 每轮只处理一条仍处于加密态的 NAS
     * - 保持与旧逻辑一致：拿到明文后，挂回当前 SignalingMessage 上
     */
    private DecryptAttemptResult decryptNasLayers(SignalingMessage msg, UEContext ctx) {
        if (msg.getNasList() == null || msg.getNasList().isEmpty()) {
            return DecryptAttemptResult.skip();
        }

        for (int i = 0; i < msg.getNasList().size(); i++) {
            NasInfo nas = msg.getNasList().get(i);
            if (nas == null || !nas.isEncrypted()) {
                continue;
            }

            // 尚未拿到 NAS 密钥：进入等待
            if (ctx == null || isBlank(ctx.getKNasEnc()) || isBlank(ctx.getKNasInt())) {
                return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_NAS_KEYS);
            }

            // 算法号未准备好：进入等待
            if (isBlank(ctx.getNasCipherAlg()) || isBlank(ctx.getNasIntAlg())) {
                return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_ALG);
            }

            // 缺密文或 MAC 时，当前 NAS 不可解，继续找下一条
            if (isBlank(nas.getCipherTextHex()) || isBlank(nas.getMsgAuthCodeHex())) {
                continue;
            }

            DecryptClient.DecryptRequest request = new DecryptClient.DecryptRequest();
            request.messageId = msg.getMsgId();
            request.ueId = msg.getUeId();
            request.contextRef = msg.getUeId();
            request.layer = "NAS";

            request.encKey = ctx.getKNasEnc();
            request.intKey = ctx.getKNasInt();

            request.encAlgo = mapNasEncAlgo(ctx.getNasCipherAlg());
            request.intAlgo = mapNasIntAlgo(ctx.getNasIntAlg());

            request.count = nas.getSeqNoInt();
            request.bearer = 1;
            request.direction = msg.getDirection();

            request.ciphertext = nas.getCipherTextHex();
            request.mac = nas.getMsgAuthCodeHex();
            request.dataLength = 0;

            String responseJson;
            try {
                responseJson = DecryptClient.decrypt(DECRYPT_URL, request);
            } catch (Exception e) {
                return DecryptAttemptResult.failed("NAS decrypt http failed: " + e.getMessage());
            }

            DecryptResponse response;
            try {
                response = objectMapper.readValue(responseJson, DecryptResponse.class);
            } catch (Exception e) {
                return DecryptAttemptResult.failed("NAS decrypt invalid json: " + e.getMessage());
            }

            if (response != null
                    && response.getDecryptStatus() != null
                    && response.getDecryptStatus().equals("DECRYPT_SUCCESS")) {

                msg.setDecryptPlainHex(response.getPlainData());
                msg.setDecryptMacHex(normalizeHex(response.getPlainMac()));

                // 记录当前这轮解密的目标位置，后面回流重解析时要靠它定位原树节点
                msg.setDecryptTargetLayer("NAS");
                msg.setDecryptTargetNasIndex(i);
                msg.setDecryptTargetNodeId(nas.getNodeId());

                return DecryptAttemptResult.ok();
            }

            return DecryptAttemptResult.failed("NAS decrypt failed");
        }

        return DecryptAttemptResult.skip();
    }

    /**
     * PDCP / AS 层解密。
     */
    private DecryptAttemptResult decryptAs(SignalingMessage msg, UEContext ctx) {
        PdcpInfo pdcp = msg.getPdcpInfo();
        if (pdcp == null || !pdcp.isPdcpencrypted()) {
            return DecryptAttemptResult.skip();
        }

        if (ctx == null) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_RRC_KEYS);
        }

        if (isBlank(ctx.getKRrcEnc()) || isBlank(ctx.getKRrcInt())) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_RRC_KEYS);
        }

        if (isBlank(ctx.getRrcCipherAlg()) || isBlank(ctx.getRrcIntAlg())) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_ALG);
        }

        if (isBlank(pdcp.getSignallingDataHex()) || isBlank(pdcp.getMacHex())) {
            return DecryptAttemptResult.failed("AS decrypt missing ciphertext/mac");
        }

        DecryptClient.DecryptRequest request = new DecryptClient.DecryptRequest();
        request.messageId = msg.getMsgId();
        request.ueId = msg.getUeId();
        request.contextRef = msg.getUeId();
        request.layer = "AS";

        request.encKey = ctx.getKRrcEnc();
        request.intKey = ctx.getKRrcInt();

        request.encAlgo = mapRrcEncAlgo(ctx.getRrcCipherAlg());
        request.intAlgo = mapRrcIntAlgo(ctx.getRrcIntAlg());

        request.count = pdcp.getSeqNumInt();
        request.bearer = 0;
        request.direction = msg.getDirection();

        request.ciphertext = pdcp.getSignallingDataHex();
        request.mac = pdcp.getMacHex();
        request.dataLength = 0;

        String responseJson;
        try {
            responseJson = DecryptClient.decrypt(DECRYPT_URL, request);
        } catch (Exception e) {
            return DecryptAttemptResult.failed("AS decrypt http failed: " + e.getMessage());
        }

        DecryptResponse response;
        try {
            response = objectMapper.readValue(responseJson, DecryptResponse.class);
        } catch (Exception e) {
            return DecryptAttemptResult.failed("AS decrypt invalid json: " + e.getMessage());
        }

        if (response != null
                && response.getDecryptStatus() != null
                && response.getDecryptStatus().equals("DECRYPT_SUCCESS")) {

            msg.setDecryptPlainHex(response.getPlainData());
            msg.setDecryptMacHex(normalizeHex(response.getPlainMac()));

            // PDCP 解密的锚点是当前 PDCP 节点
            msg.setDecryptTargetLayer("PDCP");
            if (msg.getPdcpInfo() != null) {
                msg.setDecryptTargetNodeId(msg.getPdcpInfo().getNodeId());
            }

            return DecryptAttemptResult.ok();
        }

        return DecryptAttemptResult.failed("AS decrypt failed");
    }

    /**
     * 将解密得到的明文重新解析，并把结果合并回原消息树。
     *
     * 这是当前原型系统最关键的桥接点之一：
     * “解密 -> 重新解析 -> 合并回原始消息树 -> 再继续流程处理”
     */
    private void reenterDecryptedMessage(SignalingMessage msg, String decryptedLayer) throws Exception {
        decryptResultReentryService.reenter(msg, reparsedMsg -> {

            // 先把来源锚点传给回流后的消息，便于 merge 时找到原树节点
            attachReparsedSourceNodeId(msg, reparsedMsg);

            if ("NAS".equals(decryptedLayer)) {
                mergeNasDecodedContent(msg, reparsedMsg);
            } else if ("PDCP".equals(decryptedLayer)) {
                mergePdcpDecodedContent(msg, reparsedMsg);
            } else if ("NAS+PDCP".equals(decryptedLayer)) {
                // 兜底策略：两边都尝试合并
                mergeNasDecodedContent(msg, reparsedMsg);
                mergePdcpDecodedContent(msg, reparsedMsg);
            }

            // 回写当前消息的加密状态
            msg.setEncrypted(
                    reparsedMsg.getEncrypted() != null ? reparsedMsg.getEncrypted() : false
            );
            msg.setEncryptedType(
                    !isBlank(reparsedMsg.getEncryptedType())
                            ? normalizeEncType(reparsedMsg.getEncryptedType())
                            : "NONE"
            );

            // 优先保留当前轮已经拿到的明文和 MAC
            if (isBlank(msg.getDecryptPlainHex()) && !isBlank(reparsedMsg.getDecryptPlainHex())) {
                msg.setDecryptPlainHex(reparsedMsg.getDecryptPlainHex());
            }
            if (isBlank(msg.getDecryptMacHex()) && !isBlank(reparsedMsg.getDecryptMacHex())) {
                msg.setDecryptMacHex(reparsedMsg.getDecryptMacHex());
            }

            // 标记这条消息已经经历过一次成功解密
            msg.setDecrypted(true);
            msg.setDecryptDepth(safeDecryptDepth(msg) + 1);
            msg.setDecryptPath(appendDecryptPath(msg.getDecryptPath(), normalizeEncType(decryptedLayer)));

            if (msg.getEncrypted() == null) {
                msg.setEncrypted(false);
            }
            if (isBlank(msg.getEncryptedType())) {
                msg.setEncryptedType("NONE");
            }

            // 保留旧调试输出，便于比对阶段 1 前后行为
            SignalingMessagePrinter.printAndWriteToFile(
                    msg,
                    Paths.get("logs/signaling_reentry_dump.log"),
                    true
            );
        });
    }

    /**
     * 把“本轮解密所针对的原始节点 ID”挂到回流消息上。
     *
     * 这是回流 merge 成功的关键前置条件。
     */
    private void attachReparsedSourceNodeId(SignalingMessage originalMsg, SignalingMessage reparsedMsg) {
        if (originalMsg == null || reparsedMsg == null) {
            return;
        }
        if (isBlank(originalMsg.getDecryptTargetNodeId())) {
            return;
        }

        reparsedMsg.setReentrySourceNodeId(originalMsg.getDecryptTargetNodeId());

        if (reparsedMsg.getNasList() != null && !reparsedMsg.getNasList().isEmpty()) {
            NasInfo nas = reparsedMsg.getNasList().get(0);
            if (nas != null && isBlank(nas.getSourceNodeId())) {
                nas.setSourceNodeId(originalMsg.getDecryptTargetNodeId());
            }
        }

        if (reparsedMsg.getRrcInfo() != null && isBlank(reparsedMsg.getRrcInfo().getSourceNodeId())) {
            reparsedMsg.getRrcInfo().setSourceNodeId(originalMsg.getDecryptTargetNodeId());
        }

        if (reparsedMsg.getPdcpInfo() != null && isBlank(reparsedMsg.getPdcpInfo().getSourceNodeId())) {
            reparsedMsg.getPdcpInfo().setSourceNodeId(originalMsg.getDecryptTargetNodeId());
        }
    }

    /**
     * 把回流后的 NAS 明文内容合并回原消息树。
     */
    private void mergeNasDecodedContent(SignalingMessage originalMsg, SignalingMessage reparsedMsg) {
        if (originalMsg == null || reparsedMsg == null) {
            return;
        }
        if (originalMsg.getNasList() == null || originalMsg.getNasList().isEmpty()) {
            return;
        }
        if (reparsedMsg.getNasList() == null || reparsedMsg.getNasList().isEmpty()) {
            return;
        }

        String sourceNodeId = reparsedMsg.getReentrySourceNodeId();
        if (isBlank(sourceNodeId) && !reparsedMsg.getNasList().isEmpty()) {
            NasInfo reparsedRootNas = reparsedMsg.getNasList().get(0);
            if (reparsedRootNas != null) {
                sourceNodeId = reparsedRootNas.getSourceNodeId();
            }
        }
        if (isBlank(sourceNodeId)) {
            sourceNodeId = originalMsg.getDecryptTargetNodeId();
        }
        if (isBlank(sourceNodeId)) {
            return;
        }

        NasInfo targetNas = ReentryNodeMergeSupport.findNasByNodeId(originalMsg, sourceNodeId);
        if (targetNas == null) {
            log.warn("mergeNasDecodedContent skip: target NAS not found, msgId={}, sourceNodeId={}",
                    originalMsg.getMsgId(), sourceNodeId);
            return;
        }

        NasInfo reparsedRootNas = reparsedMsg.getNasList().get(0);
        if (reparsedRootNas == null) {
            return;
        }

        // 1. 将回流后的 NAS 明文字段覆盖到原目标 NAS 节点
        ReentryNodeMergeSupport.mergeNasPayloadFields(
                targetNas,
                reparsedRootNas,
                originalMsg.getDecryptPlainHex()
        );

        // 2. 将回流树中的子节点 graft 到原始树上
        ReentryNodeMergeSupport.graftReparsedTreeIntoOriginal(
                originalMsg,
                reparsedMsg,
                sourceNodeId,
                true
        );

        if (!isBlank(reparsedMsg.getMsgType())) {
            originalMsg.setMsgType(reparsedMsg.getMsgType());
        }
        if (!isBlank(reparsedMsg.getProtocolLayer())) {
            originalMsg.setProtocolLayer(reparsedMsg.getProtocolLayer());
        }
    }

    /**
     * 把回流后的 PDCP / RRC 相关内容合并回原消息树。
     *
     * 这里先尽量保持你当前代码的语义：如果回流后上层已经能解析出
     * RRC / NAS / MsgType / ProtocolLayer，就把这些信息回填给原始消息。
     */
    private void mergePdcpDecodedContent(SignalingMessage originalMsg, SignalingMessage reparsedMsg) {
        if (originalMsg == null || reparsedMsg == null) {
            return;
        }

        if (reparsedMsg.getRrcInfo() != null) {
            originalMsg.setRrcInfo(reparsedMsg.getRrcInfo());
        }

        if (reparsedMsg.getNasList() != null && !reparsedMsg.getNasList().isEmpty()) {
            originalMsg.setNasList(reparsedMsg.getNasList());
        }

        if (!isBlank(reparsedMsg.getMsgType())) {
            originalMsg.setMsgType(reparsedMsg.getMsgType());
        }

        if (!isBlank(reparsedMsg.getProtocolLayer())) {
            originalMsg.setProtocolLayer(reparsedMsg.getProtocolLayer());
        }

        if (reparsedMsg.getMessageTree() != null) {
            originalMsg.setMessageTree(reparsedMsg.getMessageTree());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String mapNasEncAlgo(String value) {
        if ("2".equals(value)) {
            return "NEA2";
        }
        if ("3".equals(value)) {
            return "NEA3";
        }
        return "NEA1";
    }

    private String mapNasIntAlgo(String value) {
        if ("2".equals(value)) {
            return "NIA2";
        }
        if ("3".equals(value)) {
            return "NIA3";
        }
        return "NIA1";
    }

    private String mapRrcEncAlgo(String value) {
        if ("2".equals(value)) {
            return "NEA2";
        }
        if ("3".equals(value)) {
            return "NEA3";
        }
        return "NEA1";
    }

    private String mapRrcIntAlgo(String value) {
        if ("2".equals(value)) {
            return "NIA2";
        }
        if ("3".equals(value)) {
            return "NIA3";
        }
        return "NIA1";
    }

    private static String normalizeHex(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.startsWith("0x") || v.startsWith("0X")) {
            v = v.substring(2);
        }
        v = v.replace(":", "").replace(" ", "");
        return v.toLowerCase();
    }

    private int safeDecryptDepth(SignalingMessage msg) {
        if (msg == null || msg.getDecryptDepth() == null) {
            return 0;
        }
        return Math.max(msg.getDecryptDepth(), 0);
    }

    private String normalizeEncType(String encType) {
        if (isBlank(encType)) {
            return "NONE";
        }
        return encType.trim().toUpperCase();
    }

    private String appendDecryptPath(String oldPath, String layer) {
        if (isBlank(layer) || "NONE".equals(layer)) {
            return oldPath;
        }
        if (isBlank(oldPath)) {
            return layer;
        }
        return oldPath + "->" + layer;
    }
}