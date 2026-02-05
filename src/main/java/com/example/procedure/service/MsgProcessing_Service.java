package com.example.procedure.service;

import com.example.procedure.decrypt.DecryptClient;
import com.example.procedure.decrypt.DecryptResponse;
import com.example.procedure.model.*;
import com.example.procedure.parser.NasInfo;
import com.example.procedure.parser.PdcpInfo;
import com.example.procedure.rule.MessageCategoryClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DEMO 版消息处理主模块：
 * 负责：分类 → （可选）流程判别 → 调度后续功能。
 */

@Service
public class MsgProcessing_Service {
    private final UEContextService ueContextService;
    private final ObjectMapper objectMapper;


    private static final Logger log = LoggerFactory.getLogger(MsgProcessing_Service.class);

    private final MessageCategoryClassifier messageCategoryClassifier;
    private final ProClassify_Service proClassifyService;

    private final ProDispatcher_Service proDispatcherService;

    public MsgProcessing_Service(
            UEContextService ueContextService, ObjectMapper objectMapper,
            MessageCategoryClassifier messageCategoryClassifier,
            ProClassify_Service proClassifyService,
            ProDispatcher_Service proDispatcherService
    ){
        this.ueContextService = ueContextService;
        this.objectMapper = objectMapper;
        this.messageCategoryClassifier = messageCategoryClassifier;
        this.proClassifyService = proClassifyService;
        this.proDispatcherService = proDispatcherService;
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
                tryDecryptByType(msg, encType ,ctx); // ⭐ 核心：按类型解密并写回
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
                tryDecryptByType(msg, encType, ctx); // ⭐ 核心：按类型解密并写回
            }
        }
        proDispatcherService.dispatch(msg, category, procedureId, procedureTypeCode);
        // 4️⃣ 返回一个简单结果，方便测试 / 上层查看
        return new MessageProcessingResult(
                msg.getUeId(),
                msg.getMsgType(),
                category,
                procedureId,
                procedureTypeCode
        );
    }



    private void tryDecryptByType(SignalingMessage msg, String encType, UEContext ctx) {
        String url = "http://127.0.0.1:8004/decrypt";

        if ("NAS".equals(encType)) {
            decryptNasLayers(url, msg, ctx);
            return;
        }

        if ("PDCP".equals(encType)) {
            decryptAs(url, msg, ctx);   // 你定义：不是 NAS 就 AS
            return;
        }

        if ("NAS+PDCP".equals(encType)) {
            // 建议：先 NAS，再 AS（可选）
            decryptNasLayers(url, msg, ctx);
            // decryptAs(url, msg); // 如果你也需要 PDCP/AS 明文再打开
            return;
        }

        // NONE：不做
    }


    private void decryptNasLayers(String url, SignalingMessage msg, UEContext ctx) {
        if (msg.getNasList() == null) return;

        for (NasInfo nas : msg.getNasList()) {
            if (nas == null || !nas.isEncrypted()) continue;

            // 必要参数校验
            if (isBlank(ctx.getKNasEnc()) || isBlank(ctx.getKNasInt())) continue;
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
                continue;
            }

            // 假设你在类里有 ObjectMapper（Spring 注入或 new）
            DecryptResponse resp;
            try {
                resp = objectMapper.readValue(respJson, DecryptResponse.class);
            } catch (Exception ex) {
                // 返回不是合法 JSON 或字段不匹配
                // msg.setDecryptPlainHex(null);
                // msg.setDecryptMacHex(null);
                continue;
            }

            if (resp != null && resp.getDecryptStatus()!= null && (resp.getDecryptStatus().equals("DECRYPT_SUCCESS")) ) {
                // ✅ 解密成功：写回 message
                msg.setDecryptPlainHex(resp.getPlainData());
                msg.setDecryptMacHex(normalizeHex(resp.getPlainMac())); // 建议归一化（去0x/冒号/空格）

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
    }


    private void decryptAs(String url, SignalingMessage msg, UEContext ctx) {
        PdcpInfo pdcp = msg.getPdcpInfo();
        if (pdcp == null || !pdcp.isPdcpencrypted()) return;

        if (isBlank(ctx.getKRrcEnc()) || isBlank(ctx.getKRrcInt())) return;
        if (isBlank(pdcp.getSignallingDataHex()) || isBlank(pdcp.getMacHex())) return;


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

        String respJson = "";
        try {
            respJson = DecryptClient.decrypt(url, req);
        } catch (Exception e) {
            // 解密失败：写回错误信息（建议你在 NasInfo 加 decryptStatus/decryptError/plainTextHex）
            // nas.setDecryptStatus(-1);
            // nas.setDecryptError(e.getMessage());
            
        }

        // 假设你在类里有 ObjectMapper（Spring 注入或 new）
        DecryptResponse resp = null;
        try {
            resp = objectMapper.readValue(respJson, DecryptResponse.class);
        } catch (Exception ex) {
            // 返回不是合法 JSON 或字段不匹配
            // msg.setDecryptPlainHex(null);
            // msg.setDecryptMacHex(null);
        }

        if (resp != null && resp.getDecryptStatus()!= null && (resp.getDecryptStatus().equals("DECRYPT_SUCCESS")) ) {
            // ✅ 解密成功：写回 message
            msg.setDecryptPlainHex(resp.getPlainData());
            msg.setDecryptMacHex(normalizeHex(resp.getPlainMac())); // 建议归一化（去0x/冒号/空格）

            // 如果你还想把明文写回对应 NAS 层（多层情况下更推荐）
            // nas.setPlainTextHex(resp.getPlaintext());
            // nas.setDecryptMacHex(normalizeHex(resp.getMac()));

        } else {
            // ❌ 解密失败：你也可以记录失败信息（需要你在 SignalingMessage 加字段）
            // msg.setDecryptError(resp != null ? resp.getMessage() : "decrypt failed");
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

}
