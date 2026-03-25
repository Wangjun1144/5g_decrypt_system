package com.example.procedure.processing.message;

import com.example.procedure.decodebridge.DecryptResultReentryService;
import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.decrypt.DecryptClient;
import com.example.procedure.decrypt.DecryptGateway;
import com.example.procedure.decrypt.DecryptResponse;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.parser.NasInfo;
import com.example.procedure.parser.PdcpInfo;
// REFACTOR STEP: SERVICE_PACKAGE_CLEANUP
import com.example.procedure.support.reentry.ReentryNodeMergeSupport;
import com.example.procedure.util.SignalingMessagePrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;

/**
 * 解密协调器。
 *
 * 当前职责：
 * 1. 判断当前消息是否需要解密
 * 2. 根据加密类型选择 NAS / PDCP 解密策略
 * 3. 在解密成功后执行回流重解析
 * 4. 控制解密回流深度
 *
 * 当前阶段的重要变化：
 * - 不再直接依赖静态 HTTP 调用工具
 * - 改为依赖正式的 DecryptGateway 边界
 * - 这样既提升当前单体质量，也为未来拆分独立解密服务铺路
 */
@Service
public class DecryptCoordinator {

    /**
     * 日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(DecryptCoordinator.class);

    /**
     * 防止解密回流后再次进入无限递归。
     */
    private static final int MAX_DECRYPT_DEPTH = 4;

    /**
     * 外部解密能力访问边界。
     */
    private final DecryptGateway decryptGateway;

    /**
     * 解密成功后的回流服务。
     */
    private final DecryptResultReentryService decryptResultReentryService;

    /**
     * 构造解密协调器。
     *
     * @param decryptGateway 外部解密网关
     * @param decryptResultReentryService 解密回流服务
     */
    public DecryptCoordinator(
            DecryptGateway decryptGateway,
            DecryptResultReentryService decryptResultReentryService
    ) {
        this.decryptGateway = decryptGateway;
        this.decryptResultReentryService = decryptResultReentryService;
    }

    /**
     * 统一处理“加密消息”的入口。
     *
     * 返回约定：
     * - 返回 null：表示当前消息无需在此阶段提前结束，主链继续
     * - 返回非 null：表示当前消息已经得到明确解密处理结果
     *
     * @param context 当前消息处理上下文
     * @return 解密尝试结果，或 null
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
     * 处理解密成功后的回流。
     *
     * 返回值语义：
     * - true：成功回流，外层主链应重新处理这条消息
     * - false：回流失败或没有发生有效回流
     *
     * @param context 当前消息处理上下文
     * @return 是否发生有效回流
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
     * 按加密类型尝试执行解密。
     *
     * 当前支持：
     * - NAS
     * - PDCP
     * - NAS+PDCP
     * - NONE / 其他未知类型
     *
     * @param msg 当前消息
     * @param encType 当前加密类型
     * @param ctx 当前 UE 上下文
     * @return 解密尝试结果
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
     * 解密 NAS 层。
     *
     * 当前策略：
     * 1. 只处理仍然处于加密态的 NAS
     * 2. 如果缺 key 或算法，则进入 WAITING
     * 3. 如果外部解密成功，则把明文结果写回当前消息
     *
     * @param msg 当前消息
     * @param ctx 当前 UE 上下文
     * @return 解密尝试结果
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

            if (ctx == null || isBlank(ctx.getKNasEnc()) || isBlank(ctx.getKNasInt())) {
                return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_NAS_KEYS);
            }

            if (isBlank(ctx.getNasCipherAlg()) || isBlank(ctx.getNasIntAlg())) {
                return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_ALG);
            }

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

            DecryptResponse response;
            try {
                response = decryptGateway.decrypt(request);
            } catch (Exception e) {
                return DecryptAttemptResult.failed("NAS decrypt failed: " + e.getMessage());
            }

            if (response != null
                    && response.getDecryptStatus() != null
                    && response.getDecryptStatus().equals("DECRYPT_SUCCESS")) {

                msg.setDecryptPlainHex(response.getPlainData());
                msg.setDecryptMacHex(normalizeHex(response.getPlainMac()));
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
     * 解密 PDCP / AS 层。
     *
     * 当前策略：
     * 1. 需要 RRC/PDCP 相关 key 和算法准备齐全
     * 2. 外部解密成功后，把明文结果写回当前消息
     *
     * @param msg 当前消息
     * @param ctx 当前 UE 上下文
     * @return 解密尝试结果
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

        DecryptResponse response;
        try {
            response = decryptGateway.decrypt(request);
        } catch (Exception e) {
            return DecryptAttemptResult.failed("AS decrypt failed: " + e.getMessage());
        }

        if (response != null
                && response.getDecryptStatus() != null
                && response.getDecryptStatus().equals("DECRYPT_SUCCESS")) {

            msg.setDecryptPlainHex(response.getPlainData());
            msg.setDecryptMacHex(normalizeHex(response.getPlainMac()));
            msg.setDecryptTargetLayer("PDCP");
            if (msg.getPdcpInfo() != null) {
                msg.setDecryptTargetNodeId(msg.getPdcpInfo().getNodeId());
            }

            return DecryptAttemptResult.ok();
        }

        return DecryptAttemptResult.failed("AS decrypt failed");
    }

    /**
     * 把解密得到的明文重新解析，并把结果合并回原消息树。
     *
     * @param msg 原始消息
     * @param decryptedLayer 当前解密层类型
     * @throws Exception 回流解析失败时抛出异常
     */
    private void reenterDecryptedMessage(SignalingMessage msg, String decryptedLayer) throws Exception {
        decryptResultReentryService.reenter(msg, reparsedMsg -> {
            attachReparsedSourceNodeId(msg, reparsedMsg);

            if ("NAS".equals(decryptedLayer)) {
                mergeNasDecodedContent(msg, reparsedMsg);
            } else if ("PDCP".equals(decryptedLayer)) {
                mergePdcpDecodedContent(msg, reparsedMsg);
            } else if ("NAS+PDCP".equals(decryptedLayer)) {
                mergeNasDecodedContent(msg, reparsedMsg);
                mergePdcpDecodedContent(msg, reparsedMsg);
            }

            msg.setEncrypted(
                    reparsedMsg.getEncrypted() != null ? reparsedMsg.getEncrypted() : false
            );
            msg.setEncryptedType(
                    !isBlank(reparsedMsg.getEncryptedType())
                            ? normalizeEncType(reparsedMsg.getEncryptedType())
                            : "NONE"
            );

            if (isBlank(msg.getDecryptPlainHex()) && !isBlank(reparsedMsg.getDecryptPlainHex())) {
                msg.setDecryptPlainHex(reparsedMsg.getDecryptPlainHex());
            }
            if (isBlank(msg.getDecryptMacHex()) && !isBlank(reparsedMsg.getDecryptMacHex())) {
                msg.setDecryptMacHex(reparsedMsg.getDecryptMacHex());
            }

            msg.setDecrypted(true);
            msg.setDecryptDepth(safeDecryptDepth(msg) + 1);
            msg.setDecryptPath(appendDecryptPath(msg.getDecryptPath(), normalizeEncType(decryptedLayer)));

            if (msg.getEncrypted() == null) {
                msg.setEncrypted(false);
            }
            if (isBlank(msg.getEncryptedType())) {
                msg.setEncryptedType("NONE");
            }

            SignalingMessagePrinter.printAndWriteToFile(
                    msg,
                    Paths.get("logs/signaling_reentry_dump.log"),
                    true
            );
        });
    }

