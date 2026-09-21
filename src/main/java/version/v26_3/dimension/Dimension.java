package version.v26_3.dimension;

import com.google.gson.Gson;
import core.config.Config;
import core.interfaces.IDimension;
import core.interfaces.IDimensionRegistry;
import core.util.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static core.util.ExceptionHandling.attempt;

/**
 * Class to hold both custom and default dimensions. For custom dimensions, it can write a partial definition file.
 * Server does not tell us how the world is generated, but we can use an empty superflat generator which ensures
 * no new chunks are generated.
 */
public class Dimension implements IDimension {
    public static final Dimension OVERWORLD = new Dimension("minecraft", "overworld", "minecraft:overworld");
    public static final Dimension NETHER = new Dimension("minecraft", "the_nether", "minecraft:the_nether");
    public static final Dimension END = new Dimension("minecraft", "the_end", "minecraft:the_end");
    public static final List<Dimension> DEFAULTS = Arrays.asList(OVERWORLD, NETHER, END);

    private final String namespace;
    private final String name;
    private String type;

    public Dimension(String namespace, String name, String type) {
        this(namespace, name);
        this.type = type;
    }

    public Dimension(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public String getType() {
        if (type == null) { return "minecraft:overworld"; }
        return type;
    }

    /**
     * Find the dimension from its identifier name. For custom dimensions we need to consult the codec.
     */
    public static Dimension fromString(String readString) {
        switch (readString) {
            case "minecraft:the_end": return END;
            case "minecraft:the_nether": return NETHER;
            case "minecraft:overworld": return OVERWORLD;
            default: {
                IDimensionRegistry registry = Config.getVersionModule() != null
                        ? Config.getVersionModule().getDimensionRegistry() : null;
                if (registry == null) { return OVERWORLD; }

                Dimension dim = (Dimension) registry.getDimension(readString);
                if (dim == null) { return OVERWORLD; }

                return dim;
            }
        }
    }

    public static Dimension standardDimensionFromString(String string) {
        switch (string) {
            case "minecraft:the_end": return END;
            case "minecraft:the_nether": return NETHER;
            default: return OVERWORLD;
        }
    }

    /**
     * Path where the world should be saved to. For custom dimensions it depends on the name and namespace.
     */
    public String getPath() {
        // 26.1+ stores every dimension under dimensions/<namespace>/<name>.
        return Paths.get("dimensions", namespace, name).toString();
    }

    /**
     * Write the dimension data to the dimension directory.
     */
    public void write(Path prefix) throws IOException {
        Path destination = PathUtils.toPath(prefix.toString(), namespace, "dimension", name + ".json");
        Files.createDirectories(destination.getParent());

        DimensionDefinition definition = new DimensionDefinition(type);

        Files.write(destination, Collections.singleton(new Gson().toJson(definition)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Dimension dimension = (Dimension) o;

        if (!Objects.equals(namespace, dimension.namespace)) return false;
        return Objects.equals(name, dimension.name);
    }

    @Override
    public int hashCode() {
        int result = namespace != null ? namespace.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return namespace + ":" + name;
    }

    public String getName() {
        return this.toString();
    }

    public void setType(String dimensionType) {
        IDimensionRegistry registry = Config.getVersionModule() != null
                ? Config.getVersionModule().getDimensionRegistry() : null;
        if (registry == null) { return; }

        String typeName = registry.getDimensionTypeName(dimensionType);
        if (typeName == null) {
            return;
        }
        this.type = typeName;

        int[] bounds = registry.getDimensionTypeBounds(dimensionType);
        if (bounds != null && bounds.length >= 2) {
            core.config.Config.getVersionModule().setWorldHeight(bounds[0], bounds[1]);
        }

        // re-write since we write the dimension information on join otherwise
        attempt(() -> write(PathUtils.toPath(Config.getWorldOutputDir(), "datapacks", "downloaded", "data")));
    }

    public boolean isNether() {
        return this == NETHER;
    }
}

/**
 * Class to hold a dimension definition file. We need to be able to modify the type so storing it in a resource file is
 * more hassle than this.
 */
class DimensionDefinition {
    private String type = "minecraft:overworld";
    private final Generator generator = new Generator();

    public DimensionDefinition(String type) {
        if (type != null) {
            this.type = type;
        }
    }

    static class Generator {
        private final String type = "minecraft:flat";
        private final int seed = 0;
        private final Settings settings = new Settings();

        @SuppressWarnings("MismatchedCollectionQueryUpdate")
        static class Settings {
            private final byte[] layers = new byte[0];
            private final HashMap<String, HashMap> structures;

            public Settings() {
                structures = new HashMap<>();
                structures.put("structures", new HashMap<>());
            }
        }
    }
}
