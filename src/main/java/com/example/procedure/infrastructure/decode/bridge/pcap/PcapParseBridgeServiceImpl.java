package com.example.procedure.infrastructure.decode.bridge.pcap;

import com.example.procedure.processing.pcap.PcapDecodeCommand;
import org.springframework.stereotype.Service;

/**
 * Default bridge implementation that delegates directly to the configured pcap
 * decode gateway.
 */
@Service
public class PcapParseBridgeServiceImpl implements PcapParseBridgeService {

    private final PcapDecodeGateway pcapDecodeGateway;

    public PcapParseBridgeServiceImpl(PcapDecodeGateway pcapDecodeGateway) {
        this.pcapDecodeGateway = pcapDecodeGateway;
    }

    @Override
    public void parse(PcapDecodeCommand request) throws Exception {
        pcapDecodeGateway.decode(request);
    }
}
