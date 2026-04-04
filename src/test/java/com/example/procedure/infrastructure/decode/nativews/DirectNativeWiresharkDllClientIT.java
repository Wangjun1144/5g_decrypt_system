package com.example.procedure.infrastructure.decode.nativews;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

/**
 * Manual integration tests for the minimal ws-core NAS/NGAP DLLs.
 *
 * Usage:
 * 1. Edit the hex string in the test you want to run.
 * 2. Run that single test.
 * 3. Check the printed JSON or the output file under runtime/native_dll_decode.
 */
class DirectNativeWiresharkDllClientIT {

    static {
        StringJoiner joiner = new StringJoiner(";");
        joiner.add("D:\\mingw64\\bin");
        joiner.add("D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\deps\\msys2-glib\\mingw64\\bin");
        joiner.add("D:\\ideaterm\\5g-decrypt-system\\native\\ws-core\\deps\\msys2-glib-extra\\mingw64\\bin");
        String existing = System.getProperty("jna.library.path");
        if (existing != null && !existing.isBlank()) {
            joiner.add(existing);
        }
        System.setProperty("jna.library.path", joiner.toString());
    }

    @Test
    void debug_decode_nas_hex_with_minimal_dll() throws Exception {
        String nasHex = "7e004101";

        NativeWiresharkBridgeProperties properties = new NativeWiresharkBridgeProperties();
        DirectNativeWiresharkDllClient client = new DirectNativeWiresharkDllClient(properties);

        String json = client.decodeNas5gsHex(nasHex);
        writeOutput("nas_manual_decode.json", json);
        System.out.println(json);
    }

    @Test
    void debug_decode_ngap_hex_with_minimal_dll() throws Exception {
        String ngapHex = "000f40808a0000060055000200000026005a597e0194bbdaf0087e004119000bf200f110020040c00007ec2e04f070f0707100387e004119000bf200f110020040c00007ec1001032e04f070f0702f0201015200f1100000641707f070c0401180b0180100740000905301030079000f4000f11000066c000000f110000064005a4001180003400200400070400100";

        NativeWiresharkBridgeProperties properties = new NativeWiresharkBridgeProperties();
        DirectNativeWiresharkDllClient client = new DirectNativeWiresharkDllClient(properties);

        String json = client.decodeNgapHex(ngapHex);
        writeOutput("ngap_manual_decode.json", json);
        System.out.println(json);
    }

    @Test
    void debug_decode_nr_rrc_hex_with_minimal_dll() throws Exception {
        String nrRrcHex = "10c00040080597e0194bbdaf0087e004119000bf200f110020040c00007ec2e04f070f0707100387e004119000bf200f110020040c00007ec1001032e04f070f0702f0201015200f1100000641707f070c0401180b0180100740000905301030";

        NativeWiresharkBridgeProperties properties = new NativeWiresharkBridgeProperties();
        DirectNativeWiresharkDllClient client = new DirectNativeWiresharkDllClient(properties);

        String json = client.decodeNrRrcHex(nrRrcHex);
        writeOutput("nr_rrc_manual_decode.json", json);
        System.out.println(json);
    }

    @Test
    void debug_decode_mac_nr_chain_hex_with_minimal_dll() throws Exception {
        String macNrHex = "6d61632d6e72010103020c76030000060005000403400101030005000110c0040004b43f7ce70ac576a351a85e193f000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";

        NativeWiresharkBridgeProperties properties = new NativeWiresharkBridgeProperties();
        DirectNativeWiresharkDllClient client = new DirectNativeWiresharkDllClient(properties);

        String json = client.decodeMacNrChainHex(macNrHex);
        writeOutput("mac_nr_chain_manual_decode.json", json);
        System.out.println(json);
    }

    private static void writeOutput(String fileName, String json) throws Exception {
        Path outputDir = Path.of("runtime", "native_dll_decode").toAbsolutePath().normalize();
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve(fileName), json, StandardCharsets.UTF_8);
    }
}
