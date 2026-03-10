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

    public MessageProcessingResult process(SignalingMessage msg){

        // 0) 先拿加密状态（你已在 msg.isEncrypted() / getEncryptedType() 里能算出来）
        boolean encrypted = msg.getEncrypted();
        String encType = msg.getEncryptedType(); // NAS / PDCP / NAS+PDCP / NONE

        MessageCategory category = messageCategoryClassifier.classify(msg);

        UEContext ctx = ueContextService.getContext(msg.getUeId());
        String procedureId = null;
        String procedureTypeCode = null;

        if(category == MessageCategory.PROCEDURE_DRIVING ||
                category == MessageCategory.PROCEDURE_AUX){

            // 2.1) 是否需要解密（关键：只有“流程判断依赖明文”才解密）
            if (encrypted) {
                DecryptAttemptResult dr = tryDecryptByType(msg, encType, ctx);
                if (dr.getStatus() == DecryptAttemptResult.Status.OK) {
                    try {
                        reenterDecryptedMessage(msg, encType);
                    } catch (Exception e) {
                        log.error("Decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                                msg.getUeId(), msg.getMsgId(), encType, e.getMessage(), e);
                        return new MessageProcessingResult(
                                msg.getUeId(), msg.getMsgType(), category, procedureId, procedureTypeCode
                        );
                    }

                    // 回流补丁已经合并回原 msg
                    // 如果内部还存在加密层，则继续对同一个 msg 递归处理
                    if (Boolean.TRUE.equals(msg.getEncrypted())) {
                        log.info("Decrypt recursion continue: ueId={}, msgId={}, depth={}, encType={}, path={}",
                                msg.getUeId(), msg.getMsgId(), safeDecryptDepth(msg),
                                msg.getEncryptedType(), msg.getDecryptPath());
                        return process(msg);
                    }
                }

                if (dr.getStatus() == DecryptAttemptResult.Status.WAITING) {

                    pendingMessageService.enqueue(msg.getUeId(), msg, dr.getReason());
                    // 先阶段性结束（下一步再加 pending）
                    log.info("Decrypt waiting: ueId={}, reason={}, msgId={}, encType={}",
                            msg.getUeId(), dr.getReason(), msg.getMsgId(), encType);

                    return new MessageProcessingResult(
                            msg.getUeId(), msg.getMsgType(), category, procedureId, procedureTypeCode
                    );
                }

                if (dr.getStatus() == DecryptAttemptResult.Status.FAILED) {
                    log.warn("Decrypt failed: ueId={}, msgId={}, encType={}, err={}",
                            msg.getUeId(), msg.getMsgId(), encType, dr.getError());
                    // 失败你想不想继续下放？现在先继续下放（最小影响）
                    // 也可以选择 return
                }
            }

            ProcedureMatchResult r = proClassifyService.handleMessage(msg);

            if (r != null && r.getStatus() == 0) {
                procedureId = r.getProcedureId();
                ProcedureTypeEnum typeEnum = r.getProcedureType();
                if (typeEnum != null) {
                    // 这里用枚举的 code 传给后面的模块
                    procedureTypeCode = typeEnum.getCode(); // 需要你在枚举里暴露 getCode()
                }
            } else {
                // 你可以记录一下日志，方便排查
                // log.warn("Procedure match failed, status={}, msg={}", r.getStatus(), r.getMessage());
            }

        }else{
            if (encrypted) {
                DecryptAttemptResult dr = tryDecryptByType(msg, encType, ctx);
                if (dr.getStatus() == DecryptAttemptResult.Status.OK) {
                    try {
                        reenterDecryptedMessage(msg, encType);
                    } catch (Exception e) {
                        log.error("Decrypt reentry failed: ueId={}, msgId={}, encType={}, err={}",
                                msg.getUeId(), msg.getMsgId(), encType, e.getMessage(), e);
                        return new MessageProcessingResult(
                                msg.getUeId(), msg.getMsgType(), category, procedureId, procedureTypeCode
                        );
                    }

                    // 回流补丁已经合并回原 msg
                    // 如果内部还存在加密层，则继续对同一个 msg 递归处理
                    if (Boolean.TRUE.equals(msg.getEncrypted())) {
                        log.info("Decrypt recursion continue: ueId={}, msgId={}, depth={}, encType={}, path={}",
                                msg.getUeId(), msg.getMsgId(), safeDecryptDepth(msg),
                                msg.getEncryptedType(), msg.getDecryptPath());
                        return process(msg);
                    }
                }

                if (dr.getStatus() == DecryptAttemptResult.Status.WAITING) {
                    pendingMessageService.enqueue(msg.getUeId(), msg, dr.getReason());
                    log.info("Decrypt waiting: ueId={}, reason={}, msgId={}, encType={}",
                            msg.getUeId(), dr.getReason(), msg.getMsgId(), encType);

                    return new MessageProcessingResult(
                            msg.getUeId(), msg.getMsgType(), category, procedureId, procedureTypeCode
                    );
                }

                if (dr.getStatus() == DecryptAttemptResult.Status.FAILED) {
                    log.warn("Decrypt failed: ueId={}, msgId={}, encType={}, err={}",
                            msg.getUeId(), msg.getMsgId(), encType, dr.getError());
                    // 失败你想不想继续下放？现在先继续下放（最小影响）
                    // 也可以选择 return
                }
            }
        }
        proDispatcherService.dispatch(msg, category, procedureId, procedureTypeCode);

        // ✅ 只要 ctx 现在有 key，就尝试消化 pending（不做二次流程）
        retryPendingDecrypt(msg.getUeId(), ctx);
        // 4️⃣ 返回一个简单结果，方便测试 / 上层查看
        return new MessageProcessingResult(
                msg.getUeId(),
                msg.getMsgType(),
                category,
                procedureId,
                procedureTypeCode
        );
    }



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

        if ("NAS".equals(encType)) {
            return decryptNasLayers(url, msg, ctx);
        }

        if ("PDCP".equals(encType)) {
            return decryptAs(url, msg, ctx);   // 你定义：不是 NAS 就 AS
        }

        if ("NAS+PDCP".equals(encType)) {
            DecryptAttemptResult nas = decryptNasLayers(url, msg, ctx);
            if (nas.getStatus() == DecryptAttemptResult.Status.OK) return nas;
            if (nas.getStatus() == DecryptAttemptResult.Status.WAITING) return nas;

            DecryptAttemptResult pdcp = decryptAs(url, msg, ctx);
            return pdcp;
            // decryptAs(url, msg); // 如果你也需要 PDCP/AS 明文再打开
        }

        return DecryptAttemptResult.skip();

        // NONE：不做
    }


    private DecryptAttemptResult decryptNasLayers(String url, SignalingMessage msg, UEContext ctx) {
        if (msg.getNasList() == null) return DecryptAttemptResult.skip();

        boolean hasEncryptedNas = false;
        boolean anySuccess = false;

        for (NasInfo nas : msg.getNasList()) {
            if (nas == null || !nas.isEncrypted()) continue;

            hasEncryptedNas = true;

            // 必要参数校验
            // 缺 key：等待
            if (isBlank(ctx.getKNasEnc()) || isBlank(ctx.getKNasInt())) {
                return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_NAS_KEYS);
            }

            // 缺算法号也可以当 WAITING（否则你 map 默认 NEA1/NIA1 可能导致错误解密）
            if (isBlank(ctx.getNasCipherAlg()) || isBlank(ctx.getNasIntAlg())) {
                return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_ALG);
            }

            // 缺密文/缺mac：这条解不了，继续看下一条
            if (isBlank(nas.getCipherTextHex()) || isBlank(nas.getMsgAuthCodeHex())) continue;

            DecryptClient.DecryptRequest req = new DecryptClient.DecryptRequest();
            req.messageId = msg.getMsgId();
            req.ueId = msg.getUeId();
            req.contextRef = msg.getUeId(); // 或者你自己的上下文引用
            req.layer = "NAS";

            req.encKey = ctx.getKNasEnc();
            req.intKey = ctx.getKNasInt();

            req.encAlgo = mapNasEncAlgo(ctx.getNasCipherAlg()); // "NEA1/2/3"
            req.intAlgo = mapNasIntAlgo(ctx.getNasIntAlg());    // "NIA1/2/3"

            // 这两个你需要补：count / bearer
            req.count = nas.getSeqNoInt();   // TODO
            req.bearer = 1;                            // NAS 通常 bearer=0（按你服务约定）
            req.direction = msg.getDirection();         // "UL"/"DL"

            req.ciphertext = nas.getCipherTextHex();
            req.mac = nas.getMsgAuthCodeHex();          // 建议传纯 hex（不要 0x）
            req.dataLength = 0;


            String respJson;
            try {
                respJson = DecryptClient.decrypt(url, req);
            } catch (Exception e) {
                // 解密失败：写回错误信息（建议你在 NasInfo 加 decryptStatus/decryptError/plainTextHex）
                // nas.setDecryptStatus(-1);
                // nas.setDecryptError(e.getMessage());
                return DecryptAttemptResult.failed("NAS decrypt http failed: " + e.getMessage());
            }

            // 假设你在类里有 ObjectMapper（Spring 注入或 new）
            DecryptResponse resp;
            try {
                resp = objectMapper.readValue(respJson, DecryptResponse.class);
            } catch (Exception ex) {
                // 返回不是合法 JSON 或字段不匹配
                // msg.setDecryptPlainHex(null);
                // msg.setDecryptMacHex(null);
                return DecryptAttemptResult.failed("NAS decrypt invalid json: " + ex.getMessage());
            }

            if (resp != null && resp.getDecryptStatus()!= null && (resp.getDecryptStatus().equals("DECRYPT_SUCCESS")) ) {
                // ✅ 解密成功：写回 message
                msg.setDecryptPlainHex(resp.getPlainData());
                msg.setDecryptMacHex(normalizeHex(resp.getPlainMac())); // 建议归一化（去0x/冒号/空格）
                anySuccess = true;
                // 如果你还想把明文写回对应 NAS 层（多层情况下更推荐）
                // nas.setPlainTextHex(resp.getPlaintext());
                // nas.setDecryptMacHex(normalizeHex(resp.getMac()));

            } else {
                // ❌ 解密失败：你也可以记录失败信息（需要你在 SignalingMessage 加字段）
                // msg.setDecryptError(resp != null ? resp.getMessage() : "decrypt failed");
            }


            // TODO: 解析 respJson 并写回 nas
            // 例如：
            // DecryptResponse resp = MAPPER.readValue(respJson, DecryptResponse.class);
            // if (resp.status == 0) nas.setPlainTextHex(resp.plaintextHex);

        }
        if (!hasEncryptedNas) return DecryptAttemptResult.skip();
        if (anySuccess) return DecryptAttemptResult.ok();

        // 有加密NAS但没有成功：先当 FAILED（你后续做 pending/drain 时可以更细分）
        return DecryptAttemptResult.failed("NAS decrypt failed (no layer success)");
    }


    private DecryptAttemptResult decryptAs(String url, SignalingMessage msg, UEContext ctx) {
        PdcpInfo pdcp = msg.getPdcpInfo();
        if (pdcp == null || !pdcp.isPdcpencrypted()) return DecryptAttemptResult.skip();;

        // 缺 key：等待
        if (isBlank(ctx.getKRrcEnc()) || isBlank(ctx.getKRrcInt())) {
            return DecryptAttemptResult.waiting(DecryptAttemptResult.WaitReason.WAIT_RRC_KEYS);
        }

        // 缺算法号：等待（否则默认 NEA1/NIA1 可能会错）
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

        req.encAlgo = mapRrcEncAlgo(ctx.getRrcCipherAlg()); // "NEA1/2/3"
        req.intAlgo = mapRrcIntAlgo(ctx.getRrcIntAlg());    // "NIA1/2/3"

        req.count = pdcp.getSeqNumInt();                // TODO
        req.bearer = 0;                                     // SRB1/2？需要你按 PDCP/RRC 场景定
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

        // 假设你在类里有 ObjectMapper（Spring 注入或 new）
        DecryptResponse resp;
        try {
            resp = objectMapper.readValue(respJson, DecryptResponse.class);
        } catch (Exception ex) {
            return DecryptAttemptResult.failed("AS decrypt invalid json: " + ex.getMessage());
        }

        if (resp != null && resp.getDecryptStatus()!= null && (resp.getDecryptStatus().equals("DECRYPT_SUCCESS")) ) {
            // ✅ 解密成功：写回 message
            msg.setDecryptPlainHex(resp.getPlainData());
            msg.setDecryptMacHex(normalizeHex(resp.getPlainMac())); // 建议归一化（去0x/冒号/空格）
            return DecryptAttemptResult.ok();

            // 如果你还想把明文写回对应 NAS 层（多层情况下更推荐）
            // nas.setPlainTextHex(resp.getPlaintext());
            // nas.setDecryptMacHex(normalizeHex(resp.getMac()));

        } else {
            // ❌ 解密失败：你也可以记录失败信息（需要你在 SignalingMessage 加字段）
            // msg.setDecryptError(resp != null ? resp.getMessage() : "decrypt failed");
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

                if ("NAS".equals(decryptedLayer)) {
                    mergeNasDecodedContent(msg, reparsedMsg);
                } else if ("PDCP".equals(decryptedLayer)){
                    mergePdcpDecodedContent(msg, reparsedMsg);
                } else if ("NAS+PDCP".equals(decryptedLayer)) {
                    // 正常不会一次同时解两层，但兜底可以两边都试
                    mergeNasDecodedContent(msg, reparsedMsg);
                    mergePdcpDecodedContent(msg, reparsedMsg);
                }

                // 当前剩余加密状态由回流结果决定
                msg.setEncrypted(
                        reparsedMsg.getEncrypted() != null ? reparsedMsg.getEncrypted() : false
                );
                msg.setEncryptedType(
                        !isBlank(reparsedMsg.getEncryptedType())
                                ? normalizeEncType(reparsedMsg.getEncryptedType())
                                : "NONE"
                );

                // 保留最近一轮解密结果
                if (!isBlank(msg.getDecryptPlainHex())) {
                    // 原值保留即可
                } else if (!isBlank(reparsedMsg.getDecryptPlainHex())) {
                    msg.setDecryptPlainHex(reparsedMsg.getDecryptPlainHex());
                }

                if (isBlank(msg.getDecryptMacHex()) && !isBlank(reparsedMsg.getDecryptMacHex())) {
                    msg.setDecryptMacHex(reparsedMsg.getDecryptMacHex());
                }
                // 2) 记录：这条原始消息已经成功解开了一层
                msg.setDecrypted(true);
                msg.setDecryptDepth(safeDecryptDepth(msg) + 1);
                msg.setDecryptPath(
                        appendDecryptPath(msg.getDecryptPath(), normalizeEncType(decryptedLayer))
                );

                // 3) 保留回流后识别出的真实加密状态
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

        NasInfo decodedNas = reparsedMsg.getNasList().get(0);
        if (decodedNas == null) return;

        for (NasInfo originalNas : originalMsg.getNasList()) {
            if (originalNas == null) continue;
            if (!originalNas.isEncrypted()) continue;

            // 保留原始密文痕迹
            if (isBlank(originalNas.getOriginalFullNasPduHex())) {
                originalNas.setOriginalFullNasPduHex(originalNas.getFullNasPduHex());
            }
            if (isBlank(originalNas.getOriginalCipherTextHex())) {
                originalNas.setOriginalCipherTextHex(originalNas.getCipherTextHex());
            }

            // 用当前已知明文填回去
            if (!isBlank(originalMsg.getDecryptPlainHex())) {
                originalNas.setDecryptedTexHex(originalMsg.getDecryptPlainHex());
            }

            // 用回流解析出的 NAS 语义字段替换原来的那条 NAS
            copyNasSemanticFields(originalNas, decodedNas);

            // 这条 NAS 现在已经是明文态
            originalNas.setEncrypted(false);
            originalNas.setCipherTextHex(null);

            // fullNasPduHex 现在代表“当前最终 NAS 视图”
            if (!isBlank(decodedNas.getFullNasPduHex())) {
                originalNas.setFullNasPduHex(decodedNas.getFullNasPduHex());
            }

            return;
        }
    }

    private void copyNasSemanticFields(NasInfo target, NasInfo source) {
        if (target == null || source == null) return;

        target.setNasNode(source.getNasNode());
        target.setEpd(source.getEpd());
        target.setSpareHalfOctet(source.getSpareHalfOctet());
        target.setSecurityHeaderType(source.getSecurityHeaderType());
        target.setMsgAuthCodeHex(source.getMsgAuthCodeHex());
        target.setSeqNo(source.getSeqNo());

        target.setMmMessageType(source.getMmMessageType());
        target.setNas_cipheringAlgorithm(source.getNas_cipheringAlgorithm());
        target.setNas_integrityProtAlgorithm(source.getNas_integrityProtAlgorithm());

        target.setGuamiMcc(source.getGuamiMcc());
        target.setGuamiMnc(source.getGuamiMnc());
        target.setTmsi(source.getTmsi());
        target.setRegType5gs(source.getRegType5gs());

        if (source.getFieldPaths() != null && !source.getFieldPaths().isEmpty()) {
            target.setFieldPaths(new LinkedHashMap<>(source.getFieldPaths()));
        }
    }

    private void mergePdcpDecodedContent(SignalingMessage originalMsg, SignalingMessage reparsedMsg) {
        if (originalMsg == null || reparsedMsg == null) return;

        if (reparsedMsg.getRrcInfo() != null) {
            originalMsg.setRrcInfo(reparsedMsg.getRrcInfo());
        }

        if (!isBlank(reparsedMsg.getMsgType())) {
            originalMsg.setMsgType(reparsedMsg.getMsgType());
        }

        if (!isBlank(reparsedMsg.getProtocolLayer())) {
            originalMsg.setProtocolLayer(reparsedMsg.getProtocolLayer());
        }

        // 如果回流后仍有 pdcpInfo，也可以更新
        if (reparsedMsg.getPdcpInfo() != null) {
            originalMsg.setPdcpInfo(reparsedMsg.getPdcpInfo());
        }
    }

}
