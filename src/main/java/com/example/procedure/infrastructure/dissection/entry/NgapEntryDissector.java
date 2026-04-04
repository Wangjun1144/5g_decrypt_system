package com.example.procedure.infrastructure.dissection.entry;

import org.springframework.stereotype.Component;

@Component
public class NgapEntryDissector extends AbstractEntryDissector {

    public NgapEntryDissector() {
        super("NG Application Protocol", "NGAP", "ngap");
    }
}
