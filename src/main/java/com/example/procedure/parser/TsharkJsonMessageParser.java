package com.example.procedure.parser;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.util.JsonUtils;
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
 * 负责：读取 tshark 生成的 JSON 文件，
 * 把每个包解析成一条 SignalingMessage。
 * 当前版本规则：
 * 1. 解析 frame.time_epoch / frame.number
 * 2. 利用 frame.protocols 丢掉“mac-nr 后面只有 rlc-nr”的包
 * 3. 对每条 packet 调用 RrcNasPacketParser，一遍解析出 RRC + NAS 信息，
 *    用 RRC 信息填充 SignalingMessage 的基础字段。
 */
public class TsharkJsonMessageParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对外入口：传入 JSON 文件路径（仅 logic json），返回所有信令记录
     */
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
            SignalingMessage msg = buildMessage(pkt); // 只用 logic
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * ⭐ 新增：传入 logic.json 和 json_raw 两个文件，
     * 每个下标 i 的 packet 视为同一帧，同时解析。
     */
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

            SignalingMessage msg = buildMessage(logicPkt, rawPkt); // logic + raw 同时用
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * 传入两个 JSON 文件路径，分别解析后合并，
     * 再根据 timestamp + frameNo 排序，返回一个“全局时序”的列表。
     * （这个版本只用 logic json，如果要用 raw，可以再写一个 withRaw 版本）
     */
    public List<SignalingMessage> parseAndMerge(String logicJsonPath1, String logicJsonPath2,
                                                String rawJsonPath1, String rawJsonPath2) throws IOException {
        List<SignalingMessage> all = new ArrayList<>();

        List<SignalingMessage> list1 = parseFileWithRaw(logicJsonPath1, rawJsonPath1);
        List<SignalingMessage> list2 = parseFileWithRaw(logicJsonPath2, rawJsonPath2);

        all.addAll(list1);
        all.addAll(list2);

        // 1) 先按时间 + 帧号排好“原始时序”
        all.sort(Comparator
                .comparingLong(SignalingMessage::getTimestamp)
                .thenComparingLong(SignalingMessage::getFrameNo));

        // 2) 做“6 大关键消息”的优先排序
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

        // 其余消息按原始顺序补在后面
        for (SignalingMessage m : all) {
            if (!picked.contains(m)) {
                result.add(m);
            }
        }

        // 3) 根据 NAUSF 的 IMSI 计算一个“全局 ueId”
        String ueId = resolveGlobalUeIdFromNausf(result);

        // 4) 给每条消息分配自增 messageId + ueId
        int seq = 1;
        for (SignalingMessage m : result) {
            // 自增 messageId，比如 MSG-1, MSG-2 ...
            m.setMsgId("MSG-" + seq);

            // 如果有全局 ueId，就给所有消息补上（现在默认一个抓包只有一个 UE）
            if (ueId != null && (m.getUeId() == null || m.getUeId().isEmpty())) {
                m.setUeId(ueId);
            }

            seq++;
        }

        return result;
    }
    /**
     * 从已经排好序的消息列表中，找一条 NAUSF 消息，
     * 用它的 IMSI 作为全局的 ueId（当前假设一个抓包里只有一个 UE）。
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
        // 兜底用顶层 msgType 判断
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






    // ================== 核心构建 ==================

    /**
     * 兼容旧代码：只有 logic json 的时候
     */
    private SignalingMessage buildMessage(JsonNode packetNode) {
        return buildMessage(packetNode, null);
    }

    /**
     * 核心：把一条 tshark JSON 里的 packet 转成 SignalingMessage
     * @param logicPacketNode  -T json 输出中的该 packet
     * @param rawPacketNode    -T jsonraw 输出中的对应 packet（可以为 null）
     */
    private SignalingMessage buildMessage(JsonNode logicPacketNode, JsonNode rawPacketNode) {
        JsonNode layers = logicPacketNode.path("_source").path("layers");
        // 原始协议串（用来判断是否包含 "http2:json" 这种组合）
        String protoStr = JsonUtils.text(
                JsonUtils.path(layers, "frame", "frame.protocols"),
                ""
        );

        // 1) 解析 frame.protocols，拿到协议链（只看 logic）
        List<String> protos = parseProtocols(layers);

        // ⭐ 新增：如果协议链中不包含 ngap、http2:json、mac-nr、nr-rrc、nas-5gs、pdcp-nr，则直接丢弃
        if (!containsUsefulProtocol(protos, protoStr)) {
            return null;
        }

        // 2) 如果是 Uu 的 mac-nr 包，且 mac-nr 后只有 rlc-nr（没有 pdcp-nr / nr-rrc / nas-5gs），直接丢掉
        if (isUuMacNr(protos) && onlyMacAndRlcAfterMac(protos)) {
            return null;
        }

        // 3) 基本字段：时间戳 & 帧号
        long frameNo   = parseFrameNo(layers);
        long timestamp = parseTimestamp(layers);

        // 4) iface 粗判：有 mac-nr 就当 Uu；否则 UNKNOWN（后面再扩展 N2 等）
        String iface = resolveInterface(protos);

        // 5) 默认值
        String protocolLayer = "UNKNOWN";
        String direction     = "UNKNOWN";
        String msgType       = "UNKNOWN";

        // 6) 一遍解析：针对这一条 packet，解析出 RRC + NAS 信息
        RrcNasParseResult parsed =  RrcNasPacketParser.parse(logicPacketNode, rawPacketNode);

        RrcInfo rrcInfo = parsed.getRrcInfo();
        if (rrcInfo != null && rrcInfo.getMsgName() != null) {
            protocolLayer = "RRC";
            direction     = rrcInfo.getDirection();  // UL / DL
            msgType       = rrcInfo.getMsgName();    // rrcSetupRequest / rrcSetup / rrcSetupComplete ...
        } else if (!parsed.getNasList().isEmpty()) {
            // 没有 RRC 但有 nas-5gs，可以先粗略标记成 NAS
            protocolLayer = "NAS";
            direction     = "UNKNOWN";              // 以后你可以根据 NGAP/NAS 方向再细化
            msgType       = "NAS_5GS";              // 先统一叫 NAS_5GS，后面按需要细分
        }

        // 7) 组装 SignalingMessage
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

        // ⭐ 把这一条消息里承载的 NAS 列表挂上去
        List<NasInfo> nasList = parsed.getNasList();
        msg.setNasList(nasList == null ? Collections.emptyList() : nasList);

        // ⭐ 新增：把其它各层 Info 也挂到 SignalingMessage 上，方便测试/展示
        msg.setMacInfo(parsed.getMacInfo());
        msg.setPdcpInfo(parsed.getPdcpInfo());
        msg.setRrcInfo(parsed.getRrcInfo());
        List<NgapInfo> ngapInfoList = parsed.getNgapList();
        msg.setNgapInfoList(nasList == null ? Collections.emptyList() : ngapInfoList);

        msg.setNuarInfo(parsed.getNuarInfo());

        return msg;
    }

    // ====================== 帮助方法 ======================

    /**
     * 从 frame.protocols 解析出协议链
     * 例如 "user_dlt:udp:mac-nr:rlc-nr:pdcp-nr:nr-rrc:data"
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
     * 是否是 Uu 空口上的 NR MAC 包（协议链里含 mac-nr）
     */
    private boolean isUuMacNr(List<String> protos) {
        return protos.contains("mac-nr");
    }

    /**
     * 判断：在 mac-nr 后面是否只有 rlc-nr（可以有多个 rlc-nr），
     * 没有 pdcp-nr / nr-rrc / nas-5gs 等更高层。
     * 这种包暂时认为没业务价值，直接丢掉。
     */
    private boolean onlyMacAndRlcAfterMac(List<String> protos) {
        int idx = protos.indexOf("mac-nr");
        if (idx < 0) return false;

        boolean hasHigherLayer = false;
        for (int i = idx + 1; i < protos.size(); i++) {
            String p = protos.get(i);
            // rlc-nr 可能以 rlc-nr, rlc-nr-UL, rlc-nr-DL 等形式出现，统一当作 rlc
            if (p.startsWith("rlc-nr")) {
                continue;
            }
            // 只要出现非 rlc-nr，就说明有更高层
            hasHigherLayer = true;
            break;
        }
        return !hasHigherLayer;
    }

    /**
     * iface 粗略判断：有 mac-nr -> Uu
     */
    private String resolveInterface(List<String> protos) {
        if (protos.contains("mac-nr")) {
            return "Uu";
        }
        // 以后你可以在这里加 ngap -> N2 等
        return "UNKNOWN";
    }

    /**
     * 判断这一条包的协议链里有没有“对你有价值”的协议：
     *  - ngap
     *  - http2:json（用原始 frame.protocols 串里找 "http2:json"）
     *  - mac-nr / nr-rrc / nas-5gs / pdcp-nr
     *
     * 如果一个都没有，就认为是“无业务价值”的包，直接丢弃。
     */
    private boolean containsUsefulProtocol(List<String> protos, String protoStr) {
        if (protos == null || protos.isEmpty()) {
            return false;
        }

        boolean hasNgap = protos.contains("ngap");

        // http2:json 在 frame.protocols 里一般类似 "tcp:http2:http2.headers:json"
        // 这里按你的要求，直接用原始串里包含 "http2:json" 来判断
        boolean hasHttp2Json = protoStr != null && protoStr.contains("http2:json");

        boolean has5gRelevant =
                protos.contains("mac-nr") ||
                        protos.contains("nr-rrc") ||
                        protos.contains("nas-5gs") ||
                        protos.contains("pdcp-nr");

        return hasNgap || hasHttp2Json || has5gRelevant;
    }


    /**
     * 解析 frame.number
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
     * 解析 frame.time_epoch（秒），转成毫秒时间戳
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



    public List<SignalingMessage> parseAndMergeNoPin(
            String logicJsonPath1, String logicJsonPath2,
            String rawJsonPath1,   String rawJsonPath2) throws IOException {

        List<SignalingMessage> all = new ArrayList<>();

        // 1) 分别解析两份（logic + raw）
        all.addAll(parseFileWithRaw(logicJsonPath1, rawJsonPath1));
        all.addAll(parseFileWithRaw(logicJsonPath2, rawJsonPath2));

        // 2) 合并后按 timestamp + frameNo 排序，得到全局时序（不置顶）
        all.sort(Comparator
                .comparingLong(SignalingMessage::getTimestamp)
                .thenComparingLong(SignalingMessage::getFrameNo));

        // 3) 不置顶，但仍然“识别/规范化”关键消息的展示类型（可选：只改 msgType 或者加个字段）
        for (SignalingMessage m : all) {
            normalizeKeyMessageTypeInPlace(m);
        }

        // 4) 全量补 MSG 序号（不再从 NAUSF 推 ueId）
        int seq = 1;
        for (SignalingMessage m : all) {
            m.setMsgId("MSG-" + seq);
            seq++;
        }

        return all;
    }

    private void normalizeKeyMessageTypeInPlace(SignalingMessage m) {
        if (m == null) return;

        // 按你原来那 6 类做“识别 + 规范化命名”
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

        // ✅ 你特别提到的仍需处理：RRC SecurityModeCommand
        if (isRrcSecurityModeCommand(m)) {
            m.setMsgType("RRC SecurityModeCommand");
        }
    }


}
