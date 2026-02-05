package com.example.procedure.util;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.parser.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;


public class SignalingMessagePrinter {

    private static final String IND = "  ";

    public static String prettyPrint(SignalingMessage msg) {
        StringBuilder sb = new StringBuilder();
        if (msg == null) {
            return "SignalingMessage: null\n";
        }

        sb.append("SignalingMessage {\n");

        appendKV(sb, 1, "frameNo", msg.getFrameNo());
        appendKV(sb, 1, "msgType", msg.getMsgType());
        appendKV(sb, 1, "timestamp", msg.getTimestamp());

        // ---- 顶层基础字段（你关心 + 常用） ----
        appendKV(sb, 1, "msgId", msg.getMsgId());
        appendKV(sb, 1, "ueId", msg.getUeId());
        appendKV(sb, 1, "iface", msg.getIface());
        appendKV(sb, 1, "direction(raw)", msg.getDirection()); // Lombok field getter 也会叫 getDirection()，所以这里可能冲突
        // ↑ 注意：你类里手写了 getDirection()，会覆盖 Lombok 的 getter，因此 direction(raw) 可能取不到。
        //   如果你确实想打印原始字段 direction，建议把字段改名 rawDirection，或新增 getRawDirection()。

        // 加密信息（你类里有计算逻辑）
        appendKV(sb, 1, "encrypted(field)", msg.getEncrypted());

        appendKV(sb, 1, "decryptPlainHex", msg.getDecryptPlainHex());
        appendKV(sb, 1, "decryptMacHex", msg.getDecryptMacHex());

        // payload（你没贴 MessagePayload 结构，这里做通用打印）
        if (msg.getPayload() != null) {
            sb.append(indent(1)).append("payload {\n");
            appendObjectFieldsByReflection(sb, 2, msg.getPayload());
            sb.append(indent(1)).append("}\n");
        } else {
            sb.append(indent(1)).append("payload: null\n");
        }

        // ---- 分层打印：不为 null 才打印 ----
        if (msg.getMacInfo() != null) {
            sb.append(indent(1)).append("macInfo {\n");
            appendMacInfo(sb, 2, msg.getMacInfo());
            sb.append(indent(1)).append("}\n");
        } else {
            sb.append(indent(1)).append("macInfo: null\n");
        }

        if (msg.getPdcpInfo() != null) {
            sb.append(indent(1)).append("pdcpInfo {\n");
            appendPdcpInfo(sb, 2, msg.getPdcpInfo());
            sb.append(indent(1)).append("}\n");
        } else {
            sb.append(indent(1)).append("pdcpInfo: null\n");
        }

        if (msg.getRrcInfo() != null) {
            sb.append(indent(1)).append("rrcInfo {\n");
            appendRrcInfo(sb, 2, msg.getRrcInfo());
            sb.append(indent(1)).append("}\n");
        } else {
            sb.append(indent(1)).append("rrcInfo: null\n");
        }

        if (msg.getNgapInfoList() != null && !msg.getNgapInfoList().isEmpty()) {
            sb.append(indent(1)).append("ngapInfoList [\n");
            for (int i = 0; i < msg.getNgapInfoList().size(); i++) {
                NgapInfo n = msg.getNgapInfoList().get(i);
                sb.append(indent(2)).append("[").append(i).append("] {\n");
                if (n != null) appendNgapInfo(sb, 3, n);
                else sb.append(indent(3)).append("null\n");
                sb.append(indent(2)).append("}\n");
            }
            sb.append(indent(1)).append("]\n");
        } else {
            sb.append(indent(1)).append("ngapInfoList: null/empty\n");
        }

        if (msg.getNuarInfo() != null) {
            sb.append(indent(1)).append("nuarInfo {\n");
            appendNuarInfo(sb, 2, msg.getNuarInfo());
            sb.append(indent(1)).append("}\n");
        } else {
            sb.append(indent(1)).append("nuarInfo: null\n");
        }

        if (msg.getNasList() != null && !msg.getNasList().isEmpty()) {
            sb.append(indent(1)).append("nasList [\n");
            for (int i = 0; i < msg.getNasList().size(); i++) {
                NasInfo n = msg.getNasList().get(i);
                sb.append(indent(2)).append("[").append(i).append("] {\n");
                if (n != null) appendNasInfo(sb, 3, n);
                else sb.append(indent(3)).append("null\n");
                sb.append(indent(2)).append("}\n");
            }
            sb.append(indent(1)).append("]\n");
        } else {
            sb.append(indent(1)).append("nasList: null/empty\n");
        }

        sb.append("}\n");
        return sb.toString();
    }


