package com.example.procedure.infrastructure.parser;

import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NUARInfo;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.model.message.info.RrcInfo;
import com.example.procedure.support.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

/**
 * 閽堝鍗曟潯 packet 鐨?RRC + NAS 瑙ｆ瀽鍣細
 *  - 浠?packetNode 杩涘叆锛?
 *  - 鍙?DFS 閬嶅巻涓€閬?layers锛?
 *  - 閬囧埌 "nr-rrc" -> 鎻愬彇 RRC 淇℃伅鍒?RrcInfo锛堝彧瑙ｆ瀽涓€娆★級锛?
 *  - 閬囧埌 "nas-5gs" -> 鍒涘缓涓€涓?NasInfo锛屾斁鍏ョ粨鏋滅殑 nasList锛?
 *  - 閬嶅巻缁撴潫鍚庤繑鍥?RrcNasParseResult銆?
 */
/**
 * Parses one packet into RRC, NAS, PDCP, NGAP, and related protocol facts.
 *
 * Current parsing strategy:
 * 1. Traverse the packet layers once with DFS.
 * 2. Build protocol-layer info objects as matching sections are found.
 * 3. Merge logic JSON and optional raw JSON into one parse result.
 */
public class RrcNasPacketParser {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final PacketProtocolTypeDetector PROTOCOL_TYPE_DETECTOR = new PacketProtocolTypeDetector();


    /**
     * 瀵瑰鍏ュ彛锛氶拡瀵瑰崟鏉?packet 鍋氳В鏋愩€?
     */
//    public static RrcNasParseResult parse(JsonNode packetNode) {
//        JsonNode layers = packetNode.path("_source").path("layers");
//
//        RrcNasParseResult result = new RrcNasParseResult();
//        ParseContext ctx = new ParseContext(layers, result);
//
//        // 浠?layers 鏍瑰紑濮?DFS锛屼竴閬嶅畬鎴?
//        dfsTraverse(null, layers, ctx);
//
//        return result;
//    }

    /**
     * 瀵瑰鍏ュ彛锛氫紶鍏?logic.json 鍜?raw.json 鐨勫悓涓€鏉?packet
     */
    /**
     * Parses one aligned pair of logic and raw packets.
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

    // ================== 鍐呴儴瀹炵幇 ==================

//    /**
//     * 瑙ｆ瀽杩囩▼涓殑涓婁笅鏂囷細
//     *  - rootLayers锛氭暣妫?layers 鐨勬牴锛岀敤鏉ユ彁鍙?fullNasPduHex 绛夊叏灞€瀛楁
//     *  - result锛氭壙杞?RRC + NAS 鍒楄〃鐨勭粨鏋滃璞?
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
     * 娣卞害浼樺厛閬嶅巻鏁翠釜 layers锛?
     *  - fieldName 涓哄綋鍓?node 鍦ㄧ埗鑺傜偣涓殑 key锛堟牴鑺傜偣浼?null锛?
     */
    // ================== DFS锛氬彧璐熻矗璧版爲 + 鏍囪闃舵 + 璋?handle ==================

    private static void dfsTraverse(String fieldName, JsonNode node, PacketParseContext ctx) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        Deque<String> path = ctx.path;
        if (fieldName != null) {
            path.addLast(fieldName);
        }

        // ---- 1) 闃舵杩涘叆锛氬彧鍋氭爣蹇楋紝涓嶅仛瑙ｆ瀽 ----
        boolean enterMac = false;
        boolean enterPdcp = false;
        // 猸?鏂板
        // 猸?NGAP 鐩稿叧
        boolean enterNgapPdu  = false;  // 杩涘叆涓€涓?NGAP_PDU_tree
        boolean enterNgapMsg  = false;  // 杩涘叆涓€涓?NGAP message element锛坕nitiating/successful/...锛?

        boolean enterNgap = false;
        boolean enterRrc = false;
        boolean enterNas = false;


