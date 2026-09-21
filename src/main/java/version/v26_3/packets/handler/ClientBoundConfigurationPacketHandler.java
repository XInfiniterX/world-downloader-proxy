package version.v26_3.packets.handler;

import version.v26_3.proxy.ConnectionManager;
import version.v26_3.schematic.DynamicRegistry;
import version.v26_3.schematic.ParticleRegistry;
import version.v26_3.world.WorldManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles client-bound configuration packets for the supported Minecraft versions (26.x).
 *
 * <p>This is the single (flattened) implementation, previously reached through the
 * {@code ClientBoundConfigurationPacketHandler_1_20_2 / _1_20_6} variants. The effective 26.x
 * {@code RegistryData} operator (the 1.20.6 per-registry form) is registered directly here. The
 * class is non-final and {@link #getOperators()} returns a mutable map, so a future version can
 * override individual operators (see docs/LEGACY_VERSION_REMOVAL_PLAN.md section 3.1).
 */
public class ClientBoundConfigurationPacketHandler extends PacketHandler {
    private final HashMap<String, PacketOperator> operations = new HashMap<>();

    public ClientBoundConfigurationPacketHandler(ConnectionManager connectionManager) {
        super(connectionManager);

        operations.put("RegistryData", provider -> {
            var registry = provider.readRegistry();
            switch (registry.name()) {
                case "minecraft:worldgen/biome" -> WorldManager.getInstance().getDimensionRegistry().loadBiomes(registry);
                case "minecraft:dimension_type" -> WorldManager.getInstance().getDimensionRegistry().loadDimensions(registry);
                case "minecraft:particle_type" -> ParticleRegistry.getInstance().load(registry);
                // Dynamic registries needed for data component encoding (enchantments, banner patterns).
                // These are not in the static registries.json report, so we must capture them from the
                // server's RegistryData packet to convert numeric IDs to namespaced names.
                case "minecraft:enchantment", "minecraft:banner_pattern", "minecraft:block_transformer",
                     "minecraft:decorated_pot_pattern", "minecraft:context_int_provider",
                     "minecraft:context_float_provider" -> DynamicRegistry.getInstance().load(registry);
            }
            return true;
        });
    }

    public static PacketHandler of(ConnectionManager connectionManager) {
        return new ClientBoundConfigurationPacketHandler(connectionManager);
    }

    @Override
    public Map<String, PacketOperator> getOperators() {
        return operations;
    }

    @Override
    public boolean isClientBound() {
        return true;
    }
}
