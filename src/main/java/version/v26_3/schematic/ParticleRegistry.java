package version.v26_3.schematic;

import core.config.Config;
import core.config.Version;
import version.v26_3.packets.DataTypeProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores the particle type registry received from the server during the configuration phase.
 * The registry maps particle identifiers (e.g. {@code minecraft:dust}) to their numeric VarInt
 * IDs used in the {@code LevelParticles} packet. The IDs are version/server-specific because
 * the server can add custom particles via datapacks, so we cannot hardcode them.
 *
 * <p>For versions before 1.20.6 (which don't have the configuration-phase RegistryData packet),
 * we fall back to hardcoded particle IDs that were compiled into the vanilla client.
 *
 * <p>For versions 1.20.6+, the server normally sends the {@code minecraft:particle_type} registry
 * via RegistryData. However, some servers use the "Known Packs" optimization and skip sending
 * registries that the client already knows from vanilla. In that case, {@code isLoaded()} returns
 * {@code false} and we fall back to the vanilla-default particle IDs for that version, which were
 * extracted directly from Mojang's data generator ({@code --reports}) for each supported version.
 */
public final class ParticleRegistry {
    private static final ParticleRegistry INSTANCE = new ParticleRegistry();

    private final Map<String, Integer> idsByName = new HashMap<>();

    private ParticleRegistry() { }

    public static ParticleRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Clear any loaded server registry. Used on disconnect/reconnect and in tests.
     */
    public void clear() {
        idsByName.clear();
    }

    /**
     * Load particle IDs from a {@code minecraft:particle_type} registry sent in RegistryData.
     * The entry index in the registry list IS the particle's numeric ID.
     */
    public void load(DataTypeProvider.Registry registry) {
        idsByName.clear();
        var entries = registry.entries();
        for (int id = 0; id < entries.size(); id++) {
            String name = entries.get(id).name();
            idsByName.put(name, id);
        }
    }

    /**
     * @return the numeric ID for the given particle identifier (e.g. {@code minecraft:dust}),
     *         or {@code -1} if the particle doesn't exist and no fallback is available.
     */
    public int getId(String name) {
        Integer id = idsByName.get(name);
        if (id != null) {
            return id;
        }

        // Fallback: either pre-1.20.6 (no RegistryData at all) or 1.20.6+ where the server
        // skipped sending the particle_type registry via the "Known Packs" optimization.
        if ("minecraft:flame".equals(name)) {
            return getFallbackFlameId();
        }
        return -1;
    }

    /**
     * @return the hardcoded flame particle ID for the current protocol version, or -1 if unknown.
     * Vanilla-default IDs extracted from Mojang's {@code --reports} data generator:
     * <pre>
     *   1.20.6 = 31    1.21 = 31    1.21.2 = 31    1.21.4 = 31
     *   26.1   = 32    26.2 = 39
     * </pre>
     * Pre-1.20.6 IDs from PrismarineJS minecraft-data particles.json:
     * <pre>
     *   1.13 = 23    1.14-1.16 = 26    1.17 = 28
     *   1.18 = 27    1.19-1.20.2 = 28    1.20.4 = 31
     * </pre>
     */
    private int getFallbackFlameId() {
        var vr = Config.versionReporter();
        // 26.2 (protocol 776): flame=39
        // (geyser/noxious_gas/sulfur particles inserted before flame, shifting it from 32→39)
        if (vr.isAtLeast(Version.V26_2)) {
            return 39;
        }
        // 26.1 (protocol 775): flame=32
        // (copper_fire_flame inserted at id=5, shifting flame from 31→32)
        return 32;
    }

    /**
     * @return {@code true} if the registry has been loaded from the server (1.20.6+).
     */
    public boolean isLoaded() {
        return !idsByName.isEmpty();
    }
}
