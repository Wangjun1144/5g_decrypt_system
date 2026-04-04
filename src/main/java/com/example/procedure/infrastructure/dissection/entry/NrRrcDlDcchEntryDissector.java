package com.example.procedure.infrastructure.dissection.entry;

import org.springframework.stereotype.Component;

@Component
public class NrRrcDlDcchEntryDissector extends AbstractEntryDissector {

    public NrRrcDlDcchEntryDissector() {
        super("NR Radio Resource Control DL-DCCH", "NR-RRC DL-DCCH", "nr-rrc.dl.dcch");
    }
}
