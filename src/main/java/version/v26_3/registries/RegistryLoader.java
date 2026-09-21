package version.v26_3.registries;

import version.v26_3.chunk.BlockEntityRegistry;
import version.v26_3.chunk.palette.BlockRegistry;
import version.v26_3.container.ItemRegistry;
import version.v26_3.container.MenuRegistry;
import version.v26_3.entity.EntityNames;
import version.v26_3.villagers.VillagerProfessionRegistry;
import version.v26_3.villagers.VillagerTypeRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the block/entity/item/menu registries for a given Minecraft version from the bundled
 * classpath resources ({@code /v<version>/blocks.json}, {@code /v<version>/registries.json}).
 *
 * <p>The reports are generated once from {@code server.jar --reports} and shipped inside the JAR
 * so that end users no longer need to download a server.jar or run a data generator at runtime.
 * Adding a new version means dropping a new {@code v<version>/} resource directory and the
 * reports it contains.
 */
public class RegistryLoader {
    private static final String REGISTRY_FILENAME = "registries.json";
    private static final String BLOCKS_FILENAME = "blocks.json";

    private final String version;
    private final String resourceDir;

    private static final Map<String, RegistryLoader> knownLoaders = new ConcurrentHashMap<>();

    public static RegistryLoader forVersion(String version) {
        return knownLoaders.computeIfAbsent(version, v -> {
            try {
                return new RegistryLoader(v);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    private RegistryLoader(String version) throws IOException {
        this.version = version;
        // "26.1" -> "v26_1"
        this.resourceDir = "v" + version.replaceAll("\\.", "_");

        try (InputStream blocks = openResource(BLOCKS_FILENAME)) {
            if (blocks == null) {
                throw new IOException("Missing bundled block report for version " + version
                    + " (resource /" + resourceDir + "/" + BLOCKS_FILENAME + ")");
            }
        }
        try (InputStream registries = openResource(REGISTRY_FILENAME)) {
            if (registries == null) {
                throw new IOException("Missing bundled registry report for version " + version
                    + " (resource /" + resourceDir + "/" + REGISTRY_FILENAME + ")");
            }
        }
    }

    private InputStream openResource(String filename) {
        String path = "/" + resourceDir + "/" + filename;
        return getClass().getResourceAsStream(path);
    }

    public EntityNames generateEntityNames() throws IOException {
        try (InputStream in = openResource(REGISTRY_FILENAME)) {
            return EntityNames.fromRegistry(in);
        }
    }

    public BlockRegistry generateGlobalPalette() throws IOException {
        try (InputStream in = openResource(BLOCKS_FILENAME)) {
            return new BlockRegistry(in);
        }
    }

    public MenuRegistry generateMenuRegistry() throws IOException {
        try (InputStream in = openResource(REGISTRY_FILENAME)) {
            return MenuRegistry.fromRegistry(in);
        }
    }

    public ItemRegistry generateItemRegistry() throws IOException {
        try (InputStream in = openResource(REGISTRY_FILENAME)) {
            return ItemRegistry.fromRegistry(in);
        }
    }

    public DataComponentRegistry generateDataComponentRegistry() throws IOException {
        try (InputStream in = openResource(REGISTRY_FILENAME)) {
            return DataComponentRegistry.fromRegistry(in);
        }
    }

    public BlockEntityRegistry generateBlockEntityRegistry() throws IOException {
        try (InputStream in = openResource(REGISTRY_FILENAME)) {
            return BlockEntityRegistry.fromRegistry(in);
        }
    }

    public VillagerProfessionRegistry generateVillagerProfessionRegistry() throws IOException {
        try (InputStream in = openResource(REGISTRY_FILENAME)) {
            return VillagerProfessionRegistry.fromRegistry(in);
        }
    }

    public VillagerTypeRegistry generateVillagerTypeRegistry() throws IOException {
        try (InputStream in = openResource(REGISTRY_FILENAME)) {
            return VillagerTypeRegistry.fromRegistry(in);
        }
    }
}