        // 猸?鏂板锛氳繘鍏?MAC 瀛愭爲
        if ("mac-nr".equals(fieldName) && node.isObject()) {
            ctx.macDepth++;
            enterMac = true;
            // 鍙〃绀衡€滆繘鍏?MAC 鍖哄煙鈥濓紝鍏蜂綋瀛楁浜ょ粰 handleMacNode
        }

        if ("pdcp-nr".equals(fieldName) && node.isObject()) {
            ctx.pdcpDepth++;
            enterPdcp = true;
        }

         //猸?NGAP锛氳繘鍏?NGAP PDU 瀛愭爲锛堟暣涓?NGAP_PDU_tree锛?
        if ("ngap.NGAP_PDU_tree".equals(fieldName) && node.isObject()) {
            ctx.ngapDepth++;
            enterNgapPdu = true;
        }

        // 猸?NGAP锛氬湪 NGAP_PDU_tree 閲岄潰閬囧埌涓€鏉?message element锛屽氨鏂板缓涓€涓?NgapInfo 鍘嬫爤
        if (ctx.inNgap() && node.isObject()) {
            if ("ngap.initiatingMessage_element".equals(fieldName)
                    || "ngap.successfulOutcome_element".equals(fieldName)
                    || "ngap.unsuccessfulOutcome_element".equals(fieldName)) {
                ctx.pushNewNgap();     // 鍒涘缓骞跺帇鏍?
                enterNgapMsg = true;   // 閫掑綊瀹岃繖涓瓙鏍戝悗瑕?pop
            }
        }

        if ("nr-rrc".equals(fieldName) && node.isObject()) {
            ctx.rrcDepth++;
            enterRrc = true;
            // 姝ゆ椂鍙〃绀衡€滄垜浠繘浜?RRC 鐨勫尯鍩熲€濓紝鍏蜂綋鎬庝箞鍙栧瓧娈典氦缁?handleRrcNode
        }

        if ("nas-5gs".equals(fieldName) && node.isObject()) {
            NasInfo nas = new NasInfo();
            nas.setNasNode(node);

            // 2. 鎵惧埌 raw 鏍戜腑涓庡綋鍓?nas-5gs 鍚岀骇鐨?parent锛屾嬁 nas-5gs_raw
            JsonNode rawParent = getRawParentNodeByPath(ctx.rawRoot, ctx.path);
            if (rawParent != null) {
                JsonNode nasRaw = rawParent.get("nas-5gs_raw");
                if (nasRaw != null && nasRaw.isArray() && nasRaw.size() > 0) {
                    String fullHex = normalizeHex(nasRaw.get(0).asText());
                    nas.setFullNasPduHex(fullHex);
                    nas.putFieldPath("nas-5gs_raw", pathToString(ctx.path) + " (raw sibling: nas-5gs_raw)");
                }
            }

            // 3. 鍩轰簬 logic + raw锛屽杩欎釜 nas-5gs 鍋氫竴娆″畬鏁村垎鏋?
            analyzeNasNode(nas, node);

            ctx.result.getNasList().add(nas);
            ctx.nasStack.push(nas);
            enterNas = true;
            // 姝ゆ椂鍙〃绀衡€滄垜浠繘浜?NAS 鍖哄煙鈥濓紝鍏蜂綋瀛楁浜ょ粰 handleNasNode
        }
        // 猸?http2:json 鍦烘櫙锛氶亣鍒?json.object锛岀湅鐪嬫槸鍚︽槸 Nausf_UEAuthentication_AuthenticateResponse
        if ("json.object".equals(fieldName) && node.isValueNode()) {
            handleHttp2JsonObject(node, ctx);
        }

        // ---- 2) 闃舵澶勭悊锛氱湡姝ｇ殑涓氬姟閫昏緫浜ょ粰鍚勯樁娈电殑 handle ----