    /**
     * 把原始目标节点 ID 透传给回流后的消息。
     *
     * 这样后续 merge 才能找到原树上的正确节点。
     *
     * @param originalMsg 原始消息
     * @param reparsedMsg 回流后重新解析出的消息
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
     * 合并 NAS 回流内容。
     *
     * @param originalMsg 原始消息
     * @param reparsedMsg 回流解析结果
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

        ReentryNodeMergeSupport.mergeNasPayloadFields(
                targetNas,
                reparsedRootNas,
                originalMsg.getDecryptPlainHex()
        );

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
     * 合并 PDCP / RRC 回流内容。
     *
     * @param originalMsg 原始消息
     * @param reparsedMsg 回流解析结果
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

    /**
     * 判断字符串是否为空白。
     *
     * @param value 输入字符串
     * @return true 表示为空
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 映射 NAS 加密算法代码到网关使用的算法名。
     *
     * @param value 原始算法值
     * @return 算法名
     */
    private String mapNasEncAlgo(String value) {
        if ("2".equals(value)) {
            return "NEA2";
        }
        if ("3".equals(value)) {
            return "NEA3";
        }
        return "NEA1";
    }

    /**
     * 映射 NAS 完整性算法代码到网关使用的算法名。
     *
     * @param value 原始算法值
     * @return 算法名
     */
    private String mapNasIntAlgo(String value) {
        if ("2".equals(value)) {
            return "NIA2";
        }
        if ("3".equals(value)) {
            return "NIA3";
        }
        return "NIA1";
    }

    /**
     * 映射 RRC 加密算法代码到网关使用的算法名。
     *
     * @param value 原始算法值
     * @return 算法名
     */
    private String mapRrcEncAlgo(String value) {
        if ("2".equals(value)) {
            return "NEA2";
        }
        if ("3".equals(value)) {
            return "NEA3";
        }
        return "NEA1";
    }

    /**
     * 映射 RRC 完整性算法代码到网关使用的算法名。
     *
     * @param value 原始算法值
     * @return 算法名
     */
    private String mapRrcIntAlgo(String value) {
        if ("2".equals(value)) {
            return "NIA2";
        }
        if ("3".equals(value)) {
            return "NIA3";
        }
        return "NIA1";
    }

    /**
     * 规范化十六进制字符串。
     *
     * @param value 原始 hex 字符串
     * @return 规范化后的 hex
     */
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

    /**
     * 安全读取当前消息的解密深度。
     *
     * @param msg 当前消息
     * @return 当前深度
     */
    private int safeDecryptDepth(SignalingMessage msg) {
        if (msg == null || msg.getDecryptDepth() == null) {
            return 0;
        }
        return Math.max(msg.getDecryptDepth(), 0);
    }

    /**
     * 规范化加密类型。
     *
     * @param encType 原始加密类型
     * @return 规范化后的加密类型
     */
    private String normalizeEncType(String encType) {
        if (isBlank(encType)) {
            return "NONE";
        }
        return encType.trim().toUpperCase();
    }

    /**
     * 在已有解密路径上追加一层路径标记。
     *
     * @param oldPath 原有路径
     * @param layer 当前新增层
     * @return 新路径
     */
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
