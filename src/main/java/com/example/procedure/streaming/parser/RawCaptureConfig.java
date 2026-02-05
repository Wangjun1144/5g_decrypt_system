//package com.example.procedure.streaming.parser;
//
//import java.util.Objects;
//import java.util.Set;
//
///**
// * Runtime configuration: which raw layers are enabled.
// * You can start with Set.of("nas-5gs_raw") and later enable more without changing parser logic.
// */
//
//public final class RawCaptureConfig {
//
//    private final Set<String> enabledRawLayers;
//
//    public RawCaptureConfig(Set<String> enabledRawLayers) {
//        Objects.requireNonNull(enabledRawLayers, "enabledRawLayers");
//        this.enabledRawLayers = Set.copyOf(enabledRawLayers);
//    }
//
//    public boolean enabled(String rawLayerName) {
//        return enabledRawLayers.contains(rawLayerName);
//    }
//
//    public Set<String> enabledRawLayers() {
//        return enabledRawLayers;
//    }
//
//    /** Convenience presets */
//    public static RawCaptureConfig enableNone() {
//        return new RawCaptureConfig(Set.of());
//    }
//
//    public static RawCaptureConfig enableNasOnly() {
//        return new RawCaptureConfig(Set.of("nas-5gs_raw"));
//    }
//
//    public static RawCaptureConfig enableNasAndRrc(){
//        return new RawCaptureConfig(Set.of("nas-5gs_raw", "nr-rrc_raw"));
//    }
//
//}