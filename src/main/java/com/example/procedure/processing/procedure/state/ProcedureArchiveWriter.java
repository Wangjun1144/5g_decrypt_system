package com.example.procedure.processing.procedure.state;

import com.example.procedure.model.Procedure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Component
public class ProcedureArchiveWriter {

    private static final Logger log = LoggerFactory.getLogger(ProcedureArchiveWriter.class);
    private static final String ARCHIVE_FILE = "data/procedure_history.jsonl";

    private final ObjectMapper objectMapper;

    public ProcedureArchiveWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Appends one finished procedure as a JSONL record so history writes stay streaming-friendly.
     */
    public void append(Procedure procedure) throws IOException {
        // The writer owns archive directory preparation so callers only care about business intent.
        ensureArchiveDirExists();

        try (FileWriter writer = new FileWriter(ARCHIVE_FILE, true)) {
            writer.write(objectMapper.writeValueAsString(procedure));
            writer.write("\n");
        }
    }

    /**
     * Ensures the archive directory exists before any append attempt.
     */
    private void ensureArchiveDirExists() {
        File file = new File(ARCHIVE_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean ok = parent.mkdirs();
            if (!ok) {
                log.warn("Failed to create archive directory: {}", parent.getAbsolutePath());
            }
        }
    }
}
