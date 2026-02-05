//package com.example.procedure.streaming.layers;
//
//import com.fasterxml.jackson.databind.JsonNode;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
///**
// * @param packetIndex    0-based in JSON array
// * @param frameNumber    optional, may be null
// * @param frameTimeEpoch optional, may be null
// * @param frameProtocols optional, may be null
// * @param layers         ordered, duplicates preserved
// */
//public record PacketLayers(long packetIndex, String frameNumber, String frameTimeEpoch, String frameProtocols,
//                           List<LayerOccurrence> layers) {
//    public PacketLayers(long packetIndex, String frameNumber, String frameTimeEpoch, String frameProtocols,
//                        List<LayerOccurrence> layers) {
//        this.packetIndex = packetIndex;
//        this.frameNumber = frameNumber;
//        this.frameTimeEpoch = frameTimeEpoch;
//        this.frameProtocols = frameProtocols;
//        this.layers = Collections.unmodifiableList(new ArrayList<>(layers));
//    }
//
//    public List<JsonNode> getAll(String layerName) {
//        List<JsonNode> out = new ArrayList<>();
//        for (LayerOccurrence o : layers) {
//            if (layerName.equals(o.layerName())) out.add(o.value());
//        }
//        return out;
//    }
//
//    public JsonNode getFirst(String layerName) {
//        for (LayerOccurrence o : layers) {
//            if (layerName.equals(o.layerName())) return o.value();
//        }
//        return null;
//    }
//}