    public static void printAndWriteToFile(SignalingMessage msg, Path file, boolean append) {
        String content = prettyPrint(msg);

        // 1) 控制台输出
        System.out.print(content);

        // 2) 写入文件
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);

            if (append) {
                Files.writeString(
                        file,
                        content,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                );
            } else {
                Files.writeString(
                        file,
                        content,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
            }
        } catch (IOException e) {
            // 文件写失败不影响控制台打印
            System.err.println("[SignalingMessagePrinter] write file failed: " + file + ", err=" + e.getMessage());
        }
    }


    // =========================
    // 各层专用打印
    // =========================

    private static void appendMacInfo(StringBuilder sb, int level, MacInfo m) {
        appendKV(sb, level, "sequence", m.getSequence());
        appendKV(sb, level, "rnti", m.getRnti());
        appendKV(sb, level, "rntiType", m.getRntiType());
//        appendMap(sb, level, "fieldPaths", m.getFieldPaths());
    }

    private static void appendPdcpInfo(StringBuilder sb, int level, PdcpInfo p) {
        appendKV(sb, level, "sequence", p.getSequence());
        appendKV(sb, level, "pdcpencrypted", p.isPdcpencrypted());
        appendKV(sb, level, "direction", p.getDirection());
        appendKV(sb, level, "seqnum", p.getSeqnum());
        appendKV(sb, level, "signallingDataHex", p.getSignallingDataHex());
        appendKV(sb, level, "macHex", p.getMacHex());
        appendKV(sb, level, "decyptedTexHex", p.getDecyptedTexHex());
//        appendMap(sb, level, "fieldPaths", p.getFieldPaths());
    }

    private static void appendRrcInfo(StringBuilder sb, int level, RrcInfo r) {
        appendKV(sb, level, "sequence", r.getSequence());
        appendKV(sb, level, "direction", r.getDirection());
        appendKV(sb, level, "msgName", r.getMsgName());
        appendKV(sb, level, "randomValueHex", r.getRandomValueHex());
        appendKV(sb, level, "establishmentCause", r.getEstablishmentCause());
        appendKV(sb, level, "crnti", r.getCrnti());
        appendKV(sb, level, "integrityProtAlgorithm", r.getIntegrityProtAlgorithm());
        appendKV(sb, level, "cipheringAlgorithm", r.getCipheringAlgorithm());
        appendKV(sb, level, "hasDedicatedNas", r.isHasDedicatedNas());
//        appendMap(sb, level, "fieldPaths", r.getFieldPaths());
    }

    private static void appendNgapInfo(StringBuilder sb, int level, NgapInfo n) {
        appendKV(sb, level, "sequence", n.getSequence());
        appendKV(sb, level, "direction", n.getDirection());
        appendKV(sb, level, "pduType", n.getPduType());
        appendKV(sb, level, "msgName", n.getMsgName());
        appendKV(sb, level, "securityKeyHex", n.getSecurityKeyHex());
        appendKV(sb, level, "ranUeNgapId", n.getRanUeNgapId());
//        appendMap(sb, level, "fieldPaths", n.getFieldPaths());
    }

    private static void appendNuarInfo(StringBuilder sb, int level, NUARInfo n) {
        appendKV(sb, level, "sequence", n.getSequence());
        appendKV(sb, level, "msgName", n.getMsgName());
        appendKV(sb, level, "supi", n.getSupi());
        appendKV(sb, level, "imsi", n.getImsi());
        appendKV(sb, level, "kseafHex", n.getKseafHex());
        appendKV(sb, level, "authResult", n.getAuthResult());
//        appendMap(sb, level, "fieldPaths", n.getFieldPaths());
    }

    private static void appendNasInfo(StringBuilder sb, int level, NasInfo n) {
        appendKV(sb, level, "sequence", n.getSequence());
        appendKV(sb, level, "encrypted", n.isEncrypted());
        appendKV(sb, level, "fullNasPduHex", n.getFullNasPduHex());
        appendKV(sb, level, "cipherTextHex", n.getCipherTextHex());
        appendKV(sb, level, "decyptedTexHex", n.getDecyptedTexHex());

        appendKV(sb, level, "epd", n.getEpd());
        appendKV(sb, level, "spareHalfOctet", n.getSpareHalfOctet());
        appendKV(sb, level, "securityHeaderType", n.getSecurityHeaderType());
        appendKV(sb, level, "msgAuthCodeHex", n.getMsgAuthCodeHex());
        appendKV(sb, level, "seqNo", n.getSeqNo());
        appendKV(sb, level, "mmMessageType", n.getMmMessageType());
        appendKV(sb, level, "nas_cipheringAlgorithm", n.getNas_cipheringAlgorithm());
        appendKV(sb, level, "nas_integrityProtAlgorithm", n.getNas_integrityProtAlgorithm());
        appendKV(sb, level, "guamiMcc", n.getGuamiMcc());
        appendKV(sb, level, "guamiMnc", n.getGuamiMnc());
        appendKV(sb, level, "tmsi", n.getTmsi());
        appendKV(sb, level, "regType5gs", n.getRegType5gs());

        // nasNode 可能很大：这里只打印 node 的简要信息，避免日志爆炸
        if (n.getNasNode() != null) {
            sb.append(indent(level)).append("nasNode: ").append("JsonNode(")
                    .append(n.getNasNode().getNodeType()).append(")\n");
        } else {
            sb.append(indent(level)).append("nasNode: null\n");
        }

//        appendMap(sb, level, "fieldPaths", n.getFieldPaths());
    }

    // =========================
    // 通用打印小工具
    // =========================

    private static void appendKV(StringBuilder sb, int level, String key, Object val) {
        sb.append(indent(level)).append(key).append(": ").append(valToString(val)).append("\n");
    }

    private static void appendMap(StringBuilder sb, int level, String key, Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            sb.append(indent(level)).append(key).append(": {}").append("\n");
            return;
        }
        sb.append(indent(level)).append(key).append(": {\n");
        for (Map.Entry<?, ?> e : map.entrySet()) {
            sb.append(indent(level + 1))
                    .append(String.valueOf(e.getKey()))
                    .append(" -> ")
                    .append(String.valueOf(e.getValue()))
                    .append("\n");
        }
        sb.append(indent(level)).append("}\n");
    }

    private static String indent(int level) {
        return IND.repeat(Math.max(0, level));
    }

    private static String valToString(Object val) {
        if (val == null) return "null";
        if (val instanceof String) {
            String s = (String) val;
            return s.isEmpty() ? "\"\"" : s;
        }
        return String.valueOf(val);
    }

    /**
     * 对未知对象（比如 MessagePayload）用反射把字段打印出来（只打印一层，避免递归爆炸）
     */
    private static void appendObjectFieldsByReflection(StringBuilder sb, int level, Object obj) {
        if (obj == null) {
            sb.append(indent(level)).append("null\n");
            return;
        }
        Class<?> c = obj.getClass();
        sb.append(indent(level)).append("class: ").append(c.getName()).append("\n");

        Field[] fields = c.getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                sb.append(indent(level)).append(f.getName()).append(": ").append(valToString(v)).append("\n");
            } catch (Exception ex) {
                sb.append(indent(level)).append(f.getName()).append(": <unreadable: ").append(ex.getMessage()).append(">\n");
            }
        }
    }

    // 避免 getter 抛异常导致打印中断
    private static String safeCall(SupplierWithEx<String> fn) {
        try { return fn.get(); } catch (Exception e) { return "<error: " + e.getMessage() + ">"; }
    }

    private static boolean safeCallBool(BooleanSupplierWithEx fn) {
        try { return fn.getAsBoolean(); } catch (Exception e) { return false; }
    }

    @FunctionalInterface
    private interface SupplierWithEx<T> { T get() throws Exception; }

    @FunctionalInterface
    private interface BooleanSupplierWithEx { boolean getAsBoolean() throws Exception; }
}
