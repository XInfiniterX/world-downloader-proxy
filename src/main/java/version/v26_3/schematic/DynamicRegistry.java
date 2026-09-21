package version.v26_3.schematic;

import version.v26_3.packets.DataTypeProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores dynamic registries received from the server during the configuration phase
 * (via {@code RegistryData} packets) that are needed for data component encoding but
 * are not included in the static vanilla {@code registries.json} report.
 *
 * <p>Currently tracks:
 * <ul>
 *   <li>{@code minecraft:enchantment} — maps numeric enchantment IDs to namespaced names
 *       (e.g. {@code 12 → "minecraft:sharpness"}). Used by the {@code minecraft:enchantments}
 *       and {@code minecraft:stored_enchantments} component codecs.</li>
 *   <li>{@code minecraft:banner_pattern} — maps numeric banner pattern IDs to namespaced names
 *       (e.g. {@code 1 → "minecraft:stripe_bottom"}). Used by the {@code minecraft:banner_patterns}
 *       component codec.</li>
 * </ul>
 *
 * <p>The entry index in the registry list IS the numeric ID, matching the protocol's
 * {@code IdOmitted} encoding for registry entry holders.
 */
public final class DynamicRegistry {
    private static final DynamicRegistry INSTANCE = new DynamicRegistry();

    private final ConcurrentHashMap<String, List<String>> registries = new ConcurrentHashMap<>();

    private DynamicRegistry() { }

    public static DynamicRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Clear all loaded server registries. Used on disconnect/reconnect and in tests.
     */
    public void clear() {
        registries.clear();
    }

    /**
     * Load a registry sent in RegistryData. The entry index in the registry list IS the
     * entry's numeric ID.
     */
    public void load(DataTypeProvider.Registry registry) {
        List<String> names = new ArrayList<>(registry.entries().size());
        for (var entry : registry.entries()) {
            names.add(entry.name());
        }
        registries.put(registry.name(), names);
    }

    /**
     * @return the namespaced name for the given numeric ID in the given registry,
     *         or {@code null} if the registry is not loaded or the ID is out of range.
     */
    public String getName(String registryName, int id) {
        List<String> names = registries.get(registryName);
        if (names == null || id < 0 || id >= names.size()) {
            return null;
        }
        return names.get(id);
    }

    /**
     * @return {@code true} if the given registry has been loaded from the server.
     */
    public boolean isLoaded(String registryName) {
        return registries.containsKey(registryName);
    }
}
