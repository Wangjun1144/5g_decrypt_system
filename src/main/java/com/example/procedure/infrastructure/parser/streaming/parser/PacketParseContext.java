package com.example.procedure.infrastructure.parser.streaming.parser;

import com.example.procedure.model.MsgCode;
import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NUARInfo;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.model.message.info.RrcInfo;
import com.example.procedure.infrastructure.parser.streaming.index.ChainIndex;
import com.example.procedure.infrastructure.parser.streaming.index.MsgType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Mutable parsing context used while scanning one packet's tshark JSON tree.
 *
 * It carries the partially assembled {@link StreamingChainParseResult}, current layer
 * depth, protocol-specific state, and raw-field matching state.
 */
public final class PacketParseContext {

    private int infoSequenceCounter = 0;

    public int nextSequence() {
        return infoSequenceCounter++;
    }

    public final StreamingChainParseResult result;
    public final long packetIndex;

    // DFS-style protocol-scope tracking for the current packet scan.
    public int depth = 0;

    // path锛堢敤浜?putFieldPath锛?
    public final Deque<String> path = new ArrayDeque<>();

    // 闃舵娣卞害锛氭ā鎷?DFS 鐨?inMac/inPdcp/inRrc/inNgap
    public int macDepth = 0;
    public int pdcpDepth = 0;
    public int rrcDepth = 0;
    public int ngapDepth = 0;

    // 鍚岀被宓屽锛歂AS
    public final Deque<NasInfo> nasStack = new ArrayDeque<>();
    public final Deque<NasState> nasStateStack = new ArrayDeque<>();

    // 鈥滅粨鏋勬帹鏂€濇繁搴︽爣璁?
    public int rrcC1TreeDepth = -1;       // 鍦?nr-rrc.c1_tree 鍐?
    public int ngapValueElemDepth = -1;   // 鍦?ngap.value_element 鍐?

    // raw 寤惰繜琛ラ綈锛歜aseLayerName -> rawHex锛堜緥濡?"nas-5gs" -> "..."锛?
    private final Map<String, String> rawHexByBase = new HashMap<>();

    // 鉁?raw 寮€鍏筹細鍙鐞?enabled 鐨?raw layer锛堜緥濡?nas-5gs_raw锛?
    private final Set<String> enabledRawLayers;

    // http2 json.object 闇€瑕佸眬閮?JSON parse
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // Not a NAS 5GS PD X (Unknown)
    public static final Pattern NAS_UNKNOWN_PD = Pattern.compile("^Not a NAS 5GS PD .* \\(Unknown\\)$");

    public final ChainIndex index;

    public PacketParseContext(StreamingChainParseResult result, long packetIndex, Set<String> enabledRawLayers) {
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
    // These helpers expose the currently active protocol scopes while scanning.
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
    // All parsed protocol-layer objects are appended to list-backed chain state.
    public MacInfo newMac() {
        MacInfo m = new MacInfo();
        m.setSequence(nextSequence());
        result.getMacList().add(m);
        return m;
    }

    public PdcpInfo newPdcp() {
        PdcpInfo p = new PdcpInfo();
        p.setSequence(nextSequence());
        result.getPdcpList().add(p);
        return p;
    }

    public RrcInfo newRrc() {
        RrcInfo r = new RrcInfo();
        r.setSequence(nextSequence());
        result.getRrcList().add(r);
        return r;
    }

    public NasInfo pushNewNas() {
        NasInfo n = new NasInfo();
        n.setSequence(nextSequence());
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
        g.setSequence(nextSequence());
        result.getNgapList().add(g);
        return g;
    }

    public NUARInfo ensureNuarInfo() {
        if (result.getNuarInfo() == null) {
            NUARInfo n = new NUARInfo();
            n.setSequence(nextSequence());
            result.setNuarInfo(n);
            markIface("N12");

            // 鉁?绗竴娆″嚭鐜?NUAR 灏卞缓鑺傜偣
            if (index != null) {
                index.onEnter(MsgType.NUAR, depth, pathString(), 0, n.getSequence());
                index.onExit(); // NUAR 娌℃湁瀛愭爲瀹瑰櫒姒傚康锛岀珛鍒诲叧鎺変篃琛?
            }
        }
        return result.getNuarInfo();
    }

    // ---------------------------
    // raw latch (STRICT adjacency implemented in parser)
    // ---------------------------
    // Raw values are attached only when strict sibling matching succeeds.
    public void putRawHex(String baseLayerName, String rawHex) {
        if (baseLayerName == null || rawHex == null) return;
        rawHexByBase.put(baseLayerName, rawHex);

        // 鉁?涓嶅啀鈥滃箍鎾紡鈥濆～鍏呮墍鏈?NAS锛屽彧鍦ㄥ綋鍓?NAS 瀛愭爲涓墠琛ュ綋鍓?nas
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

    // 杩欓噷涓轰簡 raw 寤惰繜琛ラ綈杩樿兘 finalize锛屼綘鍘熷厛鐨勫揩鐓ф満鍒舵垜淇濈暀锛堜笉褰卞搷 strict adjacency锛?
    private final List<NasState> finishedNasStates = new ArrayList<>(2);

    public void onNasExit() {
        NasState st = currentNasState();
        if (st != null) finishedNasStates.add(st.snapshot());
    }

    // ---------------------------
    // NAS streaming state
    // ---------------------------
    // NAS keeps additional transient state so encrypted payload completion can
    // happen after raw-field attachment.
    public static final class NasState {
        public final NasInfo nas;

        // 璁板綍鍘熷鐮佹祦锛堝彧瀛?raw hex 鐨勭涓€涓瓧绗︿覆锛屽 "94bbdaf0"銆?08"銆?7e"锛?
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
        // 鍏煎鍏滃簳锛氬鏋滀箣鍓嶆槸 PDCP锛屽啀鏉?NAS锛屽氨鍙樻垚 NAS+PDCP
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
