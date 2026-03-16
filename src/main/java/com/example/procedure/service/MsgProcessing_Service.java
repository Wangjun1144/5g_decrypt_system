package com.example.procedure.service;

import com.example.procedure.decodebridge.DecryptResultReentryService;
import com.example.procedure.decrypt.DecryptAttemptResult;
import com.example.procedure.decrypt.DecryptClient;
import com.example.procedure.decrypt.DecryptResponse;
import com.example.procedure.model.*;
import com.example.procedure.parser.NasInfo;
import com.example.procedure.parser.PdcpInfo;
import com.example.procedure.rule.MessageCategoryClassifier;
import com.example.procedure.util.SignalingMessagePrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * DEMO 版消息处理主模块：
 * 负责：分类 → （可选）流程判别 → 调度后续功能。
 */

@Service
public class MsgProcessing_Service {
    private final UEContextService ueContextService;
    private final ObjectMapper objectMapper;

    private static final int MAX_DECRYPT_DEPTH = 4;


    private static final Logger log = LoggerFactory.getLogger(MsgProcessing_Service.class);

    private final MessageCategoryClassifier messageCategoryClassifier;
    private final ProClassify_Service proClassifyService;

    private final ProDispatcher_Service proDispatcherService;

    private final PendingMessageService pendingMessageService;
    private final DecryptResultReentryService decryptResultReentryService;

    public MsgProcessing_Service(
            UEContextService ueContextService, ObjectMapper objectMapper,
            MessageCategoryClassifier messageCategoryClassifier,
            ProClassify_Service proClassifyService,
            ProDispatcher_Service proDispatcherService,
            PendingMessageService pendingMessageService,
            DecryptResultReentryService decryptResultReentryService
    ){
        this.ueContextService = ueContextService;
        this.objectMapper = objectMapper;
        this.messageCategoryClassifier = messageCategoryClassifier;
        this.proClassifyService = proClassifyService;
        this.proDispatcherService = proDispatcherService;
        this.pendingMessageService = pendingMessageService;
        this.decryptResultReentryService = decryptResultReentryService;
    }

    private boolean nasKeyReady(UEContext ctx) {
        return ctx != null && !isBlank(ctx.getKNasEnc()) && !isBlank(ctx.getKNasInt());
    }
    private boolean rrcKeyReady(UEContext ctx) {
        return ctx != null && !isBlank(ctx.getKRrcEnc()) && !isBlank(ctx.getKRrcInt());
    }

    /**
     * 消息处理主入口
     *
     * 处理顺序：
     * 1. 分类
     * 2. 拉取 UEContext
     * 3. 如有加密则优先处理解密/等待/失败
     * 4. 如果是流程相关消息，则做流程匹配
     * 5. 做后续分发
     * 6. 上下文更新后，重试 pending 解密消息
     * 7. 返回处理结果
     *
     * 说明：
     * - 本次重构不改变功能，只减少重复代码、降低主方法复杂度
     */
    public MessageProcessingResult process(SignalingMessage msg) {
        MessageProcessingContext context = new MessageProcessingContext(msg);

        // 1) 先做消息分类
        context.setCategory(messageCategoryClassifier.classify(msg));

        // 2) 读取当前 UEContext
        context.setUeContext(ueContextService.getContext(msg.getUeId()));

        // 3) 如果消息加密，统一在这里处理，不再分散到多个分支里重复写
        MessageProcessingResult earlyReturn = handleEncryptedMessageIfNeeded(context);
        if (earlyReturn != null) {
            return earlyReturn;
        }

        // 4) 只有流程相关消息才做流程匹配
        if (isProcedureMessage(context.getCategory())) {
            context.setProcedureMatchResult(proClassifyService.handleMessage(msg));
        }

        // 5) 统一分发
        dispatchMessage(context);

        // 6) dispatch 后上下文可能已经被更新（例如拿到了新的 key）
        //    所以这里重新读取最新上下文，再尝试重试 pending 解密消息
        UEContext refreshedCtx = ueContextService.getContext(msg.getUeId());
        retryPendingDecrypt(msg.getUeId(), refreshedCtx);

        // 7) 构造统一返回结果
        return buildResult(context);
    }