        // 猸?鏂板锛氬厛澶勭悊 MAC
        if (ctx.inMac()) {
            handleMacNode(fieldName, node, ctx);
        }

        // 猸?鏂板锛歅DCP 闃舵
        if (ctx.inPdcp()) {
            handlePdcpNode(fieldName, node, ctx);
        }

        if (ctx.inNgap()) {
            handleNgapNode(fieldName, node, ctx);  // 猸?鏂板
        }

        if (ctx.inRrc()) {
            handleRrcNode(fieldName, node, ctx);
        }

        if (ctx.inNas()) {
            handleNasNode(fieldName, node, ctx);
        }


        // 浠ュ悗浣犺鍔?MAC / NGAP / HTTP锛岀洿鎺ュ湪杩欓噷鍔狅細
        // if (ctx.inMac())  handleMacNode(fieldName, node, ctx);
        // if (ctx.inNgap()) handleNgapNode(fieldName, node, ctx);

        // ---- 3) 閫掑綊瀛愯妭鐐?----

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

        // ---- 4) 闃舵閫€鍑猴細涓庤繘鍏ュ绉?----

        // NGAP锛氬厛閫€鍑?message锛屽啀閫€鍑?PDU
        if (enterNgapMsg) {
            ctx.ngapStack.pop();
        }
        if (enterNgapPdu) {
            ctx.ngapDepth--;
        }

        if (enterNas) {
            ctx.nasStack.pop();
        }

        // 猸?鏂板
        if (enterPdcp) {
            ctx.pdcpDepth--;
        }

        if (enterRrc) {
            ctx.rrcDepth--;
        }
        // 猸?鏂板
        if (enterMac) {
            ctx.macDepth--;
        }

        // 猸?鏂板
        if (enterNgap) {
            ctx.ngapDepth--;
        }

