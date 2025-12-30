package com.example.procedure.parser;

import com.example.procedure.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

/**
 * 针对单条 packet 的 RRC + NAS 解析器：
 *  - 从 packetNode 进入，
 *  - 只 DFS 遍历一遍 layers，
 *  - 遇到 "nr-rrc" -> 提取 RRC 信息到 RrcInfo（只解析一次）；
 *  - 遇到 "nas-5gs" -> 创建一个 NasInfo，放入结果的 nasList；
 *  - 遍历结束后返回 RrcNasParseResult。
 */
public class RrcNasPacketParser {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();


    /**
     * 对外入口：针对单条 packet 做解析。
     */
//    public static RrcNasParseResult parse(JsonNode packetNode) {
//        JsonNode layers = packetNode.path("_source").path("layers");
//
//        RrcNasParseResult result = new RrcNasParseResult();
//        ParseContext ctx = new ParseContext(layers, result);
//
//        // 从 layers 根开始 DFS，一遍完成
//        dfsTraverse(null, layers, ctx);
//
//        return result;
//    }

    /**
     * 对外入口：传入 logic.json 和 raw.json 的同一条 packet
     */
    public static RrcNasParseResult parse(JsonNode logicPacket, JsonNode rawPacket) {
        JsonNode logicLayers = logicPacket.path("_source").path("layers");
        JsonNode rawLayers   = (rawPacket == null)
                ? null
                : rawPacket.path("_source").path("layers");

        RrcNasParseResult result = new RrcNasParseResult();
        PacketParseContext ctx = new PacketParseContext(logicLayers, rawLayers, result);

        dfsTraverse(null, logicLayers, ctx);
        return result;
    }

    // ================== 内部实现 ==================

//    /**
//     * 解析过程中的上下文：
//     *  - rootLayers：整棵 layers 的根，用来提取 fullNasPduHex 等全局字段
//     *  - result：承载 RRC + NAS 列表的结果对象
//     */
//    private static class ParseContext {
//        final JsonNode logicRoot;
//        final JsonNode rawRoot;
//        final RrcNasParseResult result;
//        final Deque<String> path = new ArrayDeque<>();
//
//        ParseContext(JsonNode logicRoot, JsonNode rawRoot, RrcNasParseResult result) {
//            this.logicRoot = logicRoot;
//            this.rawRoot = rawRoot;
//            this.result = result;
//        }
//
//        boolean hasRrc() {
//            return result.getRrcInfo() != null;
//        }
//    }


    /**
     * 深度优先遍历整个 layers：
     *  - fieldName 为当前 node 在父节点中的 key（根节点传 null）
     */
    // ================== DFS：只负责走树 + 标记阶段 + 调 handle ==================

    private static void dfsTraverse(String fieldName, JsonNode node, PacketParseContext ctx) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        Deque<String> path = ctx.path;
        if (fieldName != null) {
            path.addLast(fieldName);
        }

        // ---- 1) 阶段进入：只做标志，不做解析 ----
        boolean enterMac = false;
        boolean enterPdcp = false;
        // ⭐ 新增
        // ⭐ NGAP 相关
        boolean enterNgapPdu  = false;  // 进入一个 NGAP_PDU_tree
        boolean enterNgapMsg  = false;  // 进入一个 NGAP message element（initiating/successful/...）

        boolean enterNgap = false;
        boolean enterRrc = false;
        boolean enterNas = false;


        // ⭐ 新增：进入 MAC 子树
        if ("mac-nr".equals(fieldName) && node.isObject()) {
            ctx.macDepth++;
            enterMac = true;
            // 只表示“进入 MAC 区域”，具体字段交给 handleMacNode
        }

        if ("pdcp-nr".equals(fieldName) && node.isObject()) {
            ctx.pdcpDepth++;
            enterPdcp = true;
        }

         //⭐ NGAP：进入 NGAP PDU 子树（整个 NGAP_PDU_tree）
        if ("ngap.NGAP_PDU_tree".equals(fieldName) && node.isObject()) {
            ctx.ngapDepth++;
            enterNgapPdu = true;
        }

