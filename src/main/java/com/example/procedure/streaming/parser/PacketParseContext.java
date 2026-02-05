package com.example.procedure.streaming.parser;

import com.example.procedure.model.MsgCode;
import com.example.procedure.parser.*;
import com.example.procedure.streaming.index.ChainIndex;
import com.example.procedure.streaming.index.MsgType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public final class PacketParseContext {

    public final RrcNasParseResult result;
    public final long packetIndex;

    public int depth = 0;

    // path（用于 putFieldPath）
    public final Deque<String> path = new ArrayDeque<>();

    // 阶段深度：模拟 DFS 的 inMac/inPdcp/inRrc/inNgap
    public int macDepth = 0;
    public int pdcpDepth = 0;
    public int rrcDepth = 0;
    public int ngapDepth = 0;

    // 同类嵌套：NAS
    public final Deque<NasInfo> nasStack = new ArrayDeque<>();
    public final Deque<NasState> nasStateStack = new ArrayDeque<>();

    // “结构推断”深度标记
    public int rrcC1TreeDepth = -1;       // 在 nr-rrc.c1_tree 内
    public int ngapValueElemDepth = -1;   // 在 ngap.value_element 内

    // raw 延迟补齐：baseLayerName -> rawHex（例如 "nas-5gs" -> "..."）
    private final Map<String, String> rawHexByBase = new HashMap<>();

    // ✅ raw 开关：只处理 enabled 的 raw layer（例如 nas-5gs_raw）
    private final Set<String> enabledRawLayers;

    // http2 json.object 需要局部 JSON parse
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // Not a NAS 5GS PD X (Unknown)
    public static final Pattern NAS_UNKNOWN_PD = Pattern.compile("^Not a NAS 5GS PD .* \\(Unknown\\)$");

    public final ChainIndex index;

    public PacketParseContext(RrcNasParseResult result, long packetIndex, Set<String> enabledRawLayers) {
        this.result = result;
        this.packetIndex = packetIndex;
        this.enabledRawLayers = enabledRawLayers == null ? Set.of() : Set.copyOf(enabledRawLayers);
        this.index = result.getIndex();
    }

    public boolean isRawEnabled(String rawLayerName) {
        return enabledRawLayers.contains(rawLayerName);
    }

    // ---------------------------
    // phase flags
    // ---------------------------
    public boolean inMac()  { return macDepth > 0; }
    public boolean inPdcp() { return pdcpDepth > 0; }
    public boolean inRrc()  { return rrcDepth > 0; }
    public boolean inNgap() { return ngapDepth > 0; }
    public boolean inNas()  { return !nasStack.isEmpty(); }

    public MacInfo currentMac()  { return lastOrNull(result.getMacList()); }
    public PdcpInfo currentPdcp(){ return lastOrNull(result.getPdcpList()); }
    public RrcInfo currentRrc()  { return lastOrNull(result.getRrcList()); }
    public NasInfo currentNas()  { return nasStack.peek(); }
    public NasState currentNasState() { return nasStateStack.peek(); }
    public NgapInfo currentNgap(){ return lastOrNull(result.getNgapList()); }

    public String pathString() {
        return String.join("/", path);
    }

    // ---------------------------
    // create objects (all list)
    // ---------------------------
    public MacInfo newMac() {
        MacInfo m = new MacInfo();
        result.getMacList().add(m);
        return m;
    }

    public PdcpInfo newPdcp() {
        PdcpInfo p = new PdcpInfo();
        result.getPdcpList().add(p);
        return p;
    }

    public RrcInfo newRrc() {
        RrcInfo r = new RrcInfo();
        result.getRrcList().add(r);
        return r;
    }

    public NasInfo pushNewNas() {
        NasInfo n = new NasInfo();
        result.getNasList().add(n);
        nasStack.push(n);
        nasStateStack.push(new NasState(n));
        return n;
    }

    public void popNas() {
        nasStack.pop();
        nasStateStack.pop();
    }

    public NgapInfo newNgap() {
        NgapInfo g = new NgapInfo();
        result.getNgapList().add(g);
        return g;
    }

    public NUARInfo ensureNuarInfo() {
        if (result.getNuarInfo() == null) {
            result.setNuarInfo(new NUARInfo());
            markIface("N12");

            // ✅ 第一次出现 NUAR 就建节点
            if (index != null) {
                index.onEnter(MsgType.NUAR, depth, pathString(), 0);
                index.onExit(); // NUAR 没有子树容器概念，立刻关掉也行
            }
        }
        return result.getNuarInfo();
    }

    // ---------------------------
    // raw latch (STRICT adjacency implemented in parser)
    // ---------------------------
    public void putRawHex(String baseLayerName, String rawHex) {
        if (baseLayerName == null || rawHex == null) return;
        rawHexByBase.put(baseLayerName, rawHex);

        // ✅ 不再“广播式”填充所有 NAS，只在当前 NAS 子树中才补当前 nas
        if ("nas-5gs".equals(baseLayerName)) {
            NasInfo nas = currentNas();
            if (nas != null && (nas.getFullNasPduHex() == null ||
                    nas.getFullNasPduHex().isEmpty())) {
                nas.setFullNasPduHex(rawHex);
            }
            NasState st = currentNasState();
            if (st != null) st.tryFinalizeWithRaw();
        }
    }

    public String getRawHex(String baseLayerName) {
        return rawHexByBase.get(baseLayerName);
    }

    // 这里为了 raw 延迟补齐还能 finalize，你原先的快照机制我保留（不影响 strict adjacency）
    private final List<NasState> finishedNasStates = new ArrayList<>(2);

    public void onNasExit() {
        NasState st = currentNasState();
        if (st != null) finishedNasStates.add(st.snapshot());
    }

    // ---------------------------
    // NAS streaming state
    // ---------------------------
    public static final class NasState {
        public final NasInfo nas;

        // 记录原始码流（只存 raw hex 的第一个字符串，如 "94bbdaf0"、"08"、"7e"）
        public final Map<String, String> rawFieldHex = new HashMap<>();


        public int secDepth = -1;
        public boolean secHasAnyField = false;

        public int plainDepth = -1;
        public boolean hasPlainUnknown = false;

        public String epd;
        public String spare;
        public String sht;
        public String mac;
        public String seq;

        public boolean exited = false;

        public NasState(NasInfo nas) {
            this.nas = nas;
        }

        public NasState snapshot() {
            NasState s = new NasState(nas);
            s.secDepth = this.secDepth;
            s.secHasAnyField = this.secHasAnyField;
            s.plainDepth = this.plainDepth;
            s.hasPlainUnknown = this.hasPlainUnknown;
            s.epd = this.epd;
            s.spare = this.spare;
            s.sht = this.sht;
            s.mac = this.mac;
            s.seq = this.seq;
            s.exited = true;
            return s;
        }

        public void tryFinalizeWithRaw() {
            if (nas.getCipherTextHex() != null && !nas.getCipherTextHex().isEmpty()) return;
            if (!secHasAnyField) return;
            if (!("4".equals(sht) || "2".equals(sht))) return;

            nas.setEncrypted(true);

            String fullHex = nas.getFullNasPduHex();
            if (fullHex == null || fullHex.isEmpty()) return;

            if (!hasPlainUnknown) return;

            int headerBytes = 7;
            int headerHexLen = headerBytes * 2;
            if (fullHex.length() > headerHexLen) {
                nas.setCipherTextHex(fullHex.substring(headerHexLen));
            }
        }
    }

    // ---------------------------
    // http2 json.object helper
    // ---------------------------
    public void handleHttp2JsonObjectScalar(String jsonText) {
        if (jsonText == null || jsonText.isEmpty()) return;

        JsonNode obj;
        try {
            obj = JSON_MAPPER.readTree(jsonText);
        } catch (IOException e) {
            return;
        }
        if (obj == null || !obj.isObject()) return;

        JsonNode kseafNode = obj.get("kseaf");
        JsonNode supiNode = obj.get("supi");
        if (kseafNode == null || supiNode == null) return;

        String kseaf = kseafNode.asText(null);
        String supi = supiNode.asText(null);
        if (kseaf == null || supi == null) return;

        String authResult = obj.path("authResult").asText(null);

        NUARInfo nuar = ensureNuarInfo();
        nuar.setMsgName("Nausf_UEAuthentication_AuthenticateResponse");
        markMsgCode(MsgCode.NUAR_AUTHENTICATE_RESPONSE.code);
        nuar.setKseafHex(kseaf);
        nuar.setSupi(supi);

        if (supi.startsWith("imsi-")) nuar.setImsi(supi.substring("imsi-".length()));
        else nuar.setImsi(supi);
        String imsi = nuar.getImsi();
        if (imsi != null && !imsi.isBlank()) {
            result.setUeId(imsi.trim());
            markIface("N12");
        }


        if (authResult != null) nuar.setAuthResult(authResult);

        nuar.putFieldPath("json.object", pathString());
    }

    private static <T> T lastOrNull(List<T> list) {
        int n = list.size();
        return n == 0 ? null : list.get(n - 1);
    }

    public void markIface(String iface) {
        if (iface == null || iface.isEmpty()) return;
        if (result.getIface() == null || result.getIface().isEmpty() ||
                "UNKNOWN".equals(result.getIface())) {
            result.setIface(iface);
        }
    }

    public void markDirection(String dir) {
        if (dir == null || dir.isEmpty()) return;
        String cur = result.getDirection();
        if (cur == null || cur.isEmpty() || "UNKNOWN".equals(cur)) {
            result.setDirection(dir);
        }
    }

    public void markNasEncrypted() {
        // 兼容兜底：如果之前是 PDCP，再来 NAS，就变成 NAS+PDCP
        String cur = result.getEncryptedType();
        if ("PDCP".equals(cur)) {
            result.setEncryptedType("NAS+PDCP");
            result.setEncrypted(true);
            return;
        }
        result.setEncryptedType("NAS");
        result.setEncrypted(true);
    }

    public void markPdcpEncrypted() {
        String cur = result.getEncryptedType();
        if ("NAS".equals(cur)) {
            result.setEncryptedType("NAS+PDCP");
            result.setEncrypted(true);
            return;
        }
        result.setEncryptedType("PDCP");
        result.setEncrypted(true);
    }

    public void markMsgCode(int code) {
        if (code <= 0) return;
        if (result.getMsgCode() == 0) {
            result.setMsgCode(code);
        }
    }



}