    /**
     * 判断消息是否属于流程相关消息
     *
     * 目前只有流程驱动消息和流程辅助消息需要进入流程识别模块。
     */
    private boolean isProcedureMessage(MessageCategory category) {
        return category == MessageCategory.PROCEDURE_DRIVING
                || category == MessageCategory.PROCEDURE_AUX;
    }

    /**
     * 统一处理“加密消息”的入口
     *
     * 返回值约定：
     * - 返回 null：表示后续流程继续执行
     * - 返回非 null：表示当前消息已经完成阶段性处理，应提前返回
     */
    private MessageProcessingResult handleEncryptedMessageIfNeeded(MessageProcessingContext context) {
        if (!context.isEncrypted()) {
            return null;
        }

        SignalingMessage msg = context.getMessage();
        String encType = context.getEncryptedType();
        UEContext ueContext = context.getUeContext();

        DecryptAttemptResult dr = tryDecryptByType(msg, encType, ueContext);
        context.setDecryptResult(dr);

        // 解密成功：先回流重解析；如果解密后内部仍然加密，则递归继续处理
        if (dr.getStatus() == DecryptAttemptResult.Status.OK) {
            return handleDecryptSuccess(context);
        }

        // 等待型结果：说明缺 key / 缺算法 / 缺参数，需要进 pending 队列
        if (dr.getStatus() == DecryptAttemptResult.Status.WAITING) {
            pendingMessageService.enqueue(msg.getUeId(), msg, dr.getReason());

            log.info("Decrypt waiting: ueId={}, reason={}, msgId={}, encType={}",
                    msg.getUeId(), dr.getReason(), msg.getMsgId(), encType);

            return buildResult(context);
        }

        // 失败型结果：先保留原行为，记录日志后继续后续流程
        if (dr.getStatus() == DecryptAttemptResult.Status.FAILED) {
            log.warn("Decrypt failed: ueId={}, msgId={}, encType={}, err={}",
                    msg.getUeId(), msg.getMsgId(), encType, dr.getError());
        }

        return null;
    }

