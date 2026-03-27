package com.example.procedure.infrastructure.parser.streaming.layers;

import com.example.procedure.model.MsgCode;
import com.example.procedure.model.message.info.MacInfo;
import com.example.procedure.model.message.info.NUARInfo;
import com.example.procedure.model.message.info.NasInfo;
import com.example.procedure.model.message.info.NgapInfo;
import com.example.procedure.model.message.info.PdcpInfo;
import com.example.procedure.model.message.info.RrcInfo;
import com.example.procedure.infrastructure.parser.streaming.index.ChainIndex;
import com.example.procedure.infrastructure.parser.streaming.index.MsgType;
import com.example.procedure.infrastructure.parser.streaming.parser.PacketParseContext;
import com.example.procedure.infrastructure.parser.streaming.parser.StreamingChainParseResult;
import com.example.procedure.support.json.JsonStreamUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;

/**
 * Selectively parses tshark JSON packets into normalized chain results.
 *
 * The parser keeps only protocol layers relevant to the current 5G signaling
 * pipeline and preserves strict raw-to-logic sibling matching for raw fields
 * such as {@code nas-5gs_raw}.
 */
public final class LayersSelectiveParser {

    private static final JsonFactory FACTORY = new JsonFactory();
    private static final PacketLayerFilter PACKET_LAYER_FILTER = new PacketLayerFilter();
    private static final FrameLayerParser FRAME_LAYER_PARSER = new FrameLayerParser();
    private static final StreamingChainFactory STREAMING_CHAIN_FACTORY = new StreamingChainFactory();

    private LayersSelectiveParser() {}


    /**
     * 杈撳嚭锛氫竴涓?packet 鍙兘瀵瑰簲澶氭潯閾撅紙姣忎釜 wanted 涓旈潪 _raw 鐨?layer 涓€鏉￠摼锛?
     *
     * @param enabledRawLayers 鍙姄鍙栧惎鐢ㄧ殑 raw layer锛堜緥濡?Set.of("nas-5gs_raw")锛夈€?
     *                        鏈惎鐢ㄧ殑 *_raw 浼氳褰撲綔鏅€氬瓧娈佃烦杩囷紝涓嶅弬涓庝弗鏍奸厤瀵广€?
     */
    /**
     * Parse tshark packet JSON and emit chain results packet by packet.
     *
     * One packet may produce multiple chains when multiple wanted logical
     * layers appear under the same frame.
     *
     * @param enabledRawLayers raw layers that should participate in strict
     *                         sibling matching, for example {@code nas-5gs_raw}
     */
    // Formal entry for packet-by-packet streaming parse.
    public static void parsePackets(InputStream in,
                                    Set<String> wantedFields,
                                    Set<String> enabledRawLayers,
                                    Consumer<List<StreamingChainParseResult>> onPacket) throws IOException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(wantedFields, "wantedFields");
        Objects.requireNonNull(enabledRawLayers, "enabledRawLayers");
        Objects.requireNonNull(onPacket, "onPacket");

