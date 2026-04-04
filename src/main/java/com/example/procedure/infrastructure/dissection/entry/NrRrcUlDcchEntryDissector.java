package com.example.procedure.infrastructure.dissection.entry;

import org.springframework.stereotype.Component;

@Component
public class NrRrcUlDcchEntryDissector extends AbstractEntryDissector {

    public NrRrcUlDcchEntryDissector() {
        super("NR Radio Resource Control UL-DCCH", "NR-RRC UL-DCCH", "nr-rrc.ul.dcch");
    }
}
