package version.v26_3.chunk;

import com.google.gson.Gson;
import version.v26_3.registries.RegistriesJson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockEntityRegistry {

    private final Map<Integer, String> blockEntities;
    private Set<String> entityNames;

    public static BlockEntityRegistry fromRegistry(InputStream input) {
        if (input == null) { return new BlockEntityRegistry(); }

        RegistriesJson map = new Gson().fromJson(new InputStreamReader(input), RegistriesJson.class);

        // convert JSON structure into protocol_id->name map
        BlockEntityRegistry blockEntityRegistry = new BlockEntityRegistry();
        map.get("minecraft:block_entity_type").getEntries().forEach(
            (name, properties) -> blockEntityRegistry.blockEntities.put(properties.get("protocol_id"), name)
        );

        blockEntityRegistry.entityNames = new HashSet<>(blockEntityRegistry.blockEntities.values());

        return blockEntityRegistry;
    }

    public BlockEntityRegistry() {
        blockEntities = new HashMap<>();
        entityNames = new HashSet<>();
    }

    public String getBlockEntityName(int protocolId) {
        return blockEntities.get(protocolId);
    }

    public boolean isBlockEntity(String id) {
        // The block entity type "minecraft:piston" (PistonMovingBlockEntity) is in the
        // registry, but it should only be associated with minecraft:moving_piston blocks,
        // not minecraft:piston or minecraft:sticky_piston blocks. Without this exclusion,
        // findBlockEntities generates stubs for every piston block, and the destination
        // server rejects them with "Invalid block entity minecraft:piston state ... got
        // Block{minecraft:piston}".
        if (id.equals("minecraft:piston") || id.equals("minecraft:sticky_piston")) {
            return false;
        }
        return entityNames.contains(id) || isSpecialBlockEntity(id);
    }

    // should probably name this something else
    public boolean isSpecialBlockEntity(String id) {
        // piston_head ends with "_head" but is not a skull; exclude it explicitly.
        if (id.equals("minecraft:piston_head")) {
            return false;
        }
        return id.equals("minecraft:moving_piston")
            || id.endsWith("shulker_box")
            || id.endsWith("command_block")
            || id.endsWith("banner")
            || id.endsWith("_head")
            || id.endsWith("_skull");
    }
}
