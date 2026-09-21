package version.v26_3.chunk;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.HashMap;
import java.util.Map;

/**
 * ObjectInputStream that remaps legacy package names to the current WET package layout
 * during deserialization. Needed because the serialized {@code chunkdata_26_*} test
 * fixtures were produced before the package restructure and still reference classes by
 * their old fully-qualified names (e.g. {@code game.data.chunk.ChunkBinary}).
 */
public class PackageRemappingObjectInputStream extends ObjectInputStream {
    private static final Map<String, String> REMAPS = new HashMap<>();

    static {
        // Order-independent: each entry maps an exact old FQN prefix to the new prefix.
        put("game.data.coordinates",      "core.coordinates");
        put("game.data.chunk.palette",    "version.v26_3.chunk.palette");
        put("game.data.chunk",            "version.v26_3.chunk");
        put("game.data.commandblock",     "version.v26_3.commandblock");
        put("game.data.container",        "version.v26_3.container");
        put("game.data.dimension",        "version.v26_3.dimension");
        put("game.data.entity",           "version.v26_3.entity");
        put("game.data.maps",             "version.v26_3.maps");
        put("game.data.region",           "version.v26_3.region");
        put("game.data.registries",       "version.v26_3.registries");
        put("game.data.villagers",        "version.v26_3.villagers");
        put("game.data",                  "version.v26_3.world");
        put("game.protocol",              "version.v26_3.protocol");
        put("config",                     "version.v26_3.config");
        put("packets",                    "version.v26_3.packets");
        put("schematic",                  "version.v26_3.schematic");
        put("util",                       "core.util");
    }

    private static void put(String oldPkg, String newPkg) {
        REMAPS.put(oldPkg, newPkg);
    }

    public PackageRemappingObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    @Override
    protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
        ObjectStreamClass desc = super.readClassDescriptor();
        String remapped = remap(desc.getName());
        if (!remapped.equals(desc.getName())) {
            ObjectStreamClass replacement = ObjectStreamClass.lookupAny(Class.forName(remapped));
            return replacement;
        }
        return desc;
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        String remapped = remap(desc.getName());
        if (!remapped.equals(desc.getName())) {
            return Class.forName(remapped);
        }
        return super.resolveClass(desc);
    }

    private static String remap(String className) {
        for (Map.Entry<String, String> e : REMAPS.entrySet()) {
            String oldPkg = e.getKey();
            // match "oldPkg." prefix (top-level package) or exact "oldPkg"
            if (className.equals(oldPkg) || className.startsWith(oldPkg + ".")) {
                return e.getValue() + className.substring(oldPkg.length());
            }
        }
        return className;
    }
}
