package com.example.procedure.infrastructure.parser.streaming.layers;

import java.util.List;

/**
 * Applies packet-level layer filtering before the parser spends time building chains.
 */
public class PacketLayerFilter {

    /**
     * Returns whether a frame should be dropped before layer scanning continues.
     */
    public boolean shouldDropPacket(FrameLayerMetadata frame) {
        if (frame == null || frame.getProtocols() == null) {
            return false;
        }

        if (!containsUsefulProtocol(frame.getProtoList(), frame.getProtocols())) {
            return true;
        }

        return isUuMacNr(frame.getProtoList()) && onlyMacAndRlcAfterMac(frame.getProtoList());
    }

    boolean containsUsefulProtocol(List<String> protos, String protoStr) {
        if (protos == null || protos.isEmpty()) {
            return false;
        }

        boolean hasNgap = protos.contains("ngap");
        boolean hasHttp2Json = protoStr != null && protoStr.contains("http2:json");
        boolean has5gRelevant =
                protos.contains("mac-nr")
                        || protos.contains("nr-rrc")
                        || protos.contains("nas-5gs")
                        || protos.contains("pdcp-nr");

        return hasNgap || hasHttp2Json || has5gRelevant;
    }

    boolean isUuMacNr(List<String> protos) {
        return protos != null && protos.contains("mac-nr");
    }

    boolean onlyMacAndRlcAfterMac(List<String> protos) {
        if (protos == null) {
            return false;
        }

        int idx = protos.indexOf("mac-nr");
        if (idx < 0) {
            return false;
        }

        for (int i = idx + 1; i < protos.size(); i++) {
            String protocol = protos.get(i);
            if (protocol != null && protocol.startsWith("rlc-nr")) {
                continue;
            }
            return false;
        }
        return true;
    }
}