        if (fieldName != null) {
            path.removeLast();
        }
    }


    /**
     * 澶勭悊 http2:json 閲岀殑 json.object锛?
     *  鏈熸湜鍐呭绫讳技锛?
     *  {
     *      "authResult": "AUTHENTICATION_SUCCESS",
     *      "supi": "imsi-001010000000001",
     *      "kseaf": "5a8960ff6a8b013c7b..."
     *  }
     *  涓€鏃﹀尮閰嶅埌锛屽氨鏋勯€?NUARInfo锛屾秷鎭悕鍥哄畾涓?
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
            // 涓嶆槸鍚堟硶 JSON锛岀洿鎺ュ拷鐣?
            return;
        }
        if (obj == null || !obj.isObject()) {
            return;
        }

        // 蹇呴』鏈?kseaf 鍜?supi锛沘uthResult 鍙€変絾涓€鑸瓨鍦?
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

        // 鏋勯€?/ 鑾峰彇 NUARInfo
        NUARInfo nuar = ctx.ensureNuarInfo();

        // 娑堟伅鍚嶅浐瀹?
        nuar.setMsgName("Nausf_UEAuthentication_AuthenticateResponse");

        nuar.setKseafHex(kseaf);
        nuar.setSupi(supi);

        // 浠?supi 涓彁鍙?IMSI锛歩msi-001010000000001 -> 001010000000001
        if (supi.startsWith("imsi-")) {
            nuar.setImsi(supi.substring("imsi-".length()));
        } else {
            nuar.setImsi(supi);
        }

        if (authResult != null) {
            nuar.setAuthResult(authResult);
        }

        // 璁板綍 json.object 鐨?JSON 璺緞
        String pathStr = pathToString(ctx.path);
        nuar.putFieldPath("json.object", pathStr);
    }


    /**
     * NGAP 闃舵鐨勬墍鏈夐€昏緫锛?
     *  - 璇嗗埆 PDU 绫诲瀷锛歩nitiating/successfulOutcome/unsuccessfulOutcome
     *  - 鎶藉彇 procedureCode / criticality
     *  - 浠?ngap.value_element 涓嬬殑 xxx_element 涓彁鍙栨秷鎭被鍨嬶細
     *      ngap.UplinkNASTransport_element -> "UplinkNASTransport"
     */
    private static void handleNgapNode(String fieldName, JsonNode node, PacketParseContext ctx) {
        NgapInfo ngap = ctx.currentNgap();
        String pathStr = pathToString(ctx.path);

        // 1) 瀵硅薄鑺傜偣锛氬鐞?initiating/successful/unsuccessful 涓夊ぇ绫?
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

        // 2) 鍊艰妭鐐癸細procedureCode / criticality
        if (node.isValueNode()) {
            String value = node.asText();
            switch (fieldName) {
                // 猸?SecurityKey锛歝9:5f:32:6d:... -> c95f326d...
                case "ngap.SecurityKey":
                    ngap.setSecurityKeyHex(normalizeHex(value));
                    ngap.putFieldPath("ngap.SecurityKey", pathStr);
                    break;

                // 猸?RAN UE NGAP ID
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
     * 浠?ngap.value_element 閲屽垽鏂?NGAP 娑堟伅绫诲瀷锛?
     *  渚嬪 "ngap.UplinkNASTransport_element" -> "UplinkNASTransport"
     */
    private static void detectNgapMsgTypeFromValueElement(JsonNode msgElemNode,
                                                          NgapInfo ngap,
                                                          String basePath) {
        // Reuse the shared detector so NGAP message-type rules stay in one place.
        String msgName = PROTOCOL_TYPE_DETECTOR.detectNgapMessageType(msgElemNode);
        if (msgName == null || msgName.isBlank()) {
            return;
        }

        String fieldPath = basePath + "/ngap.value_element/ngap." + msgName + "_element";
        ngap.setMsgName(msgName);
        ngap.putFieldPath("msgType", fieldPath);

        // Direction inference is still local because it is business meaning,
        // not generic packet-shape detection.
        String dir = null;
        switch (msgName) {
            case "InitialUEMessage":
            case "DownlinkNASTransport":
            case "InitialContextSetupRequest":
                dir = "DL";
                break;
            case "UplinkNASTransport":
            case "InitialContextSetupResponse":
                dir = "UL";
                break;
            default:
                break;
        }

        if (dir != null) {
            ngap.setDirection(dir);
            ngap.putFieldPath("direction", fieldPath);
        }
    }





    /**
     * 閽堝涓€涓?nas-5gs 鑺傜偣锛屽垽鏂槸鍚︿负鍔犲瘑 NAS锛屽苟鍦ㄥ彲琛屾椂鎴嚭瀵嗘枃锛?
     *  - 涓嶅啀渚濊禆鍥哄畾 "PD 39"锛岃€屾槸鍖归厤 "Not a NAS 5GS PD X (Unknown)" 杩欑妯″紡锛?
     *  - 濡傛灉 "Security protected NAS 5GS message" 鏄┖鐨勶紝灏变笉鎴ご銆佷笉鏍囪鍔犲瘑銆?
     */
    private static void analyzeNasNode(NasInfo nas, JsonNode nasLogicNode) {
        if (nasLogicNode == null || nasLogicNode.isMissingNode()) {
            return;
        }

        // 1. 鎷垮埌 Security protected / Plain 涓や釜瀛愯妭鐐?
        JsonNode secNode   = nasLogicNode.get("Security protected NAS 5GS message");
        JsonNode plainNode = nasLogicNode.get("Plain NAS 5GS Message");

        // 鏄惁鏈?Plain Unknown 缁撴瀯锛堜笉鍐嶅浐瀹?PD=39锛?
        boolean hasPlainUnknown = hasPlainUnknownPd(plainNode);

        // 濡傛灉 Security protected 鑺傜偣涓嶅瓨鍦ㄣ€佹垨鑰呮槸绌哄璞★紝灏辩洿鎺ヨ繑鍥烇紝涓嶈涓烘槸鍔犲瘑
        if (secNode == null || secNode.isMissingNode() || !secNode.isObject() || secNode.size() == 0) {
            return;
        }

        // 2. 浠?Security protected 閲屾嬁瀛楁锛堝鏋滄湁锛?
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

        // 3. 鍙湁褰?security_header_type == "4" 鏃讹紝鎵嶈涓烘槸鍔犲瘑 + 瀹屾暣鎬т繚鎶?
        if (!("4".equals(sht) || "2".equals(sht))) {
            // 杩欓噷璇存槑杩欎釜 Security protected 鑺傜偣骞朵笉鏄€滃姞瀵?瀹屼繚(4)鈥濋偅绉嶏紝
            // 涓嶇户缁線涓嬪綋瀵嗘枃鏉ュ鐞嗭紝鐩存帴杩斿洖銆?
            return;
        }

        // 鏍囪杩欎釜 NAS 宸茬粡鍔犲瘑
        nas.setEncrypted(true);

        // 4. 鎴瘑鏂囷細蹇呴』鍚屾椂婊¤冻
        //   - 鏈?fullNasPduHex锛堜粠 nas-5gs_raw 鏉ワ級
        //   - Security protected 涓嶆槸绌?
        //   - 锛堝彲閫夛級瀛樺湪 Plain Unknown 杩欎釜缁撴瀯锛岃鏄庡悗闈㈢‘瀹炶窡鐫€涓€娈点€寃ireshark 瑙ｄ笉鍑虹殑鏄庢枃銆?
        String fullHex = nas.getFullNasPduHex();
        if (fullHex == null || fullHex.isEmpty()) {
            return;
        }

        // 濡傛灉浣犳兂鏇翠繚瀹堬細鍙湁鏈?Unknown PD 缁撴瀯鏃舵墠鎴瘑鏂?
        // 濡傛灉涓嶆兂渚濊禆 Unknown PD锛屼篃鍙互涓嶅姞 hasPlainUnknown 鐨勫垽鏂?
        if (!hasPlainUnknown) {
            // 鏈変簺鎯呭喌涓?Security protected 鑺傜偣鏈?header 瀛楁锛屼絾鏄悗闈㈠彲鑳芥病鏈?Unknown PD 缁撴瀯锛?
            // 浣犺繖閲岄€夋嫨锛氫笉鎴紙鎴栬€呬綘涔熷彲浠ラ€夋嫨鐓ф牱鎴紝鐪嬩綘闇€姹傦級
            return;
        }

        // 5. 鎸?NAS 瀹夊叏澶存牸寮忔埅鎺夊墠 7 瀛楄妭浣滀负澶撮儴锛堜綘涔嬪墠涓剧殑渚嬪瓙锛?
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



    // ================== RRC 闃舵澶勭悊 ==================

    /**
     * MAC 闃舵鐨勬墍鏈夐€昏緫閮藉啓鍦ㄨ繖閲岋細
     *  - currentMac = ctx.ensureMacInfo();
     *  - 鐩墠鍙叧蹇?mac-nr.rnti / mac-nr.rnti-type 涓や釜瀛楁銆?
     */
    private static void handleMacNode(String fieldName, JsonNode node, PacketParseContext ctx) {
        MacInfo mac = ctx.ensureMacInfo();
        String pathStr = pathToString(ctx.path);

        // 鎴戜滑瑕佺殑瀛楁鍦?mac-nr.context_tree 涓嬮潰锛屾槸鏅€氬€艰妭鐐癸細
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

        // 濡傛灉浠ュ悗鎯崇敤 raw锛屽彲浠ュ儚 RRC 涓€鏍凤細
        // JsonNode rawParent = getRawParentNodeByPath(ctx.rawRoot, ctx.path);
        // JsonNode macRaw = rawParent != null ? rawParent.get("mac-nr_raw") : null;
        // ... 鐪嬮渶瑕佸啀鎵╁睍
    }


    /**
     * PDCP 闃舵鐨勬墍鏈夐€昏緫閮藉啓鍦ㄨ繖閲岋細
     *  - 鐩墠鍙叧蹇冿細
     *      pdcp-nr.signalling-data
     *      pdcp-nr.mac
     *  - signalling-data 瑕佸幓鎺夊啋鍙凤紱
     *  - mac 瑕佸幓鎺?0x 鍓嶇紑銆?
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
                // 渚嬪锛?b4:3f:7c:e7:0a:c5:76:a3" -> "b43f7ce70ac576a3"
                String normalized = normalizeHex(value);
                pdcp.setSignallingDataHex(normalized);
                pdcp.putFieldPath("pdcp-nr.signalling-data", pathStr);
                pdcp.setPdcpencrypted(true);
                break;
            }
            case "pdcp-nr.mac": {
                // 渚嬪锛?0x51a85e19" -> "51a85e19"
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
     * 褰掍竴鍖栧崄鍏繘鍒跺瓧绗︿覆锛?
     *  - 鍘绘帀鍓嶅悗绌烘牸
     *  - 鍘绘帀鍓嶇紑 0x / 0X
     *  - 鍘绘帀鎵€鏈夊啋鍙峰拰绌烘牸
     *  - 杞垚灏忓啓
     *  渚嬪锛?
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
     * RRC 闃舵鐨勬墍鏈夐€昏緫閮藉啓鍦ㄨ繖閲岋細
     *  - 闇€瑕佺殑鏃跺€欒嚜宸卞垽鏂?fieldName锛?
     *  - 鐪熸瑙ｆ瀽 RRC 娑堟伅绫诲瀷 / 鏂瑰悜 / randomValue / C-RNTI 绛夛紱
     *  - 鎯崇敤 raw 灏辫嚜宸辫皟 getRawParentNodeByPath(ctx.rawRoot, ctx.path)銆?
     */
    private static void handleRrcNode(String fieldName, JsonNode node, PacketParseContext ctx) {
        RrcInfo rrc = ctx.ensureRrcInfo();
        String pathStr = pathToString(ctx.path);

        // 绀轰緥1锛氭牴鎹?UL/DCCH/CCCH 杩欑缁撴瀯瀛楁鏉ュ垽鏂柟鍚?+ 娑堟伅绫诲瀷
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

        // 绀轰緥2锛氬€煎瓧娈碉紝鎸夊瓧娈靛悕鍖归厤浣犲叧蹇冪殑鍐呭
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

        // 绀轰緥3锛氶渶瑕?raw 鏃讹紝闅忔椂鍙互杩欎箞鐢細
        // JsonNode rawParent = getRawParentNodeByPath(ctx.rawRoot, ctx.path);
        // JsonNode someRaw = rawParent != null ? rawParent.get("nr-rrc_raw") : null;
        // 浣犲彲浠ュ湪杩欓噷灏?raw 璺緞涔熻褰曞埌 rrc 閲岋紝灏嗘潵鐢ㄤ簬杩樺師瀵嗘枃绛夈€?
    }

    /**
     * 浠?UL/DCCH element 涓嬬殑 c1_tree 閲屽垽鏂秷鎭被鍨嬶細rrcSetupRequest / rrcSetup / rrcSetupComplete ...
     * 杩欎篃鏄湪 handle 閲屽仛锛岃€屼笉鏄湪 dfsTraverse 閲屻€?
     */
    private static void detectRrcMsgTypeFromC1(JsonNode msgElemNode,
                                               RrcInfo rrc,
                                               String basePath) {
        // Reuse the shared detector so RRC c1 detection stays consistent.
        String msgName = PROTOCOL_TYPE_DETECTOR.detectRrcMessageType(msgElemNode);
        if (msgName == null || msgName.isBlank()) {
            return;
        }

        rrc.setMsgName(msgName);
        rrc.putFieldPath(
                "msgType",
                basePath + "/nr-rrc.message_tree/nr-rrc.c1_tree/nr-rrc." + msgName + "_element"
        );
    }

    // ================== NAS 闃舵澶勭悊 ==================

    /**
     * NAS 闃舵鐨勬墍鏈夐€昏緫閮藉啓鍦ㄨ繖閲岋細
     *  - currentNas = ctx.currentNas()锛?
     *  - 鏍规嵁 fieldName 鍒ゆ柇浣犲叧蹇冪殑 NAS 瀛楁锛?
     *  - 闇€瑕?raw 鏃堕殢鏃剁敤 getRawParentNodeByPath(ctx.rawRoot, ctx.path)銆?
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

                // 浣犲師鏉ュ凡鏈夌殑鍏跺畠瀛楁涔熷彲浠ョ户缁斁杩欓噷
                default:
                    break;
            }
        }
    }



    /**
     * 褰撳墠鑺傜偣鏄惁鏄?dedicatedNAS_Message_tree 閲岀殑 data.data锛?
     *  1) 璺緞涓寘鍚?"nr-rrc.dedicatedNAS_Message_tree"
     *  2) 閭ｄ竴灞傝妭鐐逛笅闈㈠悓鏃舵湁 "nas-5gs" 鍜?"data"
     */
    private static boolean isDedicatedNasDataNode(PacketParseContext ctx) {
        if (ctx.logicRoot == null || ctx.path.isEmpty()) return false;

        // 鎶?path 鍙樻垚鍒楄〃锛屾柟渚挎壘涓嬫爣
        java.util.List<String> list = new java.util.ArrayList<>(ctx.path);
        int idx = list.indexOf("nr-rrc.dedicatedNAS_Message_tree");
        if (idx == -1) {
            return false;
        }

        // 浠?logicRoot 璧板埌 nr-rrc.dedicatedNAS_Message_tree 閭ｄ竴灞?
        JsonNode node = ctx.logicRoot;
        for (int i = 0; i <= idx; i++) {
            node = node.path(list.get(i));
            if (node.isMissingNode()) {
                return false;
            }
        }

        // 妫€鏌ヨ灞傛槸鍚﹀悓鏃舵湁 nas-5gs 鍜?data 涓や釜 key
        return node.has("nas-5gs") && node.has("data");
    }

    // ================== raw 杈呭姪 ==================

    /**
     * 鎸夊綋鍓嶉€昏緫璺緞锛屽湪 raw 鏍戜腑鎵惧埌鈥滃綋鍓嶈妭鐐圭殑鐖惰妭鐐光€濓紝
     * 鐢ㄤ簬鍦ㄨ鐖惰妭鐐逛笅鎷垮悓绾х殑 xxx_raw銆?
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




    // ================== 宸ュ叿鏂规硶 ==================

    /**
     * 鎸夎矾寰勫湪 raw 鏍戜腑鎵惧埌涓庨€昏緫鑺傜偣瀵瑰簲鐨勮妭鐐?
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
     * 鎻愬彇 RRC 淇℃伅
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
     * 鎻愬彇 NAS 淇℃伅锛堢粨鍚?raw锛?
     */
    private static NasInfo extractNasInfo(JsonNode nasLogicNode, JsonNode rawNode) {
        NasInfo info = new NasInfo();
        info.setNasNode(nasLogicNode);

        // 灏濊瘯浠?raw 閲岀洿鎺ュ彇 nas-5gs_raw 鐨勫崄鍏繘鍒?
        String fullHex = null;
        if (rawNode != null) {
            JsonNode nasRaw = rawNode.get("nas-5gs_raw");
            if (nasRaw != null && nasRaw.isArray() && nasRaw.size() > 0) {
                fullHex = nasRaw.get(0).asText();
            }
        }

        // fallback锛氬鏋?raw 鎷夸笉鍒帮紝灏遍€€鍥?logic 鐗堟湰鐨?data.data
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
