package com.example.procedure.infrastructure.dissection.registry;

import com.example.procedure.infrastructure.dissection.PacketDissector;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of protocol dissectors keyed by protocol/filter name.
 *
 * <p>This mirrors Wireshark's registration step before handoff bindings are
 * attached through dissector tables.</p>
 */
@Component
public class ProtocolDissectorRegistry {

    private final Map<String, PacketDissector> byFilterName;

    public ProtocolDissectorRegistry(List<PacketDissector> dissectors) {
        Map<String, PacketDissector> map = new LinkedHashMap<>();
        if (dissectors != null) {
            for (PacketDissector dissector : dissectors) {
                if (dissector == null || dissector.registration() == null) {
                    continue;
                }
                String filterName = normalize(dissector.registration().filterName());
                if (filterName == null || filterName.isBlank()) {
                    continue;
                }
                map.put(filterName, dissector);
            }
        }
        this.byFilterName = Map.copyOf(map);
    }

    public Optional<PacketDissector> findByFilterName(String filterName) {
        return Optional.ofNullable(byFilterName.get(normalize(filterName)));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
