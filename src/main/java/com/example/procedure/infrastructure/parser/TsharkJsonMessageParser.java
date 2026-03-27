package com.example.procedure.infrastructure.parser;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NUARInfo;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.model.message.info.RrcInfo;
import com.example.procedure.support.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * 璐熻矗锛氳鍙?tshark 鐢熸垚鐨?JSON 鏂囦欢锛?
 * 鎶婃瘡涓寘瑙ｆ瀽鎴愪竴鏉?SignalingMessage銆?
 * 褰撳墠鐗堟湰瑙勫垯锛?
 * 1. 瑙ｆ瀽 frame.time_epoch / frame.number
 * 2. 鍒╃敤 frame.protocols 涓㈡帀鈥渕ac-nr 鍚庨潰鍙湁 rlc-nr鈥濈殑鍖?
 * 3. 瀵规瘡鏉?packet 璋冪敤 RrcNasPacketParser锛屼竴閬嶈В鏋愬嚭 RRC + NAS 淇℃伅锛?
 *    鐢?RRC 淇℃伅濉厖 SignalingMessage 鐨勫熀纭€瀛楁銆?
 */
/**
 * Builds signaling-message objects from tshark JSON output.
 *
 * Current responsibilities:
 * 1. Read logic JSON and optional raw JSON files produced by tshark.
 * 2. Parse each packet through {@link RrcNasPacketParser}.
 * 3. Convert each parse result into one {@link SignalingMessage}.
 */
