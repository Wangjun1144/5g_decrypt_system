package com.example.procedure.infrastructure.dissection.entry;

import org.springframework.stereotype.Component;

@Component
public class NrRrcDlCcchEntryDissector extends AbstractEntryDissector {

    public NrRrcDlCcchEntryDissector() {
        super("NR Radio Resource Control DL-CCCH", "NR-RRC DL-CCCH", "nr-rrc.dl.ccch");
    }
}
