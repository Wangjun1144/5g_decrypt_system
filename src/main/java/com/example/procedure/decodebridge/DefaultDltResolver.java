package com.example.procedure.decodebridge;

import org.springframework.stereotype.Component;

@Component
public class DefaultDltResolver implements DltResolver {

    @Override
    public int resolve(PlaintextDecodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PlaintextDecodeRequest must not be null");
        }

        if (request.getDlt() != null) {
            return request.getDlt();
        }

        String hint = upper(request.getProtocolHint());

        return switch (hint) {
            case "NR_RRC_UL_DCCH" -> 147;
            case "NR_RRC_DL_DCCH" -> 153;
            case "NR_RRC_UL_CCCH" -> 148; // 按你项目实际 user_dlts 配置调整
            case "NR_RRC_DL_CCCH" -> 150; // 按你项目实际 user_dlts 配置调整
            case "NAS_5GS" -> 151;
            case "NGAP" -> 152;
            default -> throw new IllegalArgumentException(
                    "Cannot resolve DLT. protocolHint=" + request.getProtocolHint()
                            + ", direction=" + request.getDirection()
                            + ", iface=" + request.getIface()
            );
        };
    }

    private String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}