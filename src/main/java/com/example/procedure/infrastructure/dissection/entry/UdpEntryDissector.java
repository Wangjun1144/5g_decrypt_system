package com.example.procedure.infrastructure.dissection.entry;

import org.springframework.stereotype.Component;

@Component
public class UdpEntryDissector extends AbstractEntryDissector {

    public UdpEntryDissector() {
        super("User Datagram Protocol", "UDP", "udp");
    }
}
