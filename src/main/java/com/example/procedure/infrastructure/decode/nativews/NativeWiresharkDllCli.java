package com.example.procedure.infrastructure.decode.nativews;

/**
 * Simple CLI for directly testing the ws-core minimal DLLs from Java.
 *
 * Usage:
 * java ... NativeWiresharkDllCli nas 7e004101
 * java ... NativeWiresharkDllCli ngap 00150033...
 * java ... NativeWiresharkDllCli nr-rrc 3a2fbf...
 */
public final class NativeWiresharkDllCli {

    private NativeWiresharkDllCli() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: NativeWiresharkDllCli <nas|ngap|nr-rrc> <hex>");
        }

        String protocol = args[0].trim().toLowerCase();
        String hex = args[1].trim();

        NativeWiresharkBridgeProperties properties = new NativeWiresharkBridgeProperties();
        DirectNativeWiresharkDllClient client = new DirectNativeWiresharkDllClient(properties);

        String json = switch (protocol) {
            case "nas" -> client.decodeNas5gsHex(hex);
            case "ngap" -> client.decodeNgapHex(hex);
            case "nr-rrc", "nrrrc", "rrc" -> client.decodeNrRrcHex(hex);
            default -> throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        };

        System.out.println(json);
    }
}
