package com.example.procedure.infrastructure.dissection.entry;

import com.example.procedure.infrastructure.dissection.DissectionResult;
import com.example.procedure.infrastructure.dissection.PacketBuffer;
import com.example.procedure.infrastructure.dissection.PacketContext;
import com.example.procedure.infrastructure.dissection.PacketDissector;
import com.example.procedure.infrastructure.dissection.ProtocolRegistration;

/**
 * Base class for first-stage entry dissectors.
 *
 * <p>At this migration step each entry dissector only establishes a stable
 * protocol identity and adds itself to the protocol trace. Deeper field parsing
 * will be layered on top later.</p>
 */
public abstract class AbstractEntryDissector implements PacketDissector {

    private final ProtocolRegistration registration;

    protected AbstractEntryDissector(String displayName, String shortName, String filterName) {
        this.registration = new ProtocolRegistration(displayName, shortName, filterName);
    }

    @Override
    public ProtocolRegistration registration() {
        return registration;
    }

    @Override
    public DissectionResult dissect(PacketBuffer buffer, PacketContext context) {
        context.addProtocol(registration.filterName());
        return DissectionResult.of(
                registration.filterName(),
                registration.shortName(),
                registration.displayName(),
                context.getProtocolTrace()
        );
    }
}