        try (JsonParser p = FACTORY.createParser(JsonStreamUtil.skipToJsonStart(in))) {
            if (p.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Expected top-level JSON array (tshark -T json)");
            }

            long packetIndex = 0;
            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (p.currentToken() != JsonToken.START_OBJECT) {
                    p.skipChildren();
                    continue;
                }

                List<StreamingChainParseResult> chains = parseOnePacketObject(p, packetIndex, wantedFields, enabledRawLayers);
                onPacket.accept(chains);
                packetIndex++;
            }
        }
    }

    private static List<StreamingChainParseResult> parseOnePacketObject(JsonParser p,
                                                                long packetIndex,
                                                                Set<String> wantedFields,
                                                                Set<String> enabledRawLayers) throws IOException {
        List<StreamingChainParseResult> chains = new ArrayList<>();

        while (p.nextToken() != JsonToken.END_OBJECT) {
            if (p.currentToken() != JsonToken.FIELD_NAME) continue;

            String field = p.currentName();
            JsonToken v = p.nextToken();

            if ("_source".equals(field) && v == JsonToken.START_OBJECT) {
                parseSourceObject(p, packetIndex, wantedFields, enabledRawLayers, chains);
            } else {
                p.skipChildren();
            }
        }
        return chains;
    }

    private static void parseSourceObject(JsonParser p,
                                          long packetIndex,
                                          Set<String> wantedFields,
                                          Set<String> enabledRawLayers,
                                          List<StreamingChainParseResult> chains) throws IOException {
        while (p.nextToken() != JsonToken.END_OBJECT) {
            if (p.currentToken() != JsonToken.FIELD_NAME) continue;

            String field = p.currentName();
            JsonToken v = p.nextToken();

            if ("layers".equals(field) && v == JsonToken.START_OBJECT) {
                parseLayersObject(p, packetIndex, wantedFields, enabledRawLayers, chains);
            } else {
                p.skipChildren();
            }
        }
    }

    /**
     * 瑙勫垯锛歭ayers 涓嬫瘡涓?wanted 涓旈潪 _raw 鐨?layer = 涓€鏉￠摼
     *
     * raw 涓ユ牸閰嶅閫昏緫涓嶅湪杩欓噷鍋氾紙涓嶅啀鐢?lastCtxByLayer锛夛紝鑰屾槸鍦?scanAnyValue 鐨?
     * 鈥滄瘡涓?object 瀛楁閬嶅巻寰幆鈥濅腑鍋氾細*_raw 蹇呴』绱ц窡 xxx 鎵嶆秷璐广€?
     *
     * 浣嗘敞鎰忥細涓轰簡涓嶆紡鎺?enabledRawLayers锛堟瘮濡?nas-5gs_raw锛夛紝杩欓噷蹇呴』鍏佽 enabled raw layer 琚В鏋愶紙涓?skip锛夈€?
     */
    /**
     * 鉁?瀹屾暣鏇挎崲鐗堬細椤跺眰 layers object 涔熼渶瑕?strict pending锛堝洜涓哄畠涓嶈蛋 scanAnyValue锛?
     * 灏嗕笂闈?parseLayersObject 鐨勫唴瀹规暣浣撴浛鎹㈡垚杩欎釜瀹炵幇鍗冲彲銆?
     */
    // Core packet-layer scanner: one wanted logical layer becomes one chain.
    private static void parseLayersObject(JsonParser p,
                                                   long packetIndex,
                                                   Set<String> wantedFields,
                                                   Set<String> enabledRawLayers,
                                                   List<StreamingChainParseResult> chains) throws IOException {

        // ===== per-packet frame meta =====
        FrameLayerMetadata frame = null;
        boolean filtered = false;
        boolean dropPacket = false;

        RawSiblingPending pendingRaw = new RawSiblingPending();

        while (p.nextToken() != JsonToken.END_OBJECT) {
            if (p.currentToken() != JsonToken.FIELD_NAME) continue;

            String layerName = p.currentName();
            JsonToken v = p.nextToken();

            // ---- 0) frame 浼樺厛瑙ｆ瀽锛堜竴鑸湪鏈€鍓嶏級----
            if ("frame".equals(layerName)) {
                frame = FRAME_LAYER_PARSER.parse(p, v);

                if (!filtered) {
                    filtered = true;
                    // 娌℃嬁鍒?protocols锛氳繖閲岄€夋嫨涓嶈繃婊わ紙浣犱篃鍙敼鎴愮洿鎺ヤ涪锛?
                    if (frame != null && frame.getProtocols() != null) {
                        // Frame-level filtering is centralized so this scanner can focus on traversal.
                        dropPacket = PACKET_LAYER_FILTER.shouldDropPacket(frame);
                    }
                }
                continue;
            }

            // ---- 1) 濡傛灉鍒ゅ畾涓㈠寘锛屽悗闈㈠叏閮?skip ----
            if (dropPacket) {
                p.skipChildren();
                pendingRaw.clear();
                continue;
            }


            // 椤跺眰鍓灊锛氶€昏緫 layer 涓?wanted 灏辫烦杩囷紱
            // raw layer 濡傛灉 enabled 蹇呴』淇濈暀锛堝惁鍒?strict 閰嶅鏃犳硶鍙戠敓锛?
            boolean keepForParse = wantedFields.contains(layerName) || enabledRawLayers.contains(layerName);
            if (!keepForParse) {
                p.skipChildren();
                // Dropping a non-participating sibling also clears pending raw
                // so strict adjacency rules remain intact.
                pendingRaw.clear();
                continue;
            }

            // Enabled raw layers arm the pending state and wait for the very
            // next logical sibling with the same base name.
            if (layerName.endsWith("_raw") && enabledRawLayers.contains(layerName)) {
                String base = layerName.substring(0, layerName.length() - "_raw".length());
                String rawHex = extractFirstHexFromRawLayerValue(p, v);
                pendingRaw.arm(base, rawHex);
                continue;
            }

            // Each wanted logical layer creates one chain and may consume one
            // strictly adjacent raw sibling captured above.
            if (!layerName.endsWith("_raw") && wantedFields.contains(layerName)) {
                String consumedRaw = pendingRaw.consumeIfMatches(layerName);

                StreamingChainParseResult chain = STREAMING_CHAIN_FACTORY.create(frame);
                ChainIndex index = chain.getIndex();

                PacketParseContext ctx = new PacketParseContext(chain, packetIndex, enabledRawLayers);
                Deque<EnterMark> enterStack = new ArrayDeque<>();

                index.startPacketRoot("layers/" + layerName, ctx.depth);
                if (consumedRaw != null) {
                    ctx.putRawHex(layerName, consumedRaw);
                }

                scanAnyValue(p, v, layerName, ctx, enterStack);
                index.endPacketRoot();
                chains.add(chain);
                continue;
            }

            // Any other sibling breaks strict adjacency and must clear pending state.
            p.skipChildren();
            pendingRaw.clear();
        }

        // Strict pending raw state is scoped to the current object traversal
        // and is intentionally dropped when this object ends.
    }

    // ===========================
    // streaming scan
    // ===========================

    private enum Kind {
        MAC, PDCP, RRC, NAS, NGAP,
        NGAP_VALUE_ELEM, RRC_C1_TREE,
        NAS_SEC, NAS_PLAIN
    }

    private static final class EnterMark {
        final Kind kind;
        final int beginDepth;
        EnterMark(Kind kind, int beginDepth) {
            this.kind = kind;
            this.beginDepth = beginDepth;
        }
    }

    /**
     * 鉁?杩欓噷瀹炵幇鈥滀换鎰?object 灞傜骇鈥濈殑 strict pending锛?
     * 鍦ㄦ瘡涓?START_OBJECT 鐨勫瓧娈靛惊鐜唴缁存姢 pendingRaw锛堝眬閮ㄥ彉閲忥級锛屽疄鐜?*_raw 绱ч偦 xxx 鎵嶆秷璐广€?
     */
    /**
     * Recursively scan any JSON value while preserving strict raw-to-logic
     * sibling matching inside each object scope.
     */
    private static void scanAnyValue(JsonParser p,
                                     JsonToken current,
                                     String fieldName,
                                     PacketParseContext ctx,
                                     Deque<EnterMark> enterStack) throws IOException {
        if (current == null) return;

        switch (current) {
            case START_OBJECT: {
                ctx.depth++;
                if (fieldName != null) ctx.path.addLast(fieldName);

                EnterMark mark = enterPhase(fieldName, ctx);
                if (mark != null) enterStack.push(mark);

                // 鉁?鏈?object 灞傜骇鐨?strict pending
                RawSiblingPending pendingRaw = new RawSiblingPending();

                while (p.nextToken() != JsonToken.END_OBJECT) {
                    if (p.currentToken() != JsonToken.FIELD_NAME) {
                        p.skipChildren();
                        continue;
                    }

                    String childField = p.currentName();

                    // strict锛氳繘鍏ヤ笅涓€涓?sibling 鏃讹紝鍏堝鐞嗕笂涓€鏉?pending
                    if (pendingRaw.isArmed()) {
                        if (pendingRaw.getLogicField().equals(childField)) {
                            // 1) 澶?raw锛歯as-5gs_raw -> nas-5gs
                            if ("nas-5gs".equals(pendingRaw.getLogicField())) {
                                ctx.putRawHex("nas-5gs", pendingRaw.getRawHex());
                            }
                            // 2) 瀛楁 raw锛歯as-5gs.epd_raw -> nas-5gs.epd 绛?
                            else if (pendingRaw.getLogicField().startsWith("nas-5gs.") && ctx.inNas()) {
                                PacketParseContext.NasState st = ctx.currentNasState();
                                if (st != null) {
                                    // key 鐢ㄩ€昏緫瀛楁鍚嶄繚瀛橈紝姣斿 "nas-5gs.epd"
                                    st.rawFieldHex.put(pendingRaw.getLogicField(), pendingRaw.getRawHex());
                                }
                            }
                            // 3) 鍏朵粬灞傜殑 raw锛堟湭鏉ユ墿灞曪級鍙互鍏堜涪寮冩垨鍙﹁澶勭悊
                        }

                        // strict锛氭棤璁烘秷璐?涓㈠純閮芥竻绌?
                        pendingRaw.clear();
                    }

                    // 鍘熼€昏緫锛氭彁鍓嶇湅鍒?fieldName
                    onFieldNameSeen(childField, ctx);

                    JsonToken v = p.nextToken();

                    // 鉁?1) 鏈惎鐢ㄧ殑 *_raw锛氱洿鎺ヨ烦杩囧瓙鏍戯紝涓嶈幏鍙栦换浣曚笢瑗?
                    if (childField.endsWith("_raw") && !ctx.isRawEnabled(childField)) {
                        p.skipChildren();
                        continue;
                    }

                    // 鉁?鍚敤鐨?raw锛氭娊 hex锛岃缃?pending锛岀瓑寰呬笅涓€涓?sibling
                    if (childField.endsWith("_raw") && ctx.isRawEnabled(childField)) {
                        String base = childField.substring(0, childField.length() - "_raw".length());
                        String hex = extractFirstHexFromRawLayerValue(p, v); // 浼氭秷璐瑰瓙鏍?
                        pendingRaw.arm(base, hex);
                        continue;
                    }

                    scanAnyValue(p, v, childField, ctx, enterStack);
                }

                // strict锛歰bject 缁撴潫 pending 鐩存帴涓㈠純
                pendingRaw.clear();

                exitPhase(ctx, enterStack);

                if (fieldName != null) ctx.path.removeLast();
                ctx.depth--;
                return;
            }

            case START_ARRAY: {
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    scanAnyValue(p, p.currentToken(), null, ctx, enterStack);
                }
                return;
            }

            default:
                onScalar(fieldName, p, ctx);
                return;
        }
    }

    // ------------------------------------------------------------
    // enter/exit phases锛堜繚鎸佷綘鍘熼€昏緫锛?
    // ------------------------------------------------------------

    private static EnterMark enterPhase(String fieldName, PacketParseContext ctx) {
        if (fieldName == null) return null;

        if ("mac-nr".equals(fieldName)) {
            ctx.macDepth++;
            ctx.markIface("Uu");
            MacInfo mac = ctx.newMac();
            int payloadIndex = ctx.result.getMacList().size() - 1;
            int payloadSequence = mac.getSequence();
            ctx.index.onEnter(MsgType.MAC, ctx.depth, ctx.pathString(), payloadIndex, payloadSequence);

            return new EnterMark(Kind.MAC, ctx.depth);
        }

        if ("pdcp-nr".equals(fieldName)) {
            ctx.pdcpDepth++;
            PdcpInfo pdcp = ctx.newPdcp();
            int payloadIndex = ctx.result.getPdcpList().size() - 1;
            int payloadSequence = pdcp.getSequence();
            ctx.index.onEnter(MsgType.PDCP, ctx.depth, ctx.pathString(), payloadIndex, payloadSequence);

            return new EnterMark(Kind.PDCP, ctx.depth);
        }

        if ("nr-rrc".equals(fieldName)) {
            ctx.rrcDepth++;
            RrcInfo rrc = ctx.newRrc();
            int payloadIndex = ctx.result.getRrcList().size() - 1;
            int payloadSequence = rrc.getSequence();
            ctx.index.onEnter(MsgType.RRC, ctx.depth, ctx.pathString(), payloadIndex, payloadSequence);

            return new EnterMark(Kind.RRC, ctx.depth);
        }

        if ("nas-5gs".equals(fieldName)) {
            NasInfo nas = ctx.pushNewNas();
            int payloadIndex = ctx.result.getNasList().size() - 1;
            int payloadSequence = nas.getSequence();
            ctx.index.onEnter(MsgType.NAS, ctx.depth, ctx.pathString(), payloadIndex, payloadSequence);

            // raw 鍏堝埌锛氳繘鍏?nas-5gs 鏃惰ˉ fullNasPduHex
            String raw = ctx.getRawHex("nas-5gs");
            if (raw != null) {
                NasInfo nas1 = ctx.currentNas();
                if (nas1 != null && (nas1.getFullNasPduHex() == null || nas1.getFullNasPduHex().isEmpty())) {
                    nas1.setFullNasPduHex(raw);
                }
            }
            return new EnterMark(Kind.NAS, ctx.depth);
        }

        if (ctx.inNas()) {
            if ("Security protected NAS 5GS message".equals(fieldName)) {
                PacketParseContext.NasState st = ctx.currentNasState();
                if (st != null) {
                    st.secDepth = ctx.depth;
                    st.secHasAnyField = false;
                }
                return new EnterMark(Kind.NAS_SEC, ctx.depth);
            }
            if ("Plain NAS 5GS Message".equals(fieldName)) {
                PacketParseContext.NasState st = ctx.currentNasState();
                if (st != null) {
                    st.plainDepth = ctx.depth;
                }
                return new EnterMark(Kind.NAS_PLAIN, ctx.depth);
            }
        }

        if ("ngap".equals(fieldName)) {
            ctx.ngapDepth++;
            NgapInfo ngap = ctx.newNgap();
            ctx.markIface("N2");
            int payloadIndex = ctx.result.getNgapList().size() - 1;
            int payloadSequence = ngap.getSequence();
            ctx.index.onEnter(MsgType.NGAP, ctx.depth, ctx.pathString(), payloadIndex, payloadSequence);
            return new EnterMark(Kind.NGAP, ctx.depth);
        }

        if (ctx.inNgap()
                && ("ngap.initiatingMessage_element".equals(fieldName)
                || "ngap.successfulOutcome_element".equals(fieldName)
                || "ngap.unsuccessfulOutcome_element".equals(fieldName))) {

            handleNgapObjectEnter(fieldName, ctx);
            return null;
        }

        if (ctx.inNgap() && "ngap.value_element".equals(fieldName)) {
            ctx.ngapValueElemDepth = ctx.depth;
            return new EnterMark(Kind.NGAP_VALUE_ELEM, ctx.depth);
        }

        if (ctx.inRrc()) {
            handleRrcObjectEnter(fieldName, ctx);

            if ("nr-rrc.c1_tree".equals(fieldName)) {
                ctx.rrcC1TreeDepth = ctx.depth;
                return new EnterMark(Kind.RRC_C1_TREE, ctx.depth);
            }
        }

        return null;
    }

    private static void exitPhase(PacketParseContext ctx, Deque<EnterMark> enterStack) {
        if (enterStack.isEmpty()) return;

        EnterMark top = enterStack.peek();
        if (top.beginDepth != ctx.depth) return;

        enterStack.pop();

        switch (top.kind) {
            case NGAP:
                ctx.index.onExit();
                ctx.ngapDepth--;// NGAP 涓嶅悓绫诲祵濂楋細涓嶉渶瑕?pop
                break;
            case NGAP_VALUE_ELEM:
                ctx.ngapValueElemDepth = -1;
                break;

            case NAS_SEC:
            case NAS_PLAIN:
                break;

            case NAS: {
                ctx.index.onExit();
                PacketParseContext.NasState st = ctx.currentNasState();
                if (st != null) {
                    NasInfo nas = st.nas;
                    if (st.epd != null) nas.setEpd(st.epd);
                    if (st.spare != null) nas.setSpareHalfOctet(st.spare);
                    if (st.sht != null) nas.setSecurityHeaderType(st.sht);
                    if (st.mac != null) nas.setMsgAuthCodeHex(normalizeHex(st.mac));
                    if (st.seq != null) nas.setSeqNo(st.seq);

                    st.tryFinalizeWithRaw();

                    if (nas.isEncrypted()) {
                        ctx.markNasEncrypted();
                    }

                    ctx.onNasExit();
                }

                ctx.popNas();
                break;
            }

            case RRC:
                ctx.index.onExit();
                ctx.rrcDepth--;
                ctx.rrcC1TreeDepth = -1;
                break;
            case PDCP:
                ctx.index.onExit();
                ctx.pdcpDepth--;
                break;
            case MAC:
                ctx.index.onExit();
                ctx.macDepth--;
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------
    // onFieldNameSeen / onScalar / handlers锛堜繚鎸佷綘鍘熼€昏緫锛?
    // ------------------------------------------------------------

    private static void onFieldNameSeen(String fieldName, PacketParseContext ctx) {
        if (fieldName == null) return;

        if (ctx.inNgap() && ctx.ngapValueElemDepth != -1 && fieldName.startsWith("ngap.") && fieldName.endsWith("_element")) {
            NgapInfo ngap = ctx.currentNgap();
            if (ngap != null && (ngap.getMsgName() == null || ngap.getMsgName().isEmpty())) {
                String msgName = fieldName.substring("ngap.".length(), fieldName.length()
                        - "_element".length());
                ngap.setMsgName(msgName);
                ngap.putFieldPath("msgType", ctx.pathString() + "/" + fieldName);

                if ("InitialUEMessage".equalsIgnoreCase(msgName)) {
                    ctx.markMsgCode(MsgCode.NGAP_INITIAL_UE_MESSAGE.code);
                } else if ("InitialContextSetupRequest".equalsIgnoreCase(msgName)) {
                    ctx.markMsgCode(MsgCode.NGAP_INITIAL_CONTEXT_SETUP_REQUEST.code);
                }

                String dir = null;
                switch (msgName) {
                    case "InitialUEMessage":
                    case "DownlinkNASTransport":
                    case "InitialContextSetupRequest":
                        dir = "DL"; break;
                    case "UplinkNASTransport":
                    case "InitialContextSetupResponse":
                        dir = "UL"; break;
                    default:
                        break;
                }
                if (dir != null) {
                    ngap.setDirection(dir);
                    ngap.putFieldPath("direction", ctx.pathString() + "/" + fieldName);
                    ctx.markDirection(dir);
                }
            }
        }

        if (ctx.inRrc() && ctx.rrcC1TreeDepth != -1 &&
                fieldName.startsWith("nr-rrc.") &&
                fieldName.endsWith("_element")) {
            RrcInfo rrc = ctx.currentRrc();
            if (rrc != null && (rrc.getMsgName() == null || rrc.getMsgName().isEmpty())) {
                String msgName = fieldName.substring("nr-rrc.".length(), fieldName.length()
                        - "_element".length());
                rrc.setMsgName(msgName);
                // 鉁?鍦ㄨ繖閲岄『鎵嬫墦 msgCode
                if ("rrcSetupComplete".equalsIgnoreCase(msgName)) {
                    ctx.markMsgCode(MsgCode.RRC_SETUP_COMPLETE.code);
                } else if ("securityModeCommand".equalsIgnoreCase(msgName)) {
                    ctx.markMsgCode(MsgCode.RRC_SECURITY_MODE_COMMAND.code);
                }
                rrc.putFieldPath("msgType", ctx.pathString() + "/" + fieldName);
            }
        }

        if (ctx.inNas()) {
            PacketParseContext.NasState st = ctx.currentNasState();
            if (st != null) {
                if (st.secDepth != -1 && ctx.depth >= st.secDepth) {
                    st.secHasAnyField = true;
                }

                if (st.plainDepth != -1 && ctx.depth >= st.plainDepth) {
                    if (PacketParseContext.NAS_UNKNOWN_PD.matcher(fieldName).matches()) {
                        st.hasPlainUnknown = true;
                    }
                }
            }
        }
    }

    private static void onScalar(String fieldName, JsonParser p, PacketParseContext ctx) throws IOException {
        if (fieldName == null) return;

        if ("json.object".equals(fieldName)) {
            String jsonText = p.getValueAsString();
            ctx.handleHttp2JsonObjectScalar(jsonText);
            return;
        }

        if (ctx.inMac())  handleMacNode(fieldName, p, ctx);
        if (ctx.inPdcp()) handlePdcpNode(fieldName, p, ctx);
        if (ctx.inNgap()) handleNgapNode(fieldName, p, ctx);
        if (ctx.inRrc())  handleRrcNode(fieldName, p, ctx);
        if (ctx.inNas())  handleNasNode(fieldName, p, ctx);
    }

    // ---------------- RAW extraction helpers ----------------

    private static String extractFirstHexFromRawLayerValue(JsonParser p, JsonToken v) throws IOException {
        String[] out = new String[1];
        scanRawValue(p, v, out);
        if (out[0] != null) return normalizeHex(out[0]);
        return null;
    }

    private static void scanRawValue(JsonParser p, JsonToken current, String[] out) throws IOException {
        if (current == null) return;

        switch (current) {
            case START_OBJECT:
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    if (p.currentToken() != JsonToken.FIELD_NAME) {
                        p.skipChildren(); continue;
                    }
                    p.nextToken();
                    scanRawValue(p, p.currentToken(), out);
                }
                return;

            case START_ARRAY:
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    scanRawValue(p, p.currentToken(), out);
                }
                return;

            default:
                if (out[0] == null && current == JsonToken.VALUE_STRING) {
                    String s = p.getValueAsString();
                    if (s != null && !s.isEmpty()) out[0] = s;
                }
        }
    }

    // ---------------- handleXNode锛堜繚鎸佷綘鍘熸潵鐨勫疄鐜板嵆鍙紝杩欓噷鍙繚鐣欎綘璐磋繃鐨勭増鏈級 ----------------

    private static void handleMacNode(String fieldName, JsonParser p, PacketParseContext ctx) throws IOException {
        MacInfo mac = ctx.currentMac();
        if (mac == null) return;

        String pathStr = ctx.pathString();
        String value = p.getValueAsString();
        if (value == null) return;

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

    private static void handlePdcpNode(String fieldName, JsonParser p, PacketParseContext ctx) throws IOException {
        PdcpInfo pdcp = ctx.currentPdcp();
        if (pdcp == null) return;

        String pathStr = ctx.pathString();
        String value = p.getValueAsString();
        if (value == null) return;

        switch (fieldName) {
            case "pdcp-nr.signalling-data": {
                String normalized = normalizeHex(value);
                pdcp.setSignallingDataHex(normalized);
                pdcp.putFieldPath("pdcp-nr.signalling-data", pathStr);
                pdcp.setPdcpencrypted(true);
                ctx.markPdcpEncrypted();
                break;
            }
            case "pdcp-nr.mac": {
                String normalized = normalizeHex(value);
                pdcp.setMacHex(normalized);
                pdcp.putFieldPath("pdcp-nr.mac", pathStr);
                break;
            }
            case "pdcp-nr.direction": {
                String dir = null;
                if ("0".equals(value)) dir = "UL";
                else if ("1".equals(value)) dir = "DL";
                if (dir != null) {
                    pdcp.setDirection(dir);
                    pdcp.putFieldPath("pdcp-nr.direction", pathStr);
                    ctx.markDirection(dir);
                }
                break;
            }
            case "pdcp-nr.seq-num":
                pdcp.setSeqnum(value);
                pdcp.putFieldPath("pdcp-nr.seq-num", pathStr);
                break;

            case "pdcp-nr.Bearer-type":
                pdcp.setBearerType(value);
                pdcp.setBearerName(pdcp.mapPdcpBearerType(value));
                pdcp.putFieldPath("pdcp-nr.Bearer-type", pathStr);
                break;
            default:
                break;
        }
    }

    private static void handleNgapObjectEnter(String fieldName, PacketParseContext ctx) {
        NgapInfo ngap = ctx.currentNgap();
        if (ngap == null) return;

        String pathStr = ctx.pathString();
        switch (fieldName) {
            case "ngap.initiatingMessage_element":
                ngap.setPduType("initiatingMessage");
                ngap.putFieldPath("pduType", pathStr);
                break;
            case "ngap.successfulOutcome_element":
                ngap.setPduType("successfulOutcome");
                ngap.putFieldPath("pduType", pathStr);
                break;
            case "ngap.unsuccessfulOutcome_element":
                ngap.setPduType("unsuccessfulOutcome");
                ngap.putFieldPath("pduType", pathStr);
                break;
            default:
                break;
        }
    }

    private static void handleNgapNode(String fieldName, JsonParser p, PacketParseContext ctx) throws IOException {
        NgapInfo ngap = ctx.currentNgap();
        if (ngap == null) return;

        String pathStr = ctx.pathString();
        String value = p.getValueAsString();
        if (value == null) return;

        switch (fieldName) {
            case "ngap.SecurityKey":
                ngap.setSecurityKeyHex(normalizeHex(value));
                ngap.putFieldPath("ngap.SecurityKey", pathStr);
                break;
            case "ngap.RAN_UE_NGAP_ID":
                ngap.setRanUeNgapId(value);
                ngap.putFieldPath("ngap.RAN_UE_NGAP_ID", pathStr);
                break;
            default:
                break;
        }
    }

    private static void handleRrcObjectEnter(String fieldName, PacketParseContext ctx) {
        RrcInfo rrc = ctx.currentRrc();
        if (rrc == null) return;

        String pathStr = ctx.pathString();
        switch (fieldName) {
            case "nr-rrc.UL_CCCH_Message_element":
            case "nr-rrc.UL_DCCH_Message_element":
                rrc.setDirection("UL");
                rrc.putFieldPath("direction", pathStr);
                ctx.markDirection("UL");
                break;

            case "nr-rrc.DL_CCCH_Message_element":
            case "nr-rrc.DL_DCCH_Message_element":
                rrc.setDirection("DL");
                rrc.putFieldPath("direction", pathStr);
                ctx.markDirection("DL");
                break;

            default:
                break;
        }
    }

    private static void handleRrcNode(String fieldName, JsonParser p, PacketParseContext ctx) throws IOException {
        RrcInfo rrc = ctx.currentRrc();
        if (rrc == null) return;

        String pathStr = ctx.pathString();
        String value = p.getValueAsString();
        if (value == null) return;

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

    private static void handleNasNode(String fieldName, JsonParser p, PacketParseContext ctx) throws IOException {
        NasInfo nas = ctx.currentNas();
        PacketParseContext.NasState st = ctx.currentNasState();
        if (nas == null || st == null) return;

        String pathStr = ctx.pathString();
        String value = p.getValueAsString();
        if (value == null) return;

        switch (fieldName) {
            case "nas-5gs.security_header_type":
                nas.setSecurityHeaderType(value);
                nas.putFieldPath("nas-5gs.security_header_type", pathStr);
                st.sht = value;
                if ("4".equals(value) || "2".equals(value)) {
                    nas.setEncrypted(true);
                    ctx.result.setEncrypted(true);
                    ctx.markNasEncrypted();
                }
                break;

            case "nas-5gs.mm.message_type":
                nas.setMmMessageType(value);
                nas.putFieldPath("nas-5gs.mm.message_type", pathStr);
                if ("0x5d".equalsIgnoreCase(value)) {
                    ctx.markMsgCode(MsgCode.NAS_SECURITY_MODE_COMMAND.code);
                }
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

            case "nas-5gs.epd":
                st.epd = value;
                break;
            case "nas-5gs.spare_half_octet":
                st.spare = value;
                break;
            case "nas-5gs.msg_auth_code":
                st.mac = value;
                break;
            case "nas-5gs.seq_no":
                st.seq = value;
                break;

            default:
                break;
        }

        st.tryFinalizeWithRaw();
    }

    // utils
    private static String normalizeHex(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("0x") || v.startsWith("0X")) v = v.substring(2);
        v = v.replace(":", "").replace(" ", "");
        return v.toLowerCase();
    }
}
