package com.example.procedure.infrastructure.dissection.registry;

import com.example.procedure.infrastructure.dissection.PacketDissector;
import com.example.procedure.infrastructure.wireshark.WiresharkProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry that maps capture link types to entry dissectors.
 *
 * <p>This mirrors Wireshark's handoff idea in a project-specific and minimal
 * form: the first dispatch decision is based on link type / DLT.</p>
 */
@Component
public class LinkTypeDissectorRegistry {

    private final Map<Integer, PacketDissector> byLinkType;

    public LinkTypeDissectorRegistry(
            ProtocolDissectorRegistry protocolRegistry,
            WiresharkProperties props
    ) {
        this.byLinkType = buildRegistry(protocolRegistry, props);
    }

    public Optional<PacketDissector> lookup(int linkType) {
        return Optional.ofNullable(byLinkType.get(linkType));
    }

    private Map<Integer, PacketDissector> buildRegistry(
            ProtocolDissectorRegistry protocolRegistry,
            WiresharkProperties props
    ) {
        Map<Integer, PacketDissector> registry = new LinkedHashMap<>();
        if (props != null && props.getUserDlts() != null) {
            for (Map.Entry<Integer, String> entry : props.getUserDlts().entrySet()) {
                Integer linkType = entry.getKey();
                String dissectorName = normalize(entry.getValue());
                if (linkType == null || dissectorName == null || dissectorName.isBlank()) {
                    continue;
                }
                protocolRegistry.findByFilterName(dissectorName)
                        .ifPresent(dissector -> registry.put(linkType, dissector));
            }
        }

        addIfRegistered(protocolRegistry, registry, 147, "nr-rrc.ul.dcch");
        addIfRegistered(protocolRegistry, registry, 148, "nr-rrc.dl.dcch");
        addIfRegistered(protocolRegistry, registry, 149, "udp");
        addIfRegistered(protocolRegistry, registry, 150, "nr-rrc.dl.ccch");
        addIfRegistered(protocolRegistry, registry, 151, "nas-5gs");
        addIfRegistered(protocolRegistry, registry, 152, "ngap");
        addIfRegistered(protocolRegistry, registry, 153, "nr-rrc.ul.ccch");
        return registry;
    }

    private void addIfRegistered(
            ProtocolDissectorRegistry protocolRegistry,
            Map<Integer, PacketDissector> registry,
            int linkType,
            String filterName
    ) {
        if (registry.containsKey(linkType)) {
            return;
        }
        protocolRegistry.findByFilterName(filterName)
                .ifPresent(dissector -> registry.put(linkType, dissector));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