        // ⭐ NGAP：在 NGAP_PDU_tree 里面遇到一条 message element，就新建一个 NgapInfo 压栈
        if (ctx.inNgap() && node.isObject()) {
            if ("ngap.initiatingMessage_element".equals(fieldName)
                    || "ngap.successfulOutcome_element".equals(fieldName)
                    || "ngap.unsuccessfulOutcome_element".equals(fieldName)) {
                ctx.pushNewNgap();     // 创建并压栈
                enterNgapMsg = true;   // 递归完这个子树后要 pop
            }
        }

        if ("nr-rrc".equals(fieldName) && node.isObject()) {
            ctx.rrcDepth++;
            enterRrc = true;
            // 此时只表示“我们进了 RRC 的区域”，具体怎么取字段交给 handleRrcNode
        }

        if ("nas-5gs".equals(fieldName) && node.isObject()) {
            NasInfo nas = new NasInfo();
            nas.setNasNode(node);

            // 2. 找到 raw 树中与当前 nas-5gs 同级的 parent，拿 nas-5gs_raw
            JsonNode rawParent = getRawParentNodeByPath(ctx.rawRoot, ctx.path);
            if (rawParent != null) {
                JsonNode nasRaw = rawParent.get("nas-5gs_raw");
                if (nasRaw != null && nasRaw.isArray() && nasRaw.size() > 0) {
                    String fullHex = normalizeHex(nasRaw.get(0).asText());
                    nas.setFullNasPduHex(fullHex);
                    nas.putFieldPath("nas-5gs_raw", pathToString(ctx.path) + " (raw sibling: nas-5gs_raw)");
                }
            }

            // 3. 基于 logic + raw，对这个 nas-5gs 做一次完整分析
            analyzeNasNode(nas, node);

            ctx.result.getNasList().add(nas);
            ctx.nasStack.push(nas);
            enterNas = true;
            // 此时只表示“我们进了 NAS 区域”，具体字段交给 handleNasNode
        }
        // ⭐ http2:json 场景：遇到 json.object，看看是否是 Nausf_UEAuthentication_AuthenticateResponse
        if ("json.object".equals(fieldName) && node.isValueNode()) {
            handleHttp2JsonObject(node, ctx);
        }

        // ---- 2) 阶段处理：真正的业务逻辑交给各阶段的 handle ----

        // ⭐ 新增：先处理 MAC
        if (ctx.inMac()) {
            handleMacNode(fieldName, node, ctx);
        }

        // ⭐ 新增：PDCP 阶段
        if (ctx.inPdcp()) {
            handlePdcpNode(fieldName, node, ctx);
        }

        if (ctx.inNgap()) {
            handleNgapNode(fieldName, node, ctx);  // ⭐ 新增
        }

        if (ctx.inRrc()) {
            handleRrcNode(fieldName, node, ctx);
        }

        if (ctx.inNas()) {
            handleNasNode(fieldName, node, ctx);
        }


        // 以后你要加 MAC / NGAP / HTTP，直接在这里加：
        // if (ctx.inMac())  handleMacNode(fieldName, node, ctx);
        // if (ctx.inNgap()) handleNgapNode(fieldName, node, ctx);

