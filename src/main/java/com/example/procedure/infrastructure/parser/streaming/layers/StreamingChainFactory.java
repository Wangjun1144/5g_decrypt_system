package com.example.procedure.infrastructure.parser.streaming.layers;

import com.example.procedure.infrastructure.parser.streaming.index.ChainIndex;
import com.example.procedure.infrastructure.parser.streaming.parser.StreamingChainParseResult;

import java.util.ArrayList;

/**
 * Creates initialized streaming chain results from frame metadata.
 */
public class StreamingChainFactory {

    /**
     * Creates one chain result and copies frame metadata into it.
     */
    public StreamingChainParseResult create(FrameLayerMetadata frame) {
        StreamingChainParseResult chain = new StreamingChainParseResult();
        chain.setIndex(new ChainIndex());

        if (frame != null) {
            chain.setFrameNo(frame.getFrameNo());
            chain.setTimestampMs(frame.getTimestampMs());
            chain.setFrameProtocols(frame.getProtocols());
            chain.setProtoList(new ArrayList<>(frame.getProtoList()));
        } else {
            chain.setProtoList(new ArrayList<>());
        }

        return chain;
    }
}
