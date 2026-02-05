//package com.example.procedure.streaming.parser;
//
//
//import java.util.Map;
//import java.util.Objects;
//
///**
// * Strict latch semantics:
// * - When a *_raw is seen: arm() with expected logic layer.
// * - On entering ANY top-level layer: consumeOrDropOnEnterLayer() is called:
// *     * if entering layer matches expected logic: return hex (consume)
// *     * else: return null (drop)
// *   In both cases the latch is CLEARED (your "enter next layer must clear even if unused" requirement).
// *
// * Additionally records linking evidence: rawPath/order and logicPath/order, plus matched/sameParent.
// */
//public final class StrictRawLatch {
//
//    /**
//     * One record describing a raw->logic link attempt for debugging/auditing.
//     */
//    public record LinkRecord(
//            String rawLayer,
//            String logicLayerExpected,
//            String rawPath,
//            int rawOrder,
//            String logicLayerActual,
//            String logicPath,
//            int logicOrder,
//            boolean matched,
//            boolean sameParent,
//            String note
//    ) {}
//
//    private final RawCaptureConfig cfg;
//    private final Map<String, RawRule> rules;
//
//    private String armedRawLayer;
//    private String armedLogicExpected;
//    private String armedHex;
//    private String armedPath;
//    private int armedOrder = -1;
//
//    private LinkRecord lastLink;
//
//    public StrictRawLatch(RawCaptureConfig cfg, Map<String, RawRule> rules) {
//        this.cfg = Objects.requireNonNull(cfg, "cfg");
//        this.rules = Objects.requireNonNull(rules, "rules");
//    }
//
//    /**
//     * Arm latch with raw payload. Overwrites any existing armed raw (strict semantics).
//     *
//     * @param rawLayerName e.g. "nas-5gs_raw"
//     * @param rawHex       hex string extracted from raw layer
//     * @param rawPath      path/evidence string (e.g. "layers/nas-5gs_raw#3" or DFS path)
//     * @param order        top-level order index (occurrence order)
//     */
//    public void arm(String rawLayerName, String rawHex, String rawPath, int order) {
//        if (rawHex == null || rawHex.isEmpty()) return;
//        if (!cfg.enabled(rawLayerName)) return;
//
//        RawRule rule = rules.get(rawLayerName);
//        if (rule == null) return;
//
//        this.armedRawLayer = rawLayerName;
//        this.armedLogicExpected = rule.logicLayer();
//        this.armedHex = normalizeHex(rawHex);
//        this.armedPath = rawPath;
//        this.armedOrder = order;
//        this.lastLink = null;
//    }
//
//    /**
//     * Must be called whenever you "enter the next top-level layer".
//     * - If latch is armed and enteringLayer matches expected logic layer -> returns raw hex.
//     * - Otherwise returns null.
//     * In BOTH cases latch is cleared immediately (strict requirement).
//     *
//     * @param enteringLayer top-level layer name you're entering (e.g. "nas-5gs", "nr-rrc", "ngap", ...)
//     * @param logicPath     evidence path of this entering layer
//     * @param order         order index of this entering layer
//     */
//    public String consumeOrDropOnEnterLayer(String enteringLayer, String logicPath, int order) {
//        if (armedLogicExpected == null) return null;
//
//        boolean matched = armedLogicExpected.equals(enteringLayer);
//        boolean sameParent = sameParent(armedPath, logicPath);
//
//        String out = matched ? armedHex : null;
//
//        this.lastLink = new LinkRecord(
//                armedRawLayer,
//                armedLogicExpected,
//                armedPath,
//                armedOrder,
//                enteringLayer,
//                logicPath,
//                order,
//                matched,
//                sameParent,
//                matched ? "consumed" : "dropped (mismatch entering layer)"
//        );
//
//        // ✅ strict: entering next layer => always clear
//        clear();
//        return out;
//    }
//
//    public boolean isArmed() {
//        return armedLogicExpected != null;
//    }
//
//    public LinkRecord lastLink() {
//        return lastLink;
//    }
//
//    public void clear() {
//        armedRawLayer = null;
//        armedLogicExpected = null;
//        armedHex = null;
//        armedPath = null;
//        armedOrder = -1;
//    }
//
//    // ------------------ helpers ------------------
//
//    /**
//     * Recommended: compare parent path to decide "same path".
//     * If you format path like "layers/<name>#<idx>", parent will be "layers".
//     */
//    public static boolean sameParent(String rawPath, String logicPath) {
//        String rp = parent(rawPath);
//        String lp = parent(logicPath);
//        return rp != null && rp.equals(lp);
//    }
//
//    public static String parent(String path) {
//        if (path == null) return null;
//        int idx = path.lastIndexOf('/');
//        return idx < 0 ? "" : path.substring(0, idx);
//    }
//
//    /**
//     * Normalizes hex string:
//     * - trim
//     * - remove 0x
//     * - remove ':' and spaces
//     * - to lower case
//     */
//    public static String normalizeHex(String value) {
//        if (value == null) return null;
//        String v = value.trim();
//        if (v.startsWith("0x") || v.startsWith("0X")) v = v.substring(2);
//        v = v.replace(":", "").replace(" ", "");
//        return v.toLowerCase();
//    }
//}
