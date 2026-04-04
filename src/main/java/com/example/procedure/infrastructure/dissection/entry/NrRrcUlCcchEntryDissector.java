package com.example.procedure.infrastructure.dissection.entry;

import org.springframework.stereotype.Component;

@Component
public class NrRrcUlCcchEntryDissector extends AbstractEntryDissector {

    public NrRrcUlCcchEntryDissector() {
        super("NR Radio Resource Control UL-CCCH", "NR-RRC UL-CCCH", "nr-rrc.ul.ccch");
    }
}