    /**
     * 解密成功后的处理
     *
     * 注意：
     * - reenterDecryptedMessage(...) 会把明文重新解析并合并回原消息树
     * - 如果合并后消息内部仍然显示为加密，则继续递归调用 process(msg)
     */
    private MessageProcessingResult handleDecryptSuccess(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();
        String encType = context.getEncryptedType();

        try {
            reenterDecryptedMessage(msg, encType);
        } catch (Exception e) {
            log.error("Decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                    msg.getUeId(), msg.getMsgId(), encType, e.getMessage(), e);
            return buildResult(context);
        }

        // 如果回流后的消息内部仍有加密层，则继续对同一条消息递归处理
        if (Boolean.TRUE.equals(msg.getEncrypted())) {
            log.info("Decrypt recursion continue: ueId={}, msgId={}, depth={}, encType={}, path={}",
                    msg.getUeId(), msg.getMsgId(), safeDecryptDepth(msg),
                    msg.getEncryptedType(), msg.getDecryptPath());
            return process(msg);
        }

        return null;
    }

    /**
     * 统一做消息分发
     *
     * 这里把 procedureId / procedureTypeCode 的组装逻辑收口，
     * 避免 process(...) 主流程里出现太多细节代码。
     */
    private void dispatchMessage(MessageProcessingContext context) {
        SignalingMessage msg = context.getMessage();

        String procedureId = null;
        String procedureTypeCode = null;

        if (context.getProcedureMatchResult() != null
                && context.getProcedureMatchResult().getStatus() == 0) {
            procedureId = context.getProcedureMatchResult().getProcedureId();

            ProcedureTypeEnum typeEnum = context.getProcedureMatchResult().getProcedureType();
            if (typeEnum != null) {
                procedureTypeCode = typeEnum.getCode();
            }
        }

        proDispatcherService.dispatch(
                msg,
                context.getCategory(),
                procedureId,
                procedureTypeCode
        );
    }

    /**
     * 统一构造返回值
     *
     * 这样无论是主流程结束、等待返回、异常后返回，都复用同一套结果构造逻辑。
     */
    private MessageProcessingResult buildResult(MessageProcessingContext context) {
        String procedureId = null;
        String procedureTypeCode = null;

        if (context.getProcedureMatchResult() != null
                && context.getProcedureMatchResult().getStatus() == 0) {
            procedureId = context.getProcedureMatchResult().getProcedureId();

            ProcedureTypeEnum typeEnum = context.getProcedureMatchResult().getProcedureType();
            if (typeEnum != null) {
                procedureTypeCode = typeEnum.getCode();
            }
        }

        SignalingMessage msg = context.getMessage();

        return new MessageProcessingResult(
                msg.getUeId(),
                msg.getMsgType(),
                context.getCategory(),
                procedureId,
                procedureTypeCode
        );
    }



    /**
     * 按加密类型选择解密策略
     *
     * 注意：
     * 这里不改变原有功能，只修复一个问题：
     * normalizedEncType 之前算出来了，但原代码后面判断时仍使用 encType，
     * 导致规范化结果实际上没有生效。
     */
    private DecryptAttemptResult tryDecryptByType(SignalingMessage msg, String encType, UEContext ctx) {
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

        String url = "http://127.0.0.1:8004/decrypt";

        // 仅 NAS 加密
        if ("NAS".equals(normalizedEncType)) {
            return decryptNasLayers(url, msg, ctx);
        }

        // 仅 PDCP/AS 加密
        if ("PDCP".equals(normalizedEncType)) {
            return decryptAs(url, msg, ctx);
        }

        // 先 NAS，后 PDCP
        if ("NAS+PDCP".equals(normalizedEncType)) {
            DecryptAttemptResult nas = decryptNasLayers(url, msg, ctx);

            // 如果 NAS 这一步成功，先返回，后面会通过回流继续处理
            if (nas.getStatus() == DecryptAttemptResult.Status.OK) {
                return nas;
            }

            // 如果 NAS 仍在等待材料/密钥，也直接返回等待
            if (nas.getStatus() == DecryptAttemptResult.Status.WAITING) {
                return nas;
            }

            // NAS 不可解时，再尝试 PDCP
            return decryptAs(url, msg, ctx);
        }

        return DecryptAttemptResult.skip();
    }


    private DecryptAttemptResult decryptNasLayers(String url, SignalingMessage msg, UEContext ctx) {
        if (msg.getNasList() == null || msg.getNasList().isEmpty()) {
            return DecryptAttemptResult.skip();
        }

        // 每轮只解一个目标：找到第一条仍然加密的 NAS
        for (int i = 0; i < msg.getNasList().size(); i++) {
            NasInfo nas = msg.getNasList().get(i);
            if (nas == null || !nas.isEncrypted()) {
                continue;
            }

            // 缺 key：等待
            if (ctx == null || isBlank(ctx.getKNasEnc()) || isBlank(ctx.getKNasInt())) {
                return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_NAS_KEYS);
            }

            // 缺算法号：等待
            if (isBlank(ctx.getNasCipherAlg()) || isBlank(ctx.getNasIntAlg())) {
                return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_ALG);
            }

            // 缺密文或 mac，这条 NAS 暂时解不了，继续找下一条
            if (isBlank(nas.getCipherTextHex()) || isBlank(nas.getMsgAuthCodeHex())) {
                continue;
            }

            DecryptClient.DecryptRequest req = new DecryptClient.DecryptRequest();
            req.messageId = msg.getMsgId();
            req.ueId = msg.getUeId();
            req.contextRef = msg.getUeId();
            req.layer = "NAS";

            req.encKey = ctx.getKNasEnc();
            req.intKey = ctx.getKNasInt();

            req.encAlgo = mapNasEncAlgo(ctx.getNasCipherAlg());
            req.intAlgo = mapNasIntAlgo(ctx.getNasIntAlg());

            req.count = nas.getSeqNoInt();
            req.bearer = 1; // 你后续再按服务约定细化
            req.direction = msg.getDirection();

            req.ciphertext = nas.getCipherTextHex();
            req.mac = nas.getMsgAuthCodeHex();
            req.dataLength = 0;

            String respJson;
            try {
                respJson = DecryptClient.decrypt(url, req);
            } catch (Exception e) {
                return DecryptAttemptResult.failed("NAS decrypt http failed: " + e.getMessage());
            }

            DecryptResponse resp;
            try {
                resp = objectMapper.readValue(respJson, DecryptResponse.class);
            } catch (Exception ex) {
                return DecryptAttemptResult.failed("NAS decrypt invalid json: " + ex.getMessage());
            }

            if (resp != null
                    && resp.getDecryptStatus() != null
                    && resp.getDecryptStatus().equals("DECRYPT_SUCCESS")) {

                msg.setDecryptPlainHex(resp.getPlainData());
                msg.setDecryptMacHex(normalizeHex(resp.getPlainMac()));

                // 记录本轮解密目标
                msg.setDecryptTargetLayer("NAS");
                msg.setDecryptTargetNasIndex(i); // 兼容旧逻辑
                msg.setDecryptTargetNodeId(nas.getNodeId());

                return DecryptAttemptResult.ok();
            }

            return DecryptAttemptResult.failed("NAS decrypt failed");
        }

