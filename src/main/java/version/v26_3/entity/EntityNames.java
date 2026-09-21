package version.v26_3.entity;

import com.google.gson.Gson;
import version.v26_3.registries.RegistriesJson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

/**
 * Holds a map of entity names.
 */
public class EntityNames {
    private HashMap<Integer, String> entities;

    public EntityNames() {
        entities = new HashMap<>();
    }

    /**
     * Read entity names from auto-generated registries file
     */
    public static EntityNames fromRegistry(InputStream input) {
        if (input == null) { return new EntityNames(); }

        RegistriesJson map = new Gson().fromJson(new InputStreamReader(input), RegistriesJson.class);

        // convert JSON structure into protocol_id->name map
        EntityNames entityNames = new EntityNames();
        map.get("minecraft:entity_type").getEntries().forEach(
            (name, properties) -> entityNames.entities.put(properties.get("protocol_id"), name)
        );

        return entityNames;
    }

    /**
     * Get a block state from a given index. Used to convert packet palettes to the block registry.
     */
    public String getName(int key) {
        return entities.getOrDefault(key, null);
    }
}
