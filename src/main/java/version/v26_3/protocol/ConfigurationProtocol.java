package version.v26_3.protocol;

import java.util.HashMap;
import java.util.Map;

/**
 * Packet name/id tables for the Configuration phase. The supported versions (26.x) all use the
 * 1.20.6 layout: server-bound {@code FinishConfiguration} at 0x03, client-bound {@code RegistryData}
 * at 0x07. If a future Minecraft version renumbers these, override this class or extend the tables
 * here (see docs/LEGACY_VERSION_REMOVAL_PLAN.md section 3.1).
 */
public class ConfigurationProtocol extends Protocol {
    private final Map<Integer, String> clientBound;
    private final Map<Integer, String> serverBound;

    public ConfigurationProtocol() {
        clientBound = new HashMap<>();
        serverBound = new HashMap<>();

        // 1.20.6+ (and 26.x) configuration packet layout
        serverBound.put(0x03, "FinishConfiguration");
        clientBound.put(0x07, "RegistryData");
    }

    @Override
    protected String clientBound(int packet) {
        return clientBound.getOrDefault(packet, "UNKNOWN");
    }

    @Override
    protected String serverBound(int packet) {
        return serverBound.getOrDefault(packet, "UNKNOWN");
    }
}
