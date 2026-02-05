package com.example.procedure.streaming.layers;

import com.example.procedure.model.MsgCode;
import com.example.procedure.parser.*;
import com.example.procedure.streaming.index.ChainIndex;
import com.example.procedure.streaming.index.MsgType;
import com.example.procedure.streaming.parser.RrcNasParseResult;
import com.example.procedure.streaming.parser.PacketParseContext;
import com.example.procedure.util.JsonStreamUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;

public final class LayersSelectiveParser {

    private static final JsonFactory FACTORY = new JsonFactory();

    private LayersSelectiveParser() {}


    /**
     * 输出：一个 packet 可能对应多条链（每个 wanted 且非 _raw 的 layer 一条链）
     *
     * @param enabledRawLayers 只抓取你启用的 raw layer（例如 Set.of("nas-5gs_raw")）。
     *                        未启用的 *_raw 会被当作普通字段跳过，不参与严格配对。
     */
    public static void parsePackets(InputStream in,
                                    Set<String> wantedFields,
                                    Set<String> enabledRawLayers,
                                    Consumer<List<RrcNasParseResult>> onPacket) throws IOException {
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

                List<RrcNasParseResult> chains = parseOnePacketObject(p, packetIndex, wantedFields, enabledRawLayers);
                onPacket.accept(chains);
                packetIndex++;
            }
        }
    }

    private static List<RrcNasParseResult> parseOnePacketObject(JsonParser p,
                                                                long packetIndex,
                                                                Set<String> wantedFields,
                                                                Set<String> enabledRawLayers) throws IOException {
        List<RrcNasParseResult> chains = new ArrayList<>();

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
                                          List<RrcNasParseResult> chains) throws IOException {
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
     * 规则：layers 下每个 wanted 且非 _raw 的 layer = 一条链
     *
     * raw 严格配对逻辑不在这里做（不再用 lastCtxByLayer），而是在 scanAnyValue 的
     * “每个 object 字段遍历循环”中做：*_raw 必须紧跟 xxx 才消费。
     *
     * 但注意：为了不漏掉 enabledRawLayers（比如 nas-5gs_raw），这里必须允许 enabled raw layer 被解析（不 skip）。
     */
    /**
     * ✅ 完整替换版：顶层 layers object 也需要 strict pending（因为它不走 scanAnyValue）
     * 将上面 parseLayersObject 的内容整体替换成这个实现即可。
     */
    private static void parseLayersObject(JsonParser p,
                                                   long packetIndex,
                                                   Set<String> wantedFields,
                                                   Set<String> enabledRawLayers,
                                                   List<RrcNasParseResult> chains) throws IOException {

        // ===== per-packet frame meta =====
        FrameMeta frame = new FrameMeta();
        boolean filtered = false;
        boolean dropPacket = false;

        String pendingLogic = null; // e.g. "nas-5gs"
        String pendingHex = null;   // extracted hex

        while (p.nextToken() != JsonToken.END_OBJECT) {
            if (p.currentToken() != JsonToken.FIELD_NAME) continue;

            String layerName = p.currentName();
            JsonToken v = p.nextToken();

            // ---- 0) frame 优先解析（一般在最前）----
            if ("frame".equals(layerName)) {
                parseFrameLayer(p, v, frame);

                if (!filtered) {
                    filtered = true;
                    // 没拿到 protocols：这里选择不过滤（你也可改成直接丢）
                    if (frame.protocols != null) {
                        if (!containsUsefulProtocol(frame.protoList, frame.protocols)) {
                            dropPacket = true;
                        } else if (isUuMacNr(frame.protoList) && onlyMacAndRlcAfterMac(frame.protoList)) {
                            dropPacket = true;
                        }
                    }
                }
                continue;
            }

            // ---- 1) 如果判定丢包，后面全部 skip ----
            if (dropPacket) {
                p.skipChildren();
                pendingLogic = null;
                pendingHex = null;
                continue;
            }


            // 顶层剪枝：逻辑 layer 不 wanted 就跳过；
            // raw layer 如果 enabled 必须保留（否则 strict 配对无法发生）
            boolean keepForParse = wantedFields.contains(layerName) || enabledRawLayers.contains(layerName);
            if (!keepForParse) {
                // strict：即使 skip，也要清 pending（已在循环开头处理过）
                p.skipChildren();
                // 丢弃 pending（严格语义）
                pendingLogic = null;
                pendingHex = null;
                continue;
            }

            // 如果是启用的 raw：提取并 arm pending，等待下一个 sibling
            if (layerName.endsWith("_raw") && enabledRawLayers.contains(layerName)) {
                String base = layerName.substring(0, layerName.length() - "_raw".length());
                String rawHex = extractFirstHexFromRawLayerValue(p, v); // 消费子树
                // strict：不管是否抽到 hex，都清空再决定是否设置 pending
                pendingLogic = null;
                pendingHex = null;
                if (rawHex != null) {
                    pendingLogic = base;
                    pendingHex = rawHex;
                }
                continue;
            }

            // ---- 4) wanted 逻辑层：创建 chain，必要时消费 pending raw ----
            if (!layerName.endsWith("_raw") && wantedFields.contains(layerName)) {
                // strict：如果 pending 恰好匹配本 layer，consume
                String consumedRaw = null;
                if (pendingLogic != null && pendingLogic.equals(layerName)) {
                    consumedRaw = pendingHex;
                }
                // strict：进入本 layer 后 pending 必须清空（无论 matched 与否）
                pendingLogic = null;
                pendingHex = null;

                RrcNasParseResult chain = new RrcNasParseResult();
                // ✅ 每条 chain 一个索引
                ChainIndex index = new ChainIndex();
                chain.setIndex(index);

                // ✅ 把 frame 信息复制到每条 chain
                chain.setFrameNo(frame.frameNo);
                chain.setTimestampMs(frame.timestampMs);
                chain.setFrameProtocols(frame.protocols);
                chain.setProtoList(frame.protoList == null ? new ArrayList<>() : new ArrayList<>(frame.protoList));

                PacketParseContext ctx = new PacketParseContext(chain, packetIndex, enabledRawLayers);
                Deque<EnterMark> enterStack = new ArrayDeque<>();

                // ✅ 建 PACKET 虚拟根
                index.startPacketRoot("layers/" + layerName, ctx.depth); // depth 你现在进入 scanAnyValue 前通常是 0 或当前深度

                if (consumedRaw != null) {
                    ctx.putRawHex(layerName, consumedRaw); // nas-5gs -> raw
                }

                scanAnyValue(p, v, layerName, ctx, enterStack);
                // ✅ 关 PACKET 根
                index.endPacketRoot();
                chains.add(chain);
                continue;
            }

            // 其他情况（比如 raw 但未 enabled，或逻辑层不 wanted）：跳过子树
            p.skipChildren();
            // strict：进入本 sibling 后 pending 必须清空
            pendingLogic = null;
            pendingHex = null;
        }

        // strict：object 结束 pending 直接丢弃
        // (pendingLogic/pendingHex 局部变量自然被回收)
    }

    private static final class FrameMeta {
        long frameNo;
        long timestampMs;
        String protocols;
        List<String> protoList = List.of();
    }

    private static void parseFrameLayer(JsonParser p, JsonToken v, FrameMeta out) throws IOException {
        if (v != JsonToken.START_OBJECT) {
            p.skipChildren();
            return;
        }

        String number = null;
        String timeEpoch = null;
        String protocols = null;

        while (p.nextToken() != JsonToken.END_OBJECT) {
            if (p.currentToken() != JsonToken.FIELD_NAME) {
                p.skipChildren();
                continue;
            }

            String f = p.currentName();
            JsonToken vv = p.nextToken();

            if ("frame.number".equals(f) && vv.isScalarValue()) {
                number = p.getValueAsString();
            } else if ("frame.time_epoch".equals(f) && vv.isScalarValue()) {
                timeEpoch = p.getValueAsString();
            } else if ("frame.protocols".equals(f) && vv.isScalarValue()) {
                protocols = p.getValueAsString();
            } else {
                p.skipChildren();
            }
        }

        out.frameNo = safeParseLong(number, 0L);
        out.timestampMs = safeParseEpochMs(timeEpoch, 0L);
        out.protocols = protocols;
        out.protoList = splitProtocols(protocols);
    }

    private static long safeParseLong(String s, long def) {
        try { return s == null ? def : Long.parseLong(s); } catch (Exception e) { return def; }
    }

    private static long safeParseEpochMs(String s, long def) {
        try {
            if (s == null) return def;
            double sec = Double.parseDouble(s);
            return (long) (sec * 1000L);
        } catch (Exception e) {
            return def;
        }
    }

    private static List<String> splitProtocols(String protoStr) {
        if (protoStr == null || protoStr.isEmpty()) return List.of();
        return Arrays.asList(protoStr.split(":"));
    }

    private static boolean containsUsefulProtocol(List<String> protos, String protoStr) {
        if (protos == null || protos.isEmpty()) return false;

        boolean hasNgap = protos.contains("ngap");
        boolean hasHttp2Json = protoStr != null && protoStr.contains("http2:json");
        boolean has5gRelevant =
                protos.contains("mac-nr") ||
                        protos.contains("nr-rrc") ||
                        protos.contains("nas-5gs") ||
                        protos.contains("pdcp-nr");

        return hasNgap || hasHttp2Json || has5gRelevant;
    }

    private static boolean isUuMacNr(List<String> protos) {
        return protos != null && protos.contains("mac-nr");
    }

    private static boolean onlyMacAndRlcAfterMac(List<String> protos) {
        if (protos == null) return false;
        int idx = protos.indexOf("mac-nr");
        if (idx < 0) return false;

        for (int i = idx + 1; i < protos.size(); i++) {
            String p = protos.get(i);
            if (p != null && p.startsWith("rlc-nr")) continue;
            return false; // 出现非 rlc-nr => 有更高层
        }
        return true;
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
     * ✅ 这里实现“任意 object 层级”的 strict pending：
     * 在每个 START_OBJECT 的字段循环内维护 pendingRaw（局部变量），实现 *_raw 紧邻 xxx 才消费。
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

                // ✅ 本 object 层级的 strict pending
                String pendingLogic = null; // e.g. "nas-5gs"
                String pendingHex = null;

                while (p.nextToken() != JsonToken.END_OBJECT) {
                    if (p.currentToken() != JsonToken.FIELD_NAME) {
                        p.skipChildren();
                        continue;
                    }

                    String childField = p.currentName();

                    // strict：进入下一个 sibling 时，先处理上一条 pending
                    if (pendingLogic != null) {
                        if (pendingLogic.equals(childField)) {
                            // 1) 大 raw：nas-5gs_raw -> nas-5gs
                            if ("nas-5gs".equals(pendingLogic)) {
                                ctx.putRawHex("nas-5gs", pendingHex);
                            }
                            // 2) 字段 raw：nas-5gs.epd_raw -> nas-5gs.epd 等
                            else if (pendingLogic.startsWith("nas-5gs.") && ctx.inNas()) {
                                PacketParseContext.NasState st = ctx.currentNasState();
                                if (st != null) {
                                    // key 用逻辑字段名保存，比如 "nas-5gs.epd"
                                    st.rawFieldHex.put(pendingLogic, pendingHex);
                                }
                            }
                            // 3) 其他层的 raw（未来扩展）可以先丢弃或另行处理
                        }

                        // strict：无论消费/丢弃都清空
                        pendingLogic = null;
                        pendingHex = null;
                    }

                    // 原逻辑：提前看到 fieldName
                    onFieldNameSeen(childField, ctx);

                    JsonToken v = p.nextToken();

                    // ✅ 1) 未启用的 *_raw：直接跳过子树，不获取任何东西
                    if (childField.endsWith("_raw") && !ctx.isRawEnabled(childField)) {
                        p.skipChildren();
                        continue;
                    }

                    // ✅ 启用的 raw：抽 hex，设置 pending，等待下一个 sibling
                    if (childField.endsWith("_raw") && ctx.isRawEnabled(childField)) {
                        String base = childField.substring(0, childField.length() - "_raw".length());
                        String hex = extractFirstHexFromRawLayerValue(p, v); // 会消费子树
                        if (hex != null) {
                            pendingLogic = base;
                            pendingHex = hex;
                        }
                        continue;
                    }

                    scanAnyValue(p, v, childField, ctx, enterStack);
                }

                // strict：object 结束 pending 直接丢弃
                pendingLogic = null;
                pendingHex = null;

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
    // enter/exit phases（保持你原逻辑）
    // ------------------------------------------------------------

    private static EnterMark enterPhase(String fieldName, PacketParseContext ctx) {
        if (fieldName == null) return null;

        if ("mac-nr".equals(fieldName)) {
            ctx.macDepth++;
            ctx.markIface("Uu");
            ctx.newMac();
            // ✅ payloadIndex：刚 newMac() 之后，macList 最后一个
            int payloadIndex = ctx.result.getMacList().size() - 1;
            ctx.index.onEnter(MsgType.MAC, ctx.depth, ctx.pathString(), payloadIndex);

            return new EnterMark(Kind.MAC, ctx.depth);
        }

        if ("pdcp-nr".equals(fieldName)) {
            ctx.pdcpDepth++;
            ctx.newPdcp();
            int payloadIndex = ctx.result.getPdcpList().size() - 1;
            ctx.index.onEnter(MsgType.PDCP, ctx.depth, ctx.pathString(), payloadIndex);

            return new EnterMark(Kind.PDCP, ctx.depth);
        }

        if ("nr-rrc".equals(fieldName)) {
            ctx.rrcDepth++;
            ctx.newRrc();
            int payloadIndex = ctx.result.getRrcList().size() - 1;
            ctx.index.onEnter(MsgType.RRC, ctx.depth, ctx.pathString(), payloadIndex);

            return new EnterMark(Kind.RRC, ctx.depth);
        }

        if ("nas-5gs".equals(fieldName)) {
            ctx.pushNewNas();

            int payloadIndex = ctx.result.getNasList().size() - 1;
            ctx.index.onEnter(MsgType.NAS, ctx.depth, ctx.pathString(), payloadIndex);


            // raw 先到：进入 nas-5gs 时补 fullNasPduHex
            String raw = ctx.getRawHex("nas-5gs");
            if (raw != null) {
                NasInfo nas = ctx.currentNas();
                if (nas != null && (nas.getFullNasPduHex() == null || nas.getFullNasPduHex().isEmpty())) {
                    nas.setFullNasPduHex(raw);
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
            ctx.newNgap();
            ctx.markIface("N2");

            int payloadIndex = ctx.result.getNgapList().size() - 1;
            ctx.index.onEnter(MsgType.NGAP, ctx.depth, ctx.pathString(), payloadIndex);
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
                ctx.ngapDepth--;// NGAP 不同类嵌套：不需要 pop
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
    // onFieldNameSeen / onScalar / handlers（保持你原逻辑）
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
                // ✅ 在这里顺手打 msgCode
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

    // ---------------- handleXNode（保持你原来的实现即可，这里只保留你贴过的版本） ----------------

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
                if ("4".equals(value)) {
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
