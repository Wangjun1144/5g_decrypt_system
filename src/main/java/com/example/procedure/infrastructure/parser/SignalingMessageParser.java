package com.example.procedure.infrastructure.parser;

import com.example.procedure.model.SignalingMessage;

import java.io.IOException;
import java.util.List;

/**
 * Formal parser entry contract for signaling-message parsing.
 *
 * Current responsibilities:
 * 1. Parse tshark-derived JSON inputs into signaling messages.
 * 2. Expose a stable parser boundary for application code and tests.
 * 3. Allow future parser implementations to be swapped without changing
 *    upstream processing code.
 */
public interface SignalingMessageParser {

    /**
     * Parses a single logic JSON file into signaling messages.
     *
     * @param jsonFilePath tshark logic JSON file path
     * @return parsed signaling messages
     * @throws IOException when the input cannot be read or parsed
     */
    List<SignalingMessage> parseFile(String jsonFilePath) throws IOException;

    /**
     * Parses aligned logic and raw JSON files into signaling messages.
     *
     * @param logicJsonPath logic JSON file path
     * @param rawJsonPath raw JSON file path
     * @return parsed signaling messages
     * @throws IOException when the input cannot be read or parsed
     */
    List<SignalingMessage> parseFileWithRaw(String logicJsonPath, String rawJsonPath) throws IOException;

    /**
     * Parses and merges two aligned logic/raw capture pairs into a single
     * ordered signaling-message stream.
     *
     * @param logicJsonPath1 first logic JSON file path
     * @param logicJsonPath2 second logic JSON file path
     * @param rawJsonPath1 first raw JSON file path
     * @param rawJsonPath2 second raw JSON file path
     * @return merged signaling messages
     * @throws IOException when the input cannot be read or parsed
     */
    List<SignalingMessage> parseAndMerge(
            String logicJsonPath1,
            String logicJsonPath2,
            String rawJsonPath1,
            String rawJsonPath2
    ) throws IOException;

    /**
     * Parses and merges two aligned logic/raw capture pairs without applying
     * pinning-style reordering.
     *
     * @param logicJsonPath1 first logic JSON file path
     * @param logicJsonPath2 second logic JSON file path
     * @param rawJsonPath1 first raw JSON file path
     * @param rawJsonPath2 second raw JSON file path
     * @return merged signaling messages
     * @throws IOException when the input cannot be read or parsed
     */
    List<SignalingMessage> parseAndMergeNoPin(
            String logicJsonPath1,
            String logicJsonPath2,
            String rawJsonPath1,
            String rawJsonPath2
    ) throws IOException;
}