        // 有 nasList，但没有可解目标
        return DecryptAttemptResult.skip();
    }


    private DecryptAttemptResult decryptAs(String url, SignalingMessage msg, UEContext ctx) {
        PdcpInfo pdcp = msg.getPdcpInfo();
        if (pdcp == null || !pdcp.isPdcpencrypted()) {
            return DecryptAttemptResult.skip();
        }

        // 先判断上下文是否存在
        if (ctx == null) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_RRC_KEYS);
        }

        // 缺 key：等待
        if (isBlank(ctx.getKRrcEnc()) || isBlank(ctx.getKRrcInt())) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_RRC_KEYS);
        }

        // 缺算法号：等待
        if (isBlank(ctx.getRrcCipherAlg()) || isBlank(ctx.getRrcIntAlg())) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_ALG);
        }

        if (isBlank(pdcp.getSignallingDataHex()) || isBlank(pdcp.getMacHex())) {
            return DecryptAttemptResult.failed("AS decrypt missing ciphertext/mac");
        }

        DecryptClient.DecryptRequest req = new DecryptClient.DecryptRequest();
        req.messageId = msg.getMsgId();
        req.ueId = msg.getUeId();
        req.contextRef = msg.getUeId();
        req.layer = "AS";

        req.encKey = ctx.getKRrcEnc();
        req.intKey = ctx.getKRrcInt();

        req.encAlgo = mapRrcEncAlgo(ctx.getRrcCipherAlg());
        req.intAlgo = mapRrcIntAlgo(ctx.getRrcIntAlg());

        req.count = pdcp.getSeqNumInt();
        req.bearer = 0;
        req.direction = msg.getDirection();

        req.ciphertext = pdcp.getSignallingDataHex();
        req.mac = pdcp.getMacHex();
        req.dataLength = 0;

        String respJson;
        try {
            respJson = DecryptClient.decrypt(url, req);
        } catch (Exception e) {
            return DecryptAttemptResult.failed("AS decrypt http failed: " + e.getMessage());
        }

        DecryptResponse resp;
        try {
            resp = objectMapper.readValue(respJson, DecryptResponse.class);
        } catch (Exception ex) {
            return DecryptAttemptResult.failed("AS decrypt invalid json: " + ex.getMessage());
        }

        if (resp != null
                && resp.getDecryptStatus() != null
                && resp.getDecryptStatus().equals("DECRYPT_SUCCESS")) {

            msg.setDecryptPlainHex(resp.getPlainData());
            msg.setDecryptMacHex(normalizeHex(resp.getPlainMac()));

            // PDCP/AS 解密的目标锚点是当前 PDCP 节点
            msg.setDecryptTargetLayer("PDCP");
            if (msg.getPdcpInfo() != null) {
                msg.setDecryptTargetNodeId(msg.getPdcpInfo().getNodeId());
            }

            return DecryptAttemptResult.ok();
        }

        return DecryptAttemptResult.failed("AS decrypt failed");
    }

    private void retryPendingDecrypt(String ueId, UEContext ctx) {
        if (ueId == null || ueId.isEmpty()) return;

        // 没有任何 key 就别白跑
        boolean canTryNas = nasKeyReady(ctx);
        boolean canTryRrc = rrcKeyReady(ctx);
        if (!canTryNas && !canTryRrc) return;

        int batchSize = 200; // 一次最多重试多少条，防止卡死
        List<PendingMessageService.PendingItem> items = pendingMessageService.pollBatch(ueId, batchSize);
        if (items.isEmpty()) return;

        int ok = 0, back = 0, fail = 0;

        for (PendingMessageService.PendingItem it : items) {
            SignalingMessage m = it.msg;
            if (m == null) continue;

            // 如果 encType 是 NAS 但 NAS key 不 ready，则直接放回（避免不必要的 tryDecrypt）
            String encType = m.getEncryptedType();
            if ("NAS".equals(encType) && !canTryNas) {
                pendingMessageService.requeue(ueId, it);
                back++;
                continue;
            }
            if ("PDCP".equals(encType) && !canTryRrc) {
                pendingMessageService.requeue(ueId, it);
                back++;
                continue;
            }
            if ("NAS+PDCP".equals(encType) && (!canTryNas && !canTryRrc)) {
                pendingMessageService.requeue(ueId, it);
                back++;
                continue;
            }

            DecryptAttemptResult dr = tryDecryptByType(m, encType, ctx);

            if (dr.getStatus() == DecryptAttemptResult.Status.OK) {
                if (safeDecryptDepth(m) >= MAX_DECRYPT_DEPTH) {
                    log.warn("Pending decrypt max-depth reached(drop): ueId={}, msgId={}, encType={}, depth={}",
                            ueId, m.getMsgId(), m.getEncryptedType(), safeDecryptDepth(m));
                    continue;
                }
                ok++;

                try {
                    reenterDecryptedMessage(m, encType);

                    // 如果回填后内部仍加密，则继续递归处理同一个消息
                    if (Boolean.TRUE.equals(m.getEncrypted())) {
                        process(m);
                    } else {
                        process(m);
                    }
                } catch (Exception e) {
                    log.error("Decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                            m.getUeId(), m.getMsgId(), encType, e.getMessage(), e);
                }
                log.info("Pending decrypt OK: ueId={}, msgId={}, encType={}", ueId, m.getMsgId(), encType);
                SignalingMessagePrinter.printAndWriteToFile(
                        m, Paths.get("logs/signaling_dump_1.log"), true
                );
                continue;
            }

            if (dr.getStatus() == DecryptAttemptResult.Status.WAITING) {
                // 仍缺材料/参数：放回队尾
                PendingMessageService.PendingItem newItem =
                        new PendingMessageService.PendingItem(System.currentTimeMillis(), it.msgId, dr.getReason(), m);
                pendingMessageService.requeue(ueId, newItem);
                back++;
                continue;
            }

            if (dr.getStatus() == DecryptAttemptResult.Status.FAILED) {
                fail++;
                log.warn("Pending decrypt FAILED(drop): ueId={}, msgId={}, encType={}, err={}",
                        ueId, m.getMsgId(), encType, dr.getError());
                // 失败这里先丢弃（避免永远循环），你后面如果想保留也可以改成 requeue+计数
            } else {
                // SKIP：当作成功“可离队”
                ok++;
            }
        }

        log.info("Pending decrypt retry done: ueId={}, batch={}, ok={}, requeue={}, fail={}, remain={}",
                ueId, items.size(), ok, back, fail, pendingMessageService.size(ueId));
    }

    private void reenterDecryptedMessage(SignalingMessage msg, String decryptedLayer) {
        try {
            decryptResultReentryService.reenter(msg, reparsedMsg -> {

                // 先把来源锚点挂到回流消息
                attachReparsedSourceNodeId(msg, reparsedMsg);

                if ("NAS".equals(decryptedLayer)) {
                    mergeNasDecodedContent(msg, reparsedMsg);
                } else if ("PDCP".equals(decryptedLayer)) {
                    mergePdcpDecodedContent(msg, reparsedMsg);
                } else if ("NAS+PDCP".equals(decryptedLayer)) {
                    // 兜底：两边都试
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

                if (!isBlank(msg.getDecryptPlainHex())) {
                    // 原值保留
                } else if (!isBlank(reparsedMsg.getDecryptPlainHex())) {
                    msg.setDecryptPlainHex(reparsedMsg.getDecryptPlainHex());
                }

                if (isBlank(msg.getDecryptMacHex()) && !isBlank(reparsedMsg.getDecryptMacHex())) {
                    msg.setDecryptMacHex(reparsedMsg.getDecryptMacHex());
                }

                msg.setDecrypted(true);
                msg.setDecryptDepth(safeDecryptDepth(msg) + 1);
                msg.setDecryptPath(
                        appendDecryptPath(msg.getDecryptPath(), normalizeEncType(decryptedLayer))
                );

                // 清理本轮解密目标标记
//                msg.setDecryptTargetLayer(null);
//                msg.setDecryptTargetNasIndex(null);
//                msg.setDecryptTargetNodeId(null);

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
        } catch (Exception e) {
            log.error("Decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                    msg.getUeId(), msg.getMsgId(), msg.getEncryptedType(), e.getMessage(), e);
        }
    }

    private void attachReparsedSourceNodeId(SignalingMessage originalMsg, SignalingMessage reparsedMsg) {
        if (originalMsg == null || reparsedMsg == null) return;
        if (isBlank(originalMsg.getDecryptTargetNodeId())) return;

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

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String mapNasEncAlgo(String s) {
        if ("2".equals(s)) return "NEA2";
        if ("3".equals(s)) return "NEA3";
        return "NEA1";
    }
    private String mapNasIntAlgo(String s) {
        if ("2".equals(s)) return "NIA2";
        if ("3".equals(s)) return "NIA3";
         return "NIA1";
    }

    private String mapRrcEncAlgo(String s) {
        if ("2".equals(s)) return "NEA2";
        if ("3".equals(s)) return "NEA3";
        return "NEA1";
    }
    private String mapRrcIntAlgo(String s) {
        if ("2".equals(s)) return "NIA2";
        if ("3".equals(s)) return "NIA3";
        return "NIA1";
    }

    private static String normalizeHex(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("0x") || v.startsWith("0X")) v = v.substring(2);
        v = v.replace(":", "").replace(" ", "");
        return v.toLowerCase();
    }

    private int safeDecryptDepth(SignalingMessage msg) {
        if (msg == null || msg.getDecryptDepth() == null) return 0;
        return Math.max(msg.getDecryptDepth(), 0);
    }

    private String normalizeEncType(String encType) {
        if (isBlank(encType)) return "NONE";
        return encType.trim().toUpperCase();
    }

    private String appendDecryptPath(String oldPath, String layer) {
        if (isBlank(layer) || "NONE".equals(layer)) return oldPath;
        if (isBlank(oldPath)) return layer;
        return oldPath + "->" + layer;
    }

    private void mergeNasDecodedContent(SignalingMessage originalMsg, SignalingMessage reparsedMsg) {
        if (originalMsg == null || reparsedMsg == null) return;
        if (originalMsg.getNasList() == null || originalMsg.getNasList().isEmpty()) return;
        if (reparsedMsg.getNasList() == null || reparsedMsg.getNasList().isEmpty()) return;

        String sourceNodeId = reparsedMsg.getReentrySourceNodeId();
        if (isBlank(sourceNodeId) && !reparsedMsg.getNasList().isEmpty()) {
            NasInfo rootNas = reparsedMsg.getNasList().get(0);
            if (rootNas != null) {
                sourceNodeId = rootNas.getSourceNodeId();
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

        // 1) 直接合并到原始 NAS 节点，不新建并列 NAS
        ReentryNodeMergeSupport.mergeNasPayloadFields(
                targetNas,
                reparsedRootNas,
                originalMsg.getDecryptPlainHex()
        );

        // 2) NAS -> NAS，同类型根节点，不 graft 根本身，只 graft 它的 children
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

    private NasInfo cloneNasInfo(NasInfo source) {
        if (source == null) return null;

        NasInfo n = new NasInfo();
        n.setSequence(source.getSequence());
        n.setNodeId(source.getNodeId());
        n.setSourceNodeId(source.getSourceNodeId());
        n.setNasNode(source.getNasNode());
        n.setFullNasPduHex(source.getFullNasPduHex());
        n.setCipherTextHex(source.getCipherTextHex());
        n.setDecryptedTexHex(source.getDecryptedTexHex());

        n.setOriginalFullNasPduHex(source.getOriginalFullNasPduHex());
        n.setOriginalCipherTextHex(source.getOriginalCipherTextHex());

        n.setEncrypted(source.isEncrypted());

        n.setEpd(source.getEpd());
        n.setSpareHalfOctet(source.getSpareHalfOctet());
        n.setSecurityHeaderType(source.getSecurityHeaderType());
        n.setMsgAuthCodeHex(source.getMsgAuthCodeHex());
        n.setSeqNo(source.getSeqNo());

        n.setMmMessageType(source.getMmMessageType());
        n.setNas_cipheringAlgorithm(source.getNas_cipheringAlgorithm());
        n.setNas_integrityProtAlgorithm(source.getNas_integrityProtAlgorithm());

        n.setGuamiMcc(source.getGuamiMcc());
        n.setGuamiMnc(source.getGuamiMnc());
        n.setTmsi(source.getTmsi());
        n.setRegType5gs(source.getRegType5gs());

        if (source.getFieldPaths() != null && !source.getFieldPaths().isEmpty()) {
            n.setFieldPaths(new LinkedHashMap<>(source.getFieldPaths()));
        }

        return n;
    }

    private void resequenceNasList(List<NasInfo> nasList) {
        if (nasList == null) return;
        int seq = 1;
        for (NasInfo nas : nasList) {
            if (nas != null) {
                nas.setSequence(seq++);
            }
        }
    }

    private void mergePdcpDecodedContent(SignalingMessage originalMsg, SignalingMessage reparsedMsg) {
        if (originalMsg == null || reparsedMsg == null) return;
        if (originalMsg.getPdcpInfo() == null) return;

        String sourceNodeId = reparsedMsg.getReentrySourceNodeId();
        if (isBlank(sourceNodeId)) {
            sourceNodeId = originalMsg.getDecryptTargetNodeId();
        }
        if (isBlank(sourceNodeId)) {
            return;
        }

        PdcpInfo targetPdcp = ReentryNodeMergeSupport.findPdcpByNodeId(originalMsg, sourceNodeId);
        if (targetPdcp == null) {
            log.warn("mergePdcpDecodedContent skip: target PDCP not found, msgId={}, sourceNodeId={}",
                    originalMsg.getMsgId(), sourceNodeId);
            return;
        }

        // 1) 原 PDCP 节点保留，记录密文痕迹和明文
        ReentryNodeMergeSupport.mergePdcpDecryptTrace(
                targetPdcp,
                originalMsg.getDecryptPlainHex(),
                originalMsg.getDecryptMacHex()
        );

        // 2) PDCP 解密后一般回流出的是 RRC 根，不是 PDCP 根
        //    所以要把“回流根整棵树” graft 到原 PDCP 节点下面
        ReentryNodeMergeSupport.graftReparsedTreeIntoOriginal(
                originalMsg,
                reparsedMsg,
                sourceNodeId,
                false
        );

        // 3) 回流若包含 RRC，挂到原消息的 rrcInfo 上（语义可见）
        if (reparsedMsg.getRrcInfo() != null) {
            if (originalMsg.getRrcInfo() == null) {
                originalMsg.setRrcInfo(reparsedMsg.getRrcInfo());
            } else {
                ReentryNodeMergeSupport.mergeRrcPayloadFields(
                        originalMsg.getRrcInfo(),
                        reparsedMsg.getRrcInfo()
                );
            }
        }

        // 4) PDCP 解密回流后，若产生 NAS，也保留到消息上用于后续 NAS 解密/流程判定
        //    这里不 append 成并列链；只保留解析得到的语义层对象列表
        if (reparsedMsg.getNasList() != null && !reparsedMsg.getNasList().isEmpty()) {
            if (originalMsg.getNasList() == null || originalMsg.getNasList().isEmpty()) {
                originalMsg.setNasList(reparsedMsg.getNasList());
            } else {
                // 只把原消息里还不存在的 nodeId 加进去，避免重复
                mergeNasSemanticViewByNodeId(originalMsg, reparsedMsg.getNasList());
            }
        }

        if (!isBlank(reparsedMsg.getMsgType())) {
            originalMsg.setMsgType(reparsedMsg.getMsgType());
        }
        if (!isBlank(reparsedMsg.getProtocolLayer())) {
            originalMsg.setProtocolLayer(reparsedMsg.getProtocolLayer());
        }
    }

    private void mergeNasSemanticViewByNodeId(SignalingMessage originalMsg, List<NasInfo> incomingNasList) {
        if (originalMsg == null || incomingNasList == null || incomingNasList.isEmpty()) {
            return;
        }

        if (originalMsg.getNasList() == null) {
            originalMsg.setNasList(new java.util.ArrayList<>());
        }

        java.util.Set<String> existingNodeIds = new java.util.LinkedHashSet<>();
        for (NasInfo n : originalMsg.getNasList()) {
            if (n != null && !isBlank(n.getNodeId())) {
                existingNodeIds.add(n.getNodeId());
            }
        }

        for (NasInfo incoming : incomingNasList) {
            if (incoming == null) continue;
            if (!isBlank(incoming.getNodeId()) && existingNodeIds.contains(incoming.getNodeId())) {
                continue;
            }
            originalMsg.getNasList().add(incoming);
            if (!isBlank(incoming.getNodeId())) {
                existingNodeIds.add(incoming.getNodeId());
            }
        }
    }

    private void appendNasList(SignalingMessage originalMsg, List<NasInfo> incomingNasList) {
        if (originalMsg == null || incomingNasList == null || incomingNasList.isEmpty()) {
            return;
        }

        if (originalMsg.getNasList() == null) {
            originalMsg.setNasList(new java.util.ArrayList<>());
        }

        for (NasInfo nas : incomingNasList) {
            NasInfo cloned = cloneNasInfo(nas);
            if (cloned != null) {
                originalMsg.getNasList().add(cloned);
            }
        }

        resequenceNasList(originalMsg.getNasList());
    }

}