public class TsharkJsonMessageParser implements SignalingMessageParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 瀵瑰鍏ュ彛锛氫紶鍏?JSON 鏂囦欢璺緞锛堜粎 logic json锛夛紝杩斿洖鎵€鏈変俊浠よ褰?
     */
    /**
     * Parses a logic-only tshark JSON file.
     */
    @Override
    public List<SignalingMessage> parseFile(String jsonFilePath) throws IOException {
        String raw = Files.readString(Path.of(jsonFilePath), StandardCharsets.UTF_8);

        int idx = raw.indexOf('[');
        if (idx < 0) {
            throw new IllegalArgumentException("JSON file does not contain a JSON array: " + jsonFilePath);
        }
        String jsonArrayText = raw.substring(idx);

        ArrayNode root = (ArrayNode) objectMapper.readTree(jsonArrayText);

        List<SignalingMessage> result = new ArrayList<>();
        for (JsonNode pkt : root) {
            SignalingMessage msg = buildMessage(pkt); // 鍙敤 logic
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * 猸?鏂板锛氫紶鍏?logic.json 鍜?json_raw 涓や釜鏂囦欢锛?
     * 姣忎釜涓嬫爣 i 鐨?packet 瑙嗕负鍚屼竴甯э紝鍚屾椂瑙ｆ瀽銆?
     */
    /**
     * Parses aligned logic and raw tshark JSON files together.
     */
    @Override
    public List<SignalingMessage> parseFileWithRaw(String logicJsonPath, String rawJsonPath) throws IOException {
        String logicText = Files.readString(Path.of(logicJsonPath), StandardCharsets.UTF_8);
        String rawText   = Files.readString(Path.of(rawJsonPath),   StandardCharsets.UTF_8);

        int idx1 = logicText.indexOf('[');
        int idx2 = rawText.indexOf('[');
        if (idx1 < 0) {
            throw new IllegalArgumentException("Logic JSON file does not contain a JSON array: " + logicJsonPath);
        }
        if (idx2 < 0) {
            throw new IllegalArgumentException("Raw JSON file does not contain a JSON array: " + rawJsonPath);
        }

        ArrayNode logicRoot = (ArrayNode) objectMapper.readTree(logicText.substring(idx1));
        ArrayNode rawRoot   = (ArrayNode) objectMapper.readTree(rawText.substring(idx2));

        if (logicRoot.size() != rawRoot.size()) {
            throw new IllegalStateException(
                    "logic json size (" + logicRoot.size() + ") != raw json size (" + rawRoot.size() + ")");
        }

        List<SignalingMessage> result = new ArrayList<>();
        for (int i = 0; i < logicRoot.size(); i++) {
            JsonNode logicPkt = logicRoot.get(i);
            JsonNode rawPkt   = rawRoot.get(i);

            SignalingMessage msg = buildMessage(logicPkt, rawPkt); // logic + raw 鍚屾椂鐢?
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * 浼犲叆涓や釜 JSON 鏂囦欢璺緞锛屽垎鍒В鏋愬悗鍚堝苟锛?
     * 鍐嶆牴鎹?timestamp + frameNo 鎺掑簭锛岃繑鍥炰竴涓€滃叏灞€鏃跺簭鈥濈殑鍒楄〃銆?
     * 锛堣繖涓増鏈彧鐢?logic json锛屽鏋滆鐢?raw锛屽彲浠ュ啀鍐欎竴涓?withRaw 鐗堟湰锛?
     */
    @Override
    public List<SignalingMessage> parseAndMerge(String logicJsonPath1, String logicJsonPath2,
                                                String rawJsonPath1, String rawJsonPath2) throws IOException {
        List<SignalingMessage> all = new ArrayList<>();

        List<SignalingMessage> list1 = parseFileWithRaw(logicJsonPath1, rawJsonPath1);
        List<SignalingMessage> list2 = parseFileWithRaw(logicJsonPath2, rawJsonPath2);

        all.addAll(list1);
        all.addAll(list2);

        // 1) 鍏堟寜鏃堕棿 + 甯у彿鎺掑ソ鈥滃師濮嬫椂搴忊€?
        all.sort(Comparator
                .comparingLong(SignalingMessage::getTimestamp)
                .thenComparingLong(SignalingMessage::getFrameNo));

        // 2) 鍋氣€? 澶у叧閿秷鎭€濈殑浼樺厛鎺掑簭
        List<SignalingMessage> result = new ArrayList<>();
        Set<SignalingMessage> picked =
                Collections.newSetFromMap(new IdentityHashMap<>());


        // 1) RRCSetupComplete
        SignalingMessage m1 = pickFirst(all, picked, this::isRrcSetupComplete);
        if (m1 != null) {
            m1.setMsgType("RRCSetupComplete");
            result.add(m1);
        }

        // 2) NGAP InitialUEMessage
        SignalingMessage m2 = pickFirst(all, picked, this::isNgapInitialUeMessage);
        if (m2 != null) {
            m2.setMsgType("Initial UE Message");
            result.add(m2);
        }

        // 3) NAUSF Nausf_UEAuthentication_AuthenticateResponse
        SignalingMessage m3 = pickFirst(all, picked, this::isNausfAuthResponse);
        if (m3 != null) {
            m3.setMsgType("Nausf_UEAuthentication_Authenticate Response");
            result.add(m3);
        }

        // 4) NAS Security Mode Command (mmType = 0x5d)
        SignalingMessage m4 = pickFirst(all, picked, this::isNasSecurityModeCommand);
        if (m4 != null) {
            m4.setMsgType("NAS SecurityModeCommand");
            result.add(m4);
        }

        // 5) NGAP InitialContextSetupRequest
        SignalingMessage m5 = pickFirst(all, picked, this::isNgapInitialContextSetupRequest);
        if (m5 != null) {
            m5.setMsgType("Initial Context Setup Request");
            result.add(m5);
        }

        // 6) RRC SecurityModeCommand
        SignalingMessage m6 = pickFirst(all, picked, this::isRrcSecurityModeCommand);
        if (m6 != null) {
            m6.setMsgType("RRC SecurityModeCommand");
            result.add(m6);
        }

        // 鍏朵綑娑堟伅鎸夊師濮嬮『搴忚ˉ鍦ㄥ悗闈?
        for (SignalingMessage m : all) {
            if (!picked.contains(m)) {
                result.add(m);
            }
        }

        // 3) 鏍规嵁 NAUSF 鐨?IMSI 璁＄畻涓€涓€滃叏灞€ ueId鈥?
        String ueId = resolveGlobalUeIdFromNausf(result);

        // 4) 缁欐瘡鏉℃秷鎭垎閰嶈嚜澧?messageId + ueId
        int seq = 1;
        for (SignalingMessage m : result) {
            // 鑷 messageId锛屾瘮濡?MSG-1, MSG-2 ...
            m.setMsgId("MSG-" + seq);

            // 濡傛灉鏈夊叏灞€ ueId锛屽氨缁欐墍鏈夋秷鎭ˉ涓婏紙鐜板湪榛樿涓€涓姄鍖呭彧鏈変竴涓?UE锛?
            if (ueId != null && (m.getUeId() == null || m.getUeId().isEmpty())) {
                m.setUeId(ueId);
            }

            seq++;
        }

        return result;
    }
    /**
     * 浠庡凡缁忔帓濂藉簭鐨勬秷鎭垪琛ㄤ腑锛屾壘涓€鏉?NAUSF 娑堟伅锛?
     * 鐢ㄥ畠鐨?IMSI 浣滀负鍏ㄥ眬鐨?ueId锛堝綋鍓嶅亣璁句竴涓姄鍖呴噷鍙湁涓€涓?UE锛夈€?
     */
    private String resolveGlobalUeIdFromNausf(List<SignalingMessage> messages) {
        for (SignalingMessage m : messages) {
            NUARInfo nuar = m.getNuarInfo();
            if (nuar == null) continue;

            String imsi = nuar.getImsi();
            if (imsi != null && !imsi.isEmpty()) {
                return imsi;
            }
        }
        return null;
    }


    private SignalingMessage pickFirst(List<SignalingMessage> all,
                                       Set<SignalingMessage> picked,
                                       Predicate<SignalingMessage> predicate) {
        for (SignalingMessage m : all) {
            if (!picked.contains(m) && predicate.test(m)) {
                picked.add(m);
                return m;
            }
        }
        return null;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private boolean isRrcSetupComplete(SignalingMessage m) {
        RrcInfo rrc = m.getRrcInfo();
        if (rrc != null && equalsIgnoreCase(rrc.getMsgName(), "rrcSetupComplete")) {
            return true;
        }
        // 鍏滃簳鐢ㄩ《灞?msgType 鍒ゆ柇
        return equalsIgnoreCase(m.getMsgType(), "rrcSetupComplete");
    }

    private boolean isNgapInitialUeMessage(SignalingMessage m) {
        List<NgapInfo> ngapList = m.getNgapInfoList();
        if (ngapList == null || ngapList.isEmpty()) return false;

        for(NgapInfo ngap : ngapList){
            if (ngap != null && equalsIgnoreCase(ngap.getMsgName(), "InitialUEMessage")) {
                return true;
            }
            return "NGAP".equalsIgnoreCase(m.getProtocolLayer())
                    && equalsIgnoreCase(m.getMsgType(), "InitialUEMessage");
        }
        return false;
    }

    private boolean isNausfAuthResponse(SignalingMessage m) {
        NUARInfo nuar = m.getNuarInfo();
        if (nuar != null && equalsIgnoreCase(
                nuar.getMsgName(), "Nausf_UEAuthentication_AuthenticateResponse")) {
            return true;
        }
        return equalsIgnoreCase(m.getMsgType(), "Nausf_UEAuthentication_AuthenticateResponse");
    }

    private boolean isNasSecurityModeCommand(SignalingMessage m) {
        List<NasInfo> nasList = m.getNasList();
        if (nasList == null || nasList.isEmpty()) return false;

        for (NasInfo nas : nasList) {
            if (equalsIgnoreCase(nas.getMmMessageType(), "0x5d")) {
                return true;
            }
        }
        return false;
    }

    private boolean isNgapInitialContextSetupRequest(SignalingMessage m) {
        List<NgapInfo> ngapList = m.getNgapInfoList();
        if (ngapList == null || ngapList.isEmpty()) return false;

        for(NgapInfo ngap : ngapList){
            if (ngap != null && equalsIgnoreCase(ngap.getMsgName(), "InitialContextSetupRequest")) {
                return true;
            }
            return "NGAP".equalsIgnoreCase(m.getProtocolLayer())
                    && equalsIgnoreCase(m.getMsgType(), "InitialContextSetupRequest");
        }
        return false;
    }

    private boolean isRrcSecurityModeCommand(SignalingMessage m) {
        RrcInfo rrc = m.getRrcInfo();
        if (rrc != null && equalsIgnoreCase(rrc.getMsgName(), "securityModeCommand")) {
            return true;
        }
        return "RRC".equalsIgnoreCase(m.getProtocolLayer())
                && equalsIgnoreCase(m.getMsgType(), "securityModeCommand");
    }






    // ================== 鏍稿績鏋勫缓 ==================

    /**
     * 鍏煎鏃т唬鐮侊細鍙湁 logic json 鐨勬椂鍊?
     */
    private SignalingMessage buildMessage(JsonNode packetNode) {
        return buildMessage(packetNode, null);
    }

    /**
     * 鏍稿績锛氭妸涓€鏉?tshark JSON 閲岀殑 packet 杞垚 SignalingMessage
     * @param logicPacketNode  -T json 杈撳嚭涓殑璇?packet
     * @param rawPacketNode    -T jsonraw 杈撳嚭涓殑瀵瑰簲 packet锛堝彲浠ヤ负 null锛?
     */
    private SignalingMessage buildMessage(JsonNode logicPacketNode, JsonNode rawPacketNode) {
        JsonNode layers = logicPacketNode.path("_source").path("layers");
        // 鍘熷鍗忚涓诧紙鐢ㄦ潵鍒ゆ柇鏄惁鍖呭惈 "http2:json" 杩欑缁勫悎锛?
        String protoStr = JsonUtils.text(
                JsonUtils.path(layers, "frame", "frame.protocols"),
                ""
        );

        // 1) 瑙ｆ瀽 frame.protocols锛屾嬁鍒板崗璁摼锛堝彧鐪?logic锛?
        List<String> protos = parseProtocols(layers);

        // 猸?鏂板锛氬鏋滃崗璁摼涓笉鍖呭惈 ngap銆乭ttp2:json銆乵ac-nr銆乶r-rrc銆乶as-5gs銆乸dcp-nr锛屽垯鐩存帴涓㈠純
        if (!containsUsefulProtocol(protos, protoStr)) {
            return null;
        }

        // 2) 濡傛灉鏄?Uu 鐨?mac-nr 鍖咃紝涓?mac-nr 鍚庡彧鏈?rlc-nr锛堟病鏈?pdcp-nr / nr-rrc / nas-5gs锛夛紝鐩存帴涓㈡帀
        if (isUuMacNr(protos) && onlyMacAndRlcAfterMac(protos)) {
            return null;
        }

        // 3) 鍩烘湰瀛楁锛氭椂闂存埑 & 甯у彿
        long frameNo   = parseFrameNo(layers);
        long timestamp = parseTimestamp(layers);

        // 4) iface 绮楀垽锛氭湁 mac-nr 灏卞綋 Uu锛涘惁鍒?UNKNOWN锛堝悗闈㈠啀鎵╁睍 N2 绛夛級
        String iface = resolveInterface(protos);

        // 5) 榛樿鍊?
        String protocolLayer = "UNKNOWN";
        String direction     = "UNKNOWN";
        String msgType       = "UNKNOWN";

        // 6) 涓€閬嶈В鏋愶細閽堝杩欎竴鏉?packet锛岃В鏋愬嚭 RRC + NAS 淇℃伅
        RrcNasParseResult parsed =  RrcNasPacketParser.parse(logicPacketNode, rawPacketNode);

        RrcInfo rrcInfo = parsed.getRrcInfo();
        if (rrcInfo != null && rrcInfo.getMsgName() != null) {
            protocolLayer = "RRC";
            direction     = rrcInfo.getDirection();  // UL / DL
            msgType       = rrcInfo.getMsgName();    // rrcSetupRequest / rrcSetup / rrcSetupComplete ...
        } else if (!parsed.getNasList().isEmpty()) {
            // 娌℃湁 RRC 浣嗘湁 nas-5gs锛屽彲浠ュ厛绮楃暐鏍囪鎴?NAS
            protocolLayer = "NAS";
            direction     = "UNKNOWN";              // 浠ュ悗浣犲彲浠ユ牴鎹?NGAP/NAS 鏂瑰悜鍐嶇粏鍖?
            msgType       = "NAS_5GS";              // 鍏堢粺涓€鍙?NAS_5GS锛屽悗闈㈡寜闇€瑕佺粏鍒?
        }

        // 7) 缁勮 SignalingMessage
        SignalingMessage msg = new SignalingMessage();
        msg.setFrameNo(frameNo);
        msg.setTimestamp(timestamp);
        msg.setIface(iface);
        msg.setDirection(direction);
        msg.setProtocolLayer(protocolLayer);
        msg.setMsgType(msgType);

        msg.setMsgId("FRAME-" + frameNo);
        msg.setUeId(null);
        msg.setPayload(null);

        // 猸?鎶婅繖涓€鏉℃秷鎭噷鎵胯浇鐨?NAS 鍒楄〃鎸備笂鍘?
        List<NasInfo> nasList = parsed.getNasList();
        msg.setNasList(nasList == null ? Collections.emptyList() : nasList);

        // 猸?鏂板锛氭妸鍏跺畠鍚勫眰 Info 涔熸寕鍒?SignalingMessage 涓婏紝鏂逛究娴嬭瘯/灞曠ず
        msg.setMacInfo(parsed.getMacInfo());
        msg.setPdcpInfo(parsed.getPdcpInfo());
        msg.setRrcInfo(parsed.getRrcInfo());
        List<NgapInfo> ngapInfoList = parsed.getNgapList();
        msg.setNgapInfoList(nasList == null ? Collections.emptyList() : ngapInfoList);

        msg.setNuarInfo(parsed.getNuarInfo());

        return msg;
    }

    // ====================== 甯姪鏂规硶 ======================

    /**
     * 浠?frame.protocols 瑙ｆ瀽鍑哄崗璁摼
     * 渚嬪 "user_dlt:udp:mac-nr:rlc-nr:pdcp-nr:nr-rrc:data"
     * -> ["user_dlt","udp","mac-nr","rlc-nr","pdcp-nr","nr-rrc","data"]
     */
    private List<String> parseProtocols(JsonNode layers) {
        String protoStr = JsonUtils.text(
                JsonUtils.path(layers, "frame", "frame.protocols"),
                ""
        );
        if (protoStr.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(protoStr.split(":"));
    }

    /**
     * 鏄惁鏄?Uu 绌哄彛涓婄殑 NR MAC 鍖咃紙鍗忚閾鹃噷鍚?mac-nr锛?
     */
    private boolean isUuMacNr(List<String> protos) {
        return protos.contains("mac-nr");
    }

    /**
     * 鍒ゆ柇锛氬湪 mac-nr 鍚庨潰鏄惁鍙湁 rlc-nr锛堝彲浠ユ湁澶氫釜 rlc-nr锛夛紝
     * 娌℃湁 pdcp-nr / nr-rrc / nas-5gs 绛夋洿楂樺眰銆?
     * 杩欑鍖呮殏鏃惰涓烘病涓氬姟浠峰€硷紝鐩存帴涓㈡帀銆?
     */
    private boolean onlyMacAndRlcAfterMac(List<String> protos) {
        int idx = protos.indexOf("mac-nr");
        if (idx < 0) return false;

        boolean hasHigherLayer = false;
        for (int i = idx + 1; i < protos.size(); i++) {
            String p = protos.get(i);
            // rlc-nr 鍙兘浠?rlc-nr, rlc-nr-UL, rlc-nr-DL 绛夊舰寮忓嚭鐜帮紝缁熶竴褰撲綔 rlc
            if (p.startsWith("rlc-nr")) {
                continue;
            }
            // 鍙鍑虹幇闈?rlc-nr锛屽氨璇存槑鏈夋洿楂樺眰
            hasHigherLayer = true;
            break;
        }
        return !hasHigherLayer;
    }

    /**
     * iface 绮楃暐鍒ゆ柇锛氭湁 mac-nr -> Uu
     */
    private String resolveInterface(List<String> protos) {
        if (protos.contains("mac-nr")) {
            return "Uu";
        }
        // 浠ュ悗浣犲彲浠ュ湪杩欓噷鍔?ngap -> N2 绛?
        return "UNKNOWN";
    }

    /**
     * 鍒ゆ柇杩欎竴鏉″寘鐨勫崗璁摼閲屾湁娌℃湁鈥滃浣犳湁浠峰€尖€濈殑鍗忚锛?
     *  - ngap
     *  - http2:json锛堢敤鍘熷 frame.protocols 涓查噷鎵?"http2:json"锛?
     *  - mac-nr / nr-rrc / nas-5gs / pdcp-nr
     *
     * 濡傛灉涓€涓兘娌℃湁锛屽氨璁や负鏄€滄棤涓氬姟浠峰€尖€濈殑鍖咃紝鐩存帴涓㈠純銆?
     */
    private boolean containsUsefulProtocol(List<String> protos, String protoStr) {
        if (protos == null || protos.isEmpty()) {
            return false;
        }

        boolean hasNgap = protos.contains("ngap");

        // http2:json 鍦?frame.protocols 閲屼竴鑸被浼?"tcp:http2:http2.headers:json"
        // 杩欓噷鎸変綘鐨勮姹傦紝鐩存帴鐢ㄥ師濮嬩覆閲屽寘鍚?"http2:json" 鏉ュ垽鏂?
        boolean hasHttp2Json = protoStr != null && protoStr.contains("http2:json");

        boolean has5gRelevant =
                protos.contains("mac-nr") ||
                        protos.contains("nr-rrc") ||
                        protos.contains("nas-5gs") ||
                        protos.contains("pdcp-nr");

        return hasNgap || hasHttp2Json || has5gRelevant;
    }


    /**
     * 瑙ｆ瀽 frame.number
     */
    private long parseFrameNo(JsonNode layers) {
        String numStr = JsonUtils.text(
                JsonUtils.path(layers, "frame", "frame.number"),
                "0"
        );
        try {
            return Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 瑙ｆ瀽 frame.time_epoch锛堢锛夛紝杞垚姣鏃堕棿鎴?
     */
    private long parseTimestamp(JsonNode layers) {
        String epochStr = JsonUtils.text(
                JsonUtils.path(layers, "frame", "frame.time_epoch"),
                null
        );
        if (epochStr == null) {
            return 0L;
        }
        try {
            double seconds = Double.parseDouble(epochStr);
            return (long) (seconds * 1000L);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }



    @Override
    public List<SignalingMessage> parseAndMergeNoPin(
            String logicJsonPath1, String logicJsonPath2,
            String rawJsonPath1,   String rawJsonPath2) throws IOException {

        List<SignalingMessage> all = new ArrayList<>();

        // 1) 鍒嗗埆瑙ｆ瀽涓や唤锛坙ogic + raw锛?
        all.addAll(parseFileWithRaw(logicJsonPath1, rawJsonPath1));
        all.addAll(parseFileWithRaw(logicJsonPath2, rawJsonPath2));

        // 2) 鍚堝苟鍚庢寜 timestamp + frameNo 鎺掑簭锛屽緱鍒板叏灞€鏃跺簭锛堜笉缃《锛?
        all.sort(Comparator
                .comparingLong(SignalingMessage::getTimestamp)
                .thenComparingLong(SignalingMessage::getFrameNo));

        // 3) 涓嶇疆椤讹紝浣嗕粛鐒垛€滆瘑鍒?瑙勮寖鍖栤€濆叧閿秷鎭殑灞曠ず绫诲瀷锛堝彲閫夛細鍙敼 msgType 鎴栬€呭姞涓瓧娈碉級
        for (SignalingMessage m : all) {
            normalizeKeyMessageTypeInPlace(m);
        }

        // 4) 鍏ㄩ噺琛?MSG 搴忓彿锛堜笉鍐嶄粠 NAUSF 鎺?ueId锛?
        int seq = 1;
        for (SignalingMessage m : all) {
            m.setMsgId("MSG-" + seq);
            seq++;
        }

        return all;
    }

    private void normalizeKeyMessageTypeInPlace(SignalingMessage m) {
        if (m == null) return;

        // 鎸変綘鍘熸潵閭?6 绫诲仛鈥滆瘑鍒?+ 瑙勮寖鍖栧懡鍚嶁€?
        if (isRrcSetupComplete(m)) {
            m.setMsgType("RRCSetupComplete");
            return;
        }

        if (isNgapInitialUeMessage(m)) {
            m.setMsgType("Initial UE Message");
            return;
        }

        if (isNausfAuthResponse(m)) {
            m.setMsgType("Nausf_UEAuthentication_Authenticate Response");
            return;
        }

        if (isNasSecurityModeCommand(m)) {
            m.setMsgType("NAS SecurityModeCommand");
            return;
        }

        if (isNgapInitialContextSetupRequest(m)) {
            m.setMsgType("Initial Context Setup Request");
            return;
        }

        // 鉁?浣犵壒鍒彁鍒扮殑浠嶉渶澶勭悊锛歊RC SecurityModeCommand
        if (isRrcSecurityModeCommand(m)) {
            m.setMsgType("RRC SecurityModeCommand");
        }
    }


}