        // ---- 3) 递归子节点 ----

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                dfsTraverse(e.getKey(), e.getValue(), ctx);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                dfsTraverse(null, child, ctx);
            }
        }

        // ---- 4) 阶段退出：与进入对称 ----

        // NGAP：先退出 message，再退出 PDU
        if (enterNgapMsg) {
            ctx.ngapStack.pop();
        }
        if (enterNgapPdu) {
            ctx.ngapDepth--;
        }

        if (enterNas) {
            ctx.nasStack.pop();
        }

        // ⭐ 新增
        if (enterPdcp) {
            ctx.pdcpDepth--;
        }

        if (enterRrc) {
            ctx.rrcDepth--;
        }
        // ⭐ 新增
        if (enterMac) {
            ctx.macDepth--;
        }

        // ⭐ 新增
        if (enterNgap) {
            ctx.ngapDepth--;
        }

        if (fieldName != null) {
            path.removeLast();
        }
    }


    /**
     * 处理 http2:json 里的 json.object：
     *  期望内容类似：
     *  {
     *      "authResult": "AUTHENTICATION_SUCCESS",
     *      "supi": "imsi-001010000000001",
     *      "kseaf": "5a8960ff6a8b013c7b..."
     *  }
     *  一旦匹配到，就构造 NUARInfo，消息名固定为
     *  Nausf_UEAuthentication_AuthenticateResponse
     */
    private static void handleHttp2JsonObject(JsonNode node, PacketParseContext ctx) {
        String jsonText = node.asText();
        if (jsonText == null || jsonText.isEmpty()) {
            return;
        }

        JsonNode obj;
        try {
            obj = JSON_MAPPER.readTree(jsonText);
        } catch (IOException e) {
            // 不是合法 JSON，直接忽略
            return;
        }
        if (obj == null || !obj.isObject()) {
            return;
        }

        // 必须有 kseaf 和 supi；authResult 可选但一般存在
        JsonNode kseafNode = obj.get("kseaf");
        JsonNode supiNode  = obj.get("supi");
        if (kseafNode == null || supiNode == null ||
                kseafNode.isMissingNode() || supiNode.isMissingNode()) {
            return;
        }

        String kseaf = kseafNode.asText(null);
        String supi  = supiNode.asText(null);
        if (kseaf == null || supi == null) {
            return;
        }

        String authResult = obj.path("authResult").asText(null);

        // 构造 / 获取 NUARInfo
        NUARInfo nuar = ctx.ensureNuarInfo();

        // 消息名固定
        nuar.setMsgName("Nausf_UEAuthentication_AuthenticateResponse");

        nuar.setKseafHex(kseaf);
        nuar.setSupi(supi);

        // 从 supi 中提取 IMSI：imsi-001010000000001 -> 001010000000001
        if (supi.startsWith("imsi-")) {
            nuar.setImsi(supi.substring("imsi-".length()));
        } else {
            nuar.setImsi(supi);
        }

        if (authResult != null) {
            nuar.setAuthResult(authResult);
        }

        // 记录 json.object 的 JSON 路径
        String pathStr = pathToString(ctx.path);
        nuar.putFieldPath("json.object", pathStr);
    }


    /**
     * NGAP 阶段的所有逻辑：
     *  - 识别 PDU 类型：initiating/successfulOutcome/unsuccessfulOutcome
     *  - 抽取 procedureCode / criticality
     *  - 从 ngap.value_element 下的 xxx_element 中提取消息类型：
     *      ngap.UplinkNASTransport_element -> "UplinkNASTransport"
     */
    private static void handleNgapNode(String fieldName, JsonNode node, PacketParseContext ctx) {
        NgapInfo ngap = ctx.currentNgap();
        String pathStr = pathToString(ctx.path);

        // 1) 对象节点：处理 initiating/successful/unsuccessful 三大类
        if (node.isObject()) {
            switch (fieldName) {
                case "ngap.initiatingMessage_element":
                    ngap.setPduType("initiatingMessage");
                    ngap.putFieldPath("pduType", pathStr);
                    detectNgapMsgTypeFromValueElement(node, ngap, pathStr);
                    break;

                case "ngap.successfulOutcome_element":
                    ngap.setPduType("successfulOutcome");
                    ngap.putFieldPath("pduType", pathStr);
                    detectNgapMsgTypeFromValueElement(node, ngap, pathStr);
                    break;

                case "ngap.unsuccessfulOutcome_element":
                    ngap.setPduType("unsuccessfulOutcome");
                    ngap.putFieldPath("pduType", pathStr);
                    detectNgapMsgTypeFromValueElement(node, ngap, pathStr);
                    break;

                default:
                    break;
            }
        }

        // 2) 值节点：procedureCode / criticality
        if (node.isValueNode()) {
            String value = node.asText();
            switch (fieldName) {
                // ⭐ SecurityKey：c9:5f:32:6d:... -> c95f326d...
                case "ngap.SecurityKey":
                    ngap.setSecurityKeyHex(normalizeHex(value));
                    ngap.putFieldPath("ngap.SecurityKey", pathStr);
                    break;

                // ⭐ RAN UE NGAP ID
                case "ngap.RAN_UE_NGAP_ID":
                    ngap.setRanUeNgapId(value);
                    ngap.putFieldPath("ngap.RAN_UE_NGAP_ID", pathStr);
                    break;

                default:
                    break;
            }
        }
    }

    /**
     * 从 ngap.value_element 里判断 NGAP 消息类型：
     *  例如 "ngap.UplinkNASTransport_element" -> "UplinkNASTransport"
     */
    private static void detectNgapMsgTypeFromValueElement(JsonNode msgElemNode,
                                                          NgapInfo ngap,
                                                          String basePath) {
        JsonNode valueElem = JsonUtils.path(
                msgElemNode,
                "ngap.value_element"
        );
        if (valueElem == null || valueElem.isMissingNode() || !valueElem.isObject()) {
            return;
        }

        Iterator<String> fieldNames = valueElem.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (field.startsWith("ngap.") && field.endsWith("_element")) {
                String msgName = field.substring("ngap.".length(), field.length() - "_element".length());
                ngap.setMsgName(msgName);
                ngap.putFieldPath("msgType", basePath + "/ngap.value_element/" + field);

                // ⭐ 根据消息名判断上下行
                String dir = null;
                switch (msgName) {
                    case "InitialUEMessage":
                        dir = "DL";
                        break;
                    case "DownlinkNASTransport":
                        dir = "DL";
                        break;
                    case "UplinkNASTransport":
                        dir = "UL";
                        break;
                    case "InitialContextSetupRequest":
                        dir = "DL";
                        break;
                    case "InitialContextSetupResponse":
                        dir = "UL";
                        break;
                    default:
                        // 其它消息不判断，留空或 UNKNOWN
                        break;
                }

                if (dir != null) {
                    ngap.setDirection(dir);
                    ngap.putFieldPath("direction", basePath + "/ngap.value_element/" + field);
                }

                break;
            }
        }
    }





    /**
     * 针对一个 nas-5gs 节点，判断是否为加密 NAS，并在可行时截出密文：
     *  - 不再依赖固定 "PD 39"，而是匹配 "Not a NAS 5GS PD X (Unknown)" 这种模式；
     *  - 如果 "Security protected NAS 5GS message" 是空的，就不截头、不标记加密。
     */
    private static void analyzeNasNode(NasInfo nas, JsonNode nasLogicNode) {
        if (nasLogicNode == null || nasLogicNode.isMissingNode()) {
            return;
        }

        // 1. 拿到 Security protected / Plain 两个子节点
        JsonNode secNode   = nasLogicNode.get("Security protected NAS 5GS message");
        JsonNode plainNode = nasLogicNode.get("Plain NAS 5GS Message");

        // 是否有 Plain Unknown 结构（不再固定 PD=39）
        boolean hasPlainUnknown = hasPlainUnknownPd(plainNode);

        // 如果 Security protected 节点不存在、或者是空对象，就直接返回，不认为是加密
        if (secNode == null || secNode.isMissingNode() || !secNode.isObject() || secNode.size() == 0) {
            return;
        }

        // 2. 从 Security protected 里拿字段（如果有）
        String epd = getText(secNode, "nas-5gs.epd");
        String spare = getText(secNode, "nas-5gs.spare_half_octet");
        String sht = getText(secNode, "nas-5gs.security_header_type");
        String mac = getText(secNode, "nas-5gs.msg_auth_code");
        String seq = getText(secNode, "nas-5gs.seq_no");

        if (epd != null)   nas.setEpd(epd);
        if (spare != null) nas.setSpareHalfOctet(spare);
        if (sht != null)   nas.setSecurityHeaderType(sht);
        if (mac != null)   nas.setMsgAuthCodeHex(normalizeHex(mac));
        if (seq != null)   nas.setSeqNo(seq);

        // 3. 只有当 security_header_type == "4" 时，才认为是加密 + 完整性保护
        if (!("4".equals(sht) || "2".equals(sht))) {
            // 这里说明这个 Security protected 节点并不是“加密+完保(4)”那种，
            // 不继续往下当密文来处理，直接返回。
            return;
        }

        // 标记这个 NAS 已经加密
        nas.setEncrypted(true);

        // 4. 截密文：必须同时满足
        //   - 有 fullNasPduHex（从 nas-5gs_raw 来）
        //   - Security protected 不是空
        //   - （可选）存在 Plain Unknown 这个结构，说明后面确实跟着一段「wireshark 解不出的明文」
        String fullHex = nas.getFullNasPduHex();
        if (fullHex == null || fullHex.isEmpty()) {
            return;
        }

        // 如果你想更保守：只有有 Unknown PD 结构时才截密文
        // 如果不想依赖 Unknown PD，也可以不加 hasPlainUnknown 的判断
        if (!hasPlainUnknown) {
            // 有些情况下 Security protected 节点有 header 字段，但是后面可能没有 Unknown PD 结构，
            // 你这里选择：不截（或者你也可以选择照样截，看你需求）
            return;
        }

        // 5. 按 NAS 安全头格式截掉前 7 字节作为头部（你之前举的例子）
        int headerBytes = 7;
        int headerHexLen = headerBytes * 2;
        if (fullHex.length() > headerHexLen) {
            String cipher = fullHex.substring(headerHexLen);
            nas.setCipherTextHex(cipher);
        }
    }

    private static String getText(JsonNode parent, String key) {
        if (parent == null) return null;
        JsonNode n = parent.get(key);
        return (n == null || n.isMissingNode()) ? null : n.asText();
    }


    private static boolean hasPlainUnknownPd(JsonNode plainNode) {
        if (plainNode == null || plainNode.isMissingNode() || !plainNode.isObject()) {
            return false;
        }
        Iterator<String> names = plainNode.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.startsWith("Not a NAS 5GS PD ") && name.endsWith(" (Unknown)")) {
                return true;
            }
        }
        return false;
    }



    // ================== RRC 阶段处理 ==================

    /**
     * MAC 阶段的所有逻辑都写在这里：
     *  - currentMac = ctx.ensureMacInfo();
     *  - 目前只关心 mac-nr.rnti / mac-nr.rnti-type 两个字段。
     */
    private static void handleMacNode(String fieldName, JsonNode node, PacketParseContext ctx) {
        MacInfo mac = ctx.ensureMacInfo();
        String pathStr = pathToString(ctx.path);

        // 我们要的字段在 mac-nr.context_tree 下面，是普通值节点：
        //   "mac-nr.context_tree": {
        //       "mac-nr.rnti": "0x0b16",
        //       "mac-nr.rnti-type": "3",
        //       ...
        //   }

        if (node.isValueNode()) {
            String value = node.asText();

            switch (fieldName) {
                case "mac-nr.rnti":
                    mac.setRnti(value);
                    mac.putFieldPath("mac-nr.rnti", pathStr);
                    break;

                case "mac-nr.rnti-type":
                    mac.setRntiType(value);
                    mac.putFieldPath("mac-nr.rnti-type", pathStr);
                    break;

                default:
                    break;
            }
        }

        // 如果以后想用 raw，可以像 RRC 一样：
        // JsonNode rawParent = getRawParentNodeByPath(ctx.rawRoot, ctx.path);
        // JsonNode macRaw = rawParent != null ? rawParent.get("mac-nr_raw") : null;
        // ... 看需要再扩展
    }


    /**
     * PDCP 阶段的所有逻辑都写在这里：
     *  - 目前只关心：
     *      pdcp-nr.signalling-data
     *      pdcp-nr.mac
     *  - signalling-data 要去掉冒号；
     *  - mac 要去掉 0x 前缀。
     */
    private static void handlePdcpNode(String fieldName, JsonNode node, PacketParseContext ctx) {
        PdcpInfo pdcp = ctx.ensurePdcpInfo();
        String pathStr = pathToString(ctx.path);

        if (!node.isValueNode()) {
            return;
        }

        String value = node.asText();
        switch (fieldName) {
            case "pdcp-nr.signalling-data": {
                // 例如："b4:3f:7c:e7:0a:c5:76:a3" -> "b43f7ce70ac576a3"
                String normalized = normalizeHex(value);
                pdcp.setSignallingDataHex(normalized);
                pdcp.putFieldPath("pdcp-nr.signalling-data", pathStr);
                pdcp.setPdcpencrypted(true);
                break;
            }
            case "pdcp-nr.mac": {
                // 例如："0x51a85e19" -> "51a85e19"
                String normalized = normalizeHex(value);
                pdcp.setMacHex(normalized);
                pdcp.putFieldPath("pdcp-nr.mac", pathStr);
                break;
            }

            case "pdcp-nr.direction": {
                // "0" -> uplink, "1" -> downlink
                String dir = null;
                if ("0".equals(value)) {
                    dir = "UL";
                } else if ("1".equals(value)) {
                    dir = "DL";
                }

                if (dir != null) {
                    pdcp.setDirection(dir);
                    pdcp.putFieldPath("pdcp-nr.direction", pathStr);
                }
                break;
            }

            case "pdcp-nr.seq-num": {
                pdcp.setSeqnum(value);
                pdcp.putFieldPath("pdcp-nr.seq-num", pathStr);
                break;
            }

            default:
                break;
        }
    }

    /**
     * 归一化十六进制字符串：
     *  - 去掉前后空格
     *  - 去掉前缀 0x / 0X
     *  - 去掉所有冒号和空格
     *  - 转成小写
     *  例如：
     *      "b4:3f:7c:e7:0a:c5:76:a3" -> "b43f7ce70ac576a3"
     *      "0x51a85e19"              -> "51a85e19"
     */
    private static String normalizeHex(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("0x") || v.startsWith("0X")) {
            v = v.substring(2);
        }
        v = v.replace(":", "").replace(" ", "");
        return v.toLowerCase();
    }


    /**
     * RRC 阶段的所有逻辑都写在这里：
     *  - 需要的时候自己判断 fieldName；
     *  - 真正解析 RRC 消息类型 / 方向 / randomValue / C-RNTI 等；
     *  - 想用 raw 就自己调 getRawParentNodeByPath(ctx.rawRoot, ctx.path)。
     */
    private static void handleRrcNode(String fieldName, JsonNode node, PacketParseContext ctx) {
        RrcInfo rrc = ctx.ensureRrcInfo();
        String pathStr = pathToString(ctx.path);

        // 示例1：根据 UL/DCCH/CCCH 这种结构字段来判断方向 + 消息类型
        if (node.isObject()) {
            switch (fieldName) {
                case "nr-rrc.UL_CCCH_Message_element":
                case "nr-rrc.UL_DCCH_Message_element":
                    rrc.setDirection("UL");
                    rrc.putFieldPath("direction", pathStr);
                    detectRrcMsgTypeFromC1(node, rrc, pathStr);
                    break;
                case "nr-rrc.DL_CCCH_Message_element":
                case "nr-rrc.DL_DCCH_Message_element":
                    rrc.setDirection("DL");
                    rrc.putFieldPath("direction", pathStr);
                    detectRrcMsgTypeFromC1(node, rrc, pathStr);
                    break;
                default:
                    break;
            }
        }

        // 示例2：值字段，按字段名匹配你关心的内容
        if (node.isValueNode()) {
            String value = node.asText();

            switch (fieldName) {
                case "nr-rrc.cipheringAlgorithm":
                    rrc.setCipheringAlgorithm(value);
                    rrc.putFieldPath("nr-rrc.cipheringAlgorithm", pathStr);
                    break;

                case "nr-rrc.integrityProtAlgorithm":
                    rrc.setIntegrityProtAlgorithm(value);
                    rrc.putFieldPath("nr-rrc.integrityProtAlgorithm", pathStr);

                    break;

                case "mac-nr.rnti":
                    rrc.setCrnti(value);
                    rrc.putFieldPath("mac-nr.rnti", pathStr);
                    break;

                case "nr-rrc.dedicatedNAS_Message":
                    if ("rrcSetupComplete".equals(rrc.getMsgName())) {
                        rrc.setHasDedicatedNas(true);
                        rrc.putFieldPath("nr-rrc.dedicatedNAS_Message", pathStr);
                    }
                    break;

                default:
                    break;
            }
        }

        // 示例3：需要 raw 时，随时可以这么用：
        // JsonNode rawParent = getRawParentNodeByPath(ctx.rawRoot, ctx.path);
        // JsonNode someRaw = rawParent != null ? rawParent.get("nr-rrc_raw") : null;
        // 你可以在这里将 raw 路径也记录到 rrc 里，将来用于还原密文等。
    }

    /**
     * 从 UL/DCCH element 下的 c1_tree 里判断消息类型：rrcSetupRequest / rrcSetup / rrcSetupComplete ...
     * 这也是在 handle 里做，而不是在 dfsTraverse 里。
     */
    private static void detectRrcMsgTypeFromC1(JsonNode msgElemNode,
                                               RrcInfo rrc,
                                               String basePath) {
        JsonNode c1Tree = JsonUtils.path(
                msgElemNode,
                "nr-rrc.message_tree",
                "nr-rrc.c1_tree"
        );
        if (c1Tree == null || c1Tree.isMissingNode()) {
            return;
        }

        Iterator<String> fieldNames = c1Tree.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (field.startsWith("nr-rrc.") && field.endsWith("_element")) {
                String msgName = field.substring("nr-rrc.".length(), field.length() - "_element".length());
                rrc.setMsgName(msgName);
                rrc.putFieldPath("msgType", basePath + "/nr-rrc.message_tree/nr-rrc.c1_tree/" + field);
                break;
            }
        }
    }

    // ================== NAS 阶段处理 ==================

    /**
     * NAS 阶段的所有逻辑都写在这里：
     *  - currentNas = ctx.currentNas()；
     *  - 根据 fieldName 判断你关心的 NAS 字段；
     *  - 需要 raw 时随时用 getRawParentNodeByPath(ctx.rawRoot, ctx.path)。
     */
    private static void handleNasNode(String fieldName, JsonNode node, PacketParseContext ctx) {
        NasInfo nas = ctx.currentNas();
        if (nas == null) return;

        String pathStr = pathToString(ctx.path);

        if (node.isValueNode()) {
            String value = node.asText();

            switch (fieldName) {
                case "nas-5gs.security_header_type":
                    nas.setSecurityHeaderType(value);
                    nas.putFieldPath("nas-5gs.security_header_type", pathStr);
                    if ("4".equals(value)) {
                        nas.setEncrypted(true);
                        ctx.result.setNasEncrypted(true);
                        ctx.result.setEncryptedLayer("NAS");
                    }
                    break;

                case "nas-5gs.mm.message_type":
                    nas.setMmMessageType(value);
                    nas.putFieldPath("nas-5gs.mm.message_type", pathStr);
                    break;

                case "nas-5gs.mm.nas_sec_algo_enc":
                    nas.setNas_cipheringAlgorithm(value);
                    nas.putFieldPath("nas-5gs.mm.nas_sec_algo_enc", pathStr);
                    break;

                case "nas-5gs.mm.nas_sec_algo_ip":
                    nas.setNas_integrityProtAlgorithm(value);
                    nas.putFieldPath("nas-5gs.mm.nas_sec_algo_ip", pathStr);
                    break;

                case "e212.guami.mcc":
                    nas.setGuamiMcc(value);
                    nas.putFieldPath("e212.guami.mcc", pathStr);
                    break;

                case "e212.guami.mnc":
                    nas.setGuamiMnc(value);
                    nas.putFieldPath("e212.guami.mnc", pathStr);
                    break;

                case "3gpp.tmsi":
                    nas.setTmsi(value);
                    nas.putFieldPath("3gpp.tmsi", pathStr);
                    break;

                case "nas-5gs.mm.5gs_reg_type":
                    nas.setRegType5gs(value);
                    nas.putFieldPath("nas-5gs.mm.5gs_reg_type", pathStr);
                    break;

                // 你原来已有的其它字段也可以继续放这里
                default:
                    break;
            }
        }
    }



    /**
     * 当前节点是否是 dedicatedNAS_Message_tree 里的 data.data：
     *  1) 路径中包含 "nr-rrc.dedicatedNAS_Message_tree"
     *  2) 那一层节点下面同时有 "nas-5gs" 和 "data"
     */
    private static boolean isDedicatedNasDataNode(PacketParseContext ctx) {
        if (ctx.logicRoot == null || ctx.path.isEmpty()) return false;

        // 把 path 变成列表，方便找下标
        java.util.List<String> list = new java.util.ArrayList<>(ctx.path);
        int idx = list.indexOf("nr-rrc.dedicatedNAS_Message_tree");
        if (idx == -1) {
            return false;
        }

        // 从 logicRoot 走到 nr-rrc.dedicatedNAS_Message_tree 那一层
        JsonNode node = ctx.logicRoot;
        for (int i = 0; i <= idx; i++) {
            node = node.path(list.get(i));
            if (node.isMissingNode()) {
                return false;
            }
        }

        // 检查该层是否同时有 nas-5gs 和 data 两个 key
        return node.has("nas-5gs") && node.has("data");
    }

    // ================== raw 辅助 ==================

    /**
     * 按当前逻辑路径，在 raw 树中找到“当前节点的父节点”，
     * 用于在该父节点下拿同级的 xxx_raw。
     */
    public static JsonNode getRawParentNodeByPath(JsonNode rawRoot, Deque<String> path) {
        if (rawRoot == null) return null;
        if (path.isEmpty()) return rawRoot;

        JsonNode cur = rawRoot;
        List<String> list = new ArrayList<>(path);
        int parentDepth = list.size() - 1;

        for (int i = 0; i < parentDepth; i++) {
            String key = list.get(i);
            cur = cur.path(key);
            if (cur.isMissingNode()) {
                return null;
            }
        }
        return cur;
    }

    private static String pathToString(Deque<String> path) {
        return String.join("/", new ArrayList<>(path));
    }




    // ================== 工具方法 ==================

    /**
     * 按路径在 raw 树中找到与逻辑节点对应的节点
     */
    private static JsonNode getRawNodeByPath(JsonNode rawRoot, Deque<String> path) {
        if (rawRoot == null) return null;
        JsonNode cur = rawRoot;
        for (String key : path) {
            cur = cur.path(key);
            if (cur.isMissingNode()) {
                return null;
            }
        }
        return cur;
    }



    /**
     * 提取 RRC 信息
     */
    private static RrcInfo extractRrcInfo(JsonNode nrRrc) {
        RrcInfo info = new RrcInfo();
        String[] msgElemKeys = {
                "nr-rrc.UL_CCCH_Message_element",
                "nr-rrc.DL_CCCH_Message_element",
                "nr-rrc.UL_DCCH_Message_element",
                "nr-rrc.DL_DCCH_Message_element"
        };

        String direction = "UNKNOWN";
        JsonNode msgElemNode = null;

        for (String key : msgElemKeys) {
            JsonNode node = nrRrc.get(key);
            if (node != null && !node.isMissingNode()) {
                msgElemNode = node;
                if (key.startsWith("nr-rrc.UL_")) direction = "UL";
                else if (key.startsWith("nr-rrc.DL_")) direction = "DL";
                break;
            }
        }
        info.setDirection(direction);

        if (msgElemNode == null) {
            info.setMsgName("UNKNOWN");
            return info;
        }

        JsonNode c1Tree = JsonUtils.path(
                msgElemNode,
                "nr-rrc.message_tree",
                "nr-rrc.c1_tree"
        );
        if (c1Tree == null || c1Tree.isMissingNode()) {
            info.setMsgName("UNKNOWN");
            return info;
        }

        String msgName = "UNKNOWN";
        Iterator<String> fieldNames = c1Tree.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (field.startsWith("nr-rrc.") && field.endsWith("_element")) {
                msgName = field.substring("nr-rrc.".length(), field.length() - "_element".length());
                break;
            }
        }
        info.setMsgName(msgName);
        return info;
    }

    /**
     * 提取 NAS 信息（结合 raw）
     */
    private static NasInfo extractNasInfo(JsonNode nasLogicNode, JsonNode rawNode) {
        NasInfo info = new NasInfo();
        info.setNasNode(nasLogicNode);

        // 尝试从 raw 里直接取 nas-5gs_raw 的十六进制
        String fullHex = null;
        if (rawNode != null) {
            JsonNode nasRaw = rawNode.get("nas-5gs_raw");
            if (nasRaw != null && nasRaw.isArray() && nasRaw.size() > 0) {
                fullHex = nasRaw.get(0).asText();
            }
        }

        // fallback：如果 raw 拿不到，就退回 logic 版本的 data.data
        if (fullHex == null) {
            fullHex = JsonUtils.text(
                    JsonUtils.path(nasLogicNode, "data", "data.data"),
                    null
            );
        }

        info.setCipherTextHex(null);
        return info;
    }

}
