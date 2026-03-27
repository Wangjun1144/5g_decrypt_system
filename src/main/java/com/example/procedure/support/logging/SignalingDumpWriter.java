package com.example.procedure.support.logging;

import com.example.procedure.model.SignalingMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Utility for writing signaling dump logs to disk.
 *
 * This keeps file-oriented dump behavior out of the application and processing
 * layers while preserving the existing log file generation semantics.
 */
public final class SignalingDumpWriter {

    private SignalingDumpWriter() {
    }

    /**
     * Write one signaling message to the target dump file.
     *
     * @param msg signaling message to dump
     * @param output target log file
     * @param append whether to append to the existing file
     */
    public static void write(SignalingMessage msg, Path output, boolean append) {
        SignalingMessagePrinter.printAndWriteToFile(msg, output, append);
    }

    /**
     * Delete a whole log directory recursively.
     *
     * @param logDir log directory to delete
     */
    public static void deleteLogDirectory(Path logDir) {
        if (logDir == null || Files.notExists(logDir)) {
            return;
        }

        try {
            Files.walk(logDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete log file: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete log directory: " + logDir, e);
        }
    }
}
