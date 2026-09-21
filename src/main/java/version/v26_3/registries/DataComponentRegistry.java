package version.v26_3.registries;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DataComponentRegistry {
    private final Map<Integer, String> idToName = new HashMap<>();
    private final Map<String, Integer> nameToId = new HashMap<>();

    public static DataComponentRegistry fromRegistry(InputStream input) {
        DataComponentRegistry registry = new DataComponentRegistry();
        if (input == null) {
            return registry;
        }

        RegistriesJson registries = new Gson().fromJson(new InputStreamReader(input), RegistriesJson.class);
        RegistryTypes componentTypes = registries.get("minecraft:data_component_type");
        if (componentTypes == null) {
            return registry;
        }
        componentTypes.getEntries().forEach((name, properties) -> {
            Integer protocolId = properties.get("protocol_id");
            if (protocolId != null) {
                registry.idToName.put(protocolId, name);
                registry.nameToId.put(name, protocolId);
            }
        });
        return registry;
    }

    public String getName(int protocolId) {
        return idToName.get(protocolId);
    }

    public Integer getProtocolId(String name) {
        return nameToId.get(name);
    }

    public int size() {
        return idToName.size();
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(nameToId.keySet());
    }
}
